/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.AttributeSyntaxCfgDefn;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.config.ConfigException;
import org.opends.messages.Message;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;


import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of attribute
 * syntaxes defined in the Directory Server.  It wil initialize the syntaxes
 * when the server starts, and then will manage any additions, removals, or
 * modifications to any syntaxes while the server is running.
 */
public class AttributeSyntaxConfigManager
       implements ConfigurationChangeListener<AttributeSyntaxCfg>,
                  ConfigurationAddListener<AttributeSyntaxCfg>,
                  ConfigurationDeleteListener<AttributeSyntaxCfg>

{
  // A mapping between the DNs of the config entries and the associated
  // attribute syntaxes.
  private ConcurrentHashMap<DN,AttributeSyntax> syntaxes;



  /**
   * Creates a new instance of this attribute syntax config manager.
   */
  public AttributeSyntaxConfigManager()
  {
    syntaxes = new ConcurrentHashMap<DN,AttributeSyntax>();
  }



  /**
   * Initializes all attribute syntaxes currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the attribute
   *                           syntax initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the attribute syntaxes that is not
   *                                   related to the server configuration.
   */
  public void initializeAttributeSyntaxes()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any attribute syntax entries are added or removed.
    rootConfiguration.addAttributeSyntaxAddListener(this);
    rootConfiguration.addAttributeSyntaxDeleteListener(this);


    //Initialize the existing attribute syntaxes.
    for (String name : rootConfiguration.listAttributeSyntaxes())
    {
      AttributeSyntaxCfg syntaxConfiguration =
           rootConfiguration.getAttributeSyntax(name);
      syntaxConfiguration.addChangeListener(this);

      if (syntaxConfiguration.isEnabled())
      {
        String className = syntaxConfiguration.getJavaClass();
        try
        {
          AttributeSyntax syntax = loadSyntax(className, syntaxConfiguration,
                                              true);

          try
          {
            DirectoryServer.registerAttributeSyntax(syntax, false);
            syntaxes.put(syntaxConfiguration.dn(), syntax);
          }
          catch (DirectoryException de)
          {
            Message message = WARN_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX.get(
               String.valueOf(syntaxConfiguration.dn()), de.getMessageObject());
            logError(message);
            continue;
          }
        }
        catch (InitializationException ie)
        {
          logError(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // attribute syntax.
      String className = configuration.getJavaClass();
      try
      {
        loadSyntax(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
                                 AttributeSyntaxCfg configuration)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    AttributeSyntax syntax = null;

    // Get the name of the class and make sure we can instantiate it as an
    // attribute syntax.
    String className = configuration.getJavaClass();
    try
    {
      syntax = loadSyntax(className, configuration, true);

      try
      {
        DirectoryServer.registerAttributeSyntax(syntax, false);
        syntaxes.put(configuration.dn(), syntax);
      }
      catch (DirectoryException de)
      {
        Message message = WARN_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX.get(
                String.valueOf(configuration.dn()), de.getMessageObject());
        messages.add(message);

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // If the syntax is enabled, then check to see if there are any defined
    // attribute types that use the syntax.  If so, then don't allow it to be
    // deleted.
    boolean configAcceptable = true;
    AttributeSyntax syntax = syntaxes.get(configuration.dn());
    if (syntax != null)
    {
      String oid = syntax.getOID();
      for (AttributeType at : DirectoryServer.getAttributeTypes().values())
      {
        if (oid.equals(at.getSyntaxOID()))
        {
          Message message = WARN_CONFIG_SCHEMA_CANNOT_DELETE_SYNTAX_IN_USE.get(
                  syntax.getSyntaxName(), at.getNameOrOID());
          unacceptableReasons.add(message);

          configAcceptable = false;
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 AttributeSyntaxCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    AttributeSyntax syntax = syntaxes.remove(configuration.dn());
    if (syntax != null)
    {
      DirectoryServer.deregisterAttributeSyntax(syntax);
      syntax.finalizeSyntax();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      AttributeSyntaxCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as an
      // attribute syntax.
      String className = configuration.getJavaClass();
      try
      {
        loadSyntax(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }
    else
    {
      // If the syntax is currently enabled and the change would make it
      // disabled, then only allow it if the syntax isn't already in use.
      AttributeSyntax syntax = syntaxes.get(configuration.dn());
      if (syntax != null)
      {
        String oid = syntax.getOID();
        for (AttributeType at : DirectoryServer.getAttributeTypes().values())
        {
          if (oid.equals(at.getSyntaxOID()))
          {
            Message message =
                    WARN_CONFIG_SCHEMA_CANNOT_DISABLE_SYNTAX_IN_USE.get(
                            syntax.getSyntaxName(),
                            at.getNameOrOID());
            unacceptableReasons.add(message);
            return false;
          }
        }
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 AttributeSyntaxCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing syntax if it's already enabled.
    AttributeSyntax existingSyntax = syntaxes.get(configuration.dn());


    // If the new configuration has the syntax disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingSyntax != null)
      {
        DirectoryServer.deregisterAttributeSyntax(existingSyntax);

        AttributeSyntax syntax = syntaxes.remove(configuration.dn());
        if (syntax != null)
        {
          syntax.finalizeSyntax();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the attribute syntax.  If the syntax is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the syntax is disabled, then instantiate the class and
    // initialize and register it as an attribute syntax.
    String className = configuration.getJavaClass();
    if (existingSyntax != null)
    {
      if (! className.equals(existingSyntax.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    AttributeSyntax syntax = null;
    try
    {
      syntax = loadSyntax(className, configuration, true);

      try
      {
        DirectoryServer.registerAttributeSyntax(syntax, false);
        syntaxes.put(configuration.dn(), syntax);
      }
      catch (DirectoryException de)
      {
        Message message = WARN_CONFIG_SCHEMA_SYNTAX_CONFLICTING_SYNTAX.get(
                String.valueOf(configuration.dn()),
                de.getMessageObject());
        messages.add(message);

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }
      }
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as an attribute syntax, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the attribute syntax
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the attribute
   *                        syntax.  It should not be {@code null}.
   * @param  initialize     Indicates whether the attribute syntax instance
   *                        should be initialized.
   *
   * @return  The possibly initialized attribute syntax.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the attribute syntax.
   */
  private AttributeSyntax loadSyntax(String className,
                                     AttributeSyntaxCfg configuration,
                                     boolean initialize)
          throws InitializationException
  {
    try
    {
      AttributeSyntaxCfgDefn definition =
           AttributeSyntaxCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends AttributeSyntax> syntaxClass =
           propertyDefinition.loadClass(className, AttributeSyntax.class);
      AttributeSyntax syntax = syntaxClass.newInstance();

      if (initialize)
      {
        Method method = syntax.getClass().getMethod("initializeSyntax",
            configuration.configurationClass());
        method.invoke(syntax, configuration);
      }
      else
      {
        Method method = syntax.getClass().getMethod("isConfigurationAcceptable",
                                                    AttributeSyntaxCfg.class,
                                                    List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(syntax, configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<Message> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message = ERR_CONFIG_SCHEMA_SYNTAX_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return syntax;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_SCHEMA_SYNTAX_CANNOT_INITIALIZE.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}


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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.core;
import org.opends.server.types.SearchScope;
import org.opends.messages.Message;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.admin.std.server.VirtualAttributeCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;


import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.ErrorLogger;
import static org.opends.messages.ConfigMessages.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of
 * virtual attribute providers defined in the Directory Server.  It will
 * initialize the providers when the server starts, and then will manage any
 * additions, removals, or modifications to any virtual attribute providers
 * while the server is running.
 */
public class VirtualAttributeConfigManager
       implements ConfigurationChangeListener<VirtualAttributeCfg>,
                  ConfigurationAddListener<VirtualAttributeCfg>,
                  ConfigurationDeleteListener<VirtualAttributeCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // A mapping between the DNs of the config entries and the associated
  // virtual attribute rules.
  private ConcurrentHashMap<DN,VirtualAttributeRule> rules;



  /**
   * Creates a new instance of this virtual attribute config manager.
   */
  public VirtualAttributeConfigManager()
  {
    rules = new ConcurrentHashMap<DN,VirtualAttributeRule>();
  }



  /**
   * Initializes all virtual attribute providers currently defined in the
   * Directory Server configuration.  This should only be called at Directory
   * Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the virtual
   *                           attribute provider initialization process to
   *                           fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the virtual attribute providers that is
   *                                   not related to the server configuration.
   */
  public void initializeVirtualAttributes()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any virtual attribute provider entries are added or
    // removed.
    rootConfiguration.addVirtualAttributeAddListener(this);
    rootConfiguration.addVirtualAttributeDeleteListener(this);


    //Initialize the existing virtual attribute providers.
    for (String providerName : rootConfiguration.listVirtualAttributes())
    {
      VirtualAttributeCfg cfg =
           rootConfiguration.getVirtualAttribute(providerName);
      cfg.addChangeListener(this);

      if (cfg.isEnabled())
      {
        String className = cfg.getJavaClass();
        try
        {
          VirtualAttributeProvider<? extends VirtualAttributeCfg> provider =
               loadProvider(className, cfg, true);

          LinkedHashSet<SearchFilter> filters =
               new LinkedHashSet<SearchFilter>();
          for (String filterString : cfg.getFilter())
          {
            try
            {
              filters.add(SearchFilter.createFilterFromString(filterString));
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              Message message = ERR_CONFIG_VATTR_INVALID_SEARCH_FILTER.get(
                      filterString, String.valueOf(cfg.dn()),
                      de.getMessageObject());
              throw new ConfigException(message, de);
            }
          }

          if (cfg.getAttributeType().isSingleValue())
          {
            if (provider.isMultiValued())
            {
              Message message = ERR_CONFIG_VATTR_SV_TYPE_WITH_MV_PROVIDER.
                  get(String.valueOf(cfg.dn()),
                      cfg.getAttributeType().getNameOrOID(), className);
              throw new ConfigException(message);
            }
            else if (cfg.getConflictBehavior() ==
                     VirtualAttributeCfgDefn.ConflictBehavior.
                          MERGE_REAL_AND_VIRTUAL)
            {
              Message message = ERR_CONFIG_VATTR_SV_TYPE_WITH_MERGE_VALUES.
                  get(String.valueOf(cfg.dn()),
                      cfg.getAttributeType().getNameOrOID());
              throw new ConfigException(message);
            }
          }

          VirtualAttributeRule rule =
               new VirtualAttributeRule(cfg.getAttributeType(), provider,
                     cfg.getBaseDN(),
                     SearchScope.valueOf(cfg.getScope().name()),
                     cfg.getGroupDN(),
                     filters, cfg.getConflictBehavior());
          rules.put(cfg.dn(), rule);
          DirectoryServer.registerVirtualAttribute(rule);
        }
        catch (InitializationException ie)
        {
          ErrorLogger.logError(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      VirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // virtual attribute provider.
      String className = configuration.getJavaClass();
      try
      {
        loadProvider(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If there were any search filters provided, then make sure they are all
    // valid.
    for (String filterString : configuration.getFilter())
    {
      try
      {
        SearchFilter.createFilterFromString(filterString);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        Message message = ERR_CONFIG_VATTR_INVALID_SEARCH_FILTER.get(
                filterString,
                String.valueOf(configuration.dn()),
                de.getMessageObject());
        unacceptableReasons.add(message);
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
                                 VirtualAttributeCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    // Make sure that we can parse all of the search filters.
    LinkedHashSet<SearchFilter> filters =
         new LinkedHashSet<SearchFilter>();
    for (String filterString : configuration.getFilter())
    {
      try
      {
        filters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
        }

        Message message = ERR_CONFIG_VATTR_INVALID_SEARCH_FILTER.get(
                filterString,
                String.valueOf(configuration.dn()),
                de.getMessageObject());
        messages.add(message);
      }
    }

    // Get the name of the class and make sure we can instantiate it as a
    // certificate mapper.
    VirtualAttributeProvider<? extends VirtualAttributeCfg> provider = null;
    if (resultCode == ResultCode.SUCCESS)
    {
      String className = configuration.getJavaClass();
      try
      {
        provider = loadProvider(className, configuration, true);
      }
      catch (InitializationException ie)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(ie.getMessageObject());
      }
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      VirtualAttributeRule rule =
           new VirtualAttributeRule(configuration.getAttributeType(), provider,
                 configuration.getBaseDN(),
                 SearchScope.valueOf(configuration.getScope().name()),
                 configuration.getGroupDN(),
                 filters,
                 configuration.getConflictBehavior());

      rules.put(configuration.dn(), rule);
      DirectoryServer.registerVirtualAttribute(rule);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      VirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // We will always allow getting rid of a virtual attribute rule.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 VirtualAttributeCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    VirtualAttributeRule rule = rules.remove(configuration.dn());
    if (rule != null)
    {
      DirectoryServer.deregisterVirtualAttribute(rule);
      rule.getProvider().finalizeVirtualAttributeProvider();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      VirtualAttributeCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // virtual attribute provider.
      String className = configuration.getJavaClass();
      try
      {
        loadProvider(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If there were any search filters provided, then make sure they are all
    // valid.
    for (String filterString : configuration.getFilter())
    {
      try
      {
        SearchFilter.createFilterFromString(filterString);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        Message message = ERR_CONFIG_VATTR_INVALID_SEARCH_FILTER.get(
                filterString,
                String.valueOf(configuration.dn()),
                de.getMessageObject());
        unacceptableReasons.add(message);
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 VirtualAttributeCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing rule if it's already enabled.
    VirtualAttributeRule existingRule = rules.get(configuration.dn());


    // If the new configuration has the rule disabled, then disable it if it
    // is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingRule != null)
      {
        rules.remove(configuration.dn());
        DirectoryServer.deregisterVirtualAttribute(existingRule);
        existingRule.getProvider().finalizeVirtualAttributeProvider();
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that we can parse all of the search filters.
    LinkedHashSet<SearchFilter> filters =
         new LinkedHashSet<SearchFilter>();
    for (String filterString : configuration.getFilter())
    {
      try
      {
        filters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
        }

        Message message = ERR_CONFIG_VATTR_INVALID_SEARCH_FILTER.get(
                filterString,
                String.valueOf(configuration.dn()),
                de.getMessageObject());
        messages.add(message);
      }
    }

    // Get the name of the class and make sure we can instantiate it as a
    // certificate mapper.
    VirtualAttributeProvider<? extends VirtualAttributeCfg> provider = null;
    if (resultCode == ResultCode.SUCCESS)
    {
      String className = configuration.getJavaClass();
      try
      {
        provider = loadProvider(className, configuration, true);
      }
      catch (InitializationException ie)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(ie.getMessageObject());
      }
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      VirtualAttributeRule rule =
           new VirtualAttributeRule(configuration.getAttributeType(), provider,
                 configuration.getBaseDN(),
                 SearchScope.valueOf(configuration.getScope().name()),
                 configuration.getGroupDN(),
                 filters,
                 configuration.getConflictBehavior());

      rules.put(configuration.dn(), rule);
      if (existingRule == null)
      {
        DirectoryServer.registerVirtualAttribute(rule);
      }
      else
      {
        DirectoryServer.replaceVirtualAttribute(existingRule, rule);
        existingRule.getProvider().finalizeVirtualAttributeProvider();
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a certificate mapper, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the certificate mapper
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        virtual attribute provider.  It must not be
   *                        {@code null}.
   * @param  initialize     Indicates whether the virtual attribute provider
   *                        instance should be initialized.
   *
   * @return  The possibly initialized certificate mapper.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the certificate mapper.
   */
  private VirtualAttributeProvider<? extends VirtualAttributeCfg>
               loadProvider(String className, VirtualAttributeCfg configuration,
                            boolean initialize)
          throws InitializationException
  {
    try
    {
      VirtualAttributeCfgDefn definition =
           VirtualAttributeCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends VirtualAttributeProvider> providerClass =
           propertyDefinition.loadClass(className,
                                        VirtualAttributeProvider.class);
      VirtualAttributeProvider<? extends VirtualAttributeCfg> provider =
           (VirtualAttributeProvider<? extends VirtualAttributeCfg>)
           providerClass.newInstance();

      if (initialize)
      {
        Method method = provider.getClass().getMethod(
            "initializeVirtualAttributeProvider",
            configuration.configurationClass());
        method.invoke(provider, configuration);
      }
      else
      {
        Method method =
             provider.getClass().getMethod("isConfigurationAcceptable",
                                           VirtualAttributeCfg.class,
                                           List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(provider, configuration,
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

          Message message = ERR_CONFIG_VATTR_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return provider;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_VATTR_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}


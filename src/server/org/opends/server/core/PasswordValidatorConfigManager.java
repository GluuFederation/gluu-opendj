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
import org.opends.messages.Message;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.PasswordValidatorCfgDefn;
import org.opends.server.admin.std.server.PasswordValidatorCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of
 * password validators defined in the Directory Server.  It will initialize the
 * validators when the server starts, and then will manage any additions,
 * removals, or modifications to any password validators while the server is
 * running.
 */
public class PasswordValidatorConfigManager
       implements ConfigurationChangeListener<PasswordValidatorCfg>,
                  ConfigurationAddListener<PasswordValidatorCfg>,
                  ConfigurationDeleteListener<PasswordValidatorCfg>

{
  // A mapping between the DNs of the config entries and the associated
  // password validators.
  private ConcurrentHashMap<DN,PasswordValidator> passwordValidators;



  /**
   * Creates a new instance of this password validator config manager.
   */
  public PasswordValidatorConfigManager()
  {
    passwordValidators = new ConcurrentHashMap<DN,PasswordValidator>();
  }



  /**
   * Initializes all password validators currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the password
   *                           validator initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the password validators that is not
   *                                   related to the server configuration.
   */
  public void initializePasswordValidators()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any password validator entries are added or removed.
    rootConfiguration.addPasswordValidatorAddListener(this);
    rootConfiguration.addPasswordValidatorDeleteListener(this);


    //Initialize the existing password validators.
    for (String validatorName : rootConfiguration.listPasswordValidators())
    {
      PasswordValidatorCfg validatorConfiguration =
           rootConfiguration.getPasswordValidator(validatorName);
      validatorConfiguration.addChangeListener(this);

      if (validatorConfiguration.isEnabled())
      {
        String className = validatorConfiguration.getJavaClass();
        try
        {
          PasswordValidator<? extends PasswordValidatorCfg>
               validator = loadValidator(className, validatorConfiguration,
                                         true);
          passwordValidators.put(validatorConfiguration.dn(), validator);
          DirectoryServer.registerPasswordValidator(validatorConfiguration.dn(),
                                                    validator);
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
                      PasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // password validator.
      String className = configuration.getJavaClass();
      try
      {
        loadValidator(className, configuration, false);
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
                                 PasswordValidatorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    PasswordValidator<? extends PasswordValidatorCfg>
         passwordValidator = null;

    // Get the name of the class and make sure we can instantiate it as a
    // password validator.
    String className = configuration.getJavaClass();
    try
    {
      passwordValidator = loadValidator(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      passwordValidators.put(configuration.dn(), passwordValidator);
      DirectoryServer.registerPasswordValidator(configuration.dn(),
                                                passwordValidator);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      PasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // password validator is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 PasswordValidatorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    DirectoryServer.deregisterPasswordValidator(configuration.dn());

    PasswordValidator passwordValidator =
         passwordValidators.remove(configuration.dn());
    if (passwordValidator != null)
    {
      passwordValidator.finalizePasswordValidator();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      PasswordValidatorCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // password validator.
      String className = configuration.getJavaClass();
      try
      {
        loadValidator(className, configuration, false);
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
  public ConfigChangeResult applyConfigurationChange(
                                 PasswordValidatorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing validator if it's already enabled.
    PasswordValidator existingValidator =
         passwordValidators.get(configuration.dn());


    // If the new configuration has the validator disabled, then disable it if
    // it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingValidator != null)
      {
        DirectoryServer.deregisterPasswordValidator(configuration.dn());

        PasswordValidator passwordValidator =
             passwordValidators.remove(configuration.dn());
        if (passwordValidator != null)
        {
          passwordValidator.finalizePasswordValidator();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the password validator.  If the validator is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the validator is disabled, then instantiate the class
    // and initialize and register it as a password validator.
    String className = configuration.getJavaClass();
    if (existingValidator != null)
    {
      if (! className.equals(existingValidator.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    PasswordValidator<? extends PasswordValidatorCfg>
         passwordValidator = null;
    try
    {
      passwordValidator = loadValidator(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      passwordValidators.put(configuration.dn(), passwordValidator);
      DirectoryServer.registerPasswordValidator(configuration.dn(),
                                                passwordValidator);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a password validator, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the password validator
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        password validator.  It must not be {@code null}.
   * @param  initialize     Indicates whether the password validator instance
   *                        should be initialized.
   *
   * @return  The possibly initialized password validator.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the password validator.
   */
  private PasswordValidator<? extends PasswordValidatorCfg>
               loadValidator(String className,
                             PasswordValidatorCfg configuration,
                             boolean initialize)
          throws InitializationException
  {
    try
    {
      PasswordValidatorCfgDefn definition =
           PasswordValidatorCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends PasswordValidator> validatorClass =
           propertyDefinition.loadClass(className, PasswordValidator.class);
      PasswordValidator<? extends PasswordValidatorCfg> validator =
           (PasswordValidator<? extends PasswordValidatorCfg>)
           validatorClass.newInstance();

      if (initialize)
      {
        Method method = validator.getClass().getMethod(
            "initializePasswordValidator", configuration.configurationClass());
        method.invoke(validator, configuration);
      }
      else
      {
        Method method =
             validator.getClass().getMethod("isConfigurationAcceptable",
                                            PasswordValidatorCfg.class,
                                            List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(validator, configuration,
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

          Message message = ERR_CONFIG_PWVALIDATOR_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return validator;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_PWVALIDATOR_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}


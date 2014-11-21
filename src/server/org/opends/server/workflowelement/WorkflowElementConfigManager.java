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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement;



import java.lang.reflect.InvocationTargetException;
import static org.opends.messages.ConfigMessages.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.WorkflowElementCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.WorkflowElementCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;


/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of workflow elements defined in the Directory Server.
 * It will perform the necessary initialization of those backends when the
 * server is first started, and then will manage any changes to them while
 * the server is running.
 */
public class WorkflowElementConfigManager
       implements ConfigurationChangeListener<WorkflowElementCfg>,
                  ConfigurationAddListener   <WorkflowElementCfg>,
                  ConfigurationDeleteListener<WorkflowElementCfg>

{

  /**
   * Creates a new instance of this workflow config manager.
   */
  public WorkflowElementConfigManager()
  {
  }



  /**
   * Initializes all workflow elements currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the workflow
   *                           element initialization process to fail.
   * @throws InitializationException If a problem occurs while the workflow
   *                                 element is loaded and registered with
   *                                 the server
   */
  public void initializeWorkflowElements()
      throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any workflow element entries are added or removed.
    rootConfiguration.addWorkflowElementAddListener(this);
    rootConfiguration.addWorkflowElementDeleteListener(this);


    //Initialize the existing workflows.
    for (String workflowName : rootConfiguration.listWorkflowElements())
    {
      loadAndRegisterWorkflowElement(workflowName);
    }
  }

  /**
   * Return the associated workflowElement is enabled if the
   * workflow is enabled.
   *
   * @param workflowName workflow identifier
   * @return workflowelement associated with the workflowName of null
   * @throws org.opends.server.config.ConfigException Exception will reading
   *         the config
   * @throws org.opends.server.types.InitializationException Exception while
   *         initializing the workflow element
   */
  public WorkflowElement<?> loadAndRegisterWorkflowElement(String workflowName)
          throws ConfigException, InitializationException {
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();
    WorkflowElementCfg workflowConfiguration =
        rootConfiguration.getWorkflowElement(workflowName);
    workflowConfiguration.addChangeListener(this);

    if (workflowConfiguration.isEnabled())
    {
      return (loadAndRegisterWorkflowElement(workflowConfiguration));
    }

    return (null);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      WorkflowElementCfg configuration,
      List<Message> unacceptableReasons)
  {
    boolean isAcceptable = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // a workflow element.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadWorkflowElement(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        isAcceptable = false;
      }
    }

    return isAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
      WorkflowElementCfg configuration)
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

    configuration.addChangeListener(this);

    // If the new workflow element is enabled then create it and register it.
    if (configuration.isEnabled())
    {
      try
      {
        WorkflowElement<?> we = loadAndRegisterWorkflowElement(configuration);

        // Notify observers who want to be notify when new workflow elements
        // are created.
        WorkflowElement.notifyStateUpdate(we);
      }
      catch (InitializationException de)
      {
        if (changeResult.getResultCode() == ResultCode.SUCCESS)
        {
          changeResult.setResultCode(
              DirectoryServer.getServerErrorResultCode());
        }
        changeResult.addMessage(de.getMessageObject());
      }
    }

    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      WorkflowElementCfg configuration,
      List<Message> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // workflow element is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      WorkflowElementCfg configuration)
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );


    WorkflowElement<?> workflowElement =
            DirectoryServer.getWorkflowElement(
            configuration.dn().getRDN().getAttributeValue(0).toString());
    if (workflowElement != null)
    {
      // Notify to observers that the workflow element is now disabled
      ObservableWorkflowElementState observableState =
        workflowElement.getObservableState();
      observableState.setWorkflowElementEnabled(false);
      observableState.notifyObservers();

      // Remove the workflow element
      DirectoryServer.deregisterWorkflowElement(workflowElement);
      workflowElement.finalizeWorkflowElement();
    }


    return changeResult;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      WorkflowElementCfg configuration,
      List<Message> unacceptableReasons)
  {
    boolean isAcceptable = true;

    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as
      // a workflow element.
      String className = configuration.getJavaClass();
      try
      {
        // Load the class but don't initialize it.
        loadWorkflowElement(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add (ie.getMessageObject());
        isAcceptable = false;
      }
    }

    return isAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      WorkflowElementCfg configuration)
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );


    // Get the existing workflow element if it's already enabled.
    WorkflowElement<?> existingWorkflowElement =
      DirectoryServer.getWorkflowElement(
      configuration.dn().getRDN().getAttributeValue(0).toString());

    // If the new configuration has the workflow element disabled,
    // then disable it if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingWorkflowElement != null)
      {
        // Notify to observers that the workflow element is now disabled
        ObservableWorkflowElementState observableState =
          existingWorkflowElement.getObservableState();
        observableState.setWorkflowElementEnabled(false);
        observableState.notifyObservers();

        // Remove the workflow element
        DirectoryServer.deregisterWorkflowElement(existingWorkflowElement);
        existingWorkflowElement.finalizeWorkflowElement();
      }

      return changeResult;
    }

    // If the workflow element is disabled then create it and register it.
    if (existingWorkflowElement == null)
    {
      try
      {
        WorkflowElement<?> we = loadAndRegisterWorkflowElement(configuration);

        // Notify observers who want to be notify when new workflow elements
        // are created.
        WorkflowElement.notifyStateUpdate(we);
      }
      catch (InitializationException de)
      {
        if (changeResult.getResultCode() == ResultCode.SUCCESS)
        {
          changeResult.setResultCode(
              DirectoryServer.getServerErrorResultCode());
        }
        changeResult.addMessage(de.getMessageObject());
      }
    }

    return changeResult;
  }


  /**
   * Loads a class and instanciates it as a workflow element. The workflow
   * element is initialized and registered with the server.
   *
   * @param workflowElementCfg  the workflow element configuration
   * @return WorkflowElement
   * @throws InitializationException If a problem occurs while trying to
   *                            decode a provided string as a DN or if
   *                            the workflow element ID for a provided
   *                            workflow element conflicts with the workflow
   *                            ID of an existing workflow during workflow
   *                            registration.
   */
  WorkflowElement<?> loadAndRegisterWorkflowElement(
      WorkflowElementCfg workflowElementCfg
      ) throws InitializationException
  {
    // Load the workflow element class
    String className = workflowElementCfg.getJavaClass();
    WorkflowElement<?> workflowElement =
      loadWorkflowElement(className, workflowElementCfg, true);

    try
    {
      // register the workflow element
      DirectoryServer.registerWorkflowElement(workflowElement);
    }
    catch (DirectoryException de)
    {
      throw new InitializationException(de.getMessageObject());
    }
    return (workflowElement);
  }


  /**
   * Loads a class and instanciates it as a workflow element. If requested
   * initializes the newly created instance.
   *
   * @param  className      The fully-qualified name of the workflow element
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the workflow
   *                        element.  It must not be {@code null}.
   * @param  initialize     Indicates whether the workflow element instance
   *                        should be initialized.
   *
   * @return  The possibly initialized workflow element.
   *
   * @throws  InitializationException  If a problem occurred while attempting
   *                                   to initialize the workflow element.
   */
  private WorkflowElement<?> loadWorkflowElement(
      String className,
      WorkflowElementCfg configuration,
      boolean initialize
      ) throws InitializationException
  {
    try
    {
      WorkflowElementCfgDefn              definition;
      ClassPropertyDefinition             propertyDefinition;
      // I cannot use the parameterized type WorflowElement<?>
      // because it would break the line WorkflowElement.class below.
      // Use SuppressWarning because we know the cast is safe.
      @SuppressWarnings("unchecked")
      Class<? extends WorkflowElement>    workflowElementClass;

      definition = WorkflowElementCfgDefn.getInstance();
      propertyDefinition =
        definition.getJavaClassPropertyDefinition();
      workflowElementClass =
        propertyDefinition.loadClass(className, WorkflowElement.class);
      // Again, use SuppressWarning because we know the cast is safe
      @SuppressWarnings("unchecked")
      WorkflowElement<? extends WorkflowElementCfg> workflowElement =
        (WorkflowElement<? extends WorkflowElementCfg>)
          workflowElementClass.newInstance();

      if (initialize)
      {
        Method method = workflowElement.getClass().getMethod(
            "initializeWorkflowElement", configuration.configurationClass());
        method.invoke(workflowElement, configuration);
      }
      else
      {
        Method method = workflowElement.getClass().getMethod(
            "isConfigurationAcceptable",
            WorkflowElementCfg.class,
            List.class);

        List<String> unacceptableReasons = new ArrayList<String>();
        Boolean acceptable = (Boolean) method.invoke(
            workflowElement, configuration, unacceptableReasons);

        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<String> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message =
            ERR_CONFIG_WORKFLOW_ELEMENT_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return workflowElement;
    }
    catch (Exception e)
    {
      Throwable t = e;
      if (e instanceof InvocationTargetException && e.getCause() != null) {
        t = e.getCause();
      }

      Message message =
        ERR_CONFIG_WORKFLOW_ELEMENT_CANNOT_INITIALIZE.get(
            className, String.valueOf(configuration.dn()),
            t.getMessage());
      throw new InitializationException(message);
    }
  }

}


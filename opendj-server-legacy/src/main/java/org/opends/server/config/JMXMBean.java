/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.api.InvokableComponent;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.jmx.Credential;
import org.opends.server.protocols.jmx.JmxClientConnection;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InvokableMethod;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a JMX MBean that can be registered with the Directory
 * Server to provide monitoring and statistical information, provide read and/or
 * read-write access to the configuration, and provide notifications and alerts
 * if a significant event or severe/fatal error occurs.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class JMXMBean
       implements DynamicMBean, DirectoryServerMBean
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.config.JMXMBean";



  /** The set of alert generators for this MBean. */
  private List<AlertGenerator> alertGenerators;

  /** The set of invokable components for this MBean. */
  private List<InvokableComponent> invokableComponents;

  /** The set of monitor providers for this MBean. */
  private List<MonitorProvider<? extends MonitorProviderCfg>> monitorProviders;

  /** The DN of the configuration entry with which this MBean is associated. */
  private DN configEntryDN;

  /** The object name for this MBean. */
  private ObjectName objectName;


  /**
   * Creates a JMX object name string based on a DN.
   *
   * @param  configEntryDN  The DN of the configuration entry with which
   *                        this ObjectName is associated.
   *
   * @return The string representation of the JMX Object Name
   * associated with the input DN.
   */
  public static String getJmxName (DN configEntryDN)
  {
      String typeStr = null;
      String nameStr = null ;
      try
      {
          String dnString = configEntryDN.toString();
          if (dnString != null && dnString.length() != 0)
          {
              StringBuilder buffer = new StringBuilder(dnString.length());
              String rdns[] = dnString.replace(',', ';').split(";");
              for (int j = rdns.length - 1; j >= 0; j--)
              {
                  int rdnIndex = rdns.length - j;
                  buffer.append(",Rdn").append(rdnIndex).append("=") ;
                  for (int i = 0; i < rdns[j].length(); i++)
                  {
                      char c = rdns[j].charAt(i);
                      if (isAlpha(c) || isDigit(c))
                      {
                          buffer.append(c);
                      } else
                      {
                          switch (c)
                          {
                              case ' ':
                                  buffer.append("_");
                                  break;
                              case '=':
                                  buffer.append("-");
                          }
                      }
                  }
              }

              typeStr = buffer.toString();
          }

          nameStr = MBEAN_BASE_DOMAIN + ":" + "Name=rootDSE" + typeStr;
      } catch (Exception e)
      {
        logger.traceException(e);
        logger.error(ERR_CONFIG_JMX_CANNOT_REGISTER_MBEAN, configEntryDN, e);
      }
      return nameStr ;
  }

  /**
   * Creates a new dynamic JMX MBean for use with the Directory Server.
   *
   * @param  configEntryDN  The DN of the configuration entry with which this
   *                        MBean is associated.
   */
  public JMXMBean(DN configEntryDN)
    {
        this.configEntryDN = configEntryDN;

        alertGenerators = new CopyOnWriteArrayList<>();
        invokableComponents = new CopyOnWriteArrayList<>();
        monitorProviders = new CopyOnWriteArrayList<>();

        MBeanServer mBeanServer = DirectoryServer.getJMXMBeanServer();
        if (mBeanServer != null)
        {
            try
            {
                objectName = new ObjectName(getJmxName(configEntryDN)) ;

                try
                {
                  if(mBeanServer.isRegistered(objectName))
                  {
                    mBeanServer.unregisterMBean(objectName);
                  }
                }
                catch(Exception e)
                {
                  logger.traceException(e);
                }

                mBeanServer.registerMBean(this, objectName);

            }
            catch (Exception e)
            {
              logger.traceException(e);
              logger.error(ERR_CONFIG_JMX_CANNOT_REGISTER_MBEAN, configEntryDN, e);
            }
        }
    }



  /**
   * Retrieves the JMX object name for this JMX MBean.
   *
   * @return  The JMX object name for this JMX MBean.
   */
  @Override
  public ObjectName getObjectName()
  {
    return objectName;
  }



  /**
   * Retrieves the set of alert generators for this JMX MBean.
   *
   * @return  The set of alert generators for this JMX MBean.
   */
  public List<AlertGenerator> getAlertGenerators()
  {
    return alertGenerators;
  }



  /**
   * Adds the provided alert generator to the set of alert generators associated
   * with this JMX MBean.
   *
   * @param  generator  The alert generator to add to the set of alert
   *                    generators for this JMX MBean.
   */
  public void addAlertGenerator(AlertGenerator generator)
  {
    synchronized (alertGenerators)
    {
      if (! alertGenerators.contains(generator))
      {
        alertGenerators.add(generator);
      }
    }
  }



  /**
   * Removes the provided alert generator from the set of alert generators
   * associated with this JMX MBean.
   *
   * @param  generator  The alert generator to remove from the set of alert
   *                    generators for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the alert generator was removed, or
   *          <CODE>false</CODE> if it was not associated with this MBean.
   */
  public boolean removeAlertGenerator(AlertGenerator generator)
  {
    synchronized (alertGenerators)
    {
      return alertGenerators.remove(generator);
    }
  }



  /**
   * Retrieves the set of invokable components associated with this JMX MBean.
   *
   * @return  The set of invokable components associated with this JMX MBean.
   */
  public List<InvokableComponent> getInvokableComponents()
  {
    return invokableComponents;
  }



  /**
   * Adds the provided invokable component to the set of components associated
   * with this JMX MBean.
   *
   * @param  component  The component to add to the set of invokable components
   *                    for this JMX MBean.
   */
  public void addInvokableComponent(InvokableComponent component)
  {
    synchronized (invokableComponents)
    {
      if (! invokableComponents.contains(component))
      {
        invokableComponents.add(component);
      }
    }
  }



  /**
   * Removes the provided invokable component from the set of components
   * associated with this JMX MBean.
   *
   * @param  component  The component to remove from the set of invokable
   *                    components for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the specified component was successfully
   *          removed, or <CODE>false</CODE> if not.
   */
  public boolean removeInvokableComponent(InvokableComponent component)
  {
    synchronized (invokableComponents)
    {
      return invokableComponents.remove(component);
    }
  }



  /**
   * Retrieves the set of monitor providers associated with this JMX MBean.
   *
   * @return  The set of monitor providers associated with this JMX MBean.
   */
  public List<MonitorProvider<? extends MonitorProviderCfg>>
              getMonitorProviders()
  {
    return monitorProviders;
  }



  /**
   * Adds the given monitor provider to the set of components associated with
   * this JMX MBean.
   *
   * @param  component  The component to add to the set of monitor providers
   *                    for this JMX MBean.
   */
  public void addMonitorProvider(MonitorProvider<? extends MonitorProviderCfg>
                                      component)
  {
    synchronized (monitorProviders)
    {
      if (! monitorProviders.contains(component))
      {
        monitorProviders.add(component);
      }
    }
  }



  /**
   * Removes the given monitor provider from the set of components associated
   * with this JMX MBean.
   *
   * @param  component  The component to remove from the set of monitor
   *                    providers for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the specified component was successfully
   *          removed, or <CODE>false</CODE> if not.
   */
  public boolean removeMonitorProvider(MonitorProvider<?> component)
  {
    synchronized (monitorProviders)
    {
      return monitorProviders.remove(component);
    }
  }



  /**
   * Retrieves the specified configuration attribute.
   *
   * @param  name  The name of the configuration attribute to retrieve.
   *
   * @return  The specified configuration attribute, or <CODE>null</CODE> if
   *          there is no such attribute.
   */
  private Attribute getJmxAttribute(String name)
  {
    // It's possible that this is a monitor attribute rather than a configurable
    // one. Check all of those.
    AttributeType attrType = DirectoryServer.getAttributeTypeOrDefault(name.toLowerCase(), name);
    for (MonitorProvider<? extends MonitorProviderCfg> monitor : monitorProviders)
    {
      for (org.opends.server.types.Attribute a : monitor.getMonitorData())
      {
        if (attrType.equals(a.getAttributeType()))
        {
          if (a.isEmpty())
          {
            continue;
          }

          Iterator<ByteString> iterator = a.iterator();
          ByteString value = iterator.next();

          if (iterator.hasNext())
          {
            List<String> stringValues = newArrayList(value.toString());
            while (iterator.hasNext())
            {
              value = iterator.next();
              stringValues.add(value.toString());
            }

            String[] valueArray = new String[stringValues.size()];
            stringValues.toArray(valueArray);
            return new Attribute(name, valueArray);
          }
          else
          {
            return new Attribute(name, value.toString());
          }
        }
      }
    }

    return null;
  }



  /**
   * Obtain the value of a specific attribute of the Dynamic MBean.
   *
   * @param  attributeName  The name of the attribute to be retrieved.
   *
   * @return  The requested attribute.
   *
   * @throws  AttributeNotFoundException  If the specified attribute is not
   *                                      associated with this MBean.
   */
  @Override
  public Attribute getAttribute(String attributeName)
         throws AttributeNotFoundException
  {
    // Get the jmx Client connection
    ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      return null;
    }

    // prepare the ldap search

    try
    {
      // Perform the Ldap operation for
      //  - ACI Check
      //  - Loggin purpose
      SearchRequest request = newSearchRequest(configEntryDN, SearchScope.BASE_OBJECT);
      InternalSearchOperation op = null;
      if (clientConnection instanceof JmxClientConnection) {
        op = ((JmxClientConnection) clientConnection).processSearch(request);
      }
      else if (clientConnection instanceof InternalClientConnection) {
        op = ((InternalClientConnection) clientConnection).processSearch(request);
      }
      // BUG : op may be null
      ResultCode rc = op.getResultCode();
      if (rc != ResultCode.SUCCESS) {
        LocalizableMessage message = ERR_CONFIG_JMX_CANNOT_GET_ATTRIBUTE.
            get(attributeName, configEntryDN, op.getErrorMessage());
        throw new AttributeNotFoundException(message.toString());
      }

      return getJmxAttribute(attributeName);
    }
    catch (AttributeNotFoundException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_JMX_ATTR_NO_ATTR.get(configEntryDN, attributeName);
      logger.error(message);
      throw new AttributeNotFoundException(message.toString());
    }
  }

  /**
   * Set the value of a specific attribute of the Dynamic MBean.  In this case,
   * it will always throw {@code InvalidAttributeValueException} because setting
   * attribute values over JMX is currently not allowed.
   *
   * @param  attribute  The identification of the attribute to be set and the
   *                    value it is to be set to.
   *
   * @throws  AttributeNotFoundException  If the specified attribute is not
   *                                       associated with this MBean.
   *
   * @throws  InvalidAttributeValueException  If the provided value is not
   *                                          acceptable for this MBean.
   */
  @Override
  public void setAttribute(javax.management.Attribute attribute)
         throws AttributeNotFoundException, InvalidAttributeValueException
  {
    throw new InvalidAttributeValueException();
  }

  /**
   * Get the values of several attributes of the Dynamic MBean.
   *
   * @param  attributes  A list of the attributes to be retrieved.
   *
   * @return  The list of attributes retrieved.
   */
  @Override
  public AttributeList getAttributes(String[] attributes)
  {
    // Get the jmx Client connection
    ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      return null;
    }

    // Perform the Ldap operation for
    //  - ACI Check
    //  - Loggin purpose
    SearchRequest request = newSearchRequest(configEntryDN, SearchScope.BASE_OBJECT);
    InternalSearchOperation op = null;
    if (clientConnection instanceof JmxClientConnection) {
      op = ((JmxClientConnection) clientConnection).processSearch(request);
    }
    else if (clientConnection instanceof InternalClientConnection) {
      op = ((InternalClientConnection) clientConnection).processSearch(request);
    }

    if (op == null)
    {
      return null;
    }

    ResultCode rc = op.getResultCode();
    if (rc != ResultCode.SUCCESS)
    {
      return null;
    }


    AttributeList attrList = new AttributeList(attributes.length);
    for (String name : attributes)
    {
      try
      {
        Attribute attr = getJmxAttribute(name);
        if (attr != null)
        {
          attrList.add(attr);
          continue;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }

      // It's possible that this is a monitor attribute rather than a
      // configurable one. Check all of those.
      AttributeType attrType = DirectoryServer.getAttributeTypeOrDefault(name.toLowerCase(), name);

monitorLoop:
      for (MonitorProvider<? extends MonitorProviderCfg> monitor :
           monitorProviders)
      {
        for (org.opends.server.types.Attribute a : monitor.getMonitorData())
        {
          if (attrType.equals(a.getAttributeType()))
          {
            if (a.isEmpty())
            {
              continue;
            }

            Iterator<ByteString> iterator = a.iterator();
            ByteString value = iterator.next();

            if (iterator.hasNext())
            {
              List<String> stringValues = newArrayList(value.toString());
              while (iterator.hasNext())
              {
                value = iterator.next();
                stringValues.add(value.toString());
              }

              String[] valueArray = new String[stringValues.size()];
              stringValues.toArray(valueArray);
              attrList.add(new Attribute(name, valueArray));
            }
            else
            {
              attrList.add(new Attribute(name, value.toString()));
            }
            break monitorLoop;
          }
        }
      }
    }

    return attrList;
  }

  /**
   * Sets the values of several attributes of the Dynamic MBean.
   *
   * @param  attributes  A list of attributes:  The identification of the
   *                     attributes to be set and the values they are to be set
   *                     to.
   *
   * @return  The list of attributes that were set with their new values.  In
   *          this case, the list will always be empty because we do not support
   *          setting attribute values over JMX.
   */
  @Override
  public AttributeList setAttributes(AttributeList attributes)
  {
    return new AttributeList();
  }



  /**
   * Allows an action to be invoked on the Dynamic MBean.
   *
   * @param  actionName  The name of the action to be invoked.
   * @param  params      An array containing the parameters to be set when the
   *                     action is invoked.
   * @param  signature   An array containing the signature of the action.  The
   *                     class objects will be loaded through the same class
   *                     loader as the one used for loading the MBean on which
   *                     action is invoked.
   *
   * @return  The object returned by the action, which represents the result of
   *          invoking the action on the MBean specified.
   *
   * @throws  MBeanException  If a problem is encountered while invoking the
   *                          method.
   */
  @Override
  public Object invoke(String actionName, Object[] params, String[] signature)
         throws MBeanException
  {
    for (InvokableComponent component : invokableComponents)
    {
      for (InvokableMethod method : component.getOperationSignatures())
      {
        if (method.hasSignature(actionName, signature))
        {
          try
          {
            method.invoke(component, params);
          }
          catch (MBeanException me)
          {
            logger.traceException(me);

            throw me;
          }
          catch (Exception e)
          {
            logger.traceException(e);

            throw new MBeanException(e);
          }
        }
      }
    }


    // If we've gotten here, then there is no such method so throw an exception.
    StringBuilder buffer = new StringBuilder();
    buffer.append(actionName);
    buffer.append("(");

    if (signature.length > 0)
    {
      buffer.append(signature[0]);

      for (int i=1; i < signature.length; i++)
      {
        buffer.append(", ");
        buffer.append(signature[i]);
      }
    }

    buffer.append(")");

    LocalizableMessage message = ERR_CONFIG_JMX_NO_METHOD.get(buffer, configEntryDN);
    throw new MBeanException(
        new DirectoryException(ResultCode.NO_SUCH_OPERATION, message));
  }



  /**
   * Provides the exposed attributes and actions of the Dynamic MBean using an
   * MBeanInfo object.
   *
   * @return  An instance of <CODE>MBeanInfo</CODE> allowing all attributes and
   *          actions exposed by this Dynamic MBean to be retrieved.
   */
  @Override
  public MBeanInfo getMBeanInfo()
  {
    ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      return new MBeanInfo(CLASS_NAME, null, null, null, null, null);
    }

    List<MBeanAttributeInfo> attrs = new ArrayList<>();
    for (MonitorProvider<? extends MonitorProviderCfg> monitor : monitorProviders)
    {
      for (org.opends.server.types.Attribute a : monitor.getMonitorData())
      {
        attrs.add(new MBeanAttributeInfo(a.getName(), String.class.getName(),
                                         null, true, false, false));
      }
    }

    MBeanAttributeInfo[] mBeanAttributes = attrs.toArray(new MBeanAttributeInfo[attrs.size()]);

    List<MBeanNotificationInfo> notifications = new ArrayList<>();
    for (AlertGenerator generator : alertGenerators)
    {
      String className = generator.getClassName();

      Map<String, String> alerts = generator.getAlerts();
      for (String type : alerts.keySet())
      {
        String[] types       = { type };
        String   description = alerts.get(type);
        notifications.add(new MBeanNotificationInfo(types, className, description));
      }
    }


    MBeanNotificationInfo[] mBeanNotifications = new MBeanNotificationInfo[notifications.size()];
    notifications.toArray(mBeanNotifications);


    List<MBeanOperationInfo> ops = new ArrayList<>();
    for (InvokableComponent component : invokableComponents)
    {
      for (InvokableMethod method : component.getOperationSignatures())
      {
        ops.add(method.toOperationInfo());
      }
    }

    MBeanOperationInfo[] mBeanOperations = new MBeanOperationInfo[ops.size()];
    ops.toArray(mBeanOperations);


    MBeanConstructorInfo[]  mBeanConstructors  = new MBeanConstructorInfo[0];
    return new MBeanInfo(CLASS_NAME,
                         "Configurable Attributes for " + configEntryDN,
                         mBeanAttributes, mBeanConstructors, mBeanOperations,
                         mBeanNotifications);
  }

  /**
   * Get the client JMX connection to use. Returns null if an Exception is
   * caught or if the AccessControlContext subject is null.
   *
   * @return The JmxClientConnection.
   */
  private ClientConnection getClientConnection()
  {
    ClientConnection clientConnection = null;
    java.security.AccessControlContext acc = java.security.AccessController
        .getContext();
    try
    {
      javax.security.auth.Subject subject = javax.security.auth.Subject
          .getSubject(acc);
      if (subject != null)
      {
        Set<?> privateCreds = subject.getPrivateCredentials(Credential.class);
        clientConnection = ((Credential) privateCreds.iterator().next())
            .getClientConnection();
      }
    }
    catch (Exception e)
    {
    }
    return clientConnection;
  }
}

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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.monitors;

import static org.opends.server.util.ServerConstants.*;

import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;

/**
 * This class implements a monitor provider that will report generic information
 * for an enabled Directory Server connection handler, including its protocol,
 * listeners, and established connections.
 */
public class ConnectionHandlerMonitor
       extends MonitorProvider<MonitorProviderCfg>
{
  /** The attribute type that will be used to report the established connections. */
  private AttributeType connectionsType;

  /** The attribute type that will be used to report the listeners. */
  private AttributeType listenerType;

  /**
   * The attribute type that will be used to report the number of established
   * client connections.
   */
  private AttributeType numConnectionsType;

  /** The attribute type that will be used to report the protocol. */
  private AttributeType protocolType;

  /** The attribute type that will be used to report the config dn . */
  private AttributeType configDnType;

  /** The connection handler with which this monitor is associated. */
  private ConnectionHandler<?> connectionHandler;

  /** The name for this monitor. */
  private String monitorName;



  /**
   * Creates a new instance of this connection handler monitor provider that
   * will work with the provided connection handler.  Most of the initialization
   * should be handled in the {@code initializeMonitorProvider} method.
   *
   * @param  connectionHandler  The connection handler with which this monitor
   *                            is associated.
   */
  public ConnectionHandlerMonitor(
       ConnectionHandler<? extends ConnectionHandlerCfg> connectionHandler)
  {
    this.connectionHandler = connectionHandler;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  {
    monitorName = connectionHandler.getConnectionHandlerName();

    connectionsType = DirectoryServer.getAttributeTypeOrDefault(ATTR_MONITOR_CONNHANDLER_CONNECTION);
    listenerType = DirectoryServer.getAttributeTypeOrDefault(ATTR_MONITOR_CONNHANDLER_LISTENER);
    numConnectionsType = DirectoryServer.getAttributeTypeOrDefault(ATTR_MONITOR_CONNHANDLER_NUMCONNECTIONS);
    protocolType = DirectoryServer.getAttributeTypeOrDefault(ATTR_MONITOR_CONNHANDLER_PROTOCOL);
    configDnType = DirectoryServer.getAttributeTypeOrDefault(ATTR_MONITOR_CONFIG_DN);
  }



  /** {@inheritDoc} */
  @Override
  public String getMonitorInstanceName()
  {
    return monitorName;
  }



  /**
   * Retrieves the objectclass that should be included in the monitor entry
   * created from this monitor provider.
   *
   * @return  The objectclass that should be included in the monitor entry
   *          created from this monitor provider.
   */
  @Override
  public ObjectClass getMonitorObjectClass()
  {
    return DirectoryConfig.getObjectClass(OC_MONITOR_CONNHANDLER, true);
  }



  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    LinkedList<Attribute> attrs = new LinkedList<>();

    // Configuration DN
    attrs.add(Attributes.create(configDnType, connectionHandler.getComponentEntryDN().toString()));

    int numConnections = 0;
    LinkedList<ClientConnection> conns = new LinkedList<>(connectionHandler.getClientConnections());
    LinkedList<HostPort> listeners = new LinkedList<>(connectionHandler.getListeners());

    attrs.add(Attributes.create(protocolType, connectionHandler.getProtocol()));

    if (!listeners.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(listenerType);
      builder.addAllStrings(listeners);
      attrs.add(builder.toAttribute());
    }

    if (!conns.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(connectionsType);
      for (ClientConnection c : conns)
      {
        numConnections++;
        builder.add(c.getMonitorSummary());
      }
      attrs.add(builder.toAttribute());
    }

    attrs.add(Attributes.create(numConnectionsType, String
        .valueOf(numConnections)));

    return attrs;
  }
}

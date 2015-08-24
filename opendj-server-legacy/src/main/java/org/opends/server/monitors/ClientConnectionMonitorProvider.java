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

import java.util.List;
import java.util.TreeMap;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.ClientConnectionMonitorProviderCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.InitializationException;

/**
 * This class defines a Directory Server monitor provider that can be
 * used to obtain information about the client connections established
 * to the server. Note that the information reported is obtained with
 * little or no locking, so it may not be entirely consistent,
 * especially for active connections.
 */
public class ClientConnectionMonitorProvider extends
    MonitorProvider<ClientConnectionMonitorProviderCfg>
{

  /**
   * The connection handler associated with this monitor, or null if all
   * connection handlers should be monitored.
   */
  private final ConnectionHandler<?> handler;



  /**
   * Creates an instance of this monitor provider.
   */
  public ClientConnectionMonitorProvider()
  {
    // This will monitor all connection handlers.
    this.handler = null;
  }



  /**
   * Creates an instance of this monitor provider.
   *
   * @param handler
   *          to which the monitor provider is associated.
   */
  public ClientConnectionMonitorProvider(ConnectionHandler handler)
  {
    this.handler = handler;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(
      ClientConnectionMonitorProviderCfg configuration)
      throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the name of this monitor provider. It should be unique
   * among all monitor providers, including all instances of the same
   * monitor provider.
   *
   * @return The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    if (handler == null)
    {
      return "Client Connections";
    }
    else
    {
      // Client connections of a connection handler
      return "Client Connections" + ",cn="
          + handler.getConnectionHandlerName();
    }
  }



  /**
   * Retrieves a set of attributes containing monitor data that should
   * be returned to the client if the corresponding monitor entry is
   * requested.
   *
   * @return A set of attributes containing monitor data that should be
   *         returned to the client if the corresponding monitor entry
   *         is requested.
   */
  @Override
  public List<Attribute> getMonitorData()
  {
    // Re-order the connections by connection ID.
    TreeMap<Long, ClientConnection> connMap = new TreeMap<>();

    if (handler == null)
    {
      // Get information about all the available connections.
      for (ConnectionHandler<?> hdl : DirectoryServer.getConnectionHandlers())
      {
        // FIXME: connections from different handlers could have the same ID.
        for (ClientConnection conn : hdl.getClientConnections())
        {
          connMap.put(conn.getConnectionID(), conn);
        }
      }
    }
    else
    {
      for (ClientConnection conn : handler.getClientConnections())
      {
        connMap.put(conn.getConnectionID(), conn);
      }
    }

    AttributeType attrType = DirectoryServer.getDefaultAttributeType("connection");
    AttributeBuilder builder = new AttributeBuilder(attrType);
    for (ClientConnection conn : connMap.values())
    {
      builder.add(conn.getMonitorSummary());
    }
    return builder.toAttributeList();
  }
}

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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portion Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.admin.ads;

import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.quicksetup.util.Utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.Constants;
import org.opends.server.config.ConfigConstants;
import org.opends.server.schema.SchemaConstants;

/** The object of this class represent an OpenDS server. */
public class ServerDescriptor
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private static final String TRUSTSTORE_DN = "cn=ads-truststore";

  private final Map<ADSContext.ServerProperty, Object> adsProperties = new HashMap<>();
  private final Set<ReplicaDescriptor> replicas = new HashSet<>();
  private final Map<ServerProperty, Object> serverProperties = new HashMap<>();
  private TopologyCacheException lastException;

  /**
   * Enumeration containing the different server properties that we can keep in
   * the ServerProperty object.
   */
  public enum ServerProperty
  {
    /** The associated value is a String. */
    HOST_NAME,
    /** The associated value is an ArrayList of Integer. */
    LDAP_PORT,
    /** The associated value is an ArrayList of Integer. */
    LDAPS_PORT,
    /** The associated value is an Integer. */
    ADMIN_PORT,
    /** The associated value is an ArrayList of Boolean. */
    LDAP_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    LDAPS_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    ADMIN_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    STARTTLS_ENABLED,
    /** The associated value is an ArrayList of Integer. */
    JMX_PORT,
    /** The associated value is an ArrayList of Integer. */
    JMXS_PORT,
    /** The associated value is an ArrayList of Boolean. */
    JMX_ENABLED,
    /** The associated value is an ArrayList of Boolean. */
    JMXS_ENABLED,
    /** The associated value is an Integer. */
    REPLICATION_SERVER_PORT,
    /** The associated value is a Boolean. */
    IS_REPLICATION_SERVER,
    /** The associated value is a Boolean. */
    IS_REPLICATION_ENABLED,
    /** The associated value is a Boolean. */
    IS_REPLICATION_SECURE,
    /** List of servers specified in the Replication Server configuration. This is a Set of String. */
    EXTERNAL_REPLICATION_SERVERS,
    /** The associated value is an Integer. */
    REPLICATION_SERVER_ID,
    /**
     * The instance key-pair public-key certificate. The associated value is a
     * byte[] (ds-cfg-public-key-certificate;binary).
     */
    INSTANCE_PUBLIC_KEY_CERTIFICATE,
    /** The schema generation ID. */
    SCHEMA_GENERATION_ID
  }

  /** Default constructor. */
  protected ServerDescriptor()
  {
  }

  /**
   * Returns the replicas contained on the server.
   * @return the replicas contained on the server.
   */
  public Set<ReplicaDescriptor> getReplicas()
  {
    return new HashSet<>(replicas);
  }

  /**
   * Sets the replicas contained on the server.
   * @param replicas the replicas contained on the server.
   */
  public void setReplicas(Set<ReplicaDescriptor> replicas)
  {
    this.replicas.clear();
    this.replicas.addAll(replicas);
  }

  /**
   * Returns a Map containing the ADS properties of the server.
   * @return a Map containing the ADS properties of the server.
   */
  public Map<ADSContext.ServerProperty, Object> getAdsProperties()
  {
    return adsProperties;
  }

  /**
   * Returns a Map containing the properties of the server.
   * @return a Map containing the properties of the server.
   */
  public Map<ServerProperty, Object> getServerProperties()
  {
    return serverProperties;
  }

  /**
   * Tells whether this server is registered in the ADS or not.
   * @return <CODE>true</CODE> if the server is registered in the ADS and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isRegistered()
  {
    return !adsProperties.isEmpty();
  }

  /**
   * Tells whether this server is a replication server or not.
   * @return <CODE>true</CODE> if the server is a replication server and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationServer()
  {
    return Boolean.TRUE.equals(
        serverProperties.get(ServerProperty.IS_REPLICATION_SERVER));
  }

  /**
   * Tells whether replication is enabled on this server or not.
   * @return <CODE>true</CODE> if replication is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationEnabled()
  {
    return Boolean.TRUE.equals(
        serverProperties.get(ServerProperty.IS_REPLICATION_ENABLED));
  }

  /**
   * Returns the String representation of this replication server based
   * on the information we have ("hostname":"replication port") and
   * <CODE>null</CODE> if this is not a replication server.
   * @return the String representation of this replication server based
   * on the information we have ("hostname":"replication port") and
   * <CODE>null</CODE> if this is not a replication server.
   */
  public String getReplicationServerHostPort()
  {
    if (isReplicationServer())
    {
      return getReplicationServer(getHostName(), getReplicationServerPort());
    }
    return null;
  }

  /**
   * Returns the replication server ID of this server and -1 if this is not a
   * replications server.
   * @return the replication server ID of this server and -1 if this is not a
   * replications server.
   */
  public int getReplicationServerId()
  {
    if (isReplicationServer())
    {
      return (Integer) serverProperties.get(ServerProperty.REPLICATION_SERVER_ID);
    }
    return -1;
  }

  /**
   * Returns the replication port of this server and -1 if this is not a
   * replications server.
   * @return the replication port of this server and -1 if this is not a
   * replications server.
   */
  public int getReplicationServerPort()
  {
    if (isReplicationServer())
    {
      return (Integer) serverProperties.get(
          ServerProperty.REPLICATION_SERVER_PORT);
    }
    return -1;
  }

  /**
   * Returns whether the communication with the replication port on the server
   * is encrypted or not.
   * @return <CODE>true</CODE> if the communication with the replication port on
   * the server is encrypted and <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationSecure()
  {
    return isReplicationServer()
        && Boolean.TRUE.equals(serverProperties.get(ServerProperty.IS_REPLICATION_SECURE));
  }

  /**
   * Sets the ADS properties of the server.
   * @param adsProperties a Map containing the ADS properties of the server.
   */
  public void setAdsProperties(
      Map<ADSContext.ServerProperty, Object> adsProperties)
  {
    this.adsProperties.clear();
    this.adsProperties.putAll(adsProperties);
  }

  /**
   * Returns the host name of the server.
   * @return the host name of the server.
   */
  public String getHostName()
  {
    String host = (String)serverProperties.get(ServerProperty.HOST_NAME);
    if (host != null)
    {
      return host;
    }
    return (String) adsProperties.get(ADSContext.ServerProperty.HOST_NAME);
  }

  /**
   * Returns the URL to access this server using LDAP.  Returns
   * <CODE>null</CODE> if the server is not configured to listen on an LDAP
   * port.
   * @return the URL to access this server using LDAP.
   */
  public String getLDAPURL()
  {
    int port = getPort(ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT);
    if (port != -1)
    {
      String host = getHostName();
      return getLDAPUrl(host, port, false);
    }
    return null;
  }

  /**
   * Returns the URL to access this server using LDAPS.  Returns
   * <CODE>null</CODE> if the server is not configured to listen on an LDAPS
   * port.
   * @return the URL to access this server using LDAP.
   */
  public String getLDAPsURL()
  {
    int port = getPort(ServerProperty.LDAPS_ENABLED, ServerProperty.LDAPS_PORT);
    if (port != -1)
    {
      String host = getHostName();
      return getLDAPUrl(host, port, true);
    }
    return null;
  }

  private int getPort(ServerProperty enabled, ServerProperty port)
  {
    if (!serverProperties.isEmpty())
    {
      List<?> s = (List<?>) serverProperties.get(enabled);
      List<?> p = (List<?>) serverProperties.get(port);
      if (s != null)
      {
        for (int i=0; i<s.size(); i++)
        {
          if (Boolean.TRUE.equals(s.get(i)))
          {
            return (Integer) p.get(i);
          }
        }
      }
    }
    return -1;
  }

  /**
   * Returns the URL to access this server using the administration connector.
   * Returns <CODE>null</CODE> if the server cannot get the administration
   * connector.
   * @return the URL to access this server using the administration connector.
   */
  public String getAdminConnectorURL()
  {
    String host = getHostName();
    int port = getPort(ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT);
    if (port != -1)
    {
      return getLDAPUrl(host, port, true);
    }
    return null;
  }

  /**
   * Returns the list of enabled administration ports.
   * @return the list of enabled administration ports.
   */
  public List<Integer> getEnabledAdministrationPorts()
  {
    List<Integer> ports = new ArrayList<>(1);
    ArrayList<?> s = (ArrayList<?>)serverProperties.get(ServerProperty.ADMIN_ENABLED);
    ArrayList<?> p = (ArrayList<?>)serverProperties.get(ServerProperty.ADMIN_PORT);
    if (s != null)
    {
      for (int i=0; i<s.size(); i++)
      {
        if (Boolean.TRUE.equals(s.get(i)))
        {
          ports.add((Integer)p.get(i));
        }
      }
    }
    return ports;
  }

  /**
   * Returns a String of type host-name:port-number for the server.  If
   * the provided securePreferred is set to true the port that will be used
   * will be the administration connector port.
   * @param securePreferred whether to try to use the secure port as part
   * of the returning String or not.
   * @return a String of type host-name:port-number for the server.
   */
  public String getHostPort(boolean securePreferred)
  {
    int port = -1;

    if (!serverProperties.isEmpty())
    {
      port = getPort(port, ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT);
      if (securePreferred)
      {
        port = getPort(port, ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT);
      }
    }
    else
    {
      ArrayList<ADSContext.ServerProperty> enabledAttrs = new ArrayList<>();

      if (securePreferred)
      {
        enabledAttrs.add(ADSContext.ServerProperty.ADMIN_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.LDAPS_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.LDAP_ENABLED);
      }
      else
      {
        enabledAttrs.add(ADSContext.ServerProperty.LDAP_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.ADMIN_ENABLED);
        enabledAttrs.add(ADSContext.ServerProperty.LDAPS_ENABLED);
      }

      for (ADSContext.ServerProperty prop : enabledAttrs)
      {
        Object v = adsProperties.get(prop);
        if (v != null && "true".equalsIgnoreCase(String.valueOf(v)))
        {
          ADSContext.ServerProperty portProp = getPortProperty(prop);
          Object p = adsProperties.get(portProp);
          if (p != null)
          {
            try
            {
              port = Integer.parseInt(String.valueOf(p));
            }
            catch (Throwable t)
            {
              logger.warn(LocalizableMessage.raw("Error calculating host port: "+t+" in "+
                  adsProperties, t));
            }
            break;
          }
          else
          {
            logger.warn(LocalizableMessage.raw("Value for "+portProp+" is null in "+
                adsProperties));
          }
        }
      }
    }

    String host = getHostName();
    return host + ":" + port;
  }

  private ADSContext.ServerProperty getPortProperty(ADSContext.ServerProperty prop)
  {
    if (prop == ADSContext.ServerProperty.ADMIN_ENABLED)
    {
      return ADSContext.ServerProperty.ADMIN_PORT;
    }
    else if (prop == ADSContext.ServerProperty.LDAPS_ENABLED)
    {
      return ADSContext.ServerProperty.LDAPS_PORT;
    }
    else if (prop == ADSContext.ServerProperty.LDAP_ENABLED)
    {
      return ADSContext.ServerProperty.LDAP_PORT;
    }
    else
    {
      throw new IllegalStateException("Unexpected prop: "+prop);
    }
  }

  private int getPort(int port, ServerProperty adminEnabled, ServerProperty adminPort)
  {
    List<?> s = (List<?>) serverProperties.get(adminEnabled);
    List<?> p = (List<?>) serverProperties.get(adminPort);
    if (s != null)
    {
      for (int i=0; i<s.size(); i++)
      {
        if (Boolean.TRUE.equals(s.get(i)))
        {
          return (Integer) p.get(i);
        }
      }
    }
    return port;
  }

  /**
   * Returns an Id that is unique for this server.
   * @return an Id that is unique for this server.
   */
  public String getId()
  {
    StringBuilder buf = new StringBuilder();
    if (!serverProperties.isEmpty())
    {
      buf.append(serverProperties.get(ServerProperty.HOST_NAME));
      ServerProperty [] props =
      {
          ServerProperty.LDAP_PORT, ServerProperty.LDAPS_PORT,
          ServerProperty.ADMIN_PORT,
          ServerProperty.LDAP_ENABLED, ServerProperty.LDAPS_ENABLED,
          ServerProperty.ADMIN_ENABLED
      };
      for (ServerProperty prop : props) {
        ArrayList<?> s = (ArrayList<?>) serverProperties.get(prop);
        for (Object o : s) {
          buf.append(":").append(o);
        }
      }
    }
    else
    {
      ADSContext.ServerProperty[] props =
      {
          ADSContext.ServerProperty.HOST_NAME,
          ADSContext.ServerProperty.LDAP_PORT,
          ADSContext.ServerProperty.LDAPS_PORT,
          ADSContext.ServerProperty.ADMIN_PORT,
          ADSContext.ServerProperty.LDAP_ENABLED,
          ADSContext.ServerProperty.LDAPS_ENABLED,
          ADSContext.ServerProperty.ADMIN_ENABLED
      };
      for (int i=0; i<props.length; i++)
      {
        if (i != 0)
        {
          buf.append(":");
        }
        buf.append(adsProperties.get(props[i]));
      }
    }
    return buf.toString();
  }

  /**
   * Returns the instance-key public-key certificate retrieved from the
   * truststore backend of the instance referenced through this descriptor.
   *
   * @return The public-key certificate of the instance.
   */
  public byte[] getInstancePublicKeyCertificate()
  {
    return (byte[]) serverProperties.get(ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE);
  }

  /**
   * Returns the schema generation ID of the server.
   * @return the schema generation ID of the server.
   */
  public String getSchemaReplicationID()
  {
    return (String)serverProperties.get(ServerProperty.SCHEMA_GENERATION_ID);
  }

  /**
   * Returns the last exception that was encountered reading the configuration
   * of the server.  Returns null if there was no problem loading the
   * configuration of the server.
   * @return the last exception that was encountered reading the configuration
   * of the server.  Returns null if there was no problem loading the
   * configuration of the server.
   */
  public TopologyCacheException getLastException()
  {
    return lastException;
  }

  /**
   * Sets the last exception that occurred while reading the configuration of
   * the server.
   * @param lastException the last exception that occurred while reading the
   * configuration of the server.
   */
  public void setLastException(TopologyCacheException lastException)
  {
    this.lastException = lastException;
  }

  /**
   * This methods updates the ADS properties (the ones that were read from
   * the ADS) with the contents of the server properties (the ones that were
   * read directly from the server).
   */
  public void updateAdsPropertiesWithServerProperties()
  {
    adsProperties.put(ADSContext.ServerProperty.HOST_NAME, getHostName());
    ServerProperty[][] sProps =
    {
        {ServerProperty.LDAP_ENABLED, ServerProperty.LDAP_PORT},
        {ServerProperty.LDAPS_ENABLED, ServerProperty.LDAPS_PORT},
        {ServerProperty.ADMIN_ENABLED, ServerProperty.ADMIN_PORT},
        {ServerProperty.JMX_ENABLED, ServerProperty.JMX_PORT},
        {ServerProperty.JMXS_ENABLED, ServerProperty.JMXS_PORT}
    };
    ADSContext.ServerProperty[][] adsProps =
    {
        {ADSContext.ServerProperty.LDAP_ENABLED,
          ADSContext.ServerProperty.LDAP_PORT},
        {ADSContext.ServerProperty.LDAPS_ENABLED,
          ADSContext.ServerProperty.LDAPS_PORT},
        {ADSContext.ServerProperty.ADMIN_ENABLED,
          ADSContext.ServerProperty.ADMIN_PORT},
        {ADSContext.ServerProperty.JMX_ENABLED,
          ADSContext.ServerProperty.JMX_PORT},
        {ADSContext.ServerProperty.JMXS_ENABLED,
          ADSContext.ServerProperty.JMXS_PORT}
    };

    for (int i=0; i<sProps.length; i++)
    {
      ArrayList<?> s = (ArrayList<?>)serverProperties.get(sProps[i][0]);
      ArrayList<?> p = (ArrayList<?>)serverProperties.get(sProps[i][1]);
      if (s != null)
      {
        int port = getPort(s, p);
        if (port == -1)
        {
          adsProperties.put(adsProps[i][0], "false");
          if (!p.isEmpty())
          {
            port = (Integer)p.iterator().next();
          }
        }
        else
        {
          adsProperties.put(adsProps[i][0], "true");
        }
        adsProperties.put(adsProps[i][1], String.valueOf(port));
      }
    }

    ArrayList<?> array = (ArrayList<?>)serverProperties.get(
        ServerProperty.STARTTLS_ENABLED);
    boolean startTLSEnabled = false;
    if (array != null && !array.isEmpty())
    {
      startTLSEnabled = Boolean.TRUE.equals(array.get(array.size() -1));
    }
    adsProperties.put(ADSContext.ServerProperty.STARTTLS_ENABLED, Boolean.toString(startTLSEnabled));
    adsProperties.put(ADSContext.ServerProperty.ID, getHostPort(true));
    adsProperties.put(ADSContext.ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE,
                      getInstancePublicKeyCertificate());
  }

  private int getPort(List<?> enabled, List<?> port)
  {
    for (int j = 0; j < enabled.size(); j++)
    {
      if (Boolean.TRUE.equals(enabled.get(j)))
      {
        return (Integer) port.get(j);
      }
    }
    return -1;
  }

  /**
   * Creates a ServerDescriptor object based on some ADS properties provided.
   * @param adsProperties the ADS properties of the server.
   * @return a ServerDescriptor object that corresponds to the provided ADS
   * properties.
   */
  public static ServerDescriptor createStandalone(
      Map<ADSContext.ServerProperty, Object> adsProperties)
  {
    ServerDescriptor desc = new ServerDescriptor();
    desc.setAdsProperties(adsProperties);
    return desc;
  }

  /**
   * Creates a ServerDescriptor object based on the configuration that we read
   * using the provided InitialLdapContext.
   * @param ctx the InitialLdapContext that will be used to read the
   * configuration of the server.
   * @param filter the topology cache filter describing the information that
   * must be retrieved.
   * @return a ServerDescriptor object that corresponds to the read
   * configuration.
   * @throws NamingException if a problem occurred reading the server
   * configuration.
   */
  public static ServerDescriptor createStandalone(InitialLdapContext ctx,
      TopologyCacheFilter filter)
  throws NamingException
  {
    ServerDescriptor desc = new ServerDescriptor();

    updateLdapConfiguration(desc, ctx);
    updateAdminConnectorConfiguration(desc, ctx);
    updateJmxConfiguration(desc, ctx);
    updateReplicas(desc, ctx, filter);
    updateReplication(desc, ctx, filter);
    updatePublicKeyCertificate(desc, ctx);
    updateMiscellaneous(desc, ctx);

    desc.serverProperties.put(ServerProperty.HOST_NAME,
        ConnectionUtils.getHostName(ctx));

    return desc;
  }

  private static void updateLdapConfiguration(ServerDescriptor desc, InitialLdapContext ctx)
      throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-enabled",
            "ds-cfg-listen-address",
            "ds-cfg-listen-port",
            "ds-cfg-use-ssl",
            "ds-cfg-allow-start-tls",
            "objectclass"
        });
    String filter = "(objectclass=ds-cfg-ldap-connection-handler)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration<SearchResult> listeners =
      ctx.search(jndiName, filter, ctls);

    try
    {
      ArrayList<Integer> ldapPorts = new ArrayList<>();
      ArrayList<Integer> ldapsPorts = new ArrayList<>();
      ArrayList<Boolean> ldapEnabled = new ArrayList<>();
      ArrayList<Boolean> ldapsEnabled = new ArrayList<>();
      ArrayList<Boolean> startTLSEnabled = new ArrayList<>();

      desc.serverProperties.put(ServerProperty.LDAP_PORT, ldapPorts);
      desc.serverProperties.put(ServerProperty.LDAPS_PORT, ldapsPorts);
      desc.serverProperties.put(ServerProperty.LDAP_ENABLED, ldapEnabled);
      desc.serverProperties.put(ServerProperty.LDAPS_ENABLED, ldapsEnabled);
      desc.serverProperties.put(ServerProperty.STARTTLS_ENABLED,
          startTLSEnabled);

      while(listeners.hasMore())
      {
        SearchResult sr = listeners.next();

        String port = getFirstValue(sr, "ds-cfg-listen-port");

        boolean isSecure = "true".equalsIgnoreCase(
            getFirstValue(sr, "ds-cfg-use-ssl"));

        boolean enabled = "true".equalsIgnoreCase(
            getFirstValue(sr, "ds-cfg-enabled"));
        final Integer portNumber = Integer.valueOf(port);
        if (isSecure)
        {
          ldapsPorts.add(portNumber);
          ldapsEnabled.add(enabled);
        }
        else
        {
          ldapPorts.add(portNumber);
          ldapEnabled.add(enabled);
          enabled = "true".equalsIgnoreCase(
              getFirstValue(sr, "ds-cfg-allow-start-tls"));
          startTLSEnabled.add(enabled);
        }
      }
    }
    finally
    {
      listeners.close();
    }
  }

  private static void updateAdminConnectorConfiguration(ServerDescriptor desc, InitialLdapContext ctx)
      throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-listen-port",
            "objectclass"
        });
    String filter = "(objectclass=ds-cfg-administration-connector)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration<SearchResult> listeners =
      ctx.search(jndiName, filter, ctls);

    try
    {
      Integer adminConnectorPort = null;

      // we should have a single administration connector
      while (listeners.hasMore()) {
        SearchResult sr = listeners.next();
        String port = getFirstValue(sr, "ds-cfg-listen-port");
        adminConnectorPort = Integer.valueOf(port);
      }

      // Even if we have a single port, use an array to be consistent with
      // other protocols.
      ArrayList<Integer> adminPorts = new ArrayList<>();
      ArrayList<Boolean> adminEnabled = new ArrayList<>();
      if (adminConnectorPort != null)
      {
        adminPorts.add(adminConnectorPort);
        adminEnabled.add(Boolean.TRUE);
      }
      desc.serverProperties.put(ServerProperty.ADMIN_PORT, adminPorts);
      desc.serverProperties.put(ServerProperty.ADMIN_ENABLED, adminEnabled);
    }
    finally
    {
      listeners.close();
    }
  }

  private static void updateJmxConfiguration(ServerDescriptor desc, InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-enabled",
            "ds-cfg-listen-address",
            "ds-cfg-listen-port",
            "ds-cfg-use-ssl",
            "objectclass"
        });
    String filter = "(objectclass=ds-cfg-jmx-connection-handler)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration<SearchResult> listeners =
      ctx.search(jndiName, filter, ctls);

    ArrayList<Integer> jmxPorts = new ArrayList<>();
    ArrayList<Integer> jmxsPorts = new ArrayList<>();
    ArrayList<Boolean> jmxEnabled = new ArrayList<>();
    ArrayList<Boolean> jmxsEnabled = new ArrayList<>();

    desc.serverProperties.put(ServerProperty.JMX_PORT, jmxPorts);
    desc.serverProperties.put(ServerProperty.JMXS_PORT, jmxsPorts);
    desc.serverProperties.put(ServerProperty.JMX_ENABLED, jmxEnabled);
    desc.serverProperties.put(ServerProperty.JMXS_ENABLED, jmxsEnabled);

    try
    {
      while(listeners.hasMore())
      {
        SearchResult sr = listeners.next();

        String port = getFirstValue(sr, "ds-cfg-listen-port");

        boolean isSecure = "true".equalsIgnoreCase(
            getFirstValue(sr, "ds-cfg-use-ssl"));

        boolean enabled = "true".equalsIgnoreCase(
            getFirstValue(sr, "ds-cfg-enabled"));
        Integer portNumber = Integer.valueOf(port);
        if (isSecure)
        {
          jmxsPorts.add(portNumber);
          jmxsEnabled.add(enabled);
        }
        else
        {
          jmxPorts.add(portNumber);
          jmxEnabled.add(enabled);
        }
      }
    }
    finally
    {
      listeners.close();
    }
  }

  private static void updateReplicas(ServerDescriptor desc,
      InitialLdapContext ctx, TopologyCacheFilter cacheFilter)
  throws NamingException
  {
    if (!cacheFilter.searchBaseDNInformation())
    {
      return;
    }
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-base-dn",
            "ds-cfg-backend-id",
            ConfigConstants.ATTR_OBJECTCLASS
        });
    String filter = "(objectclass=ds-cfg-backend)";

    LdapName jndiName = new LdapName("cn=config");
    NamingEnumeration<SearchResult> databases =
      ctx.search(jndiName, filter, ctls);

    try
    {
      while(databases.hasMore())
      {
        SearchResult sr = databases.next();

        String id = getFirstValue(sr, "ds-cfg-backend-id");

        if (!isConfigBackend(id) || isSchemaBackend(id))
        {
          Set<String> baseDns = getValues(sr, "ds-cfg-base-dn");

          Set<String> entries;
          if (cacheFilter.searchMonitoringInformation())
          {
            entries = getBaseDNEntryCount(ctx, id);
          }
          else
          {
            entries = new HashSet<>();
          }

          Set<ReplicaDescriptor> replicas = desc.getReplicas();
          for (String baseDn : baseDns)
          {
            if (isAddReplica(cacheFilter, baseDn))
            {
              SuffixDescriptor suffix = new SuffixDescriptor();
              suffix.setDN(baseDn);
              ReplicaDescriptor replica = new ReplicaDescriptor();
              replica.setServer(desc);
              replica.setObjectClasses(getValues(sr, ConfigConstants.ATTR_OBJECTCLASS));
              replica.setBackendName(id);
              replicas.add(replica);
              HashSet<ReplicaDescriptor> r = new HashSet<>();
              r.add(replica);
              suffix.setReplicas(r);
              replica.setSuffix(suffix);
              int nEntries = -1;
              for (String s : entries)
              {
                int index = s.indexOf(" ");
                if (index != -1)
                {
                  String dn = s.substring(index + 1);
                  if (areDnsEqual(baseDn, dn))
                  {
                    try
                    {
                      nEntries = Integer.parseInt(s.substring(0, index));
                    }
                    catch (Throwable t)
                    {
                      /* Ignore */
                    }
                    break;
                  }
                }
              }
              replica.setEntries(nEntries);
            }
          }
          desc.setReplicas(replicas);
        }
      }
    }
    finally
    {
      databases.close();
    }
  }

  private static boolean isAddReplica(TopologyCacheFilter cacheFilter, String baseDn)
  {
    if (cacheFilter.searchAllBaseDNs())
    {
      return true;
    }

    for (String dn : cacheFilter.getBaseDNsToSearch())
    {
      if (areDnsEqual(dn, baseDn))
      {
        return true;
      }
    }
    return false;
  }

  private static void updateReplication(ServerDescriptor desc,
      InitialLdapContext ctx, TopologyCacheFilter cacheFilter)
  throws NamingException
  {
    boolean replicationEnabled = false;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-cfg-enabled"
        });
    String filter = "(objectclass=ds-cfg-synchronization-provider)";

    LdapName jndiName = new LdapName(
      "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");
    NamingEnumeration<SearchResult> syncProviders = null;

    try
    {
      syncProviders = ctx.search(jndiName, filter, ctls);

      while(syncProviders.hasMore())
      {
        SearchResult sr = syncProviders.next();

        if ("true".equalsIgnoreCase(getFirstValue(sr,
          "ds-cfg-enabled")))
        {
          replicationEnabled = true;
        }
      }
    }
    catch (NameNotFoundException nse)
    {
      /* ignore */
    }
    finally
    {
      if (syncProviders != null)
      {
        syncProviders.close();
      }
    }
    desc.serverProperties.put(ServerProperty.IS_REPLICATION_ENABLED,
        Boolean.valueOf(replicationEnabled));

    Set<String> allReplicationServers = new LinkedHashSet<>();

    if (cacheFilter.searchBaseDNInformation())
    {
      ctls = new SearchControls();
      ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      ctls.setReturningAttributes(
          new String[] {
              "ds-cfg-base-dn",
              "ds-cfg-replication-server",
              "ds-cfg-server-id"
          });
      filter = "(objectclass=ds-cfg-replication-domain)";

      jndiName = new LdapName(
      "cn=Multimaster Synchronization,cn=Synchronization Providers,cn=config");

      syncProviders = null;
      try
      {
        syncProviders = ctx.search(jndiName, filter, ctls);

        while(syncProviders.hasMore())
        {
          SearchResult sr = syncProviders.next();

          int id = Integer.parseInt(
              getFirstValue(sr, "ds-cfg-server-id"));
          Set<String> replicationServers = getValues(sr,
          "ds-cfg-replication-server");
          Set<String> dns = getValues(sr, "ds-cfg-base-dn");
          for (String dn : dns)
          {
            for (ReplicaDescriptor replica : desc.getReplicas())
            {
              if (areDnsEqual(replica.getSuffix().getDN(), dn))
              {
                replica.setReplicationId(id);
                // Keep the values of the replication servers in lower case
                // to make use of Sets as String simpler.
                LinkedHashSet<String> repServers = new LinkedHashSet<>();
                for (String s: replicationServers)
                {
                  repServers.add(s.toLowerCase());
                }
                replica.setReplicationServers(repServers);
                allReplicationServers.addAll(repServers);
              }
            }
          }
        }
      }
      catch (NameNotFoundException nse)
      {
        /* ignore */
      }
      finally
      {
        if (syncProviders != null)
        {
          syncProviders.close();
        }
      }
    }

    ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    ctls.setReturningAttributes(
    new String[] {
      "ds-cfg-replication-port", "ds-cfg-replication-server",
      "ds-cfg-replication-server-id"
    });
    filter = "(objectclass=ds-cfg-replication-server)";

    jndiName = new LdapName("cn=Multimaster "+
        "Synchronization,cn=Synchronization Providers,cn=config");

    desc.serverProperties.put(ServerProperty.IS_REPLICATION_SERVER,
        Boolean.FALSE);
    NamingEnumeration<SearchResult> entries = null;
    try
    {
      entries = ctx.search(jndiName, filter, ctls);

      while (entries.hasMore())
      {
        SearchResult sr = entries.next();

        desc.serverProperties.put(ServerProperty.IS_REPLICATION_SERVER,
            Boolean.TRUE);
        String v = getFirstValue(sr, "ds-cfg-replication-port");
        desc.serverProperties.put(ServerProperty.REPLICATION_SERVER_PORT,
            Integer.parseInt(v));
        v = getFirstValue(sr, "ds-cfg-replication-server-id");
        desc.serverProperties.put(ServerProperty.REPLICATION_SERVER_ID,
            Integer.parseInt(v));
        Set<String> values = getValues(sr, "ds-cfg-replication-server");
        // Keep the values of the replication servers in lower case
        // to make use of Sets as String simpler.
        LinkedHashSet<String> repServers = new LinkedHashSet<>();
        for (String s: values)
        {
          repServers.add(s.toLowerCase());
        }
        allReplicationServers.addAll(repServers);
        desc.serverProperties.put(ServerProperty.EXTERNAL_REPLICATION_SERVERS,
            allReplicationServers);
      }
    }
    catch (NameNotFoundException nse)
    {
      /* ignore */
    }
    finally
    {
      if (entries != null)
      {
        entries.close();
      }
    }

    boolean replicationSecure = false;
    if (replicationEnabled)
    {
      ctls = new SearchControls();
      ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
      ctls.setReturningAttributes(
      new String[] {"ds-cfg-ssl-encryption"});
      filter = "(objectclass=ds-cfg-crypto-manager)";

      jndiName = new LdapName("cn=Crypto Manager,cn=config");

      entries = ctx.search(jndiName, filter, ctls);

      try
      {
        while (entries.hasMore())
        {
          SearchResult sr = entries.next();

          String v = getFirstValue(sr, "ds-cfg-ssl-encryption");
          replicationSecure = "true".equalsIgnoreCase(v);
        }
      }
      finally
      {
        entries.close();
      }
    }
    desc.serverProperties.put(ServerProperty.IS_REPLICATION_SECURE,
        Boolean.valueOf(replicationSecure));
  }

  /**
   Updates the instance key public-key certificate value of this context from
   the local truststore of the instance bound by this context. Any current
   value of the certificate is overwritten. The intent of this method is to
   retrieve the instance-key public-key certificate when this context is bound
   to an instance, and cache it for later use in registering the instance into
   ADS.
   @param desc The map to update with the instance key-pair public-key
   certificate.
   @param ctx The bound server instance.
   @throws NamingException if unable to retrieve certificate from bound
   instance.
   */
  private static void updatePublicKeyCertificate(ServerDescriptor desc, InitialLdapContext ctx) throws NamingException
  {
    /* TODO: this DN is declared in some core constants file. Create a constants
       file for the installer and import it into the core. */
    final String dnStr = "ds-cfg-key-id=ads-certificate,cn=ads-truststore";
    final LdapName dn = new LdapName(dnStr);
    for (int i = 0; i < 2 ; ++i) {
      /* If the entry does not exist in the instance's truststore backend, add
         it (which induces the CryptoManager to create the public-key
         certificate attribute), then repeat the search. */
      try {
        final SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
        final String attrIDs[] = { "ds-cfg-public-key-certificate;binary" };
        searchControls.setReturningAttributes(attrIDs);
        final SearchResult certEntry = ctx.search(dn,
                   "(objectclass=ds-cfg-instance-key)", searchControls).next();
        final Attribute certAttr = certEntry.getAttributes().get(attrIDs[0]);
        if (null != certAttr) {
          /* attribute ds-cfg-public-key-certificate is a MUST in the schema */
          desc.serverProperties.put(
                  ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE,
                  certAttr.get());
        }
        break;
      }
      catch (NameNotFoundException x) {
        if (0 == i) {
          // Poke CryptoManager to initialize truststore. Note the special attribute in the request.
          final Attributes attrs = new BasicAttributes();
          final Attribute oc = new BasicAttribute("objectclass");
          oc.add("top");
          oc.add("ds-cfg-self-signed-cert-request");
          attrs.put(oc);
          ctx.createSubcontext(dn, attrs).close();
        }
        else {
          throw x;
        }
      }
    }
  }

  private static void updateMiscellaneous(ServerDescriptor desc, InitialLdapContext ctx) throws NamingException
  {
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-sync-generation-id"
        });
    String filter = "(|(objectclass=*)(objectclass=ldapsubentry))";

    LdapName jndiName = new LdapName("cn=schema");
    NamingEnumeration<SearchResult> listeners =
      ctx.search(jndiName, filter, ctls);

    try
    {
      while(listeners.hasMore())
      {
        SearchResult sr = listeners.next();

        desc.serverProperties.put(ServerProperty.SCHEMA_GENERATION_ID,
            getFirstValue(sr, "ds-sync-generation-id"));
      }
    }
    finally
    {
      listeners.close();
    }
  }

  /**
   Seeds the bound instance's local ads-truststore with a set of instance
   key-pair public key certificates. The result is the instance will trust any
   instance possessing the private key corresponding to one of the public-key
   certificates. This trust is necessary at least to initialize replication,
   which uses the trusted certificate entries in the ads-truststore for server
   authentication.
   @param ctx The bound instance.
   @param keyEntryMap The set of valid (i.e., not tagged as compromised)
   instance key-pair public-key certificate entries in ADS represented as a map
   from keyID to public-key certificate (binary).
   @throws NamingException in case an error occurs while updating the instance's
   ads-truststore via LDAP.
   */
  public static void seedAdsTrustStore(
          InitialLdapContext ctx,
          Map<String, byte[]> keyEntryMap)
          throws NamingException
  {
    /* TODO: this DN is declared in some core constants file. Create a
       constants file for the installer and import it into the core. */
    final Attribute oc = new BasicAttribute("objectclass");
    oc.add("top");
    oc.add("ds-cfg-instance-key");
    for (Map.Entry<String, byte[]> keyEntry : keyEntryMap.entrySet()){
      final BasicAttributes keyAttrs = new BasicAttributes();
      keyAttrs.put(oc);
      final Attribute rdnAttr = new BasicAttribute(
              ADSContext.ServerProperty.INSTANCE_KEY_ID.getAttributeName(),
              keyEntry.getKey());
      keyAttrs.put(rdnAttr);
      keyAttrs.put(new BasicAttribute(
              ADSContext.ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE.
                      getAttributeName() + ";binary", keyEntry.getValue()));
      final LdapName keyDn = new LdapName(rdnAttr.getID() + "=" + Rdn.escapeValue(rdnAttr.get()) + "," + TRUSTSTORE_DN);
      try {
        ctx.createSubcontext(keyDn, keyAttrs).close();
      }
      catch(NameAlreadyBoundException x){
        ctx.destroySubcontext(keyDn);
        ctx.createSubcontext(keyDn, keyAttrs).close();
      }
    }
  }

  /**
   * Cleans up the contents of the ads truststore.
   *
   * @param ctx the bound instance.
   * @throws NamingException in case an error occurs while updating the
   * instance's ads-truststore via LDAP.
   */
  public static void cleanAdsTrustStore(InitialLdapContext ctx)
  throws NamingException
  {
    try
    {
      SearchControls sc = new SearchControls();
      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      sc.setReturningAttributes(new String[] { SchemaConstants.NO_ATTRIBUTES });
      NamingEnumeration<SearchResult> ne = ctx.search(TRUSTSTORE_DN,
          "(objectclass=ds-cfg-instance-key)", sc);
      ArrayList<String> dnsToDelete = new ArrayList<>();
      try
      {
        while (ne.hasMore())
        {
          SearchResult sr = ne.next();
          dnsToDelete.add(sr.getName()+","+TRUSTSTORE_DN);
        }
      }
      finally
      {
        ne.close();
      }
      for (String dn : dnsToDelete)
      {
        ctx.destroySubcontext(dn);
      }
    }
    catch (NameNotFoundException nnfe)
    {
      // Ignore
      logger.warn(LocalizableMessage.raw("Error cleaning truststore: "+nnfe, nnfe));
    }
  }

  /**
   * Returns the values of the ds-base-dn-entry count attributes for the given
   * backend monitor entry using the provided InitialLdapContext.
   * @param ctx the InitialLdapContext to use to update the configuration.
   * @param backendID the id of the backend.
   * @return the values of the ds-base-dn-entry count attribute.
   * @throws NamingException if there was an error.
   */
  private static Set<String> getBaseDNEntryCount(InitialLdapContext ctx,
      String backendID) throws NamingException
  {
    LinkedHashSet<String> v = new LinkedHashSet<>();
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "ds-base-dn-entry-count"
        });
    String filter = "(ds-backend-id="+backendID+")";

    LdapName jndiName = new LdapName("cn=monitor");
    NamingEnumeration<SearchResult> listeners =
      ctx.search(jndiName, filter, ctls);

    try
    {
      while(listeners.hasMore())
      {
        SearchResult sr = listeners.next();

        v.addAll(getValues(sr, "ds-base-dn-entry-count"));
      }
    }
    finally
    {
      listeners.close();
    }
    return v;
  }

  /**
   * An convenience method to know if the provided ID corresponds to a
   * configuration backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to a configuration
   * backend and <CODE>false</CODE> otherwise.
   */
  private static boolean isConfigBackend(String id)
  {
    return "tasks".equalsIgnoreCase(id) ||
    "schema".equalsIgnoreCase(id) ||
    "config".equalsIgnoreCase(id) ||
    "monitor".equalsIgnoreCase(id) ||
    "backup".equalsIgnoreCase(id) ||
    "ads-truststore".equalsIgnoreCase(id);
  }

  /**
   * An convenience method to know if the provided ID corresponds to the schema
   * backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to the schema backend
   * and <CODE>false</CODE> otherwise.
   */
  private static boolean isSchemaBackend(String id)
  {
    return "schema".equalsIgnoreCase(id);
  }

  /**
   * Returns the replication server normalized String for a given host name
   * and replication port.
   * @param hostName the host name.
   * @param replicationPort the replication port.
   * @return the replication server normalized String for a given host name
   * and replication port.
   */
  public static String getReplicationServer(String hostName, int replicationPort)
  {
    return getServerRepresentation(hostName, replicationPort);
  }

  /**
   * Returns the normalized server representation for a given host name and
   * port.
   * @param hostName the host name.
   * @param port the port.
   * @return the normalized server representation for a given host name and
   * port.
   */
  public static String getServerRepresentation(String hostName, int port)
  {
    return hostName.toLowerCase() + ":" + port;
  }

  /**
   * Returns a representation of a base DN for a set of servers.
   * @param baseDN the base DN.
   * @param servers the servers.
   * @return a representation of a base DN for a set of servers.
   */
  public static String getSuffixDisplay(String baseDN,
      Set<ServerDescriptor> servers)
  {
    StringBuilder sb = new StringBuilder();
    sb.append(baseDN);
    for (ServerDescriptor server : servers)
    {
      sb.append(Constants.LINE_SEPARATOR).append("    ");
      sb.append(server.getHostPort(true));
    }
    return sb.toString();
  }

  /**
   * Tells whether the provided server descriptor represents the same server
   * as this object.
   * @param server the server to make the comparison.
   * @return whether the provided server descriptor represents the same server
   * as this object or not.
   */
  public boolean isSameServer(ServerDescriptor server)
  {
    return getId().equals(server.getId());
  }
}

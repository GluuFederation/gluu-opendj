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
package org.opends.server.protocols.jmx;



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.JMXConnectionHandlerCfgDefn;
import org.opends.server.admin.std.server.JMXConnectionHandlerCfg;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * A simple test for : - JMX connection establishment withou using SSL -
 * JMX get and set - configuration change
 */
public class JmxConnectTest extends JmxTestCase {
  
  /**
   * Set up the environment for performing the tests in this suite.
   * 
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();
    
    TestCaseUtils.addEntries(
        "dn: cn=Privileged User,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: Privileged User",
        "givenName: Privileged",
        "sn: User",
        "uid: privileged.user",
        "userPassword: password",
        "ds-privilege-name: config-read",
        "ds-privilege-name: config-write",
        "ds-privilege-name: password-reset",
        "ds-privilege-name: update-schema",
        "ds-privilege-name: ldif-import",
        "ds-privilege-name: ldif-export",
        "ds-privilege-name: backend-backup",
        "ds-privilege-name: backend-restore",
        "ds-privilege-name: proxied-auth",
        "ds-privilege-name: bypass-acl",
        "ds-privilege-name: unindexed-search",
        "ds-privilege-name: jmx-read",
        "ds-privilege-name: jmx-write",
        "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
             "cn=Password Policies,cn=config",
        "",
        "dn: cn=Unprivileged JMX User,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: Privileged User",
        "givenName: Privileged",
        "sn: User",
        "uid: privileged.user",
        "userPassword: password",
        "ds-privilege-name: config-read",
        "ds-privilege-name: config-write",
        "ds-privilege-name: password-reset",
        "ds-privilege-name: update-schema",
        "ds-privilege-name: ldif-import",
        "ds-privilege-name: ldif-export",
        "ds-privilege-name: backend-backup",
        "ds-privilege-name: backend-restore",
        "ds-privilege-name: proxied-auth",
        "ds-privilege-name: bypass-acl",
        "ds-privilege-name: unindexed-search",
        "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
             "cn=Password Policies,cn=config");
  }
  
  
  /**
   * Clean up the environment after performing the tests in this suite.
   * 
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  public void afterClass() throws Exception
  {
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    DeleteOperation deleteOperation = conn.processDelete(DN
        .decode("cn=Privileged User,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    
    deleteOperation = conn.processDelete(DN
        .decode("cn=Unprivileged JMX User,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    
  }
  
  

  /**
   * Build data for the simpleConnect test.
   *
   * @return the data.
   */
  @DataProvider(name = "simpleConnect")
  Object[][] createCredentials() {
    return new Object[][] {
        { "cn=directory manager", "password", false }, // no JMX_READ privilege
        { "cn=Privileged User,o=test", "password", true },
        { "cn=Privileged User,o=test", "wrongPassword", false },
        { "cn=wrong user", "password", false },
        { "invalid DN", "password", false },
        { "cn=Privileged User,o=test", null, false },
        { null, "password", false }, { null, null, false }, };
  }



  /**
   * Check that simple (no SSL) connections to the JMX service are
   * accepted when the given credentials are OK and refused when the
   * credentials are invalid.
   */
  @Test(enabled = false, dataProvider = "simpleConnect")
  public void simpleConnect(String user, String password,
      boolean expectedResult) throws Exception {
    OpendsJmxConnector connector = connect(user, password,
        TestCaseUtils.getServerJmxPort());
    assertEquals((connector != null), expectedResult);
    if (connector != null) {
      connector.close();
    }
  }
  
  /**
   * Build some data for the simpleGet test.
   */
  @DataProvider(name = "simpleGet")
  Object[][] createNames() {
    return new Object[][] {
        {
            "cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
            "ds-cfg-listen-port", TestCaseUtils.getServerJmxPort() },
        {
            "cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
            "objectclass", null },
        {
            "cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
            "ds-cfg-ssl-cert-nickname", "server-cert" },
    // not working at the moment see issue 655
    // {"cn=JE
    // Database,ds-cfg-backend-id=userRoot,cn=Backends,cn=config",
    // "ds-cfg-db-cache-percent", 10},
    };
  }



  /**
   * Test simple JMX get.
   */
  @Test(enabled = false, dataProvider = "simpleGet")
  public void simpleGet(String dn, String attributeName, Object value)
      throws Exception {

    OpendsJmxConnector connector = connect("cn=Privileged User,o=test",
        "password", TestCaseUtils.getServerJmxPort());
    MBeanServerConnection jmxc = connector.getMBeanServerConnection();
    assertNotNull(jmxc);

    Object val = jmxGet(dn, attributeName, jmxc);

    if (value != null) {
      assertEquals(val, value);
    } else {
      assertTrue(val == null);
    }
    connector.close();
  }



  /**
   * Test setting some config attribute through jmx.
   */
  // NOTE:  This test is disabled because JMX interaction is not allowed with
  //        the admin framework, and the cn=config entry is now governed by it.
  @Test(enabled = false)
  public void simpleSet() throws Exception {
    OpendsJmxConnector connector = connect("cn=Privileged User,o=test",
        "password", TestCaseUtils.getServerJmxPort());
    MBeanServerConnection jmxc = connector.getMBeanServerConnection();
    assertNotNull(jmxc);

    Set names = jmxc.queryNames(null, null);
    names.clear();

    final String dn = "cn=config";
    final String attribute = "ds-cfg-size-limit";

    Long val = (Long) jmxGet(dn, attribute, jmxc);

    jmxSet(dn, attribute, val + 1, jmxc);

    Long newVal = (Long) jmxGet(dn, attribute, jmxc);

    assertEquals((long) newVal, (long) val + 1);

    jmxSet(dn, attribute, val + 1, jmxc);

    connector.close();
  }



  /**
   * Test that disabling JMX connection handler does its job by -
   * opening a JMX connection - changing the JMX connection handler
   * state to disable - trying to open a new JMX connection and check
   * that it fails.
   *
   * @throws Exception
   */
  @Test(enabled = false)
  public void disable() throws Exception {

    // Create a new JMX connector for this test.
    // This will allow to use the old one if this test fails.
    //
    ServerSocket serverJmxSocket = new ServerSocket();
    serverJmxSocket.setReuseAddress(true);
    serverJmxSocket.bind(new InetSocketAddress("127.0.0.1", 0));
    int serverJmxPort = serverJmxSocket.getLocalPort();
    serverJmxSocket.close();
    Entry newJmxConnectionJmx = TestCaseUtils
        .makeEntry(
            "dn: cn= New JMX Connection Handler,cn=Connection Handlers,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-connection-handler",
            "objectClass: ds-cfg-jmx-connection-handler",
            "ds-cfg-ssl-cert-nickname: server-cert",
            "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
            "ds-cfg-enabled: true",
            "ds-cfg-use-ssl: false", "ds-cfg-listen-port: "
                + serverJmxPort, "cn: JMX Connection Handler");
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();
    AddOperationBasis addOp = new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        newJmxConnectionJmx.getDN(), newJmxConnectionJmx
            .getObjectClasses(), newJmxConnectionJmx
            .getUserAttributes(), newJmxConnectionJmx
            .getOperationalAttributes());
    addOp.run();
    Thread.sleep(200);
    OpendsJmxConnector newJmxConnector = connect(
        "cn=Privileged User,o=test", "password", serverJmxPort);
    assertNotNull(newJmxConnector);
    newJmxConnector.close();

    // Get the "old" connector
    OpendsJmxConnector connector = connect("cn=Privileged User,o=test",
        "password", TestCaseUtils.getServerJmxPort());
    MBeanServerConnection jmxc = connector.getMBeanServerConnection();
    assertNotNull(jmxc);

    // Disable the "new" connector
    toggleEnableJmxConnector(connector, newJmxConnectionJmx.getDN(),
        false);
    Thread.sleep(100);
    OpendsJmxConnector jmxcDisabled = connect("cn=Privileged User,o=test",
        "password", serverJmxPort);
    assertNull(jmxcDisabled);

    toggleEnableJmxConnector(connector, newJmxConnectionJmx.getDN(),
        true);
    Thread.sleep(100);
    jmxcDisabled = connect("cn=Privileged User,o=test", "password",
        serverJmxPort);
    assertNotNull(jmxcDisabled);

    // cleanup client connection
    connector.close();
    jmxcDisabled.close();
    DeleteOperationBasis delOp = new DeleteOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        newJmxConnectionJmx.getDN());
    delOp.run();
  }



  /**
   * Test changing JMX port through LDAP
   *
   * @throws Exception
   */
  @Test(enabled = false)
  public void changePort() throws Exception {
    // Connect to the JMX service and get the current port
    final String dn = "cn=JMX Connection Handler,cn=Connection Handlers,cn=config";
    final String attribute = "ds-cfg-listen-port";

    OpendsJmxConnector connector = connect("cn=Privileged User,o=test",
        "password", TestCaseUtils.getServerJmxPort());
    MBeanServerConnection jmxc = connector.getMBeanServerConnection();
    assertNotNull(jmxc);

    // use JMX to get the current value of the JMX port number
    Long initJmxPort = (Long) jmxGet(dn, attribute, jmxc);
    connector.close();
    assertNotNull(initJmxPort);

    // change the configuration of the connection handler to use
    // a free port
    ServerSocket serverJmxSocket = TestCaseUtils.bindFreePort();
    int serverJmxPort = serverJmxSocket.getLocalPort();

    Entry entry = TestCaseUtils
            .makeEntry(
                "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-connection-handler",
                "objectClass: ds-cfg-jmx-connection-handler",
                "ds-cfg-ssl-cert-nickname: server-cert",
                "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
                "ds-cfg-enabled: true",
                "ds-cfg-use-ssl: false", "ds-cfg-listen-port: "
                    + serverJmxPort, "cn: JMX Connection Handler");
    serverJmxSocket.close();
    configureJmx(entry);

    // connect the the JMX service using the new port
    connector = connect("cn=Privileged User,o=test", "password",
        serverJmxPort);
    jmxc = connector.getMBeanServerConnection();
    assertNotNull(jmxc);
    Long val = (Long) jmxGet(dn, attribute, jmxc);
    assertEquals((long) val, (long) serverJmxPort);
    connector.close();

    // re-establish the initial configuration of the JMX service
    entry = TestCaseUtils
            .makeEntry(
                "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-connection-handler",
                "objectClass: ds-cfg-jmx-connection-handler",
                "ds-cfg-ssl-cert-nickname: server-cert",
                "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
                "ds-cfg-enabled: true",
                "ds-cfg-use-ssl: false", "ds-cfg-listen-port: "
                    + initJmxPort, "cn: JMX Connection Handler");

    configureJmx(entry);

    // Check that the old port is ok
    connector = connect("cn=Privileged User,o=test", "password",
        TestCaseUtils.getServerJmxPort());
    jmxc = connector.getMBeanServerConnection();
    assertNotNull(jmxc);
  }



  /**
   * Check that simple (no SSL) connections to the JMX service are
   * accepted when the given credentials are OK and refused when the
   * credentials are invalid.
   */
  @Test(enabled = false) // this fails a lot especially due to the in core restarts
  public void sslConnect() throws Exception {
    // Enable SSL by setting ds-cfg-use-ssl boolean and the
    // certificate alias using ds-cfg-ssl-cert-nickname attribute.
    int initJmxPort = TestCaseUtils.getServerJmxPort();

    Entry entry = TestCaseUtils
        .makeEntry(
            "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-connection-handler",
            "objectClass: ds-cfg-jmx-connection-handler",
            "ds-cfg-ssl-cert-nickname: server-cert",
            "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
            "ds-cfg-enabled: true",
            "ds-cfg-use-ssl: true",
            "ds-cfg-listen-port: " + initJmxPort,
            "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
            "cn: JMX Connection Handler");

    configureJmx(entry);

    OpendsJmxConnector jmxc = sslConnect("cn=Privileged User,o=test",
        "password", initJmxPort);
    assertNotNull(jmxc,"OpendsJmxConnector shouldn't be null");
    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
    jmxc.close();

    // Before returning the result,
    // disable SSL by setting ds-cfg-use-ssl boolean
    entry = TestCaseUtils
            .makeEntry(
                "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-connection-handler",
                "objectClass: ds-cfg-jmx-connection-handler",
                "ds-cfg-ssl-cert-nickname: server-cert",
                "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
                "ds-cfg-enabled: true",
                "ds-cfg-use-ssl: false", "ds-cfg-listen-port: "
                    + initJmxPort, "cn: JMX Connection Handler");
    try {
      configureJmx(entry);
    } catch (RuntimeException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    jmxc = connect("cn=Privileged User,o=test", "password", initJmxPort);
    jmxc.close();
    assertNotNull(jmxc);
  }



  /**
   * @param config
   * @throws Exception
   */
  private void configureJmx(Entry entry) throws Exception {
    ArrayList<Message> reasons = new ArrayList<Message>();

    // Get the Jmx connection handler from the core server
    JmxConnectionHandler jmxConnectionHandler = getJmxConnectionHandler();
    assertNotNull(jmxConnectionHandler);

    JMXConnectionHandlerCfg config = AdminTestCaseUtils
        .getConfiguration(JMXConnectionHandlerCfgDefn
            .getInstance(), entry);

    if (!jmxConnectionHandler.isConfigurationChangeAcceptable(config,
        reasons)) {
      fail("unacceptable JMX configuration" + reasons);
    }

    ConfigChangeResult configResult = jmxConnectionHandler
        .applyConfigurationChange(config);

    assertEquals(configResult.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Connect to the JMX service.
   */
  private OpendsJmxConnector connect(String user, String password,
      long jmxPort) throws MalformedURLException, IOException{
    HashMap<String, Object> env = new HashMap<String, Object>();

    // Provide the credentials required by the server to successfully
    // perform user authentication
    //
    String[] credentials;
    if ((user == null) && (password == null)) {
      credentials = null;
    } else
      credentials = new String[] { user, password };
    env.put("jmx.remote.credentials", credentials);

    env.put("jmx.remote.x.client.connection.check.period", 0);

    // Create an RMI connector client and
    // connect it to the RMI connector server
    //
    OpendsJmxConnector opendsConnector;
    try {
      opendsConnector = new OpendsJmxConnector("localhost",
          (int) jmxPort, env);
      opendsConnector.connect();
      return opendsConnector;
    } catch (SecurityException e) {
      return null;
    } catch (IOException e) {
      return null;
    }
  }



  /**
   * Connect to the JMX service using SSL.
   */
  private OpendsJmxConnector sslConnect(String user, String password,
      long jmxPort) throws Exception {
    HashMap<String, Object> env = new HashMap<String, Object>();

    // Provide the credentials required by the server to successfully
    // perform user authentication
    String[] credentials;
    if ((user == null) && (password == null)) {
      credentials = null;
    } else
      credentials = new String[] { user, password };
    env.put("jmx.remote.credentials", credentials);

    // Provide the Trust manager.
    KeyStore ks = null;
    ks = KeyStore.getInstance("JKS");
    FileInputStream keyStoreFile = new FileInputStream(
        getJmxKeystorePath());
    ks.load(keyStoreFile, "password".toCharArray());
    keyStoreFile.close();

    TrustManagerFactory tmf = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ks);
    TrustManager tms[] = tmf.getTrustManagers();
    env.put(JmxConnectionHandler.TRUST_MANAGER_ARRAY_KEY, tms);

    // Create an RMI connector client and
    // connect it to the RMI connector server
    OpendsJmxConnector opendsConnector;
    try {
      opendsConnector = new OpendsJmxConnector("localhost",
          (int) jmxPort, env);
      opendsConnector.connect();
      return opendsConnector;
    } catch (Exception e) {

      return null;
    }

  }



  /**
   * @return
   */
  private String getJmxKeystorePath() {
    return DirectoryServer.getInstanceRoot() + File.separator + "config"
        + File.separator + "server.keystore";
  }



  /**
   * Set the enabled config attribute for a JMX connector thorugh JMX
   * operation.
   *
   * @param jmxc
   *          connector to use for the interaction
   * @param testedConnector
   *          The DN of the connector the test
   * @param enabled
   *          the value of the enabled config attribute
   */
  private void toggleEnableJmxConnector(OpendsJmxConnector jmxc,
      DN testedConnector, boolean enabled) throws Exception {
    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

    // Get status of the JMX connection handler
    String jmxName = JMXMBean.getJmxName(testedConnector);
    ObjectName name = ObjectName.getInstance(jmxName);
    Attribute status = (Attribute) mbsc.getAttribute(name,
        "ds-cfg-enabled");
    if (status != null)
      status.getValue();
    Attribute attr = new Attribute(
        "ds-cfg-enabled", enabled);
    mbsc.setAttribute(name, attr);
    status = (Attribute) mbsc.getAttribute(name,
        "ds-cfg-enabled");

    status = null;
  }



  /**
   * Get an attribute value through JMX.
   */
  private Object jmxGet(String dn, String attributeName,
      MBeanServerConnection mbsc) throws Exception {
    String jmxName = JMXMBean.getJmxName(DN.decode(dn));
    ObjectName name = ObjectName.getInstance(jmxName);

    try
    {
      Attribute status = (Attribute) mbsc.getAttribute(name, attributeName);
      if (status == null)
        return null;
      else
        return status.getValue();
    }
    catch (AttributeNotFoundException anfe)
    {
      return null;
    }
  }



  /**
   * Set an attribute value through JMX.
   */
  private void jmxSet(String dn, String attributeName, Object value,
      MBeanServerConnection mbsc) throws Exception {
    String jmxName = JMXMBean.getJmxName(DN.decode(dn));
    ObjectName name = ObjectName.getInstance(jmxName);
    Attribute attr = new Attribute(attributeName, value);

    mbsc.setAttribute(name, attr);
  }

}

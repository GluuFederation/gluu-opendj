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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.tools.LDAPAuthenticationHandler;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the "Who Am I?" extended operation.
 */
public class WhoAmIExtendedOperationTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the use of the Who Am I? extended operation with an internal
   * connection authenticated as a root user.
   */
  @Test
  public void testAsInternalRootUser()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(extOp.getResponseValue());
  }



  /**
   * Tests the use of the Who Am I? extended operation with an internal
   * unauthenticated connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsInternalAnonymous()
         throws Exception
  {
    InternalClientConnection conn = new InternalClientConnection(DN.rootDN());
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(extOp.getResponseValue());
  }



  /**
   * Tests the use of the Who Am I? extended operation with an internal
   * connection authenticated as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsInternalNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry e = TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");


    InternalClientConnection conn = new InternalClientConnection(new AuthenticationInfo(e, false));
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(extOp.getResponseValue());
  }



  /**
   * Tests the use of the Who Am I? extended operation with an LDAP connection
   * authenticated as a root user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsLDAPRootUser()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    authHandler.doSimpleBind(3, ByteString.valueOf("cn=Directory Manager"),
                             ByteString.valueOf("password"),
                             new ArrayList<Control>(),
                             new ArrayList<Control>());
    ByteString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeMessage(unbindMessage);
    s.close();
  }



  /**
   * Tests the use of the Who Am I? extended operation with an unauthenticated
   * LDAP connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsLDAPAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    ByteString authzID = authHandler.requestAuthorizationIdentity();
    assertNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeMessage(unbindMessage);
    s.close();
  }



  /**
   * Tests the use of the Who Am I? extended operation with an LDAP connection
   * authenticated as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsLDAPNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    authHandler.doSimpleBind(3, ByteString.valueOf("uid=test.user,o=test"),
                             ByteString.valueOf("password"),
                             new ArrayList<Control>(),
                             new ArrayList<Control>());
    ByteString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeMessage(unbindMessage);
    s.close();
  }



  /**
   * Tests the use of the "Who Am I?" extended operation when used by a client
   * that has authenticated using a SASL mechanism and specified an alternate
   * authorization identity.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithAlternateSASLAuthzID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "",
         "dn: uid=proxy.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: proxy.user",
         "givenName: Proxy",
         "sn: User",
         "cn: Proxy User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl",
         "ds-privilege-name: proxied-auth");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);


    // Bind as the proxy user with an alternate authorization identity, and use
    // the "Who Am I?" operation.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);

    HashMap<String,List<String>> saslProperties = new HashMap<>(2);
    saslProperties.put("authID", newArrayList("dn:uid=proxy.user,o=test"));
    saslProperties.put("authzID", newArrayList("dn:uid=test.user,o=test"));

    authHandler.doSASLPlain(ByteString.empty(),
                            ByteString.valueOf("password"), saslProperties,
                            new ArrayList<Control>(),
                            new ArrayList<Control>());
    ByteString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);
    assertEquals(authzID.toString(), "dn:uid=test.user,o=test");


    // Close the connection to the server.
    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeMessage(unbindMessage);
    s.close();
  }



  /**
   * Tests the use of the Who Am I? extended operation in conjunction with the
   * proxied authorization control by an appropriately authorized user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithAllowedProxiedAuthControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "",
         "dn: uid=proxy.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: proxy.user",
         "givenName: Proxy",
         "sn: User",
         "cn: Proxy User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl",
         "ds-privilege-name: proxied-auth");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);


    // Bind as the proxy user and use the "Who Am I?" operation, but without the
    // proxied auth control.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    authHandler.doSimpleBind(3, ByteString.valueOf("uid=proxy.user,o=test"),
                             ByteString.valueOf("password"),
                             new ArrayList<Control>(),
                             new ArrayList<Control>());
    ByteString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);
    assertEquals(authzID.toString(), "dn:uid=proxy.user,o=test");


    // Use the "Who Am I?" operation again, this time with the proxy control.
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST);
    ArrayList<Control> requestControls = new ArrayList<>(1);
    requestControls.add(new ProxiedAuthV2Control(
         ByteString.valueOf("dn:uid=test.user,o=test")));
    LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                          extendedRequest, requestControls);
    writer.writeMessage(message);

    message = reader.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
    authzID = extendedResponse.getValue();
    assertNotNull(authzID);
    assertEquals(authzID.toString(), "dn:uid=test.user,o=test");


    // Close the connection to the server.
    message = new LDAPMessage(nextMessageID.getAndIncrement(),
                              new UnbindRequestProtocolOp());
    writer.writeMessage(message);
    s.close();
  }



  /**
   * Tests the use of the Who Am I? extended operation in conjunction with the
   * proxied authorization control by a user who doesn't have the rights to use
   * that control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithDisallowedProxiedAuthControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "",
         "dn: uid=cantproxy.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: proxy.user",
         "givenName: Cantproxy",
         "sn: User",
         "cn: Cantproxy User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);


    // Bind as the proxy user and use the "Who Am I?" operation, but without the
    // proxied auth control.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    authHandler.doSimpleBind(3,
                             ByteString.valueOf("uid=cantproxy.user,o=test"),
                             ByteString.valueOf("password"),
                             new ArrayList<Control>(),
                             new ArrayList<Control>());
    ByteString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);
    assertEquals(authzID.toString(), "dn:uid=cantproxy.user,o=test");


    // Use the "Who Am I?" operation again, this time with the proxy control.
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST);
    ArrayList<Control> requestControls = new ArrayList<>(1);
    requestControls.add(new ProxiedAuthV2Control(
         ByteString.valueOf("dn:uid=test.user,o=test")));
    LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                          extendedRequest, requestControls);
    writer.writeMessage(message);

    message = reader.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(),
                 LDAPResultCode.AUTHORIZATION_DENIED);
    assertNull(extendedResponse.getValue());


    // Close the connection to the server.
    message = new LDAPMessage(nextMessageID.getAndIncrement(),
                              new UnbindRequestProtocolOp());
    writer.writeMessage(message);
    s.close();
  }
}


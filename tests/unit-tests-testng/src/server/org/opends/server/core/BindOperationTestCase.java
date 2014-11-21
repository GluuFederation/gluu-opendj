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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.core;



import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.*;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.messages.Message;
import org.opends.server.tools.LDAPDelete;
import org.opends.server.tools.LDAPModify;

import static org.testng.Assert.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;


/**
 * A set of test cases for bind operations
 */
public class BindOperationTestCase
       extends OperationTestCase
{
  /**
   * Retrieves a set of bind operation objects using simple authentication.
   *
   * @return  A set of bind operation objects using simple authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "simpleBinds")
  public Object[][] getSimpleBindOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    ByteString nullOS = null;
    DN nullDN = null;

    BindOperation[] simpleBinds = new BindOperation[]
    {
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", ByteString.empty(),
                        ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", ByteString.empty(),
                        ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullOS, ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullOS, ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", ByteString.empty(), nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", ByteString.empty(), nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullOS, nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullOS, nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3",
                        ByteString.valueOf("cn=Directory Manager"),
                        ByteString.valueOf("password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", DN.nullDN(), ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", DN.nullDN(), ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullDN, ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullDN, ByteString.empty()),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", DN.nullDN(), nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", DN.nullDN(), nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullDN, nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullDN, nullOS),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", DN.decode("cn=Directory Manager"),
                        ByteString.valueOf("password"))
    };

    Object[][] array = new Object[simpleBinds.length][1];
    for (int i=0; i < simpleBinds.length; i++)
    {
      array[i][0] = simpleBinds[i];
    }

    return array;
  }



  /**
   * Retrieves a set of bind operation objects using SASL authentication.
   *
   * @return  A set of bind operation objects using SASL authentication.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "saslBinds")
  public Object[][] getSASLBindOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ArrayList<Control> noControls = new ArrayList<Control>(0);
    ByteString nullOS = null;
    DN nullDN = null;

    BindOperation[] saslBinds = new BindOperation[]
    {
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", ByteString.empty(), "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", ByteString.empty(), "EXTERNAL",
                        null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullOS, "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullOS, "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", ByteString.empty(), "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", ByteString.empty(), "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullOS, "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullOS, "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", DN.nullDN(), "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", DN.nullDN(), "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullDN, "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullDN, "EXTERNAL", null),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", DN.nullDN(), "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", DN.nullDN(), "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        null, "3", nullDN, "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password")),
      new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                        noControls, "3", nullDN, "PLAIN",
                        ByteString.valueOf("\u0000u:test.user\u0000password"))
    };

    Object[][] array = new Object[saslBinds.length][1];
    for (int i=0; i < saslBinds.length; i++)
    {
      array[i][0] = saslBinds[i];
    }

    return array;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  protected Operation[] createTestOperations()
         throws Exception
  {
    Object[][] simpleBinds = getSimpleBindOperations();
    Object[][] saslBinds   = getSASLBindOperations();

    Operation[] bindOps = new Operation[simpleBinds.length + saslBinds.length];

    int pos = 0;
    for (int i=0; i < simpleBinds.length; i++)
    {
      bindOps[pos++] = (BindOperation) simpleBinds[i][0];
    }

    for (int i=0; i < saslBinds.length; i++)
    {
      bindOps[pos++] = (BindOperation) saslBinds[i][0];
    }

    return bindOps;
  }



  /**
   * Tests the <CODE>getAuthenticationType</CODE> method for simple bind
   * operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetAuthenticationTypeSimple(BindOperation o)
  {
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
  }



  /**
   * Tests the <CODE>getAuthenticationType</CODE> method for SASL bind
   * operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetAuthenticationTypeSASL(BindOperation o)
  {
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
  }



  /**
   * Tests the <CODE>getGetProtocolVersion</CODE> method for simple bind
   * operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetProtocolVersionSimple(BindOperation o)
  {
    assertNotNull(o.getProtocolVersion());
    assertTrue(o.getProtocolVersion().length() > 0);
  }



  /**
   * Tests the <CODE>getProtocolVersion</CODE> method for SASL bind operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetProtocolVersionSASL(BindOperation o)
  {
    assertNotNull(o.getProtocolVersion());
    assertTrue(o.getProtocolVersion().length() > 0);
  }



  /**
   * Tests the <CODE>getRawBindDN</CODE> method for simple bind operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetRawBindDNSimple(BindOperation o)
  {
    assertNotNull(o.getRawBindDN());
  }



  /**
   * Tests the <CODE>getRawBindDN</CODE> method for SASL bind operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetRawBindDNSASL(BindOperation o)
  {
    assertNotNull(o.getRawBindDN());
  }



  /**
   * Tests the <CODE>setRawBindDN()</CODE> method for simple bind operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testSetRawBindDNSimple(BindOperation o)
  {
    ByteString originalRawBindDN = o.getRawBindDN();
    assertNotNull(originalRawBindDN);

    o.setRawBindDN(null);
    assertEquals(o.getRawBindDN(), ByteString.empty());

    o.setRawBindDN(ByteString.empty());
    assertEquals(o.getRawBindDN(), ByteString.empty());

    o.setRawBindDN(ByteString.valueOf("cn=Directory Manager"));
    assertEquals(o.getRawBindDN(), ByteString.valueOf("cn=Directory Manager"));

    o.setRawBindDN(originalRawBindDN);
    assertEquals(o.getRawBindDN(), originalRawBindDN);
  }



  /**
   * Tests the <CODE>setRawBindDN()</CODE> method for SASL bind operations.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testSetRawBindDNSASL(BindOperation o)
  {
    ByteString originalRawBindDN = o.getRawBindDN();
    assertNotNull(originalRawBindDN);

    o.setRawBindDN(null);
    assertEquals(o.getRawBindDN(), ByteString.empty());

    o.setRawBindDN(ByteString.empty());
    assertEquals(o.getRawBindDN(), ByteString.empty());

    o.setRawBindDN(ByteString.valueOf("cn=Directory Manager"));
    assertEquals(o.getRawBindDN(), ByteString.valueOf("cn=Directory Manager"));

    o.setRawBindDN(originalRawBindDN);
    assertEquals(o.getRawBindDN(), originalRawBindDN);
  }



  /**
   * Tests the <CODE>getBindDN</CODE> method on bind operations using simple
   * authentication.
   *
   * @param  o  The bind operation to be tested.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetBindDNSimple(BindOperation o)
         throws Exception
  {
    o.getBindDN();
  }



  /**
   * Tests the <CODE>getSimplePassword</CODE> method for bind operations using
   * simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetSimplePasswordSimple(BindOperation o)
  {
    assertNotNull(o.getSimplePassword());
  }



  /**
   * Tests the <CODE>getSimplePassword</CODE> method for bind operations using
   * SASL authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetSimplePasswordSASL(BindOperation o)
  {
    assertNull(o.getSimplePassword());
  }



  /**
   * Tests the <CODE>getSASLMechanism</CODE> method for bind operations using
   * simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetSASLMechanismSimple(BindOperation o)
  {
    assertNull(o.getSASLMechanism());
  }



  /**
   * Tests the <CODE>getSASLMechanism</CODE> method for bind operations using
   * SASL authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetSASLMechanismSASL(BindOperation o)
  {
    assertNotNull(o.getSASLMechanism());
  }



  /**
   * Tests the <CODE>getSASLCredentials</CODE> method for bind operations using
   * simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetSASLCredentialsSimple(BindOperation o)
  {
    assertNull(o.getSASLCredentials());
  }



  /**
   * Tests the <CODE>getSASLCredentials</CODE> method for bind operations using
   * SASL authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetSASLCredentialsSASL(BindOperation o)
  {
    // We don't know whether they should be null or not, so we'll just not
    // bother checking what it returns.
    o.getSASLCredentials();
  }



  /**
   * Tests the ability to change a simple bind operation to a SASL bind
   * operation and back again.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testChangeSimpleToSASLAndBack(BindOperation o)
  {
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
    assertNull(o.getSASLMechanism());
    assertNull(o.getSASLCredentials());

    ByteString originalPassword = o.getSimplePassword();
    assertNotNull(originalPassword);

    o.setSASLCredentials("EXTERNAL", null);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
    assertNotNull(o.getSASLMechanism());
    assertNull(o.getSASLCredentials());

    o.setSimplePassword(originalPassword);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
    assertNull(o.getSASLMechanism());
    assertNull(o.getSASLCredentials());

    o.setSASLCredentials("PLAIN",
         ByteString.valueOf("\u0000u:test.user\u0000password"));
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
    assertNotNull(o.getSASLMechanism());
    assertNotNull(o.getSASLCredentials());

    o.setSimplePassword(originalPassword);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
    assertNull(o.getSASLMechanism());
    assertNull(o.getSASLCredentials());
  }



  /**
   * Tests the ability to change a SASL bind operation to a simple bind
   * operation and back again.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testChangeSASLToSimpleAndBack(BindOperation o)
  {
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
    assertNull(o.getSimplePassword());

    String          originalMech  = o.getSASLMechanism();
    ByteString originalCreds = o.getSASLCredentials();
    assertNotNull(originalMech);

    o.setSimplePassword(null);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
    assertNotNull(o.getSimplePassword());

    o.setSASLCredentials(originalMech, originalCreds);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
    assertNull(o.getSimplePassword());

    o.setSimplePassword(ByteString.empty());
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
    assertNotNull(o.getSimplePassword());

    o.setSASLCredentials(originalMech, originalCreds);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
    assertNull(o.getSimplePassword());

    o.setSimplePassword(ByteString.valueOf("password"));
    assertEquals(o.getAuthenticationType(), AuthenticationType.SIMPLE);
    assertNotNull(o.getSimplePassword());

    o.setSASLCredentials(originalMech, originalCreds);
    assertEquals(o.getAuthenticationType(), AuthenticationType.SASL);
    assertNull(o.getSimplePassword());
  }



  /**
   * Tests the <CODE>getServerSASLCredentials</CODE> method for bind operations
   * using simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetServerSASLCredentialsSimple(BindOperation o)
  {
    assertNull(o.getServerSASLCredentials());
  }



  /**
   * Tests the <CODE>getServerSASLCredentials</CODE> method for bind operations
   * using SASL authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetServerSASLCredentialsSASL(BindOperation o)
  {
    assertNull(o.getServerSASLCredentials());
  }



  /**
   * Tests the <CODE>getSASLAuthUserEntry</CODE> method for bind operations
   * using simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetSASLAuthUserEntrySimple(BindOperation o)
  {
    assertNull(o.getSASLAuthUserEntry());
  }



  /**
   * Tests the <CODE>getSASLAuthUserEntry</CODE> method for bind operations
   * using SASL authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetSASLAuthUserEntrySASL(BindOperation o)
  {
    assertNull(o.getSASLAuthUserEntry());
  }



  /**
   * Tests the <CODE>getSASLAuthUserEntry</CODE> method for completed SASL bind
   * operations in which this value will be set.
   */
  @Test
  public void testGetSASLAuthUserEntryNonNull()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperation bindOperation =
                       conn.processSASLBind(DN.nullDN(), "PLAIN", saslCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(bindOperation.getSASLAuthUserEntry());
  }



  /**
   * Tests the <CODE>getUserEntryDN</CODE> method for bind operations using
   * simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetUserEntryDNSimple(BindOperation o)
  {
    assertNull(o.getUserEntryDN());
  }



  /**
   * Tests the <CODE>getUserEntryDN</CODE> method for a completed bind operation
   * using simple authentication in which this value will be set.
   */
  @Test()
  public void testGetUserEntryDNSimpleNonNull()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(bindOperation.getUserEntryDN());
  }



  /**
   * Tests the <CODE>getUserEntryDN</CODE> method for bind operations using SASL
   * authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetUserEntryDNSASL(BindOperation o)
{
    assertNull(o.getUserEntryDN());
  }



  /**
   * Tests the <CODE>getUserEntryDN</CODE> method for a completed bind operation
   * using SASL authentication in which this value will be set.
   */
  @Test()
  public void testGetUserEntryDNSASLNonNull()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperation bindOperation =
         conn.processSASLBind(DN.nullDN(), "PLAIN", saslCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(bindOperation.getUserEntryDN());
  }



  /**
   * Tests the <CODE>getProcessingStartTime</CODE>,
   * <CODE>getProcessingStopTime</CODE>, and <CODE>getProcessingTime()</CODE>
   * methods for a completed bind operation using simple authentication.
   */
  @Test()
  public void testGetProcessingStartAndStopTimesSimple()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(bindOperation.getProcessingStartTime() > 0);
    assertTrue(bindOperation.getProcessingStopTime() >=
               bindOperation.getProcessingStartTime());
    assertTrue(bindOperation.getProcessingTime() >= 0);
  }



  /**
   * Tests the <CODE>getProcessingStartTime</CODE>,
   * <CODE>getProcessingStopTime</CODE>, and <CODE>getProcessingTime()</CODE>
   * methods for a completed bind operation using SASL authentication.
   */
  @Test()
  public void testGetProcessingStartAndStopTimesSASL()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperation bindOperation =
         conn.processSASLBind(DN.nullDN(), "PLAIN", saslCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(bindOperation.getProcessingStartTime() > 0);
    assertTrue(bindOperation.getProcessingStopTime() >=
               bindOperation.getProcessingStartTime());
    assertTrue(bindOperation.getProcessingTime() >= 0);
  }



  /**
   * Tests the <CODE>getOperationType</CODE> method for bind operations using
   * simple authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetOperationTypeSimple(BindOperation o)
  {
    assertEquals(o.getOperationType(), OperationType.BIND);
  }



  /**
   * Tests the <CODE>getOperationType</CODE> method for bind operations using
   * SASL authentication.
   *
   * @param  o  The bind operation to be tested.
   */
  @Test(dataProvider = "saslBinds")
  public void testGetOperationTypeSASL(BindOperation o)
  {
    assertEquals(o.getOperationType(), OperationType.BIND);
  }



  /**
   * Tests the <CODE>getResponseLogElements</CODE> method for a completed
   * successful bind operation using simple authentication.
   */
  @Test()
  public void testGetResponseLogElementsSimple()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(bindOperation.getResponseLogElements());
    assertTrue(bindOperation.getResponseLogElements().length > 0);
  }



  /**
   * Tests the <CODE>getResponseLogElements</CODE> method for a completed bind
   * operation using SASL authentication.
   */
  @Test()
  public void testGetResponseLogElementsSASL()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperation bindOperation =
         conn.processSASLBind(DN.nullDN(), "PLAIN", saslCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(bindOperation.getResponseLogElements());
    assertTrue(bindOperation.getResponseLogElements().length > 0);
  }



  /**
   * Tests the <CODE>getResponseLogElements</CODE> method for a failed simple
   * bind attempt in which the target user didn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetResponseLogElementsSimpleNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("uid=test,o=test"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
    assertNotNull(bindOperation.getResponseLogElements());
    assertTrue(bindOperation.getResponseLogElements().length > 0);
  }



  /**
   * Tests a simple bind operation to ensure that all plugin types are invoked
   * as expected.
   */
  @Test()
  public void testAllPluginsCalledSimple()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

//    assertTrue(InvocationCounterPlugin.getPreParseCount() > 0);
//    assertTrue(InvocationCounterPlugin.getPreOperationCount() > 0);
//    assertTrue(InvocationCounterPlugin.getPostOperationCount() > 0);
    ensurePostReponseHasRun();
//    assertTrue(InvocationCounterPlugin.getPostResponseCount() > 0);
  }



  /**
   * Tests a SASL bind operation to ensure that all plugin types are invoked
   * as expected.
   */
  @Test()
  public void testAllPluginsCalledSASL()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperation bindOperation =
         conn.processSASLBind(DN.nullDN(), "PLAIN", saslCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

//    assertTrue(InvocationCounterPlugin.getPreParseCount() > 0);
//    assertTrue(InvocationCounterPlugin.getPreOperationCount() > 0);
//    assertTrue(InvocationCounterPlugin.getPostOperationCount() > 0);
    ensurePostReponseHasRun();
//    assertTrue(InvocationCounterPlugin.getPostResponseCount() > 0);
  }



  /**
   * Tests an anonymous simple bind operation to ensure that it's treated
   * properly if the client connection is lost in pre-parse plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPreParseSimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), 3,
                                   ByteString.empty());
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList("PreParse"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an anonymous simple bind operation to ensure that it's treated
   * properly if the client connection is lost in pre-operation plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPreOperationSimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), 3,
                                   ByteString.empty());
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an anonymous simple bind operation to ensure that it's treated
   * properly if the client connection is lost in post-operation plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPostOperationSimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), 3,
                                   ByteString.empty());
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PostOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an anonymous simple bind operation to ensure that it's treated
   * properly if the client connection is lost in post-response plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPostResponseSimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), 3,
                                   ByteString.empty());
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PostResponse"));
    w.writeMessage(message);

    message = r.readMessage();
    while (message != null)
    {
      assertTrue((message.getProtocolOpType() == OP_TYPE_BIND_RESPONSE) ||
                 (message.getProtocolOpType() == OP_TYPE_EXTENDED_RESPONSE));
      message = r.readMessage();
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an authenticated simple bind operation to ensure that it's treated
   * properly if the client connection is lost in pre-parse plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPreParseSimpleAuthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList("PreParse"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an authenticated simple bind operation to ensure that it's treated
   * properly if the client connection is lost in pre-operation plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPreOperationSimpleAuthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an authenticated simple bind operation to ensure that it's treated
   * properly if the client connection is lost in post-operation plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPostOperationSimpleAuthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PostOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an authenticated simple bind operation to ensure that it's treated
   * properly if the client connection is lost in post-response plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPostResponseSimpleAuthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PostResponse"));
    w.writeMessage(message);

    message = r.readMessage();
    while (message != null)
    {
      assertTrue((message.getProtocolOpType() == OP_TYPE_BIND_RESPONSE) ||
                 (message.getProtocolOpType() == OP_TYPE_EXTENDED_RESPONSE));
      message = r.readMessage();
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a SASL bind operation to ensure that it's treated properly if the
   * client connection is lost in pre-parse plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPreParseSASL()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "PLAIN", saslCreds);
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList("PreParse"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a SASL bind operation to ensure that it's treated properly if the
   * client connection is lost in pre-operation plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPreOperationSASL()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "PLAIN", saslCreds);
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a SASL bind operation to ensure that it's treated properly if the
   * client connection is lost in post-operation plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPostOperationSASL()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "PLAIN", saslCreds);
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PostOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    if (message != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a SASL bind operation to ensure that it's treated properly if the
   * client connection is lost in post-response plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testBindDisconnectInPostResponseSASL()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "PLAIN", saslCreds);
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         DisconnectClientPlugin.createDisconnectControlList(
              "PostResponse"));
    w.writeMessage(message);

    message = r.readMessage();
    while (message != null)
    {
      assertTrue((message.getProtocolOpType() == OP_TYPE_BIND_RESPONSE) ||
                 (message.getProtocolOpType() == OP_TYPE_EXTENDED_RESPONSE));
      message = r.readMessage();
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an anonymous simple bind operation to ensure that it's treated
   * properly if the operation gets short-circuited in pre-parse plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindShortCircuitInPreParseSimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), 3,
                                   ByteString.empty());
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         ShortCircuitPlugin.createShortCircuitControlList(80, "PreParse"));
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 80);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an anonymous simple bind operation to ensure that it's treated
   * properly if the operation gets short-circuited in pre-operation plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindShortCircuitInPreOperationSimpleAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), 3,
                                   ByteString.empty());
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         ShortCircuitPlugin.createShortCircuitControlList(80,
                                                              "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 80);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an authenticated simple bind operation to ensure that it's treated
   * properly if the operation gets short-circuited in pre-parse plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindShortCircuitInPreParseSimpleAuthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         ShortCircuitPlugin.createShortCircuitControlList(80, "PreParse"));
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 80);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests an authenticated simple bind operation to ensure that it's treated
   * properly if the operation gets short-circuited in pre-operation plugin
   * processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindShortCircuitInPreOperationSimpleAuthenticated()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         ShortCircuitPlugin.createShortCircuitControlList(80,
                                                              "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 80);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a SASL bind operation to ensure that it's treated properly if the
   * operation gets short-circuited in pre-parse plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindShortCircuitInPreParseSASL()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "PLAIN", saslCreds);
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         ShortCircuitPlugin.createShortCircuitControlList(80, "PreParse"));
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 80);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a SASL bind operation to ensure that it's treated properly if the
   * operation gets short-circuited in pre-operation plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBindShortCircuitInPreOperationSASL()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.empty(), "PLAIN", saslCreds);
    LDAPMessage message = new LDAPMessage(1, bindRequest,
         ShortCircuitPlugin.createShortCircuitControlList(80,
                                                              "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 80);

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests performing a simple bind operation with an invalid user DN.
   */
  @Test()
  public void testSimpleBindInvalidDN()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("invaliddn"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
  }



  /**
   * Tests performing a SASL bind operation with an invalid user DN.
   */
  @Test()
  public void testSASLBindInvalidDN()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperation bindOperation =
         conn.processSASLBind(ByteString.valueOf("invaliddn"), "PLAIN",
                              saslCreds);
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
  }



  /**
   * Tests performing a simple bind operation with an unsupported control that
   * is marked critical.
   */
  @Test()
  public void testSimpleBindUnsupportedCriticalControl()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ArrayList<Control> requestControls = new ArrayList<Control>(1);
    requestControls.add(new LDAPControl("1.2.3.4", true));

    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                           requestControls, "3", DN.nullDN(),
                        ByteString.empty());
    bindOperation.run();
    assertEquals(bindOperation.getResultCode(),
                 ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
  }



  /**
   * Tests performing a SASL bind operation with an unsupported control that is
   * marked critical.
   */
  @Test()
  public void testSASLBindUnsupportedCriticalControl()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ArrayList<Control> requestControls = new ArrayList<Control>(1);
    requestControls.add(new LDAPControl("1.2.3.4", true));

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                           requestControls, "3", DN.nullDN(), "PLAIN",
                        saslCreds);
    bindOperation.run();
    assertEquals(bindOperation.getResultCode(),
                 ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
  }



  /**
   * Tests performing a simple bind operation with an unsupported control that
   * is not marked critical.
   */
  @Test()
  public void testSimpleBindUnsupportedNonCriticalControl()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ArrayList<Control> requestControls = new ArrayList<Control>(1);
    requestControls.add(new LDAPControl("1.2.3.4", false));

    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                           requestControls, "3", DN.nullDN(),
                           ByteString.empty());

    bindOperation.run();
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests performing a SASL bind operation with an unsupported control that is
   * is not marked critical.
   */
  @Test()
  public void testSASLBindUnsupportedNonCriticalControl()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    ArrayList<Control> requestControls = new ArrayList<Control>(1);
    requestControls.add(new LDAPControl("1.2.3.4", false));

    ByteString saslCreds =
         ByteString.valueOf("\u0000dn:cn=Directory Manager\u0000password");

    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                           requestControls, "3", DN.nullDN(), "PLAIN",
                           saslCreds);
    bindOperation.run();
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests performing a simple bind operation with the DN of a user that doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleBindNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("uid=test,o=test"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
  }



  /**
   * Tests performing a simple bind operation with the DN of a valid user but
   * without including a password in the request, with the server configured to
   * disallow that combination.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleBindWithDNNoPasswordDisallowed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.empty());
    assertEquals(bindOperation.getResultCode(),
                           ResultCode.UNWILLING_TO_PERFORM);
  }



  /**
   * Tests performing a simple bind operation with the DN of a valid user but
   * without including a password in the request, with the server configured to
   * allow that combination.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleBindWithDNNoPasswordAllowed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    String attr = "ds-cfg-bind-with-dn-requires-password";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attr, "false")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode("cn=config"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.empty());
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attr, "true")));
    modifyOperation =  conn.processModify(DN.decode("cn=config"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests performing a simple bind operation as a user who doesn't have a
   * password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleBindNoUserPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
         "dn: uid=test,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test",
         "givenName: Test",
         "sn: User",
         "cn: Test User");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
//         new InternalClientConnection(new AuthenticationInfo());

    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("uid=test,o=test"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
  }



  /**
   * Tests performing a simple bind operation as a user who exists on
   * another server for which a named subordinate reference exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSimpleBindReferral()
         throws Exception
  {
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

    TestCaseUtils.addEntry(
         "dn: ou=people,dc=example,dc=com",
         "objectClass: top",
         "objectClass: referral",
         "objectClass: extensibleObject",
         "ou: people",
         "ref: ldap://example.com:1389/ou=people,o=test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("uid=test,ou=people,dc=example,dc=com"),
                                ByteString.valueOf("password"));
    assertEquals(bindOperation.getResultCode(), ResultCode.REFERRAL);

    List<String> referralURLs = bindOperation.getReferralURLs();
    assertNotNull(referralURLs);
    assertEquals(referralURLs.size(), 1);
    assertEquals(referralURLs.get(0), "ldap://example.com:1389/uid=test,ou=people,o=test");
  }



  /**
   * Tests performing a simple bind operation with a valid DN but incorrect
   * password.
   */
  @Test()
  public void testSimpleBindWrongPassword()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("wrongpassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
  }



  /**
   * Tests the behavior of the returnBindErrorMessage configuration option.
   */
  @Test()
  public void testReturnBindErrorMessage()
  {
    // Make sure that the default behavior is to not include the error message.
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());

    BindOperation bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("wrongpassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
    assertTrue(((bindOperation.getErrorMessage() == null) ||
                (bindOperation.getErrorMessage().length() == 0)),
               bindOperation.getErrorMessage().toString());


    // Change the server configuration so that error messages should be
    // returned.
    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--set", "return-bind-error-messages:true");

    bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("wrongpassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
    assertTrue(bindOperation.getErrorMessage().length() > 0);


    // Change the configuration back and make sure that the error message goes
    // away.
    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--set", "return-bind-error-messages:false");

    bindOperation =
         conn.processSimpleBind(ByteString.valueOf("cn=Directory Manager"),
                                ByteString.valueOf("wrongpassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.INVALID_CREDENTIALS);
    assertTrue(((bindOperation.getErrorMessage() == null) ||
                (bindOperation.getErrorMessage().length() == 0)),
               bindOperation.getErrorMessage().toString());
  }



  /**
   * Tests to ensure that performing multiple binds on a client connection will
   * cause the connection to no longer be associated with the previous identity.
   * This helps provide coverage for issue #1392.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRebindClearsAuthInfo()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=rebind.test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: rebind.test",
      "givenName: Rebind",
      "sn: Test",
      "cn: Rebind Test",
      "userPassword: password");
    String dnString = "uid=rebind.test,o=test";
    DN userDN = DN.decode(dnString);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    TestCaseUtils.configureSocket(s);
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf(dnString),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    assertNotNull(DirectoryServer.getAuthenticatedUsers().get(userDN));
    assertEquals(DirectoryServer.getAuthenticatedUsers().get(userDN).size(),
                 1);

    // We occasionally run into
    // ProtocolMessages.MSGID_LDAP_CLIENT_DUPLICATE_MESSAGE_ID, so we wait
    // for previous ops to complete.
    TestCaseUtils.quiesceServer();
    bindRequest = new BindRequestProtocolOp(
                           ByteString.valueOf("cn=Directory Manager"), 3,
                           ByteString.valueOf("password"));
    message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0, message.toString());

    assertNull(DirectoryServer.getAuthenticatedUsers().get(userDN));

    s.close();
  }



  /**
   * Tests to ensure that performing subtree delete will
   * cause the connection to no longer be associated
   * with the previous identity.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testSubtreeDeleteClearsAuthInfo()
         throws Exception
  {
    TestCaseUtils.restartServer();
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
      "dn: ou=people,dc=example,dc=com",
      "objectClass: organizationalUnit",
      "objectClass: top",
      "ou: people",
      "",
      "dn: uid=test,ou=people,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test",
      "givenName: test",
      "sn: Test",
      "cn: Test",
      "userPassword: password");

    String dnString = "uid=test,ou=people,dc=example,dc=com";
    DN userDN = DN.decode(dnString);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    TestCaseUtils.configureSocket(s);
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf(dnString),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse =
            message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    assertNotNull(DirectoryServer.getAuthenticatedUsers().get(
            userDN));
    assertEquals(DirectoryServer.getAuthenticatedUsers().get(
            userDN).size(), 1);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", OID_SUBTREE_DELETE_CONTROL + ":true",
      "--noPropertiesFile",
      "ou=people,dc=example,dc=com"
    };
    assertEquals(LDAPDelete.mainDelete(args, false, null, System.err), 0);

    assertNull(DirectoryServer.getAuthenticatedUsers().get(userDN));

    s.close();

    // Cleanup.
    TestCaseUtils.clearJEBackend(false,
       "userRoot", "dc=example,dc=com");
  }



  /**
   * Tests to ensure that performing subtree modify will
   * cause the connection to be associated with new auth
   * identity instead of the previous identity.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testSubtreeModifyUpdatesAuthInfo()
         throws Exception
  {
    TestCaseUtils.restartServer();
    TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
      "dn: ou=people,dc=example,dc=com",
      "objectClass: organizationalUnit",
      "objectClass: top",
      "ou: people",
      "",
      "dn: uid=test,ou=people,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test",
      "givenName: test",
      "sn: Test",
      "cn: Test",
      "userPassword: password");

    String dnString = "uid=test,ou=people,dc=example,dc=com";
    DN userDN = DN.decode(dnString);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    TestCaseUtils.configureSocket(s);
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf(dnString),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse =
            message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    assertNotNull(DirectoryServer.getAuthenticatedUsers().get(
            userDN));
    assertEquals(DirectoryServer.getAuthenticatedUsers().get(
            userDN).size(), 1);

    String path = TestCaseUtils.createTempFile(
         "dn: ou=people,dc=example,dc=com",
         "changetype: moddn",
         "newRDN: ou=users",
         "deleteOldRDN: 1");
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "--noPropertiesFile",
      "-f", path
    };
    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    String newDNString = "uid=test,ou=users,dc=example,dc=com";
    DN newUserDN = DN.decode(newDNString);

    assertNotNull(DirectoryServer.getAuthenticatedUsers().get(
            newUserDN));
    assertEquals(DirectoryServer.getAuthenticatedUsers().get(
            newUserDN).size(), 1);

    s.close();

    // Cleanup.
    TestCaseUtils.clearJEBackend(false,
       "userRoot", "dc=example,dc=com");
  }



  /**
   * Tests to ensure that the "ignore" password policy state update policy
   * works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIgnoreStateUpdateFailurePolicy()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.applyModifications(false,
      "dn: uid=test.user,o=test",
      "changetype: add",
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
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-last-login-time-attribute",
      "ds-cfg-last-login-time-attribute: ds-pwp-last-login-time",
      "-",
      "replace: ds-cfg-last-login-time-format",
      "ds-cfg-last-login-time-format: yyyyMMdd",
      "-",
      "replace: ds-cfg-state-update-failure-policy",
      "ds-cfg-state-update-failure-policy: ignore",
      "",
      "dn: cn=config",
      "changetype: modify",
      "replace: ds-cfg-writability-mode",
      "ds-cfg-writability-mode: disabled"
    );

    try
    {
      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, System.out, System.err),
                   0);

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=config",
        "changetype: modify",
        "replace: ds-cfg-writability-mode",
        "ds-cfg-writability-mode: enabled",
        "",
        "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
        "changetype: modify",
        "replace: ds-cfg-last-login-time-attribute",
        "-",
        "replace: ds-cfg-last-login-time-format",
        "-",
        "replace: ds-cfg-state-update-failure-policy",
        "ds-cfg-state-update-failure-policy: reactive"
      );
    }
  }



  /**
   * Tests to ensure that the "reactive" password policy state update policy
   * works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testReactiveStateUpdateFailurePolicy()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.applyModifications(false,
      "dn: uid=test.user,o=test",
      "changetype: add",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password");
    TestCaseUtils.applyModifications(true,
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-last-login-time-attribute",
      "ds-cfg-last-login-time-attribute: ds-pwp-last-login-time",
      "-",
      "replace: ds-cfg-last-login-time-format",
      "ds-cfg-last-login-time-format: yyyyMMdd",
      "-",
      "replace: ds-cfg-state-update-failure-policy",
      "ds-cfg-state-update-failure-policy: reactive",
      "",
      "dn: cn=config",
      "changetype: modify",
      "replace: ds-cfg-writability-mode",
      "ds-cfg-writability-mode: disabled"
    );

    try
    {
      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      int rc = LDAPSearch.mainSearch(args, false, System.out, System.err);
      assertFalse(rc == 0);
      assertFalse(rc == LDAPResultCode.INVALID_CREDENTIALS);

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=config",
        "changetype: modify",
        "replace: ds-cfg-writability-mode",
        "ds-cfg-writability-mode: enabled",
        "",
        "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
        "changetype: modify",
        "replace: ds-cfg-last-login-time-attribute",
        "-",
        "replace: ds-cfg-last-login-time-format",
        "-",
        "replace: ds-cfg-state-update-failure-policy",
        "ds-cfg-state-update-failure-policy: reactive"
      );
    }
  }



  /**
   * Tests to ensure that the "proactive" password policy state update policy
   * works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProactiveStateUpdateFailurePolicy()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.applyModifications(false,
      "dn: uid=test.user,o=test",
      "changetype: add",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password");
    TestCaseUtils.applyModifications(true,
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-last-login-time-attribute",
      "ds-cfg-last-login-time-attribute: ds-pwp-last-login-time",
      "-",
      "replace: ds-cfg-last-login-time-format",
      "ds-cfg-last-login-time-format: yyyyMMdd",
      "-",
      "replace: ds-cfg-state-update-failure-policy",
      "ds-cfg-state-update-failure-policy: proactive",
      "",
      "dn: cn=config",
      "changetype: modify",
      "replace: ds-cfg-writability-mode",
      "ds-cfg-writability-mode: disabled"
    );

    try
    {
      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, System.out, System.err),
                   LDAPResultCode.INVALID_CREDENTIALS);

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };

      assertEquals(LDAPSearch.mainSearch(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=config",
        "changetype: modify",
        "replace: ds-cfg-writability-mode",
        "ds-cfg-writability-mode: enabled",
        "",
        "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
        "changetype: modify",
        "replace: ds-cfg-last-login-time-attribute",
        "-",
        "replace: ds-cfg-last-login-time-format",
        "-",
        "replace: ds-cfg-state-update-failure-policy",
        "ds-cfg-state-update-failure-policy: reactive"
      );
    }
  }

  /**
   * Tests the <CODE>cancel</CODE> method to ensure that it indicates that the
   * operation cannot be cancelled.
   * @param bindOperation The bind operation.
   */
  @Test(dataProvider = "simpleBinds")
  public void testCancel(BindOperation bindOperation)
  {
    CancelRequest cancelRequest =
         new CancelRequest(false, Message.raw("Test Unbind Cancel"));

    assertEquals(bindOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);
  }

  /**
   * Tests the <CODE>getCancelRequest</CODE> method to ensure that it always
   * returns <CODE>null</CODE>.
   * @param bindOperation The bind operation.
   */
  @Test(dataProvider = "simpleBinds")
  public void testGetCancelRequest(BindOperation bindOperation)
  {
    CancelRequest cancelRequest =
         new CancelRequest(false, Message.raw("Test Unbind Cancel"));

    assertNull(bindOperation.getCancelRequest());

    assertEquals(bindOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);

    assertNull(bindOperation.getCancelRequest());
  }
}


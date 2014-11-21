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
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.server.PasswordModifyExtendedOperationHandlerCfg;
import org.opends.server.admin.std.meta.PasswordModifyExtendedOperationHandlerCfgDefn;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.tools.LDAPPasswordModify;
import org.opends.server.types.*;

import static org.testng.Assert.*;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the password modify extended operation.
 */
public class PasswordModifyExtendedOperationTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @return The set of invalid configuration entries.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Password Modify,cn=Extended Operations,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-extended-operation-handler",
         "objectClass: ds-cfg-password-modify-extended-operation-handler",
         "cn: Password Modify",
         "ds-cfg-java-class: org.opends.server." +
              "extensions.PasswordModifyExtendedOperation",
         "ds-cfg-enabled: true",
         "",
         "dn: cn=Password Modify,cn=Extended Operations,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-extended-operation-handler",
         "objectClass: ds-cfg-password-modify-extended-operation-handler",
         "cn: Password Modify",
         "ds-cfg-java-class: org.opends.server." +
              "extensions.PasswordModifyExtendedOperation",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: invaliddn",
         "",
         "dn: cn=Password Modify,cn=Extended Operations,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-extended-operation-handler",
         "objectClass: ds-cfg-password-modify-extended-operation-handler",
         "cn: Password Modify",
         "ds-cfg-java-class: org.opends.server." +
              "extensions.PasswordModifyExtendedOperation",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: cn=nonexistent,cn=config");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with invalid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    PasswordModifyExtendedOperationHandlerCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              PasswordModifyExtendedOperationHandlerCfgDefn.getInstance(),
              e);

    PasswordModifyExtendedOperation handler =
         new PasswordModifyExtendedOperation();
    handler.initializeExtendedOperationHandler(configuration);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a root user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsRootImplicitSelfWithOldPassword()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(DN.decode("cn=Directory Manager"),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);


    // Now change the password back to what it was.
    args = new String[]
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "newPassword",
      "-c", "newPassword",
      "-n", "password"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a root user</LI>
   *   <LI>Authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsRootExplicitSelfWithOldPassword()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword",
      "-A"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(DN.decode("cn=Directory Manager"),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);


    // Now change the password back to what it was.
    args = new String[]
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "newPassword",
      "-c", "newPassword",
      "-n", "password",
      "-A"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Unauthenticated client conection</LI>
   *   <LI>Authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsAnonymousExplicitRootWithOldPassword()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:cn=Directory Manager",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(DN.decode("cn=Directory Manager"),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);


    // Now change the password back to what it was.
    args = new String[]
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:cn=Directory Manager",
      "-c", "newPassword",
      "-n", "password"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserImplicitSelfWithOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserImplicitSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>No current password provided</LI>
   *   <LI>No new password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserImplicitSelfNoOldPasswordNoNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>Authorization ID provided ("dn:" form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserExplicitDNColonSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>Authorization ID provided ("u:" form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserExplicitUColonSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "u:test.user",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>userIdentity provided (LDAPDN form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserExplicitDNSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "uid=test.user,o=test",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>userIdentity provided (userID form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsUserExplicitUSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "test.user",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Unauthenticated client connection</LI>
   *   <LI>Authorization ID provided ("dn:" form)</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsExplicitDNColonAnonymousWithOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Unauthenticated client connection</LI>
   *   <LI>Authorization ID provided ("u:" form)</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAsExplicitUColonAnonymousWithOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "u:test.user",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the
   * following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a root user</LI>
   *   <LI>Authorization ID provided ("dn:" form) for a different user</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testRootPasswordResetNoCurrentPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    // Perform an internal bind to verify the password was actually changed.
    conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSimpleBind(userEntry.getDN(),
                                ByteString.valueOf("newPassword"));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP using an
   * authorization ID with the DN of a user that doesn't exist.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureDNColonNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over LDAP using an
   * authorization ID with a malformed DN.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureDNColonMalformedDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:malformed",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over LDAP using an
   * authorization ID with the DN of a user where no part of the hierarchy
   * exists.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureDNColonNoSuchBaseDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=doesnt.exist,o=doesnt.exist",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over LDAP using an
   * authorization ID with the uid of a user that doesn't exist.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureUColonNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "u:test.user",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over LDAP using an
   * authorization ID with the uid of a user that doesn't exist and providing
   * the current password..
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureUColonNoSuchUserWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "u:test.user",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 32);
  }



  /**
   * Tests the password modify extended operation over LDAP using a malformed
   * authorization ID.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureMalformedAuthZID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "malformed",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over LDAP using a bad
   * current password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureWrongCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-c", "wrongPassword",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 49);
  }



  /**
   * Tests the password modify extended operation over LDAP using a pre-encoded
   * new password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAllowPreEncoded()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-pre-encoded-passwords";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "{SSHA}Fv4b7f4AnRMUiGqBi9QA1xJrTtRTqS3WpRi81g=="
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP using a pre-encoded
   * new password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailurePreEncodedNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    /* Make sure preEncoded passwords are rejected */
    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-pre-encoded-passwords";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attr, "false")));
    ModifyOperation modifyOperation =
        conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "{SSHA}Fv4b7f4AnRMUiGqBi9QA1xJrTtRTqS3WpRi81g=="
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over LDAP using a pre-encoded
   * new password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailurePreEncodedNewPasswordWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    /* Make sure preEncoded passwords are rejected */
    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-pre-encoded-passwords";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(attr, "false")));
    ModifyOperation modifyOperation =
        conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "{SSHA}Fv4b7f4AnRMUiGqBi9QA1xJrTtRTqS3WpRi81g=="
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);
  }



  /**
   * Tests the password modify extended operation over an internal connection
   * with a request whose value isn't a valid encoded sequence.
   */
  @Test()
  public void testFailureInvalidRequestValueFormat()
  {
    ByteString requestValue = ByteString.valueOf("malformed");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_PASSWORD_MODIFY_REQUEST,
                                       requestValue);
    assertFalse(extOp.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over an internal connection
   * with a request that contain an invalid sequence element type.
   */
  @Test()
  public void testFailureInvalidSequenceElementType() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeNull((byte)0x50);
    writer.writeEndSequence();
    ByteString requestValue = builder.toByteString();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_PASSWORD_MODIFY_REQUEST,
                                       requestValue);
    assertFalse(extOp.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over an unauthenticated
   * internal connection and without providing an authorization ID.
   */
  @Test()
  public void testFailureCompletelyAnonymous() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                     "newPassword");
    writer.writeEndSequence();
    ByteString requestValue = builder.toByteString();

    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_PASSWORD_MODIFY_REQUEST,
                                       requestValue);
    assertFalse(extOp.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password policy that
   * doesn't allow users to change their own passwords.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureRejectUserPasswordChanges()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-user-password-changes";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password policy that
   * doesn't allow users to change their own passwords.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureRejectUserPasswordChangesWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-user-password-changes";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 53);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation without providing the current
   * password but with a password policy that requires it.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureRequireCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-password-change-requires-current-password";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation that requires secure
   * authentication but a connection that doesn't provide it.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureRequireSecureAuthentication()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-require-secure-authentication";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 13);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation that requires secure
   * password changes but a connection that doesn't provide it.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureRequireSecurePasswordChanges()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-require-secure-password-changes";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation that requires secure
   * password changes but a connection that doesn't provide it.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureRequireSecurePasswordChangesWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-require-secure-password-changes";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 13);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change that is
   * within the minimum password age.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureWithinMinAge()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-min-password-age";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "24 hours")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "0 seconds")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change that is
   * within the minimum password age.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureWithinMinAgeWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-min-password-age";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "24 hours")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 53);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "0 seconds")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change for a
   * user whose password is expired but expired password changes are not
   * allowed.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureExpiredChangesNotAllowed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);



    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr1 = "ds-cfg-max-password-age";
    String attr2 = "ds-cfg-expire-passwords-without-warning";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr1, "90 days")));
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr2, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create("pwdchangedtime",
                                            "20050101000000.000Z")));
    modifyOperation = conn.processModify(userEntry.getDN(), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr1, "0 seconds")));
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr2, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change for a
   * user whose password is expired but expired password changes are allowed.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testExpiredChangesAllowed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);



    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr1 = "ds-cfg-max-password-age";
    String attr2 = "ds-cfg-expire-passwords-without-warning";
    String attr3 = "ds-cfg-allow-expired-password-changes";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr1, "90 days")));
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr2, "true")));
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr3, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create("pwdchangedtime",
                                            "20050101000000.000Z")));
    modifyOperation = conn.processModify(userEntry.getDN(), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr1, "0 seconds")));
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr2, "false")));
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr3, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change for a
   * user where there is no new password provided and there is no password
   * generator.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureNoPasswordNoGenerator()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);



    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-generator";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                      Attributes.empty(DirectoryServer.getAttributeType(attr))));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    String genDN =
         "cn=Random Password Generator,cn=Password Generators,cn=config";
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, genDN)));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change for a
   * user where there is no new password provided and there is no password
   * generator.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureNoPasswordNoGeneratorWithPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);



    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-generator";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                      Attributes.empty(DirectoryServer.getAttributeType(attr))));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    String genDN =
         "cn=Random Password Generator,cn=Password Generators,cn=config";
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, genDN)));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change for a
   * user where a password validator rejects the change.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureValidatorReject()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);



    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-validator";
    String valDN =
         "cn=Length-Based Password Validator,cn=Password Validators,cn=config";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, valDN)));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "short"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                      Attributes.empty(DirectoryServer.getAttributeType(attr))));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password change for a
   * user where a password validator rejects the change.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testFailureValidatorRejectWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);



    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-validator";
    String valDN =
         "cn=Length-Based Password Validator,cn=Password Validators,cn=config";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, valDN)));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "short"
    };
    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertFalse(exitCode == 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                      Attributes.empty(DirectoryServer.getAttributeType(attr))));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP when the existing
   * account has multiple passwords.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAllowMultiplePasswords()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
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
         "userPassword: password2");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-multiple-password-values";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over LDAP when the existing
   * account has multiple passwords.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testAllowMultipleAuthPasswords()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "objectClass: authPasswordObject",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "authPassword: password",
         "authPassword: password2",
         "ds-pwp-password-policy-dn: cn=SHA1 AuthPassword Policy," +
              "cn=Password Policies,cn=config");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String dnStr = "cn=SHA1 AuthPassword Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-multiple-password-values";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "true")));
    ModifyOperation modifyOperation =
         conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    int exitCode =
         LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    assertEquals(exitCode, 0);


    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              Attributes.create(attr, "false")));
    modifyOperation = conn.processModify(DN.decode(dnStr), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests to ensure that if the user provides the correct old password, then
   * the last login time will be updated if that feature is enabled.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testUpdateLastLoginTime()
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
      "userPassword: oldpassword",
      // FIXME -- I shouldn't have to add this ACI explicitly, but for some
      //          reason the global ACIs are getting removed and never put back,
      //          and without this ACI the user won't have permission to change
      //          its own password.  If we ever change the access control syntax
      //          then this will likely need to be updated, but I don't want to
      //          have to give the user the bypass-acl privilege.
      "aci: (targetattr=\"*\")(version 3.0; acl \"Self Modify Rights\"; " +
           "allow (read,search,compare,write) userdn=\"ldap:///self\";)");
    TestCaseUtils.applyModifications(true,
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-last-login-time-attribute",
      "ds-cfg-last-login-time-attribute: ds-pwp-last-login-time",
      "-",
      "replace: ds-cfg-last-login-time-format",
      "ds-cfg-last-login-time-format: yyyyMMdd");

    try
    {
      AttributeType lastLoginTimeAttr =
           DirectoryServer.getAttributeType("ds-pwp-last-login-time", false);
      assertNotNull(lastLoginTimeAttr);

      DN userDN = DN.decode("uid=test.user,o=test");
      Entry userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertFalse(userEntry.hasAttribute(lastLoginTimeAttr));

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "oldpassword",
        "-n", "newpassword"
      };

      int exitCode = LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                           System.err);
      assertEquals(exitCode, 0);

      userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertTrue(userEntry.hasAttribute(lastLoginTimeAttr));
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
        "changetype: modify",
        "replace: ds-cfg-last-login-time-attribute",
        "-",
        "replace: ds-cfg-last-login-time-format");
    }
  }



  /**
   * Tests to ensure that if the user provides an incorrect old password, then
   * the auth failure times will be updated if that feature is enabled.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testUpdateAuthFailureTimes()
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
      "userPassword: oldpassword");
    TestCaseUtils.applyModifications(true,
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-lockout-failure-count",
      "ds-cfg-lockout-failure-count: 3");

    try
    {
      AttributeType authFailureTimesAttr =
           DirectoryServer.getAttributeType("pwdfailuretime", false);
      assertNotNull(authFailureTimesAttr);

      DN userDN = DN.decode("uid=test.user,o=test");
      Entry userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertFalse(userEntry.hasAttribute(authFailureTimesAttr));

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "wrongoldpassword",
        "-n", "newpassword"
      };

      int exitCode = LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                           System.err);
      assertFalse(exitCode == 0);

      userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertTrue(userEntry.hasAttribute(authFailureTimesAttr));
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
        "changetype: modify",
        "replace: ds-cfg-lockout-failure-count",
        "ds-cfg-lockout-failure-count: 0");
    }
  }
}


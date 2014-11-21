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
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.messages.MessageBuilder;
import org.opends.messages.Message;
import org.opends.server.admin.std.meta.
            RepeatedCharactersPasswordValidatorCfgDefn;
import org.opends.server.admin.std.server.
            RepeatedCharactersPasswordValidatorCfg;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of test cases for the repeated characters password validator.
 */
public class RepeatedCharactersPasswordValidatorTestCase
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
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the validator.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: false",
         "",
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: true",
         "",
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 0",
         "ds-cfg-case-sensitive-validation: false");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with valid configurations.
   *
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(), e);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);
    validator.finalizePasswordValidator();
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         // Missing maximum consecutive length
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-case-sensitive-validation: false",
         "",
         // Missing case-sensitive validation
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "",
         // Non-numeric maximum consecutive length
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: non-numeric",
         "ds-cfg-case-sensitive-validation: false",
         "",
         // Non-boolean case-sensitive validation
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: non-boolean",
         "",
         // Maximum consecutive length out of range.
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: -1",
         "ds-cfg-case-sensitive-validation: false");

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
   * @param  entry  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(), e);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);
  }



  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * within the constraints of the password validator.  Case-sensitivity will
   * not be an issue.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsAcceptable2Consecutive()
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
         "userPassword: doesntmatter");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: false");

    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString password = ByteString.valueOf("password");
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", "password")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertTrue(validator.passwordIsAcceptable(password,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
               invalidReason.toString());

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * outside of the constraints of the password validator.  Case-sensitivity
   * will not be an issue.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsAcceptable3Consecutive()
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
         "userPassword: doesntmatter");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: false");

    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString password = ByteString.valueOf("passsword");
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", "passsword")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertFalse(validator.passwordIsAcceptable(password,
                               new HashSet<ByteString>(0), modifyOperation,
                               userEntry, invalidReason));

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * within the constraints of the password validator only because it is
   * configured to operate in a case-sensitive manner.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsAcceptableCaseSensitive()
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
         "userPassword: doesntmatter");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: true");

    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString password = ByteString.valueOf("passSword");
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", "passSword")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertTrue(validator.passwordIsAcceptable(password,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
               invalidReason.toString());

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the {@code passwordIsAcceptable} method with a password that falls
   * outside of the constraints of the password validator because it is
   * configured to operate in a case-insensitive manner.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsAcceptableCaseInsensitive()
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
         "userPassword: doesntmatter");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: false");

    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString password = ByteString.valueOf("passSword");
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", "passSword")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertFalse(validator.passwordIsAcceptable(password,
                               new HashSet<ByteString>(0), modifyOperation,
                               userEntry, invalidReason));

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the {@code passwordIsAcceptable} method when the validator is
   * configured to accept any number of repeated characters.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsAcceptableUnlimitedRepeats()
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
         "userPassword: doesntmatter");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 0",
         "ds-cfg-case-sensitive-validation: true");

    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString password = ByteString.valueOf("aaaaaaaa");
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", "aaaaaaaa")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertTrue(validator.passwordIsAcceptable(password,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
               invalidReason.toString());

    validator.finalizePasswordValidator();
  }



  /**
   * Tests the ability of the password validator to change its behavior when
   * the configuration is updated.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordIsAcceptableConfigurationChange()
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
         "userPassword: doesntmatter");

    Entry validatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 0",
         "ds-cfg-case-sensitive-validation: true");

    RepeatedCharactersPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              validatorEntry);

    RepeatedCharactersPasswordValidator validator =
         new RepeatedCharactersPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString password = ByteString.valueOf("aaaaaaaa");
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", "aaaaaaaa")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertTrue(validator.passwordIsAcceptable(password,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
               invalidReason.toString());

    Entry updatedValidatorEntry = TestCaseUtils.makeEntry(
         "dn: cn=Repeated Characters,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-repeated-characters-password-validator",
         "cn: Repeated Characters",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "RepeatedCharactersPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-max-consecutive-length: 2",
         "ds-cfg-case-sensitive-validation: true");

    RepeatedCharactersPasswordValidatorCfg updatedConfiguration =
         AdminTestCaseUtils.getConfiguration(
              RepeatedCharactersPasswordValidatorCfgDefn.getInstance(),
              updatedValidatorEntry);

    ArrayList<Message> unacceptableReasons = new ArrayList<Message>();
    assertTrue(validator.isConfigurationChangeAcceptable(updatedConfiguration,
                                                         unacceptableReasons),
               String.valueOf(unacceptableReasons));

    ConfigChangeResult changeResult =
         validator.applyConfigurationChange(updatedConfiguration);
    assertEquals(changeResult.getResultCode(), ResultCode.SUCCESS);

    assertFalse(validator.passwordIsAcceptable(password,
                               new HashSet<ByteString>(0), modifyOperation,
                               userEntry, invalidReason));

    validator.finalizePasswordValidator();
  }
}


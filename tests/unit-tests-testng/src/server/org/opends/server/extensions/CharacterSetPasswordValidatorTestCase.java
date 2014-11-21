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
 *      Portions Copyright 2011-2012 ForgeRock AS
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
import org.opends.server.admin.std.meta.CharacterSetPasswordValidatorCfgDefn;
import org.opends.server.admin.std.server.CharacterSetPasswordValidatorCfg;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

import static org.testng.Assert.*;



/**
 * A set of test cases for the character set password validator.
 */
public class CharacterSetPasswordValidatorTestCase
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
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
         "ds-cfg-character-set: 1:0123456789",
         "ds-cfg-character-set: 1:~!@#$%^&*()-_=+[]{}|;:,.<>/?",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
         "ds-cfg-character-set: 1:0123456789",
         "ds-cfg-character-set: 1:~!@#$%^&*()-_=+[]{}|;:,.<>/?",
         "ds-cfg-allow-unclassified-characters: false",
         "",
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 0:0123456789",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 2",
         "",
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 0:0-9",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 1",
         "",
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:A-Z\u13a0-\u13f4",
         "ds-cfg-character-set: 0:0123456789",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 2");

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
  @Test(dataProvider = "validConfigs", groups= { "slow" })
  public void testInitializeWithValidConfigs(Entry e)
         throws Exception
  {
    CharacterSetPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              CharacterSetPasswordValidatorCfgDefn.getInstance(), e);

    CharacterSetPasswordValidator validator =
         new CharacterSetPasswordValidator();
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
         // Malformed character set definition -- no colon.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: malformed",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character set definition -- colon first.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: :malformed",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character set definition -- colon last.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character set definition -- non-integer count.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: noninteger:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character set definition -- negative count.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: -1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character set definition -- duplicate character in the
         // same character set.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyza",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character set definition -- duplicate character in
         // different character sets.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYz",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed allow unclassified characters.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-allow-unclassified-characters: malformed",
         "",
         // Malformed min-character-sets: too low.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 0:0123456789",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 0",
         "",
         // Malformed min-character-sets: too low.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 0:0123456789",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 1",
         "",
         // Malformed min-character-sets: too high.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
         "ds-cfg-character-set: 0:0123456789",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 3",
         "",
         // Malformed character range definition -- no colon.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: malformed",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character range definition -- colon first.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: :malformed",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character ranges definition -- colon last.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character ranges definition -- non-integer count.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: noninteger:a-z",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character ranges definition -- negative count.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: -1:a-z",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character ranges definition -- incomplete range.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:a-",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character ranges definition -- incomplete first
         // of multiple ranges.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:a-A-Z",
         "ds-cfg-allow-unclassified-characters: true",
         "",
         // Malformed character ranges definition -- incomplete last
         // of multiple ranges.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:a-zA",
         "ds-cfg-allow-unclassified-characters: malformed",
         "",
         // Malformed character ranges definition -- unsorted range.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:z-a",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 0",
         "",
         // Malformed character ranges definition -- no minus.
         "dn: cn=Character Set,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-character-set-password-validator",
         "cn: Character Set",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "CharacterSetPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-character-set-ranges: 1:a_z",
         "ds-cfg-allow-unclassified-characters: true",
         "ds-cfg-min-character-sets: 1");

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
    CharacterSetPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              CharacterSetPasswordValidatorCfgDefn.getInstance(), e);

    CharacterSetPasswordValidator validator =
         new CharacterSetPasswordValidator();
    validator.initializePasswordValidator(configuration);

    StringBuilder buffer = new StringBuilder();
    for (StringBuilder line : e.toLDIF())
    {
      buffer.append(line);
      buffer.append("\n");
    }
    fail(buffer.toString());
  }



  /**
   * Retrieves a set of data to use when testing a given password with a
   * provided configuration.  Each element of the returned array should be an
   * array of a configuration entry, a test password string, and an indication
   * as to whether the provided password should be acceptable.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testData")
  public Object[][] getTestData()
         throws Exception
  {
    return new Object[][]
    {
      // Default configuration, missing characters from multiple character sets.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-character-set: 1:~!@#$%^&*()-_=+[]{}|;:,.<>/?",
             "ds-cfg-allow-unclassified-characters: true"),
        "password",
        false
      },

      // Default configuration, including characters from all of multiple
      // character sets.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-character-set: 1:~!@#$%^&*()-_=+[]{}|;:,.<>/?",
             "ds-cfg-allow-unclassified-characters: true"),
        "PaS$w0rD",
        true
      },

      // Default configuration, including characters from some (but not all) of
      // multiple character sets.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 1:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-character-set: 1:~!@#$%^&*()-_=+[]{}|;:,.<>/?",
             "ds-cfg-allow-unclassified-characters: true"),
        "PaS$worD",
        false
      },

      // Default configuration, including enough characters from a single
      // character set.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-allow-unclassified-characters: true"),
        "password",
        true
      },

      // Default configuration, including too few characters from a single
      // character set.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 6:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-allow-unclassified-characters: true"),
        "short",
        false
      },

      // Default configuration, allowing characters outside of any defined
      // character set.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-allow-unclassified-characters: true"),
        "PaS$w0rD",
        true
      },

      // Default configuration, rejecting characters outside of any defined
      // character set.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 1:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-allow-unclassified-characters: false"),
        "PaS$w0rD",
        false
      },

      // 1 mandatory, 2 optional, must have at least one optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "!@#$%",
        false
      },

      // 1 mandatory, 2 optional, must have at least one optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "0123456",
        false
      },

      // 1 mandatory, 2 optional, must have at least one optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abcDEF",
        false
      },

      // 1 mandatory, 2 optional, must have at least one optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abc123",
        true
      },

      // 1 mandatory, 2 optional, must have at least one optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 1:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abc123ABC",
        true
      },

      // 3 optional, must have at least two optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 0:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abcdef",
        false
      },

      // 3 optional, must have at least two optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 0:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abc123",
        true
      },

      // 3 optional, must have at least two optional.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set: 0:abcdefghijklmnopqrstuvwxyz",
             "ds-cfg-character-set: 0:ABCDEFGHIJKLMNOPQRSTUVWXYZ",
             "ds-cfg-character-set: 0:0123456789",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abc123ABC",
        true
      },

      // 3 optional ranges (one is Thai), must use two.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set-ranges: 0:a-zA-Z",
             "ds-cfg-character-set-ranges: 0:0-9",
             "ds-cfg-character-set-ranges: 0:\u0e01-\u0e5b",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abcABC",
        false
      },

      // 3 optional ranges (one is Thai), must use two.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Character Set,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-character-set-password-validator",
             "cn: Character Set",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "CharacterSetPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-character-set-ranges: 0:a-zA-Z",
             "ds-cfg-character-set-ranges: 0:0-9",
             "ds-cfg-character-set-ranges: 0:\u0e01-\u0e5b",
             "ds-cfg-min-character-sets: 2",
             "ds-cfg-allow-unclassified-characters: false"),
        "abc\u0e17ABC",
        true
      },
    };
  }



  /**
   * Tests the {@code passwordIsAcceptable} method using the provided
   * information.
   *
   * @param  configEntry  The configuration entry to use for the password
   *                      validator.
   * @param  password     The password to test with the validator.
   * @param  acceptable   Indicates whether the provided password should be
   *                      considered acceptable.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testData")
  public void testPasswordIsAcceptable(Entry configEntry, String password,
                                       boolean acceptable)
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

    CharacterSetPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              CharacterSetPasswordValidatorCfgDefn.getInstance(),
              configEntry);

    CharacterSetPasswordValidator validator =
         new CharacterSetPasswordValidator();
    validator.initializePasswordValidator(configuration);

    ByteString pwOS = ByteString.valueOf(password);
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create("userpassword", password)));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                             new ArrayList<Control>(),
                             DN.decode("uid=test.user,o=test"), mods);

    MessageBuilder invalidReason = new MessageBuilder();
    assertEquals(validator.passwordIsAcceptable(pwOS,
                              new HashSet<ByteString>(0), modifyOperation,
                              userEntry, invalidReason),
                 acceptable, invalidReason.toString());

    validator.finalizePasswordValidator();
  }
}


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
 *      Portions Copyright 2011 profiq, s.r.o.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;

import org.opends.server.TestCaseUtils;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.meta.DictionaryPasswordValidatorCfgDefn;
import org.opends.server.admin.std.server.DictionaryPasswordValidatorCfg;
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
 * A set of test cases for the dictionary password validator.
 */
public class DictionaryPasswordValidatorTestCase
       extends ExtensionsTestCase
{
  /**
   * The path to the dictionary file that we have created for the purposes of
   * this test case.
   */
  private static String dictionaryFile;



  /**
   * Ensures that the Directory Server is running.  Also, create a very small
   * test dictionary file to use for the test cases so we don't suffer from
   * loading the real word list every time.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    dictionaryFile = TestCaseUtils.createTempFile(
      "love",
      "sex",
      "secret",
      "god",
      "password"
    );
  }

  /**
   * The Dictionary can take up a lot of memory, so we restart the server to
   * implicitly unregister the validator and free the memory. 
   */
  @AfterClass
  public void freeDictionaryMemory() throws Exception
  {
    TestCaseUtils.restartServer();
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
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-check-substrings: true",
         "ds-cfg-min-substring-length: 3",
         "",
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: true",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-check-substrings: false",
         "",
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-check-substrings: true");

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
    DictionaryPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              DictionaryPasswordValidatorCfgDefn.getInstance(), e);

    DictionaryPasswordValidator validator =
         new DictionaryPasswordValidator();
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
         // Invalid dictionary file
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: invalid",
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-check-substrings: false",
         "",
         // Dictionary file not a file.
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: config",
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-check-substrings: false",
         "",
         // Invalid case-sensitive-validation
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: invalid",
         "ds-cfg-test-reversed-password: true",
         "ds-cfg-check-substrings: false",
         "",
         // Invalid test-reversed-password
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: invalid",
         "ds-cfg-check-substrings: false",
         "",
         // Invalid check-substrings
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: invalid",
         "ds-cfg-check-substrings: invalid",
         "",
         // Invalid min-substring-length
         "dn: cn=Dictionary,cn=Password Validators,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-validator",
         "objectClass: ds-cfg-dictionary-password-validator",
         "cn: Dictionary",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DictionaryPasswordValidator",
         "ds-cfg-enabled: true",
         "ds-cfg-dictionary-file: " + dictionaryFile,
         "ds-cfg-case-sensitive-validation: false",
         "ds-cfg-test-reversed-password: invalid",
         "ds-cfg-check-substrings: true",
         "ds-cfg-min-substring-length: invalid");

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
    DictionaryPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              DictionaryPasswordValidatorCfgDefn.getInstance(), e);

    DictionaryPasswordValidator validator =
         new DictionaryPasswordValidator();
    validator.initializePasswordValidator(configuration);
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
      // Default configuration with a word not in the dictionary.
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-check-substrings: false",
             "ds-cfg-test-reversed-password: true"),
        "notindictionary",
        true
      },

      // Default configuration with a word in the dictionary
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-check-substrings: false",
             "ds-cfg-test-reversed-password: true"),
        "password",
        false
      },

      // Default configuration with a word in the dictionary, case-insensitive
      // matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-check-substrings: false",
             "ds-cfg-test-reversed-password: true"),
        "PaSsWoRd",
        false
      },

      // Default configuration with a word in the dictionary, case-insensitive
      // matching disabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: true",
             "ds-cfg-check-substrings: false",
             "ds-cfg-test-reversed-password: true"),
        "PaSsWoRd",
        true
      },

      // Default configuration with a reverse of a word in the dictionary,
      // reversed matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-test-reversed-password: true"),
        "drowssap",
        false
      },

      // Default configuration with a reverse of a word in the dictionary,
      // reversed matching disabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-test-reversed-password: false"),
        "drowssap",
        true
      },

      // Default configuration with a reverse of a word in the dictionary,
      // reversed matching enabled and case-insensitive matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-test-reversed-password: true"),
        "dRoWsSaP",
        false
      },

      // Default configuration with a reverse of a word in the dictionary,
      // reversed matching enabled and case-insensitive matching disabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: true",
             "ds-cfg-test-reversed-password: true"),
        "dRoWsSaP",
        true
      },
   
      // Substrings checking configuration with a word in the dictionary,
      // case-sensitive matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: true",
             "ds-cfg-check-substrings: true",
             "ds-cfg-min-substring-length: 3",
             "ds-cfg-test-reversed-password: true"),
        "oldpassword",
        false
      },
      
      // Substrings checking configuration with a word in the dictionary,
      // case-sensitive matching disabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-check-substrings: true",
             "ds-cfg-min-substring-length: 3",
             "ds-cfg-test-reversed-password: true"),
        "NewPassword",
        false
      },
      
      // Substrings checking configuration with a word in the dictionary,
      // case-sensitive matching enabled (dictionary word is lower case)
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: true",
             "ds-cfg-check-substrings: true",
             "ds-cfg-min-substring-length: 3",
             "ds-cfg-test-reversed-password: true"),
        "NewPassword",
        true
      },
      
      // Substrings checking configuration with a word in the dictionary,
      // case-sensitive matching disabled, and minimal substring length
      // of 5 while the password is only 3 characters
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-check-substrings: true",
             "ds-cfg-min-substring-length: 5",
             "ds-cfg-test-reversed-password: true"),
        "god",
        false
      },
      
      // Substrings checking configuration with a word in the dictionary,
      // case-sensitive matching disabled, and minimal substring length
      // of 5 while the word in the dictionary is only 3 characters
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-check-substrings: true",
             "ds-cfg-min-substring-length: 5",
             "ds-cfg-test-reversed-password: true"),
        "godblessus",
        true
      },
      
      // Substring checking configuration with a reverse of a word in the 
      // dictionary, reversed matching enabled and case-insensitive 
      // matching enabled
      new Object[]
      {
        TestCaseUtils.makeEntry(
             "dn: cn=Dictionary,cn=Password Validators,cn=config",
             "objectClass: top",
             "objectClass: ds-cfg-password-validator",
             "objectClass: ds-cfg-dictionary-password-validator",
             "cn: Dictionary",
             "ds-cfg-java-class: org.opends.server.extensions." +
                  "DictionaryPasswordValidator",
             "ds-cfg-enabled: true",
             "ds-cfg-dictionary-file: " + dictionaryFile,
             "ds-cfg-case-sensitive-validation: false",
             "ds-cfg-test-reversed-password: true",
             "ds-cfg-check-substrings: true"),
        "sdfdRoWsSaPqwerty",
        false
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

    DictionaryPasswordValidatorCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              DictionaryPasswordValidatorCfgDefn.getInstance(),
              configEntry);

    DictionaryPasswordValidator validator =
         new DictionaryPasswordValidator();
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


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



import java.io.File;
import java.io.FileWriter;
import java.util.List;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.FileBasedKeyManagerProviderCfgDefn;
import org.opends.server.admin.std.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the file-based key manager provider.
 */
public class FileBasedKeyManagerProviderTestCase
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

    FileWriter writer = new FileWriter(DirectoryServer.getInstanceRoot() +
                                       File.separator + "config" +
                                       File.separator + "server.pin");
    writer.write("password" + EOL);
    writer.close();

    writer = new FileWriter(DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "empty");
    writer.close();

    System.setProperty("org.opends.server.KeyStorePIN", "password");
  }



  /**
   * Retrieves a set of valid configurations that can be used to
   * initialize the file-based key manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin: password",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-file: config/server.pin",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-property: org.opends.server.KeyStorePIN",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin: password",
         "ds-cfg-key-store-type: JKS",
         "",
         "dn: cn=Key Manager Provider,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server-cert.p12",
         "ds-cfg-key-store-pin: password",
         "ds-cfg-key-store-type: PKCS12");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an valid configurations.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "validConfigs")
  public void testVvalidConfigs(Entry e)
         throws Exception
  {
    FileBasedKeyManagerProviderCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              FileBasedKeyManagerProviderCfgDefn.getInstance(), e);

    FileBasedKeyManagerProvider provider = new FileBasedKeyManagerProvider();
    provider.initializeKeyManagerProvider(configuration);
    provider.finalizeKeyManagerProvider();
  }



  /**
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize the file-based key manager provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=No Key Store File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-pin: password",
         "",
         "dn: cn=Nonexistent Key Store File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/nosuchfile",
         "ds-cfg-key-store-pin: password",
         "",
         "dn: cn=No Key Store PIN,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "",
         "dn: cn=Nonexistent Key Store PIN File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-file: config/nosuchfile",
         "",
         "dn: cn=Empty Key Store PIN File,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-file: config/empty",
         "",
         "dn: cn=Nonexistent Key Store PIN Property,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-property: nosuchproperty",
         "",
         "dn: cn=Nonexistent Key Store PIN Env Variable,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin-environment-variable: nosuchenv",
         "",
         "dn: cn=Invalid Key Store Type,cn=SSL,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-key-manager-provider",
         "objectClass: ds-cfg-file-based-key-manager-provider",
         "cn: Key Manager Provider",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "FileBasedKeyManagerProvider",
         "ds-cfg-enabled: true",
         "ds-cfg-key-store-file: config/server.keystore",
         "ds-cfg-key-store-pin: password",
         "ds-cfg-key-store-type: invalid");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests initialization with an invalid configuration.
   *
   * @param  e  The configuration entry to use to initialize the identity
   *            mapper.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigs(Entry e)
         throws Exception
  {
    FileBasedKeyManagerProviderCfg configuration =
         AdminTestCaseUtils.getConfiguration(
              FileBasedKeyManagerProviderCfgDefn.getInstance(), e);


    FileBasedKeyManagerProvider provider = new FileBasedKeyManagerProvider();
    provider.initializeKeyManagerProvider(configuration);
  }
}


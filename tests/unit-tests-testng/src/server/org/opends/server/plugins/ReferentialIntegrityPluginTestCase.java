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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 profiq s.r.o.
 */

package org.opends.server.plugins;

import org.testng.annotations.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.admin.std.server.ReferentialIntegrityPluginCfg;
import org.opends.server.admin.std.meta.ReferentialIntegrityPluginCfgDefn;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.Group;
import static org.testng.Assert.assertEquals;
import org.opends.server.core.*;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.HashSet;

/**
 * Unit test to test Referential Integrity plugin.
 */

public class ReferentialIntegrityPluginTestCase extends PluginTestCase  {

  //Config DNs and attributes.
  private DN configDN;
  private String dsConfigAttrType="ds-cfg-attribute-type";
  private String dsConfigBaseDN="ds-cfg-base-dn";
  private String dsConfigUpdateInterval=
                               "ds-cfg-update-interval";
  private String dsConfigEnforceIntegrity = "ds-cfg-check-references";
  private String dsConfigAttrFiltMapping =
    "ds-cfg-check-references-filter-criteria";
  private String dsConfigPluginType = "ds-cfg-plugin-type";

  //Suffixes to use for non-public naming context tests.
  private String exSuffix="dc=example,dc=com";
  private String testSuffix="o=test";

  //dc=example,dc=com entries.
  private String user1="uid=user.1, ou=People, ou=dept," + exSuffix;
  private String user2="uid=user.2, ou=People, ou=dept," + exSuffix;
  private String user3="uid=user.3, ou=People, ou=dept," + exSuffix;

  //Test entry to use for rename tests.
  private String tuser1="uid=user.1, ou=People, ou=dept," + testSuffix;

  //Old superior, new superior and new RDN for move tree tests.
  private String newSuperior="ou=moved dept," + exSuffix;
  private String oldSuperior="ou=people, ou=dept," + exSuffix;
  private String newRdn="ou=moved people";

  //DNs to verfiy that the moved tree test worked.
  private String user1_moved= "uid=user.1," + newRdn + ',' +newSuperior;
  private String user2_moved= "uid=user.2," + newRdn + ',' + newSuperior;
  private String user3_moved= "uid=user.3," + newRdn + ',' + newSuperior;

  //DN to test that the rename test worked.
  private String tuser1_rename=
                              "cn=new user.1, ou=People, ou=dept," + testSuffix;
  private String tuser1_rdn="cn=new user.1";

  //Test DNs to add to various groups.
  private String tuser2="uid=user.2, ou=People, ou=dept," + testSuffix;
  private String tuser3="uid=user.3, ou=People, ou=dept," + testSuffix;

  //Groups to use for member and uniquemember attrbutes in dc=example, dc=com
  //suffix.
  private String group = "cn=group, ou=groups," + exSuffix;
  private String ugroup = "cn=group, ou=unique groups," + exSuffix;

  //DN to use for seeAlso attrbutes.
  private String spPerson = "cn=special person, ou=Special People," + exSuffix;

  //Same as above but for o=test suffix.
  private String tgroup = "cn=group, ou=groups," + testSuffix;
  private String tugroup = "cn=group, ou=unique groups," + testSuffix;
  private String tspPerson =
                         "cn=special person, ou=Special People," + testSuffix;


  /**
   * Test that a move to a new superior changes the correct entries under
   * the correct suffixes.
   *
   * @throws Exception If an unexpected result is returned.
   *
   */
  @Test()
  public void testModDNMoveTree() throws Exception {
    //Add attributes interested in: member, uniquemember, seealso.
    replaceAttrEntry(configDN, dsConfigAttrType,"member");
    addAttrEntry(configDN, dsConfigAttrType,"uniquemember", "seealso");
    //Add suffixes to make referential changes under:
    //o=test, and cn=group, ou=unique groups, dc=example, dc=com
    replaceAttrEntry(configDN, dsConfigBaseDN, testSuffix);
    addAttrEntry(configDN, dsConfigBaseDN, ugroup);
    //Add DNs to groups and special entries
    addAttrEntry(DN.decode(tgroup), "member", user1, user2, user3);
    addAttrEntry(DN.decode(tugroup), "uniquemember", user1, user2, user3);
    addAttrEntry(DN.decode(ugroup), "uniquemember", user1, user2, user3);
    addAttrEntry(DN.decode(spPerson), "seealso", user1, user2, user3);
    //Perform the move.
    doModDN(oldSuperior, newRdn, newSuperior);
    //This group under the suffix all DNs should be moved.
    isMember(tgroup, true, user1_moved, user2_moved, user3_moved);
    //This group under a suffix all DNs should be moved.
    isAttributeValueEntry(tugroup, true, "uniquemember",
                          user1_moved, user2_moved, user3_moved);
     //This group under a suffix all DNs should be moved.
    isAttributeValueEntry(ugroup, true, "uniquemember",
                          user1_moved, user2_moved, user3_moved);
     //This group  not under a suffix, old entries should exist.
    isAttributeValueEntry(spPerson, true,"seealso",
                          user1, user2, user3);
  }


  /**
   * Test that a delete subtree changes the correct entries
   * under the correct suffixes.
   *
   * @throws Exception If an unexpected result is returned.
   *
   */
  @Test()
  public void testReferentialDeleteTree() throws Exception {
    // Add attributes interested in: member, uniquemember, seealso.
    replaceAttrEntry(configDN, dsConfigAttrType,"member");
    addAttrEntry(configDN, dsConfigAttrType,"uniquemember", "seealso");

    // Add suffixes to make referential changes under:
    // o=test, and cn=group, ou=unique groups, dc=example, dc=com
    replaceAttrEntry(configDN, dsConfigBaseDN, testSuffix);
    addAttrEntry(configDN, dsConfigBaseDN, ugroup);

    // Add DNs to groups and special entries
    addAttrEntry(DN.decode(tgroup), "member", user1, user2, user3);
    addAttrEntry(DN.decode(tugroup), "uniquemember", user1, user2, user3);
    addAttrEntry(DN.decode(ugroup), "uniquemember", user1, user2, user3);
    addAttrEntry(DN.decode(spPerson), "seealso", user1, user2, user3);

    // Check group membership before delete.
    isMember(tgroup, true, user1, user2, user3);

    // Check values exist as before delete.
    isAttributeValueEntry(tgroup, true, "member", user1, user2, user3);
    isAttributeValueEntry(tugroup, true, "uniquemember", user1, user2, user3);
    isAttributeValueEntry(ugroup, true, "uniquemember", user1, user2, user3);
    isAttributeValueEntry(spPerson, true, "seealso", user1, user2, user3);

    // Perform the subtree delete.
    deleteSubtree(oldSuperior);

    // Check that the group cache is updated.
    isMember(tgroup, false, user1, user2, user3);

    // Check values exist as before delete.
    isAttributeValueEntry(tgroup, false, "member", user1, user2, user3);
    isAttributeValueEntry(tugroup, false, "uniquemember", user1, user2, user3);
    isAttributeValueEntry(ugroup, false, "uniquemember", user1, user2, user3);

    // This entry not managed by RI.
    isAttributeValueEntry(spPerson, true, "seealso", user1, user2, user3);
  }

   /**
   * Test that a rename  changes the correct entries under
   * the correct suffixes.
   *
   * @throws Exception If an unexpected result is returned.
    *
   */

  @Test()
  public void testModDNMoveEntry() throws Exception {
    //Add attributes interested in: member, uniquemember, seealso.
    replaceAttrEntry(configDN, dsConfigAttrType,"member");
    addAttrEntry(configDN, dsConfigAttrType,"uniquemember", "seealso");
    //Add suffixes to make referential changes under:
    //dc=example,dc=com and o=group, ou=unique groups, o=test
    replaceAttrEntry(configDN, dsConfigBaseDN, exSuffix);
    addAttrEntry(configDN, dsConfigBaseDN, tugroup);
    //Add DNs to groups and special entry
    addAttrEntry(DN.decode(group), "member", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(ugroup), "uniquemember", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tugroup), "uniquemember", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tspPerson), "seealso", tuser1, tuser2, tuser3);
    //Perform rename.
    doModDN(tuser1,tuser1_rdn, null);
    //Verify that the changes were made.
    isMember(group, true, tuser1_rename, tuser2, tuser3);
    isAttributeValueEntry(ugroup, true, "uniquemember",
                          tuser1_rename, tuser2, tuser3);
    isAttributeValueEntry(tugroup, true, "uniquemember",
                          tuser1_rename, tuser2, tuser3);
    isAttributeValueEntry(tspPerson, true,"seealso",
                          tuser1, tuser2, tuser3);
  }


  /**
   * Test a delete using public naming contexts as base DNs.
   *
   * @throws Exception If an unexpected result is returned.
   *
   */
  @Test()
  public void testReferentialDelete() throws Exception {
   replaceAttrEntry(configDN, dsConfigAttrType,"member");
   addAttrEntry(DN.decode(tgroup), "member", tuser1, tuser2, tuser3);
   deleteEntries(tuser1, tuser2, tuser3);
   isMember(tgroup, false, tuser1, tuser2, tuser3);
  }


  /**
   * Test that delete using public naming context works in both background
   * processing (set interval to 1 and wait 2 seconds) and forground. The
   * changes are made without restarting the server.
   *
   * @throws Exception If an unexpected result happens.
   *
   */
  @Test()
   public void testReferentialDeleteBackGround() throws Exception {
    replaceAttrEntry(configDN, dsConfigAttrType,"member");
    //Set interval to 1 second, this should start the background thread
    //and put the plugin in background mode.
    replaceAttrEntry(configDN, dsConfigUpdateInterval,"1 seconds");
    addAttrEntry(DN.decode(tgroup), "member", tuser1, tuser2, tuser3);
    deleteEntries(tuser1, tuser2, tuser3);
    //Wait two seconds and then check the group.
    Thread.sleep(2000);
    isMember(tgroup, false, tuser1, tuser2, tuser3);
    //Change the interval to zero seconds, this should stop the background
    //thread.
    replaceAttrEntry(configDN, dsConfigUpdateInterval,"0 seconds");
    addEntries(tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tgroup), "member", tuser1, tuser2, tuser3);
    deleteEntries(tuser1, tuser2, tuser3);
    //Don't wait, the changes should be there.
    isMember(tgroup, false, tuser1, tuser2, tuser3);
   }

  /**
   * Test delete using multiple attribute types and public naming contexts.
   *
   * @throws Exception If an unexpected result happened.
   *
   */
  @Test()
   public void testReferentialDeleteAttrs() throws Exception {
    replaceAttrEntry(configDN, dsConfigAttrType,"member");
    addAttrEntry(configDN, dsConfigAttrType,"uniquemember", "seealso");
    addAttrEntry(DN.decode(tgroup), "member", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tugroup), "uniquemember", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tspPerson), "seealso", tuser1, tuser2, tuser3);
    deleteEntries(tuser1, tuser2, tuser3);
    isMember(tgroup, false, tuser1, tuser2, tuser3);
    isAttributeValueEntry(tugroup, false, "uniquemember",
                          tuser1, tuser2, tuser3);
    isAttributeValueEntry(tspPerson, false,"seealso",
                          tuser1, tuser2, tuser3);
   }

  /**
   * Check delete with multiple attribute types and multiple suffixes.
   *
   * @throws Exception If an unexpected result happened.
   *
   */
  @Test()
   public void testReferentialDeleteAttrsSuffix() throws Exception {
    replaceAttrEntry(configDN, dsConfigAttrType,"member");
    addAttrEntry(configDN, dsConfigAttrType,"uniquemember", "seealso");
    replaceAttrEntry(configDN, dsConfigBaseDN, exSuffix);
    addAttrEntry(configDN, dsConfigBaseDN, tugroup);
    addAttrEntry(DN.decode(group), "member", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(ugroup), "uniquemember", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tugroup), "uniquemember", tuser1, tuser2, tuser3);
    addAttrEntry(DN.decode(tspPerson), "seealso", tuser1, tuser2, tuser3);
    deleteEntries(tuser1, tuser2, tuser3);
    isMember(group, false, tuser1, tuser2, tuser3);
    isAttributeValueEntry(ugroup, true, "uniquemember",
                          tuser1, tuser2, tuser3);
    isAttributeValueEntry(tugroup, false, "uniquemember",
                          tuser1, tuser2, tuser3);
    isAttributeValueEntry(tspPerson, true,"seealso",
                          tuser1, tuser2, tuser3);
   }

  /**
   * Retrieves a set of valid configuration entries that may be used to
   * initialize the plugin.
   *
   * @return An array of config entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   *
   */
  @DataProvider(name = "validConfigs")
  public Object[][] getValidConfigs()
          throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "ds-cfg-update-interval: 300 seconds",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "ds-cfg-update-interval: 300 seconds",
            "ds-cfg-log-file: logs/test",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "ds-cfg-update-interval: 300 seconds",
            "ds-cfg-log-file: logs/test",
            "",
            // check-references, default
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-check-references: false",
            "",
            // check-references enabled
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "",
            // check-references enabled, check-references-filter-criteria set
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "ds-cfg-check-references-filter-criteria: member:(objectclass=person)",
            "",
            // check-references disabled, check-references-filter-criteria set
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-check-references: false",
            "ds-cfg-check-references-filter-criteria: member:(objectclass=person)"
    );
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
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   *
   */
  @Test(dataProvider = "validConfigs")
  public void testInitializeWithValidConfigs(Entry e)
          throws Exception
  {
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList){
      for (AttributeValue v : a)
        pluginTypes.add(PluginType.forName(
            v.getValue().toString().toLowerCase()));
    }
    ReferentialIntegrityPluginCfg configuration =
            AdminTestCaseUtils.getConfiguration(
                    ReferentialIntegrityPluginCfgDefn.getInstance(), e);
    ReferentialIntegrityPlugin plugin = new ReferentialIntegrityPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }

  /**
   * Retrieves a set of invalid configuration entries that may be used to
   * initialize the plugin.
   *
   * @return An array of config entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   *
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInValidConfigs()
          throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: cn",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: sn",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: preOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: baddn",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "ds-cfg-update-interval: -5 seconds",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: notanattribute",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "ds-cfg-update-interval: 300 seconds",
            "ds-cfg-log-file: logs/test",
            "",
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-attribute-type: member",
            "ds-cfg-attribute-type: uniqueMember",
            "ds-cfg-attribute-type: seeAlso",
            "ds-cfg-base-dn: ou=people, dc=example,dc=com",
            "ds-cfg-base-dn: ou=dept, dc=example,dc=com",
            "ds-cfg-base-dn: ou=people, o=test",
            "ds-cfg-update-interval: 300 seconds",
            "ds-cfg-log-file: /hopefully/doesn't/file/exist",
            "",
            // check-references bad value
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: bad",
            "ds-cfg-check-references-filter-criteria: member:(objectclass=person)",
            "",
            // check-references enabled, attrbute not on the list
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "ds-cfg-check-references-filter-criteria: manager:(objectclass=person)",
            "",
            // check-references true, bad filter
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "ds-cfg-check-references-filter-criteria: member:bad",
            "",
            // check-references true, attr-filt bad format
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "ds-cfg-check-references-filter-criteria: bad",
            "",
            // check-references true, no filter
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "ds-cfg-check-references-filter-criteria: member:",
            "",
            // check-references true, null:null
            "dn: cn=Referential Integrity,cn=Plugins,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-plugin",
            "objectClass: ds-cfg-referential-integrity-plugin",
            "cn: Referential Integrity",
            "ds-cfg-java-class: org.opends.server.plugins.ReferentialIntegrityPlugin",
            "ds-cfg-enabled: true",
            "ds-cfg-plugin-type: postOperationDelete",
            "ds-cfg-plugin-type: postOperationModifyDN",
            "ds-cfg-plugin-type: subordinateModifyDN",
            "ds-cfg-plugin-type: preOperationAdd",
            "ds-cfg-plugin-type: preOperationModify",
            "ds-cfg-attribute-type: member",
            "ds-cfg-base-dn: o=test",
            "ds-cfg-base-dn: dc=example, dc=com",
            "ds-cfg-check-references: true",
            "ds-cfg-check-references-filter-criteria: :"
    );
    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }


  /**
   * Tests the process of initializing the server with inValid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   *
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class })
  public void testInitializeWithInValidConfigs(Entry e)
          throws Exception
  {
    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    List<Attribute> attrList = e.getAttribute("ds-cfg-plugin-type");
    for (Attribute a : attrList){
      for (AttributeValue v : a)
        pluginTypes.add(PluginType.forName(
            v.getValue().toString().toLowerCase()));
    }
    ReferentialIntegrityPluginCfg configuration =
            AdminTestCaseUtils.getConfiguration(
                    ReferentialIntegrityPluginCfgDefn.getInstance(), e);
    ReferentialIntegrityPlugin plugin = new ReferentialIntegrityPlugin();
    plugin.initializePlugin(pluginTypes, configuration);
    plugin.finalizePlugin();
  }

  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   *
   */
  @BeforeClass()
  public void startServer()
          throws Exception
  {
    TestCaseUtils.startServer();
    configDN= DN.decode("cn=Referential Integrity ,cn=Plugins,cn=config");
  }

  /**
   * Clears configuration information before each method run and re-adds
   * entries.
   *
   * @throws Exception If an unexpected problem occurs.
   *
   */
  @BeforeMethod
  public void clearConfigEntries() throws Exception {
    deleteAttrsEntry(configDN, dsConfigBaseDN);
    deleteAttrsEntry(configDN, dsConfigEnforceIntegrity);
    deleteAttrsEntry(configDN, dsConfigAttrFiltMapping);
    //Hopefully put an attribute type there that won't impact the rest of the
    //unit tests.
    replaceAttrEntry(configDN, dsConfigAttrType,"seeAlso");
    replaceAttrEntry(configDN, dsConfigUpdateInterval,"0 seconds");
    TestCaseUtils.initializeTestBackend(true);
    addTestEntries("o=test");
    TestCaseUtils.clearJEBackend(true,"userRoot", "dc=example,dc=com");
    addTestEntries("dc=example,dc=com");
  }


  /**
   * Clears things up after the unit test is completed.
   *
   * @throws Exception If an unexpected problem occurs.
   *
   */
  @AfterClass
  public void tearDown() throws Exception {
     deleteAttrsEntry(configDN, dsConfigBaseDN);
    //Hopefully put an attribute type there that won't impact the rest of the
    //unit tests.
    replaceAttrEntry(configDN, dsConfigAttrType,"seeAlso");
    replaceAttrEntry(configDN, dsConfigUpdateInterval,"0 seconds");
    TestCaseUtils.clearJEBackend(false,"userRoot", "dc=example,dc=com");
  }

  /**
   * Create entries under the specified suffix and add them to the server.
   * The character argument is used to make the mail attribute unique.
   *
   * @param suffix  The suffix to use in building the entries.
   *
   * @throws Exception If a problem occurs.
   *
   */
  private void addTestEntries(String suffix) throws Exception {
    TestCaseUtils.addEntries(
            "dn: ou=dept," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: dept",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: ou=moved dept," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: moved dept",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: ou=groups," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: groups",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: ou=unique Groups," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: unique Groups",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: ou=People, ou=dept," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: People",
            "",
            "dn: ou=Special People," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: Special People",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: cn=special person, ou=Special People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 1",
            "givenName: User",
            "sn: 1",
            "cn: special person",
            "userPassword: password",
            "mail: user1" +"@test",
            "employeeNumber: 1",
            "mobile: 1-111-1234",
            "pager: 1-111-5678",
            "description: Use for seeAlso attribute",
            "",
            "dn: cn=group, ou=groups," + suffix,
            "objectClass: top",
            "objectClass: groupOfNames",
            "cn: group",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: cn=group, ou=unique groups," + suffix,
            "objectClass: top",
            "objectClass: groupOfUniqueNames",
            "cn: group",
            "aci: (targetattr= \"*\")" +
                    "(version 3.0; acl \"allow all\";" +
                    "allow(all) userdn=\"ldap:///anyone\";)",
            "",
            "dn: uid=user.1, ou=People, ou=dept," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 1",
            "givenName: User",
            "sn: 1",
            "cn: User 1",
            "userPassword: password",
            "mail: user1" +"@test",
            "employeeNumber: 1",
            "mobile: 1-111-1234",
            "pager: 1-111-5678",
            "telephoneNumber: 1-111-9012",
            "",
            "dn: uid=user.2, ou=People, ou=dept," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 2",
            "givenName: User",
            "sn: 2",
            "cn: User 2",
            "mail: user2" + "@test",
            "userPassword: password",
            "employeeNumber: 2",
            "mobile: 1-222-1234",
            "pager: 1-222-5678",
            "telephoneNumber: 1-222-9012",
            "",
            "dn: uid=user.3, ou=People, ou=dept," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 3",
            "givenName: User",
            "sn: 3",
            "cn: User 3",
            "mail: user3" + "@test",
            "userPassword: password",
            "employeeNumber: 3",
            "mobile: 1-333-1234",
            "pager: 1-333-5678",
            "telephoneNumber: 1-333-9012",
            "",
            "dn: uid=user.4, ou=People, ou=dept," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 4",
            "givenName: User",
            "sn: 4",
            "cn: User 4",
            "mail: user4" + "@test",
            "userPassword: password",
            "employeeNumber: 4",
            "mobile: 1-444-1234",
            "pager: 1-444-5678",
            "telephoneNumber: 1-444-9012",
            "",
            "dn: uid=user.5, ou=People, ou=dept," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 5",
            "givenName: User",
            "sn: 5",
            "cn: User 5",
            "mail: user5" + "@test",
            "userPassword: password",
            "employeeNumber: 5",
            "mobile: 1-555-1234",
            "pager: 1-555-5678",
            "telephoneNumber: 1-555-9012"
    );
  }

  /**
   * Add specified attr type and type values to the entry specified by dn.
   *
   * @param dn The dn of the entry to add the attribute and values to.
   *
   * @param attrTypeString The attribute type to add the values to.
   *
   * @param attrValStrings The values to add to the entry.
   *
   */
  private ModifyOperation
  addAttrEntry(DN dn, String attrTypeString, String... attrValStrings) {
    LinkedList<Modification> mods = new LinkedList<Modification>();
    AttributeType attrType = getAttrType(attrTypeString);
    AttributeBuilder builder = new AttributeBuilder(attrType, attrTypeString);
    for(String valString : attrValStrings) {
      builder.add(valString);
    }
    mods.add(new Modification(ModificationType.ADD, builder.toAttribute()));
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    return conn.processModify(dn, mods);
  }

/**
   * Replace specified attr type and type values to the entry specified by dn.
   *
   * @param dn The dn of the entry to replace the attribute and values to.
   *
   * @param attrTypeString The attribute type to replace the values in.
   *
   * @param attrValStrings The values to replace in the the entry.
 *
   */
  private ModifyOperation
  replaceAttrEntry(DN dn, String attrTypeString, String... attrValStrings) {
    LinkedList<Modification> mods = new LinkedList<Modification>();
    AttributeType attrType = getAttrType(attrTypeString);
    AttributeBuilder builder = new AttributeBuilder(attrType, attrTypeString);
    for(String valString : attrValStrings) {
      builder.add(valString);
    }
    mods.add(new Modification(ModificationType.REPLACE, builder.toAttribute()));
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    return conn.processModify(dn, mods);
  }


  /**
   * Remove the attributes specified by the attribute type strings from the
   * entry corresponding to the dn argument.
   *
   * @param dn The entry to remove the attributes from.
   *
   * @param attrTypeStrings The attribute type string list to remove from the
   *                        entry.
   *
   * @throws Exception  If an error occurs.
   *
   */
  private void
  deleteAttrsEntry(DN dn, String... attrTypeStrings) throws Exception {
    LinkedList<Modification> mods = new LinkedList<Modification>();
    for(String attrTypeString : attrTypeStrings) {
      AttributeType attrType = getAttrType(attrTypeString);
      mods.add(new Modification(ModificationType.DELETE,
          Attributes.empty(attrType)));
    }
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    conn.processModify(dn, mods);
  }

  /**
   * Return the attribute type corresponding to the attribute type string.
   *
   * @param attrTypeString  The attribute type string name.
   *
   * @return  An attribute type object pertaining to the string.
   *
   */
  private AttributeType getAttrType(String attrTypeString) {
    AttributeType attrType =
            DirectoryServer.getAttributeType(attrTypeString);
    if (attrType == null)
      attrType = DirectoryServer.getDefaultAttributeType(attrTypeString);
    return attrType;
  }

  private void deleteEntries(String... dns) throws Exception{
    InternalClientConnection conn =
                                 InternalClientConnection.getRootConnection();
    for(String dn : dns) {
         DeleteOperation op=conn.processDelete(DN.decode(dn));
       assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    }
  }



  private void deleteSubtree(String... dns) throws Exception
  {
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    SubtreeDeleteControl control = new SubtreeDeleteControl(true);
    List<Control> controls = new ArrayList<Control>(1);
    controls.add(control);

    for (String dn : dns)
    {
      DeleteOperation op = conn.processDelete(DN.decode(dn), controls);
      assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    }
  }

  /**
   * Check membership in a static group of the specified dns. The expected
   * boolean is used to check if the dns are expected or not expected in the
   * groups.
   *
   * @param group The group to check membership in.
   *
   * @param expected Set to <code>true</code> if the dns are expected in the
   *                 groups.
   *
   * @param dns The dns to check membership for.
   *
   * @throws Exception If an unexpected membership occurs.
   *
   */
  private void isMember(String group, boolean expected, String... dns)
  throws Exception {
   GroupManager groupManager=DirectoryServer.getGroupManager();
   Group<?> instance=groupManager.getGroupInstance(DN.decode(group));
   for(String dn : dns)
     assertEquals(instance.isMember(DN.decode(dn)), expected);
  }

  private void isAttributeValueEntry(String entryDN, boolean expected,
                                     String attr,
                                     String... dns)
          throws Exception {
    AttributeType type= getAttrType(attr);
    String filterStr="(" + attr + "=*)";
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    InternalSearchOperation operation = conn.processSearch(DN.decode(entryDN),
            SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
            SearchFilter.createFilterFromString(filterStr),
            null);
    for (SearchResultEntry entry : operation.getSearchEntries()) {
      for(String dn : dns) {
        AttributeValue value = AttributeValues.create(type, dn);
        assertEquals(entry.hasValue(type, null, value), expected);
      }
    }
  }

  /**
   * Add the entries created using the specified DNs to the server.
   *
   * @param dns The dns to use in entry creation.
   *
   * @throws Exception If an unexpected result happens.
   *
   */
  private void addEntries(String... dns) throws Exception {
        InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for(String dn : dns) {
      Entry e=makeEntry(dn);
      AddOperation op=conn.processAdd(e);
      assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    }
  }

  /**
   * Make a entry with the specified dn.
   *
   * @param dn The dn of the entry.
   * @return The created entry.
   * @throws Exception  If the entry can't be created.
   */
  private Entry makeEntry(String dn) throws Exception {
      return TestCaseUtils.makeEntry(
            "dn: " + dn,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: 1",
            "givenName: test",
            "sn: 1",
            "cn: test"
    );
  }

  /**
   * Perform modify DN operation.
   *
   * @param dn  The DN to renmame or move.
   *
   * @param rdn RDN value.
   *
   * @param newSuperior New superior to move to.
   *
   * @throws Exception If the operation can't be performed.
   *
   */
  private void
  doModDN(String dn, String rdn, String newSuperior) throws Exception {
    InternalClientConnection conn =
            InternalClientConnection.getRootConnection();
    ModifyDNOperation modDNop;
    if(newSuperior != null)
        modDNop = conn.processModifyDN(DN.decode(dn), RDN.decode(rdn), true,
                                       DN.decode(newSuperior));
    else
        modDNop = conn.processModifyDN(DN.decode(dn), RDN.decode(rdn),
                                       false, null);
    assertEquals(modDNop.getResultCode(), ResultCode.SUCCESS);
  }

    /**
   * Test case:
   * - integrity is enforced on the attribute 'manager'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a user 'manager' to the 'dc=example,dc=com'
   * - add a user 'employee' with the attribute manager which points to the
   *   entry 'manager'
   * - SUCCESS
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddUserWitManagerFilterNoNC()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    deleteAttrsEntry(configDN, dsConfigBaseDN);
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    Entry entry = null;
    AddOperation addOperation = null;

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    entry = makeEntry("uid=manager,ou=people,ou=dept,o=test");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    entry = TestCaseUtils.makeEntry(
      "dn: uid=employee,ou=people,ou=dept,dc=example,dc=com",
      "objectclass: top",
      "objectclass: person",
      "objectclass: organizationalperson",
      "objectclass: inetorgperson",
      "uid: employee",
      "cn: employee",
      "sn: employee",
      "givenname: employee",
      "manager: uid=manager,ou=people,ou=dept,o=test");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - integrity is enforced on the attribute 'manager'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a user 'manager' to the 'dc=example,dc=com'
   * - add a user 'employee' with the attribute manager which points to the
   *   entry 'manager'
   * - SUCCESS
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddUserWitManagerFilter()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    Entry entry = null;
    AddOperation addOperation = null;

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    entry = makeEntry("uid=manager,ou=people,ou=dept,dc=example,dc=com");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    entry = TestCaseUtils.makeEntry(
      "dn: uid=employee,ou=people,ou=dept,dc=example,dc=com",
      "objectclass: top",
      "objectclass: person",
      "objectclass: organizationalperson",
      "objectclass: inetorgperson",
      "uid: employee",
      "cn: employee",
      "sn: employee",
      "givenname: employee",
      "manager: uid=manager,ou=people,ou=dept,dc=example,dc=com");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }

    /**
   * Test case:
   * - integrity is enforced on the attribute 'manager'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a user 'employee' with the attribute manager which points to the
   *   entry 'manager' which doesn't exist
   * - CONSTRAINT VIOLATION
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddUserWithMissingManagerEntry()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    Entry entry = null;
    AddOperation addOperation = null;
    
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    entry = TestCaseUtils.makeEntry(
      "dn: uid=employee,ou=people,ou=dept,dc=example,dc=com",
      "objectclass: top",
      "objectclass: person",
      "objectclass: organizationalperson",
      "objectclass: inetorgperson",
      "uid: employee",
      "cn: employee",
      "sn: employee",
      "givenname: employee",
      "manager: uid=bad,ou=people,ou=dept,dc=example,dc=com");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

    /**
   * Test case:
   * - integrity is enforced on the attribute 'manager'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=groupOfNames)
   * - add a user 'manager' with the object class 'person' to the
   *   'dc=example,dc=com'
   * - add a user 'employee' with the attribute manager which points to the
   *   entry 'manager'
   * - CONSTRAINT VIOLATION
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddUserWitManagerFilterMismatch()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=gropuOfNames)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    Entry entry = null;
    AddOperation addOperation = null;
    
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    entry = makeEntry("uid=manager,ou=people,ou=dept,dc=example,dc=com");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    entry = TestCaseUtils.makeEntry(
      "dn: uid=employee,ou=people,ou=dept,dc=example,dc=com",
      "objectclass: top",
      "objectclass: person",
      "objectclass: organizationalperson",
      "objectclass: inetorgperson",
      "uid: employee",
      "cn: employee",
      "sn: employee",
      "givenname: employee",
      "manager: uid=manager,ou=people,ou=dept,dc=example,dc=com");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

    /**
   * Test case:
   * - integrity is enforced on the attribute 'manager'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a user 'manager' to the 'o=test'
   * - add a user 'employee' with the attribute manager which points to the
   *   entry 'manager'
   * - SUCCESS
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddUserWithManagerNC()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN,
                           "dc=example,dc=com",
                           "o=test");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    Entry entry = null;
    AddOperation addOperation = null;

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    entry = makeEntry("uid=manager,ou=people,ou=dept,o=test");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    entry = TestCaseUtils.makeEntry(
      "dn: uid=employee,ou=people,ou=dept,dc=example,dc=com",
      "objectclass: top",
      "objectclass: person",
      "objectclass: organizationalperson",
      "objectclass: inetorgperson",
      "uid: employee",
      "cn: employee",
      "sn: employee",
      "givenname: employee",
      "manager: uid=manager,ou=people,ou=dept,o=test");

    addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(),
                 ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - integrity is enforced on the attribute 'member'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a group 'referent group' to the 'dc=example,dc=com' with the
   *   'member' attribute pointing to the existing user entries
   * - SUCCESS
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddGroupWithFilter()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=referent group,ou=groups,dc=example,dc=com",
      "objectclass: top",
      "objectclass: groupofnames",
      "cn: refetent group",
      "member: uid=user.1,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.2,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.3,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.4,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.5,ou=people,ou=dept,dc=example,dc=com"
      );

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - integrity is enforced on the attribute 'member'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a group 'referent group' to the 'dc=example,dc=com' with the
   *   'member' attribute pointing to the existing user entries and one missing
   * - CONSTRAINT VIOLATION
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddGroupWithMissingMember()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=referent group,ou=groups,dc=example,dc=com",
      "objectclass: top",
      "objectclass: groupofnames",
      "cn: refetent group",
      "member: uid=user.1,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.2,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=bad,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.4,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.5,ou=people,ou=dept,dc=example,dc=com"
      );

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), 
                 ResultCode.CONSTRAINT_VIOLATION);
  }

  /**
   * Test case:
   * - integrity is enforced on the attribute 'member'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a group 'referent group' to the 'dc=example,dc=com' with the
   *   'member' attribute pointing to the existing user entries and one entry
   *   being of object class groupOfNames
   * - CONSTRAINT VIOLATION
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddGroupMemberFilterMismatch()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=referent group,ou=groups,dc=example,dc=com",
      "objectclass: top",
      "objectclass: groupofnames",
      "cn: refetent group",
      "member: uid=user.1,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.2,ou=people,ou=dept,dc=example,dc=com",
      "member: cn=group,ou=groups,dc=example,dc=com",
      "member: uid=user.4,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.5,ou=people,ou=dept,dc=example,dc=com"
      );

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

  /**
   * Test case:
   * - integrity is enforced on the attribute 'member'
   * - value of the 'manager' attribute should match the filter:
   *  (objectclass=person)
   * - add a group 'referent group' to the 'dc=example,dc=com' with the
   *   'member' attribute pointing to the existing user entries with one memeber
   *   belonging to 'o=test' naming context
   * - SUCCESS
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityAddGroupMemberNC()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN,
                           "dc=example,dc=com",
                           "o=test");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=referent group,ou=groups,dc=example,dc=com",
      "objectclass: top",
      "objectclass: groupofnames",
      "cn: refetent group",
      "member: uid=user.1,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.2,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.3,ou=people,ou=dept,o=test",
      "member: uid=user.4,ou=people,ou=dept,dc=example,dc=com",
      "member: uid=user.5,ou=people,ou=dept,dc=example,dc=com"
      );

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(),
                 ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - employee entry exists
   * - manager entry exists
   * - add 'manager' attribute to the manager entry
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityModifyUserAddManagerFilter()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = makeEntry("uid=manager,ou=people,ou=dept,dc=example,dc=com");

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    ModifyOperation modOperation = addAttrEntry(DN.decode(user1),
     "manager", "uid=manager,ou=people,ou=dept,dc=example,dc=com");
    assertEquals(modOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - employee entry exists with 'manager' attribute pointing to the
   *   manager entry
   * - manager entry exists
   * - user.2 entry exists
   * - modify 'manager' attribute to the 'user.2' entry
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityModifyUserModifyManagerFilter()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = makeEntry("uid=manager,ou=people,ou=dept,dc=example,dc=com");

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    ModifyOperation modOperation = addAttrEntry(DN.decode(user1),
     "manager", "uid=manager,ou=people,ou=dept,dc=example,dc=com");
    assertEquals(modOperation.getResultCode(), ResultCode.SUCCESS);

    modOperation = replaceAttrEntry(DN.decode(user1),
                                    "manager", user2);
    assertEquals(modOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - filter is set to posixAccount
   * - employee entry exists
   * - manager entry exists with objectclass person
   * - add 'manager' attribute to the manager entry
   * - constraint violation
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityModifyUserAddManagerFilterMismatch()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=posixAccount)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = makeEntry("uid=manager,ou=people,ou=dept,dc=example,dc=com");

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    ModifyOperation modOperation = addAttrEntry(DN.decode(user1),
     "manager", "uid=manager,ou=people,ou=dept,dc=example,dc=com");
    assertEquals(modOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

  /**
   * Test case:
   * - employee entry exists in dc=example,dc=com
   * - manager entry exists in o=other
   * - add 'manager' attribute to the employee poiting to the manager
   *   entry
   * - SUCCESS
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityModifyUserAddManagerNC()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN,
                           "dc=example,dc=com",
                           "o=test");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    Entry entry = makeEntry("uid=manager,ou=people,ou=dept,o=test");

    AddOperation addOperation = conn.processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

    ModifyOperation modOperation = addAttrEntry(DN.decode(user1),
     "manager", "uid=manager,ou=people,ou=dept,o=test");
    assertEquals(modOperation.getResultCode(),
                 ResultCode.SUCCESS);
  }

  /**
   * Test case:
   * - employee entry exists
   * - manager entry does not exist
   * - add 'manager' attribute to the employee
   * - constraint violation
   * @throws Exception
   */
  @Test()
  public void testEnforceIntegrityModifyUserAddManagerMissing()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "manager");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "manager:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    ModifyOperation modOperation = addAttrEntry(DN.decode(user1),
     "manager", "uid=manager,ou=people,ou=dept,dc=example,dc=com");
    assertEquals(modOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

  @Test()
  public void testEnforceIntegrityModifyGroupAddMember()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    ModifyOperation modOperation = addAttrEntry(DN.decode(group),
                                                "member",
                                                user1);
    assertEquals(modOperation.getResultCode(), ResultCode.SUCCESS);
  }

  @Test()
  public void testEnforceIntegrityModifyGroupAddMissingMember()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    ModifyOperation modOperation = addAttrEntry(DN.decode(group),
      "member", "uid=user.100,ou=people,ou=dept,dc=example,dc=com");
    assertEquals(modOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

  @Test()
  public void testEnforceIntegrityModifyGroupAddMemberFilterMismatch()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, "dc=example,dc=com");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=posixaccount)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    ModifyOperation modOperation = addAttrEntry(DN.decode(group),
      "member", "uid=user.100,ou=people,ou=dept,dc=example,dc=com");
    assertEquals(modOperation.getResultCode(),
                 ResultCode.CONSTRAINT_VIOLATION);
  }

  @Test()
  public void testEnforceIntegrityModifyGroupAddMemberNC()
    throws Exception
  {
    replaceAttrEntry(configDN, "ds-cfg-enabled", "false");
    replaceAttrEntry(configDN, dsConfigPluginType,
                               "postoperationdelete",
                               "postoperationmodifydn",
                               "subordinatemodifydn",
                               "subordinatedelete",
                               "preoperationadd",
                               "preoperationmodify");
    addAttrEntry(configDN, dsConfigBaseDN, 
                           "dc=example,dc=com",
                           "o=test");
    replaceAttrEntry(configDN, dsConfigEnforceIntegrity, "true");
    replaceAttrEntry(configDN, dsConfigAttrType, "member");
    addAttrEntry(configDN, dsConfigAttrFiltMapping,
                           "member:(objectclass=person)");
    replaceAttrEntry(configDN, "ds-cfg-enabled", "true");

    ModifyOperation modOperation = addAttrEntry(DN.decode(group),
      "member", "uid=user.1,ou=people,ou=dept,o=test");
    assertEquals(modOperation.getResultCode(),
                 ResultCode.SUCCESS);
  }
}

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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.Assert;
import static org.opends.server.util.ServerConstants.OID_MANAGE_DSAIT_CONTROL;
import static org.opends.server.config.ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI;

import java.io.StringReader;
import java.io.BufferedReader;
import java.io.IOException;


/**
 * Unit test to test ACI behavior and Named Subordinate References (RFC 3296).
 * This test needs a jeb backend, the memory backend cannot be used.
 */

public class ReferencesTestCase extends AciTestCase{
  private static String suffix="dc=example,dc=com";
  private static final String level5User="uid=user.5,ou=People," + suffix;
  private static final String adminBase="ou=Admins," + suffix;
  private static final String peopleBase="ou=people," + suffix;
  private static final String smartReferralAdmin=
          "uid=smart referral admin,uid=proxyuser,ou=admins," + suffix;
  private static final String ctrlString = OID_MANAGE_DSAIT_CONTROL + ":false";

  //Allow based on plus operator.
  private static final
  String ALLOW_OC_PLUS = "(targetattr=\"objectclass || +\")" +
          "(version 3.0;acl \"plus\";" +
          "allow (search, read) " +
          "userdn=\"ldap:///" + level5User + "\";)";

  //Allow based on ref name.
  private static final
  String ALLOW_OC = "(targetattr=\"objectclass || ref\")" +
          "(version 3.0;acl \"ref name\";" +
          "allow (search, read) " +
          "userdn=\"ldap:///" + level5User + "\";)";

  //Allow based on target keyword.
  private static final
  String ALLOW_PEOPLE =
          "(target=\"ldap:///" + peopleBase + "\")" +
                  "(targetattr=\"objectclass || ref\")" +
                  "(version 3.0;acl \"target\";" +
                  "allow (search, read) " +
                  "userdn=\"ldap:///" + level5User + "\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries(suffix);
  }


  /**
   * Test using ACI added to admin base containing "ref" attribute type name
   * specified in targetattr keword.
   *
   * @throws Exception If results are unexpected.
   */
  @Test()
  public void testRef() throws Exception {
    try {
    String pwdLdifs =
            makeAddLDIF("aci", adminBase, ALLOW_OC);

    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(level5User, PWD, null,null, null,
                    adminBase, filter, null);
    Assert.assertTrue(isRefMap(userResults));
    } finally {
      deleteAttrFromEntry(adminBase, "aci");
    }
  }


  /**
   * Test using ACI added to actual referral entry (added using ldifmodify
   * passing manageDsaIT control).
   *
   * @throws Exception  If results are unexpected.
   */
  @Test()
  public void testRefAci() throws Exception {
    try {
    String pwdLdifs =
            makeAddLDIF("aci", smartReferralAdmin, ALLOW_OC);
    //Add the ACI passing the manageDsaIT control.
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD, ctrlString);
    String userResults =
            LDAPSearchParams(level5User, PWD, null,null, null,
                    adminBase, filter, null);
    Assert.assertTrue(isRefMap(userResults));
    } finally {
      String delPwdLdifs =
        makeDelLDIF("aci", smartReferralAdmin, ALLOW_OC);
      //Delete the ACI passing the manageDsaIT control.
      LDIFModify(delPwdLdifs, DIR_MGR_DN, PWD, ctrlString);
    }
  }


  /**
   * Test global ACI allowing the "ref" attribute type to be returned only if
   * if the search is under the people base. A search under the admin base
   * should not return a reference.
   *
   * @throws Exception If an unexpected result is returned.
   */
  @Test()
  public void testGlobalTargetAci() throws Exception {
    try {
    String pwdLdifs =
            makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN, ALLOW_PEOPLE);
    LDIFAdminModify(pwdLdifs, DIR_MGR_DN, PWD);
    //Fail, ACI only allows people references
    String userResults =
            LDAPSearchParams(level5User, PWD, null,null, null,
                    adminBase, filter, null);
    Assert.assertFalse(isRefMap(userResults));
    //Pass, ACI allows people references
    String userResults1 =
            LDAPSearchParams(level5User, PWD, null,null, null,
                    peopleBase, filter, null);
    Assert.assertTrue(isRefMap(userResults1));
    } finally {
      deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    }
  }


  /**
   * Test global ACI allowing the "ref" attribute type specifed by the
   * plus operator.
   *
   * @throws Exception If an unexpected result us returned.
   */
  @Test()
  public void testGlobalAci() throws Exception {
    try {
    String pwdLdifs =
           makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN, ALLOW_OC_PLUS);
    LDIFAdminModify(pwdLdifs, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(level5User, PWD, null,null, null,
                    adminBase, filter, null);
    Assert.assertTrue(isRefMap(userResults));
    } finally {
      deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    }
  }


  /**
   * Simple function that searches for the "SearchReference" string and returns
   * true if it is seen.
   *
   * @param resultString The string containing the results from the search.
   * @return True if the "SearchReference" string is seen in the results.
   */
  protected boolean
  isRefMap(String resultString) {
    boolean ret=false;
    StringReader r=new StringReader(resultString);
    BufferedReader br=new BufferedReader(r);
    try {
      while(true) {
        String s = br.readLine();
        if(s == null)
          break;
        if(s.startsWith("SearchReference")) {
          ret=true;
          break;
        }
      }
    } catch (IOException e) {
      Assert.assertEquals(0, 1,  e.getMessage());
    }
    return ret;
  }
}

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
package org.opends.server.types;



import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.messages.Message;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the directory exception class.
 */
public class DirectoryExceptionTestCase
       extends TypesTestCase
{
  /**
   * Retrieves the set of result codes that may be used by the server.
   *
   * @return  The set of result codes that may be used by the server.
   */
  @DataProvider(name = "resultCodes")
  public Object[][] getResultCodes()
  {
    ResultCode[] resultCodes = ResultCode.values();
    Object[][]   array       = new Object[resultCodes.length][1];

    for (int i=0; i < resultCodes.length; i++)
    {
      array[i][0] = resultCodes[i];
    }

    return array;
  }



  /**
   * Tests the first constructor, which takes ResultCode, String, and int
   * arguments.
   *
   * @param  resultCode  The result code to use for the test.
   */
  @Test(dataProvider = "resultCodes")
  public void testConstructor1(ResultCode resultCode)
  {
    Message msg = Message.raw("Test Constructor 1");

    validateException(new DirectoryException(resultCode, msg));
    validateException(new DirectoryException(resultCode, (Message)null));
  }



  /**
   * Tests the second constructor, which takes ResultCode, String, int, and
   * Throwable arguments.
   *
   * @param  resultCode  The result code to use for the test.
   */
  @Test(dataProvider = "resultCodes")
  public void testConstructor2(ResultCode resultCode)
  {
    Message   msg = Message.raw("Test Constructor 2");
    Exception e   = new Exception("Test Constructor 2 Exception");

    validateException(new DirectoryException(resultCode, msg, e));
    validateException(new DirectoryException(resultCode, null, e));
  }



  /**
   * Tests the third constructor, which takes ResultCode, String, int, DN, and
   * Throwable arguments.
   *
   * @param  resultCode  The result code to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "resultCodes")
  public void testConstructor3(ResultCode resultCode)
         throws Exception
  {
    Message   msg = Message.raw("Test Constructor 3");
    DN        dn  = DN.decode("cn=Test Constructor 3,dc=example,dc=com");
    Exception e   = new Exception("Test Constructor 3 Exception");

    validateException(new DirectoryException(resultCode, msg, dn, e));
    validateException(new DirectoryException(resultCode, null, dn, e));

    validateException(new DirectoryException(resultCode, msg, dn, null));
    validateException(new DirectoryException(resultCode, null, dn, null));

    validateException(new DirectoryException(resultCode, msg, null, e));
    validateException(new DirectoryException(resultCode, null, null, e));

    validateException(new DirectoryException(resultCode, msg, null, null));
    validateException(new DirectoryException(resultCode, null, null, null));
  }



  /**
   * Tests the fourth constructor, which takes ResultCode, String, int, DN,
   * List<String>, and Throwable arguments.
   *
   * @param  resultCode  The result code to use for the test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "resultCodes")
  public void testConstructor4(ResultCode resultCode)
         throws Exception
  {
    Message   msg     = Message.raw("Test Constructor 4");
    DN        dn      = DN.decode("cn=Test Constructor 4,dc=example,dc=com");
    Exception e       = new Exception("Test Constructor 4 Exception");
    List<String> refs = new ArrayList<String>();
    refs.add("ldap://ldap.example.com/cn=Test Constructor 4,dc=example,dc=com");

    validateException(new DirectoryException(resultCode, msg, dn, refs, e));
    validateException(new DirectoryException(resultCode, null, dn, refs, e));

    validateException(new DirectoryException(resultCode, msg, dn, refs,
                                             null));
    validateException(new DirectoryException(resultCode, null, dn, refs,
                                             null));

    validateException(new DirectoryException(resultCode, msg, null, refs,
                                             e));
    validateException(new DirectoryException(resultCode, null, null, refs,
                                             e));

    validateException(new DirectoryException(resultCode, msg, null, refs,
                                             null));
    validateException(new DirectoryException(resultCode, null, null, refs,
                                             null));

    refs.clear();
    validateException(new DirectoryException(resultCode, msg, dn, refs, e));
    validateException(new DirectoryException(resultCode, null, dn, refs, e));

    validateException(new DirectoryException(resultCode, msg, dn, refs,
                                             null));
    validateException(new DirectoryException(resultCode, null, dn, refs,
                                             null));

    validateException(new DirectoryException(resultCode, msg, null, refs,
                                             e));
    validateException(new DirectoryException(resultCode, null, null, refs,
                                             e));

    validateException(new DirectoryException(resultCode, msg, null, refs,
                                             null));
    validateException(new DirectoryException(resultCode, null, null, refs,
                                             null));

    validateException(new DirectoryException(resultCode, msg, dn, null, e));
    validateException(new DirectoryException(resultCode, null, dn, null, e));

    validateException(new DirectoryException(resultCode, msg, dn, null,
                                             null));
    validateException(new DirectoryException(resultCode, null, dn, null,
                                             null));

    validateException(new DirectoryException(resultCode, msg, null, null,
                                             e));
    validateException(new DirectoryException(resultCode, null, null, null,
                                             e));

    validateException(new DirectoryException(resultCode, msg, null, null,
                                             null));
    validateException(new DirectoryException(resultCode, null, null, null,
                                             null));
  }



  /**
   * Verifies that everything is OK with the provided exception (i.e., that it
   * is possible to get the result code, error message, message ID, matched DN,
   * and referrals.
   *
   * @param  de  The directory exception to be validated.
   */
  private void validateException(DirectoryException de)
  {
    assertNotNull(de.getResultCode());

    de.getMessageObject();
    de.getMatchedDN();
    de.getReferralURLs();
  }
}


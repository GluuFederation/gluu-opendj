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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import java.util.Arrays;
import java.util.Collection;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Unit tests for ANDConnectionCriteria.
 */
public class ANDConnectionCriteriaTest extends DirectoryServerTestCase
{
  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           if the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Returns test data for the following test cases.
   *
   * @return The test data for the following test cases.
   */
  @DataProvider(name = "testData")
  public Object[][] createTestData()
  {
    return new Object[][] {
        { Arrays.<ConnectionCriteria> asList(), true },
        { Arrays.asList(ConnectionCriteria.TRUE), true },
        {
            Arrays.asList(ConnectionCriteria.TRUE,
                ConnectionCriteria.TRUE), true },
        { Arrays.asList(ConnectionCriteria.FALSE), false },
        {
            Arrays.asList(ConnectionCriteria.TRUE,
                ConnectionCriteria.FALSE), false },
        {
            Arrays.asList(ConnectionCriteria.FALSE,
                ConnectionCriteria.TRUE), false },
        {
            Arrays.asList(ConnectionCriteria.FALSE,
                ConnectionCriteria.FALSE), false }, };
  }



  /**
   * Tests the matches method.
   *
   * @param subCriteria
   *          The sub criteria.
   * @param expectedResult
   *          The expected result.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testData")
  public void testMatches(Collection<ConnectionCriteria> subCriteria,
      boolean expectedResult) throws Exception
  {
    ANDConnectionCriteria criteria =
        new ANDConnectionCriteria(subCriteria);
    ClientConnection connection =
        new InternalClientConnection(DN.NULL_DN);
    Assert.assertEquals(criteria.matches(connection), expectedResult);
  }



  /**
   * Tests the willMatchAfterBind method.
   *
   * @param subCriteria
   *          The sub criteria.
   * @param expectedResult
   *          The expected result.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testData")
  public void testWillMatchAfterBind(
      Collection<ConnectionCriteria> subCriteria, boolean expectedResult)
      throws Exception
  {
    ANDConnectionCriteria criteria =
        new ANDConnectionCriteria(subCriteria);
    ClientConnection connection =
        new InternalClientConnection(DN.NULL_DN);
    Assert.assertEquals(criteria.willMatchAfterBind(connection,
        DN.NULL_DN, AuthenticationType.SIMPLE, false), expectedResult);
  }

}

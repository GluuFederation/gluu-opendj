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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * AttributeTypePropertyDefinition Tester.
 */
public class AttributeTypePropertyDefinitionTest extends DirectoryServerTestCase {

  /**
   * Sets up tests.
   *
   * @throws Exception
   *           If the server could not be started.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
  }



  /**
   * Tests validateValue() with valid data
   */
  @Test
  public void testValidateValue() {
    AttributeTypePropertyDefinition.setCheckSchema(true);
    AttributeTypePropertyDefinition d = createPropertyDefinition();
    d.validateValue(DirectoryServer.getAttributeType("cn"));
  }



  /**
   * @return data for testing
   */
  @DataProvider(name = "testDecodeValueLegalData")
  public Object[][] createValidateValueLegalData() {
    return new Object[][] { { "cn" }, { "o" }, { "ou" } };
  }



  /**
   * Tests decodeValue()
   *
   * @param value
   *          to decode
   */
  @Test(dataProvider = "testDecodeValueLegalData")
  public void testDecodeValue(String value) {
    AttributeTypePropertyDefinition.setCheckSchema(true);
    AttributeTypePropertyDefinition d = createPropertyDefinition();
    AttributeType expected = DirectoryServer.getAttributeType(value);
    assertEquals(d.decodeValue(value), expected);
  }



  /**
   * Tests encodeValue()
   *
   * @param value
   *          to decode/encode
   */
  @Test(dataProvider = "testDecodeValueLegalData")
  public void testEncodeValue(String value) {
    AttributeTypePropertyDefinition.setCheckSchema(true);
    AttributeTypePropertyDefinition d = createPropertyDefinition();
    assertEquals(d.encodeValue(d.decodeValue(value)), value);
  }



  /**
   * @return data for testing illegal values
   */
  @DataProvider(name = "testDecodeValueIllegalData")
  public Object[][] createValidateValueIllegalData() {
    return new Object[][] { { "dummy-type-xxx" } };
  }



  /**
   * Tests decodeValue() with illegal data
   *
   * @param value
   *          to decode
   */
  @Test(dataProvider = "testDecodeValueIllegalData", expectedExceptions = { IllegalPropertyValueStringException.class })
  public void testDecodeValue2(String value) {
    AttributeTypePropertyDefinition.setCheckSchema(true);
    AttributeTypePropertyDefinition d = createPropertyDefinition();
    d.decodeValue(value);
  }



  /**
   * Tests decodeValue() with illegal data with schema checking off.
   *
   * @param value
   *          to decode
   */
  @Test(dataProvider = "testDecodeValueIllegalData")
  public void testDecodeValue3(String value) {
    AttributeTypePropertyDefinition.setCheckSchema(false);
    AttributeTypePropertyDefinition d = createPropertyDefinition();
    AttributeType type = d.decodeValue(value);
    assertEquals(type.getNameOrOID(), value);

    // Make sure to turn schema checking back on so that other tests which
    // depend on it don't fail.
    AttributeTypePropertyDefinition.setCheckSchema(true);
  }



  // Create a new definition.
  private AttributeTypePropertyDefinition createPropertyDefinition() {
    AttributeTypePropertyDefinition.Builder builder = AttributeTypePropertyDefinition
        .createBuilder(RootCfgDefn.getInstance(), "test-property");
    return builder.getInstance();
  }

}

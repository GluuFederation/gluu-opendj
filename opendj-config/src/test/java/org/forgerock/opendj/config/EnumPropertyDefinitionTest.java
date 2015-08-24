/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EnumPropertyDefinitionTest extends ConfigTestCase {

    private enum TestEnum {
        ONE, TWO, THREE
    }

    private EnumPropertyDefinition.Builder<TestEnum> builder;

    @BeforeClass
    public void setUp() {
        builder = EnumPropertyDefinition.createBuilder(RootCfgDefn.getInstance(), "test-property");
        builder.setEnumClass(TestEnum.class);
    }

    @Test
    public void testCreateBuilder() {
        assertNotNull(builder);
    }

    /**
     * Tests that exception thrown when no enum class specified by builder.
     */
    @Test
    public void testBuildInstance() {
        EnumPropertyDefinition<?> def = builder.getInstance();
        assertEquals(def.getEnumClass(), TestEnum.class);
    }

    /**
     * Tests that exception thrown when no enum class specified by builder.
     */
    @Test(expectedExceptions = { IllegalStateException.class })
    public void testBuildInstanceWithoutEnumClassSpecified() {
        EnumPropertyDefinition.Builder<TestEnum> localBuilder = EnumPropertyDefinition.createBuilder(
                RootCfgDefn.getInstance(), "test-property");
        localBuilder.getInstance();
    }

    /**
     * Creates data decodeValue test.
     *
     * @return data
     */
    @DataProvider(name = "decodeValueData")
    Object[][] createDecodeValueData() {
        return new Object[][] { { "ONE", TestEnum.ONE } };
    }

    /**
     * Tests decodeValue().
     *
     * @param value
     *            to decode
     * @param expectedValue
     *            enum expected
     */
    @Test(dataProvider = "decodeValueData")
    public void testDecodeValue(String value, TestEnum expectedValue) {
        EnumPropertyDefinition<?> def = builder.getInstance();
        assertEquals(def.decodeValue(value), expectedValue);
    }

    /**
     * Creates illegal data for decode value test.
     *
     * @return data
     */
    @DataProvider(name = "decodeValueIllegalData")
    Object[][] createDecodeValueIllegalData() {
        return new Object[][] { { "xxx" }, { null } };
    }

    /**
     * Tests decodeValue().
     *
     * @param value
     *            to decode
     */
    @Test(dataProvider = "decodeValueIllegalData", expectedExceptions = { NullPointerException.class,
            PropertyException.class })
    public void testDecodeValueIllegalData(String value) {
        EnumPropertyDefinition<?> def = builder.getInstance();
        def.decodeValue(value);
    }

    /** Tests normalization. */
    @Test
    public void testNormalizeValue() {
        EnumPropertyDefinition<TestEnum> def = builder.getInstance();
        assertEquals(def.normalizeValue(TestEnum.ONE), "one");
    }

    /** Tests validation. */
    @Test
    public void testValidateValue() {
        EnumPropertyDefinition<TestEnum> def = builder.getInstance();
        def.validateValue(TestEnum.ONE);
    }

}

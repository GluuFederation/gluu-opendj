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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import static org.testng.Assert.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ConnectionHandlerCfgDefn;
import org.opends.server.admin.std.meta.JMXConnectionHandlerCfgDefn;
import org.opends.server.admin.std.meta.LDAPConnectionHandlerCfgDefn;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * AbstractManagedObjectDefinition test cases.
 */
@Test(sequential=true)
public class AbstractManagedObjectDefinitionTest extends DirectoryServerTestCase {

  /**
   * A test managed object definition.
   */
  private static class TestDefinition extends AbstractManagedObjectDefinition {

    /**
     * Creates a new test definition.
     *
     * @param name
     *          The name of the test definition.
     * @param parent
     *          The parent definition (can be null).
     */
    @SuppressWarnings("unchecked")
    protected TestDefinition(String name, AbstractManagedObjectDefinition parent) {
      super(name, parent);
    }
  }

  // Test definitions.
  private TestDefinition top = new TestDefinition("topmost", null);

  private TestDefinition middle1 = new TestDefinition("middle1", top);

  private TestDefinition middle2 = new TestDefinition("middle2", top);

  private TestDefinition bottom1 = new TestDefinition("bottom1", middle1);

  private TestDefinition bottom2 = new TestDefinition("bottom2", middle1);

  private TestDefinition bottom3 = new TestDefinition("bottom3", middle1);



  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
    TestCfg.setUp();
  }



  /**
   * Tears down test environment.
   */
  @AfterClass
  public void tearDown() {
    TestCfg.cleanup();
  }



  /**
   * @return data for testIsChildOf.
   */
  @DataProvider(name = "testIsChildOf")
  public Object[][] createTestIsChildOf() {
    return new Object[][] { { top, top, true }, { middle1, middle1, true },
        { bottom1, bottom1, true }, { top, middle1, false },
        { top, bottom1, false }, { middle1, top, true },
        { bottom1, top, true }, { bottom1, middle1, true }, };
  }



  /**
   * Tests isChildOf method.
   *
   * @param d1
   *          The child definition.
   * @param d2
   *          The parent definition.
   * @param expected
   *          The expected result.
   */
  @SuppressWarnings("unchecked")
  @Test(dataProvider = "testIsChildOf")
  public void testIsChildOf(TestDefinition d1, TestDefinition d2,
      boolean expected) {
    assertEquals(d1.isChildOf(d2), expected);
  }



  /**
   * @return data for testIsParentOf.
   */
  @DataProvider(name = "testIsParentOf")
  public Object[][] createTestIsParentOf() {
    return new Object[][] { { top, top, true }, { middle1, middle1, true },
        { bottom1, bottom1, true }, { top, middle1, true },
        { top, bottom1, true }, { middle1, top, false },
        { bottom1, top, false }, { bottom1, middle1, false }, };
  }



  /**
   * Tests isParentOf method.
   *
   * @param d1
   *          The parent definition.
   * @param d2
   *          The child definition.
   * @param expected
   *          The expected result.
   */
  @SuppressWarnings("unchecked")
  @Test(dataProvider = "testIsParentOf")
  public void testIsParentOf(TestDefinition d1, TestDefinition d2,
      boolean expected) {
    assertEquals(d1.isParentOf(d2), expected);
  }



  /**
   * Tests getAllChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetAllChildren1() {
    Collection<AbstractManagedObjectDefinition> children = top.getAllChildren();
    assertEquals(children.size(), 5);
    assertTrue(children.contains(middle1));
    assertTrue(children.contains(middle2));
    assertTrue(children.contains(bottom1));
    assertTrue(children.contains(bottom2));
    assertTrue(children.contains(bottom3));
  }



  /**
   * Tests getAllChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetAllChildren2() {
    Collection<AbstractManagedObjectDefinition> children = middle1
        .getAllChildren();
    assertEquals(children.size(), 3);
    assertTrue(children.contains(bottom1));
    assertTrue(children.contains(bottom2));
    assertTrue(children.contains(bottom3));
  }



  /**
   * Tests getAllChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetAllChildren3() {
    Collection<AbstractManagedObjectDefinition> children = middle2
        .getAllChildren();
    assertEquals(children.size(), 0);
  }



  /**
   * Tests getChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetChildren1() {
    Collection<AbstractManagedObjectDefinition> children = top.getChildren();
    assertEquals(children.size(), 2);
    assertTrue(children.contains(middle1));
    assertTrue(children.contains(middle2));
  }



  /**
   * Tests getChildren method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testGetChildren2() {
    Collection<AbstractManagedObjectDefinition> children = middle2
        .getChildren();
    assertEquals(children.size(), 0);
  }



  /**
   * Tests that overridden properties work properly. FIXME: should not
   * use Connection Handlers - should define our own definitions.
   * <p>
   * Check that the generic connection handler definition does not
   * have a default behavior defined for the
   * java-class.
   */
  @Test
  public void testPropertyOverride1() {
    AbstractManagedObjectDefinition<?, ?> d = ConnectionHandlerCfgDefn
        .getInstance();
    PropertyDefinition<?> pd = d
        .getPropertyDefinition("java-class");
    DefaultBehaviorProvider<?> dbp = pd.getDefaultBehaviorProvider();
    assertEquals(dbp.getClass(), UndefinedDefaultBehaviorProvider.class);
  }



  /**
   * Tests that overridden properties work properly. FIXME: should not
   * use Connection Handlers - should define our own definitions.
   * <p>
   * Check that the LDAP connection handler definition does have a
   * default behavior defined for the java-class.
   */
  @Test
  public void testPropertyOverride2() {
    AbstractManagedObjectDefinition<?, ?> d = LDAPConnectionHandlerCfgDefn
        .getInstance();
    PropertyDefinition<?> pd = d
        .getPropertyDefinition("java-class");
    DefaultBehaviorProvider<?> dbp = pd.getDefaultBehaviorProvider();
    assertEquals(dbp.getClass(), DefinedDefaultBehaviorProvider.class);

    DefinedDefaultBehaviorProvider<?> ddbp = (DefinedDefaultBehaviorProvider<?>) dbp;
    assertEquals(ddbp.getDefaultValues(), Collections
        .singleton("org.opends.server.protocols.ldap.LDAPConnectionHandler"));
  }



  /**
   * Tests that overridden properties work properly. FIXME: should not
   * use Connection Handlers - should define our own definitions.
   * <p>
   * Check that the JMX connection handler definition does have a
   * default behavior defined for the java-class.
   */
  @Test
  public void testPropertyOverride3() {
    AbstractManagedObjectDefinition<?, ?> d = JMXConnectionHandlerCfgDefn
        .getInstance();
    PropertyDefinition<?> pd = d
        .getPropertyDefinition("java-class");
    DefaultBehaviorProvider<?> dbp = pd.getDefaultBehaviorProvider();
    assertEquals(dbp.getClass(), DefinedDefaultBehaviorProvider.class);

    DefinedDefaultBehaviorProvider<?> ddbp = (DefinedDefaultBehaviorProvider<?>) dbp;
    assertEquals(ddbp.getDefaultValues(), Collections
        .singleton("org.opends.server.protocols.jmx.JmxConnectionHandler"));
  }



  /**
   * Tests that the getReverseRelationDefinitions() method returns
   * relations referring to a managed object.
   */
  @Test
  public void testGetReverseRelationDefinitions() {
    Collection<RelationDefinition<TestParentCfgClient, TestParentCfg>> rdlist1 = TestParentCfgDefn
        .getInstance().getReverseRelationDefinitions();

    assertEquals(rdlist1.size(), 2);
    assertTrue(rdlist1.contains(TestCfg
        .getTestOneToManyParentRelationDefinition()));
    assertTrue(rdlist1.contains(TestCfg
        .getTestOneToZeroOrOneParentRelationDefinition()));

    Collection<RelationDefinition<TestChildCfgClient, TestChildCfg>> rdlist2 = TestChildCfgDefn
        .getInstance().getReverseRelationDefinitions();

    assertEquals(rdlist2.size(), 2);
    assertTrue(rdlist2.contains(TestParentCfgDefn.getInstance()
        .getTestChildrenRelationDefinition()));
    assertTrue(rdlist2.contains(TestParentCfgDefn.getInstance()
        .getOptionalTestChildRelationDefinition()));
  }



  /**
   * Tests that the getAllReverseRelationDefinitions() method returns
   * all relations referring to a managed object.
   */
  @Test
  public void testGetAllReverseRelationDefinitions() {
    Collection<RelationDefinition<? super TestParentCfgClient, ? super TestParentCfg>> rdlist1 = TestParentCfgDefn
        .getInstance().getAllReverseRelationDefinitions();

    assertEquals(rdlist1.size(), 2);
    assertTrue(rdlist1.contains(TestCfg
        .getTestOneToManyParentRelationDefinition()));
    assertTrue(rdlist1.contains(TestCfg
        .getTestOneToZeroOrOneParentRelationDefinition()));

    Collection<RelationDefinition<? super TestChildCfgClient, ? super TestChildCfg>> rdlist2 = TestChildCfgDefn
        .getInstance().getAllReverseRelationDefinitions();

    assertEquals(rdlist2.size(), 2);
    assertTrue(rdlist2.contains(TestParentCfgDefn.getInstance()
        .getTestChildrenRelationDefinition()));
    assertTrue(rdlist2.contains(TestParentCfgDefn.getInstance()
        .getOptionalTestChildRelationDefinition()));
  }
}

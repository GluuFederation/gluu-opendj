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
package org.opends.server.admin.client.ldap;



import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;

import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfgClient;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfgClient;
import org.opends.server.admin.TestParentCfgDefn;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Administration framework LDAP client unit tests.
 */
@Test(sequential=true)
public final class LDAPClientTest extends AdminTestCase {

  // Test LDIF.
  private static final String[] TEST_LDIF = new String[] {
      // Base entries.
      "dn: cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: config",
      "",
      "dn: cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test-parents",
      "",
      // Parent 1 - uses default values for
      // optional-multi-valued-dn-property.
      "dn: cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-parent-dummy",
      "cn: test parent 1",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "",
      // Parent 2 - overrides default values for
      // optional-multi-valued-dn-property.
      "dn: cn=test parent 2,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-parent-dummy",
      "cn: test parent 2",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-base-dn: dc=default value p2v1,dc=com",
      "ds-cfg-base-dn: dc=default value p2v2,dc=com",
      "",
      // Parent 3 - overrides default values for
      // optional-multi-valued-dn-property.
      "dn: cn=test parent 3,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-parent-dummy",
      "cn: test parent 3",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-base-dn: dc=default value p3v1,dc=com",
      "ds-cfg-base-dn: dc=default value p3v2,dc=com",
      "",
      // Child base entries.
      "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: multiple children",
      "",
      "dn:cn=test children,cn=test parent 2,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: multiple children",
      "",
      // Child 1 inherits defaults for both
      // optional-multi-valued-dn-property1 and
      // optional-multi-valued-dn-property2.
      "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 1",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "",
      // Child 2 inherits defaults for
      // optional-multi-valued-dn-property2.
      "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 2",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-base-dn: dc=default value c2v1,dc=com",
      "ds-cfg-base-dn: dc=default value c2v2,dc=com",
      "",
      // Child 3 overrides defaults for
      // optional-multi-valued-dn-property1 and
      // optional-multi-valued-dn-property2.
      "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 3",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-base-dn: dc=default value c3v1,dc=com",
      "ds-cfg-base-dn: dc=default value c3v2,dc=com",
      "ds-cfg-group-dn: dc=default value c3v3,dc=com",
      "ds-cfg-group-dn: dc=default value c3v4,dc=com",
      "",
      // Child 4 inherits overridden defaults for both
      // optional-multi-valued-dn-property1 and
      // optional-multi-valued-dn-property2.
      "dn: cn=test child 1,cn=test children,cn=test parent 2,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 1",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "",
  };



  /**
   * Provide valid naming exception to client API exception mappings.
   *
   * @return Returns valid naming exception to client API exception
   *         mappings.
   */
  @DataProvider(name = "createManagedObjectExceptions")
  public Object[][] createManagedObjectExceptions() {
    return new Object[][] {
        {
            new javax.naming.CommunicationException(),
            CommunicationException.class
        },
        {
            new javax.naming.ServiceUnavailableException(),
            CommunicationException.class
        },
        {
            new javax.naming.CannotProceedException(),
            CommunicationException.class
        },
        {
            new javax.naming.NameAlreadyBoundException(),
            ManagedObjectAlreadyExistsException.class
        },
        {
            new javax.naming.NoPermissionException(),
            AuthorizationException.class
        },
        {
            new OperationNotSupportedException(),
            OperationRejectedException.class
        }
    };
  }



  /**
   * Provide valid naming exception to client API exception mappings.
   *
   * @return Returns valid naming exception to client API exception
   *         mappings.
   */
  @DataProvider(name = "getManagedObjectExceptions")
  public Object[][] getManagedObjectExceptions() {
    return new Object[][] {
        {
            new javax.naming.CommunicationException(),
            CommunicationException.class
        },
        {
            new javax.naming.ServiceUnavailableException(),
            CommunicationException.class
        },
        {
            new javax.naming.CannotProceedException(),
            CommunicationException.class
        },
        {
            new javax.naming.NameNotFoundException(),
            ManagedObjectNotFoundException.class
        },
        {
            new javax.naming.NoPermissionException(),
            AuthorizationException.class
        },
        {
            new OperationNotSupportedException(), CommunicationException.class
        }
    };
  }



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
   * Tests creation of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateChildManagedObject() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child new");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child-dummy");
    c.addExpectedAttribute("ds-cfg-enabled", "true");
    c.addExpectedAttribute("ds-cfg-java-class",
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    c.addExpectedAttribute("ds-cfg-attribute-type", "description");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child new", null);
    child.setMandatoryBooleanProperty(true);
    child.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
        .getAttributeType("description"));
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests creation of a top-level managed object using fails when an
   * underlying NamingException occurs.
   *
   * @param cause
   *          The NamingException cause of the failure.
   * @param expected
   *          The expected client API exception class.
   */
  @Test(dataProvider = "createManagedObjectExceptions")
  public void testCreateManagedObjectException(final NamingException cause,
      Class<? extends Exception> expected) {
    MockLDAPConnection c = new MockLDAPConnection() {

      /**
       * {@inheritDoc}
       */
      @Override
      public void createEntry(LdapName dn, Attributes attributes)
          throws NamingException {
        throw cause;
      }

    };
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    try {
      TestParentCfgClient parent = createTestParent(ctx, "test parent new");
      parent.setMandatoryBooleanProperty(true);
      parent.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
          .getAttributeType("description"));
      parent.commit();
    } catch (Exception e) {
      Assert.assertEquals(e.getClass(), expected);
    }
  }



  /**
   * Tests creation of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateTopLevelManagedObject() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test parent new,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test parent new");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-parent-dummy");
    c.addExpectedAttribute("ds-cfg-enabled", "true");
    c.addExpectedAttribute("ds-cfg-java-class",
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    c.addExpectedAttribute("ds-cfg-attribute-type", "description");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = createTestParent(ctx, "test parent new");
    parent.setMandatoryBooleanProperty(true);
    parent.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
        .getAttributeType("description"));
    parent.commit();
    c.assertEntryIsCreated();
  }



  /**
   * Tests retrieval of a child managed object with non-default
   * values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetChildManagedObject() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 3");
    Assert.assertEquals(child.isMandatoryBooleanProperty(), Boolean.TRUE);
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=default value c3v1,dc=com", "dc=default value c3v2,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=default value c3v3,dc=com", "dc=default value c3v4,dc=com");
  }



  /**
   * Tests retrieval of a child managed object with default values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetChildManagedObjectDefault() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 1");
    Assert.assertEquals(child.isMandatoryBooleanProperty(), Boolean.TRUE);
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
    Assert.assertEquals(child.isMandatoryBooleanProperty(), Boolean.TRUE);
  }



  /**
   * Tests retrieval of a top-level managed object fails when an
   * underlying NamingException occurs.
   *
   * @param cause
   *          The NamingException cause of the failure.
   * @param expected
   *          The expected client API exception class.
   */
  @Test(dataProvider = "getManagedObjectExceptions")
  public void testGetManagedObjectException(final NamingException cause,
      Class<? extends Exception> expected) {
    MockLDAPConnection c = new MockLDAPConnection() {

      /**
       * {@inheritDoc}
       */
      @Override
      public Attributes readEntry(LdapName dn, Collection<String> attrIds)
          throws NamingException {
        throw cause;
      }

    };
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    try {
      getTestParent(ctx, "test parent 2");
    } catch (Exception e) {
      Assert.assertEquals(e.getClass(), expected);
    }
  }



  /**
   * Tests retrieval of a top-level managed object with non-default
   * values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetTopLevelManagedObject() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
    Assert.assertEquals(parent.isMandatoryBooleanProperty(), Boolean.TRUE);
    Assert.assertEquals(parent.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(parent.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(),
        "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
  }



  /**
   * Tests retrieval of a top-level managed object with default
   * values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetTopLevelManagedObjectDefault() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    Assert.assertEquals(parent.isMandatoryBooleanProperty(), Boolean.TRUE);
    Assert.assertEquals(parent.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(parent.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
  }



  /**
   * Tests retrieval of relative inherited default values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testInheritedDefaultValues1() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child new");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child-dummy");
    c.addExpectedAttribute("ds-cfg-enabled", "true");
    c.addExpectedAttribute("ds-cfg-java-class",
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    c.addExpectedAttribute("ds-cfg-attribute-type", "description");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child new", null);

    // Check pre-commit values.
    Assert.assertEquals(child.isMandatoryBooleanProperty(), null);
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert
        .assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(), null);
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");

    // Check that the default values are not committed.
    child.setMandatoryBooleanProperty(true);
    child.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
        .getAttributeType("description"));
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests retrieval of relative inherited default values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testInheritedDefaultValues2() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child new,cn=test children,cn=test parent 2,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child new");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child-dummy");
    c.addExpectedAttribute("ds-cfg-enabled", "true");
    c.addExpectedAttribute("ds-cfg-java-class",
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    c.addExpectedAttribute("ds-cfg-attribute-type", "description");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child new", null);

    // Check pre-commit values.
    Assert.assertEquals(child.isMandatoryBooleanProperty(), null);
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert
        .assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(), null);
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");

    // Check that the default values are not committed.
    child.setMandatoryBooleanProperty(true);
    child.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
        .getAttributeType("description"));
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests listing of child managed objects.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListChildManagedObjects() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    String[] actual = parent.listTestChildren();
    String[] expected = new String[] {
        "test child 1", "test child 2", "test child 3"
    };
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests listing of child managed objects when their are not any.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListChildManagedObjectsEmpty() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 3");
    String[] actual = parent.listTestChildren();
    String[] expected = new String[] {};
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests listing of top level managed objects.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListTopLevelManagedObjects() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    String[] actual = listTestParents(ctx);
    String[] expected = new String[] {
        "test parent 1", "test parent 2", "test parent 3"
    };
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests listing of top level managed objects when their are not
   * any.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListTopLevelManagedObjectsEmpty() throws Exception {
    String[] ldif = {};
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(ldif);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    String[] actual = listTestParents(ctx);
    String[] expected = new String[] {};
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests modification of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyChildManagedObjectResetToDefault() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedModification("ds-cfg-base-dn");
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 2");
    child.setOptionalMultiValuedDNProperty1(Collections.<DN> emptySet());
    child.commit();
    Assert.assertTrue(c.isEntryModified());
  }



  /**
   * Tests modification of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyTopLevelManagedObjectNoChanges() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    parent.commit();
    Assert.assertFalse(c.isEntryModified());
  }



  /**
   * Tests modification of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyTopLevelManagedObjectWithChanges() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedModification("ds-cfg-enabled", "false");
    c.addExpectedModification("ds-cfg-base-dn",
        "dc=mod1,dc=com", "dc=mod2,dc=com");
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    parent.setMandatoryBooleanProperty(false);
    parent.setOptionalMultiValuedDNProperty(Arrays.asList(DN
        .decode("dc=mod1,dc=com"), DN.decode("dc=mod2,dc=com")));
    parent.commit();
    Assert.assertTrue(c.isEntryModified());
  }



  /**
   * Tests removal of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testRemoveChildManagedObject() throws Exception {
    DeleteSubtreeMockLDAPConnection c = new DeleteSubtreeMockLDAPConnection(
        "cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    parent.removeTestChild("test child 1");
    c.assertSubtreeIsDeleted();
  }



  /**
   * Tests removal of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testRemoveTopLevelManagedObject() throws Exception {
    DeleteSubtreeMockLDAPConnection c = new DeleteSubtreeMockLDAPConnection(
        "cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    removeTestParent(ctx, "test parent 1");
    c.assertSubtreeIsDeleted();
  }



  /**
   * Tests creation of a child managed object succeeds when registered
   * add constraints succeed.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testAddConstraintSuccess() throws Exception {
    Constraint constraint = new MockConstraint(true, false, false);
    TestCfg.addConstraint(constraint);

    try {
      CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
          "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      c.importLDIF(TEST_LDIF);
      c.addExpectedAttribute("cn", "test child new");
      c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child-dummy");
      c.addExpectedAttribute("ds-cfg-enabled", "true");
      c.addExpectedAttribute("ds-cfg-java-class",
          "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      c.addExpectedAttribute("ds-cfg-attribute-type", "description");

      ManagementContext ctx = LDAPManagementContext.createFromContext(c);
      TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
      TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
          .getInstance(), "test child new", null);
      child.setMandatoryBooleanProperty(true);
      child.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
          .getAttributeType("description"));
      child.commit();

      c.assertEntryIsCreated();
    } finally {
      // Clean up.
      TestCfg.removeConstraint(constraint);
    }
  }



  /**
   * Tests creation of a child managed object fails when registered
   * add constraints fail.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test(expectedExceptions=OperationRejectedException.class)
  public void testAddConstraintFail() throws Exception {
    Constraint constraint = new MockConstraint(false, true, true);
    TestCfg.addConstraint(constraint);

    try {
      CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
          "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      c.importLDIF(TEST_LDIF);
      c.addExpectedAttribute("cn", "test child new");
      c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child-dummy");
      c.addExpectedAttribute("ds-cfg-enabled", "true");
      c.addExpectedAttribute("ds-cfg-java-class",
          "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      c.addExpectedAttribute("ds-cfg-attribute-type", "description");

      ManagementContext ctx = LDAPManagementContext.createFromContext(c);
      TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
      TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
          .getInstance(), "test child new", null);
      child.setMandatoryBooleanProperty(true);
      child.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
          .getAttributeType("description"));
      child.commit();
      Assert.fail("The add constraint failed to prevent creation of the managed object");
    } finally {
      // Clean up.
      TestCfg.removeConstraint(constraint);
    }
  }



  /**
   * Tests removal of a child managed object succeeds when registered
   * remove constraints succeed.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testRemoveConstraintSuccess() throws Exception {
    Constraint constraint = new MockConstraint(false, false, true);
    TestCfg.addConstraint(constraint);

    try {
      DeleteSubtreeMockLDAPConnection c = new DeleteSubtreeMockLDAPConnection(
          "cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      c.importLDIF(TEST_LDIF);
      ManagementContext ctx = LDAPManagementContext.createFromContext(c);
      TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
      parent.removeTestChild("test child 1");
      c.assertSubtreeIsDeleted();
    } finally {
      // Clean up.
      TestCfg.removeConstraint(constraint);
    }
  }



  /**
   * Tests removal of a child managed object fails when registered
   * remove constraints fails.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test(expectedExceptions=OperationRejectedException.class)
  public void testRemoveConstraintFail() throws Exception {
    Constraint constraint = new MockConstraint(true, true, false);
    TestCfg.addConstraint(constraint);

    try {
      DeleteSubtreeMockLDAPConnection c = new DeleteSubtreeMockLDAPConnection(
          "cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      c.importLDIF(TEST_LDIF);
      ManagementContext ctx = LDAPManagementContext.createFromContext(c);
      TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
      parent.removeTestChild("test child 1");
      Assert.fail("The remove constraint failed to prevent removal of the managed object");
    } finally {
      // Clean up.
      TestCfg.removeConstraint(constraint);
    }
  }



  /**
   * Tests modification of a child managed object succeeds when
   * registered remove constraints succeed.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyConstraintSuccess() throws Exception {
    Constraint constraint = new MockConstraint(false, true, false);
    TestCfg.addConstraint(constraint);

    try {
      ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
          "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      c.importLDIF(TEST_LDIF);
      c.addExpectedModification("ds-cfg-base-dn");
      ManagementContext ctx = LDAPManagementContext.createFromContext(c);
      TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
      TestChildCfgClient child = parent.getTestChild("test child 2");
      child.setOptionalMultiValuedDNProperty1(Collections.<DN> emptySet());
      child.commit();
      Assert.assertTrue(c.isEntryModified());
    } finally {
      // Clean up.
      TestCfg.removeConstraint(constraint);
    }
  }



  /**
   * Tests modification of a child managed object fails when
   * registered remove constraints fails.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test(expectedExceptions = OperationRejectedException.class)
  public void testModifyConstraintFail() throws Exception {
    Constraint constraint = new MockConstraint(true, false, true);
    TestCfg.addConstraint(constraint);

    try {
      ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
          "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      c.importLDIF(TEST_LDIF);
      c.addExpectedModification("ds-cfg-base-dn");
      ManagementContext ctx = LDAPManagementContext.createFromContext(c);
      TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
      TestChildCfgClient child = parent.getTestChild("test child 2");
      child.setOptionalMultiValuedDNProperty1(Collections.<DN> emptySet());
      child.commit();
      Assert
          .fail("The modify constraint failed to prevent modification of the managed object");
    } finally {
      // Clean up.
      TestCfg.removeConstraint(constraint);
    }
  }



  // Asserts that the actual set of DNs contains the expected values.
  private void assertDNSetEquals(SortedSet<DN> actual, String... expected) {
    String[] actualStrings = new String[actual.size()];
    int i = 0;
    for (DN dn : actual) {
      actualStrings[i] = dn.toString();
      i++;
    }
    Assert.assertEqualsNoOrder(actualStrings, expected);
  }



  // Create the named test parent managed object.
  private TestParentCfgClient createTestParent(ManagementContext context,
      String name) throws ManagedObjectDecodingException,
      AuthorizationException, ManagedObjectAlreadyExistsException,
      ConcurrentModificationException, OperationRejectedException,
      CommunicationException, IllegalManagedObjectNameException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.createChild(TestCfg.getTestOneToManyParentRelationDefinition(),
        TestParentCfgDefn.getInstance(), name, null).getConfiguration();
  }



  // Retrieve the named test parent managed object.
  private TestParentCfgClient getTestParent(ManagementContext context,
      String name) throws DefinitionDecodingException,
      ManagedObjectDecodingException, AuthorizationException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(),
        name).getConfiguration();
  }



  // List test parent managed objects.
  private String[] listTestParents(ManagementContext context)
      throws AuthorizationException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.listChildren(TestCfg.getTestOneToManyParentRelationDefinition());
  }



  // Remove the named test parent managed object.
  private void removeTestParent(ManagementContext context, String name)
      throws AuthorizationException, ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    root.removeChild(TestCfg.getTestOneToManyParentRelationDefinition(), name);
  }
}

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
package org.opends.server.protocols.ldap;

import static org.testng.Assert.*;

import org.testng.annotations.*;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import static org.opends.server.util.ServerConstants.EOL;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocol.ldap.DeleteRequestProtocolOp class.
 */
public class TestDeleteRequestProtocolOp extends LdapTestCase
{
  /**
   * The protocol op type for delete requests.
   */
  public static final byte OP_TYPE_DELETE_REQUEST = 0x4A;



  /**
   * The protocol op type for delete responses.
   */
  public static final byte OP_TYPE_DELETE_RESPONSE = 0x6B;

  /**
   * The DN for delete requests in this test case.
   */
  private static final ByteString dn =
      ByteString.valueOf("dc=example,dc=com");

  /**
   * The alternative DN for delete requests in this test case.
   */
  private static final ByteString dnAlt =
      ByteString.valueOf("dc=sun,dc=com");

  /**
   * Test the constructors to make sure the right objects are constructed.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testConstructors() throws Exception
  {
    DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(dn);

    assertEquals(deleteRequest.getDN(), dn);
  }

  /**
   * Test to make sure the class processes the right LDAP op type.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testOpType() throws Exception
  {
    DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(dn);
    assertEquals(deleteRequest.getType(), OP_TYPE_DELETE_REQUEST);
  }

  /**
   * Test to make sure the class returns the correct protocol name.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testProtocolOpName() throws Exception
  {
    DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(dn);
    assertEquals(deleteRequest.getProtocolOpName(), "Delete Request");
  }

  /**
   * Test the encode and decode methods and corner cases.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test
  public void testEncodeDecode() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    DeleteRequestProtocolOp deleteEncoded;
    DeleteRequestProtocolOp deleteDecoded;

    deleteEncoded = new DeleteRequestProtocolOp(dn);
    deleteEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    assertEquals(reader.peekType(), OP_TYPE_DELETE_REQUEST);

    deleteDecoded = (DeleteRequestProtocolOp)LDAPReader.readProtocolOp(reader);

    assertEquals(deleteDecoded.getDN(), deleteEncoded.getDN());
  }

  /**
   * Test the encode and decode methods with null params
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = Exception.class)
  public void testNullEncodeDecode() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    DeleteRequestProtocolOp deleteEncoded;
    DeleteRequestProtocolOp deleteDecoded;

    deleteEncoded = new DeleteRequestProtocolOp(null);
    deleteEncoded.write(writer);
    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    deleteDecoded = (DeleteRequestProtocolOp)LDAPReader.readProtocolOp(reader);
  }

  /**
   * Test the decode method when an null element is passed
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(expectedExceptions = LDAPException.class)
  public void testDecodeNullElement() throws Exception
  {
    LDAPReader.readProtocolOp(null);
  }

  /**
   * Test the toString (single line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringSingleLine() throws Exception
  {
    DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(dn);
    StringBuilder buffer = new StringBuilder();

    String expectedStr = "DeleteRequest(dn=" + dn.toString() + ")";
    deleteRequest.toString(buffer);

    assertEquals(buffer.toString(), expectedStr);
  }

  /**
   * Test the toString (multi line) method.
   *
   * @throws Exception If the test fails unexpectedly.
   */
  @Test
  public void TestToStringMultiLine() throws Exception
  {
    DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(dn);
    StringBuilder buffer = new StringBuilder();

    String expectedStr = "   Delete Request" +
        EOL + "     Entry DN:  " +
        dn.toString() +
        EOL;
    deleteRequest.toString(buffer, 3);

    assertEquals(buffer.toString(), expectedStr);
  }
}

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
 *      Portions Copyright 2012 ForgeRock AS.
 */
package org.opends.dsml.protocol;



import java.io.IOException;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.ByteString;
import org.opends.server.types.LDAPException;



/**
 * This class provides the functionality for the performing an
 * LDAP COMPARE operation based on the specified DSML request.
 */
public class DSMLCompareOperation
{
  private LDAPConnection connection;

  /**
   * Create an instance with the specified LDAP connection.
   *
   * @param connection    The LDAP connection to send the request on.
   */
  public DSMLCompareOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }

  /**
   * Perform the LDAP COMPARE operation and send the result back to the
   * client.
   *
   * @param  objFactory      The object factory for this operation.
   * @param  compareRequest  The compare request for this operation.
   * @param  controls        Any required controls (e.g. for proxy authz).
   *
   * @return  The result of the compare operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   *
   * @throws  LDAPException  If an error occurs while interacting with an LDAP
   *                         element.
   *
   * @throws  ASN1Exception  If an error occurs while interacting with an ASN.1
   *                         element.
   */
  public LDAPResult doOperation(ObjectFactory objFactory,
        CompareRequest compareRequest,
        List<org.opends.server.types.Control> controls)
    throws IOException, LDAPException, ASN1Exception
  {
    LDAPResult compareResponse = objFactory.createLDAPResult();
    compareResponse.setRequestID(compareRequest.getRequestID());

    // Read the attribute name and value for the compare request.
    AttributeValueAssertion attrValAssertion = compareRequest.getAssertion();
    String attrName = attrValAssertion.getName();
    Object assertion = attrValAssertion.getValue();
    ByteString attrValue = ByteStringUtility.convertValue(assertion);
    ByteString dnStr = ByteString.valueOf(compareRequest.getDn());

    // Create and send the LDAP compare request to the server.
    ProtocolOp op = new CompareRequestProtocolOp(dnStr, attrName, attrValue);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op,
        controls);
    connection.getLDAPWriter().writeMessage(msg);

    // Read and decode the LDAP response from the server.
    LDAPMessage responseMessage = connection.getLDAPReader().readMessage();

    CompareResponseProtocolOp compareOp =
          responseMessage.getCompareResponseProtocolOp();
    int resultCode = compareOp.getResultCode();
    Message errorMessage = compareOp.getErrorMessage();

    // Set the response code and error message for the DSML response.
    compareResponse.setErrorMessage(
            errorMessage != null ? errorMessage.toString() : null);
    ResultCode code = ResultCodeFactory.create(objFactory, resultCode);
    compareResponse.setResultCode(code);

    if(compareOp.getMatchedDN() != null)
    {
      compareResponse.setMatchedDN(compareOp.getMatchedDN().toString());
    }

    return compareResponse;
  }

}


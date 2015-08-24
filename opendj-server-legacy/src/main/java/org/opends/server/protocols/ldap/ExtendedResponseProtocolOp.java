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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.ldap;

import java.io.IOException;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Utils;
import org.opends.server.types.DN;

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines the structures and methods for an LDAP extended response
 * protocol op, which is used to provide information about the result of
 * processing a extended request.
 */
public class ExtendedResponseProtocolOp
       extends ProtocolOp
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The value for this extended response. */
  private ByteString value;

  /** The matched DN for this response. */
  private DN matchedDN;
  /** The result code for this response. */
  private int resultCode;
  /** The set of referral URLs for this response. */
  private List<String> referralURLs;
  /** The error message for this response. */
  private LocalizableMessage errorMessage;

  /** The OID for this extended response. */
  private String oid;



  /**
   * Creates a new extended response protocol op with the provided result code.
   *
   * @param  resultCode  The result code for this response.
   */
  public ExtendedResponseProtocolOp(int resultCode)
  {
    this.resultCode = resultCode;
  }



  /**
   * Creates a new extended response protocol op with the provided result code
   * and error message.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   */
  public ExtendedResponseProtocolOp(int resultCode, LocalizableMessage errorMessage)
  {
    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
  }



  /**
   * Creates a new extended response protocol op with the provided information.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   * @param  matchedDN     The matched DN for this response.
   * @param  referralURLs  The referral URLs for this response.
   */
  public ExtendedResponseProtocolOp(int resultCode, LocalizableMessage errorMessage,
                                    DN matchedDN, List<String> referralURLs)
  {
    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.matchedDN    = matchedDN;
    this.referralURLs = referralURLs;
  }



  /**
   * Creates a new extended response protocol op with the provided information.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   * @param  matchedDN     The matched DN for this response.
   * @param  referralURLs  The referral URLs for this response.
   * @param  oid           The OID for this extended response.
   * @param  value         The value for this extended response.
   */
  public ExtendedResponseProtocolOp(int resultCode, LocalizableMessage errorMessage,
                                    DN matchedDN, List<String> referralURLs,
                                    String oid, ByteString value)
  {
    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.matchedDN    = matchedDN;
    this.referralURLs = referralURLs;
    this.oid          = oid;
    this.value        = value;
  }



  /**
   * Retrieves the result code for this response.
   *
   * @return  The result code for this response.
   */
  public int getResultCode()
  {
    return resultCode;
  }



  /**
   * Retrieves the error message for this response.
   *
   * @return  The error message for this response, or <CODE>null</CODE> if none
   *          is available.
   */
  public LocalizableMessage getErrorMessage()
  {
    return errorMessage;
  }


  /**
   * Retrieves the matched DN for this response.
   *
   * @return  The matched DN for this response, or <CODE>null</CODE> if none is
   *          available.
   */
  public DN getMatchedDN()
  {
    return matchedDN;
  }



  /**
   * Retrieves the set of referral URLs for this response.
   *
   * @return  The set of referral URLs for this response, or <CODE>null</CODE>
   *          if none are available.
   */
  public List<String> getReferralURLs()
  {
    return referralURLs;
  }



  /**
   * Retrieves the OID for this extended response.
   *
   * @return  The OID for this extended response, or <CODE>null</CODE> if none
   *          was provided.
   */
  public String getOID()
  {
    return oid;
  }



  /**
   * Retrieves the value for this extended response.
   *
   * @return  The value for this extended response, or <CODE>null</CODE> if none
   *          was provided.
   */
  public ByteString getValue()
  {
    return value;
  }

  @Override
  public byte getType()
  {
    return OP_TYPE_EXTENDED_RESPONSE;
  }

  @Override
  public String getProtocolOpName()
  {
    return "Extended Response";
  }

  @Override
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_EXTENDED_RESPONSE);
    stream.writeEnumerated(resultCode);

    if(matchedDN == null)
    {
      stream.writeOctetString((String)null);
    }
    else
    {
      stream.writeOctetString(matchedDN.toString());
    }

    if(errorMessage == null)
    {
      stream.writeOctetString((String)null);
    }
    else
    {
      stream.writeOctetString(errorMessage.toString());
    }

    if (referralURLs != null && !referralURLs.isEmpty())
    {
      stream.writeStartSequence(TYPE_REFERRAL_SEQUENCE);
      for (String s : referralURLs)
      {
        stream.writeOctetString(s);
      }
      stream.writeEndSequence();
    }

    if (oid != null && oid.length() > 0)
    {
      stream.writeOctetString(TYPE_EXTENDED_RESPONSE_OID, oid);
    }

    if (value != null)
    {
      stream.writeOctetString(TYPE_EXTENDED_RESPONSE_VALUE, value);
    }

    stream.writeEndSequence();
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ExtendedResponse(resultCode=");
    buffer.append(resultCode);

    if (errorMessage != null && errorMessage.length() > 0)
    {
      buffer.append(", errorMessage=");
      buffer.append(errorMessage);
    }
    if (matchedDN != null)
    {
      buffer.append(", matchedDN=");
      buffer.append(matchedDN);
    }
    if (referralURLs != null && !referralURLs.isEmpty())
    {
      buffer.append(", referralURLs={");
      Utils.joinAsString(buffer, ", ", referralURLs);
      buffer.append("}");
    }
    if (oid != null && oid.length() > 0)
    {
      buffer.append(", oid=");
      buffer.append(oid);
    }
    if (value != null)
    {
      buffer.append(", value=");
      buffer.append(value);
    }

    buffer.append(")");
  }

  @Override
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Extended Response");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Result Code:  ");
    buffer.append(resultCode);
    buffer.append(EOL);

    if (errorMessage != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Error LocalizableMessage:  ");
      buffer.append(errorMessage);
      buffer.append(EOL);
    }

    if (matchedDN != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Matched DN:  ");
      matchedDN.toString(buffer);
      buffer.append(EOL);
    }

    if (referralURLs != null && !referralURLs.isEmpty())
    {
      buffer.append(indentBuf);
      buffer.append("  Referral URLs:  ");
      buffer.append(EOL);

      for (String s : referralURLs)
      {
        buffer.append(indentBuf);
        buffer.append("  ");
        buffer.append(s);
        buffer.append(EOL);
      }
    }

    if (oid != null && oid.length() > 0)
    {
      buffer.append(indentBuf);
      buffer.append("  Response OID:  ");
      buffer.append(oid);
      buffer.append(EOL);
    }

    if (value != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Response Value:  ");
      buffer.append(value);
      buffer.append(EOL);
    }
  }
}

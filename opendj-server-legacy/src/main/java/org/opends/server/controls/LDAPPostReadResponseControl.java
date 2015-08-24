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
package org.opends.server.controls;
import org.forgerock.i18n.LocalizableMessage;


import org.forgerock.opendj.io.*;
import org.opends.server.protocols.ldap.*;
import org.opends.server.protocols.ldap.LDAPReader;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;


/**
 * This class implements the post-read response control as defined in RFC 4527.
 * This control holds the search result entry representing the state of the
 * entry immediately before an add, modify, or modify DN operation.
 */
public class LDAPPostReadResponseControl
    extends Control
{
  /**
   * ControlDecoder implementation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<LDAPPostReadResponseControl>
  {
    /** {@inheritDoc} */
    public LDAPPostReadResponseControl decode(boolean isCritical,
                                              ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_POSTREADRESP_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      ASN1Reader reader = ASN1.getReader(value);
      SearchResultEntry searchEntry;
      try
      {
        SearchResultEntryProtocolOp searchResultEntryProtocolOp =
            LDAPReader.readSearchEntry(reader);
        searchEntry = searchResultEntryProtocolOp.toSearchResultEntry();
      }
      catch (LDAPException le)
      {
        logger.traceException(le);

        LocalizableMessage message =
            ERR_POSTREADRESP_CANNOT_DECODE_VALUE.get(le.getMessage());
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message,
            le);
      }

      return new LDAPPostReadResponseControl(isCritical, searchEntry);
    }

    public String getOID()
    {
      return OID_LDAP_READENTRY_POSTREAD;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<LDAPPostReadResponseControl> DECODER =
    new Decoder();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /** The search result entry to include in the response control. */
  private SearchResultEntry searchEntry;



  /**
   * Creates a new instance of this LDAP post-read response control with the
   * provided information.
   *
   * @param  searchEntry  The search result entry to include in the response
   *                      control.
   */
  public LDAPPostReadResponseControl(SearchResultEntry searchEntry)
  {
    this(false, searchEntry);
  }



  /**
   * Creates a new instance of this LDAP post-read response control with the
   * provided information.
   *
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the server processing.
   * @param  searchEntry   The search result entry to include in the response
   *                       control.
   */
  public LDAPPostReadResponseControl(boolean isCritical,
                                     SearchResultEntry searchEntry)
  {
    super(OID_LDAP_READENTRY_POSTREAD, isCritical);


    this.searchEntry = searchEntry;
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

    SearchResultEntryProtocolOp protocolOp =
        new SearchResultEntryProtocolOp(searchEntry);
    protocolOp.write(writer);

    writer.writeEndSequence();
  }



  /**
   * Retrieves the search result entry associated with this post-read response
   * control.
   *
   * @return  The search result entry associated with this post-read response
   *          control.
   */
  public SearchResultEntry getSearchEntry()
  {
    return searchEntry;
  }



  /**
   * Appends a string representation of this LDAP post-read response control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPPostReadResponseControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",entry=");
    searchEntry.toSingleLineString(buffer);
    buffer.append(")");
  }
}


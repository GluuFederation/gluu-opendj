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
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;


/**
 * This message is used by an LDAP server to communicate to the topology
 * that the generation must be reset for the domain.
 */
public class ResetGenerationIdMsg extends ReplicationMsg
{
  private long generationId;

  /**
   * Creates a new message.
   * @param generationId The new reference value of the generationID.
   */
  public ResetGenerationIdMsg(long generationId)
  {
    this.generationId = generationId;
  }

  /**
   * Creates a new GenerationIdMessage from its encoded form.
   *
   * @param in The byte array containing the encoded form of the
   *           WindowMessage.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the WindowMessage.
   */
  public ResetGenerationIdMsg(byte[] in) throws DataFormatException
  {
    try
    {
      if (in[0] != MSG_TYPE_RESET_GENERATION_ID)
        throw new
        DataFormatException("input is not a valid GenerationId Message");

      int pos = 1;

      /* read the generationId */
      int length = getNextLength(in, pos);
      generationId = Long.valueOf(new String(in, pos, length,
      "UTF-8"));
      pos += length +1;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    try
    {
      ByteArrayOutputStream oStream = new ByteArrayOutputStream();

      /* Put the message type */
      oStream.write(MSG_TYPE_RESET_GENERATION_ID);

      // Put the generationId
      oStream.write(String.valueOf(generationId).getBytes("UTF-8"));
      oStream.write(0);

      return oStream.toByteArray();
    }
    catch (IOException e)
    {
      // never happens
      return null;
    }
  }

  /**
   * Returns the generation Id set in this message.
   * @return the value of the generation ID.
   *
   */
  public long getGenerationId()
  {
    return this.generationId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ResetGenerationIdMsg content: " +
      "\ngenerationId: " + generationId;
  }
}

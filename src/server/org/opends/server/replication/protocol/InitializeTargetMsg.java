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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to one or several servers as the
 * first message of an export, before sending the entries.
 */
public class InitializeTargetMsg extends RoutableMsg
{
  private String baseDN = null;

  // Specifies the number of entries expected to be exported.
  private long entryCount;

  // Specifies the serverID of the server that requested this export
  // to happen. It allows a server that previously sent an
  // InitializeRequestMessage to know that the current message
  // is related to its own request.
  private int requestorID;

  private int initWindow;

  /**
   * Creates a InitializeTargetMsg.
   *
   * @param baseDN     The base DN for which the InitializeMessage is created.
   * @param serverID   The serverID of the server that sends this message.
   * @param target     The destination of this message.
   * @param target2    The server that initiates this export.
   * @param entryCount The count of entries that will be sent.
   * @param initWindow the initialization window.
   */
  public InitializeTargetMsg(String baseDN, int serverID,
      int target, int target2, long entryCount, int initWindow)
  {
    super(serverID, target);
    this.requestorID = target2;
    this.baseDN = baseDN;
    this.entryCount = entryCount;
    this.initWindow = initWindow; // V4
  }

  /**
   * Creates an InitializeTargetMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the Message
   * @param version The protocol version to use to decode the msg
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded InitializeMessage.
   */
  public InitializeTargetMsg(byte[] in, short version)
  throws DataFormatException
  {
    super();
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_INITIALIZE_TARGET)
        throw new DataFormatException(
            "input is not a valid InitializeDestinationMessage");
      int pos = 1;

      // destination
      int length = getNextLength(in, pos);
      String destinationString = new String(in, pos, length, "UTF-8");
      this.destination = Integer.valueOf(destinationString);
      pos += length +1;

      // baseDn
      length = getNextLength(in, pos);
      baseDN = new String(in, pos, length, "UTF-8");
      pos += length +1;

      // sender
      length = getNextLength(in, pos);
      String senderString = new String(in, pos, length, "UTF-8");
      senderID = Integer.valueOf(senderString);
      pos += length +1;

      // requestor
      length = getNextLength(in, pos);
      String requestorString = new String(in, pos, length, "UTF-8");
      requestorID = Integer.valueOf(requestorString);
      pos += length +1;

      // entryCount
      length = getNextLength(in, pos);
      String entryCountString = new String(in, pos, length, "UTF-8");
      entryCount = Long.valueOf(entryCountString);
      pos += length +1;

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        // init window
        length = getNextLength(in, pos);
        String initWindowString = new String(in, pos, length, "UTF-8");
        initWindow = Integer.valueOf(initWindowString);
        pos += length +1;
      }
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the number of entries expected to be sent during the export.
   * @return the entry count
   */
  public long getEntryCount()
  {
    return this.entryCount;
  }

  /**
   * Get the serverID of the server that initiated the export.
   * Roughly it is the server running the task,
   * - the importer for the Initialize task,
   * - the exporter for the InitializeRemote task.
   * @return the serverID
   */
  public long getInitiatorID()
  {
    return this.requestorID;
  }

  /**
   * Get the base DN of the domain.
   *
   * @return the base DN
   */
  public String getBaseDN()
  {
    return this.baseDN;
  }

  /**
   * Get the initializationWindow.
   *
   * @return the initialization window.
   */
  public int getInitWindow()
  {
    return this.initWindow;
  }

  // ============
  // Msg encoding
  // ============

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short version)
  throws UnsupportedEncodingException
  {
    try
    {
      byte[] byteDestination = String.valueOf(destination).getBytes("UTF-8");
      byte[] byteDn = baseDN.getBytes("UTF-8");
      byte[] byteSender = String.valueOf(senderID).getBytes("UTF-8");
      byte[] byteRequestor = String.valueOf(requestorID).getBytes("UTF-8");
      byte[] byteEntryCount = String.valueOf(entryCount).getBytes("UTF-8");
      byte[] byteInitWindow = null;
      int length = 1 + byteDestination.length + 1
                     + byteDn.length + 1
                     + byteSender.length + 1
                     + byteRequestor.length + 1
                     + byteEntryCount.length + 1;

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        byteInitWindow = String.valueOf(initWindow).getBytes("UTF-8");
        length += byteInitWindow.length + 1;
      }

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_INITIALIZE_TARGET;
      int pos = 1;

      /* put the destination */
      pos = addByteArray(byteDestination, resultByteArray, pos);

      /* put the baseDN and a terminating 0 */
      pos = addByteArray(byteDn, resultByteArray, pos);

      /* put the sender */
      pos = addByteArray(byteSender, resultByteArray, pos);

      /* put the requestorID */
      pos = addByteArray(byteRequestor, resultByteArray, pos);

      /* put the entryCount */
      pos = addByteArray(byteEntryCount, resultByteArray, pos);

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        /* put the initWindow */
        pos = addByteArray(byteInitWindow, resultByteArray, pos);
      }

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * Set the initWindow value.
   * @param initWindow The initialization window.
   */
  public void setInitWindow(int initWindow)
  {
    this.initWindow = initWindow;
  }
}

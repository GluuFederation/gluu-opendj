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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ServerState;

/**
 * Message sent by a replication server to another replication server
 * at Startup.
 */
public class ReplServerStartMsg extends StartMsg
{
  private Integer serverId;
  private String serverURL;
  private String baseDn = null;
  private int windowSize;
  private ServerState serverState;

  /**
   * Whether to continue using SSL to encrypt messages after the start
   * messages have been exchanged.
   */
  private boolean sslEncryption;

  /**
   * NOTE: Starting from protocol V4, we introduce a dedicated PDU for answering
   * to the DS ServerStartMsg. This is the ReplServerStartDSMsg. So the
   * degradedStatusThreshold value being used only by a DS, it could be removed
   * from the ReplServerStartMsg PDU. However for a smoothly transition to V4
   * protocol, we prefer to let this variable also in this PDU but the one
   * really used is in the ReplServerStartDSMsg PDU. This prevents from having
   * only RSv3 able to connect to RSv4 as connection initiator.
   *
   * Threshold value used by the RS to determine if a DS must be put in
   * degraded status because the number of pending changes for him has crossed
   * this value. This field is only used by a DS.
   */
  private int degradedStatusThreshold = -1;

  /**
   * Create a ReplServerStartMsg.
   *
   * @param serverId replication server id
   * @param serverURL replication server URL
   * @param baseDn base DN for which the ReplServerStartMsg is created.
   * @param windowSize The window size.
   * @param serverState our ServerState for this baseDn.
   * @param generationId The generationId for this server.
   * @param sslEncryption Whether to continue using SSL to encrypt messages
   *                      after the start messages have been exchanged.
   * @param groupId The group id of the RS
   * @param degradedStatusThreshold The degraded status threshold
   */
  public ReplServerStartMsg(int serverId, String serverURL, String baseDn,
                               int windowSize,
                               ServerState serverState,
                               long generationId,
                               boolean sslEncryption,
                               byte groupId,
                               int degradedStatusThreshold)
  {
    super((short) -1 /* version set when sending */, generationId);
    this.serverId = serverId;
    this.serverURL = serverURL;
    if (baseDn != null)
      this.baseDn = baseDn;
    else
      this.baseDn = null;
    this.windowSize = windowSize;
    this.serverState = serverState;
    this.sslEncryption = sslEncryption;
    this.groupId = groupId;
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /**
   * Creates a new ReplServerStartMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the
   *             ReplServerStartMsg
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded ReplServerStartMsg.
   */
  public ReplServerStartMsg(byte[] in) throws DataFormatException
  {
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_REPL_SERVER_START;
    allowedPduTypes[1] = MSG_TYPE_REPL_SERVER_START_V1;
    headerLength = decodeHeader(allowedPduTypes, in);

    // Protocol version has been read as part of the header:
    // decode the body according to the protocol version read in the header
    switch(protocolVersion)
    {
      case ProtocolVersion.REPLICATION_PROTOCOL_V1:
        decodeBody_V1(in, headerLength);
        return;
    }

    try
    {
      /* The ReplServerStartMsg payload is stored in the form :
       * <baseDn><serverId><serverURL><windowSize><sslEncryption>
       * <degradedStatusThreshold><serverState>
       */

      /* first bytes are the header */
      int pos = headerLength;

      /* read the dn
       * first calculate the length then construct the string
       */
      int length = getNextLength(in, pos);
      baseDn = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the ServerId
       */
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      serverId = Integer.valueOf(serverIdString);
      pos += length +1;

      /*
       * read the ServerURL
       */
      length = getNextLength(in, pos);
      serverURL = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the window size
       */
      length = getNextLength(in, pos);
      windowSize = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the sslEncryption setting
       */
      length = getNextLength(in, pos);
      sslEncryption = Boolean.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /**
       * read the degraded status threshold
       */
      length = getNextLength(in, pos);
      degradedStatusThreshold =
        Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length + 1;

      // Read the ServerState
      // Caution: ServerState MUST be the last field. Because ServerState can
      // contain null character (string termination of serverid string ..) it
      // cannot be decoded using getNextLength() like the other fields. The
      // only way is to rely on the end of the input buffer : and that forces
      // the ServerState to be the last. This should be changed and we want to
      // have more than one ServerState field.
      serverState = new ServerState(in, pos, in.length - 1);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Decodes the body of a just received ReplServerStartMsg. The body is in the
   * passed array, and starts at the provided location. This is for a PDU
   * encoded in V1 protocol version.
   * @param in A byte array containing the body for the ReplServerStartMsg
   * @param pos The position in the array where the decoding should start
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded ReplServerStartMsg.
   */
  public void decodeBody_V1(byte[] in, int pos) throws DataFormatException
  {
    try
    {
      /* The ReplServerStartMsg payload is stored in the form :
       * <baseDn><serverId><serverURL><windowSize><sslEncryption>
       * <serverState>
       */

      /* read the dn
       * first calculate the length then construct the string
       */
      int length = getNextLength(in, pos);
      baseDn = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the ServerId
       */
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      serverId = Integer.valueOf(serverIdString);
      pos += length +1;

      /*
       * read the ServerURL
       */
      length = getNextLength(in, pos);
      serverURL = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the window size
       */
      length = getNextLength(in, pos);
      windowSize = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the sslEncryption setting
       */
      length = getNextLength(in, pos);
      sslEncryption = Boolean.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      // Read the ServerState
      // Caution: ServerState MUST be the last field. Because ServerState can
      // contain null character (string termination of serverid string ..) it
      // cannot be decoded using getNextLength() like the other fields. The
      // only way is to rely on the end of the input buffer : and that forces
      // the ServerState to be the last. This should be changed and we want to
      // have more than one ServerState field.
      serverState = new ServerState(in, pos, in.length - 1);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the Server Id.
   * @return the server id
   */
  public int getServerId()
  {
    return this.serverId;
  }

  /**
   * Get the server URL.
   * @return the server URL
   */
  public String getServerURL()
  {
    return this.serverURL;
  }

  /**
   * Get the base DN from this ReplServerStartMsg.
   *
   * @return the base DN from this ReplServerStartMsg.
   */
  public String getBaseDn()
  {
    return baseDn;
  }

  /**
   * Get the serverState.
   * @return Returns the serverState.
   */
  public ServerState getServerState()
  {
    return this.serverState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short sessionProtocolVersion)
     throws UnsupportedEncodingException
  {
    // If an older version requested, encode in the requested way
    switch(sessionProtocolVersion)
    {
      case ProtocolVersion.REPLICATION_PROTOCOL_V1:
        return getBytes_V1();
    }

    /* The ReplServerStartMsg is stored in the form :
     * <operation type><baseDn><serverId><serverURL><windowSize><sslEncryption>
     * <degradedStatusThreshold><serverState>
     */

    byte[] byteDn = baseDn.getBytes("UTF-8");
    byte[] byteServerId = String.valueOf(serverId).getBytes("UTF-8");
    byte[] byteServerUrl = serverURL.getBytes("UTF-8");
    byte[] byteServerState = serverState.getBytes();
    byte[] byteWindowSize = String.valueOf(windowSize).getBytes("UTF-8");
    byte[] byteSSLEncryption =
      String.valueOf(sslEncryption).getBytes("UTF-8");
    byte[] byteDegradedStatusThreshold =
      String.valueOf(degradedStatusThreshold).getBytes("UTF-8");

    int length = byteDn.length + 1 + byteServerId.length + 1 +
      byteServerUrl.length + 1 + byteWindowSize.length + 1 +
      byteSSLEncryption.length + 1 +
      byteDegradedStatusThreshold.length + 1 +
      byteServerState.length + 1;

    /* encode the header in a byte[] large enough */
    byte resultByteArray[] = encodeHeader(MSG_TYPE_REPL_SERVER_START, length,
        sessionProtocolVersion);

    int pos = headerLength;

    /* put the baseDN and a terminating 0 */
    pos = addByteArray(byteDn, resultByteArray, pos);

    /* put the ServerId */
    pos = addByteArray(byteServerId, resultByteArray, pos);

    /* put the ServerURL */
    pos = addByteArray(byteServerUrl, resultByteArray, pos);

    /* put the window size */
    pos = addByteArray(byteWindowSize, resultByteArray, pos);

    /* put the SSL Encryption setting */
    pos = addByteArray(byteSSLEncryption, resultByteArray, pos);

    /* put the degraded status threshold */
    pos = addByteArray(byteDegradedStatusThreshold, resultByteArray, pos);

    /* put the ServerState */
    pos = addByteArray(byteServerState, resultByteArray, pos);

    return resultByteArray;
  }

  /**
   * get the window size for the server that created this message.
   *
   * @return The window size for the server that created this message.
   */
  public int getWindowSize()
  {
    return windowSize;
  }

  /**
   * Get the SSL encryption value for the server that created the
   * message.
   *
   * @return The SSL encryption value for the server that created the
   *         message.
   */
  public boolean getSSLEncryption()
  {
    return sslEncryption;
  }

  /**
   * Get the degraded status threshold value.
   * @return The degraded status threshold value.
   */
  public int getDegradedStatusThreshold()
  {
    return degradedStatusThreshold;
  }

  /**
   * Set the degraded status threshold (For test purpose).
   * @param degradedStatusThreshold The degraded status threshold to set.
   */
  public void setDegradedStatusThreshold(int degradedStatusThreshold)
  {
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ReplServerStartMsg content: " +
      "\nprotocolVersion: " + protocolVersion +
      "\ngenerationId: " + generationId +
      "\nbaseDn: " + baseDn +
      "\ngroupId: " + groupId +
      "\nserverId: " + serverId +
      "\nserverState: " + serverState +
      "\nserverURL: " + serverURL +
      "\nsslEncryption: " + sslEncryption +
      "\ndegradedStatusThreshold: " + degradedStatusThreshold +
      "\nwindowSize: " + windowSize;
  }

  /**
   * Get the byte array representation of this Message. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @return The byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    /*
     * The ReplServerStartMessage is stored in the form :
     * <operation type><basedn><serverid><serverURL><windowsize><serverState>
     */
    try {
      byte[] byteDn = baseDn.getBytes("UTF-8");
      byte[] byteServerId = String.valueOf(serverId).getBytes("UTF-8");
      byte[] byteServerUrl = serverURL.getBytes("UTF-8");
      byte[] byteServerState = serverState.getBytes();
      byte[] byteWindowSize = String.valueOf(windowSize).getBytes("UTF-8");
      byte[] byteSSLEncryption =
                     String.valueOf(sslEncryption).getBytes("UTF-8");

      int length = byteDn.length + 1 + byteServerId.length + 1 +
                   byteServerUrl.length + 1 + byteWindowSize.length + 1 +
                   byteSSLEncryption.length + 1 +
                   byteServerState.length + 1;

      /* encode the header in a byte[] large enough */
      byte resultByteArray[] = encodeHeader_V1(MSG_TYPE_REPL_SERVER_START_V1,
        length);
      int pos = headerLength;

      /* put the baseDN and a terminating 0 */
      pos = addByteArray(byteDn, resultByteArray, pos);

      /* put the ServerId */
      pos = addByteArray(byteServerId, resultByteArray, pos);

      /* put the ServerURL */
      pos = addByteArray(byteServerUrl, resultByteArray, pos);

      /* put the window size */
      pos = addByteArray(byteWindowSize, resultByteArray, pos);

      /* put the SSL Encryption setting */
      pos = addByteArray(byteSSLEncryption, resultByteArray, pos);

      /* put the ServerState */
      pos = addByteArray(byteServerState, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }
}

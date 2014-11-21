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
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ChangeNumber;

/**
 * AckMsg messages are used for acknowledging an update that has been sent
 * requesting an ack: update sent in Assured Mode, either safe data or safe
 * read sub mode.
 * The change number refers to the update change number that was requested to be
 * acknowledged.
 * If some errors occurred during attempt to acknowledge the update in the path
 * to final servers, errors are marked with the following fields:
 * - hasTimeout:
 * Some servers went in timeout when the matching update was sent.
 * - hasWrongStatus:
 * Some servers were in a status where we cannot ask for an ack when the
 * matching update was sent.
 * - hasReplayError:
 * Some servers made an error replaying the sent matching update.
 * - failedServers:
 * The list of server ids that had errors for the sent matching update. Each
 * server id of the list had one of the 3 possible errors (timeout/wrong status
 * /replay error)
 *
 * AckMsg messages are sent all along the reverse path of the path followed
 * an update message.
 */
public class AckMsg extends ReplicationMsg
{
  // ChangeNumber of the update that was acked.
  private ChangeNumber changeNumber;

  // Did some servers go in timeout when the matching update (corresponding to
  // change number) was sent ?
  private boolean hasTimeout = false;

  // Were some servers in wrong status when the matching
  // update (correspondig to change number) was sent ?
  private boolean hasWrongStatus = false;

  // Did some servers make an error replaying the sent matching update
  // (corresponding to change number) ?
  private boolean hasReplayError = false;

  // The list of server ids that had errors for the sent matching update
  // (corresponding to change number). Each server id of the list had one of the
  // 3 possible errors (timeout/degraded or admin/replay error)
  private List<Integer> failedServers = new ArrayList<Integer>();

  /**
   * Creates a new AckMsg from a ChangeNumber (no errors).
   *
   * @param changeNumber The ChangeNumber used to build the AckMsg.
   */
  public AckMsg(ChangeNumber changeNumber)
  {
    this.changeNumber = changeNumber;
  }

  /**
   * Creates a new AckMsg from a ChangeNumber (with specified error info).
   *
   * @param changeNumber The ChangeNumber used to build the AckMsg.
   * @param hasTimeout The hasTimeout info
   * @param hasWrongStatus The hasWrongStatus info
   * @param hasReplayError The hasReplayError info
   * @param failedServers The list of failed servers
   */
  public AckMsg(ChangeNumber changeNumber, boolean hasTimeout,
    boolean hasWrongStatus, boolean hasReplayError, List<Integer> failedServers)
  {
    this.changeNumber = changeNumber;
    this.hasTimeout = hasTimeout;
    this.hasWrongStatus = hasWrongStatus;
    this.hasReplayError = hasReplayError;
    this.failedServers = failedServers;
  }

  /**
   * Sets the timeout marker for this message.
   * @param hasTimeout True if some timeout occurred
   */
  public void setHasTimeout(boolean hasTimeout)
  {
    this.hasTimeout = hasTimeout;
  }

  /**
   * Sets the wrong status marker for this message.
   * @param hasWrongStatus True if some servers were in wrong status
   */
  public void setHasWrongStatus(boolean hasWrongStatus)
  {
    this.hasWrongStatus = hasWrongStatus;
  }

  /**
   * Sets the replay error marker for this message.
   * @param hasReplayError True if some servers had errors replaying the change
   */
  public void setHasReplayError(boolean hasReplayError)
  {
    this.hasReplayError = hasReplayError;
  }

  /**
   * Sets the list of failing servers for this message.
   * @param idList The list of failing servers for this message.
   */
  public void setFailedServers(List<Integer> idList)
  {
    this.failedServers = idList;
  }

  /**
   * Creates a new AckMsg by decoding the provided byte array.
   *
   * @param in The byte array containing the encoded form of the AckMsg.
   * @throws DataFormatException If in does not contain a properly encoded
   *                             AckMsg.
   */
  public AckMsg(byte[] in) throws DataFormatException
  {
    try
    {
      /*
       * The message is stored in the form:
       * <operation type><change number><has timeout><has degraded><has replay
       * error><failed server ids>
       */

      /* First byte is the type */
      if (in[0] != MSG_TYPE_ACK)
      {
        throw new DataFormatException("byte[] is not a valid modify msg");
      }
      int pos = 1;

      /* Read the changeNumber */
      int length = getNextLength(in, pos);
      String changenumberStr = new String(in, pos, length, "UTF-8");
      changeNumber = new ChangeNumber(changenumberStr);
      pos += length + 1;

      /* Read the hasTimeout flag */
      hasTimeout = in[pos++] == 1;

      /* Read the hasWrongStatus flag */
      hasWrongStatus = in[pos++] == 1;

      /* Read the hasReplayError flag */
      hasReplayError = in[pos++] == 1;

      /* Read the list of failed server ids */
      while (pos < in.length)
      {
        length = getNextLength(in, pos);
        String serverIdString = new String(in, pos, length, "UTF-8");
        Integer serverId = Integer.valueOf(serverIdString);
        failedServers.add(serverId);
        pos += length + 1;
      }
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the ChangeNumber from the message.
   *
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    try
    {
      /*
       * The message is stored in the form:
       * <operation type><change number><has timeout><has degraded><has replay
       * error><failed server ids>
       */

      ByteArrayOutputStream oStream = new ByteArrayOutputStream(200);

      /* Put the type of the operation */
      oStream.write(MSG_TYPE_ACK);

      /* Put the ChangeNumber */
      byte[] changeNumberByte = changeNumber.toString().getBytes("UTF-8");
      oStream.write(changeNumberByte);
      oStream.write(0);

      /* Put the hasTimeout flag */
      oStream.write((hasTimeout ? (byte) 1 : (byte) 0));

      /* Put the hasWrongStatus flag */
      oStream.write((hasWrongStatus ? (byte) 1 : (byte) 0));

      /* Put the hasReplayError flag */
      oStream.write((hasReplayError ? (byte) 1 : (byte) 0));

      /* Put the list of server ids */
      for (Integer sid : failedServers)
      {
        byte[] byteServerId = String.valueOf(sid).getBytes("UTF-8");
        oStream.write(byteServerId);
        oStream.write(0);
      }

      return oStream.toByteArray();
    } catch (IOException e)
    {
      // never happens
      return null;
    }
  }

  /**
   * Tells if the matching update had timeout.
   * @return true if the matching update had timeout
   */
  public boolean hasTimeout()
  {
    return hasTimeout;
  }

  /**
   * Tells if the matching update had wrong status error.
   * @return true if the matching update had wrong status error
   */
  public boolean hasWrongStatus()
  {
    return hasWrongStatus;
  }

  /**
   * Tells if the matching update had replay error.
   * @return true if the matching update had replay error
   */
  public boolean hasReplayError()
  {
    return hasReplayError;
  }

  /**
   * Get the list of failed servers.
   * @return the list of failed servers
   */
  public List<Integer> getFailedServers()
  {
    return failedServers;
  }

  /**
   * Transforms the errors information of the ack into human readable string.
   * @return A human readable string for errors embedded in the message.
   */
  public String errorsToString()
  {
    String idList;
    if (failedServers.size() > 0)
    {
      idList = "[";
      int size = failedServers.size();
      for (int i=0 ; i<size ; i++) {
        idList += failedServers.get(i);
        if ( i != (size-1) )
          idList += ", ";
      }
      idList += "]";
    } else
    {
      idList="none";
    }

    return "hasTimeout: " + (hasTimeout ? "yes" : "no")  + ", " +
      "hasWrongStatus: " + (hasWrongStatus ? "yes" : "no")  + ", " +
      "hasReplayError: " + (hasReplayError ? "yes" : "no")  + ", " +
      "concerned server ids: " + idList;
  }

}


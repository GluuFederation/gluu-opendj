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
 */
package org.opends.server.replication.protocol;

/**
 * This is an abstract class of messages of the replication protocol
 * for message that needs to contain information about the server that
 * send them and the destination servers to which they should be sent.
 */
public abstract class RoutableMsg extends ReplicationMsg
{

  /**
   *  Special values for the server ids fields contained in the routable
   *  messages.
   **/

  /**
   *  Specifies that no server is identified.
   */
  public static final int UNKNOWN_SERVER      = -1;
  /**
   * Specifies all servers in the replication domain.
   */
  public static final int ALL_SERVERS         = -2;
  /**
   * Inside a topology of servers in the same domain, it specifies
   * the server that is the "closest" to the sender.
   */
  public static final int THE_CLOSEST_SERVER  = -3;

  /**
   * The destination server or servers of this message.
   */
  protected int destination = UNKNOWN_SERVER;
  /**
   * The serverID of the server that sends this message.
   */
  protected int senderID = UNKNOWN_SERVER;

  /**
   * Creates a routable message.
   * @param serverID replication server id
   * @param destination replication server id
   */
  public RoutableMsg(int serverID, int destination)
  {
    this.senderID = serverID;
    this.destination = destination;
  }

  /**
   * Creates a routable message.
   */
  public RoutableMsg()
  {
  }

  /**
   * Get the destination. The value is a serverId, or ALL_SERVERS dedicated
   * value.
   * @return the destination
   */
  public int getDestination()
  {
    return this.destination;
  }

  /**
   * Get the server ID of the server that sent this message.
   * @return the server id
   */
  public int getSenderID()
  {
    return this.senderID;
  }

  /**
   * Returns a string representation of the message.
   *
   * @return the string representation of this message.
   */
  public String toString()
  {
    return "["+
      this.getClass().getCanonicalName() +
      " sender=" + this.senderID +
      " destination=" + this.destination + "]";
  }
}

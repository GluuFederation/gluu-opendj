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
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.common;

import org.opends.server.util.TimeThread;

/**
 * This class defines a structure that is used for storing the
 * last change numbers generated on this server or received from other servers
 * and generating new changenumbers that are guaranteed to be larger than
 * all the previously seen or generated change numbers.
 */
public class ChangeNumberGenerator
{
  private long lastTime;
  private int seqnum;
  private int serverId;

  /**
   * Create a new ChangeNumber Generator.
   * @param serverID2 id to use when creating change numbers.
   * @param timestamp time to start with.
   */
  public ChangeNumberGenerator(int serverID2, long timestamp)
  {
    this.lastTime = timestamp;
    this.serverId = serverID2;
    this.seqnum = 0;
  }

  /**
  * Create a new ChangeNumber Generator.
  *
  * @param id id to use when creating change numbers.
  * @param state This generator will be created in a way that makes sure that
  *              all change numbers generated will be larger than all the
  *              changenumbers currently in state.
  */
 public ChangeNumberGenerator(int id, ServerState state)
 {
   this.lastTime = TimeThread.getTime();
   for (int stateId : state)
   {
     if (this.lastTime < state.getMaxChangeNumber(stateId).getTime())
       this.lastTime = state.getMaxChangeNumber(stateId).getTime();
     if (stateId == id)
       this.seqnum = state.getMaxChangeNumber(id).getSeqnum();
   }
   this.serverId = id;

 }

  /**
   * Generate a new ChangeNumber.
   *
   * @return the generated ChangeNUmber
   */
  public ChangeNumber newChangeNumber()
  {
    long curTime = TimeThread.getTime();
    int mySeqnum;
    long myTime;

    synchronized(this)
    {
      if (curTime > lastTime)
      {
        lastTime = curTime;
      }

      if (++seqnum <= 0)
      {
        seqnum = 0;
        lastTime++;
      }
      mySeqnum = seqnum;
      myTime = lastTime;
    }

    return new ChangeNumber(myTime, mySeqnum, serverId);

  }

  /**
   * Adjust the lastTime of this Changenumber generator with
   * a ChangeNumber that we have received from another server.
   * This is necessary because we need that the changenumber generated
   * after processing an update received from other hosts to be larger
   * than the received changenumber
   *
   * @param number the ChangeNumber to adjust with
   */
  public void adjust(ChangeNumber number)
  {
    if (number==null)
    {
      synchronized(this)
      {
        lastTime = TimeThread.getTime();
        seqnum = 0;
      }
      return;
    }

    long rcvdTime = number.getTime();

    int changeServerId = number.getServerId();
    int changeSeqNum = number.getSeqnum();

    /* need to synchronize with NewChangeNumber method so that we
     * protect writing lastTime fields
     */
    synchronized(this)
    {
      if (lastTime <= rcvdTime)
      {
        lastTime = ++rcvdTime;
      }

      if ((serverId == changeServerId) && (seqnum < changeSeqNum))
      {
        seqnum = changeSeqNum;
      }
    }
  }

  /**
   * Adjust utility method that takes ServerState as a parameter.
   * @param state the ServerState to adjust with
   */
  public void adjust(ServerState state)
  {
    for (int localServerId : state)
    {
      adjust(state.getMaxChangeNumber(localServerId));
     }
  }
}

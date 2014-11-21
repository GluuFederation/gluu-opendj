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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.common;

import static org.testng.Assert.*;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.util.TimeThread;
import org.testng.annotations.Test;

public class ChangeNumberGeneratorTest extends ReplicationTestCase
{
  /**
   * Test the adjust method of ChangeNumberGenerator
   */
  @Test
  public void adjustTest()
  {
    ChangeNumberGenerator generator =
      new ChangeNumberGenerator(5, TimeThread.getTime());

    ChangeNumber cn = generator.newChangeNumber();

    ChangeNumber cn1 =
      new ChangeNumber(cn.getTime() + 5000, cn.getSeqnum(), 6);
    generator.adjust(cn1);

    ChangeNumber cn2 = generator.newChangeNumber();

    assertTrue((cn2.compareTo(cn1)>0),
        "ChangeNumberGenerator generated an earlier ChangeNumber "
        + " after calling the adjust method.");
  }

  @Test
  public void adjustSameMilliTest()
  {
    ChangeNumberGenerator generator =
      new ChangeNumberGenerator(5, TimeThread.getTime());

    ChangeNumber cn = generator.newChangeNumber();

    ChangeNumber cn1 =
      new ChangeNumber(cn.getTime(), cn.getSeqnum() + 10, 6);
    generator.adjust(cn1);

    ChangeNumber cn2 = generator.newChangeNumber();

    assertTrue((cn2.compareTo(cn1)>0),
        "ChangeNumberGenerator generated an earlier ChangeNumber "
        + " after calling the adjust method.");
  }

  /**
   * Test the correct behavior of the ChangeNumberGenerator when
   * the seqnum is rolling over its limit
   */
  @Test
  public void adjustRollingSeqnum()
  {
    ServerState state = new ServerState();
    ChangeNumber cn1 = new ChangeNumber(TimeThread.getTime(), Integer.MAX_VALUE, 5);
    state.update(cn1);

    ChangeNumberGenerator generator = new ChangeNumberGenerator(5, state);

    ChangeNumber cn2 = generator.newChangeNumber();

    assertTrue(cn2.getSeqnum() == 0);
    assertTrue(cn2.getTime()>cn1.getTime());
  }
}

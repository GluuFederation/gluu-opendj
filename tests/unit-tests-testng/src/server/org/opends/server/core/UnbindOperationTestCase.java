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
 */
package org.opends.server.core;



import java.util.ArrayList;

import org.testng.annotations.Test;

import org.opends.messages.Message;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.*;

import static org.testng.Assert.*;



/**
 * A set of test cases for unbind operations
 */
public class UnbindOperationTestCase
       extends OperationTestCase
{
  /**
   * {@inheritDoc}
   */
  @Override()
  protected Operation[] createTestOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
      new UnbindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                          null),
      new UnbindOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                          new ArrayList<Control>())
    };
  }



  /**
   * Invokes a number of operation methods on the provided unbind operation for
   * which all processing has been completed.
   *
   * @param  unbindOperation  The operation to be tested.
   */
  private void examineCompletedOperation(UnbindOperation unbindOperation)
  {
    assertTrue(unbindOperation.getProcessingStartTime() > 0);
    assertTrue(unbindOperation.getProcessingStopTime() > 0);
    assertTrue(unbindOperation.getProcessingTime() >= 0);
    assertNotNull(unbindOperation.getResponseLogElements());
  }



  /**
   * Attempts an internal unbind operation.  This won't actually do anything,
   * since there's nothing to disconnect with an internal connection, but it
   * will at least exercise the code path.
   */
  @Test()
  public void testUnbindInternal()
  {
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    UnbindOperationBasis unbindOperation =
         new UnbindOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(), new ArrayList<Control>());
    unbindOperation.run();
    examineCompletedOperation(unbindOperation);

//    assertTrue(InvocationCounterPlugin.getPreParseCount() > 0);
//    assertTrue(InvocationCounterPlugin.getPostOperationCount() > 0);
  }



  /**
   * Tests the <CODE>cancel</CODE> method to ensure that it indicates that the
   * operation cannot be cancelled.
   */
  @Test()
  public void testCancel()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CancelRequest cancelRequest =
         new CancelRequest(false, Message.raw("Test Unbind Cancel"));

    UnbindOperationBasis unbindOperation =
         new UnbindOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(), new ArrayList<Control>());
    assertEquals(unbindOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);
  }



  /**
   * Tests the <CODE>getCancelRequest</CODE> method to ensure that it always
   * returns <CODE>null</CODE>.
   */
  @Test
  public void testGetCancelRequest()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    CancelRequest cancelRequest =
         new CancelRequest(false, Message.raw("Test Unbind Cancel"));

    UnbindOperationBasis unbindOperation =
         new UnbindOperationBasis(conn, InternalClientConnection.nextOperationID(),
                             InternalClientConnection.nextMessageID(), new ArrayList<Control>());
    assertNull(unbindOperation.getCancelRequest());

    assertEquals(unbindOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);

    assertNull(unbindOperation.getCancelRequest());
  }
}


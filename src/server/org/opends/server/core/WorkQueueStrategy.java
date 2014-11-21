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
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.core;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;

/**
 *
 * This class implements the work queue strategy.
 */
public class WorkQueueStrategy implements QueueingStrategy {

  /**
   * Put the request in the work queue.
   *
   * @param operation Operation to put in the work queue.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs in the Directory Server.
   */
  @Override
  public void enqueueRequest(Operation operation) throws DirectoryException {
    DirectoryServer.enqueueRequest(operation);
  }
}

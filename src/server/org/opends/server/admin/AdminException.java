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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import org.opends.messages.Message;

import org.opends.server.types.OpenDsException;



/**
 * Exceptions thrown when interacting with administration framework.
 */
public abstract class AdminException extends OpenDsException {

  /**
   * Fake serialization ID.
   */
  private static final long serialVersionUID = 1L;



  /**
   * Create an admin exception with a message and cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  protected AdminException(Message message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an admin exception with a message.
   *
   * @param message
   *          The message.
   */
  protected AdminException(Message message) {
    super(message);
  }
}

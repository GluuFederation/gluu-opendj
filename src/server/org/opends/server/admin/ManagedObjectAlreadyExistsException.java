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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import static org.opends.messages.AdminMessages.*;



/**
 * A managed object could not be created because there is an existing
 * managed object with the same name.
 */
public final class ManagedObjectAlreadyExistsException extends
    OperationsException {

  /**
   * Version ID required by serializable classes.
   */
  private static final long serialVersionUID = -2344653674171609366L;



  /**
   * Create a managed object already exists exception.
   */
  public ManagedObjectAlreadyExistsException() {
    super(ERR_MANAGED_OBJECT_ALREADY_EXISTS_EXCEPTION.get());
  }
}

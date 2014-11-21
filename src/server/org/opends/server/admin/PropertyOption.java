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



/**
 * This enumeration contains various options that can be associated
 * with property definitions.
 */
public enum PropertyOption {
  /**
   * Use this option to identify properties which should be considered
   * as advanced and should not be exposed by default in client
   * applications.
   */
  ADVANCED,

  /**
   * Use this option to identify properties which must not be directly
   * exposed in client applications.
   */
  HIDDEN,

  /**
   * Use this option to identify properties which must have a value.
   */
  MANDATORY,

  /**
   * Use this option to identify properties which are multi-valued.
   */
  MULTI_VALUED,

  /**
   * Use this option to identify properties which can be initialized
   * once only and are read-only thereafter.
   */
  READ_ONLY,

  /**
   * Use this option to identify properties which are for monitoring
   * purposes only and are generated automatically by the server..
   */
  MONITORING;
}

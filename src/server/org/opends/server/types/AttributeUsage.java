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
package org.opends.server.types;



/**
 * This enumeration defines the set of possible attribute usage values
 * that may apply to an attribute type, as defined in RFC 2252.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum AttributeUsage
{
  /**
   * The attribute usage intended for user-defined attribute types.
   */
  USER_APPLICATIONS("userApplications", false),



  /**
   * The attribute usage intended for standard operational attributes.
   */
  DIRECTORY_OPERATION("directoryOperation", true),



  /**
   * The attribute usage intended for non-standard operational
   * attributes shared among multiple DSAs.
   */
  DISTRIBUTED_OPERATION("distributedOperation", true),



  /**
   * The attribute usage intended for non-standard operational
   * attributes used by a single DSA.
   */
  DSA_OPERATION("dSAOperation", true);



  // The string representation of this attribute usage.
  private final String usageString;

  // Flag indicating whether or not the usage should be categorized as
  // operational.
  private final boolean isOperational;



  /**
   * Creates a new attribute usage with the provided string
   * representation.
   *
   * @param usageString
   *          The string representation of this attribute usage.
   * @param isOperational
   *          <code>true</code> if attributes having this attribute
   *          usage are operational, or <code>false</code>
   *          otherwise.
   */
  private AttributeUsage(String usageString, boolean isOperational)
  {
    this.usageString = usageString;
    this.isOperational = isOperational;
  }



  /**
   * Retrieves a string representation of this attribute usage.
   *
   * @return  A string representation of this attribute usage.
   */
  public String toString()
  {
    return usageString;
  }



  /**
   * Determine whether or not attributes having this attribute usage
   * are operational.
   *
   * @return Returns <code>true</code> if attributes having this
   *         attribute usage are operational, or <code>false</code>
   *         otherwise.
   */
  public boolean isOperational() {
    return isOperational;
  }
}


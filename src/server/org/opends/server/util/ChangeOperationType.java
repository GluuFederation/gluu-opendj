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
package org.opends.server.util;



/**
 * This enumeration defines the days of the week.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum ChangeOperationType
{
  /**
   * The change type for add operations.
   */
  ADD("ADD", "add"),



  /**
   * The change type for delete operations.
   */
  DELETE("DELETE", "delete"),



  /**
   * The change type for modify operations.
   */
  MODIFY("MODIFY", "modify"),



  /**
   * The change type for modify DN operations.
   */
  MODIFY_DN("MODIFY_DN", "moddn");



  // The name of this change type as it should appear in the "changetype" field
  // in LDIF records.
  private String ldifChangeType;

  // The user-friendly name given to this change type.
  private String type;



  /**
   * Creates a change type with the given string value.
   *
   * @param  type            The string value for this change type.
   * @param  ldifChangeType  The change type as it should appear in the
   *                         "changetype" field in LDIF records.
   */
  private ChangeOperationType(String type, String ldifChangeType)
  {
    this.type           = type;
    this.ldifChangeType = ldifChangeType;
  }


  /**
   * Retrieves the human-readable name this change type.
   *
   * @return  The human-readable name for this change type.
   */
  public String getType()
  {
    return type;
  }


  /**
   * Retrieves the name of the change type as it should appear in LDIF
   * "changetype" records.
   *
   * @return  The name of the change type as it should appear in LDIF
   *          "changetype" records.
   */
  public String getLDIFChangeType()
  {
    return ldifChangeType;
  }


  /**
   * Retrieves a string representation of this type.
   *
   * @return  A string representation of this type.
   */
  public String toString()
  {
    return type;
  }
}


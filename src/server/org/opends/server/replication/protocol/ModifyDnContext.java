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
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import org.opends.server.replication.common.ChangeNumber;

/**
 * This class describe the replication context that is attached to
 * ModifyDN operation.
 */
public class ModifyDnContext extends OperationContext
{
  private String newSuperiorEntryUUID;

  /**
   * Creates a new ModifyDN Context with the provided parameters.
   *
   * @param changeNumber The change number of the operation.
   * @param entryUUID the unique Id of the modified entry.
   * @param newSuperiorEntryUUID The unique Identifier of the new parent,
   *                    can be null if the entry is to stay below the same
   *                    parent.
   */
  public ModifyDnContext(ChangeNumber changeNumber, String entryUUID,
                         String newSuperiorEntryUUID)
  {
    super(changeNumber, entryUUID);
    this.newSuperiorEntryUUID = newSuperiorEntryUUID;
  }

  /**
   * Get the unique Identifier of the new parent.
   * Can be null if the entry is to stay below the same parent.
   *
   * @return Returns the unique Identifier of the new parent..
   */
  public String getNewSuperiorEntryUUID()
  {
    return newSuperiorEntryUUID;
  }
}

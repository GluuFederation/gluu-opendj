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
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplicationMsg;

/**
 *
 * This class if used to build pseudo DEL Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 *
 */
public class FakeDelOperation extends FakeOperation
{
  final private String dn;
  private final String entryUUID;

  /**
   * Creates a new FakeDelOperation from the provided information.
   *
   * @param dn             The dn of the entry that was deleted.
   * @param changeNumber   The ChangeNumber of the operation.
   * @param entryUUID      The Unique ID of the deleted entry.
   */
  public FakeDelOperation(String dn, ChangeNumber changeNumber,
      String entryUUID)
  {
    super(changeNumber);
    this.dn = dn;
    this.entryUUID = entryUUID;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public ReplicationMsg generateMessage()
  {
    return new DeleteMsg(dn, this.getChangeNumber(), entryUUID);
  }

  /**
   * Retrieves the Unique ID of the entry that was deleted with this operation.
   *
   * @return  The Unique ID of the entry that was deleted with this operation.
   */
  public String getEntryUUID()
  {
    return entryUUID;
  }
}

/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DN;

/**
 * Multi domain DB cursor that only returns updates for the domains which have
 * been enabled for the external changelog.
 */
public final class ECLMultiDomainDBCursor implements DBCursor<UpdateMsg>
{
  private final ECLEnabledDomainPredicate predicate;
  private final MultiDomainDBCursor cursor;

  /**
   * Builds an instance of this class filtering updates from the provided cursor.
   *
   * @param predicate
   *          tells whether a domain is enabled for the external changelog
   * @param cursor
   *          the cursor whose updates will be filtered
   */
  public ECLMultiDomainDBCursor(ECLEnabledDomainPredicate predicate, MultiDomainDBCursor cursor)
  {
    this.predicate = predicate;
    this.cursor = cursor;
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return cursor.getRecord();
  }

  /**
   * Returns the data associated to the cursor that returned the current record.
   *
   * @return the data associated to the cursor that returned the current record.
   */
  public DN getData()
  {
    return cursor.getData();
  }

  /**
   * Removes a replication domain from this cursor and stops iterating over it.
   * Removed cursors will be effectively removed on the next call to
   * {@link #next()}.
   *
   * @param baseDN
   *          the replication domain's baseDN
   */
  public void removeDomain(DN baseDN)
  {
    cursor.removeDomain(baseDN);
  }

  @Override
  public boolean next() throws ChangelogException
  {
    if (!cursor.next())
    {
      return false;
    }
    // discard updates from non ECL enabled domains by removing the disabled domains from the cursor
    DN domain = cursor.getData();
    while (domain != null && !predicate.isECLEnabledDomain(domain))
    {
      cursor.removeDomain(domain);
      domain = cursor.getData();
    }
    return domain != null;
  }

  @Override
  public void close()
  {
    cursor.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " cursor=[" + cursor + ']';
  }
}

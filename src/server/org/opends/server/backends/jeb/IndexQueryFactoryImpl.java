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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.server.backends.jeb;



import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opends.messages.Message;
import org.opends.server.api.IndexQueryFactory;
import org.opends.server.types.ByteSequence;

import static org.opends.messages.JebMessages.
    INFO_JEB_INDEX_FILTER_INDEX_LIMIT_EXCEEDED;
import static org.opends.messages.JebMessages.
    INFO_JEB_INDEX_FILTER_INDEX_NOT_TRUSTED;
import static org.opends.messages.JebMessages.
    INFO_JEB_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS;


/**
 * This class is an implementation of IndexQueryFactory which creates
 * IndexQuery objects as part of the query of the JEB index.
 */
public final class IndexQueryFactoryImpl implements
    IndexQueryFactory<IndexQuery>
{
  /**
   * The Map containing the string type identifier and the corresponding
   * index.
   */
  private Map<String, Index> indexMap;



  /**
   * Creates a new IndexQueryFactoryImpl object.
   *
   * @param indexMap
   *          A map containing the index id and the corresponding index.
   */
  public IndexQueryFactoryImpl(Map<String, Index> indexMap)
  {
    this.indexMap = indexMap;
  }



  /**
   * {@inheritDoc}
   */
  public IndexQuery createExactMatchQuery(final String indexID,
      final ByteSequence value)
  {
    return new IndexQuery()
      {

        @Override
        public EntryIDSet evaluate(List<Message> debugMessages)
        {
          // Read the database and get Record for the key.
          DatabaseEntry key = new DatabaseEntry(value.toByteArray());

          // Select the right index to be used.
          Index index = indexMap.get(indexID);
          EntryIDSet entrySet =
              index.readKey(key, null, LockMode.DEFAULT);
          if(debugMessages != null && !entrySet.isDefined())
          {
            if(!index.isTrusted())
            {
              debugMessages.add(
                  INFO_JEB_INDEX_FILTER_INDEX_NOT_TRUSTED.get(
                      index.getName()));
            }
            else if(index.isRebuildRunning())
            {
              debugMessages.add(
                  INFO_JEB_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS.get(
                      index.getName()));
            }
            else
            {
              debugMessages.add(
                  INFO_JEB_INDEX_FILTER_INDEX_LIMIT_EXCEEDED.get(
                      index.getName()));
            }
          }
          return entrySet;
        }
      };
  }



  /**
   * {@inheritDoc}
   */
  public IndexQuery createRangeMatchQuery(final String indexID,
      final ByteSequence lowerBound, final ByteSequence upperBound,
      final boolean includeLowerBound, final boolean includeUpperBound)
  {
    return new IndexQuery()
      {

        @Override
        public EntryIDSet evaluate(List<Message> debugMessages)
        {
          // Find the right index.
          Index index = indexMap.get(indexID);
          EntryIDSet entrySet =
              index.readRange(lowerBound.toByteArray(), upperBound
                  .toByteArray(), includeLowerBound, includeUpperBound);
          if(debugMessages != null && !entrySet.isDefined())
          {
            if(!index.isTrusted())
            {
              debugMessages.add(
                  INFO_JEB_INDEX_FILTER_INDEX_NOT_TRUSTED.get(
                      index.getName()));
            }
            else if(index.isRebuildRunning())
            {
              debugMessages.add(
                  INFO_JEB_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS.get(
                      index.getName()));
            }
            else
            {
              debugMessages.add(
                  INFO_JEB_INDEX_FILTER_INDEX_LIMIT_EXCEEDED.get(
                      index.getName()));
            }
          }
          return entrySet;
        }
      };
  }



  /**
   * {@inheritDoc}
   */
  public IndexQuery createIntersectionQuery(
      Collection<IndexQuery> subqueries)
  {
    return IndexQuery.createIntersectionIndexQuery(subqueries);
  }



  /**
   * {@inheritDoc}
   */
  public IndexQuery createUnionQuery(Collection<IndexQuery> subqueries)
  {
    return IndexQuery.createUnionIndexQuery(subqueries);
  }



  /**
   * {@inheritDoc}
   * <p>
   * It returns an empty EntryIDSet object when either all or no record
   * sets are requested.
   */
  public IndexQuery createMatchAllQuery()
  {
    return new IndexQuery()
      {

        @Override
        public EntryIDSet evaluate(List<Message> debugMessages)
        {
          return new EntryIDSet();
        }
      };
  }
}

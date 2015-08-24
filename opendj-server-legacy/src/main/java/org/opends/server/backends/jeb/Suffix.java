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
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static org.opends.messages.BackendMessages.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.backends.jeb.Importer.DNCache;
import org.opends.server.types.DN;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;

/**
 * The class represents a suffix that is to be loaded during an import, or
 * rebuild index process. Multiple instances of this class can be instantiated
 * during and import to support multiple suffixes in a backend. A rebuild
 * index has only one of these instances.
 */
class Suffix
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final List<DN> includeBranches, excludeBranches;
  private final DN baseDN;
  private final EntryContainer srcEntryContainer;
  private final EntryContainer entryContainer;
  private final Object synchObject = new Object();
  private static final int PARENT_ID_SET_SIZE = 16 * 1024;
  private final ConcurrentHashMap<DN, CountDownLatch> pendingMap = new ConcurrentHashMap<>();
  private final Set<DN> parentSet = new HashSet<>(PARENT_ID_SET_SIZE);
  private final List<AttributeIndex> attributeIndexes = new ArrayList<>();
  private final List<VLVIndex> vlvIndexes = new ArrayList<>();
  private boolean processID2Children, processID2Subtree;

  /**
   * Creates a suffix instance using the specified parameters.
   *
   * @param entryContainer The entry container pertaining to the suffix.
   * @param srcEntryContainer The original entry container.
   * @param includeBranches The include branches.
   * @param excludeBranches The exclude branches.
   */
  Suffix(EntryContainer entryContainer, EntryContainer srcEntryContainer,
         List<DN> includeBranches, List<DN> excludeBranches)
  {
    this.entryContainer = entryContainer;
    this.srcEntryContainer = srcEntryContainer;
    this.baseDN = entryContainer.getBaseDN();
    if (includeBranches != null)
    {
      this.includeBranches = includeBranches;
    }
    else
    {
      this.includeBranches = new ArrayList<>(0);
    }
    if (excludeBranches != null)
    {
      this.excludeBranches = excludeBranches;
    }
    else
    {
      this.excludeBranches = new ArrayList<>(0);
    }
  }

  /**
   * Returns the DN2ID instance pertaining to a suffix instance.
   *
   * @return A DN2ID instance that can be used to manipulate the DN2ID database.
   */
  public DN2ID getDN2ID()
  {
    return entryContainer.getDN2ID();
  }


  /**
   * Returns the ID2Entry instance pertaining to a suffix instance.
   *
   * @return A ID2Entry instance that can be used to manipulate the ID2Entry
   *       database.
   */
  public ID2Entry getID2Entry()
  {
    return entryContainer.getID2Entry();
  }


  /**
   * Returns the DN2URI instance pertaining to a suffix instance.
   *
   * @return A DN2URI instance that can be used to manipulate the DN2URI
   *        database.
   */
  public DN2URI getDN2URI()
  {
    return entryContainer.getDN2URI();
  }


  /**
   * Returns the entry container pertaining to a suffix instance.
   *
   * @return The entry container used to create a suffix instance.
   */
  public EntryContainer getEntryContainer()
  {
    return entryContainer;
  }


  /**
   * Returns a map associating the attribute types with their corresponding attribute indexes.
   * The map contains only trusted indexes.
   *
   * @return a map associating the attribute types with their corresponding trusted attribute indexes.
   */
  public List<AttributeIndex> getAttributeIndexes()
  {
    return attributeIndexes;
  }

  /**
   * Returns the list of trusted VLV indexes.
   *
   * @return the list of trusted VLV indexes.
   */
  public List<VLVIndex> getVLVIndexes()
  {
    return vlvIndexes;
  }

  /**
   * Make sure the specified parent DN is not in the pending map.
   *
   * @param parentDN The DN of the parent.
   */
  private void assureNotPending(DN parentDN)  throws InterruptedException
  {
    final CountDownLatch l = pendingMap.get(parentDN);
    if (l != null)
    {
      l.await();
    }
  }


  /**
   * Add specified DN to the pending map.
   *
   * @param dn The DN to add to the map.
   */
  public void addPending(DN dn)
  {
    pendingMap.putIfAbsent(dn, new CountDownLatch(1));
  }


  /**
   * Remove the specified DN from the pending map, it may not exist if the
   * entries are being migrated so just return.
   *
   * @param dn The DN to remove from the map.
   */
  public void removePending(DN dn)
  {
    CountDownLatch l = pendingMap.remove(dn);
    if(l != null)
    {
      l.countDown();
    }
  }


  /**
   * Return {@code true} if the specified dn is contained in the parent set, or
   * in the specified DN cache. This would indicate that the parent has already
   * been processed. It returns {@code false} otherwise.
   *
   * It will optionally check the dn2id database for the dn if the specified
   * cleared backend boolean is {@code true}.
   *
   * @param dn The DN to check for.
   * @param dnCache The importer DN cache.
   * @param clearedBackend Set to {@code true} if the import process cleared the
   *                       backend before processing.
   * @return {@code true} if the dn is contained in the parent ID, or
   *         {@code false} otherwise.
   *
   * @throws DatabaseException If an error occurred searching the DN cache, or
   *                           dn2id database.
   * @throws InterruptedException If an error occurred processing the pending
   *                              map.
   */
  public
  boolean isParentProcessed(DN dn, DNCache dnCache, boolean clearedBackend)
                            throws DatabaseException, InterruptedException {
    synchronized(synchObject) {
      if(parentSet.contains(dn))
      {
        return true;
      }
    }
    //The DN was not in the parent set. Make sure it isn't pending.
    try {
      assureNotPending(dn);
    } catch (InterruptedException e) {
      logger.error(ERR_IMPORT_LDIF_PENDING_ERR, e.getMessage());
      throw e;
    }
    // Either parent is in the DN cache,
    // or else check the dn2id database for the DN (only if backend wasn't cleared)
    final boolean parentThere = dnCache.contains(dn)
        || (!clearedBackend
            && getDN2ID().get(null, dn, LockMode.DEFAULT) != null);
    //Add the DN to the parent set if needed.
    if (parentThere) {
      synchronized(synchObject) {
        if (parentSet.size() >= PARENT_ID_SET_SIZE) {
          Iterator<DN> iterator = parentSet.iterator();
          iterator.next();
          iterator.remove();
        }
        parentSet.add(dn);
      }
    }
    return parentThere;
  }


  final boolean isProcessID2Children()
  {
    return processID2Children;
  }

  final boolean isProcessID2Subtree()
  {
    return processID2Subtree;
  }

  /**
   * Sets the trusted status of all of the indexes, vlvIndexes, id2children
   * and id2subtree indexes.
   *
   * @throws DatabaseException If an error occurred setting the indexes to
   *                           trusted.
   */
  public void setIndexesTrusted() throws DatabaseException
  {
    if (processID2Children)
    {
      entryContainer.getID2Children().setTrusted(null, true);
    }
    if (processID2Subtree)
    {
      entryContainer.getID2Subtree().setTrusted(null, true);
    }
    for (AttributeIndex attrIndex : attributeIndexes)
    {
      setTrusted(attrIndex, true);
    }
    for (VLVIndex vlvIdx : vlvIndexes)
    {
      vlvIdx.setTrusted(null, true);
    }
  }

  /**
   * Build the lists of indexes to process and set their status as not trusted.
   * ID2Children and ID2Subtree are also considered, albeit as special cases.
   *
   * @param onlyDegraded
   *           true if currently untrusted indexes should be processed as well.
   * @throws DatabaseException
   *           If an error occurred setting the indexes to trusted.
   */
  public void setIndexesNotTrusted(boolean onlyDegraded) throws DatabaseException
  {
    setNotTrustedDN2IDRelatedIndexes(onlyDegraded);
    for (AttributeIndex attributeIndex : entryContainer.getAttributeIndexes())
    {
      if (!onlyDegraded || attributeIndex.isTrusted())
      {
        attributeIndexes.add(attributeIndex);
        setTrusted(attributeIndex, false);
      }
    }
    for (VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if (!onlyDegraded || vlvIndex.isTrusted())
      {
        vlvIndex.setTrusted(null, false);
        vlvIndexes.add(vlvIndex);
      }
    }
  }

  private void setNotTrustedDN2IDRelatedIndexes(boolean onlyDegraded)
  {
    if (setNotTrustedDN2IDRelated(entryContainer.getID2Children(), onlyDegraded))
    {
      processID2Children = true;
    }
    if (setNotTrustedDN2IDRelated(entryContainer.getID2Subtree(), onlyDegraded))
    {
      processID2Subtree = true;
    }
  }

  private boolean setNotTrustedDN2IDRelated(Index auxIndex, boolean onlyDegraded)
  {
    if (!onlyDegraded || auxIndex.isTrusted())
    {
      auxIndex.setTrusted(null, false);
      return true;
    }
    return false;
  }

  private void setTrusted(AttributeIndex attrIndex, boolean trusted)
  {
    for (Index index : attrIndex.getAllIndexes())
    {
      if (index != null)
      {
        index.setTrusted(null, trusted);
      }
    }
  }

  void forceTrustedDN2IDRelated(boolean trusted)
  {
    entryContainer.getID2Children().setTrusted(null, trusted);
    entryContainer.getID2Subtree().setTrusted(null, trusted);
    if (!trusted)
    {
      processID2Subtree = true;
      processID2Children = true;
    }
  }

  /**
   * Return a src entry container.
   *
   * @return  The src entry container.
   */
  public EntryContainer getSrcEntryContainer()
  {
    return this.srcEntryContainer;
  }


  /**
   * Return include branches.
   *
   * @return The include branches.
   */
  public List<DN> getIncludeBranches()
  {
    return this.includeBranches;
  }


  /**
   * Return exclude branches.
   *
   * @return the exclude branches.
   */
  public List<DN> getExcludeBranches()
  {
    return this.excludeBranches;
  }


  /**
   * Return base DN.
   *
   * @return The base DN.
   */
  public DN getBaseDN()
  {
    return this.baseDN;
  }
}

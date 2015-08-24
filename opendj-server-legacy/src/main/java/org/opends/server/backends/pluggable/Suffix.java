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
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.OnDiskMergeBufferImporter.DNCache;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.DN;

/**
 * The class represents a suffix that is to be loaded during an import, or
 * rebuild index process. Multiple instances of this class can be instantiated
 * during and import to support multiple suffixes in a backend. A rebuild
 * index has only one of these instances.
 */
@SuppressWarnings("javadoc")
final class Suffix
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final List<DN> includeBranches, excludeBranches;
  private final DN baseDN;
 /**
  * If not null this is the original entry container for the suffix in the backend before the
  * import begins.
  * <p>
  * Then its data is completely deleted only at the very end of the import when calling
  * *Importer.switchEntryContainers().
  */
  private final EntryContainer srcEntryContainer;
  /**
   * If {@link #srcEntryContainer} is null, it is the original entry container for the suffix in the
   * backend. Otherwise it is a temporary entry container that will eventually replace the existing
   * {@link #srcEntryContainer} at the end of the import.
   */
  private final EntryContainer entryContainer;
  private final Object synchObject = new Object();
  private static final int PARENT_ID_SET_SIZE = 16 * 1024;
  private final ConcurrentHashMap<DN, CountDownLatch> pendingMap = new ConcurrentHashMap<>();
  private final Set<DN> parentSet = new HashSet<>(PARENT_ID_SET_SIZE);
  private final List<AttributeIndex> attributeIndexes = new ArrayList<>();
  private final List<VLVIndex> vlvIndexes = new ArrayList<>();

  Suffix(EntryContainer entryContainer)
  {
    this(entryContainer, null, null, null);
  }

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
    this.baseDN = srcEntryContainer != null ? srcEntryContainer.getBaseDN() : entryContainer.getBaseDN();
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
   * @return A DN2ID instance that can be used to manipulate the DN2ID tree.
   */
  DN2ID getDN2ID()
  {
    return entryContainer.getDN2ID();
  }

  /**
   * Returns the ID2Entry instance pertaining to a suffix instance.
   *
   * @return A ID2Entry instance that can be used to manipulate the ID2Entry tree.
   */
  ID2Entry getID2Entry()
  {
    return entryContainer.getID2Entry();
  }

  /**
   * Returns the DN2URI instance pertaining to a suffix instance.
   *
   * @return A DN2URI instance that can be used to manipulate the DN2URI tree.
   */
  DN2URI getDN2URI()
  {
    return entryContainer.getDN2URI();
  }

  /**
   * Returns the entry container pertaining to a suffix instance.
   *
   * @return The entry container used to create a suffix instance.
   */
  EntryContainer getEntryContainer()
  {
    return entryContainer;
  }

  /**
   * Return the Attribute Type - Index map used to map an attribute type to an
   * index instance.
   *
   * @return A suffixes Attribute Type - Index map.
   */
  List<AttributeIndex> getAttributeIndexes()
  {
    return attributeIndexes;
  }

  List<VLVIndex> getVLVIndexes()
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
  void addPending(DN dn)
  {
    pendingMap.putIfAbsent(dn, new CountDownLatch(1));
  }

  /**
   * Remove the specified DN from the pending map, it may not exist if the
   * entries are being migrated so just return.
   *
   * @param dn The DN to remove from the map.
   */
  void removePending(DN dn)
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
   * It will optionally check the dn2id tree for the dn if the specified
   * cleared backend boolean is {@code true}.
   *
   * @param dn The DN to check for.
   * @param dnCache The importer DN cache.
   * @return {@code true} if the dn is contained in the parent ID, or {@code false} otherwise.
   * @throws StorageRuntimeException If an error occurred searching the DN cache, or dn2id tree.
   * @throws InterruptedException If an error occurred processing the pending map
   */
  boolean isParentProcessed(DN dn, DNCache dnCache) throws StorageRuntimeException, InterruptedException
  {
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
    // or else check the dn2id tree for the DN (only if backend wasn't cleared)
    final boolean parentThere = dnCache.contains(dn);
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

  /**
   * Builds the lists of Attribute and VLV indexes to process, setting their status as not trusted.
   *
   * @param txn
   *          a non null transaction
   * @param OnlyCurrentlyTrusted
   *          true if currently untrusted indexes should be processed as well.
   * @throws StorageRuntimeException
   *          If an error occurred setting the indexes to trusted.
   */
  void setIndexesNotTrusted(WriteableTransaction txn, boolean onlyCurrentlyTrusted) throws StorageRuntimeException
  {
    for (AttributeIndex attrIndex : entryContainer.getAttributeIndexes())
    {
      if (!onlyCurrentlyTrusted || attrIndex.isTrusted())
      {
        setTrusted(txn, attrIndex.getNameToIndexes().values(), false);
        attributeIndexes.add(attrIndex);
      }
    }
    for (VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if (!onlyCurrentlyTrusted || vlvIndex.isTrusted())
      {
        vlvIndex.setTrusted(txn, false);
        vlvIndexes.add(vlvIndex);
      }
    }
  }

  void setIndexesTrusted(WriteableTransaction txn) throws StorageRuntimeException
  {
    for (AttributeIndex attrIndex : attributeIndexes)
    {
      setTrusted(txn, attrIndex.getNameToIndexes().values(), true);
    }
    for (VLVIndex vlvIdx : vlvIndexes)
    {
      vlvIdx.setTrusted(txn, true);
    }
  }

  private void setTrusted(WriteableTransaction txn, Collection<MatchingRuleIndex> indexes, boolean trusted)
  {
    if (indexes != null)
    {
      for (Index index : indexes)
      {
        index.setTrusted(txn, trusted);
      }
    }
  }

  /**
   * Return a src entry container.
   *
   * @return  The src entry container.
   */
  EntryContainer getSrcEntryContainer()
  {
    return this.srcEntryContainer;
  }

  /**
   * Return include branches.
   *
   * @return The include branches.
   */
  List<DN> getIncludeBranches()
  {
    return this.includeBranches;
  }

  /**
   * Return exclude branches.
   *
   * @return the exclude branches.
   */
  List<DN> getExcludeBranches()
  {
    return this.excludeBranches;
  }

  /**
   * Return base DN.
   *
   * @return The base DN.
   */
  DN getBaseDN()
  {
    return this.baseDN;
  }
}

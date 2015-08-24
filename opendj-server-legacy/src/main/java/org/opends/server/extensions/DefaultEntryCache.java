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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.EntryCache;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

/**
 * This class defines the default entry cache which acts as an arbiter for
 * every entry cache implementation configured and installed within the
 * Directory Server or acts an an empty cache if no implementation specific
 * entry cache is configured.  It does not actually store any entries, so
 * all calls to the entry cache public API are routed to underlying entry
 * cache according to the current configuration order and preferences.
 */
public class DefaultEntryCache
       extends EntryCache<EntryCacheCfg>
       implements ConfigurationChangeListener<EntryCacheCfg>,
       BackendInitializationListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * The entry cache order array reflects all currently configured and
   * active entry cache implementations in cache level specific order.
   */
  private static EntryCache<? extends EntryCacheCfg>[] cacheOrder =
    new EntryCache<?>[0];


  /**
   * Creates a new instance of this default entry cache.
   */
  public DefaultEntryCache()
  {
    super();

    // Register with backend initialization listener to clear cache
    // entries belonging to given backend that about to go offline.
    DirectoryServer.registerBackendInitializationListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeEntryCache(EntryCacheCfg configEntry)
         throws ConfigException, InitializationException
  {
    // No implementation required.
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeEntryCache()
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      entryCache.finalizeEntryCache();
    }
    // ReInitialize cache order array.
    cacheOrder = new EntryCache<?>[0];
  }

  /** {@inheritDoc} */
  @Override
  public boolean containsEntry(DN entryDN)
  {
    if (entryDN == null) {
      return false;
    }

    for (EntryCache<?> entryCache : cacheOrder) {
      if (entryCache.containsEntry(entryDN)) {
        return true;
      }
    }

    return false;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(String backendID, long entryID)
  {
    for (EntryCache<? extends EntryCacheCfg> entryCache : cacheOrder)
    {
      Entry entry = entryCache.getEntry(backendID, entryID);
      if (entry != null)
      {
        return entry.duplicate(true);
      }
    }

    // Indicate global cache miss.
    if (cacheOrder.length != 0)
    {
      cacheMisses.getAndIncrement();
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN)
  {
    for (EntryCache<? extends EntryCacheCfg> entryCache : cacheOrder)
    {
      Entry entry = entryCache.getEntry(entryDN);
      if (entry != null)
      {
        return entry.duplicate(true);
      }
    }

    // Indicate global cache miss.
    if (cacheOrder.length != 0)
    {
      cacheMisses.getAndIncrement();
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryID(DN entryDN)
  {
    for (EntryCache<?> entryCache : cacheOrder)
    {
      long entryID = entryCache.getEntryID(entryDN);
      if (entryID != -1)
      {
        return entryID;
      }
    }
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public DN getEntryDN(String backendID, long entryID)
  {
    for (EntryCache<?> entryCache : cacheOrder)
    {
      DN entryDN = entryCache.getEntryDN(backendID, entryID);
      if (entryDN != null)
      {
        return entryDN;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void putEntry(Entry entry, String backendID, long entryID)
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      // The first cache in the order which can take this entry
      // gets it.
      if (entryCache.filtersAllowCaching(entry)) {
        entryCache.putEntry(entry.duplicate(false), backendID, entryID);
        break;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean putEntryIfAbsent(Entry entry, String backendID, long entryID)
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      // The first cache in the order which can take this entry
      // gets it.
      if (entryCache.filtersAllowCaching(entry)) {
        return entryCache.putEntryIfAbsent(entry.duplicate(false),
                backendID, entryID);
      }
    }

    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void removeEntry(DN entryDN)
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      if (entryCache.containsEntry(entryDN)) {
        entryCache.removeEntry(entryDN);
        break;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void clear()
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      entryCache.clear();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void clearBackend(String backendID)
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      entryCache.clearBackend(backendID);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void clearSubtree(DN baseDN)
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      entryCache.clearSubtree(baseDN);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void handleLowMemory()
  {
    for (EntryCache<?> entryCache : cacheOrder) {
      entryCache.handleLowMemory();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      EntryCacheCfg configuration,
      List<LocalizableMessage> unacceptableReasons
      )
  {
    // No implementation required.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(EntryCacheCfg configuration)
  {
    // No implementation required.
    return new ConfigChangeResult();
  }

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // The sum of cache hits of all active entry cache implementations.
    long entryCacheHits = 0;
    // Common for all active entry cache implementations.
    long entryCacheMisses = cacheMisses.longValue();
    // The sum of cache counts of all active entry cache implementations.
    long currentEntryCacheCount = 0;

    for (EntryCache<?> entryCache : cacheOrder) {
      // Get cache hits and counts from every active cache.
      entryCacheHits += entryCache.getCacheHits();
      currentEntryCacheCount += entryCache.getCacheCount();
    }

    try {
      return EntryCacheCommon.getGenericMonitorData(
        entryCacheHits,
        entryCacheMisses,
        null,
        null,
        currentEntryCacheCount,
        null
        );
    } catch (Exception e) {
      logger.traceException(e);
      return Collections.emptyList();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Long getCacheCount()
  {
    long cacheCount = 0;
    for (EntryCache<?> entryCache : cacheOrder) {
      cacheCount += entryCache.getCacheCount();
    }
    return cacheCount;
  }

  /** {@inheritDoc} */
  @Override
  public String toVerboseString()
  {
    StringBuilder sb = new StringBuilder();
    for (EntryCache<?> entryCache : cacheOrder)
    {
      String s = entryCache.toVerboseString();
      if (s != null)
      {
        sb.append(s);
      }
    }
    String verboseString = sb.toString();
    return verboseString.length() > 0 ? verboseString : null;
  }



  /**
   * Retrieves the current cache order array.
   *
   * @return  The current cache order array.
   */
  public final static EntryCache<? extends EntryCacheCfg>[] getCacheOrder()
  {
    return DefaultEntryCache.cacheOrder;
  }



  /**
   * Sets the current cache order array.
   *
   * @param  cacheOrderMap  The current cache order array.
   */
  public final static void setCacheOrder(
    SortedMap<Integer,
    EntryCache<? extends EntryCacheCfg>> cacheOrderMap)
  {
    DefaultEntryCache.cacheOrder =
      cacheOrderMap.values().toArray(new EntryCache<?>[0]);
  }



  /**
   * Performs any processing that may be required whenever a backend
   * is initialized for use in the Directory Server.  This method will
   * be invoked after the backend has been initialized but before it
   * has been put into service.
   *
   * @param  backend  The backend that has been initialized and is
   *                  about to be put into service.
   */
  @Override
  public void performBackendInitializationProcessing(Backend<?> backend)
  {
    // Do nothing.
  }



  /**
   * Performs any processing that may be required whenever a backend
   * is finalized.  This method will be invoked after the backend has
   * been taken out of service but before it has been finalized.
   *
   * @param  backend  The backend that has been taken out of service
   *                  and is about to be finalized.
   */
  @Override
  public void performBackendFinalizationProcessing(Backend<?> backend)
  {
    // Do not clear any backends if the server is shutting down.
    if (!DirectoryServer.getInstance().isShuttingDown())
    {
      clearBackend(backend.getBackendID());
    }
  }
}

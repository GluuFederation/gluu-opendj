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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Reject;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.backends.pluggable.ImportSuffixCommand.SuffixImportStrategy;
import org.opends.server.backends.pluggable.spi.AccessMode;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageInUseException;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Operation;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.LDIFException;
import org.opends.server.util.RuntimeInformation;

/**
 * This is an implementation of a Directory Server Backend which stores entries locally
 * in a pluggable storage.
 *
 * @param <C>
 *          the type of the BackendCfg for the current backend
 */
public abstract class BackendImpl<C extends PluggableBackendCfg> extends Backend<C> implements
    ConfigurationChangeListener<PluggableBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration of this backend. */
  private PluggableBackendCfg cfg;
  /** The root container to use for this backend. */
  private RootContainer rootContainer;

  // FIXME: this is broken. Replace with read-write lock.
  /** A count of the total operation threads currently in the backend. */
  private final AtomicInteger threadTotalCount = new AtomicInteger(0);
  /** The base DNs defined for this backend instance. */
  private DN[] baseDNs;

  private MonitorProvider<?> rootContainerMonitor;

  /** The underlying storage engine. */
  private Storage storage;

  /** The controls supported by this backend. */
  private static final Set<String> supportedControls = CollectionUtils.newHashSet(
      OID_SUBTREE_DELETE_CONTROL,
      OID_PAGED_RESULTS_CONTROL,
      OID_MANAGE_DSAIT_CONTROL,
      OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
      OID_VLV_REQUEST_CONTROL);

  /**
   * Begin a Backend API method that accesses the {@link EntryContainer} for <code>entryDN</code>
   * and returns it.
   * @param operation requesting the storage
   * @param entryDN the target DN for the operation
   * @return <code>EntryContainer</code> where <code>entryDN</code> resides
   */
  private EntryContainer accessBegin(Operation operation, DN entryDN) throws DirectoryException
  {
    checkRootContainerInitialized();
    rootContainer.checkForEnoughResources(operation);
    EntryContainer ec = rootContainer.getEntryContainer(entryDN);
    if (ec == null)
    {
      throw new DirectoryException(ResultCode.UNDEFINED, ERR_BACKEND_ENTRY_DOESNT_EXIST.get(entryDN, getBackendID()));
    }
    threadTotalCount.getAndIncrement();
    return ec;
  }

  /** End a Backend API method that accesses the EntryContainer. */
  private void accessEnd()
  {
    threadTotalCount.getAndDecrement();
  }

  /**
   * Wait until there are no more threads accessing the storage. It is assumed
   * that new threads have been prevented from entering the storage at the time
   * this method is called.
   */
  private void waitUntilQuiescent()
  {
    while (threadTotalCount.get() > 0)
    {
      // Still have threads accessing the storage so sleep a little
      try
      {
        Thread.sleep(500);
      }
      catch (InterruptedException e)
      {
        logger.traceException(e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void configureBackend(C cfg, ServerContext serverContext) throws ConfigException
  {
    Reject.ifNull(cfg, "cfg must not be null");

    this.cfg = cfg;
    baseDNs = this.cfg.getBaseDN().toArray(new DN[0]);
    storage = new TracedStorage(configureStorage(cfg, serverContext), cfg.getBackendId());
  }

  /** {@inheritDoc} */
  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    if (mustOpenRootContainer())
    {
      rootContainer = newRootContainer(AccessMode.READ_WRITE);
    }

    // Preload the tree cache.
    rootContainer.preload(cfg.getPreloadTimeLimit());

    try
    {
      // Log an informational message about the number of entries.
      logger.info(NOTE_BACKEND_STARTED, cfg.getBackendId(), getEntryCount());
    }
    catch (StorageRuntimeException e)
    {
      LocalizableMessage message = WARN_GET_ENTRY_COUNT_FAILED.get(e.getMessage());
      throw new InitializationException(message, e);
    }

    for (DN dn : cfg.getBaseDN())
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(dn, e), e);
      }
    }

    // Register a monitor provider for the environment.
    rootContainerMonitor = rootContainer.getMonitorProvider();
    DirectoryServer.registerMonitorProvider(rootContainerMonitor);

    // Register this backend as a change listener.
    cfg.addPluggableChangeListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public void closeBackend()
  {
    cfg.removePluggableChangeListener(this);

    // Deregister our base DNs.
    for (DN dn : rootContainer.getBaseDNs())
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    DirectoryServer.deregisterMonitorProvider(rootContainerMonitor);

    // We presume the server will prevent more operations coming into this
    // backend, but there may be existing operations already in the
    // backend. We need to wait for them to finish.
    waitUntilQuiescent();

    // Close RootContainer and Storage.
    try
    {
      rootContainer.close();
      rootContainer = null;
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
      logger.error(ERR_DATABASE_EXCEPTION, e.getMessage());
    }

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.set(0);

    // Log an informational message.
    logger.info(NOTE_BACKEND_OFFLINE, cfg.getBackendId());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    try
    {
      EntryContainer ec = rootContainer.getEntryContainer(baseDNs[0]);
      AttributeIndex ai = ec.getAttributeIndex(attributeType);
      return ai != null ? ai.isIndexed(indexType) : false;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    switch (backendOperation)
    {
    case BACKUP:
    case RESTORE:
      // Responsibility of the underlying storage.
      return storage.supportsBackupAndRestore();
    default: // INDEXING, LDIF_EXPORT, LDIF_IMPORT
      // Responsibility of this pluggable backend.
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }

  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    if (rootContainer != null)
    {
      try
      {
        return rootContainer.getEntryCount();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    EntryContainer container;
    try {
      container = accessBegin(null, entryDN);
    }
    catch (DirectoryException de)
    {
      if (de.getResultCode() == ResultCode.UNDEFINED)
      {
        return ConditionResult.UNDEFINED;
      }
      throw de;
    }

    container.sharedLock.lock();
    try
    {
      return ConditionResult.valueOf(container.hasSubordinates(entryDN));
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      container.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");

    final EntryContainer ec = accessBegin(null, baseDN);
    ec.sharedLock.lock();
    try
    {
      return ec.getNumberOfEntriesInBaseDN();
    }
    catch (Exception e)
    {
      throw new DirectoryException(getServerErrorResultCode(), LocalizableMessage.raw(e.getMessage()), e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    EntryContainer ec;

    /*
     * Only place where we need special handling. Should return -1 instead of an
     * error if the EntryContainer is null...
     */
    try {
      ec = accessBegin(null, parentDN);
    }
    catch (DirectoryException de)
    {
      if (de.getResultCode() == ResultCode.UNDEFINED)
      {
        return -1;
      }
      throw de;
    }

    ec.sharedLock.lock();
    try
    {
      return ec.getNumberOfChildren(parentDN);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean entryExists(final DN entryDN) throws DirectoryException
  {
    EntryContainer ec = accessBegin(null, entryDN);
    ec.sharedLock.lock();
    try
    {
      return ec.entryExists(entryDN);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    EntryContainer ec = accessBegin(null, entryDN);
    ec.sharedLock.lock();
    try
    {
      return ec.getEntry(entryDN);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation) throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(addOperation, entry.getName());

    ec.sharedLock.lock();
    try
    {
      ec.addEntry(entry, addOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(deleteOperation, entryDN);

    ec.sharedLock.lock();
    try
    {
      ec.deleteEntry(entryDN, deleteOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry, ModifyOperation modifyOperation)
      throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(modifyOperation, newEntry.getName());

    ec.sharedLock.lock();

    try
    {
      ec.replaceEntry(oldEntry, newEntry, modifyOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry, ModifyDNOperation modifyDNOperation)
      throws DirectoryException, CanceledOperationException
  {
    EntryContainer currentContainer = accessBegin(modifyDNOperation, currentDN);
    EntryContainer container = rootContainer.getEntryContainer(entry.getName());

    if (currentContainer != container)
    {
      accessEnd();
      // FIXME: No reason why we cannot implement a move between containers
      // since the containers share the same "container"
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, WARN_FUNCTION_NOT_SUPPORTED.get());
    }

    currentContainer.sharedLock.lock();
    try
    {
      currentContainer.renameEntry(currentDN, entry, modifyDNOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      currentContainer.sharedLock.unlock();
      accessEnd();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void search(SearchOperation searchOperation) throws DirectoryException, CanceledOperationException
  {
    EntryContainer ec = accessBegin(searchOperation, searchOperation.getBaseDN());

    ec.sharedLock.lock();

    try
    {
      ec.search(searchOperation);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      accessEnd();
    }
  }

  private void checkRootContainerInitialized() throws DirectoryException
  {
    if (rootContainer == null)
    {
      LocalizableMessage msg = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(getServerErrorResultCode(), msg);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();
    try
    {
      if (openRootContainer)
      {
        rootContainer = getReadOnlyRootContainer();
      }

      ExportJob exportJob = new ExportJob(exportConfig);
      exportJob.exportLDIF(rootContainer);
    }
    catch (IOException ioe)
    {
      throw new DirectoryException(getServerErrorResultCode(), ERR_EXPORT_IO_ERROR.get(ioe.getMessage()), ioe);
    }
    catch (StorageRuntimeException de)
    {
      throw createDirectoryException(de);
    }
    catch (ConfigException | InitializationException | LDIFException e)
    {
      throw new DirectoryException(getServerErrorResultCode(), e.getMessageObject(), e);
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  private boolean mustOpenRootContainer()
  {
    return rootContainer == null;
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    RuntimeInformation.logInfo();

    // If the rootContainer is open, the backend is initialized by something else.
    // We can't do import while the backend is online.
    if (rootContainer != null)
    {
      throw new DirectoryException(getServerErrorResultCode(), ERR_IMPORT_BACKEND_ONLINE.get());
    }
    for (DN dn : cfg.getBaseDN())
    {
      ImportSuffixCommand importCommand = new ImportSuffixCommand(dn, importConfig);
      if (importCommand.getSuffixImportStrategy() == SuffixImportStrategy.MERGE_DB_WITH_LDIF)
      {
        // fail-fast to avoid ending up in an unrecoverable state for the server
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_IMPORT_UNSUPPORTED_WITH_BRANCH.get());
      }
    }

    try
    {
      if (OnDiskMergeBufferImporter.mustClearBackend(importConfig, cfg))
      {
        try
        {
          // clear all files before opening the root container
          storage.removeStorageFiles();
        }
        catch (Exception e)
        {
          throw new DirectoryException(getServerErrorResultCode(), ERR_REMOVE_FAIL.get(e.getMessage()), e);
        }
      }

      rootContainer = newRootContainer(AccessMode.READ_WRITE);
      return getImportStrategy().importLDIF(importConfig, rootContainer, serverContext);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    catch (DirectoryException e)
    {
      throw e;
    }
    catch (OpenDsException | ConfigException e)
    {
      throw new DirectoryException(getServerErrorResultCode(), e.getMessageObject(), e);
    }
    finally
    {
      try
      {
        if (rootContainer != null)
        {
          long startTime = System.currentTimeMillis();
          rootContainer.close();
          long finishTime = System.currentTimeMillis();
          long closeTime = (finishTime - startTime) / 1000;
          logger.info(NOTE_IMPORT_LDIF_ROOTCONTAINER_CLOSE, closeTime);
          rootContainer = null;
        }

        logger.info(NOTE_IMPORT_CLOSING_DATABASE);
      }
      catch (StorageRuntimeException de)
      {
        logger.traceException(de);
      }
    }
  }

  private ImportStrategy getImportStrategy() throws DirectoryException
  {
    // TODO JNR may call new SuccessiveAddsImportStrategy() depending on configured import strategy
    return new OnDiskMergeBufferImporter.StrategyImpl(cfg);
  }

  /** {@inheritDoc} */
  @Override
  public long verifyBackend(VerifyConfig verifyConfig)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    final boolean openRootContainer = mustOpenRootContainer();
    try
    {
      if (openRootContainer)
      {
        rootContainer = getReadOnlyRootContainer();
      }
      return new VerifyJob(rootContainer, verifyConfig).verifyBackend();
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  /**
   * If a root container was opened in the calling method method as read only,
   * close it to leave the backend in the same state.
   */
  private void closeTemporaryRootContainer(boolean openRootContainer)
  {
    if (openRootContainer && rootContainer != null)
    {
      try
      {
        rootContainer.close();
        rootContainer = null;
      }
      catch (StorageRuntimeException e)
      {
        logger.traceException(e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void rebuildBackend(RebuildConfig rebuildConfig, ServerContext serverContext)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = mustOpenRootContainer();

    /*
     * If the rootContainer is open, the backend is initialized by something else.
     * We can't do any rebuild of system indexes while others are using this backend.
     */
    if (!openRootContainer && rebuildConfig.includesSystemIndex())
    {
      throw new DirectoryException(getServerErrorResultCode(), ERR_REBUILD_BACKEND_ONLINE.get());
    }

    try
    {
      if (openRootContainer)
      {
        rootContainer = newRootContainer(AccessMode.READ_WRITE);
      }
      new OnDiskMergeBufferImporter(rootContainer, rebuildConfig, cfg, serverContext).rebuildIndexes();
    }
    catch (ExecutionException execEx)
    {
      throw new DirectoryException(getServerErrorResultCode(), ERR_EXECUTION_ERROR.get(execEx.getMessage()), execEx);
    }
    catch (InterruptedException intEx)
    {
      throw new DirectoryException(getServerErrorResultCode(), ERR_INTERRUPTED_ERROR.get(intEx.getMessage()), intEx);
    }
    catch (ConfigException ce)
    {
      throw new DirectoryException(getServerErrorResultCode(), ce.getMessageObject(), ce);
    }
    catch (StorageRuntimeException e)
    {
      throw createDirectoryException(e);
    }
    catch (InitializationException e)
    {
      throw e;
    }
    finally
    {
      closeTemporaryRootContainer(openRootContainer);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    storage.createBackup(backupConfig);
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    storage.removeBackup(backupDirectory, backupID);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    storage.restoreBackup(restoreConfig);
  }

  /**
   * Creates the storage engine which will be used by this pluggable backend. Implementations should
   * create and configure a new storage engine but not open it.
   *
   * @param cfg
   *          the configuration object
   * @param serverContext
   *          this Directory Server intsance's server context
   * @return The storage engine to be used by this pluggable backend.
   * @throws ConfigException
   *           If there is an error in the configuration.
   */
  protected abstract Storage configureStorage(C cfg, ServerContext serverContext) throws ConfigException;

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(C config, List<LocalizableMessage> unacceptableReasons,
      ServerContext serverContext)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(PluggableBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(final PluggableBackendCfg newCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      if(rootContainer != null)
      {
        rootContainer.getStorage().write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            SortedSet<DN> newBaseDNs = newCfg.getBaseDN();
            DN[] newBaseDNsArray = newBaseDNs.toArray(new DN[newBaseDNs.size()]);

            // Check for changes to the base DNs.
            removeDeletedBaseDNs(newBaseDNs, txn);
            if (!createNewBaseDNs(newBaseDNsArray, ccr, txn))
            {
              return;
            }

            baseDNs = newBaseDNsArray;

            // Put the new configuration in place.
            cfg = newCfg;
          }
        });
      }
    }
    catch (Exception e)
    {
      ccr.setResultCode(getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(stackTraceToSingleLineString(e)));
    }
    return ccr;
  }

  private void removeDeletedBaseDNs(SortedSet<DN> newBaseDNs, WriteableTransaction txn) throws DirectoryException
  {
    for (DN baseDN : cfg.getBaseDN())
    {
      if (!newBaseDNs.contains(baseDN))
      {
        // The base DN was deleted.
        DirectoryServer.deregisterBaseDN(baseDN);
        EntryContainer ec = rootContainer.unregisterEntryContainer(baseDN);
        ec.close();
        ec.delete(txn);
      }
    }
  }

  private boolean createNewBaseDNs(DN[] newBaseDNsArray, ConfigChangeResult ccr, WriteableTransaction txn)
  {
    for (DN baseDN : newBaseDNsArray)
    {
      if (!rootContainer.getBaseDNs().contains(baseDN))
      {
        try
        {
          // The base DN was added.
          EntryContainer ec = rootContainer.openEntryContainer(baseDN, txn, AccessMode.READ_WRITE);
          rootContainer.registerEntryContainer(baseDN, ec);
          DirectoryServer.registerBaseDN(baseDN, this, false);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.setResultCode(getServerErrorResultCode());
          ccr.addMessage(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(baseDN, e));
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns a handle to the root container currently used by this backend.
   * The rootContainer could be NULL if the backend is not initialized.
   *
   * @return The RootContainer object currently used by this backend.
   */
  public final RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Returns a new read-only handle to the root container for this backend.
   * The caller is responsible for closing the root container after use.
   *
   * @return The read-only RootContainer object for this backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises during
   *                           initialization.
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  private final RootContainer getReadOnlyRootContainer()
      throws ConfigException, InitializationException
  {
    return newRootContainer(AccessMode.READ_ONLY);
  }

  /**
   * Creates a customized DirectoryException from the StorageRuntimeException
   * thrown by the backend.
   *
   * @param e
   *          The StorageRuntimeException to be converted.
   * @return DirectoryException created from exception.
   */
  private DirectoryException createDirectoryException(StorageRuntimeException e)
  {
    Throwable cause = e.getCause();
    if (cause instanceof OpenDsException)
    {
      return new DirectoryException(getServerErrorResultCode(), (OpenDsException) cause);
    }
    else
    {
      return new DirectoryException(getServerErrorResultCode(), LocalizableMessage.raw(e.getMessage()), e);
    }
  }

  private RootContainer newRootContainer(AccessMode accessMode)
          throws ConfigException, InitializationException {
    // Open the storage
    try {
      final RootContainer rc = new RootContainer(getBackendID(), storage, cfg);
      rc.open(accessMode);
      return rc;
    }
    catch (StorageInUseException e) {
      throw new InitializationException(ERR_VERIFY_BACKEND_ONLINE.get(), e);
    }
    catch (StorageRuntimeException e)
    {
      throw new InitializationException(ERR_OPEN_ENV_FAIL.get(e.getMessage()), e);
    }
  }

}

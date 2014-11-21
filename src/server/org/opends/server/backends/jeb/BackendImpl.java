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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import java.io.IOException;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.*;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;

import java.util.logging.Level;

import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.jeb.importLDIF.*;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.AlertGenerator;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.util.LDIFException;
import org.opends.server.util.RuntimeInformation;
import org.opends.server.util.Validator;
import static org.opends.server.util.StaticUtils.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.server.util.ServerConstants.*;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.types.DN;

/**
 * This is an implementation of a Directory Server Backend which stores entries
 * locally in a Berkeley DB JE database.
 */
public class BackendImpl
    extends Backend
    implements ConfigurationChangeListener<LocalDBBackendCfg>, AlertGenerator,
    DiskSpaceMonitorHandler
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
    * The fully-qualified name of this class.
    */
   private static final String CLASS_NAME =
        "org.opends.server.backends.jeb.BackendImpl";


  /**
   * The configuration of this JE backend.
   */
  private LocalDBBackendCfg cfg;

  /**
   * The root JE container to use for this backend.
   */
  private RootContainer rootContainer;

  /**
   * A count of the total operation threads currently in the backend.
   */
  private final AtomicInteger threadTotalCount = new AtomicInteger(0);

  /**
   * A count of the write operation threads currently in the backend.
   */
  private final AtomicInteger threadWriteCount = new AtomicInteger(0);

  /**
   * The base DNs defined for this backend instance.
   */
  private DN[] baseDNs;

  private MonitorProvider<?> rootContainerMonitor;
  private DiskSpaceMonitor diskMonitor;

  /**
   * The controls supported by this backend.
   */
  private static HashSet<String> supportedControls;

  static
  {
    // Set our supported controls.
    supportedControls = new HashSet<String>();
    supportedControls.add(OID_SUBTREE_DELETE_CONTROL);
    supportedControls.add(OID_PAGED_RESULTS_CONTROL);
    supportedControls.add(OID_MANAGE_DSAIT_CONTROL);
    supportedControls.add(OID_SERVER_SIDE_SORT_REQUEST_CONTROL);
    supportedControls.add(OID_VLV_REQUEST_CONTROL);
  }

  /**
   * The features supported by this backend.
   */
  private static HashSet<String> supportedFeatures;

  static {
    // Set our supported features.
    supportedFeatures = new HashSet<String>();

    //NYI
  }



  /**
   * Begin a Backend API method that reads the database.
   */
  private void readerBegin()
  {
    threadTotalCount.getAndIncrement();
  }



  /**
   * End a Backend API method that reads the database.
   */
  private void readerEnd()
  {
    threadTotalCount.getAndDecrement();
  }



  /**
   * Begin a Backend API method that writes the database.
   */
  private void writerBegin()
  {
    threadTotalCount.getAndIncrement();
    threadWriteCount.getAndIncrement();
  }



  /**
   * End a Backend API method that writes the database.
   */
  private void writerEnd()
  {
    threadWriteCount.getAndDecrement();
    threadTotalCount.getAndDecrement();
  }



  /**
   * Wait until there are no more threads accessing the database. It is assumed
   * that new threads have been prevented from entering the database at the time
   * this method is called.
   */
  private void waitUntilQuiescent()
  {
    while (threadTotalCount.get() > 0)
    {
      // Still have threads in the database so sleep a little
      try
      {
        Thread.sleep(500);
      }
      catch (InterruptedException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /**
   * This method will attempt to checksum the current JE db environment by
   * computing the Adler-32 checksum on the latest JE log file available.
   *
   * @return  The checksum of JE db environment or zero if checksum failed.
   */
  private long checksumDbEnv() {

    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    File backendDirectory = new File(parentDirectory, cfg.getBackendId());

    List<File> jdbFiles = new ArrayList<File>();
    if(backendDirectory.isDirectory())
    {
      jdbFiles =
          Arrays.asList(backendDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return name.endsWith(".jdb");
            }
          }));
    }

    if ( !jdbFiles.isEmpty() ) {
      Collections.sort(jdbFiles, Collections.reverseOrder());
      FileInputStream fis = null;
      try {
        fis = new FileInputStream(jdbFiles.get(0).toString());
        CheckedInputStream cis = new CheckedInputStream(fis, new Adler32());
        byte[] tempBuf = new byte[8192];
        while (cis.read(tempBuf) >= 0) {
        }

        return cis.getChecksum().getValue();
      } catch (Exception e) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      } finally {
        if (fis != null) {
          try {
            fis.close();
          } catch (Exception e) {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }

    return 0;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void configureBackend(Configuration cfg)
      throws ConfigException
  {
    Validator.ensureNotNull(cfg);
    Validator.ensureTrue(cfg instanceof LocalDBBackendCfg);

    this.cfg = (LocalDBBackendCfg)cfg;

    Set<DN> dnSet = this.cfg.getBaseDN();
    baseDNs = new DN[dnSet.size()];
    dnSet.toArray(baseDNs);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeBackend()
      throws ConfigException, InitializationException
  {
    // Checksum this db environment and register its offline state id/checksum.
    DirectoryServer.registerOfflineBackendStateID(this.getBackendID(),
                                                  checksumDbEnv());

    if(rootContainer == null)
    {
      EnvironmentConfig envConfig =
          ConfigurableEnvironment.parseConfigEntry(cfg);
      rootContainer = initializeRootContainer(envConfig);
    }

    // Preload the database cache.
    rootContainer.preload(cfg.getPreloadTimeLimit());

    try
    {
      // Log an informational message about the number of entries.
      Message message = NOTE_JEB_BACKEND_STARTED.get(
          cfg.getBackendId(), rootContainer.getEntryCount());
      logError(message);
    }
    catch(DatabaseException databaseException)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, databaseException);
      }
      Message message =
          WARN_JEB_GET_ENTRY_COUNT_FAILED.get(databaseException.getMessage());
      throw new InitializationException(
                                        message, databaseException);
    }

    for (DN dn : cfg.getBaseDN())
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            String.valueOf(dn), String.valueOf(e));
        throw new InitializationException(message, e);
      }
    }

    // Register a monitor provider for the environment.
    rootContainerMonitor = rootContainer.getMonitorProvider();
    DirectoryServer.registerMonitorProvider(rootContainerMonitor);

    // Register as disk space monitor handler
    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    File backendDirectory =
        new File(parentDirectory, cfg.getBackendId());
    diskMonitor = new DiskSpaceMonitor(getBackendID() + " backend",
        backendDirectory, cfg.getDiskLowThreshold(), cfg.getDiskFullThreshold(),
        5, TimeUnit.SECONDS, this);
    diskMonitor.initializeMonitorProvider(null);
    DirectoryServer.registerMonitorProvider(diskMonitor);

    //Register as an AlertGenerator.
    DirectoryServer.registerAlertGenerator(this);
    // Register this backend as a change listener.
    cfg.addLocalDBChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeBackend()
  {
    // Deregister as a change listener.
    cfg.removeLocalDBChangeListener(this);

    // Deregister our base DNs.
    for (DN dn : rootContainer.getBaseDNs())
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    DirectoryServer.deregisterMonitorProvider(rootContainerMonitor);
    DirectoryServer.deregisterMonitorProvider(diskMonitor);

    // We presume the server will prevent more operations coming into this
    // backend, but there may be existing operations already in the
    // backend. We need to wait for them to finish.
    waitUntilQuiescent();

    // Close the database.
    try
    {
      rootContainer.close();
      rootContainer = null;
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_JEB_DATABASE_EXCEPTION.get(e.getMessage());
      logError(message);
    }

    // Checksum this db environment and register its offline state id/checksum.
    DirectoryServer.registerOfflineBackendStateID(this.getBackendID(),
      checksumDbEnv());

    //Deregister the alert generator.
    DirectoryServer.deregisterAlertGenerator(this);

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.set(0);
    threadWriteCount.set(0);

    // Log an informational message.
    Message message = NOTE_BACKEND_OFFLINE.get(cfg.getBackendId());
    logError(message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    try
    {
      EntryContainer ec = rootContainer.getEntryContainer(baseDNs[0]);
      AttributeIndex ai = ec.getAttributeIndex(attributeType);
      if (ai == null)
      {
        return false;
      }

      Set<LocalDBIndexCfgDefn.IndexType> indexTypes =
           ai.getConfiguration().getIndexType();
      switch (indexType)
      {
        case PRESENCE:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.PRESENCE);

        case EQUALITY:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.EQUALITY);

        case SUBSTRING:
        case SUBINITIAL:
        case SUBANY:
        case SUBFINAL:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING);

        case GREATER_OR_EQUAL:
        case LESS_OR_EQUAL:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.ORDERING);

        case APPROXIMATE:
          return indexTypes.contains(LocalDBIndexCfgDefn.IndexType.APPROXIMATE);

        default:
          return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFExport()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    return -1;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    long ret = numSubordinates(entryDN, false);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    else if(ret == 0)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.TRUE;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    if(ec == null)
    {
      return -1;
    }

    readerBegin();
    ec.sharedLock.lock();
    try
    {
      long count = ec.getNumSubordinates(entryDN, subtree);
      if(count == Long.MAX_VALUE)
      {
        // The index entry limit has exceeded and there is no count maintained.
        return -1;
      }
      return count;
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    readerBegin();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    Entry entry;
    try
    {
      entry = ec.getEntry(entryDN);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }

    return entry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(addOperation);
    writerBegin();
    DN entryDN = entry.getDN();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    try
    {
      ec.addEntry(entry, addOperation);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(deleteOperation);
    writerBegin();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    try
    {
      ec.deleteEntry(entryDN, deleteOperation);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException,
      CanceledOperationException
  {
    checkDiskSpace(modifyOperation);
    writerBegin();

    DN entryDN = newEntry.getDN();
    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();

    try
    {
      ec.replaceEntry(oldEntry, newEntry, modifyOperation);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
      throws DirectoryException, CanceledOperationException
  {
    checkDiskSpace(modifyDNOperation);
    writerBegin();

    EntryContainer currentContainer;
    if (rootContainer != null)
    {
      currentContainer = rootContainer.getEntryContainer(currentDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    EntryContainer container = rootContainer.getEntryContainer(entry.getDN());

    if (currentContainer != container)
    {
      // FIXME: No reason why we cannot implement a move between containers
      // since the containers share the same database environment.
      Message msg = WARN_JEB_FUNCTION_NOT_SUPPORTED.get();
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                   msg);
    }

    currentContainer.sharedLock.lock();
    try
    {
      currentContainer.renameEntry(currentDN, entry, modifyDNOperation);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      currentContainer.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void search(SearchOperation searchOperation)
      throws DirectoryException, CanceledOperationException
  {
    readerBegin();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(searchOperation.getBaseDN());
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }
    ec.sharedLock.lock();

    try
    {
      ec.search(searchOperation);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = rootContainer == null;

    try
    {
      if(openRootContainer)
      {
        EnvironmentConfig envConfig =
            ConfigurableEnvironment.parseConfigEntry(cfg);

        envConfig.setReadOnly(true);
        envConfig.setAllowCreate(false);
        envConfig.setTransactional(false);
        envConfig.setConfigParam("je.env.isLocking", "true");
        envConfig.setConfigParam("je.env.runCheckpointer", "true");

        rootContainer = initializeRootContainer(envConfig);
      }


      ExportJob exportJob = new ExportJob(exportConfig);
      exportJob.exportLDIF(rootContainer);
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
      }
      Message message = ERR_JEB_EXPORT_IO_ERROR.get(ioe.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    catch (JebException je)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, je);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   je.getMessageObject());
    }
    catch (DatabaseException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
      throw createDirectoryException(de);
    }
    catch (LDIFException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ie.getMessageObject());
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ce.getMessageObject());
    }
    finally
    {
      //If a root container was opened in this method as read only, close it
      //to leave the backend in the same state.
      if (openRootContainer && rootContainer != null)
      {
        try
        {
          rootContainer.close();
          rootContainer = null;
        }
        catch (DatabaseException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    RuntimeInformation.logInfo();

    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = rootContainer == null;

    // If the rootContainer is open, the backend is initialized by something
    // else.
    // We can't do import while the backend is online.
    if(!openRootContainer)
    {
      Message message = ERR_JEB_IMPORT_BACKEND_ONLINE.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    try
    {
      EnvironmentConfig envConfig = new EnvironmentConfig();

      envConfig.setAllowCreate(true);
      envConfig.setTransactional(false);
      envConfig.setDurability(Durability.COMMIT_NO_SYNC);
      envConfig.setLockTimeout(0, TimeUnit.SECONDS);
      envConfig.setTxnTimeout(0, TimeUnit.SECONDS);
      envConfig.setConfigParam(EnvironmentConfig.CLEANER_MIN_FILE_UTILIZATION,
          String.valueOf(cfg.getDBCleanerMinUtilization()));
      envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, String
          .valueOf(cfg.getDBLogFileMax()));

      if(!importConfig.appendToExistingData()) {
        if(importConfig.clearBackend() || cfg.getBaseDN().size() <= 1) {
          // We have the writer lock on the environment, now delete the
          // environment and re-open it. Only do this when we are
          // importing to all the base DNs in the backend or if the backend only
          // have one base DN.
          File parentDirectory = getFileForPath(cfg.getDBDirectory());
          File backendDirectory = new File(parentDirectory, cfg.getBackendId());
          // If the backend does not exist the import will create it.
          if (backendDirectory.exists()) {
            EnvManager.removeFiles(backendDirectory.getPath());
          }
        }
      }

      Importer importer = new Importer(importConfig, cfg, envConfig);
      rootContainer = initializeRootContainer(envConfig);
      return importer.processImport(rootContainer);
    }
    catch (ExecutionException execEx)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, execEx);
      }
      if (execEx.getCause() instanceof DirectoryException)
      {
        throw ((DirectoryException) execEx.getCause());
      }
      else
      {
        Message message = ERR_EXECUTION_ERROR.get(execEx.getMessage());
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), message);
      }
    }
    catch (InterruptedException intEx)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, intEx);
      }
      Message message = ERR_INTERRUPTED_ERROR.get(intEx.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }
    catch (JebException je)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, je);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   je.getMessageObject());
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ie.getMessageObject());
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ce.getMessageObject());
    }
    finally
    {
      // leave the backend in the same state.
      try
      {
        if (rootContainer != null)
        {
          long startTime = System.currentTimeMillis();
          rootContainer.close();
          long finishTime = System.currentTimeMillis();
          long closeTime = (finishTime - startTime) / 1000;
          Message msg =
                       NOTE_JEB_IMPORT_LDIF_ROOTCONTAINER_CLOSE.get(closeTime);
          logError(msg);
          rootContainer = null;
        }

        // Sync the environment to disk.
        if (debugEnabled())
        {
          Message message = NOTE_JEB_IMPORT_CLOSING_DATABASE.get();
          TRACER.debugInfo(message.toString());
        }
      }
      catch (DatabaseException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
      }
    }
  }



  /**
   * Verify the integrity of the backend instance.
   * @param verifyConfig The verify configuration.
   * @param statEntry Optional entry to save stats into.
   * @return The error count.
   * @throws  ConfigException  If an unrecoverable problem arises during
   *                           initialization.
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public long verifyBackend(VerifyConfig verifyConfig, Entry statEntry)
      throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = rootContainer == null;
    long errorCount = 0 ;

    try
    {
      if(openRootContainer)
      {
        EnvironmentConfig envConfig =
            ConfigurableEnvironment.parseConfigEntry(cfg);

        envConfig.setReadOnly(true);
        envConfig.setAllowCreate(false);
        envConfig.setTransactional(false);
        envConfig.setConfigParam("je.env.isLocking", "true");
        envConfig.setConfigParam("je.env.runCheckpointer", "true");

        rootContainer = initializeRootContainer(envConfig);
      }

      VerifyJob verifyJob = new VerifyJob(verifyConfig);
      errorCount = verifyJob.verifyBackend(rootContainer, statEntry);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (JebException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      //If a root container was opened in this method as read only, close it
      //to leave the backend in the same state.
      if (openRootContainer && rootContainer != null)
      {
        try
        {
          rootContainer.close();
          rootContainer = null;
        }
        catch (DatabaseException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
    return errorCount;
  }


  /**
   * Rebuild index(es) in the backend instance. Note that the server will not
   * explicitly initialize this backend before calling this method.
   * @param rebuildConfig The rebuild configuration.
   * @throws  ConfigException  If an unrecoverable problem arises during
   *                           initialization.
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void rebuildBackend(RebuildConfig rebuildConfig)
          throws InitializationException, ConfigException, DirectoryException
  {
    // If the backend already has the root container open, we must use the same
    // underlying root container
    boolean openRootContainer = rootContainer == null;

    /*
     * If the rootContainer is open, the backend is initialized by something
     * else. We can't do any rebuild of system indexes while others are using
     * this backend.
     */
    if(!openRootContainer && rebuildConfig.includesSystemIndex())
    {
      Message message = ERR_JEB_REBUILD_BACKEND_ONLINE.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    try
    {
      EnvironmentConfig envConfig;
      if (openRootContainer)
      {
        envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(false);
        envConfig.setDurability(Durability.COMMIT_NO_SYNC);
        envConfig.setLockTimeout(0, TimeUnit.SECONDS);
        envConfig.setTxnTimeout(0, TimeUnit.SECONDS);
        envConfig.setConfigParam(
            EnvironmentConfig.CLEANER_MIN_FILE_UTILIZATION, String.valueOf(cfg
                .getDBCleanerMinUtilization()));
        envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, String
            .valueOf(cfg.getDBLogFileMax()));

        Importer importer = new Importer(rebuildConfig, cfg, envConfig);
        rootContainer = initializeRootContainer(envConfig);
        importer.rebuildIndexes(rootContainer);
      }
      else
      {
        envConfig = ConfigurableEnvironment.parseConfigEntry(cfg);

        Importer importer = new Importer(rebuildConfig, cfg, envConfig);
        importer.rebuildIndexes(rootContainer);
      }
    }
    catch (ExecutionException execEx)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, execEx);
      }
      Message message = ERR_EXECUTION_ERROR.get(execEx.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }
    catch (InterruptedException intEx)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, intEx);
      }
      Message message = ERR_INTERRUPTED_ERROR.get(intEx.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              ce.getMessageObject());
    }
    catch (JebException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              e.getMessageObject());
    }
    catch (InitializationException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new InitializationException(e.getMessageObject());
    }
    finally
    {
      //If a root container was opened in this method as read only, close it
      //to leave the backend in the same state.
      if (openRootContainer && rootContainer != null)
      {
        try
        {
          rootContainer.close();
          rootContainer = null;
        }
        catch (DatabaseException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(BackupConfig backupConfig)
      throws DirectoryException
  {
    BackupManager backupManager =
        new BackupManager(getBackendID());
    File parentDir = getFileForPath(cfg.getDBDirectory());
    File backendDir = new File(parentDir, cfg.getBackendId());
    backupManager.createBackup(backendDir, backupConfig);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    BackupManager backupManager =
        new BackupManager(getBackendID());
    backupManager.removeBackup(backupDirectory, backupID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void restoreBackup(RestoreConfig restoreConfig)
      throws DirectoryException
  {
    BackupManager backupManager =
        new BackupManager(getBackendID());
    File parentDir = getFileForPath(cfg.getDBDirectory());
    File backendDir = new File(parentDir, cfg.getBackendId());
    backupManager.restoreBackup(backendDir, restoreConfig);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(Configuration configuration,
                                           List<Message> unacceptableReasons)
  {
    LocalDBBackendCfg config = (LocalDBBackendCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      LocalDBBackendCfg cfg,
      List<Message> unacceptableReasons)
  {
    // Make sure that the logging level value is acceptable.
    try {
      Level.parse(cfg.getDBLoggingLevel());
    } catch (Exception e) {
      Message message = ERR_JEB_INVALID_LOGGING_LEVEL.get(
              String.valueOf(cfg.getDBLoggingLevel()),
              String.valueOf(cfg.dn()));
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(LocalDBBackendCfg newCfg)
  {
    ConfigChangeResult ccr;
    ResultCode resultCode = ResultCode.SUCCESS;
    ArrayList<Message> messages = new ArrayList<Message>();


    try
    {
      if(rootContainer != null)
      {
        DN[] newBaseDNs = new DN[newCfg.getBaseDN().size()];
        newBaseDNs = newCfg.getBaseDN().toArray(newBaseDNs);

        // Check for changes to the base DNs.
        for (DN baseDN : cfg.getBaseDN())
        {
          boolean found = false;
          for (DN dn : newBaseDNs)
          {
            if (dn.equals(baseDN))
            {
              found = true;
            }
          }
          if (!found)
          {
            // The base DN was deleted.
            DirectoryServer.deregisterBaseDN(baseDN);
            EntryContainer ec =
                rootContainer.unregisterEntryContainer(baseDN);
            ec.close();
            ec.delete();
          }
        }

        for (DN baseDN : newBaseDNs)
        {
          if (!rootContainer.getBaseDNs().contains(baseDN))
          {
            try
            {
              // The base DN was added.
              EntryContainer ec =
                  rootContainer.openEntryContainer(baseDN, null);
              rootContainer.registerEntryContainer(baseDN, ec);
              DirectoryServer.registerBaseDN(baseDN, this, false);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              resultCode = DirectoryServer.getServerErrorResultCode();


              messages.add(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
                      String.valueOf(baseDN),
                      String.valueOf(e)));
              ccr = new ConfigChangeResult(resultCode, false, messages);
              return ccr;
            }
          }
        }

        baseDNs = newBaseDNs;
      }

      if(cfg.getDiskFullThreshold() != newCfg.getDiskFullThreshold() ||
          cfg.getDiskLowThreshold() != newCfg.getDiskLowThreshold())
      {
        diskMonitor.setFullThreshold(newCfg.getDiskFullThreshold());
        diskMonitor.setLowThreshold(newCfg.getDiskLowThreshold());
      }

      // Put the new configuration in place.
      this.cfg = newCfg;
    }
    catch (Exception e)
    {
      messages.add(Message.raw(stackTraceToSingleLineString(e)));
      ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                   false, messages);
      return ccr;
    }

    ccr = new ConfigChangeResult(resultCode, false, messages);
    return ccr;
  }

  /**
   * Returns a handle to the JE root container currently used by this backend.
   * The rootContainer could be NULL if the backend is not initialized.
   *
   * @return The RootContainer object currently used by this backend.
   */
  public RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Returns a new read-only handle to the JE root container for this backend.
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
  public RootContainer getReadOnlyRootContainer()
      throws ConfigException, InitializationException
  {
    EnvironmentConfig envConfig =
        ConfigurableEnvironment.parseConfigEntry(cfg);

    envConfig.setReadOnly(true);
    envConfig.setAllowCreate(false);
    envConfig.setTransactional(false);
    envConfig.setConfigParam("je.env.isLocking", "true");
    envConfig.setConfigParam("je.env.runCheckpointer", "true");

    return initializeRootContainer(envConfig);
  }

  /**
   * Clears all the entries from the backend.  This method is for test cases
   * that use the JE backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  JebException     If an error occurs while removing the data.
   */
  public void clearBackend()
      throws ConfigException, JebException
  {
    // Determine the backend database directory.
    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    File backendDirectory = new File(parentDirectory, cfg.getBackendId());
    EnvManager.removeFiles(backendDirectory.getPath());
  }

  /**
   * Creates a customized DirectoryException from the DatabaseException thrown
   * by JE backend.
   *
   * @param  e The DatabaseException to be converted.
   * @return  DirectoryException created from exception.
   */
  DirectoryException createDirectoryException(DatabaseException e) {
    ResultCode resultCode = DirectoryServer.getServerErrorResultCode();
    Message message;
    if ((e instanceof EnvironmentFailureException) &&
            !rootContainer.isValid()) {
      message = NOTE_BACKEND_ENVIRONMENT_UNUSABLE.get(getBackendID());
      logError(message);
      DirectoryServer.sendAlertNotification(DirectoryServer.getInstance(),
              ALERT_TYPE_BACKEND_ENVIRONMENT_UNUSABLE, message);
    }

    String jeMessage = e.getMessage();
    if (jeMessage == null) {
      jeMessage = stackTraceToSingleLineString(e);
    }
    message = ERR_JEB_DATABASE_EXCEPTION.get(jeMessage);
    return new DirectoryException(resultCode, message, e);
  }

  /**
   * {@inheritDoc}
   */
  public String getClassName() {
    return CLASS_NAME;
  }

  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String, String> getAlerts() {
    LinkedHashMap<String, String> alerts = new LinkedHashMap<String, String>();

    alerts.put(ALERT_TYPE_BACKEND_ENVIRONMENT_UNUSABLE,
            ALERT_DESCRIPTION_BACKEND_ENVIRONMENT_UNUSABLE);
    alerts.put(ALERT_TYPE_DISK_SPACE_LOW,
            ALERT_DESCRIPTION_DISK_SPACE_LOW);
    alerts.put(ALERT_TYPE_DISK_FULL,
            ALERT_DESCRIPTION_DISK_FULL);
    return alerts;
  }

  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN() {
    return cfg.dn();
  }

  private RootContainer initializeRootContainer(EnvironmentConfig envConfig)
          throws ConfigException, InitializationException {
    // Open the database environment
    try {
      RootContainer rc = new RootContainer(this, cfg);
      rc.open(envConfig);
      return rc;
    }
    catch (DatabaseException e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_JEB_OPEN_ENV_FAIL.get(e.getMessage());
      throw new InitializationException(message, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws
          UnsupportedOperationException {
    EntryCachePreloader preloader =
            new EntryCachePreloader(this);
    preloader.preload();
  }

  /**
   * {@inheritDoc}
   */
  public void diskLowThresholdReached(DiskSpaceMonitor monitor) {
    Message msg = ERR_JEB_DISK_LOW_THRESHOLD_REACHED.get(
        monitor.getDirectory().getPath(), cfg.getBackendId(),
        monitor.getFreeSpace(), Math.max(monitor.getLowThreshold(),
            monitor.getFullThreshold()));
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_DISK_SPACE_LOW, msg);
  }

  /**
   * {@inheritDoc}
   */
  public void diskFullThresholdReached(DiskSpaceMonitor monitor) {
    Message msg = ERR_JEB_DISK_FULL_THRESHOLD_REACHED.get(
        monitor.getDirectory().getPath(), cfg.getBackendId(),
        monitor.getFreeSpace(), Math.max(monitor.getLowThreshold(),
            monitor.getFullThreshold()));
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_DISK_FULL, msg);
  }

  /**
   * {@inheritDoc}
   */
  public void diskSpaceRestored(DiskSpaceMonitor monitor) {
    Message msg = NOTE_JEB_DISK_SPACE_RESTORED.get(monitor.getFreeSpace(),
        monitor.getDirectory().getPath(), cfg.getBackendId(),
        Math.max(monitor.getLowThreshold(), monitor.getFullThreshold()));
    logError(msg);
  }

  private void checkDiskSpace(Operation operation) throws DirectoryException
  {
    if(diskMonitor.isFullThresholdReached() ||
        (diskMonitor.isLowThresholdReached() && operation != null &&
            !operation.getClientConnection().hasPrivilege(
                Privilege.BYPASS_LOCKDOWN, operation)))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          WARN_JEB_OUT_OF_DISK_SPACE.get());
    }
  }
}

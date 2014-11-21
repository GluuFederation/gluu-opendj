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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.JebMessages.NOTE_JEB_EXPORT_FINAL_STATUS;
import static org.opends.messages.JebMessages.NOTE_JEB_EXPORT_PROGRESS_REPORT;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.*;

import org.opends.server.replication.protocol.LDAPUpdateMsg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.jeb.BackupManager;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.plugin.ReplicationServerListener;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ByteString;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.FilterType;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.DeleteChangeRecordEntry;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.ModifyDNChangeRecordEntry;
import org.opends.server.util.Validator;
import static org.opends.server.config.ConfigConstants.ATTR_OBJECTCLASSES_LC;
import org.opends.server.protocols.internal.InternalSearchOperation;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines a backend that stores its information in an
 * associated replication server object.
 * This is primarily intended to take advantage of the backup/restore/
 * import/export of the backend API, and to provide an LDAP access
 * to the replication server database.
 * <BR><BR>
 * Entries stored in this backend are held in the DB associated with
 * the replication server.
 * <BR><BR>
 * Currently are only implemented the create and restore backup features.
 *
 */
public class ReplicationBackend
       extends Backend
{
  private static final String CHANGE_NUMBER = "replicationChangeNumber";

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private static final String BASE_DN = "dc=replicationchanges";

  // The base DNs for this backend.
  private DN[] baseDNs;

  // The base DNs for this backend, in a hash set.
  private HashSet<DN> baseDNSet;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  private ReplicationServer server;

  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The current number of entries exported.
   */
  private long exportedCount = 0;

  /**
   * The current number of entries skipped.
   */
  private long skippedCount = 0;

  //Objectclass for getEntry root entries.
  private HashMap<ObjectClass,String> rootObjectclasses;

  //Attributes used for getEntry root entries.
  private LinkedHashMap<AttributeType,List<Attribute>> attributes;

  //Operational attributes used for getEntry root entries.
  private Map<AttributeType,List<Attribute>> operationalAttributes;


  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public ReplicationBackend()
  {
    super();
    // Perform all initialization in initializeBackend.
  }


  /**
   * Set the base DNs for this backend.  This is used by the unit tests
   * to set the base DNs without having to provide a configuration
   * object when initializing the backend.
   * @param baseDNs The set of base DNs to be served by this memory backend.
   */
  public void setBaseDNs(DN[] baseDNs)
  {
    this.baseDNs = baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void configureBackend(Configuration config) throws ConfigException
  {
    if (config != null)
    {
      Validator.ensureTrue(config instanceof BackendCfg);
      BackendCfg cfg = (BackendCfg) config;
      DN[] newBaseDNs = new DN[cfg.getBaseDN().size()];
      cfg.getBaseDN().toArray(newBaseDNs);
      setBaseDNs(newBaseDNs);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void initializeBackend()
       throws ConfigException, InitializationException
  {
    if ((baseDNs == null) || (baseDNs.length != 1))
    {
      Message message = ERR_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE.get();
      throw new ConfigException(message);
    }

    baseDNSet = new HashSet<DN>();
    baseDNSet.addAll(Arrays.asList(baseDNs));

    supportedControls = new HashSet<String>();
    supportedFeatures = new HashSet<String>();

    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, true);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            dn.toString(), getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }
    rootObjectclasses = new LinkedHashMap<ObjectClass,String>(3);
    rootObjectclasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    ObjectClass domainOC = DirectoryServer.getObjectClass("domain", true);
    rootObjectclasses.put(domainOC, "domain");
    ObjectClass objectclassOC =
                   DirectoryServer.getObjectClass(ATTR_OBJECTCLASSES_LC, true);
    rootObjectclasses.put(objectclassOC, ATTR_OBJECTCLASSES_LC);
    attributes = new LinkedHashMap<AttributeType,List<Attribute>>();

    Attribute a = Attributes.create("changetype", "add");
    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    attributes.put(a.getAttributeType(), attrList);
    operationalAttributes = new LinkedHashMap<AttributeType,List<Attribute>>();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void finalizeBackend()
  {
    for (DN dn : baseDNs)
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
  public synchronized long getEntryCount()
  {
    if (server==null)
    {
      try
      {
        server = getReplicationServer();
        if (server == null)
        {
          return 0;
        }
      }
      catch(Exception e)
      {
        return 0;
      }
    }

    //This method only returns the number of actual change entries, the
    //domain and any baseDN entries are not counted.
    long retNum=0;
    Iterator<ReplicationServerDomain> rcachei = server.getDomainIterator();
    if (rcachei != null)
    {
      while (rcachei.hasNext())
      {
        ReplicationServerDomain rsd = rcachei.next();
        retNum += rsd.getChangesCount();
      }
    }
    return retNum;

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
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized Entry getEntry(DN entryDN)
  {
    Entry e = null;
    try {
      if(baseDNSet.contains(entryDN)) {
           return new Entry(entryDN, rootObjectclasses, attributes,
                            operationalAttributes);
      } else {
        InternalClientConnection conn =
                InternalClientConnection.getRootConnection();
        SearchFilter filter=
                SearchFilter.createFilterFromString("(changetype=*)");
        InternalSearchOperation searchOperation =
                new InternalSearchOperation(conn,
                        InternalClientConnection.nextOperationID(),
                        InternalClientConnection.nextMessageID(), null, entryDN,
                        SearchScope.BASE_OBJECT,
                        DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                        filter, null, null);
        search(searchOperation);
        LinkedList<SearchResultEntry> resultEntries =
                searchOperation.getSearchEntries();
        if(resultEntries.size() != 0) {
          e=resultEntries.getFirst();
        }
      }
    } catch (DirectoryException ex) {
      e=null;
    }
    return e;

  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized boolean entryExists(DN entryDN)
  {
   return getEntry(entryDN) != null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_ADD_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void deleteEntry(DN entryDN,
                                       DeleteOperation deleteOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_DELETE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void replaceEntry(Entry oldEntry, Entry newEntry,
                                        ModifyOperation modifyOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_MODIFY_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void renameEntry(DN currentDN, Entry entry,
                                       ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_MODIFY_DN_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
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
  public synchronized void exportLDIF(LDIFExportConfig exportConfig)
  throws DirectoryException
  {
    List<DN> includeBranches = exportConfig.getIncludeBranches();
    DN baseDN;
    ArrayList<ReplicationServerDomain> exportContainers =
      new ArrayList<ReplicationServerDomain>();
    if(server == null) {
       Message message = ERR_REPLICATONBACKEND_EXPORT_LDIF_FAILED.get();
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,message);
    }
    Iterator<ReplicationServerDomain> rsdi = server.getDomainIterator();
    if (rsdi != null)
    {
      while (rsdi.hasNext())
      {
        ReplicationServerDomain rc = rsdi.next();

        // Skip containers that are not covered by the include branches.
        baseDN = DN.decode(rc.getBaseDn() + "," + BASE_DN);

        if (includeBranches == null || includeBranches.isEmpty())
        {
          exportContainers.add(rc);
        }
        else
        {
          for (DN includeBranch : includeBranches)
          {
            if (includeBranch.isDescendantOf(baseDN) ||
                includeBranch.isAncestorOf(baseDN))
            {
              exportContainers.add(rc);
            }
          }
        }
      }
    }

    // Make a note of the time we started.
    long startTime = System.currentTimeMillis();

    // Start a timer for the progress report.
    Timer timer = new Timer();
    TimerTask progressTask = new ProgressTask();
    timer.scheduleAtFixedRate(progressTask, progressInterval,
        progressInterval);

    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
        ERR_BACKEND_CANNOT_CREATE_LDIF_WRITER.get(String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }

    exportRootChanges(exportContainers, exportConfig, ldifWriter);

    // Iterate through the containers.
    try
    {
      for (ReplicationServerDomain exportContainer : exportContainers)
      {
        if (exportConfig.isCancelled())
        {
          break;
        }
        processContainer(exportContainer, exportConfig, ldifWriter, null);
      }
    }
    finally
    {
      timer.cancel();

      // Close the LDIF writer
      try
      {
        ldifWriter.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    long finishTime = System.currentTimeMillis();
    long totalTime = (finishTime - startTime);

    float rate = 0;
    if (totalTime > 0)
    {
      rate = 1000f*exportedCount / totalTime;
    }

    Message message = NOTE_JEB_EXPORT_FINAL_STATUS.get(
        exportedCount, skippedCount, totalTime/1000, rate);
    logError(message);
  }

  /*
   * Exports the root changes of the export, and one entry by domain.
   */
  private void exportRootChanges(List<ReplicationServerDomain> exportContainers,
      LDIFExportConfig exportConfig, LDIFWriter ldifWriter)
  {
    Map<AttributeType,List<Attribute>> attrs =
      new HashMap<AttributeType,List<Attribute>>();
    ArrayList<Attribute> ldapAttrList = new ArrayList<Attribute>();

    AttributeType ocType = DirectoryServer.getObjectClassAttributeType();
    AttributeBuilder builder = new AttributeBuilder(ocType);
    builder.add("top");
    builder.add("domain");
    Attribute ocAttr = builder.toAttribute();
    ldapAttrList.add(ocAttr);
    attrs.put(ocType, ldapAttrList);

    try
    {
      AddChangeRecordEntry changeRecord =
        new AddChangeRecordEntry(DN.decode(BASE_DN),
                               attrs);
      ldifWriter.writeChangeRecord(changeRecord);
    }
    catch (Exception e) { /* do nothing */ }

    for (ReplicationServerDomain exportContainer : exportContainers)
    {
      if (exportConfig != null && exportConfig.isCancelled())
      {
        break;
      }

      attrs.clear();

      ldapAttrList.clear();
      ldapAttrList.add(ocAttr);
      attrs.put(ocType, ldapAttrList);

      TRACER.debugInfo("State=" +
          exportContainer.getDbServerState().toString());
      Attribute stateAttr = Attributes.create("state", exportContainer
          .getDbServerState().toString());
      ldapAttrList.clear();
      ldapAttrList.add(stateAttr);
      attrs.put(stateAttr.getAttributeType(), ldapAttrList);

      Attribute genidAttr = Attributes.create("generation-id", String
          .valueOf(exportContainer.getGenerationId())
          + exportContainer.getBaseDn());
      ldapAttrList.clear();
      ldapAttrList.add(genidAttr);
      attrs.put(genidAttr.getAttributeType(), ldapAttrList);

      try
      {
        AddChangeRecordEntry changeRecord =
          new AddChangeRecordEntry(DN.decode(
              exportContainer.getBaseDn() + "," + BASE_DN),
              attrs);
        ldifWriter.writeChangeRecord(changeRecord);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        Message message = ERR_BACKEND_EXPORT_ENTRY.get(
            exportContainer.getBaseDn() + "," + BASE_DN,
            String.valueOf(e));
        logError(message);
      }
    }
  }

  /**
   * Processes the changes for a given ReplicationServerDomain.
   */
  private void processContainer(ReplicationServerDomain rsd,
      LDIFExportConfig exportConfig, LDIFWriter ldifWriter,
      SearchOperation searchOperation)
  {
    // Walk through the servers
    for (int serverId : rsd.getServers())
    {
      if (exportConfig != null && exportConfig.isCancelled())
      { // Abort if cancelled
        break;
      }

      ChangeNumber previousChangeNumber = null;
      if (searchOperation != null)
      {
        // Try to optimize for filters like replicationChangeNumber>=xxxxx
        // or replicationChangeNumber=xxxxx :
        // If the search filter is one of these 2 filters, move directly to
        // ChangeNumber=xxxx before starting the iteration.
        SearchFilter filter = searchOperation.getFilter();
        previousChangeNumber = extractChangeNumber(filter);

        if ((previousChangeNumber == null) &&
            (filter.getFilterType().equals(FilterType.AND)))
        {
          for (SearchFilter filterComponents: filter.getFilterComponents())
          {
            previousChangeNumber = extractChangeNumber(filterComponents);
            if (previousChangeNumber != null)
              break;
          }
        }
      }


      ReplicationIterator ri = rsd.getChangelogIterator(serverId,
          previousChangeNumber);

      if (ri != null)
      {
        try
        {
          int lookthroughCount = 0;
          int lookthroughLimit = 0;
          if (searchOperation != null)
          {
            lookthroughLimit =
              searchOperation.getClientConnection().getLookthroughLimit();
          }

          // Walk through the changes
          while (ri.getChange() != null)
          {
            if (exportConfig != null && exportConfig.isCancelled())
            { // abort if cancelled
              break;
            }
            if (searchOperation != null)
            {
              try
              {
                if (lookthroughLimit > 0 && lookthroughCount > lookthroughLimit)
                {
                  // Lookthrough limit exceeded
                  searchOperation.setResultCode(
                      ResultCode.ADMIN_LIMIT_EXCEEDED);
                  searchOperation.setErrorMessage(null);
                  break;
                }

                searchOperation.checkIfCanceled(false);
              } catch (CanceledOperationException e)
              {
                searchOperation.setResultCode(ResultCode.CANCELED);
                searchOperation.setErrorMessage(null);
                break;
              }
            }
            lookthroughCount++;
            UpdateMsg msg = ri.getChange();
            processChange(
                msg, exportConfig, ldifWriter, searchOperation,
                rsd.getBaseDn());
            if (!ri.next())
              break;
          }
        }
        finally
        {
          ri.releaseCursor();
        }
      }
    }
  }

  /**
   * Attempt to extract a ChangeNumber from searchFilter like
   * ReplicationChangeNumber=xxxx or ReplicationChangeNumber>=xxxx.
   *
   * @param filter The filter to evaluate.
   *
   * @return       The extracted ChangeNumber or null if no ChangeNumber
   *               was found.
   */
  private ChangeNumber extractChangeNumber(SearchFilter filter)
  {
    AttributeType changeNumberAttrType =
      DirectoryServer.getDefaultAttributeType(CHANGE_NUMBER);

    FilterType filterType = filter.getFilterType();

    if ( (filterType.equals(FilterType.GREATER_OR_EQUAL) ||
             filterType.equals(FilterType.EQUALITY) ) &&
             (filter.getAttributeType().equals(changeNumberAttrType)))
    {
      try
      {
        ChangeNumber startingChangeNumber =
          new ChangeNumber(filter.getAssertionValue().getValue().toString());
         return new ChangeNumber(
              startingChangeNumber.getTime(),
              startingChangeNumber.getSeqnum()-1,
              startingChangeNumber.getServerId());
      }
      catch (Exception e)
      {
        // don't try to optimize the search if we the ChangeNumber is
        // not a valid replication ChangeNumber.
      }
    }
    return null;
  }


  /**
   * Export one change.
   */
  private void processChange(UpdateMsg updateMsg,
      LDIFExportConfig exportConfig, LDIFWriter ldifWriter,
      SearchOperation searchOperation, String baseDN)
  {
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();
    Entry entry = null;
    DN dn = null;

    ObjectClass objectclass =
      DirectoryServer.getDefaultObjectClass("extensibleObject");


    try
    {
      if (updateMsg instanceof LDAPUpdateMsg)
      {
        LDAPUpdateMsg msg = (LDAPUpdateMsg) updateMsg;

        if (msg instanceof AddMsg)
        {
          AddMsg addMsg = (AddMsg)msg;
          AddOperation addOperation = (AddOperation)msg.createOperation(conn);

          dn = DN.decode("puid=" + addMsg.getParentEntryUUID() + "+" +
              CHANGE_NUMBER + "=" + msg.getChangeNumber().toString() + "+" +
              msg.getDn() + "," + BASE_DN);

          Map<AttributeType,List<Attribute>> attrs =
            new HashMap<AttributeType,List<Attribute>>();
          Map<ObjectClass, String> objectclasses =
            new HashMap<ObjectClass, String>();

          for (RawAttribute a : addOperation.getRawAttributes())
          {
            Attribute attr = a.toAttribute();
            if (attr.getAttributeType().isObjectClassType())
            {
              for (ByteString os : a.getValues())
              {
                String ocName = os.toString();
                ObjectClass oc =
                  DirectoryServer.getObjectClass(toLowerCase(ocName));
                if (oc == null)
                {
                  oc = DirectoryServer.getDefaultObjectClass(ocName);
                }

                objectclasses.put(oc,ocName);
              }
            }
            else
            {
              addAttribute(attrs, attr);
            }
          }

          Attribute changetype = Attributes.create("changetype", "add");
          addAttribute(attrs, changetype);

          if (exportConfig != null)
          {
            AddChangeRecordEntry changeRecord =
              new AddChangeRecordEntry(dn, attrs);
            ldifWriter.writeChangeRecord(changeRecord);
          }
          else
          {
            entry = new Entry(dn, objectclasses, attrs, null);
          }
        }
        else if (msg instanceof DeleteMsg)
        {
          DeleteMsg delMsg = (DeleteMsg)msg;

          dn = DN.decode("uuid=" + msg.getEntryUUID() + "," +
              CHANGE_NUMBER + "=" + delMsg.getChangeNumber().toString()+ "," +
              msg.getDn() +","+ BASE_DN);

          DeleteChangeRecordEntry changeRecord =
            new DeleteChangeRecordEntry(dn);
          if (exportConfig != null)
          {
            ldifWriter.writeChangeRecord(changeRecord);
          }
          else
          {
            Writer writer = new Writer();
            LDIFWriter ldifWriter2 = writer.getLDIFWriter();
            ldifWriter2.writeChangeRecord(changeRecord);
            LDIFReader reader = writer.getLDIFReader();
            entry = reader.readEntry();
          }
        }
        else if (msg instanceof ModifyMsg)
        {
          ModifyOperation op = (ModifyOperation)msg.createOperation(conn);

          dn = DN.decode("uuid=" + msg.getEntryUUID() + "," +
              CHANGE_NUMBER + "=" + msg.getChangeNumber().toString()+ "," +
              msg.getDn() +","+ BASE_DN);
          op.setInternalOperation(true);

          ModifyChangeRecordEntry changeRecord =
            new ModifyChangeRecordEntry(dn, op.getRawModifications());
          if (exportConfig != null)
          {
            ldifWriter.writeChangeRecord(changeRecord);
          }
          else
          {
            Writer writer = new Writer();
            LDIFWriter ldifWriter2 = writer.getLDIFWriter();
            ldifWriter2.writeChangeRecord(changeRecord);
            LDIFReader reader = writer.getLDIFReader();
            entry = reader.readEntry();
          }
        }
        else if (msg instanceof ModifyDNMsg)
        {
          ModifyDNOperation op = (ModifyDNOperation)msg.createOperation(conn);

          dn = DN.decode("uuid=" + msg.getEntryUUID() + "," +
              CHANGE_NUMBER + "=" + msg.getChangeNumber().toString()+ "," +
              msg.getDn() +","+ BASE_DN);
          op.setInternalOperation(true);

          ModifyDNChangeRecordEntry changeRecord =
            new ModifyDNChangeRecordEntry(dn, op.getNewRDN(), op.deleteOldRDN(),
                op.getNewSuperior());

          if (exportConfig != null)
          {
            ldifWriter.writeChangeRecord(changeRecord);
          }
          else
          {
            Writer writer = new Writer();
            LDIFWriter ldifWriter2 = writer.getLDIFWriter();
            ldifWriter2.writeChangeRecord(changeRecord);
            LDIFReader reader = writer.getLDIFReader();
            entry = reader.readEntry();
          }
        }

        if (exportConfig != null)
        {
          this.exportedCount++;
        }
        else
        {
          // Add extensibleObject objectclass and the ChangeNumber
          // in the entry.
          if (!entry.getObjectClasses().containsKey(objectclass))
            entry.addObjectClass(objectclass);
          Attribute changeNumber =
            Attributes.create(CHANGE_NUMBER,
                msg.getChangeNumber().toString());
          addAttribute(entry.getUserAttributes(), changeNumber);
          Attribute domain = Attributes.create("replicationDomain", baseDN);
          addAttribute(entry.getUserAttributes(), domain);

          // Get the base DN, scope, and filter for the search.
          DN  searchBaseDN = searchOperation.getBaseDN();
          SearchScope  scope  = searchOperation.getScope();
          SearchFilter filter = searchOperation.getFilter();

          boolean ms = entry.matchesBaseAndScope(searchBaseDN, scope);
          boolean mf = filter.matchesEntry(entry);
          if ( ms && mf )
          {
            searchOperation.returnEntry(entry, new LinkedList<Control>());
          }
        }
      }
    }
    catch (Exception e)
    {
      this.skippedCount++;
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message;
      String dnStr;
      if (dn == null)
      {
        dnStr = "Unkown";
      }
      else
      {
        dnStr = dn.toNormalizedString();
      }
      if (exportConfig != null)
      {
        message = ERR_BACKEND_EXPORT_ENTRY.get(
          dnStr, String.valueOf(e));
      }
      else
      {
        message = ERR_BACKEND_SEARCH_ENTRY.get(
            dnStr, e.getLocalizedMessage());
      }
      logError(message);
    }
  }

  /**
   * Add an attribute to a provided Map of attribute.
   *
   * @param attributes The Map that should be updated.
   * @param attribute  The attribute that should be added to the Map.
   */
  private void addAttribute(
      Map<AttributeType,List<Attribute>> attributes, Attribute attribute)
  {
    AttributeType attrType = attribute.getAttributeType();
    List<Attribute> attrs = attributes.get(attrType);
    if (attrs == null)
    {
      attrs = new ArrayList<Attribute>(1);
      attrs.add(attribute);
      attributes.put(attrType, attrs);
    }
    else
    {
      attrs.add(attribute);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    Message message = ERR_REPLICATONBACKEND_IMPORT_LDIF_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
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
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    BackupManager backupManager = new BackupManager(getBackendID());
    File backendDir = getFileForPath(getReplicationServerCfg()
        .getReplicationDBDirectory());
    backupManager.createBackup(backendDir, backupConfig);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
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
  public boolean supportsRestore()
  {
    return true;
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
    File backendDir = getFileForPath(getReplicationServerCfg()
        .getReplicationDBDirectory());
    backupManager.restoreBackup(backendDir, restoreConfig);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                 ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
        throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                 ERR_HAS_SUBORDINATES_NOT_SUPPORTED.get());
  }

  /**
   * Set the replication server associated with this backend.
   * @param server The replication server.
   */
  public void setServer(ReplicationServer server)
  {
    this.server = server;
  }

  /**
   * This class reports progress of the export job at fixed intervals.
   */
  private final class ProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been exported at the time of the
     * previous progress report.
     */
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * Create a new export progress task.
     */
    public ProgressTask()
    {
      previousTime = System.currentTimeMillis();
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run()
    {
      long latestCount = exportedCount;
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      float rate = 1000f*deltaCount / deltaTime;

      Message message =
          NOTE_JEB_EXPORT_PROGRESS_REPORT.get(latestCount, skippedCount, rate);
      logError(message);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Get the base DN, scope, and filter for the search.
    DN           searchBaseDN = searchOperation.getBaseDN();
    DN baseDN;
    ArrayList<ReplicationServerDomain> searchContainers =
      new ArrayList<ReplicationServerDomain>();

    //This check is for GroupManager initialization. It currently doesn't
    //come into play because the replication server variable is null in
    //the check above. But if the order of initialization of the server variable
    //is ever changed, the following check will keep replication change entries
    //from being added to the groupmanager cache erroneously.
    List<Control> requestControls = searchOperation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE))
        {
          return;
        }
      }
    }

    // don't do anything if the search is a base search on
    // the backend suffix.
    try
    {
      DN backendBaseDN = DN.decode(BASE_DN);
      if ( (searchOperation.getScope().equals(SearchScope.BASE_OBJECT)) &&
           (backendBaseDN.equals(searchOperation.getBaseDN())) )
      {
        return;
      }
    }
    catch (Exception e)
    {
      return;
    }

    // Make sure the base entry exists if it's supposed to be in this backend.
    if (!handlesEntry(searchBaseDN))
    {
      DN matchedDN = searchBaseDN.getParentDNInSuffix();
      while (matchedDN != null)
      {
        if (handlesEntry(matchedDN))
        {
          break;
        }
        matchedDN = matchedDN.getParentDNInSuffix();
      }

      Message message = ERR_REPLICATIONBACKEND_ENTRY_DOESNT_EXIST.
        get(String.valueOf(searchBaseDN));
      throw new DirectoryException(
          ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
    }

    if (server==null)
    {
      server = getReplicationServer();

      if (server == null)
      {
        if (baseDNSet.contains(searchBaseDN))
        {
          return;
        }
        else
        {
          Message message = ERR_REPLICATIONBACKEND_ENTRY_DOESNT_EXIST.
          get(String.valueOf(searchBaseDN));
          throw new DirectoryException(
              ResultCode.NO_SUCH_OBJECT, message, null, null);
        }
      }
    }

    // Walk through all entries and send the ones that match.
    Iterator<ReplicationServerDomain> rsdi = server.getDomainIterator();
    if (rsdi != null)
    {
      while (rsdi.hasNext())
      {
        ReplicationServerDomain rsd = rsdi.next();

        // Skip containers that are not covered by the include branches.
        baseDN = DN.decode(rsd.getBaseDn() + "," + BASE_DN);

            if (searchBaseDN.isDescendantOf(baseDN) ||
                searchBaseDN.isAncestorOf(baseDN))
            {
              searchContainers.add(rsd);
            }
      }
    }

    for (ReplicationServerDomain exportContainer : searchContainers)
    {
      processContainer(exportContainer, null, null, searchOperation);
    }
  }


  /**
   * Retrieves the replication server associated to this backend.
   *
   * @return The server retrieved
   * @throws DirectoryException When it occurs.
   */
  private ReplicationServer getReplicationServer() throws DirectoryException
  {
    ReplicationServer replicationServer = null;

    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider<?> provider :
      DirectoryServer.getSynchronizationProviders())
    {
      if (provider instanceof MultimasterReplication)
      {
        MultimasterReplication mmp = (MultimasterReplication)provider;
        ReplicationServerListener list = mmp.getReplicationServerListener();
        if (list != null)
        {
          replicationServer = list.getReplicationServer();
          break;
        }
      }
    }
    return replicationServer;
  }

  // Find the replication server configuration associated with this
  // replication backend.
  private ReplicationServerCfg getReplicationServerCfg()
      throws DirectoryException {
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();

    for (String name : root.listSynchronizationProviders()) {
      SynchronizationProviderCfg syncCfg;
      try {
        syncCfg = root.getSynchronizationProvider(name);
      } catch (ConfigException e) {
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
            ERR_REPLICATION_SERVER_CONFIG_NOT_FOUND.get(), e);
      }
      if (syncCfg instanceof ReplicationSynchronizationProviderCfg) {
        ReplicationSynchronizationProviderCfg scfg =
          (ReplicationSynchronizationProviderCfg) syncCfg;
        try {
          return scfg.getReplicationServer();
        } catch (ConfigException e) {
          throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
              ERR_REPLICATION_SERVER_CONFIG_NOT_FOUND.get(), e);
        }
      }
    }

    // No replication server found.
    throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
        ERR_REPLICATION_SERVER_CONFIG_NOT_FOUND.get());
  }

  /**
   * Writer class to read/write from/to a bytearray.
   */
  private static final class Writer
  {
    // The underlying output stream.
    private final ByteArrayOutputStream stream;

    // The underlying LDIF config.
    private final LDIFExportConfig config;

    // The LDIF writer.
    private final LDIFWriter writer;

    /**
     * Create a new string writer.
     */
    public Writer() {
      this.stream = new ByteArrayOutputStream();
      this.config = new LDIFExportConfig(stream);
      try {
        this.writer = new LDIFWriter(config);
      } catch (IOException e) {
        // Should not happen.
        throw new RuntimeException(e);
      }
    }

    /**
     * Get the LDIF writer.
     *
     * @return Returns the LDIF writer.
     */
    public LDIFWriter getLDIFWriter() {
      return writer;
    }



    /**
     * Close the writer and get an LDIF reader for the LDIF content.
     *
     * @return Returns an LDIF Reader.
     * @throws Exception
     *           If an error occurred closing the writer.
     */
    public LDIFReader getLDIFReader() throws Exception {
      writer.close();
      String ldif = stream.toString("UTF-8");
      ldif = ldif.replace("\n-\n", "\n");
      ByteArrayInputStream istream = new ByteArrayInputStream(ldif.getBytes());
      LDIFImportConfig newConfig = new LDIFImportConfig(istream);
      // ReplicationBackend may contain entries that are not schema
      // compliant. Let's ignore them for now.
      newConfig.setValidateSchema(false);
      return new LDIFReader(newConfig);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}


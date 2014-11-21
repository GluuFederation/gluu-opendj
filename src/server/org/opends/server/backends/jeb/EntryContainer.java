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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 *      Portions copyright 2013 Manuel Gaupp
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import com.sleepycat.je.*;

import org.opends.server.api.*;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.ServerSideSortResponseControl;
import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.opends.messages.JebMessages.*;

import org.opends.messages.MessageBuilder;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.ErrorLogger.logError;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.admin.std.server.LocalDBVLVIndexCfg;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.config.ConfigException;

/**
 * Storage container for LDAP entries.  Each base DN of a JE backend is given
 * its own entry container.  The entry container is the object that implements
 * the guts of the backend API methods for LDAP operations.
 */
public class EntryContainer
implements ConfigurationChangeListener<LocalDBBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * The name of the entry database.
   */
  public static final String ID2ENTRY_DATABASE_NAME = "id2entry";

  /**
   * The name of the DN database.
   */
  public static final String DN2ID_DATABASE_NAME = "dn2id";

  /**
   * The name of the children index database.
   */
  public static final String ID2CHILDREN_DATABASE_NAME = "id2children";

  /**
   * The name of the subtree index database.
   */
  public static final String ID2SUBTREE_DATABASE_NAME = "id2subtree";

  /**
   * The name of the referral database.
   */
  public static final String REFERRAL_DATABASE_NAME = "referral";

  /**
   * The name of the state database.
   */
  public static final String STATE_DATABASE_NAME = "state";

  /**
   * The attribute used to return a search index debug string to the client.
   */
  public static final String ATTR_DEBUG_SEARCH_INDEX = "debugsearchindex";

  /**
   * The attribute index configuration manager.
   */
  public AttributeJEIndexCfgManager attributeJEIndexCfgManager;

  /**
   * The vlv index configuration manager.
   */
  public VLVJEIndexCfgManager vlvJEIndexCfgManager;

  /**
   * The backend to which this entry entryContainer belongs.
   */
  private final Backend backend;

  /**
   * The root container in which this entryContainer belongs.
   */
  private final RootContainer rootContainer;

  /**
   * The baseDN this entry container is responsible for.
   */
  private final DN baseDN;

  /**
   * The backend configuration.
   */
  private LocalDBBackendCfg config;

  /**
   * The JE database environment.
   */
  private final Environment env;

  /**
   * The DN database maps a normalized DN string to an entry ID (8 bytes).
   */
  private DN2ID dn2id;

  /**
   * The entry database maps an entry ID (8 bytes) to a complete encoded entry.
   */
  private ID2Entry id2entry;

  /**
   * Index maps entry ID to an entry ID list containing its children.
   */
  private Index id2children;

  /**
   * Index maps entry ID to an entry ID list containing its subordinates.
   */
  private Index id2subtree;

  /**
   * The referral database maps a normalized DN string to labeled URIs.
   */
  private DN2URI dn2uri;

  /**
   * The state database maps a config DN to config entries.
   */
  private State state;

  /**
   * The set of attribute indexes.
   */
  private final HashMap<AttributeType, AttributeIndex> attrIndexMap;

  /**
   * The set of VLV indexes.
   */
  private final HashMap<String, VLVIndex> vlvIndexMap;

  private String databasePrefix;
  /**
   * This class is responsible for managing the configuration for attribute
   * indexes used within this entry container.
   */
  public class AttributeJEIndexCfgManager implements
  ConfigurationAddListener<LocalDBIndexCfg>,
  ConfigurationDeleteListener<LocalDBIndexCfg>
  {
    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(
        LocalDBIndexCfg cfg,
        List<Message> unacceptableReasons)
    {
      boolean isValid = true;
      try
      {
        //Try creating all the indexes before confirming they are valid ones.
        new AttributeIndex(cfg, state, env, EntryContainer.this);
      }
      catch(Exception e)
      {
        unacceptableReasons.add(Message.raw(e.getLocalizedMessage()));
        isValid = false ;
      }
      return isValid;
    }

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(LocalDBIndexCfg cfg)
    {
      ConfigChangeResult ccr;
      boolean adminActionRequired = false;
      List<Message> messages = new ArrayList<Message>();

      try
      {
        AttributeIndex index =
          new AttributeIndex(cfg, state, env, EntryContainer.this);
        index.open();
        if(!index.isTrusted())
        {
          adminActionRequired = true;
          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
              cfg.getAttribute().getNameOrOID()));
        }
        attrIndexMap.put(cfg.getAttribute(), index);
      }
      catch(Exception e)
      {
        messages.add(Message.raw(e.getLocalizedMessage()));
        ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
            adminActionRequired,
            messages);
        return ccr;
      }

      return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
          messages);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(
        LocalDBIndexCfg cfg, List<Message> unacceptableReasons)
    {
      // TODO: validate more before returning true?
      return true;
    }

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(LocalDBIndexCfg cfg)
    {
      ConfigChangeResult ccr;
      boolean adminActionRequired = false;
      ArrayList<Message> messages = new ArrayList<Message>();

      exclusiveLock.lock();
      try
      {
        AttributeIndex index = attrIndexMap.get(cfg.getAttribute());
        deleteAttributeIndex(index);
        attrIndexMap.remove(cfg.getAttribute());
      }
      catch(DatabaseException de)
      {
        messages.add(Message.raw(StaticUtils.stackTraceToSingleLineString(de)));
        ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
            adminActionRequired,
            messages);
        return ccr;
      }
      finally
      {
        exclusiveLock.unlock();
      }

      return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
          messages);
    }
  }

  /**
   * This class is responsible for managing the configuration for VLV indexes
   * used within this entry container.
   */
  public class VLVJEIndexCfgManager implements
  ConfigurationAddListener<LocalDBVLVIndexCfg>,
  ConfigurationDeleteListener<LocalDBVLVIndexCfg>
  {
    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(
        LocalDBVLVIndexCfg cfg, List<Message> unacceptableReasons)
    {
      try
      {
        SearchFilter.createFilterFromString(cfg.getFilter());
      }
      catch(Exception e)
      {
        Message msg = ERR_JEB_CONFIG_VLV_INDEX_BAD_FILTER.get(
            cfg.getFilter(), cfg.getName(),
            e.getLocalizedMessage());
        unacceptableReasons.add(msg);
        return false;
      }

      String[] sortAttrs = cfg.getSortOrder().split(" ");
      SortKey[] sortKeys = new SortKey[sortAttrs.length];
      boolean[] ascending = new boolean[sortAttrs.length];
      for(int i = 0; i < sortAttrs.length; i++)
      {
        try
        {
          if(sortAttrs[i].startsWith("-"))
          {
            ascending[i] = false;
            sortAttrs[i] = sortAttrs[i].substring(1);
          }
          else
          {
            ascending[i] = true;
            if(sortAttrs[i].startsWith("+"))
            {
              sortAttrs[i] = sortAttrs[i].substring(1);
            }
          }
        }
        catch(Exception e)
        {
          Message msg =
            ERR_JEB_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(
                String.valueOf(sortKeys[i]), cfg.getName());
          unacceptableReasons.add(msg);
          return false;
        }

        AttributeType attrType =
          DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
        if(attrType == null)
        {
          Message msg = ERR_JEB_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(
              sortAttrs[i], cfg.getName());
          unacceptableReasons.add(msg);
          return false;
        }
        sortKeys[i] = new SortKey(attrType, ascending[i]);
      }

      return true;
    }

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(LocalDBVLVIndexCfg cfg)
    {
      ConfigChangeResult ccr;
      boolean adminActionRequired = false;
      ArrayList<Message> messages = new ArrayList<Message>();

      try
      {
        VLVIndex vlvIndex = new VLVIndex(cfg, state, env, EntryContainer.this);
        vlvIndex.open();
        if(!vlvIndex.isTrusted())
        {
          adminActionRequired = true;
          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
              cfg.getName()));
        }
        vlvIndexMap.put(cfg.getName().toLowerCase(), vlvIndex);
      }
      catch(Exception e)
      {
        messages.add(Message.raw(StaticUtils.stackTraceToSingleLineString(e)));
        ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
            adminActionRequired,
            messages);
        return ccr;
      }

      return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
          messages);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(
        LocalDBVLVIndexCfg cfg,
        List<Message> unacceptableReasons)
    {
      // TODO: validate more before returning true?
      return true;
    }

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(LocalDBVLVIndexCfg cfg)
    {
      ConfigChangeResult ccr;
      boolean adminActionRequired = false;
      List<Message> messages = new ArrayList<Message>();

      exclusiveLock.lock();
      try
      {
        VLVIndex vlvIndex =
          vlvIndexMap.get(cfg.getName().toLowerCase());
        deleteDatabase(vlvIndex);
        vlvIndexMap.remove(cfg.getName());
      }
      catch(DatabaseException de)
      {
        messages.add(Message.raw(StaticUtils.stackTraceToSingleLineString(de)));
        ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
            adminActionRequired,
            messages);
        return ccr;
      }
      finally
      {
        exclusiveLock.unlock();
      }

      return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
          messages);
    }

  }

  /**
   * A read write lock to handle schema changes and bulk changes.
   */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  final Lock sharedLock = lock.readLock();
  final Lock exclusiveLock = lock.writeLock();

  /**
   * Create a new entry entryContainer object.
   *
   * @param baseDN  The baseDN this entry container will be responsible for
   *                storing on disk.
   * @param databasePrefix The prefix to use in the database names used by
   *                       this entry container.
   * @param backend A reference to the JE backend that is creating this entry
   *                container. It is needed by the Directory Server entry cache
   *                methods.
   * @param config The configuration of the JE backend.
   * @param env The JE environment to create this entryContainer in.
   * @param rootContainer The root container this entry container is in.
   * @throws ConfigException if a configuration related error occurs.
   */
  public EntryContainer(DN baseDN, String databasePrefix, Backend backend,
      LocalDBBackendCfg config, Environment env,
      RootContainer rootContainer)
  throws ConfigException
  {
    this.backend = backend;
    this.baseDN = baseDN;
    this.config = config;
    this.env = env;
    this.rootContainer = rootContainer;

    this.databasePrefix = preparePrefix(databasePrefix);

    // Instantiate the attribute indexes.
    attrIndexMap = new HashMap<AttributeType, AttributeIndex>();

    // Instantiate the VLV indexes.
    vlvIndexMap = new HashMap<String, VLVIndex>();

    config.addLocalDBChangeListener(this);

    attributeJEIndexCfgManager =
      new AttributeJEIndexCfgManager();
    config.addLocalDBIndexAddListener(attributeJEIndexCfgManager);
    config.addLocalDBIndexDeleteListener(attributeJEIndexCfgManager);

    vlvJEIndexCfgManager =
      new VLVJEIndexCfgManager();
    config.addLocalDBVLVIndexAddListener(vlvJEIndexCfgManager);
    config.addLocalDBVLVIndexDeleteListener(vlvJEIndexCfgManager);
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws ConfigException if a configuration related error occurs.
   */
  public void open()
  throws DatabaseException, ConfigException
  {
    try
    {
      DataConfig entryDataConfig =
        new DataConfig(config.isEntriesCompressed(),
            config.isCompactEncoding(),
            rootContainer.getCompressedSchema());

      id2entry = new ID2Entry(databasePrefix + "_" + ID2ENTRY_DATABASE_NAME,
          entryDataConfig, env, this);
      id2entry.open();

      dn2id = new DN2ID(databasePrefix + "_" + DN2ID_DATABASE_NAME, env, this);
      dn2id.open();

      state = new State(databasePrefix + "_" + STATE_DATABASE_NAME, env, this);
      state.open();

      if (config.isSubordinateIndexesEnabled())
      {
        openSubordinateIndexes();
      }
      else
      {
        // Use a null index and ensure that future attempts to use the real
        // subordinate indexes will fail.
        id2children = new NullIndex(databasePrefix + "_"
            + ID2CHILDREN_DATABASE_NAME, new ID2CIndexer(), state, env, this);
        if (!env.getConfig().getReadOnly())
        {
          state.putIndexTrustState(null, id2children, false);
        }
        id2children.open(); // No-op

        id2subtree = new NullIndex(databasePrefix + "_"
            + ID2SUBTREE_DATABASE_NAME, new ID2SIndexer(), state, env, this);
        if (!env.getConfig().getReadOnly())
        {
          state.putIndexTrustState(null, id2subtree, false);
        }
        id2subtree.open(); // No-op

        logError(NOTE_JEB_SUBORDINATE_INDEXES_DISABLED.get(backend
            .getBackendID()));
      }

      dn2uri = new DN2URI(databasePrefix + "_" + REFERRAL_DATABASE_NAME,
          env, this);
      dn2uri.open();

      for (String idx : config.listLocalDBIndexes())
      {
        LocalDBIndexCfg indexCfg = config.getLocalDBIndex(idx);

        AttributeIndex index =
          new AttributeIndex(indexCfg, state, env, this);
        index.open();
        if(!index.isTrusted())
        {
          logError(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
              index.getName()));
        }
        attrIndexMap.put(indexCfg.getAttribute(), index);
      }

      for(String idx : config.listLocalDBVLVIndexes())
      {
        LocalDBVLVIndexCfg vlvIndexCfg = config.getLocalDBVLVIndex(idx);

        VLVIndex vlvIndex = new VLVIndex(vlvIndexCfg, state, env, this);
        vlvIndex.open();

        if(!vlvIndex.isTrusted())
        {
          logError(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
              vlvIndex.getName()));
        }

        vlvIndexMap.put(vlvIndexCfg.getName().toLowerCase(), vlvIndex);
      }
    }
    catch (DatabaseException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
      close();
      throw de;
    }
  }

  /**
   * Closes the entry entryContainer.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void close()
  throws DatabaseException
  {
    // Close core indexes.
    dn2id.close();
    id2entry.close();
    dn2uri.close();
    id2children.close();
    id2subtree.close();
    state.close();

    // Close attribute indexes and deregister any listeners.
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.close();
    }

    // Close VLV indexes and deregister any listeners.
    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.close();
    }

    config.removeLocalDBChangeListener(this);
    config.removeLocalDBIndexAddListener(attributeJEIndexCfgManager);
    config.removeLocalDBIndexDeleteListener(attributeJEIndexCfgManager);
    config.removeLocalDBVLVIndexAddListener(vlvJEIndexCfgManager);
    config.removeLocalDBVLVIndexDeleteListener(vlvJEIndexCfgManager);
  }

  /**
   * Retrieves a reference to the root container in which this entry container
   * exists.
   *
   * @return  A reference to the root container in which this entry container
   *          exists.
   */
  public RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Get the DN database used by this entry entryContainer. The entryContainer
   * must have been opened.
   *
   * @return The DN database.
   */
  public DN2ID getDN2ID()
  {
    return dn2id;
  }

  /**
   * Get the entry database used by this entry entryContainer. The
   * entryContainer must have been opened.
   *
   * @return The entry database.
   */
  public ID2Entry getID2Entry()
  {
    return id2entry;
  }

  /**
   * Get the referral database used by this entry entryContainer. The
   * entryContainer must have been opened.
   *
   * @return The referral database.
   */
  public DN2URI getDN2URI()
  {
    return dn2uri;
  }

  /**
   * Get the children database used by this entry entryContainer.
   * The entryContainer must have been opened.
   *
   * @return The children database.
   */
  public Index getID2Children()
  {
    return id2children;
  }

  /**
   * Get the subtree database used by this entry entryContainer.
   * The entryContainer must have been opened.
   *
   * @return The subtree database.
   */
  public Index getID2Subtree()
  {
    return id2subtree;
  }

  /**
   * Get the state database used by this entry container.
   * The entry container must have been opened.
   *
   * @return The state database.
   */
  public State getState()
  {
    return state;
  }

  /**
   * Look for an attribute index for the given attribute type.
   *
   * @param attrType The attribute type for which an attribute index is needed.
   * @return The attribute index or null if there is none for that type.
   */
  public AttributeIndex getAttributeIndex(AttributeType attrType)
  {
    return attrIndexMap.get(attrType);
  }


  /**
   * Return attribute index map.
   *
   * @return The attribute index map.
   */
  public Map<AttributeType, AttributeIndex> getAttributeIndexMap() {
    return attrIndexMap;
  }

  /**
   * Look for an VLV index for the given index name.
   *
   * @param vlvIndexName The vlv index name for which an vlv index is needed.
   * @return The VLV index or null if there is none with that name.
   */
  public VLVIndex getVLVIndex(String vlvIndexName)
  {
    return vlvIndexMap.get(vlvIndexName);
  }

  /**
   * Retrieve all attribute indexes.
   *
   * @return All attribute indexes defined in this entry container.
   */
  public Collection<AttributeIndex> getAttributeIndexes()
  {
    return attrIndexMap.values();
  }

  /**
   * Retrieve all VLV indexes.
   *
   * @return The collection of VLV indexes defined in this entry container.
   */
  public Collection<VLVIndex> getVLVIndexes()
  {
    return vlvIndexMap.values();
  }

  /**
   * Determine the highest entryID in the entryContainer.
   * The entryContainer must already be open.
   *
   * @return The highest entry ID.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public EntryID getHighestEntryID() throws DatabaseException
  {
    EntryID entryID = new EntryID(0);
    Cursor cursor = id2entry.openCursor(null, null);
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();

    // Position a cursor on the last data item, and the key should
    // give the highest ID.
    try
    {
      OperationStatus status = cursor.getLast(key, data, LockMode.DEFAULT);
      if (status == OperationStatus.SUCCESS)
      {
        entryID = new EntryID(key);
      }
    }
    finally
    {
      cursor.close();
    }
    return entryID;
  }

  /**
   * Determine the number of subordinate entries for a given entry.
   *
   * @param entryDN The distinguished name of the entry.
   * @param subtree <code>true</code> will include all the entries under the
   *                given entries. <code>false</code> will only return the
   *                number of entries immediately under the given entry.
   * @return The number of subordinate entries for the given entry or -1 if
   *         the entry does not exist.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public long getNumSubordinates(DN entryDN, boolean subtree)
  throws DatabaseException
  {
    EntryID entryID = dn2id.get(null, entryDN, LockMode.DEFAULT);
    if (entryID != null)
    {
      DatabaseEntry key =
        new DatabaseEntry(JebFormat.entryIDToDatabase(entryID.longValue()));
      EntryIDSet entryIDSet;
      if(!subtree)
      {
        entryIDSet = id2children.readKey(key, null, LockMode.DEFAULT);
      }
      else
      {
        entryIDSet = id2subtree.readKey(key, null, LockMode.DEFAULT);
      }
      long count = entryIDSet.size();
      if(count != Long.MAX_VALUE)
      {
        return count;
      }
    }
    return -1;
  }

  /**
   * Processes the specified search in this entryContainer.
   * Matching entries should be provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param searchOperation The search operation to be processed.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs while processing the
   *          search.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  public void search(SearchOperation searchOperation)
  throws DirectoryException, DatabaseException, CanceledOperationException
  {
    DN aBaseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();

    PagedResultsControl pageRequest = searchOperation
    .getRequestControl(PagedResultsControl.DECODER);
    ServerSideSortRequestControl sortRequest = searchOperation
    .getRequestControl(ServerSideSortRequestControl.DECODER);
    if(sortRequest != null && !sortRequest.containsSortKeys()
            && sortRequest.isCritical())
    {
      /**
         If the control's criticality field is true then the server SHOULD do
         the following: return unavailableCriticalExtension as a return code
         in the searchResultDone message; include the sortKeyResponseControl in
         the searchResultDone message, and not send back any search result
         entries.
       */
      searchOperation.addResponseControl(
            new ServerSideSortResponseControl(
                LDAPResultCode.NO_SUCH_ATTRIBUTE, null));
      searchOperation.setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
      return;
    }
    VLVRequestControl vlvRequest = searchOperation
    .getRequestControl(VLVRequestControl.DECODER);

    if (vlvRequest != null && pageRequest != null)
    {
      Message message = ERR_JEB_SEARCH_CANNOT_MIX_PAGEDRESULTS_AND_VLV.get();
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    // Handle client abandon of paged results.
    if (pageRequest != null)
    {
      if (pageRequest.getSize() == 0)
      {
        PagedResultsControl control;
        control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
        searchOperation.getResponseControls().add(control);
        return;
      }
      if (searchOperation.getSizeLimit() > 0 &&
        pageRequest.getSize() >= searchOperation.getSizeLimit())
      {
        // The RFC says : "If the page size is greater than or equal to the
        // sizeLimit value, the server should ignore the control as the
        // request can be satisfied in a single page"
        pageRequest = null;
      }
    }

    // Handle base-object search first.
    if (searchScope == SearchScope.BASE_OBJECT)
    {
      // Fetch the base entry.
      Entry baseEntry = fetchBaseEntry(aBaseDN, searchScope);

      if (!isManageDsaITOperation(searchOperation))
      {
        dn2uri.checkTargetForReferral(baseEntry, searchOperation.getScope());
      }

      if (searchOperation.getFilter().matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, null);
      }

      if (pageRequest != null)
      {
        // Indicate no more pages.
        PagedResultsControl control;
        control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
        searchOperation.getResponseControls().add(control);
      }

      return;
    }

    // Check whether the client requested debug information about the
    // contribution of the indexes to the search.
    StringBuilder debugBuffer = null;
    if (searchOperation.getAttributes().contains(ATTR_DEBUG_SEARCH_INDEX))
    {
      debugBuffer = new StringBuilder();
    }

    EntryIDSet entryIDList = null;
    boolean candidatesAreInScope = false;
    if(sortRequest != null)
    {
      for(VLVIndex vlvIndex : vlvIndexMap.values())
      {
        try
        {
          entryIDList =
            vlvIndex.evaluate(null, searchOperation, sortRequest, vlvRequest,
                debugBuffer);
          if(entryIDList != null)
          {
            searchOperation.addResponseControl(
                new ServerSideSortResponseControl(LDAPResultCode.SUCCESS,
                    null));
            candidatesAreInScope = true;
            break;
          }
        }
        catch (DirectoryException de)
        {
          searchOperation.addResponseControl(
              new ServerSideSortResponseControl(
                  de.getResultCode().getIntValue(), null));

          if (sortRequest.isCritical())
          {
            throw de;
          }
        }
      }
    }

    if(entryIDList == null)
    {
      // See if we could use a virtual attribute rule to process the search.
      for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes())
      {
        if (rule.getProvider().isSearchable(rule, searchOperation, true))
        {
          rule.getProvider().processSearch(rule, searchOperation);
          return;
        }
      }

      // Create an index filter to get the search result candidate entries.
      IndexFilter indexFilter =
        new IndexFilter(this, searchOperation, debugBuffer,
            rootContainer.getMonitorProvider());

      // Evaluate the filter against the attribute indexes.
      entryIDList = indexFilter.evaluate();

      // Evaluate the search scope against the id2children and id2subtree
      // indexes.
      if (entryIDList.size() > IndexFilter.FILTER_CANDIDATE_THRESHOLD)
      {
        // Read the ID from dn2id.
        EntryID baseID = dn2id.get(null, aBaseDN, LockMode.DEFAULT);
        if (baseID == null)
        {
          Message message =
            ERR_JEB_SEARCH_NO_SUCH_OBJECT.get(aBaseDN.toString());
          DN matchedDN = getMatchedDN(aBaseDN);
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              message, matchedDN, null);
        }
        DatabaseEntry baseIDData = baseID.getDatabaseEntry();

        EntryIDSet scopeList;
        if (searchScope == SearchScope.SINGLE_LEVEL)
        {
          scopeList = id2children.readKey(baseIDData, null, LockMode.DEFAULT);
        }
        else
        {
          scopeList = id2subtree.readKey(baseIDData, null, LockMode.DEFAULT);
          if (searchScope == SearchScope.WHOLE_SUBTREE)
          {
            // The id2subtree list does not include the base entry ID.
            scopeList.add(baseID);
          }
        }
        entryIDList.retainAll(scopeList);
        if (debugBuffer != null)
        {
          debugBuffer.append(" scope=");
          debugBuffer.append(searchScope);
          scopeList.toString(debugBuffer);
        }
        if (scopeList.isDefined())
        {
          // In this case we know that every candidate is in scope.
          candidatesAreInScope = true;
        }
      }

      if (sortRequest != null)
      {
          try
          {
            //If the sort key is not present, the sorting will generate the
            //default ordering. VLV search request goes through as if
            //this sort key was not found in the user entry.
            entryIDList = EntryIDSetSorter.sort(this, entryIDList,
                searchOperation,
                sortRequest.getSortOrder(),
                vlvRequest);
            if(sortRequest.containsSortKeys())
            {
              searchOperation.addResponseControl(
                new ServerSideSortResponseControl(
                                              LDAPResultCode.SUCCESS, null));
            }
            else
            {
              /*
                There is no sort key associated with the sort control. Since it
                came here it means that the criticality is false so let the
                server return all search results unsorted and include the
                sortKeyResponseControl in the searchResultDone message.
              */
              searchOperation.addResponseControl(
                      new ServerSideSortResponseControl
                                (LDAPResultCode.NO_SUCH_ATTRIBUTE, null));
            }
          }
          catch (DirectoryException de)
          {
            searchOperation.addResponseControl(
                new ServerSideSortResponseControl(
                    de.getResultCode().getIntValue(), null));

            if (sortRequest.isCritical())
            {
              throw de;
            }
          }
        }
      }

    // If requested, construct and return a fictitious entry containing
    // debug information, and no other entries.
    if (debugBuffer != null)
    {
      debugBuffer.append(" final=");
      entryIDList.toString(debugBuffer);

      Attribute attr = Attributes.create(ATTR_DEBUG_SEARCH_INDEX,
          debugBuffer.toString());

      Entry debugEntry;
      debugEntry = new Entry(DN.decode("cn=debugsearch"), null, null, null);
      debugEntry.addAttribute(attr, new ArrayList<AttributeValue>());

      searchOperation.returnEntry(debugEntry, null);
      return;
    }

    if (entryIDList.isDefined())
    {
      if(rootContainer.getMonitorProvider().isFilterUseEnabled())
      {
        rootContainer.getMonitorProvider().updateIndexedSearchCount();
      }
      searchIndexed(entryIDList, candidatesAreInScope, searchOperation,
          pageRequest);
    }
    else
    {
      if(rootContainer.getMonitorProvider().isFilterUseEnabled())
      {
        rootContainer.getMonitorProvider().updateUnindexedSearchCount();
      }

      searchOperation.addAdditionalLogItem(
          AdditionalLogItem.keyOnly(getClass(), "unindexed"));

      // See if we could use a virtual attribute rule to process the search.
      for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes())
      {
        if (rule.getProvider().isSearchable(rule, searchOperation, false))
        {
          rule.getProvider().processSearch(rule, searchOperation);
          return;
        }
      }

      ClientConnection clientConnection =
        searchOperation.getClientConnection();
      if(! clientConnection.hasPrivilege(Privilege.UNINDEXED_SEARCH,
          searchOperation))
      {
        Message message =
          ERR_JEB_SEARCH_UNINDEXED_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            message);
      }

      if (sortRequest != null)
      {
        // FIXME -- Add support for sorting unindexed searches using indexes
        //          like DSEE currently does.
        searchOperation.addResponseControl(
            new ServerSideSortResponseControl(
                LDAPResultCode.UNWILLING_TO_PERFORM, null));

        if (sortRequest.isCritical())
        {
          Message message = ERR_JEB_SEARCH_CANNOT_SORT_UNINDEXED.get();
          throw new DirectoryException(
              ResultCode.UNAVAILABLE_CRITICAL_EXTENSION, message);
        }
      }

      searchNotIndexed(searchOperation, pageRequest);
    }
  }

  /**
   * We were not able to obtain a set of candidate entry IDs for the
   * search from the indexes.
   * <p>
   * Here we are relying on the DN key order to ensure children are
   * returned after their parents.
   * <ul>
   * <li>iterate through a subtree range of the DN database
   * <li>discard non-children DNs if the search scope is single level
   * <li>fetch the entry by ID from the entry cache or the entry database
   * <li>return the entry if it matches the filter
   * </ul>
   *
   * @param searchOperation The search operation.
   * @param pageRequest A Paged Results control, or null if none.
   * @throws DirectoryException If an error prevented the search from being
   * processed.
   */
  private void searchNotIndexed(SearchOperation searchOperation,
      PagedResultsControl pageRequest)
  throws DirectoryException, CanceledOperationException
  {
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    DN aBaseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);

    // The base entry must already have been processed if this is
    // a request for the next page in paged results.  So we skip
    // the base entry processing if the cookie is set.
    if (pageRequest == null || pageRequest.getCookie().length() == 0)
    {
      // Fetch the base entry.
      Entry baseEntry = fetchBaseEntry(aBaseDN, searchScope);

      if (!manageDsaIT)
      {
        dn2uri.checkTargetForReferral(baseEntry, searchScope);
      }

      /*
       * The base entry is only included for whole subtree search.
       */
      if (searchScope == SearchScope.WHOLE_SUBTREE)
      {
        if (searchOperation.getFilter().matchesEntry(baseEntry))
        {
          searchOperation.returnEntry(baseEntry, null);
        }
      }

      if (!manageDsaIT)
      {
        // Return any search result references.
        if (!dn2uri.returnSearchReferences(searchOperation))
        {
          if (pageRequest != null)
          {
            // Indicate no more pages.
            PagedResultsControl control;
            control = new PagedResultsControl(pageRequest.isCritical(), 0,
                null);
            searchOperation.getResponseControls().add(control);
          }
        }
      }
    }

    /*
     * We will iterate forwards through a range of the dn2id keys to
     * find subordinates of the target entry from the top of the tree
     * downwards. For example, any subordinates of "dc=example,dc=com" appear
     * in dn2id with a key ending in ",dc=example,dc=com". The entry
     * "cn=joe,ou=people,dc=example,dc=com" will appear after the entry
     * "ou=people,dc=example,dc=com".
     */
    byte[] baseDNKey = JebFormat.dnToDNKey(aBaseDN,
                                             this.baseDN.getNumComponents());
    byte[] suffix = Arrays.copyOf(baseDNKey, baseDNKey.length+1);
    suffix[suffix.length-1] = 0x00;

    /*
     * Set the ending value to a value of equal length but slightly
     * greater than the suffix. Since keys are compared in
     * reverse order we must set the first byte (the comma).
     * No possibility of overflow here.
     */
    byte[] end = suffix.clone();
    end[end.length-1] = (byte) (end[end.length-1] + 1);

    // Set the starting value.
    byte[] begin;
    if (pageRequest != null && pageRequest.getCookie().length() != 0)
    {
      // The cookie contains the DN of the next entry to be returned.
      try
      {
        begin = pageRequest.getCookie().toByteArray();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        String str = pageRequest.getCookie().toHex();
        Message msg = ERR_JEB_INVALID_PAGED_RESULTS_COOKIE.get(str);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            msg, e);
      }
    }
    else
    {
      // Set the starting value to the suffix.
      begin = suffix;
    }

    DatabaseEntry data = new DatabaseEntry();
    DatabaseEntry key = new DatabaseEntry(begin);

    int lookthroughCount = 0;
    int lookthroughLimit =
      searchOperation.getClientConnection().getLookthroughLimit();

    try
    {
      Cursor cursor = dn2id.openCursor(null, null);
      try
      {
        OperationStatus status;

        // Initialize the cursor very close to the starting value.
        status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);

        // Step forward until we pass the ending value.
        while (status == OperationStatus.SUCCESS)
        {
          if(lookthroughLimit > 0 && lookthroughCount > lookthroughLimit)
          {
            //Lookthrough limit exceeded
            searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
            searchOperation.appendErrorMessage(
                NOTE_JEB_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
            return;
          }
          int cmp = dn2id.getComparator().compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate entry.

          EntryID entryID = new EntryID(data);

          boolean isInScope = true;
          if (searchScope == SearchScope.SINGLE_LEVEL)
          {
            // Check if this entry is an immediate child.
            if(JebFormat.findDNKeyParent(key.getData(), 0,
                                       key.getSize()) != baseDNKey.length)
            {
              isInScope = false;
            }
          }

          if (isInScope)
          {
            Entry entry;
            Entry cacheEntry;

            // Try the entry cache first.
            cacheEntry = entryCache.getEntry(backend, entryID.longValue());

            if (cacheEntry == null)
            {
              entry = id2entry.get(null, entryID, LockMode.DEFAULT);
            }
            else
            {
              entry = cacheEntry;
            }

            // Process the candidate entry.
            if (entry != null)
            {
              lookthroughCount++;

              if (manageDsaIT || entry.getReferralURLs() == null)
              {
                // Filter the entry.
                if (searchOperation.getFilter().matchesEntry(entry))
                {
                  if (pageRequest != null &&
                      searchOperation.getEntriesSent() ==
                        pageRequest.getSize())
                  {
                    // The current page is full.
                    // Set the cookie to remember where we were.
                    ByteString cookie = ByteString.wrap(key.getData());
                    PagedResultsControl control;
                    control = new PagedResultsControl(pageRequest.isCritical(),
                        0, cookie);
                    searchOperation.getResponseControls().add(control);
                    return;
                  }

                  if (!searchOperation.returnEntry(entry, null))
                  {
                    // We have been told to discontinue processing of the
                    // search. This could be due to size limit exceeded or
                    // operation cancelled.
                    return;
                  }
                }
              }
            }
          }

          searchOperation.checkIfCanceled(false);

          // Move to the next record.
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    if (pageRequest != null)
    {
      // Indicate no more pages.
      PagedResultsControl control;
      control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
      searchOperation.getResponseControls().add(control);
    }

  }

  /**
   * We were able to obtain a set of candidate entry IDs for the
   * search from the indexes.
   * <p>
   * Here we are relying on ID order to ensure children are returned
   * after their parents.
   * <ul>
   * <li>Iterate through the candidate IDs
   * <li>fetch entry by ID from cache or id2entry
   * <li>put the entry in the cache if not present
   * <li>discard entries that are not in scope
   * <li>return entry if it matches the filter
   * </ul>
   *
   * @param entryIDList The candidate entry IDs.
   * @param candidatesAreInScope true if it is certain that every candidate
   *                             entry is in the search scope.
   * @param searchOperation The search operation.
   * @param pageRequest A Paged Results control, or null if none.
   * @throws DirectoryException If an error prevented the search from being
   * processed.
   */
  private void searchIndexed(EntryIDSet entryIDList,
      boolean candidatesAreInScope,
      SearchOperation searchOperation,
      PagedResultsControl pageRequest)
  throws DirectoryException, CanceledOperationException
  {
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    SearchScope searchScope = searchOperation.getScope();
    DN aBaseDN = searchOperation.getBaseDN();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);
    boolean continueSearch = true;

    // Set the starting value.
    EntryID begin = null;
    if (pageRequest != null && pageRequest.getCookie().length() != 0)
    {
      // The cookie contains the ID of the next entry to be returned.
      try
      {
        begin = new EntryID(pageRequest.getCookie().toLong());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        String str = pageRequest.getCookie().toHex();
        Message msg = ERR_JEB_INVALID_PAGED_RESULTS_COOKIE.get(str);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            msg, e);
      }
    }
    else
    {
      if (!manageDsaIT)
      {
        // Return any search result references.
        continueSearch = dn2uri.returnSearchReferences(searchOperation);
      }
    }

    // Make sure the candidate list is smaller than the lookthrough limit
    int lookthroughLimit =
      searchOperation.getClientConnection().getLookthroughLimit();
    if(lookthroughLimit > 0 && entryIDList.size() > lookthroughLimit)
    {
      //Lookthrough limit exceeded
      searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
      searchOperation.appendErrorMessage(
          NOTE_JEB_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
      continueSearch = false;
    }

    // Iterate through the index candidates.
    if (continueSearch)
    {
      Iterator<EntryID> iterator = entryIDList.iterator(begin);
      while (iterator.hasNext())
      {
        EntryID id = iterator.next();
        Entry entry;

        // Try the entry cache first.
        Entry cacheEntry = entryCache.getEntry(backend, id.longValue());
        if (cacheEntry == null)
        {
          // Fetch the candidate entry from the database.
          try
          {
            entry = id2entry.get(null, id, LockMode.DEFAULT);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            continue;
          }
        }
        else
        {
          entry = cacheEntry;
        }

        // Process the candidate entry.
        if (entry != null)
        {
          boolean isInScope = false;
          DN entryDN = entry.getDN();

          if (candidatesAreInScope)
          {
            isInScope = true;
          }
          else if (searchScope == SearchScope.SINGLE_LEVEL)
          {
            // Check if this entry is an immediate child.
            if ((entryDN.getNumComponents() ==
                aBaseDN.getNumComponents() + 1) &&
                entryDN.isDescendantOf(aBaseDN))
            {
              isInScope = true;
            }
          }
          else if (searchScope == SearchScope.WHOLE_SUBTREE)
          {
            if (entryDN.isDescendantOf(aBaseDN))
            {
              isInScope = true;
            }
          }
          else if (searchScope == SearchScope.SUBORDINATE_SUBTREE)
          {
            if ((entryDN.getNumComponents() >
            aBaseDN.getNumComponents()) &&
            entryDN.isDescendantOf(aBaseDN))
            {
              isInScope = true;
            }
          }

          // Put this entry in the cache if it did not come from the cache.
          if (cacheEntry == null)
          {
            // Put the entry in the cache making sure not to overwrite
            // a newer copy that may have been inserted since the time
            // we read the cache.
            entryCache.putEntryIfAbsent(entry, backend, id.longValue());
          }

          // Filter the entry if it is in scope.
          if (isInScope)
          {
            if (manageDsaIT || entry.getReferralURLs() == null)
            {
              if (searchOperation.getFilter().matchesEntry(entry))
              {
                if (pageRequest != null &&
                    searchOperation.getEntriesSent() ==
                    pageRequest.getSize())
                {
                  // The current page is full.
                  // Set the cookie to remember where we were.
                  byte[] cookieBytes = id.getDatabaseEntry().getData();
                  ByteString cookie = ByteString.wrap(cookieBytes);
                  PagedResultsControl control;
                  control = new PagedResultsControl(pageRequest.isCritical(),
                      0, cookie);
                  searchOperation.getResponseControls().add(control);
                  return;
                }

                if (!searchOperation.returnEntry(entry, null))
                {
                  // We have been told to discontinue processing of the
                  // search. This could be due to size limit exceeded or
                  // operation cancelled.
                  break;
                }
              }
            }
          }
        }
      }
      searchOperation.checkIfCanceled(false);
    }

    // Before we return success from the search we must ensure the base entry
    // exists. However, if we have returned at least one entry or subordinate
    // reference it implies the base does exist, so we can omit the check.
    if (searchOperation.getEntriesSent() == 0 &&
        searchOperation.getReferencesSent() == 0)
    {
      // Fetch the base entry if it exists.
      Entry baseEntry = fetchBaseEntry(aBaseDN, searchScope);

      if (!manageDsaIT)
      {
        dn2uri.checkTargetForReferral(baseEntry, searchScope);
      }
    }

    if (pageRequest != null)
    {
      // Indicate no more pages.
      PagedResultsControl control;
      control = new PagedResultsControl(pageRequest.isCritical(), 0, null);
      searchOperation.getResponseControls().add(control);
    }

  }

  /**
   * Adds the provided entry to this database.  This method must ensure that the
   * entry is appropriate for the database and that no entry already exists with
   * the same DN.  The caller must hold a write lock on the DN of the provided
   * entry.
   *
   * @param entry        The entry to add to this database.
   * @param addOperation The add operation with which the new entry is
   *                     associated.  This may be <CODE>null</CODE> for adds
   *                     performed internally.
   * @throws DirectoryException If a problem occurs while trying to add the
   *                            entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
  throws DatabaseException, DirectoryException, CanceledOperationException
  {
    Transaction txn = beginTransaction();
    DN parentDN = getParentWithinBase(entry.getDN());

    try
    {
      // Check whether the entry already exists.
      if (dn2id.get(txn, entry.getDN(), LockMode.DEFAULT) != null)
      {
        Message message =
          ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
            message);
      }

      // Check that the parent entry exists.
      EntryID parentID = null;
      if (parentDN != null)
      {
        // Check for referral entries above the target.
        dn2uri.targetEntryReferrals(entry.getDN(), null);

        // Read the parent ID from dn2id.
        parentID = dn2id.get(txn, parentDN, LockMode.DEFAULT);
        if (parentID == null)
        {
          Message message = ERR_JEB_ADD_NO_SUCH_OBJECT.get(
              entry.getDN().toString());
          DN matchedDN = getMatchedDN(baseDN);
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              message, matchedDN, null);
        }
      }

      EntryID entryID = rootContainer.getNextEntryID();

      // Insert into dn2id.
      if (!dn2id.insert(txn, entry.getDN(), entryID))
      {
        // Do not ever expect to come through here.
        Message message =
          ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
            message);
      }

      // Update the referral database for referral entries.
      if (!dn2uri.addEntry(txn, entry))
      {
        // Do not ever expect to come through here.
        Message message =
          ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
            message);
      }

      // Insert into id2entry.
      if (!id2entry.insert(txn, entryID, entry))
      {
        // Do not ever expect to come through here.
        Message message =
          ERR_JEB_ADD_ENTRY_ALREADY_EXISTS.get(entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
            message);
      }

      // Insert into the indexes, in index configuration order.
      indexInsertEntry(txn, entry, entryID);

      // Insert into id2children and id2subtree.
      // The database transaction locks on these records will be hotly
      // contested so we do them last so as to hold the locks for the
      // shortest duration.
      if (parentDN != null)
      {
        // Insert into id2children for parent ID.
        id2children.insertID(txn, parentID.getDatabaseEntry(), entryID);

        // Insert into id2subtree for parent ID.
        id2subtree.insertID(txn, parentID.getDatabaseEntry(), entryID);

        // Iterate up through the superior entries, starting above the parent.
        for (DN dn = getParentWithinBase(parentDN); dn != null;
        dn = getParentWithinBase(dn))
        {
          // Read the ID from dn2id.
          EntryID nodeID = dn2id.get(txn, dn, LockMode.DEFAULT);
          if (nodeID == null)
          {
            Message msg =
              ERR_JEB_MISSING_DN2ID_RECORD.get(dn.toNormalizedString());
            throw new JebException(msg);
          }

          // Insert into id2subtree for this node.
          id2subtree.insertID(txn, nodeID.getDatabaseEntry(), entryID);
        }
      }

      if(addOperation != null)
      {
        // One last check before committing
        addOperation.checkIfCanceled(true);
      }

      // Commit the transaction.
      EntryContainer.transactionCommit(txn);

      // Update the entry cache.
      EntryCache<?> entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.putEntry(entry, backend, entryID.longValue());
      }
    }
    catch (DatabaseException databaseException)
    {
      EntryContainer.transactionAbort(txn);
      throw databaseException;
    }
    catch (DirectoryException directoryException)
    {
      EntryContainer.transactionAbort(txn);
      throw directoryException;
    }
    catch (CanceledOperationException coe)
    {
      EntryContainer.transactionAbort(txn);
      throw coe;
    }
    catch (Exception e)
    {
      EntryContainer.transactionAbort(txn);

      String msg = e.getMessage();
      if (msg == null)
      {
        msg = stackTraceToSingleLineString(e);
      }
      Message message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }
  }

  /**
   * Removes the specified entry from this database.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the database supports a subtree delete operation and the client
   * included the appropriate information in the request).  The caller must hold
   * a write lock on the provided entry DN.
   *
   * @param entryDN         The DN of the entry to remove from this database.
   * @param deleteOperation The delete operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        deletes performed internally.
   * @throws DirectoryException If a problem occurs while trying to remove the
   *                            entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
  throws DirectoryException, DatabaseException, CanceledOperationException
  {
    Transaction txn = beginTransaction();
    IndexBuffer indexBuffer = null;

    try
    {
      // Check for referral entries above the target entry.
      dn2uri.targetEntryReferrals(entryDN, null);

      // Determine whether this is a subtree delete.
      boolean isSubtreeDelete = false;

      if (deleteOperation != null
          && deleteOperation
              .getRequestControl(SubtreeDeleteControl.DECODER) != null)
      {
        isSubtreeDelete = true;
      }

      /*
       * We will iterate forwards through a range of the dn2id keys to
       * find subordinates of the target entry from the top of the tree
       * downwards.
       */
      byte[] entryDNKey = JebFormat.dnToDNKey(entryDN,
                                               this.baseDN.getNumComponents());
      byte[] suffix = Arrays.copyOf(entryDNKey, entryDNKey.length+1);
      suffix[suffix.length-1] = 0x00;

      /*
       * Set the ending value to a value of equal length but slightly
       * greater than the suffix.
       */
      byte[] end = suffix.clone();
      end[end.length-1] = (byte) (end[end.length-1] + 1);

      int subordinateEntriesDeleted = 0;

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key = new DatabaseEntry(suffix);

      CursorConfig cursorConfig = new CursorConfig();
      cursorConfig.setReadCommitted(true);
      Cursor cursor = dn2id.openCursor(txn, cursorConfig);
      try
      {
        OperationStatus status;

        // Initialize the cursor very close to the starting value.
        status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);

        // Step forward until the key is greater than the starting value.
        while (status == OperationStatus.SUCCESS &&
            dn2id.getComparator().compare(key.getData(), suffix) <= 0)
        {
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }

        // Step forward until we pass the ending value.
        while (status == OperationStatus.SUCCESS)
        {
          int cmp = dn2id.getComparator().compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate entry.
          if (!isSubtreeDelete)
          {
            // The subtree delete control was not specified and
            // the target entry is not a leaf.
            Message message =
              ERR_JEB_DELETE_NOT_ALLOWED_ON_NONLEAF.get(entryDN.toString());
            throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
                message);
          }

          // This is a subtree delete so create a index buffer
          // if it there isn't one.
          if(indexBuffer == null)
          {
            indexBuffer = new IndexBuffer(EntryContainer.this);
          }

          /*
           * Delete this entry which by now must be a leaf because
           * we have been deleting from the bottom of the tree upwards.
           */
          EntryID entryID = new EntryID(data);

          // Invoke any subordinate delete plugins on the entry.
          if ((deleteOperation != null) &&
              !deleteOperation.isSynchronizationOperation())
          {
            Entry subordinateEntry = id2entry.get(
                    txn, entryID, LockMode.DEFAULT);
            PluginConfigManager pluginManager =
              DirectoryServer.getPluginConfigManager();
            PluginResult.SubordinateDelete pluginResult =
              pluginManager.invokeSubordinateDeletePlugins(
                  deleteOperation, subordinateEntry);

            if (!pluginResult.continueProcessing())
            {
              Message message =
                      ERR_JEB_DELETE_ABORTED_BY_SUBORDINATE_PLUGIN.get(
                      JebFormat.dnFromDNKey(key.getData(), 0, 0, getBaseDN()).
                          toString());
              throw new DirectoryException(
                  DirectoryServer.getServerErrorResultCode(), message);
            }
          }

          deleteEntry(txn, indexBuffer, true, entryDN, key, entryID);
          subordinateEntriesDeleted++;

          if(deleteOperation != null)
          {
            deleteOperation.checkIfCanceled(false);
          }

          // Get the next DN.
          data = new DatabaseEntry();
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }
      }
      finally
      {
        cursor.close();
      }

      // draft-armijo-ldap-treedelete, 4.1 Tree Delete Semantics:
      // The server MUST NOT chase referrals stored in the tree.  If
      // information about referrals is stored in this section of the
      // tree, this pointer will be deleted.
      deleteEntry(txn, indexBuffer,
          isSubtreeDelete || isManageDsaITOperation(deleteOperation),
          entryDN, null, null);


      if(indexBuffer != null)
      {
        indexBuffer.flush(txn);
      }


      if(deleteOperation != null)
      {
        // One last check before committing
        deleteOperation.checkIfCanceled(true);
      }

      // Commit the transaction.
      EntryContainer.transactionCommit(txn);

      if(isSubtreeDelete)
      {
        deleteOperation.addAdditionalLogItem(AdditionalLogItem
            .unquotedKeyValue(getClass(), "deletedEntries",
                subordinateEntriesDeleted + 1));
      }
    }
    catch (DatabaseException databaseException)
    {
      EntryContainer.transactionAbort(txn);
      throw databaseException;
    }
    catch (DirectoryException directoryException)
    {
      EntryContainer.transactionAbort(txn);
      throw directoryException;
    }
    catch (CanceledOperationException coe)
    {
      EntryContainer.transactionAbort(txn);
      throw coe;
    }
    catch (Exception e)
    {
      EntryContainer.transactionAbort(txn);

      String msg = e.getMessage();
      if (msg == null)
      {
        msg = stackTraceToSingleLineString(e);
      }
      Message message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }
  }

  private void deleteEntry(Transaction txn,
      IndexBuffer indexBuffer,
      boolean manageDsaIT,
      DN targetDN,
      DatabaseEntry leafDNKey,
      EntryID leafID)
  throws DatabaseException, DirectoryException, JebException
  {
    if(leafID == null || leafDNKey == null)
    {
      // Read the entry ID from dn2id.
      if(leafDNKey == null)
      {
        leafDNKey =
            new DatabaseEntry(JebFormat.dnToDNKey(
                targetDN, this.baseDN.getNumComponents()));
      }
      DatabaseEntry value = new DatabaseEntry();
      OperationStatus status;
      status = dn2id.read(txn, leafDNKey, value, LockMode.RMW);
      if (status != OperationStatus.SUCCESS)
      {
        Message message =
          ERR_JEB_DELETE_NO_SUCH_OBJECT.get(leafDNKey.toString());
        DN matchedDN = getMatchedDN(baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
      }
      leafID = new EntryID(value);
    }

    // Remove from dn2id.
    if (dn2id.delete(txn, leafDNKey) != OperationStatus.SUCCESS)
    {
      // Do not expect to ever come through here.
      Message message = ERR_JEB_DELETE_NO_SUCH_OBJECT.get(leafDNKey.toString());
      DN matchedDN = getMatchedDN(baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          message, matchedDN, null);
    }

    // Check that the entry exists in id2entry and read its contents.
    Entry entry = id2entry.get(txn, leafID, LockMode.RMW);
    if (entry == null)
    {
      Message msg = ERR_JEB_MISSING_ID2ENTRY_RECORD.get(leafID.toString());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          msg);
    }

    if (!manageDsaIT)
    {
      dn2uri.checkTargetForReferral(entry, null);
    }

    // Update the referral database.
    dn2uri.deleteEntry(txn, entry);

    // Remove from id2entry.
    if (!id2entry.remove(txn, leafID))
    {
      Message msg = ERR_JEB_MISSING_ID2ENTRY_RECORD.get(leafID.toString());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          msg);
    }

    // Remove from the indexes, in index config order.
    if(indexBuffer != null)
    {
      indexRemoveEntry(indexBuffer, entry, leafID);
    }
    else
    {
      indexRemoveEntry(txn, entry, leafID);
    }

    // Remove the id2c and id2s records for this entry.
    if(indexBuffer != null)
    {
      byte[] leafIDKeyBytes =
        JebFormat.entryIDToDatabase(leafID.longValue());
      id2children.delete(indexBuffer, leafIDKeyBytes);
      id2subtree.delete(indexBuffer, leafIDKeyBytes);
    }
    else
    {
      DatabaseEntry leafIDKey = leafID.getDatabaseEntry();
      id2children.delete(txn, leafIDKey);
      id2subtree.delete(txn, leafIDKey);
    }

    // Iterate up through the superior entries from the target entry.
    boolean isParent = true;
    for (DN parentDN = getParentWithinBase(targetDN); parentDN != null;
    parentDN = getParentWithinBase(parentDN))
    {
      // Read the ID from dn2id.
      EntryID parentID = dn2id.get(txn, parentDN, LockMode.DEFAULT);
      if (parentID == null)
      {
        Message msg =
          ERR_JEB_MISSING_DN2ID_RECORD.get(parentDN.toNormalizedString());
        throw new JebException(msg);
      }

      if(indexBuffer != null)
      {
        byte[] parentIDBytes =
          JebFormat.entryIDToDatabase(parentID.longValue());
        // Remove from id2children.
        if (isParent)
        {
          id2children.removeID(indexBuffer, parentIDBytes, leafID);
          isParent = false;
        }
        id2subtree.removeID(indexBuffer, parentIDBytes, leafID);
      }
      else
      {
        DatabaseEntry nodeIDData = parentID.getDatabaseEntry();
        // Remove from id2children.
        if(isParent)
        {
          id2children.removeID(txn, nodeIDData, leafID);
          isParent = false;
        }
        id2subtree.removeID(txn, nodeIDData, leafID);
      }
    }

    // Remove the entry from the entry cache.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.removeEntry(entry.getDN());
    }
  }

  /**
   * Indicates whether an entry with the specified DN exists.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  public boolean entryExists(DN entryDN)
  throws DirectoryException
  {
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();

    // Try the entry cache first.
    if (entryCache != null)
    {
      if (entryCache.containsEntry(entryDN))
      {
        return true;
      }
    }

    // Read the ID from dn2id.
    EntryID id = null;
    try
    {
      id = dn2id.get(null, entryDN, LockMode.DEFAULT);
    }
    catch (DatabaseException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return id != null;
  }

  /**
   * Fetch an entry by DN, trying the entry cache first, then the database.
   * Retrieves the requested entry, trying the entry cache first,
   * then the database.  Note that the caller must hold a read or write lock
   * on the specified DN.
   *
   * @param entryDN The distinguished name of the entry to retrieve.
   * @return The requested entry, or <CODE>null</CODE> if the entry does not
   *         exist.
   * @throws DirectoryException If a problem occurs while trying to retrieve
   *                            the entry.
   * @throws DatabaseException An error occurred during a database operation.
   */
  public Entry getEntry(DN entryDN)
  throws DatabaseException, DirectoryException
  {
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    Entry entry = null;

    // Try the entry cache first.
    if (entryCache != null)
    {
      entry = entryCache.getEntry(entryDN);
    }

    if (entry == null)
    {
      // Read dn2id.
      EntryID entryID = dn2id.get(null, entryDN, LockMode.DEFAULT);
      if (entryID == null)
      {
        // The entryDN does not exist.

        // Check for referral entries above the target entry.
        dn2uri.targetEntryReferrals(entryDN, null);

        return null;
      }

      // Read id2entry.
      entry = id2entry.get(null, entryID, LockMode.DEFAULT);

      if (entry == null)
      {
        // The entryID does not exist.
        Message msg = ERR_JEB_MISSING_ID2ENTRY_RECORD.get(entryID.toString());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
            msg);
      }

      // Put the entry in the cache making sure not to overwrite
      // a newer copy that may have been inserted since the time
      // we read the cache.
      if (entryCache != null)
      {
        entryCache.putEntryIfAbsent(entry, backend, entryID.longValue());
      }
    }

    return entry;
  }

  /**
   * The simplest case of replacing an entry in which the entry DN has
   * not changed.
   *
   * @param oldEntry           The old contents of the entry
   * @param newEntry           The new contents of the entry
   * @param modifyOperation The modify operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        modifications performed internally.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws CanceledOperationException if this operation should be cancelled.
   */
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DatabaseException,
      DirectoryException, CanceledOperationException
      {
    Transaction txn = beginTransaction();

    try
    {
      // Read dn2id.
      EntryID entryID = dn2id.get(txn, newEntry.getDN(), LockMode.RMW);
      if (entryID == null)
      {
        // The entry does not exist.
        Message message =
          ERR_JEB_MODIFY_NO_SUCH_OBJECT.get(newEntry.getDN().toString());
        DN matchedDN = getMatchedDN(baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
      }

      if (!isManageDsaITOperation(modifyOperation))
      {
        // Check if the entry is a referral entry.
        dn2uri.checkTargetForReferral(oldEntry, null);
      }

      // Update the referral database.
      if (modifyOperation != null)
      {
        // In this case we know from the operation what the modifications were.
        List<Modification> mods = modifyOperation.getModifications();
        dn2uri.modifyEntry(txn, oldEntry, newEntry, mods);
      }
      else
      {
        dn2uri.replaceEntry(txn, oldEntry, newEntry);
      }

      // Replace id2entry.
      id2entry.put(txn, entryID, newEntry);

      // Update the indexes.
      if (modifyOperation != null)
      {
        // In this case we know from the operation what the modifications were.
        List<Modification> mods = modifyOperation.getModifications();
        indexModifications(txn, oldEntry, newEntry, entryID, mods);
      }
      else
      {
        // The most optimal would be to figure out what the modifications were.
        indexRemoveEntry(txn, oldEntry, entryID);
        indexInsertEntry(txn, newEntry, entryID);
      }

      if(modifyOperation != null)
      {
        // One last check before committing
        modifyOperation.checkIfCanceled(true);
      }

      // Commit the transaction.
      EntryContainer.transactionCommit(txn);

      // Update the entry cache.
      EntryCache<?> entryCache = DirectoryServer.getEntryCache();
      if (entryCache != null)
      {
        entryCache.putEntry(newEntry, backend, entryID.longValue());
      }
    }
    catch (DatabaseException databaseException)
    {
      EntryContainer.transactionAbort(txn);
      throw databaseException;
    }
    catch (DirectoryException directoryException)
    {
      EntryContainer.transactionAbort(txn);
      throw directoryException;
    }
    catch (CanceledOperationException coe)
    {
      EntryContainer.transactionAbort(txn);
      throw coe;
    }
    catch (Exception e)
    {
      EntryContainer.transactionAbort(txn);

      String msg = e.getMessage();
      if (msg == null)
      {
        msg = stackTraceToSingleLineString(e);
      }
      Message message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }
      }

  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.  The caller must hold write locks on both
   * the current DN and the new DN for the entry.
   *
   * @param currentDN         The current DN of the entry to be replaced.
   * @param entry             The new content to use for the entry.
   * @param modifyDNOperation The modify DN operation with which this action
   *                          is associated.  This may be <CODE>null</CODE>
   *                          for modify DN operations performed internally.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs while trying to perform
   *          the rename.
   * @throws org.opends.server.types.CanceledOperationException
   *          If this backend noticed and reacted
   *          to a request to cancel or abandon the
   *          modify DN operation.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void renameEntry(DN currentDN, Entry entry,
      ModifyDNOperation modifyDNOperation)
  throws DatabaseException, DirectoryException, CanceledOperationException
  {
    Transaction txn = beginTransaction();
    DN oldSuperiorDN = getParentWithinBase(currentDN);
    DN newSuperiorDN = getParentWithinBase(entry.getDN());
    boolean isApexEntryMoved;

    if(oldSuperiorDN != null)
    {
      isApexEntryMoved = ! oldSuperiorDN.equals(newSuperiorDN);
    }
    else if(newSuperiorDN != null)
    {
      isApexEntryMoved = ! newSuperiorDN.equals(oldSuperiorDN);
    }
    else
    {
      isApexEntryMoved = false;
    }

    IndexBuffer buffer = new IndexBuffer(EntryContainer.this);

    try
    {
      // Check whether the renamed entry already exists.
      if (!currentDN.equals(entry.getDN()) &&
          dn2id.get(txn, entry.getDN(), LockMode.DEFAULT) != null)
      {
        Message message = ERR_JEB_MODIFYDN_ALREADY_EXISTS.get(
            entry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
            message);
      }

      EntryID oldApexID = dn2id.get(txn, currentDN, LockMode.DEFAULT);
      if (oldApexID == null)
      {
        // Check for referral entries above the target entry.
        dn2uri.targetEntryReferrals(currentDN, null);

        Message message =
          ERR_JEB_MODIFYDN_NO_SUCH_OBJECT.get(currentDN.toString());
        DN matchedDN = getMatchedDN(baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
      }

      Entry oldApexEntry = id2entry.get(txn, oldApexID, LockMode.DEFAULT);
      if (oldApexEntry == null)
      {
        Message msg = ERR_JEB_MISSING_ID2ENTRY_RECORD.get(oldApexID.toString());
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), msg);
      }

      if (!isManageDsaITOperation(modifyDNOperation))
      {
        dn2uri.checkTargetForReferral(oldApexEntry, null);
      }

      EntryID newApexID = oldApexID;
      if (newSuperiorDN != null && isApexEntryMoved)
      {
        /*
         * We want to preserve the invariant that the ID of an
         * entry is greater than its parent, since search
         * results are returned in ID order.
         */
        EntryID newSuperiorID = dn2id.get(txn, newSuperiorDN, LockMode.DEFAULT);
        if (newSuperiorID == null)
        {
          Message msg =
            ERR_JEB_NEW_SUPERIOR_NO_SUCH_OBJECT.get(
                newSuperiorDN.toString());
          DN matchedDN = getMatchedDN(baseDN);
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              msg, matchedDN, null);
        }

        if (newSuperiorID.compareTo(oldApexID) > 0)
        {
          // This move would break the above invariant so we must
          // renumber every entry that moves. This is even more
          // expensive since every entry has to be deleted from
          // and added back into the attribute indexes.
          newApexID = rootContainer.getNextEntryID();

          if(debugEnabled())
          {
            TRACER.debugInfo("Move of target entry requires renumbering" +
                "all entries in the subtree. " +
                "Old DN: %s " +
                "New DN: %s " +
                "Old entry ID: %d " +
                "New entry ID: %d " +
                "New Superior ID: %d" +
                oldApexEntry.getDN(), entry.getDN(),
                oldApexID.longValue(), newApexID.longValue(),
                newSuperiorID.longValue());
          }
        }
      }

      MovedEntry head = new MovedEntry(null, null, false);
      MovedEntry current = head;
      // Move or rename the apex entry.
      removeApexEntry(txn, buffer, oldSuperiorDN, oldApexID,
          newApexID, oldApexEntry, entry,isApexEntryMoved, modifyDNOperation,
          current);
      current = current.next;

      /*
       * We will iterate forwards through a range of the dn2id keys to
       * find subordinates of the target entry from the top of the tree
       * downwards.
       */
      byte[] currentDNKey = JebFormat.dnToDNKey(currentDN,
                                               this.baseDN.getNumComponents());
      byte[] suffix = Arrays.copyOf(currentDNKey, currentDNKey.length+1);
      suffix[suffix.length-1] = 0x00;

      /*
       * Set the ending value to a value of equal length but slightly
       * greater than the suffix.
       */
      byte[] end = suffix.clone();
      end[end.length-1] = (byte) (end[end.length-1] + 1);

      DatabaseEntry data = new DatabaseEntry();
      DatabaseEntry key = new DatabaseEntry(suffix);

      CursorConfig cursorConfig = new CursorConfig();
      cursorConfig.setReadCommitted(true);
      Cursor cursor = dn2id.openCursor(txn, cursorConfig);
      try
      {
        OperationStatus status;

        // Initialize the cursor very close to the starting value.
        status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);

        // Step forward until the key is greater than the starting value.
        while (status == OperationStatus.SUCCESS &&
            dn2id.getComparator().compare(key.getData(), suffix) <= 0)
        {
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }

        // Step forward until we pass the ending value.
        while (status == OperationStatus.SUCCESS)
        {
          int cmp = dn2id.getComparator().compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate entry.

          EntryID oldID = new EntryID(data);
          Entry oldEntry = id2entry.get(txn, oldID, LockMode.DEFAULT);

          // Construct the new DN of the entry.
          DN newDN = modDN(oldEntry.getDN(),
              currentDN.getNumComponents(),
              entry.getDN());

          // Assign a new entry ID if we are renumbering.
          EntryID newID = oldID;
          if (!newApexID.equals(oldApexID))
          {
            newID = rootContainer.getNextEntryID();

            if(debugEnabled())
            {
              TRACER.debugInfo("Move of subordinate entry requires " +
                  "renumbering. " +
                  "Old DN: %s " +
                  "New DN: %s " +
                  "Old entry ID: %d " +
                  "New entry ID: %d",
                  oldEntry.getDN(), newDN, oldID.longValue(),
                  newID.longValue());
            }
          }

          // Move this entry.
          removeSubordinateEntry(txn, buffer, oldSuperiorDN,
              oldID, newID, oldEntry, newDN, isApexEntryMoved,
              modifyDNOperation, current);
          current = current.next;

          if(modifyDNOperation != null)
          {
            modifyDNOperation.checkIfCanceled(false);
          }

          // Get the next DN.
          data = new DatabaseEntry();
          status = cursor.getNext(key, data, LockMode.DEFAULT);
        }
      }
      finally
      {
        cursor.close();
      }

      // Set current to the first moved entry and null out the head. This will
      // allow processed moved entries to be GCed.
      current = head.next;
      head = null;
      while(current != null)
      {
        addRenamedEntry(txn, buffer, current.entryID, current.entry,
                        isApexEntryMoved, current.renumbered,
                        modifyDNOperation);
        current = current.next;
      }
      buffer.flush(txn);

      if(modifyDNOperation != null)
      {
        // One last check before committing
        modifyDNOperation.checkIfCanceled(true);
      }

      // Commit the transaction.
      EntryContainer.transactionCommit(txn);
    }
    catch (DatabaseException databaseException)
    {
      EntryContainer.transactionAbort(txn);
      throw databaseException;
    }
    catch (DirectoryException directoryException)
    {
      EntryContainer.transactionAbort(txn);
      throw directoryException;
    }
    catch (CanceledOperationException coe)
    {
      EntryContainer.transactionAbort(txn);
      throw coe;
    }
    catch (Exception e)
    {
      EntryContainer.transactionAbort(txn);

      String msg = e.getMessage();
      if (msg == null)
      {
        msg = stackTraceToSingleLineString(e);
      }
      Message message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }
  }

  /**
   * Represents an renamed entry that was deleted from JE but yet to be added
   * back.
   */
  private static class MovedEntry
  {
    EntryID entryID;
    Entry entry;
    MovedEntry next;
    boolean renumbered;

    private MovedEntry(EntryID entryID, Entry entry, boolean renumbered)
    {
      this.entryID = entryID;
      this.entry = entry;
      this.renumbered = renumbered;
    }
  }

  private void addRenamedEntry(Transaction txn, IndexBuffer buffer,
                           EntryID newID,
                           Entry newEntry,
                           boolean isApexEntryMoved,
                           boolean renumbered,
                           ModifyDNOperation modifyDNOperation)
      throws DirectoryException, DatabaseException
  {
    if (!dn2id.insert(txn, newEntry.getDN(), newID))
    {
      Message message = ERR_JEB_MODIFYDN_ALREADY_EXISTS.get(
          newEntry.getDN().toString());
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                   message);
    }
    id2entry.put(txn, newID, newEntry);
    dn2uri.addEntry(txn, newEntry);

    if (renumbered || modifyDNOperation == null)
    {
      // Reindex the entry with the new ID.
      indexInsertEntry(buffer, newEntry, newID);
    }

    // Add the new ID to id2children and id2subtree of new apex parent entry.
    if(isApexEntryMoved)
    {
      EntryID parentID;
      byte[] parentIDKeyBytes;
      boolean isParent = true;
      for (DN dn = getParentWithinBase(newEntry.getDN()); dn != null;
           dn = getParentWithinBase(dn))
      {
        parentID = dn2id.get(txn, dn, LockMode.DEFAULT);
        parentIDKeyBytes =
            JebFormat.entryIDToDatabase(parentID.longValue());
        if(isParent)
        {
          id2children.insertID(buffer, parentIDKeyBytes, newID);
          isParent = false;
        }
        id2subtree.insertID(buffer, parentIDKeyBytes, newID);
      }
    }
  }

  private void removeApexEntry(Transaction txn, IndexBuffer buffer,
      DN oldSuperiorDN,
      EntryID oldID, EntryID newID,
      Entry oldEntry, Entry newEntry,
      boolean isApexEntryMoved,
      ModifyDNOperation modifyDNOperation,
      MovedEntry tail)
  throws DirectoryException, DatabaseException
  {
    DN oldDN = oldEntry.getDN();

    // Remove the old DN from dn2id.
    dn2id.remove(txn, oldDN);

    // Remove old ID from id2entry and put the new entry
    // (old entry with new DN) in id2entry.
    if (!newID.equals(oldID))
    {
      id2entry.remove(txn, oldID);
    }

    // Update any referral records.
    dn2uri.deleteEntry(txn, oldEntry);

    tail.next = new MovedEntry(newID, newEntry, !newID.equals(oldID));

    // Remove the old ID from id2children and id2subtree of
    // the old apex parent entry.
    if(oldSuperiorDN != null && isApexEntryMoved)
    {
      EntryID parentID;
      byte[] parentIDKeyBytes;
      boolean isParent = true;
      for (DN dn = oldSuperiorDN; dn != null; dn = getParentWithinBase(dn))
      {
        parentID = dn2id.get(txn, dn, LockMode.DEFAULT);
        parentIDKeyBytes =
          JebFormat.entryIDToDatabase(parentID.longValue());
        if(isParent)
        {
          id2children.removeID(buffer, parentIDKeyBytes, oldID);
          isParent = false;
        }
        id2subtree.removeID(buffer, parentIDKeyBytes, oldID);
      }
    }

    if (!newID.equals(oldID) || modifyDNOperation == null)
    {
      // All the subordinates will be renumbered so we have to rebuild
      // id2c and id2s with the new ID.
      byte[] oldIDKeyBytes = JebFormat.entryIDToDatabase(oldID.longValue());
      id2children.delete(buffer, oldIDKeyBytes);
      id2subtree.delete(buffer, oldIDKeyBytes);

      // Reindex the entry with the new ID.
      indexRemoveEntry(buffer, oldEntry, oldID);
    }
    else
    {
      // Update the indexes if needed.
      indexModifications(buffer, oldEntry, newEntry, oldID,
          modifyDNOperation.getModifications());
    }

    // Remove the entry from the entry cache.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.removeEntry(oldDN);
    }
  }

  private void removeSubordinateEntry(Transaction txn, IndexBuffer buffer,
      DN oldSuperiorDN,
      EntryID oldID, EntryID newID,
      Entry oldEntry, DN newDN,
      boolean isApexEntryMoved,
      ModifyDNOperation modifyDNOperation,
      MovedEntry tail)
  throws DirectoryException, DatabaseException
  {
    DN oldDN = oldEntry.getDN();
    Entry newEntry = oldEntry.duplicate(false);
    newEntry.setDN(newDN);
    List<Modification> modifications =
      Collections.unmodifiableList(new ArrayList<Modification>(0));

    // Create a new entry that is a copy of the old entry but with the new DN.
    // Also invoke any subordinate modify DN plugins on the entry.
    // FIXME -- At the present time, we don't support subordinate modify DN
    //          plugins that make changes to subordinate entries and therefore
    //          provide an unmodifiable list for the modifications element.
    // FIXME -- This will need to be updated appropriately if we decided that
    //          these plugins should be invoked for synchronization
    //          operations.
    if (! modifyDNOperation.isSynchronizationOperation())
    {
      PluginConfigManager pluginManager =
        DirectoryServer.getPluginConfigManager();
      PluginResult.SubordinateModifyDN pluginResult =
        pluginManager.invokeSubordinateModifyDNPlugins(
            modifyDNOperation, oldEntry, newEntry, modifications);

      if (!pluginResult.continueProcessing())
      {
        Message message = ERR_JEB_MODIFYDN_ABORTED_BY_SUBORDINATE_PLUGIN.get(
            oldDN.toString(), newDN.toString());
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), message);
      }

      if (! modifications.isEmpty())
      {
        MessageBuilder invalidReason = new MessageBuilder();
        if (! newEntry.conformsToSchema(null, false, false, false,
            invalidReason))
        {
          Message message =
            ERR_JEB_MODIFYDN_ABORTED_BY_SUBORDINATE_SCHEMA_ERROR.get(
                oldDN.toString(),
                newDN.toString(),
                invalidReason.toString());
          throw new DirectoryException(
              DirectoryServer.getServerErrorResultCode(), message);
        }
      }
    }

    // Remove the old DN from dn2id.
    dn2id.remove(txn, oldDN);

    // Remove old ID from id2entry and put the new entry
    // (old entry with new DN) in id2entry.
    if (!newID.equals(oldID))
    {
      id2entry.remove(txn, oldID);
    }

    // Update any referral records.
    dn2uri.deleteEntry(txn, oldEntry);

    tail.next = new MovedEntry(newID, newEntry, !newID.equals(oldID));

    if(isApexEntryMoved)
    {
      // Remove the old ID from id2subtree of old apex superior entries.
      for (DN dn = oldSuperiorDN; dn != null; dn = getParentWithinBase(dn))
      {
        EntryID parentID = dn2id.get(txn, dn, LockMode.DEFAULT);
        byte[] parentIDKeyBytes =
          JebFormat.entryIDToDatabase(parentID.longValue());
        id2subtree.removeID(buffer, parentIDKeyBytes, oldID);
      }
    }

    if (!newID.equals(oldID))
    {
      // All the subordinates will be renumbered so we have to rebuild
      // id2c and id2s with the new ID.
      byte[] oldIDKeyBytes = JebFormat.entryIDToDatabase(oldID.longValue());
      id2children.delete(buffer, oldIDKeyBytes);
      id2subtree.delete(buffer, oldIDKeyBytes);

      // Reindex the entry with the new ID.
      indexRemoveEntry(buffer, oldEntry, oldID);
    }
    else
    {
      // Update the indexes if needed.
      if(! modifications.isEmpty())
      {
        indexModifications(buffer, oldEntry, newEntry, oldID, modifications);
      }
    }

    // Remove the entry from the entry cache.
    EntryCache<?> entryCache = DirectoryServer.getEntryCache();
    if (entryCache != null)
    {
      entryCache.removeEntry(oldDN);
    }
  }

  /**
   * Make a new DN for a subordinate entry of a renamed or moved entry.
   *
   * @param oldDN The current DN of the subordinate entry.
   * @param oldSuffixLen The current DN length of the renamed or moved entry.
   * @param newSuffixDN The new DN of the renamed or moved entry.
   * @return The new DN of the subordinate entry.
   */
  public static DN modDN(DN oldDN, int oldSuffixLen, DN newSuffixDN)
  {
    int oldDNNumComponents    = oldDN.getNumComponents();
    int oldDNKeepComponents   = oldDNNumComponents - oldSuffixLen;
    int newSuffixDNComponents = newSuffixDN.getNumComponents();

    RDN[] newDNComponents = new RDN[oldDNKeepComponents+newSuffixDNComponents];
    for (int i=0; i < oldDNKeepComponents; i++)
    {
      newDNComponents[i] = oldDN.getRDN(i);
    }

    for (int i=oldDNKeepComponents, j=0; j < newSuffixDNComponents; i++,j++)
    {
      newDNComponents[i] = newSuffixDN.getRDN(j);
    }

    return new DN(newDNComponents);
  }

  /**
   * A lexicographic byte array comparator that compares in
   * reverse byte order. This is used for the dn2id database of version 2.2.
   * If we want to find all the entries in a subtree dc=com we know that
   * all subordinate entries must have ,dc=com as a common suffix. In reversing
   * the order of comparison we turn the subtree base into a common prefix
   * and are able to iterate through the keys having that prefix.
   * Keep in there to preserve ability to upgrade
   */
  static public class KeyReverseComparator implements Comparator<byte[]>
  {
    /**
     * Compares its two arguments for order.  Returns a negative integer,
     * zero, or a positive integer as the first argument is less than, equal
     * to, or greater than the second.
     *
     * @param a the first object to be compared.
     * @param b the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the
     *         first argument is less than, equal to, or greater than the
     *         second.
     */
    public int compare(byte[] a, byte[] b)
    {
      for (int ai = a.length - 1, bi = b.length - 1;
      ai >= 0 && bi >= 0; ai--, bi--)
      {
        if (a[ai] > b[bi])
        {
          return 1;
        }
        else if (a[ai] < b[bi])
        {
          return -1;
        }
      }
      if (a.length == b.length)
      {
        return 0;
      }
      if (a.length > b.length)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
  }

  /**
   * Insert a new entry into the attribute indexes.
   *
   * @param txn The database transaction to be used for the updates.
   * @param entry The entry to be inserted into the indexes.
   * @param entryID The ID of the entry to be inserted into the indexes.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void indexInsertEntry(Transaction txn, Entry entry, EntryID entryID)
  throws DatabaseException, DirectoryException, JebException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.addEntry(txn, entryID, entry);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.addEntry(txn, entryID, entry);
    }
  }

  /**
   * Insert a new entry into the attribute indexes.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param entry The entry to be inserted into the indexes.
   * @param entryID The ID of the entry to be inserted into the indexes.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void indexInsertEntry(IndexBuffer buffer, Entry entry,
      EntryID entryID)
  throws DatabaseException, DirectoryException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.addEntry(buffer, entryID, entry);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.addEntry(buffer, entryID, entry);
    }
  }

  /**
   * Remove an entry from the attribute indexes.
   *
   * @param txn The database transaction to be used for the updates.
   * @param entry The entry to be removed from the indexes.
   * @param entryID The ID of the entry to be removed from the indexes.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void indexRemoveEntry(Transaction txn, Entry entry, EntryID entryID)
  throws DatabaseException, DirectoryException, JebException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.removeEntry(txn, entryID, entry);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.removeEntry(txn, entryID, entry);
    }
  }

  /**
   * Remove an entry from the attribute indexes.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param entry The entry to be removed from the indexes.
   * @param entryID The ID of the entry to be removed from the indexes.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void indexRemoveEntry(IndexBuffer buffer, Entry entry,
      EntryID entryID)
  throws DatabaseException, DirectoryException
  {
    for (AttributeIndex index : attrIndexMap.values())
    {
      index.removeEntry(buffer, entryID, entry);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.removeEntry(buffer, entryID, entry);
    }
  }

  /**
   * Update the attribute indexes to reflect the changes to the
   * attributes of an entry resulting from a sequence of modifications.
   *
   * @param txn The database transaction to be used for the updates.
   * @param oldEntry The contents of the entry before the change.
   * @param newEntry The contents of the entry after the change.
   * @param entryID The ID of the entry that was changed.
   * @param mods The sequence of modifications made to the entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  private void indexModifications(Transaction txn, Entry oldEntry,
      Entry newEntry,
      EntryID entryID, List<Modification> mods)
  throws DatabaseException, DirectoryException, JebException
  {
    // Process in index configuration order.
    for (AttributeIndex index : attrIndexMap.values())
    {
      // Check whether any modifications apply to this indexed attribute.
      if (isAttributeModified(index, mods))
      {
        index.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
      }
    }

    for(VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
    }
  }

  /**
   * Update the attribute indexes to reflect the changes to the
   * attributes of an entry resulting from a sequence of modifications.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param oldEntry The contents of the entry before the change.
   * @param newEntry The contents of the entry after the change.
   * @param entryID The ID of the entry that was changed.
   * @param mods The sequence of modifications made to the entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  private void indexModifications(IndexBuffer buffer, Entry oldEntry,
      Entry newEntry,
      EntryID entryID, List<Modification> mods)
  throws DatabaseException, DirectoryException
  {
    // Process in index configuration order.
    for (AttributeIndex index : attrIndexMap.values())
    {
      // Check whether any modifications apply to this indexed attribute.
      if (isAttributeModified(index, mods))
      {
        index.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
      }
    }

    for(VLVIndex vlvIndex : vlvIndexMap.values())
    {
      vlvIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }
  }

  /**
   * Get a count of the number of entries stored in this entry entryContainer.
   *
   * @return The number of entries stored in this entry entryContainer.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public long getEntryCount() throws DatabaseException
  {
    EntryID entryID = dn2id.get(null, baseDN, LockMode.DEFAULT);
    if (entryID != null)
    {
      DatabaseEntry key =
        new DatabaseEntry(JebFormat.entryIDToDatabase(entryID.longValue()));
      EntryIDSet entryIDSet;
      entryIDSet = id2subtree.readKey(key, null, LockMode.DEFAULT);

      long count = entryIDSet.size();
      if(count != Long.MAX_VALUE)
      {
        // Add the base entry itself
        return ++count;
      }
      else
      {
        // The count is not maintained. Fall back to the slow method
        return id2entry.getRecordCount();
      }
    }
    else
    {
      // Base entry doesn't not exist so this entry container
      // must not have any entries
      return 0;
    }
  }

  /**
   * Get the number of values for which the entry limit has been exceeded
   * since the entry entryContainer was opened.
   * @return The number of values for which the entry limit has been exceeded.
   */
  public int getEntryLimitExceededCount()
  {
    int count = 0;
    count += id2children.getEntryLimitExceededCount();
    count += id2subtree.getEntryLimitExceededCount();
    for (AttributeIndex index : attrIndexMap.values())
    {
      count += index.getEntryLimitExceededCount();
    }
    return count;
  }


  /**
   * Get a list of the databases opened by the entryContainer.
   * @param dbList A list of database containers.
   */
  public void listDatabases(List<DatabaseContainer> dbList)
  {
    dbList.add(dn2id);
    dbList.add(id2entry);
    dbList.add(dn2uri);
    if (config.isSubordinateIndexesEnabled())
    {
      dbList.add(id2children);
      dbList.add(id2subtree);
    }
    dbList.add(state);

    for(AttributeIndex index : attrIndexMap.values())
    {
      index.listDatabases(dbList);
    }

    for (VLVIndex vlvIndex : vlvIndexMap.values())
    {
      dbList.add(vlvIndex);
    }
  }

  /**
   * Determine whether the provided operation has the ManageDsaIT request
   * control.
   * @param operation The operation for which the determination is to be made.
   * @return true if the operation has the ManageDsaIT request control, or false
   * if not.
   */
  public static boolean isManageDsaITOperation(Operation operation)
  {
    if(operation != null)
    {
      List<Control> controls = operation.getRequestControls();
      if (controls != null)
      {
        for (Control control : controls)
        {
          if (control.getOID().equals(ServerConstants.OID_MANAGE_DSAIT_CONTROL))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Begin a leaf transaction using the default configuration.
   * Provides assertion debug logging.
   * @return A JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to begin
   * a new transaction.
   */
  public Transaction beginTransaction()
  throws DatabaseException
  {
    Transaction parentTxn = null;
    TransactionConfig txnConfig = null;
    Transaction txn = env.beginTransaction(parentTxn, txnConfig);
    if (debugEnabled())
    {
      TRACER.debugVerbose("beginTransaction", "begin txnid=" + txn.getId());
    }
    return txn;
  }

  /**
   * Commit a transaction.
   * Provides assertion debug logging.
   * @param txn The JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to commit
   * the transaction.
   */
  public static void transactionCommit(Transaction txn)
  throws DatabaseException
  {
    if (txn != null)
    {
      txn.commit();
      if (debugEnabled())
      {
        TRACER.debugVerbose("commit txnid=%d", txn.getId());
      }
    }
  }

  /**
   * Abort a transaction.
   * Provides assertion debug logging.
   * @param txn The JE transaction handle.
   * @throws DatabaseException If an error occurs while attempting to abort the
   * transaction.
   */
  public static void transactionAbort(Transaction txn)
  throws DatabaseException
  {
    if (txn != null)
    {
      txn.abort();
      if (debugEnabled())
      {
        TRACER.debugVerbose("abort txnid=%d", txn.getId());
      }
    }
  }

  /**
   * Delete this entry container from disk. The entry container should be
   * closed before calling this method.
   *
   * @throws DatabaseException If an error occurs while removing the entry
   *                           container.
   */
  public void delete() throws DatabaseException
  {
    List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    listDatabases(databases);

    if(env.getConfig().getTransactional())
    {
      Transaction txn = beginTransaction();

      try
      {
        for(DatabaseContainer db : databases)
        {
          env.removeDatabase(txn, db.getName());
        }

        transactionCommit(txn);
      }
      catch(DatabaseException de)
      {
        transactionAbort(txn);
        throw de;
      }
    }
    else
    {
      for(DatabaseContainer db : databases)
      {
        env.removeDatabase(null, db.getName());
      }
    }
  }

  /**
   * Remove a database from disk.
   *
   * @param database The database container to remove.
   * @throws DatabaseException If an error occurs while attempting to delete the
   * database.
   */
  public void deleteDatabase(DatabaseContainer database)
  throws DatabaseException
  {
    if(database == state)
    {
      // The state database can not be removed individually.
      return;
    }

    database.close();
    if(env.getConfig().getTransactional())
    {
      Transaction txn = beginTransaction();
      try
      {
        env.removeDatabase(txn, database.getName());
        if(database instanceof Index)
        {
          state.removeIndexTrustState(txn, (Index)database);
        }
        transactionCommit(txn);
      }
      catch(DatabaseException de)
      {
        transactionAbort(txn);
        throw de;
      }
    }
    else
    {
      env.removeDatabase(null, database.getName());
      if(database instanceof Index)
      {
        state.removeIndexTrustState(null, (Index)database);
      }
    }
  }

  /**
   * Removes a attribute index from disk.
   *
   * @param index The attribute index to remove.
   * @throws DatabaseException If an JE database error occurs while attempting
   * to delete the index.
   */
  public void deleteAttributeIndex(AttributeIndex index)
  throws DatabaseException
  {
    index.close();
    if(env.getConfig().getTransactional())
    {
      Transaction txn = beginTransaction();
      try
      {
        if(index.equalityIndex != null)
        {
          env.removeDatabase(txn, index.equalityIndex.getName());
          state.removeIndexTrustState(txn, index.equalityIndex);
        }
        if(index.presenceIndex != null)
        {
          env.removeDatabase(txn, index.presenceIndex.getName());
          state.removeIndexTrustState(txn, index.presenceIndex);
        }
        if(index.substringIndex != null)
        {
          env.removeDatabase(txn, index.substringIndex.getName());
          state.removeIndexTrustState(txn, index.substringIndex);
        }
        if(index.orderingIndex != null)
        {
          env.removeDatabase(txn, index.orderingIndex.getName());
          state.removeIndexTrustState(txn, index.orderingIndex);
        }
        if(index.approximateIndex != null)
        {
          env.removeDatabase(txn, index.approximateIndex.getName());
          state.removeIndexTrustState(txn, index.approximateIndex);
        }
        transactionCommit(txn);
      }
      catch(DatabaseException de)
      {
        transactionAbort(txn);
        throw de;
      }
    }
    else
    {
      if(index.equalityIndex != null)
      {
        env.removeDatabase(null, index.equalityIndex.getName());
        state.removeIndexTrustState(null, index.equalityIndex);
      }
      if(index.presenceIndex != null)
      {
        env.removeDatabase(null, index.presenceIndex.getName());
        state.removeIndexTrustState(null, index.presenceIndex);
      }
      if(index.substringIndex != null)
      {
        env.removeDatabase(null, index.substringIndex.getName());
        state.removeIndexTrustState(null, index.substringIndex);
      }
      if(index.orderingIndex != null)
      {
        env.removeDatabase(null, index.orderingIndex.getName());
        state.removeIndexTrustState(null, index.orderingIndex);
      }
      if(index.approximateIndex != null)
      {
        env.removeDatabase(null, index.approximateIndex.getName());
        state.removeIndexTrustState(null, index.approximateIndex);
      }
    }
  }

  /**
   * This method constructs a container name from a base DN. Only alphanumeric
   * characters are preserved, all other characters are replaced with an
   * underscore.
   *
   * @return The container name for the base DN.
   */
  public String getDatabasePrefix()
  {
    return databasePrefix;
  }

  /**
   * Sets a new database prefix for this entry container and rename all
   * existing databases in use by this entry container.
   *
   * @param newDatabasePrefix The new database prefix to use.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void setDatabasePrefix(String newDatabasePrefix)
  throws DatabaseException, JebException

  {
    List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    listDatabases(databases);

    newDatabasePrefix = preparePrefix(newDatabasePrefix);

    // close the containers.
    for(DatabaseContainer db : databases)
    {
      db.close();
    }

    try
    {
      if(env.getConfig().getTransactional())
      {
        //Rename under transaction
        Transaction txn = beginTransaction();
        try
        {
          for(DatabaseContainer db : databases)
          {
            String oldName = db.getName();
            String newName = oldName.replace(databasePrefix, newDatabasePrefix);
            env.renameDatabase(txn, oldName, newName);
          }

          transactionCommit(txn);

          for(DatabaseContainer db : databases)
          {
            String oldName = db.getName();
            String newName = oldName.replace(databasePrefix, newDatabasePrefix);
            db.setName(newName);
          }

          // Update the prefix.
          this.databasePrefix = newDatabasePrefix;
        }
        catch(Exception e)
        {
          transactionAbort(txn);

          String msg = e.getMessage();
          if (msg == null)
          {
            msg = stackTraceToSingleLineString(e);
          }
          Message message = ERR_JEB_UNCHECKED_EXCEPTION.get(msg);
          throw new JebException(message, e);
        }
      }
      else
      {
        for(DatabaseContainer db : databases)
        {
          String oldName = db.getName();
          String newName = oldName.replace(databasePrefix, newDatabasePrefix);
          env.renameDatabase(null, oldName, newName);
          db.setName(newName);
        }

        // Update the prefix.
        this.databasePrefix = newDatabasePrefix;
      }
    }
    finally
    {
      // Open the containers backup.
      for(DatabaseContainer db : databases)
      {
        db.open();
      }
    }
  }


  /**
   * Get the baseDN this entry container is responsible for.
   *
   * @return The Base DN for this entry container.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Get the parent of a DN in the scope of the base DN.
   *
   * @param dn A DN which is in the scope of the base DN.
   * @return The parent DN, or null if the given DN is the base DN.
   */
  public DN getParentWithinBase(DN dn)
  {
    if (dn.equals(baseDN))
    {
      return null;
    }
    return dn.getParent();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      LocalDBBackendCfg cfg, List<Message> unacceptableReasons)
  {
    // This is always true because only all config attributes used
    // by the entry container should be validated by the admin framework.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(LocalDBBackendCfg cfg)
  {
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    exclusiveLock.lock();
    try
    {
      if (config.isSubordinateIndexesEnabled() != cfg
          .isSubordinateIndexesEnabled())
      {
        if (cfg.isSubordinateIndexesEnabled())
        {
          // Re-enabling subordinate indexes.
          openSubordinateIndexes();
        }
        else
        {
          // Disabling subordinate indexes. Use a null index and ensure that
          // future attempts to use the real indexes will fail.
          id2children.close();
          id2children = new NullIndex(databasePrefix + "_"
              + ID2CHILDREN_DATABASE_NAME, new ID2CIndexer(), state, env, this);
          state.putIndexTrustState(null, id2children, false);
          id2children.open(); // No-op

          id2subtree.close();
          id2subtree = new NullIndex(databasePrefix + "_"
              + ID2SUBTREE_DATABASE_NAME, new ID2SIndexer(), state, env, this);
          state.putIndexTrustState(null, id2subtree, false);
          id2subtree.open(); // No-op

          logError(NOTE_JEB_SUBORDINATE_INDEXES_DISABLED
              .get(cfg.getBackendId()));
        }
      }

      if (config.getIndexEntryLimit() != cfg.getIndexEntryLimit())
      {
        if (id2children.setIndexEntryLimit(cfg.getIndexEntryLimit()))
        {
          adminActionRequired = true;
          Message message = NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD
              .get(id2children.getName());
          messages.add(message);
        }

        if (id2subtree.setIndexEntryLimit(cfg.getIndexEntryLimit()))
        {
          adminActionRequired = true;
          Message message = NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD
              .get(id2subtree.getName());
          messages.add(message);
        }
      }

      DataConfig entryDataConfig = new DataConfig(cfg.isEntriesCompressed(),
          cfg.isCompactEncoding(), rootContainer.getCompressedSchema());
      id2entry.setDataConfig(entryDataConfig);

      this.config = cfg;
    }
    catch (DatabaseException e)
    {
      messages.add(Message.raw(stackTraceToSingleLineString(e)));
      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
          false, messages);
    }
    finally
    {
      exclusiveLock.unlock();
    }

    return new ConfigChangeResult(ResultCode.SUCCESS,
        adminActionRequired, messages);
  }

  /**
   * Get the environment config of the JE environment used in this entry
   * container.
   *
   * @return The environment config of the JE environment.
   * @throws DatabaseException If an error occurs while retriving the
   *                           configuration object.
   */
  public EnvironmentConfig getEnvironmentConfig()
  throws DatabaseException
  {
    return env.getConfig();
  }

  /**
   * Clear the contents of this entry container.
   *
   * @return The number of records deleted.
   * @throws DatabaseException If an error occurs while removing the entry
   *                           container.
   */
  public long clear() throws DatabaseException
  {
    List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
    listDatabases(databases);
    long count = 0;

    for(DatabaseContainer db : databases)
    {
      db.close();
    }
    try
    {
      if(env.getConfig().getTransactional())
      {
        Transaction txn = beginTransaction();

        try
        {
          for(DatabaseContainer db : databases)
          {
            count += env.truncateDatabase(txn, db.getName(), true);
          }

          transactionCommit(txn);
        }
        catch(DatabaseException de)
        {
          transactionAbort(txn);
          throw de;
        }
      }
      else
      {
        for(DatabaseContainer db : databases)
        {
          count += env.truncateDatabase(null, db.getName(), true);
        }
      }
    }
    finally
    {
      for(DatabaseContainer db : databases)
      {
        db.open();
      }

      Transaction txn = null;
      try
      {
        if(env.getConfig().getTransactional()) {
          txn = beginTransaction();
        }
        for(DatabaseContainer db : databases)
        {
          if (db instanceof Index)
          {
            Index index = (Index)db;
            index.setTrusted(txn, true);
          }
        }
        if(env.getConfig().getTransactional()) {
          transactionCommit(txn);
        }
      }
      catch(Exception de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        // This is mainly used during the unit tests, so it's not essential.
        try
        {
          if (txn != null)
          {
            transactionAbort(txn);
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }
        }
      }
    }

    return count;
  }

  /**
   * Clear the contents for a database from disk.
   *
   * @param database The database to clear.
   * @throws DatabaseException if a JE database error occurs.
   */
  public void clearDatabase(DatabaseContainer database)
  throws DatabaseException
  {
    database.close();
    try
    {
      if(env.getConfig().getTransactional())
      {
        Transaction txn = beginTransaction();
        try
        {
          env.removeDatabase(txn, database.getName());
          transactionCommit(txn);
        }
        catch(DatabaseException de)
        {
          transactionAbort(txn);
          throw de;
        }
      }
      else
      {
        env.removeDatabase(null, database.getName());
      }
    }
    finally
    {
      database.open();
    }
    if(debugEnabled())
    {
      TRACER.debugVerbose("Cleared the database %s", database.getName());
    }
  }

  /**
   * Clear the contents for a attribute index from disk.
   *
   * @param index The attribute index to clear.
   * @return The number of records deleted.
   * @throws DatabaseException if a JE database error occurs.
   */
  public long clearAttributeIndex(AttributeIndex index)
  throws DatabaseException
  {
    long count = 0;

    index.close();
    try
    {
      if(env.getConfig().getTransactional())
      {
        Transaction txn = beginTransaction();
        try
        {
          if(index.equalityIndex != null)
          {
            count += env.truncateDatabase(txn, index.equalityIndex.getName(),
                true);
          }
          if(index.presenceIndex != null)
          {
            count += env.truncateDatabase(txn, index.presenceIndex.getName(),
                true);
          }
          if(index.substringIndex != null)
          {
            count += env.truncateDatabase(txn, index.substringIndex.getName(),
                true);
          }
          if(index.orderingIndex != null)
          {
            count += env.truncateDatabase(txn, index.orderingIndex.getName(),
                true);
          }
          if(index.approximateIndex != null)
          {
            count += env.truncateDatabase(txn, index.approximateIndex.getName(),
                true);
          }
          transactionCommit(txn);
        }
        catch(DatabaseException de)
        {
          transactionAbort(txn);
          throw de;
        }
      }
      else
      {
        if(index.equalityIndex != null)
        {
          count += env.truncateDatabase(null, index.equalityIndex.getName(),
              true);
        }
        if(index.presenceIndex != null)
        {
          count += env.truncateDatabase(null, index.presenceIndex.getName(),
              true);
        }
        if(index.substringIndex != null)
        {
          count += env.truncateDatabase(null, index.substringIndex.getName(),
              true);
        }
        if(index.orderingIndex != null)
        {
          count += env.truncateDatabase(null, index.orderingIndex.getName(),
              true);
        }
        if(index.approximateIndex != null)
        {
          count += env.truncateDatabase(null, index.approximateIndex.getName(),
              true);
        }
      }
    }
    finally
    {
      index.open();
    }
    if(debugEnabled())
    {
      TRACER.debugVerbose("Cleared %d existing records from the " +
          "index %s", count, index.getAttributeType().getNameOrOID());
    }
    return count;
  }


  /**
   * Finds an existing entry whose DN is the closest ancestor of a given baseDN.
   *
   * @param baseDN  the DN for which we are searching a matched DN.
   * @return the DN of the closest ancestor of the baseDN.
   * @throws DirectoryException If an error prevented the check of an
   * existing entry from being performed.
   */
  private DN getMatchedDN(DN baseDN)
  throws DirectoryException
  {
    DN matchedDN = null;
    DN parentDN  = baseDN.getParentDNInSuffix();
    while ((parentDN != null) && parentDN.isDescendantOf(getBaseDN()))
    {
      if (entryExists(parentDN))
      {
        matchedDN = parentDN;
        break;
      }
      parentDN = parentDN.getParentDNInSuffix();
    }
    return matchedDN;
  }

  /**
   * Opens the id2children and id2subtree indexes.
   */
  private void openSubordinateIndexes()
  {
    id2children = new Index(databasePrefix + "_"
          + ID2CHILDREN_DATABASE_NAME, new ID2CIndexer(), state,
          config.getIndexEntryLimit(), 0, true, env, this);
    id2children.open();
    if (!id2children.isTrusted())
    {
      logError(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(id2children
              .getName()));
    }
    id2subtree = new Index(databasePrefix + "_" + ID2SUBTREE_DATABASE_NAME,
            new ID2SIndexer(), state, config.getIndexEntryLimit(), 0, true,
            env, this);
    id2subtree.open();
    if (!id2subtree.isTrusted())
    {
      logError(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(id2subtree.getName()));
    }
  }


  /**
   * Checks if any modifications apply to this indexed attribute.
   * @param index the indexed attributes.
   * @param mods the modifications to check for.
   * @return true if any apply, false otherwise.
   */
  private boolean isAttributeModified(AttributeIndex index,
                                      List<Modification> mods)
  {
    boolean attributeModified = false;
    AttributeType indexAttributeType = index.getAttributeType();
    Iterable<AttributeType> subTypes =
            DirectoryServer.getSchema().getSubTypes(indexAttributeType);

    for (Modification mod : mods)
    {
      Attribute modAttr = mod.getAttribute();
      AttributeType modAttrType = modAttr.getAttributeType();
      if (modAttrType.equals(indexAttributeType))
      {
        attributeModified = true;
        break;
      }
      for(AttributeType subType : subTypes)
      {
        if(modAttrType.equals(subType))
        {
          attributeModified = true;
          break;
        }
      }
    }
    return attributeModified;
  }


  /**
   * Fetch the base Entry of the EntryContainer.
   * @param baseDN the DN for the base entry
   * @param searchScope the scope under which this is fetched.
   *                    Scope is used for referral processing.
   * @return the Entry matching the baseDN.
   * @throws DirectoryException if the baseDN doesn't exist.
   */
  private Entry fetchBaseEntry(DN baseDN, SearchScope searchScope)
          throws DirectoryException
  {
    // Fetch the base entry.
    Entry baseEntry = null;
    try
    {
      baseEntry = getEntry(baseDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    // The base entry must exist for a successful result.
    if (baseEntry == null)
    {
      // Check for referral entries above the base entry.
      dn2uri.targetEntryReferrals(baseDN, searchScope);

      Message message = ERR_JEB_SEARCH_NO_SUCH_OBJECT.get(baseDN.toString());
      DN matchedDN = getMatchedDN(baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
    }

    return baseEntry;
  }


  /**
   * Transform a database prefix string to one usable by the DB.
   * @param databasePrefix the database prefix
   * @return a new string when non letter or digit characters
   *         have been replaced with underscore
   */
  private String preparePrefix(String databasePrefix)
  {
    StringBuilder builder = new StringBuilder(databasePrefix.length());
    for (int i = 0; i < databasePrefix.length(); i++)
    {
      char ch = databasePrefix.charAt(i);
      if (Character.isLetterOrDigit(ch))
      {
        builder.append(ch);
      }
      else
      {
        builder.append('_');
      }
    }
    return builder.toString();
  }


  /**
   * Get the exclusive lock.
   */
  public void lock() {
    exclusiveLock.lock();
  }

  /**
   * Unlock the exclusive lock.
   */
  public void unlock() {
    exclusiveLock.unlock();
  }
}

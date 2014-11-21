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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;
import org.opends.messages.*;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.File;
import java.io.UnsupportedEncodingException;

import com.sleepycat.je.*;

import java.util.concurrent.TimeUnit;

/**
 * This class is used to represent a Db environment that can be used
 * to create ReplicationDB.
 */
public class ReplicationDbEnv
{
  private Environment dbEnvironment = null;
  private Database stateDb = null;
  private ReplicationServer replicationServer = null;
  private static final String GENERATION_ID_TAG = "GENID";
  private static final String FIELD_SEPARATOR = " ";
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Initialize this class.
   * Creates Db environment that will be used to create databases.
   * It also reads the currently known databases from the "changelogstate"
   * database.
   * @param path Path where the backing files must be created.
   * @param replicationServer the ReplicationServer that creates this
   *                          ReplicationDbEnv.
   * @throws DatabaseException If a DatabaseException occurred that prevented
   *                           the initialization to happen.
   * @throws ReplicationDBException If a replicationServer internal error caused
   *                              a failure of the replicationServer processing.
   */
  public ReplicationDbEnv(String path, ReplicationServer replicationServer)
         throws DatabaseException, ReplicationDBException
  {
    this.replicationServer = replicationServer;
    EnvironmentConfig envConfig = new EnvironmentConfig();

    /* Create the DB Environment that will be used for all
     * the ReplicationServer activities related to the db
     */
    envConfig.setAllowCreate(true);
    envConfig.setTransactional(true);
    envConfig.setConfigParam("je.cleaner.threads", "2");
    envConfig.setConfigParam("je.checkpointer.highPriority", "true");

    /*
     * Tests have shown that since the parsing of the Replication log is always
     * done sequentially, it is not necessary to use a large DB cache.
     */
    if (Runtime.getRuntime().maxMemory() > 256 * 1024 * 1024)
    {
      /*
       * If the JVM is reasonably large then we can safely default to bigger
       * read buffers. This will result in more scalable checkpointer and
       * cleaner performance.
       */
      envConfig.setConfigParam("je.cleaner.lookAheadCacheSize",
          String.valueOf(2 * 1024 * 1024));

      envConfig.setConfigParam("je.log.iteratorReadSize",
          String.valueOf(2 * 1024 * 1024));

      envConfig
          .setConfigParam("je.log.faultReadSize", String.valueOf(4 * 1024));

      /*
       * The cache size must be bigger in order to accommodate the larger
       * buffers - see OPENDJ-943.
       */
      envConfig
          .setConfigParam("je.maxMemory", String.valueOf(16 * 1024 * 1024));
    }
    else
    {
      /*
       * Use 5M so that the replication can be used with 64M total for the JVM.
       */
      envConfig.setConfigParam("je.maxMemory", String.valueOf(5 * 1024 * 1024));
    }

    // Since records are always added at the end of the Replication log and
    // deleted at the beginning of the Replication log, this should never
    // cause any deadlock.
    envConfig.setTxnTimeout(0, TimeUnit.SECONDS);
    envConfig.setLockTimeout(0, TimeUnit.SECONDS);

    // Since replication provides durability, we can reduce the DB durability
    // level so that we are immune to application / JVM crashes.
    envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);

    dbEnvironment = new Environment(new File(path), envConfig);

    /*
     * One database is created to store the update from each LDAP
     * server in the topology.
     * The database "changelogstate" is used to store the list of all
     * the servers that have been seen in the past.
     */
    DatabaseConfig dbConfig = new DatabaseConfig();
    dbConfig.setAllowCreate(true);
    dbConfig.setTransactional(true);

    stateDb = dbEnvironment.openDatabase(null, "changelogstate", dbConfig);
    start();
  }

  /**
   * Read the list of known servers from the database and start dbHandler
   * for each of them.
   *
   * @throws DatabaseException in case of underlying DatabaseException
   * @throws ReplicationDBException when the information from the database
   *                              cannot be decoded correctly.
   */
  private void start() throws DatabaseException, ReplicationDBException
  {
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    Cursor cursor = stateDb.openCursor(null, null);

    try
    {
      /*
       *  Get the domain base DN/ generationIDs records
       */
      OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        try
        {
          String stringData = new String(data.getData(), "UTF-8");

          if (debugEnabled())
            TRACER.debugInfo(
                "In " + this.replicationServer.getMonitorInstanceName() +
                " Read tag baseDn generationId=" + stringData);

          String[] str = stringData.split(FIELD_SEPARATOR, 3);
          if (str[0].equals(GENERATION_ID_TAG))
          {
            long generationId;
            try
            {
              // <generationId>
              generationId = new Long(str[1]);
            }
            catch (NumberFormatException e)
            {
              // should never happen
              // TODO: i18n
              throw new ReplicationDBException(Message.raw(
                  "replicationServer state database has a wrong format: " +
                  e.getLocalizedMessage()
                  + "<" + str[1] + ">"));
            }

            String baseDn = str[2];

            if (debugEnabled())
              TRACER.debugInfo(
                "In " + this.replicationServer.getMonitorInstanceName() +
                " Has read baseDn=" + baseDn
                + " generationId=" + generationId);

            replicationServer.getReplicationServerDomain(baseDn, true).
            initGenerationID(generationId);
          }
        }
        catch (UnsupportedEncodingException e)
        {
          // should never happens
          // TODO: i18n
          throw new ReplicationDBException(Message.raw("need UTF-8 support"));
        }
        status = cursor.getNext(key, data, LockMode.DEFAULT);
      }

      /*
       * Get the server Id / domain base DN records
       */
      status = cursor.getFirst(key, data, LockMode.DEFAULT);
      while (status == OperationStatus.SUCCESS)
      {
        String stringData;
        try
        {
          stringData = new String(data.getData(), "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
          // should never happens
          // TODO: i18n
          throw new ReplicationDBException(Message.raw(
          "need UTF-8 support"));
        }

        if (debugEnabled())
          TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            " Read serverId BaseDN=" + stringData);

        String[] str = stringData.split(FIELD_SEPARATOR, 2);
        if (!str[0].equals(GENERATION_ID_TAG))
        {
          int serverId;
          try
          {
            // <serverId>
            serverId = new Integer(str[0]);
          } catch (NumberFormatException e)
          {
            // should never happen
            // TODO: i18n
            throw new ReplicationDBException(Message.raw(
                "replicationServer state database has a wrong format: " +
                e.getLocalizedMessage()
                + "<" + str[0] + ">"));
          }
          // <baseDn>
          String baseDn = str[1];

          if (debugEnabled())
            TRACER.debugInfo(
              "In " + this.replicationServer.getMonitorInstanceName() +
              " Has read: baseDn=" + baseDn
              + " serverId=" + serverId);

          DbHandler dbHandler =
            new DbHandler(
                serverId, baseDn, replicationServer, this,
                replicationServer.getQueueSize());

          replicationServer.getReplicationServerDomain(baseDn, true).
          setDbHandler(serverId, dbHandler);
        }

        status = cursor.getNext(key, data, LockMode.DEFAULT);
      }
    }
    finally
    {
      try
      {
        cursor.close();
      }
      catch (Exception ignored)
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ignored);
      }
    }
  }

    /**
     * Finds or creates the database used to store changes from the server
     * with the given serverId and the given baseDn.
     *
     * @param serverId     The server id that identifies the server.
     * @param baseDn       The baseDn that identifies the domain.
     * @param generationId The generationId associated to this domain.
     * @return the Database.
     * @throws DatabaseException in case of underlying Exception.
     */
    public Database getOrAddDb(int serverId, String baseDn, Long generationId)
    throws DatabaseException
    {
      if (debugEnabled())
        TRACER.debugInfo("ReplicationDbEnv.getOrAddDb() " +
          serverId + " " + baseDn + " " + generationId);
      try
      {
        String stringId = serverId + FIELD_SEPARATOR + baseDn;

        // Opens the database for the changes received from this server
        // on this domain. Create it if it does not already exist.
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        Database db = dbEnvironment.openDatabase(null, stringId, dbConfig);

        // Creates the record serverId/domain base Dn in the stateDb
        // if it does not already exist.
        byte[] byteId;
        byteId = stringId.getBytes("UTF-8");
        DatabaseEntry key = new DatabaseEntry();
        key.setData(byteId);
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = stateDb.get(null, key, data, LockMode.DEFAULT);
        if (status == OperationStatus.NOTFOUND)
        {
          Transaction txn = dbEnvironment.beginTransaction(null, null);
          try {
            data.setData(byteId);
            if (debugEnabled())
              TRACER.debugInfo("getOrAddDb() Created in the state Db record " +
                " serverId/Domain=<"+stringId+">");
            stateDb.put(txn, key, data);
            txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
          } catch (DatabaseException dbe)
          {
            // Abort the txn and propagate the Exception to the caller
            txn.abort();
            throw dbe;
          }
        }

        // Creates the record domain base Dn/ generationId in the stateDb
        // if it does not already exist.
        stringId = GENERATION_ID_TAG + FIELD_SEPARATOR + baseDn;
        String dataStringId = GENERATION_ID_TAG + FIELD_SEPARATOR +
        generationId.toString() + FIELD_SEPARATOR + baseDn;
        byteId = stringId.getBytes("UTF-8");
        byte[] dataByteId;
        dataByteId = dataStringId.getBytes("UTF-8");
        key = new DatabaseEntry();
        key.setData(byteId);
        data = new DatabaseEntry();
        status = stateDb.get(null, key, data, LockMode.DEFAULT);
        if (status == OperationStatus.NOTFOUND)
        {
          Transaction txn = dbEnvironment.beginTransaction(null, null);
          try {
            data.setData(dataByteId);
            if (debugEnabled())
              TRACER.debugInfo(
                  "Created in the state Db record Tag/Domain/GenId key=" +
                  stringId + " value=" + dataStringId);
            stateDb.put(txn, key, data);
            txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
          } catch (DatabaseException dbe)
          {
            // Abort the txn and propagate the Exception to the caller
            txn.abort();
            throw dbe;
          }
        }
        return db;
      }
      catch (UnsupportedEncodingException e)
      {
        // can't happen
        return null;
      }
    }

    /**
     * Creates a new transaction.
     *
     * @return the transaction.
     * @throws DatabaseException in case of underlying database Exception.
     */
    public Transaction beginTransaction() throws DatabaseException
    {
      return dbEnvironment.beginTransaction(null, null);
    }

    /**
     * Shutdown the Db environment.
     */
    public void shutdown()
    {
      try
      {
        stateDb.close();
      } catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLOSING_CHANGELOG_ENV.get());
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
      }

      try
      {
        dbEnvironment.close();
      } catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLOSING_CHANGELOG_ENV.get());
        mb.append(stackTraceToSingleLineString(e));
        logError(mb.toMessage());
      }
    }

    /**
     * Clears the provided generationId associated to the provided baseDn
     * from the state Db.
     *
     * @param baseDn The baseDn for which the generationID must be cleared.
     *
     */
    public void clearGenerationId(String baseDn)
    {
      if (debugEnabled())
        TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
          " clearGenerationId " + baseDn);
      try
      {
        // Deletes the record domain base Dn/ generationId in the stateDb
        String stringId = GENERATION_ID_TAG + FIELD_SEPARATOR + baseDn;
        byte[] byteId = stringId.getBytes("UTF-8");
        DatabaseEntry key = new DatabaseEntry();
        key.setData(byteId);
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = stateDb.get(null, key, data, LockMode.DEFAULT);
        if ((status == OperationStatus.SUCCESS) ||
            (status == OperationStatus.KEYEXIST))
        {
          Transaction txn = dbEnvironment.beginTransaction(null, null);
          try
          {
            stateDb.delete(txn, key);
            txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
            if (debugEnabled())
              TRACER.debugInfo(
                "In " + this.replicationServer.getMonitorInstanceName() +
                " clearGenerationId (" + baseDn +") succeeded.");
          }
          catch (DatabaseException dbe)
          {
            // Abort the txn and propagate the Exception to the caller
            txn.abort();
            throw dbe;
          }
        }
        else
        {
          // TODO : should have a better error logging
          if (debugEnabled())
            TRACER.debugInfo(
              "In " + this.replicationServer.getMonitorInstanceName() +
              " clearGenerationId ("+ baseDn + " failed" + status.toString());
        }
      }
      catch (UnsupportedEncodingException e)
      {
        // can't happen
      }
      catch (DatabaseException dbe)
      {
        // can't happen
      }
    }

    /**
     * Clears the provided serverId associated to the provided baseDn
     * from the state Db.
     *
     * @param baseDn The baseDn for which the generationID must be cleared.
     * @param serverId The serverId to remove from the Db.
     *
     */
    public void clearServerId(String baseDn, int serverId)
    {
      if (debugEnabled())
        TRACER.debugInfo(
            "In " + this.replicationServer.getMonitorInstanceName() +
            "clearServerId(baseDN=" + baseDn + ", serverId=" + serverId);
      try
      {
        String stringId = serverId + FIELD_SEPARATOR + baseDn;

        // Deletes the record serverId/domain base Dn in the stateDb
        byte[] byteId;
        byteId = stringId.getBytes("UTF-8");
        DatabaseEntry key = new DatabaseEntry();
        key.setData(byteId);
        DatabaseEntry data = new DatabaseEntry();
        OperationStatus status = stateDb.get(null, key, data, LockMode.DEFAULT);
        if (status != OperationStatus.NOTFOUND)
        {
          Transaction txn = dbEnvironment.beginTransaction(null, null);
          try {
            data.setData(byteId);
            stateDb.delete(txn, key);
            txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
            if (debugEnabled())
              TRACER.debugInfo(
                  " In " + this.replicationServer.getMonitorInstanceName() +
                  " clearServerId() succeeded " + baseDn + " " +
                  serverId);
          }
          catch (DatabaseException dbe)
          {
            // Abort the txn and propagate the Exception to the caller
            txn.abort();
            throw dbe;
          }
        }
      }
      catch (UnsupportedEncodingException e)
      {
        // can't happen
      }
      catch (DatabaseException dbe)
      {
        // can't happen
      }
    }

    /**
     * Clears the database.
     *
     * @param databaseName The name of the database to clear.
     */
    public final void clearDb(String databaseName)
    {
      Transaction txn = null;
      try
      {
        txn = dbEnvironment.beginTransaction(null, null);
        dbEnvironment.truncateDatabase(txn, databaseName, false);
        txn.commit(Durability.COMMIT_WRITE_NO_SYNC);
        txn = null;
      }
      catch (DatabaseException e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_ERROR_CLEARING_DB.get(databaseName,
            e.getMessage() + " " +
            stackTraceToSingleLineString(e)));
        logError(mb.toMessage());
      }
      finally
      {
        try
        {
          if (txn != null)
            txn.abort();
        }
        catch(Exception e)
        { /* do nothing */ }
      }
    }

    /**
     * Get or create a db to manage integer change  number associated
     * to multidomain server state.
     * TODO:ECL how to manage compatibility of this db with  new domains
     * added or removed ?
     * @return the retrieved or created db.
     * @throws DatabaseException when a problem occurs.
     */
    public Database getOrCreateDraftCNDb()
    throws DatabaseException
    {
      String stringId = "draftcndb";

      // Opens the database for seqnum associated to this domain.
      // Create it if it does not already exist.
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(true);

      return dbEnvironment.openDatabase(null, stringId, dbConfig);
    }
}

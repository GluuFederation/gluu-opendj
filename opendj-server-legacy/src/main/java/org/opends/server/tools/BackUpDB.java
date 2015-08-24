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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.Utils;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tasks.BackupTask;
import org.opends.server.tools.tasks.TaskTool;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.util.args.LDAPConnectionArgumentParser;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This program provides a utility that may be used to back up a Directory
 * Server backend in a binary form that may be quickly archived and restored.
 * The format of the backup may vary based on the backend type and does not need
 * to be something that can be handled by any other backend type.  This will be
 * a process that is intended to run separate from Directory Server and not
 * internally within the server process (e.g., via the tasks interface).
 */
public class BackUpDB extends TaskTool
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The main method for BackUpDB tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = mainBackUpDB(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Processes the command-line arguments and invokes the backup process.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */
  public static int mainBackUpDB(String[] args)
  {
    return mainBackUpDB(args, true, System.out, System.err);
  }

  /**
   * Processes the command-line arguments and invokes the backup process.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   *
   * @return The error code.
   */
  public static int mainBackUpDB(String[] args, boolean initializeServer,
                                 OutputStream outStream, OutputStream errStream)
  {
    BackUpDB tool = new BackUpDB();
    return tool.process(args, initializeServer, outStream, errStream);
  }

  /** Define the command-line arguments that may be used with this program. */
  private BooleanArgument backUpAll;
  private BooleanArgument compress;
  private BooleanArgument displayUsage;
  private BooleanArgument encrypt;
  private BooleanArgument hash;
  private BooleanArgument incremental;
  private BooleanArgument signHash;
  private StringArgument  backendID;
  private StringArgument  backupIDString;
  private StringArgument  configClass;
  private StringArgument  configFile;
  private StringArgument  backupDirectory;
  private StringArgument  incrementalBaseID;

  private int process(String[] args, boolean initializeServer,
                      OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Create the command-line argument parser for use with this program.
    LDAPConnectionArgumentParser argParser =
            createArgParser("org.opends.server.tools.BackUpDB",
                            INFO_BACKUPDB_TOOL_DESCRIPTION.get());
    argParser.setShortToolDescription(REF_SHORT_DESC_BACKUP.get());


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument(
                   "configclass", OPTION_SHORT_CONFIG_CLASS,
                   OPTION_LONG_CONFIG_CLASS, true, false,
                   true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
                   ConfigFileHandler.class.getName(), null,
                   INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument(
                   "configfile", 'f', "configFile", true, false,
                   true, INFO_CONFIGFILE_PLACEHOLDER.get(), null, null,
                   INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      backendID =
           new StringArgument(
                   "backendid", 'n', "backendID", false, true, true,
                   INFO_BACKENDNAME_PLACEHOLDER.get(), null, null,
                   INFO_BACKUPDB_DESCRIPTION_BACKEND_ID.get());
      argParser.addArgument(backendID);


      backUpAll = new BooleanArgument(
                  "backupall", 'a', "backUpAll",
                  INFO_BACKUPDB_DESCRIPTION_BACKUP_ALL.get());
      argParser.addArgument(backUpAll);


      backupIDString =
           new StringArgument(
                   "backupid", 'I', "backupID", false, false, true,
                   INFO_BACKUPID_PLACEHOLDER.get(), null, null,
                   INFO_BACKUPDB_DESCRIPTION_BACKUP_ID.get());
      argParser.addArgument(backupIDString);


      backupDirectory =
           new StringArgument(
                   "backupdirectory", 'd', "backupDirectory", true,
                   false, true, INFO_BACKUPDIR_PLACEHOLDER.get(), null, null,
                   INFO_BACKUPDB_DESCRIPTION_BACKUP_DIR.get());
      argParser.addArgument(backupDirectory);


      incremental = new BooleanArgument(
                  "incremental", 'i', "incremental",
                  INFO_BACKUPDB_DESCRIPTION_INCREMENTAL.get());
      argParser.addArgument(incremental);


      incrementalBaseID =
           new StringArgument(
                   "incrementalbaseid", 'B', "incrementalBaseID",
                   false, false, true, INFO_BACKUPID_PLACEHOLDER.get(), null,
                   null,
                   INFO_BACKUPDB_DESCRIPTION_INCREMENTAL_BASE_ID.get());
      argParser.addArgument(incrementalBaseID);


      compress = new BooleanArgument(
                  "compress", OPTION_SHORT_COMPRESS,
                  OPTION_LONG_COMPRESS,
                  INFO_BACKUPDB_DESCRIPTION_COMPRESS.get());
      argParser.addArgument(compress);


      encrypt = new BooleanArgument(
                  "encrypt", 'y', "encrypt",
                  INFO_BACKUPDB_DESCRIPTION_ENCRYPT.get());
      argParser.addArgument(encrypt);


      hash = new BooleanArgument(
                  "hash", 'A', "hash",
                  INFO_BACKUPDB_DESCRIPTION_HASH.get());
      argParser.addArgument(hash);


      signHash =
           new BooleanArgument(
                   "signhash", 's', "signHash",
                   INFO_BACKUPDB_DESCRIPTION_SIGN_HASH.get());
      argParser.addArgument(signHash);


      displayUsage = CommonArguments.getShowUsage();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    // Init the default values so that they can appear also on the usage.
    try
    {
      argParser.getArguments().initArgumentsWithConfiguration();
    }
    catch (ConfigException ce)
    {
      // Ignore.
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      validateTaskArgs();
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }
    catch (ClientException ce)
    {
      // No need to display the usage since the problem comes with a provided value.
      printWrappedText(err, ce.getMessageObject());
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    // Make sure that either the backUpAll argument was provided or at least one
    // backend ID was given.  They are mutually exclusive.
    if (backUpAll.isPresent())
    {
      if (backendID.isPresent())
      {
        argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_CANNOT_MIX_BACKUP_ALL_AND_BACKEND_ID.get(
            backUpAll.getLongIdentifier(), backendID.getLongIdentifier()));
        return 1;
      }
    }
    else if (! backendID.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_NEED_BACKUP_ALL_OR_BACKEND_ID.get(
          backUpAll.getLongIdentifier(), backendID.getLongIdentifier()));
      return 1;
    }
    else
    {
      // Check that the backendID has not been expressed twice.
      HashSet<String> backendIDLowerCase = new HashSet<>();
      HashSet<String> repeatedBackendIds = new HashSet<>();
      for (String id : backendID.getValues())
      {
        String lId = id.toLowerCase();
        if (!backendIDLowerCase.add(lId))
        {
          repeatedBackendIds.add(lId);
        }
      }
      if (!repeatedBackendIds.isEmpty())
      {
        argParser.displayMessageAndUsageReference(err,
            ERR_BACKUPDB_REPEATED_BACKEND_ID.get(Utils.joinAsString(", ", repeatedBackendIds)));
        return 1;
      }
    }

    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    if (incrementalBaseID.isPresent() && ! incremental.isPresent())
    {
      argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_INCREMENTAL_BASE_REQUIRES_INCREMENTAL.get(
              incrementalBaseID.getLongIdentifier(), incremental.getLongIdentifier()));
      return 1;
    }

    // Encryption or signing requires the ADS backend be available for
    // CryptoManager access to secret key entries. If no connection arguments
    //  are present, infer an offline backup.
    if ((encrypt.isPresent() || signHash.isPresent())
            && ! argParser.connectionArgumentsPresent()) {
      argParser.displayMessageAndUsageReference(err, ERR_BACKUPDB_ENCRYPT_OR_SIGN_REQUIRES_ONLINE.get(
          encrypt.getLongIdentifier(), signHash.getLongIdentifier()));
      return 1;
    }

    // If the signHash option was provided, then make sure that the hash option
    // was given.
    if (signHash.isPresent() && !hash.isPresent())
    {
      argParser.displayMessageAndUsageReference(err,
          ERR_BACKUPDB_SIGN_REQUIRES_HASH.get(signHash.getLongIdentifier(), hash.getLongIdentifier()));
      return 1;
    }


    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      checkVersion();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    return process(argParser, initializeServer, out, err);

  }

  /** {@inheritDoc} */
  @Override
  public void addTaskAttributes(List<RawAttribute> attributes)
  {
    addIfHasValue(attributes, ATTR_TASK_BACKUP_ALL, backUpAll);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_COMPRESS, compress);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_ENCRYPT, encrypt);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_HASH, hash);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_INCREMENTAL, incremental);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_SIGN_HASH, signHash);

    List<String> backendIDs = backendID.getValues();
    if (backendIDs != null && !backendIDs.isEmpty()) {
      attributes.add(
              new LDAPAttribute(ATTR_TASK_BACKUP_BACKEND_ID, backendIDs));
    }

    addIfHasValue(attributes, ATTR_BACKUP_ID, backupIDString);
    addIfHasValue(attributes, ATTR_BACKUP_DIRECTORY_PATH, backupDirectory);
    addIfHasValue(attributes, ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID, incrementalBaseID);
  }

  private void addIfHasValue(List<RawAttribute> attributes, String attrName, Argument arg)
  {
    if (hasValueDifferentThanDefaultValue(arg)) {
      attributes.add(new LDAPAttribute(attrName, arg.getValue()));
    }
  }

  private boolean hasValueDifferentThanDefaultValue(Argument arg)
  {
    return arg.getValue() != null
        && !arg.getValue().equals(arg.getDefaultValue());
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskObjectclass() {
    return "ds-task-backup";
  }

  /** {@inheritDoc} */
  @Override
  public Class<?> getTaskClass() {
    return BackupTask.class;
  }

  /** {@inheritDoc} */
  @Override
  protected int processLocal(boolean initializeServer,
                           PrintStream out,
                           PrintStream err) {

    // Make sure that the backup directory exists.  If not, then create it.
    File backupDirFile = new File(backupDirectory.getValue());
    if (! backupDirFile.exists())
    {
      try
      {
        backupDirFile.mkdirs();
      }
      catch (Exception e)
      {
        printWrappedText(
                err, ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR.get(backupDirectory.getValue(), getExceptionMessage(e)));
        return 1;
      }
    }

    // If no backup ID was provided, then create one with the current timestamp.
    String backupID;
    if (backupIDString.isPresent())
    {
      backupID = backupIDString.getValue();
    }
    else
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      backupID = dateFormat.format(new Date());
    }

    // If the incremental base ID was specified, then make sure it is an
    // incremental backup.
    String incrementalBase;
    if (incrementalBaseID.isPresent())
    {
      incrementalBase = incrementalBaseID.getValue();
    }
    else
    {
      incrementalBase = null;
    }

    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    if (initializeServer)
    {
      try
      {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e)));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e)));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_SCHEMA.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e)));
        return 1;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager(directoryServer.getServerContext());
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(e)));
        return 1;
      }


      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException | InitializationException e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(e.getMessage()));
        return 1;
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(getExceptionMessage(e)));
        return 1;
      }

      try
      {
        ErrorLogPublisher errorLogPublisher =
            TextErrorLogPublisher.getToolStartupTextErrorPublisher(
            new TextWriter.STREAM(out));
        ErrorLogger.getInstance().addLogPublisher(errorLogPublisher);
        DebugLogger.getInstance().addPublisherIfRequired(new TextWriter.STREAM(out));
      }
      catch(Exception e)
      {
        err.println("Error installing the custom error logger: " +
                    stackTraceToSingleLineString(e));
      }
    }


    // Get information about the backends defined in the server, and determine
    // whether we are backing up multiple backends or a single backend.
    ArrayList<Backend>     backendList = new ArrayList<>();
    ArrayList<BackendCfg>  entryList   = new ArrayList<>();
    ArrayList<List<DN>>    dnList      = new ArrayList<>();
    BackendToolUtils.getBackends(backendList, entryList, dnList);
    int numBackends = backendList.size();

    boolean multiple;
    ArrayList<Backend<?>> backendsToArchive = new ArrayList<>(numBackends);
    HashMap<String,BackendCfg> configEntries = new HashMap<>(numBackends);
    if (backUpAll.isPresent())
    {
      for (int i=0; i < numBackends; i++)
      {
        Backend<?> b = backendList.get(i);
        if (b.supports(BackendOperation.BACKUP))
        {
          backendsToArchive.add(b);
          configEntries.put(b.getBackendID(), entryList.get(i));
        }
      }

      // We'll proceed as if we're backing up multiple backends in this case
      // even if there's just one.
      multiple = true;
    }
    else
    {
      // Iterate through the set of backends and pick out those that were requested.
      HashSet<String> requestedBackends = new HashSet<>(backendID.getValues());
      for (int i=0; i < numBackends; i++)
      {
        Backend<?> b = backendList.get(i);
        if (requestedBackends.contains(b.getBackendID()))
        {
          if (!b.supports(BackendOperation.BACKUP))
          {
            logger.warn(WARN_BACKUPDB_BACKUP_NOT_SUPPORTED, b.getBackendID());
          }
          else
          {
            backendsToArchive.add(b);
            configEntries.put(b.getBackendID(), entryList.get(i));
            requestedBackends.remove(b.getBackendID());
          }
        }
      }

      if (! requestedBackends.isEmpty())
      {
        for (String id : requestedBackends)
        {
          logger.error(ERR_BACKUPDB_NO_BACKENDS_FOR_ID, id);
        }

        return 1;
      }


      // See if there are multiple backends to archive.
      multiple = backendsToArchive.size() > 1;
    }


    // If there are no backends to archive, then print an error and exit.
    if (backendsToArchive.isEmpty())
    {
      logger.warn(WARN_BACKUPDB_NO_BACKENDS_TO_ARCHIVE);
      return 1;
    }


    // Iterate through the backends to archive and back them up individually.
    boolean errorsEncountered = false;
    for (Backend<?> b : backendsToArchive)
    {
      // Acquire a shared lock for this backend.
      try
      {
        String        lockFile      = LockFileManager.getBackendLockFileName(b);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          logger.error(ERR_BACKUPDB_CANNOT_LOCK_BACKEND, b.getBackendID(), failureReason);
          errorsEncountered = true;
          continue;
        }
      }
      catch (Exception e)
      {
        logger.error(ERR_BACKUPDB_CANNOT_LOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
        errorsEncountered = true;
        continue;
      }


      logger.info(NOTE_BACKUPDB_STARTING_BACKUP, b.getBackendID());


      // Get the config entry for this backend.
      BackendCfg configEntry = configEntries.get(b.getBackendID());


      // Get the path to the directory to use for this backup.  If we will be
      // backing up multiple backends (or if we are backing up all backends,
      // even if there's only one of them), then create a subdirectory for each
      // backend.
      String backupDirPath;
      if (multiple)
      {
        backupDirPath = backupDirectory.getValue() + File.separator +
                        b.getBackendID();
      }
      else
      {
        backupDirPath = backupDirectory.getValue();
      }


      // If the directory doesn't exist, then create it.  If it does exist, then
      // see if it has a backup descriptor file.
      BackupDirectory backupDir;
      backupDirFile = new File(backupDirPath);
      if (backupDirFile.exists())
      {
        String descriptorPath = backupDirPath + File.separator +
                                BACKUP_DIRECTORY_DESCRIPTOR_FILE;
        File descriptorFile = new File(descriptorPath);
        if (descriptorFile.exists())
        {
          try
          {
            backupDir =
                 BackupDirectory.readBackupDirectoryDescriptor(backupDirPath);
          }
          catch (ConfigException ce)
          {
            logger.error(ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR, descriptorPath, ce.getMessage());
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
              }
            }
            catch (Exception e)
            {
              logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
            }

            continue;
          }
          catch (Exception e)
          {
            logger.error(ERR_BACKUPDB_CANNOT_PARSE_BACKUP_DESCRIPTOR, descriptorPath, getExceptionMessage(e));
            errorsEncountered = true;

            try
            {
              String lockFile = LockFileManager.getBackendLockFileName(b);
              StringBuilder failureReason = new StringBuilder();
              if (! LockFileManager.releaseLock(lockFile, failureReason))
              {
                logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
              }
            }
            catch (Exception e2)
            {
              logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e2));
            }

            continue;
          }
        }
        else
        {
          backupDir = new BackupDirectory(backupDirPath, configEntry.dn());
        }
      }
      else
      {
        try
        {
          backupDirFile.mkdirs();
        }
        catch (Exception e)
        {
          logger.error(ERR_BACKUPDB_CANNOT_CREATE_BACKUP_DIR, backupDirPath, getExceptionMessage(e));
          errorsEncountered = true;

          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(b);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
            }
          }
          catch (Exception e2)
          {
            logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e2));
          }

          continue;
        }

        backupDir = new BackupDirectory(backupDirPath, configEntry.dn());
      }


      // Create a backup configuration and determine whether the requested
      // backup can be performed using the selected backend.
      BackupConfig backupConfig = new BackupConfig(backupDir, backupID,
                                                   incremental.isPresent());
      backupConfig.setCompressData(compress.isPresent());
      backupConfig.setEncryptData(encrypt.isPresent());
      backupConfig.setHashData(hash.isPresent());
      backupConfig.setSignHash(signHash.isPresent());
      backupConfig.setIncrementalBaseID(incrementalBase);

      if (!b.supports(BackendOperation.BACKUP))
      {
        logger.error(ERR_BACKUPDB_CANNOT_BACKUP, b.getBackendID());
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
          }
        }
        catch (Exception e2)
        {
          logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e2));
        }

        continue;
      }


      // Perform the backup.
      try
      {
        b.createBackup(backupConfig);
      }
      catch (DirectoryException de)
      {
        logger.error(ERR_BACKUPDB_ERROR_DURING_BACKUP, b.getBackendID(), de.getMessageObject());
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
          }
        }
        catch (Exception e)
        {
          logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
        }

        continue;
      }
      catch (Exception e)
      {
        logger.error(ERR_BACKUPDB_ERROR_DURING_BACKUP, b.getBackendID(), getExceptionMessage(e));
        errorsEncountered = true;

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(b);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
          }
        }
        catch (Exception e2)
        {
          logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e2));
        }

        continue;
      }


      // Release the shared lock for the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(b);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), failureReason);
          errorsEncountered = true;
        }
      }
      catch (Exception e)
      {
        logger.warn(WARN_BACKUPDB_CANNOT_UNLOCK_BACKEND, b.getBackendID(), getExceptionMessage(e));
        errorsEncountered = true;
      }
    }


    // Print a final completed message, indicating whether there were any errors
    // in the process.
    int ret = 0;
    if (errorsEncountered)
    {
      logger.info(NOTE_BACKUPDB_COMPLETED_WITH_ERRORS);
      ret = 1;
    }
    else
    {
      logger.info(NOTE_BACKUPDB_COMPLETED_SUCCESSFULLY);
    }
    return ret;
  }

  /** {@inheritDoc} */
  @Override
  public String getTaskId() {
    return backupIDString != null ? backupIDString.getValue() : null;
  }
}

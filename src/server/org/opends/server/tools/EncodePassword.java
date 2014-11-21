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
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.tools;



import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.LDIFBackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.TrustStoreBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.CoreConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordStorageSchemeConfigManager;
import org.opends.server.crypto.CryptoManagerSync;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.WritabilityMode;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a utility that may be used to interact with the
 * password storage schemes defined in the Directory Server.  In particular,
 * it can encode a clear-text password using a specified scheme, and it can also
 * determine whether a given encoded password is the encoded representation of a
 * given clear-text password.  Alternately, it can be used to obtain a list of
 * the available password storage scheme names.
 */
public class EncodePassword
{

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
   * Processes the command-line arguments and performs the requested action.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int returnCode = encodePassword(args, true, System.out, System.err);
    if (returnCode != 0)
    {
      System.exit(filterExitCode(returnCode));
    }
  }



  /**
   * Processes the command-line arguments and performs the requested action.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value that indicates whether processing was successful.
   */
  public static int encodePassword(String[] args)
  {
    return encodePassword(args, true, System.out, System.err);
  }



  /**
   * Processes the command-line arguments and performs the requested action.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return  An integer value that indicates whether processing was successful.
   */
  public static int encodePassword(String[] args, boolean initializeServer,
                                   OutputStream outStream,
                                   OutputStream errStream)
  {
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    // Define the command-line arguments that may be used with this program.
    BooleanArgument   authPasswordSyntax   = null;
    BooleanArgument   useCompareResultCode = null;
    BooleanArgument   listSchemes          = null;
    BooleanArgument   showUsage            = null;
    BooleanArgument   interactivePassword  = null;
    StringArgument    clearPassword        = null;
    FileBasedArgument clearPasswordFile    = null;
    StringArgument    encodedPassword      = null;
    FileBasedArgument encodedPasswordFile  = null;
    StringArgument    configClass          = null;
    StringArgument    configFile           = null;
    StringArgument    schemeName           = null;


    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_ENCPW_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.EncodePassword",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      listSchemes = new BooleanArgument(
              "listschemes", 'l', "listSchemes",
              INFO_ENCPW_DESCRIPTION_LISTSCHEMES.get());
      argParser.addArgument(listSchemes);

      interactivePassword = new BooleanArgument(
              "interactivePassword", 'i',
              "interactivePassword",
              INFO_ENCPW_DESCRIPTION_INPUT_PW.get());
      argParser.addArgument(interactivePassword);

      clearPassword = new StringArgument("clearpw", 'c', "clearPassword", false,
                                         false, true, INFO_CLEAR_PWD.get(),
                                         null, null,
                                         INFO_ENCPW_DESCRIPTION_CLEAR_PW.get());
      argParser.addArgument(clearPassword);


      clearPasswordFile =
           new FileBasedArgument("clearpwfile", 'f', "clearPasswordFile", false,
                                 false, INFO_FILE_PLACEHOLDER.get(), null, null,
                                 INFO_ENCPW_DESCRIPTION_CLEAR_PW_FILE.get());
      argParser.addArgument(clearPasswordFile);


      encodedPassword = new StringArgument(
              "encodedpw", 'e', "encodedPassword",
              false, false, true, INFO_ENCODED_PWD_PLACEHOLDER.get(),
              null, null,
              INFO_ENCPW_DESCRIPTION_ENCODED_PW.get());
      argParser.addArgument(encodedPassword);


      encodedPasswordFile =
           new FileBasedArgument("encodedpwfile", 'E', "encodedPasswordFile",
                                 false, false, INFO_FILE_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_ENCPW_DESCRIPTION_ENCODED_PW_FILE.get());
      argParser.addArgument(encodedPasswordFile);


      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                                       OPTION_LONG_CONFIG_CLASS,
                                       true, false, true,
                                       INFO_CONFIGCLASS_PLACEHOLDER.get(),
                                       ConfigFileHandler.class.getName(), null,
                                       INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile = new StringArgument("configfile", 'F', "configFile",
                                      true, false, true,
                                      INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                                      null,
                                      INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      schemeName = new StringArgument("scheme", 's', "storageScheme", false,
                                      false, true,
                                      INFO_STORAGE_SCHEME_PLACEHOLDER.get(),
                                      null, null,
                                      INFO_ENCPW_DESCRIPTION_SCHEME.get());
      argParser.addArgument(schemeName);


      authPasswordSyntax = new BooleanArgument(
              "authpasswordsyntax", 'a',
              "authPasswordSyntax",
              INFO_ENCPW_DESCRIPTION_AUTHPW.get());
      argParser.addArgument(authPasswordSyntax);


      useCompareResultCode =
           new BooleanArgument("usecompareresultcode", 'r',
                               "useCompareResultCode",
                               INFO_ENCPW_DESCRIPTION_USE_COMPARE_RESULT.get());
      argParser.addArgument(useCompareResultCode);


      showUsage = new BooleanArgument("usage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return OPERATIONS_ERROR;
    }


    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return OPERATIONS_ERROR;
    }


    // If we should just display usage or version information,
    // then we've already done it so just return without doing anything else.
    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESS;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    // Check for conflicting arguments.
    if (clearPassword.isPresent() && clearPasswordFile.isPresent())
    {
      Message message =
              ERR_TOOL_CONFLICTING_ARGS.get(clearPassword.getLongIdentifier(),
                                  clearPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return OPERATIONS_ERROR;
    }

    if (clearPassword.isPresent() && interactivePassword.isPresent())
    {
      Message message =
              ERR_TOOL_CONFLICTING_ARGS.get(clearPassword.getLongIdentifier(),
                  interactivePassword.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return OPERATIONS_ERROR;
    }

    if (clearPasswordFile.isPresent() && interactivePassword.isPresent())
    {
      Message message =
              ERR_TOOL_CONFLICTING_ARGS.get(
                  clearPasswordFile.getLongIdentifier(),
                  interactivePassword.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return OPERATIONS_ERROR;
    }

    if (encodedPassword.isPresent() && encodedPasswordFile.isPresent())
    {
      Message message =
              ERR_TOOL_CONFLICTING_ARGS.get(encodedPassword.getLongIdentifier(),
                                  encodedPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return OPERATIONS_ERROR;
    }


    // If we are not going to just list the storage schemes, then the clear-text
    // password must have been provided.  If we're going to encode a password,
    // then the scheme must have also been provided.
    ByteString clearPW = null;
    if (! listSchemes.isPresent())
    {
      if ((! encodedPassword.isPresent()) && (! encodedPasswordFile.isPresent())
           && (! schemeName.isPresent()))
      {
        Message message =
                ERR_ENCPW_NO_SCHEME.get(schemeName.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return OPERATIONS_ERROR;
      }
    }


    // Determine whether we're encoding the clear-text password or comparing it
    // against an already-encoded password.
    boolean compareMode;
    ByteString encodedPW = null;
    if (encodedPassword.hasValue())
    {
      compareMode = true;
      encodedPW = ByteString.valueOf(encodedPassword.getValue());
    }
    else if (encodedPasswordFile.hasValue())
    {
      compareMode = true;
      encodedPW = ByteString.valueOf(encodedPasswordFile.getValue());
    }
    else
    {
      compareMode = false;
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
        Message message =
                ERR_SERVER_BOOTSTRAP_ERROR.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_CONFIG.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_SCHEMA.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }


      // Initialize the Directory Server core configuration.
      try
      {
        CoreConfigManager coreConfigManager = new CoreConfigManager();
        coreConfigManager.initializeCoreConfig();
      }
      catch (ConfigException ce)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (InitializationException ie)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        Message message =
                ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }


      if(!initializeServerComponents(directoryServer, err))
          return -1;

      // Initialize the password storage schemes.
      try
      {
        PasswordStorageSchemeConfigManager storageSchemeConfigManager =
             new PasswordStorageSchemeConfigManager();
        storageSchemeConfigManager.initializePasswordStorageSchemes();
      }
      catch (ConfigException ce)
      {
        Message message =
                ERR_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES.get(
                        ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
      catch (Exception e)
      {
        Message message = ERR_ENCPW_CANNOT_INITIALIZE_STORAGE_SCHEMES.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return OPERATIONS_ERROR;
      }
    }


    // If we are only trying to list the available schemes, then do so and exit.
    if (listSchemes.isPresent())
    {
      if (authPasswordSyntax.isPresent())
      {
        ConcurrentHashMap<String,PasswordStorageScheme> storageSchemes =
             DirectoryServer.getAuthPasswordStorageSchemes();
        if (storageSchemes.isEmpty())
        {
          Message message = ERR_ENCPW_NO_STORAGE_SCHEMES.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          int size = storageSchemes.size();

          ArrayList<String> nameList = new ArrayList<String>(size);
          for (PasswordStorageScheme s : storageSchemes.values())
          {
            nameList.add(s.getAuthPasswordSchemeName());
          }

          String[] nameArray = new String[size];
          nameList.toArray(nameArray);
          Arrays.sort(nameArray);

          for (String storageSchemeName : nameArray)
          {
            out.println(storageSchemeName);
          }
        }

        return SUCCESS;
      }
      else
      {
        ConcurrentHashMap<String,PasswordStorageScheme> storageSchemes =
             DirectoryServer.getPasswordStorageSchemes();
        if (storageSchemes.isEmpty())
        {
          Message message = ERR_ENCPW_NO_STORAGE_SCHEMES.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
        }
        else
        {
          int size = storageSchemes.size();

          ArrayList<String> nameList = new ArrayList<String>(size);
          for (PasswordStorageScheme s : storageSchemes.values())
          {
            nameList.add(s.getStorageSchemeName());
          }

          String[] nameArray = new String[size];
          nameList.toArray(nameArray);
          Arrays.sort(nameArray);

          for (String storageSchemeName : nameArray)
          {
            out.println(storageSchemeName);
          }
        }

        return SUCCESS;
      }
    }


    // Either encode the clear-text password using the provided scheme, or
    // compare the clear-text password against the encoded password.
    if (compareMode)
    {
      // Check to see if the provided password value was encoded.  If so, then
      // break it down into its component parts and use that to perform the
      // comparison.  Otherwise, the user must have provided the storage scheme.
      if (authPasswordSyntax.isPresent())
      {
        String scheme;
        String authInfo;
        String authValue;

        try
        {
          StringBuilder[] authPWElements =
               AuthPasswordSyntax.decodeAuthPassword(encodedPW.toString());
          scheme    = authPWElements[0].toString();
          authInfo  = authPWElements[1].toString();
          authValue = authPWElements[2].toString();
        }
        catch (DirectoryException de)
        {
          Message message = ERR_ENCPW_INVALID_ENCODED_AUTHPW.get(
                  de.getMessageObject());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_ENCPW_INVALID_ENCODED_AUTHPW.get(
                  String.valueOf(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }

        PasswordStorageScheme storageScheme =
             DirectoryServer.getAuthPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          Message message = ERR_ENCPW_NO_SUCH_AUTH_SCHEME.get(
                  scheme);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }

        if (clearPW == null)
        {
          clearPW = getClearPW(out, err, argParser, clearPassword,
              clearPasswordFile, interactivePassword);
          if (clearPW == null)
          {
            return OPERATIONS_ERROR;
          }
        }
        final boolean authPasswordMatches =
            storageScheme.authPasswordMatches(clearPW, authInfo, authValue);
        out.println(getOutputMessage(authPasswordMatches));
        if (useCompareResultCode.isPresent())
        {
          return authPasswordMatches ? COMPARE_TRUE : COMPARE_FALSE;
        }
        return SUCCESS;
      }
      else
      {
        PasswordStorageScheme storageScheme;
        String                encodedPWString;

        if (UserPasswordSyntax.isEncoded(encodedPW))
        {
          try
          {
            String[] userPWElements =
                 UserPasswordSyntax.decodeUserPassword(encodedPW.toString());
            encodedPWString = userPWElements[1];

            storageScheme =
                 DirectoryServer.getPasswordStorageScheme(userPWElements[0]);
            if (storageScheme == null)
            {
              Message message = ERR_ENCPW_NO_SUCH_SCHEME.get(userPWElements[0]);
              err.println(wrapText(message, MAX_LINE_WIDTH));
              return OPERATIONS_ERROR;
            }
          }
          catch (DirectoryException de)
          {
            Message message = ERR_ENCPW_INVALID_ENCODED_USERPW.get(
                    de.getMessageObject());
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return OPERATIONS_ERROR;
          }
          catch (Exception e)
          {
            Message message = ERR_ENCPW_INVALID_ENCODED_USERPW.get(
                    String.valueOf(e));
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return OPERATIONS_ERROR;
          }
        }
        else
        {
          if (! schemeName.isPresent())
          {
            Message message = ERR_ENCPW_NO_SCHEME.get(
                    schemeName.getLongIdentifier());
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return OPERATIONS_ERROR;
          }

          encodedPWString = encodedPW.toString();

          String scheme = toLowerCase(schemeName.getValue());
          storageScheme = DirectoryServer.getPasswordStorageScheme(scheme);
          if (storageScheme == null)
          {
            Message message = ERR_ENCPW_NO_SUCH_SCHEME.get(scheme);
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return OPERATIONS_ERROR;
          }
        }

        if (clearPW == null)
        {
          clearPW = getClearPW(out, err, argParser, clearPassword,
              clearPasswordFile, interactivePassword);
          if (clearPW == null)
          {
            return OPERATIONS_ERROR;
          }
        }
        boolean passwordMatches =
            storageScheme.passwordMatches(clearPW, ByteString
                .valueOf(encodedPWString));
        out.println(getOutputMessage(passwordMatches));
        if (useCompareResultCode.isPresent())
        {
          return passwordMatches ? COMPARE_TRUE : COMPARE_FALSE;
        }
        return SUCCESS;
      }
    }
    else
    {
      // Try to get a reference to the requested password storage scheme.
      PasswordStorageScheme storageScheme;
      if (authPasswordSyntax.isPresent())
      {
        String scheme = schemeName.getValue();
        storageScheme = DirectoryServer.getAuthPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          Message message = ERR_ENCPW_NO_SUCH_AUTH_SCHEME.get(scheme);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
      }
      else
      {
        String scheme = toLowerCase(schemeName.getValue());
        storageScheme = DirectoryServer.getPasswordStorageScheme(scheme);
        if (storageScheme == null)
        {
          Message message = ERR_ENCPW_NO_SUCH_SCHEME.get(scheme);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
      }

      if (authPasswordSyntax.isPresent())
      {
        try
        {
          if (clearPW == null)
          {
            clearPW = getClearPW(out, err, argParser, clearPassword,
                clearPasswordFile, interactivePassword);
            if (clearPW == null)
            {
              return OPERATIONS_ERROR;
            }
          }
          encodedPW = storageScheme.encodeAuthPassword(clearPW);

          Message message = ERR_ENCPW_ENCODED_PASSWORD.get(
                  encodedPW.toString());
          out.println(message);
        }
        catch (DirectoryException de)
        {
          Message message = ERR_ENCPW_CANNOT_ENCODE.get(de.getMessageObject());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_ENCPW_CANNOT_ENCODE.get(getExceptionMessage(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
      }
      else
      {
        try
        {
          if (clearPW == null)
          {
            clearPW = getClearPW(out, err, argParser, clearPassword,
                clearPasswordFile, interactivePassword);
            if (clearPW == null)
            {
              return OPERATIONS_ERROR;
            }
          }
          encodedPW = storageScheme.encodePasswordWithScheme(clearPW);

          Message message =
                  ERR_ENCPW_ENCODED_PASSWORD.get(encodedPW.toString());
          out.println(message);
        }
        catch (DirectoryException de)
        {
          Message message = ERR_ENCPW_CANNOT_ENCODE.get(de.getMessageObject());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
        catch (Exception e)
        {
          Message message = ERR_ENCPW_CANNOT_ENCODE.get(getExceptionMessage(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return OPERATIONS_ERROR;
        }
      }
    }

    // If we've gotten here, then all processing completed successfully.
    return SUCCESS;
  }



  private static Message getOutputMessage(boolean passwordMatches)
  {
    if (passwordMatches)
    {
      return INFO_ENCPW_PASSWORDS_MATCH.get();
    }
    return INFO_ENCPW_PASSWORDS_DO_NOT_MATCH.get();
  }



  private static boolean
          initializeServerComponents(DirectoryServer directoryServer,
                                     PrintStream err) {

      // Initialize the Directory Server crypto manager.
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return false;
      }
      catch (InitializationException ie)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return false;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(
                getExceptionMessage(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return false;
      }
      //Attempt to bring up enough of the server to process schemes requiring
      //secret keys from the trust store backend (3DES, BLOWFISH, AES, RC4) via
      //the crypto-manager.
      try {
          // Initialize the root DNs.
          directoryServer.initializeRootDNConfigManager();
          //Initialize plugins.
          HashSet<PluginType> pluginTypes = new HashSet<PluginType>(1);
          directoryServer.initializePlugins(pluginTypes);
          //Initialize Trust Backend.
          initializeServerBackends(directoryServer);
          // Initialize the subentry manager.
          directoryServer.initializeSubentryManager();
          //Initialize PWD policy components.
          directoryServer.initializeAuthenticationPolicyComponents();
          //Load the crypto-manager key cache among other things.
         new CryptoManagerSync();
    } catch (InitializationException ie) {
        Message message = ERR_ENCPW_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(
                getExceptionMessage(ie));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return false;
    } catch (ConfigException ce) {
        Message message = ERR_ENCPW_CANNOT_INITIALIZE_SERVER_COMPONENTS.get(
                getExceptionMessage(ce));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return false;
    }
    return true;
  }

  private static void initializeServerBackends(DirectoryServer directoryServer)
  throws InitializationException, ConfigException {
    directoryServer.initializeRootDSE();
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();
    ConfigEntry backendRoot;
    try {
      DN configEntryDN = DN.decode(ConfigConstants.DN_BACKEND_BASE);
      backendRoot   = DirectoryServer.getConfigEntry(configEntryDN);
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE.get(
          getExceptionMessage(e));
      throw new ConfigException(message, e);
    }
    if (backendRoot == null) {
      Message message = ERR_CONFIG_BACKEND_BASE_DOES_NOT_EXIST.get();
      throw new ConfigException(message);
    }
    for (String name : root.listBackends()) {
      BackendCfg backendCfg = root.getBackend(name);
      String backendID = backendCfg.getBackendId();
      if(backendCfg instanceof TrustStoreBackendCfg ||
          backendCfg instanceof LDIFBackendCfg) {
        if(backendCfg.isEnabled()) {
          String className = backendCfg.getJavaClass();
          Class backendClass;
          Backend backend;
          try {
            backendClass = DirectoryServer.loadClass(className);
            backend = (Backend) backendClass.newInstance();
          } catch (Exception e) {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            Message message =
              ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(
                  String.valueOf(className),
                  String.valueOf(backendCfg.dn()),
                  stackTraceToSingleLineString(e));
            logError(message);
            continue;
          }
          backend.setBackendID(backendID);
          backend.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);
          try {
            backend.configureBackend(backendCfg);
            backend.initializeBackend();
          } catch (Exception e) {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            Message message =
              ERR_CONFIG_BACKEND_CANNOT_INITIALIZE.get(
                  String.valueOf(className),
                  String.valueOf(backendCfg.dn()),
                  stackTraceToSingleLineString(e));
            logError(message);
          }
          try {
            DirectoryServer.registerBackend(backend);
          } catch (Exception e)
          {
            if (debugEnabled()) {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            Message message =
              WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND.get(
                  backendCfg.getBackendId(),
                  getExceptionMessage(e));
            logError(message);
          }
        }
      }
    }
  }

  /**
   * Get the clear password.
   * @param out The output to ask password.
   * @param err The error output.
   * @param argParser The argument parser.
   * @param clearPassword the clear password
   * @param clearPasswordFile the fil in which the password in stored
   * @param interactivePassword indicate if the password should be asked
   *        interactively.
   * @return the password or null if an error occurs.
   */
  private static ByteString getClearPW(PrintStream out, PrintStream err,
      ArgumentParser argParser, StringArgument clearPassword,
      FileBasedArgument clearPasswordFile, BooleanArgument interactivePassword)
  {
    ByteString clearPW = null;
    if (clearPassword.hasValue())
    {
      clearPW = ByteString.valueOf(clearPassword.getValue());
    }
    else if (clearPasswordFile.hasValue())
    {
      clearPW = ByteString.valueOf(clearPasswordFile.getValue());
    }
    else if (interactivePassword.isPresent())
    {
      EncodePassword encodePassword = new EncodePassword() ;
      try
      {
        String pwd1, pwd2;
        Message msg = INFO_ENCPW_INPUT_PWD_1.get();
        pwd1 = encodePassword.getPassword(out, msg.toString());

        msg = INFO_ENCPW_INPUT_PWD_2.get();
        pwd2 = encodePassword.getPassword(out,msg.toString());
        if (pwd1.equals(pwd2))
        {
          clearPW = ByteString.valueOf(pwd1);
        }
        else
        {
          Message message = ERR_ENCPW_NOT_SAME_PW.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return null;
        }
      }
      catch (IOException e)
      {
        Message message = ERR_ENCPW_CANNOT_READ_PW.get(e.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return null;
      }

    }
    else
    {
      Message message = ERR_ENCPW_NO_CLEAR_PW.get(clearPassword
          .getLongIdentifier(), clearPasswordFile.getLongIdentifier(),
          interactivePassword.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return null;
    }
    return clearPW;
  }

  /**
   * Get the password from JDK6 console or from masked password.
   * @param out The output
   * @param prompt The message to print out.
   * @return the password
   * @throws IOException if an issue occurs when reading the password
   *         from the input
   */
  private String getPassword(PrintStream out, String prompt)
      throws IOException
  {
    String password;
    try // JDK 6 console
    {
      // get the Console (class the constructor)
      Method constructor =
        System.class.getDeclaredMethod("console",new Class[0]);
      Object console = constructor.invoke(null, new Object[0]);

      if (console != null)
      {
        // class to method
        Class<?> c = Class.forName("java.io.Console");
        Object[] args = new Object[] { prompt, new Object[0] };
        Method m = c.getDeclaredMethod("readPassword",
            new Class[] { String.class, args.getClass() });
        password = new String((char[]) m.invoke(console, args));
      }
      else
      {
        throw new IOException("No console");
      }

    }
    catch (Exception e)
    {
      // Try the fallback to the old trick method.
      // Create the thread that will erase chars
      ErasingThread erasingThread = new ErasingThread(out, prompt);
      erasingThread.start();

      password = "";

      // block until enter is pressed
      while (true)
      {
        char c = (char) System.in.read();
        // assume enter pressed, stop masking
        erasingThread.stopMasking();
        if (c == '\r')
        {
          c = (char) System.in.read();
          if (c == '\n')
          {
            break;
          }
          else
          {
            continue;
          }
        }
        else if (c == '\n')
        {
          break;
        }
        else
        {
          // store the password
          password += c;
        }
      }
    }
    return password;
  }


  /**
   * Thread that mask user input.
   *
   */
  private class ErasingThread extends Thread
  {

    private boolean stop = false;
    private String prompt;

    /**
     * The class will mask the user input.
     * @param out
     *          the output
     * @param prompt
     *          The prompt displayed to the user
     */
    public ErasingThread(PrintStream out, String prompt)
    {
      this.prompt = prompt;
    }

    /**
     * Begin masking until asked to stop.
     */
    @Override
    public void run()
    {
      while (!stop)
      {
        try
        {
          // attempt masking at this rate
          Thread.sleep(1);
        }
        catch (InterruptedException iex)
        {
          iex.printStackTrace();
        }
        if (!stop)
        {
          System.out.print("\r" + prompt + " \r" + prompt);
        }
        System.out.flush();
      }
    }

    /**
     * Instruct the thread to stop masking.
     */
    public void stopMasking()
    {
      this.stop = true;
    }

  }

}


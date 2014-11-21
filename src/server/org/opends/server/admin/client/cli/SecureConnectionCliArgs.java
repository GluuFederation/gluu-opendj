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
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.server.admin.client.cli;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.wrapText;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;

import org.opends.admin.ads.util.ApplicationKeyManager;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.Constants;
import org.opends.server.admin.AdministrationConnector;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.FileBasedTrustManagerProviderCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.SelectableCertificateKeyManager;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

/**
 * This is a commodity class that can be used to check the arguments required
 * to establish a secure connection in the command line.  It can be used
 * to generate an ApplicationTrustManager object based on the options provided
 * by the user in the command line.
 *
 */
public final class SecureConnectionCliArgs
{
  /**
   * The 'hostName' global argument.
   */
  public StringArgument hostNameArg = null;

  /**
   * The 'port' global argument.
   */
  public IntegerArgument portArg = null;

  /**
   * The 'bindDN' global argument.
   */
  public StringArgument bindDnArg = null;

  /**
   * The 'adminUID' global argument.
   */
  public StringArgument adminUidArg = null;

  /**
   * The 'bindPasswordFile' global argument.
   */
  public FileBasedArgument bindPasswordFileArg = null;

  /**
   * The 'bindPassword' global argument.
   */
  public StringArgument bindPasswordArg = null;

  /**
   * The 'trustAllArg' global argument.
   */
  public BooleanArgument trustAllArg = null;

  /**
   * The 'trustStore' global argument.
   */
  public StringArgument trustStorePathArg = null;

  /**
   * The 'trustStorePassword' global argument.
   */
  public StringArgument trustStorePasswordArg = null;

  /**
   * The 'trustStorePasswordFile' global argument.
   */
  public FileBasedArgument trustStorePasswordFileArg = null;

  /**
   * The 'keyStore' global argument.
   */
  public StringArgument keyStorePathArg = null;

  /**
   * The 'keyStorePassword' global argument.
   */
  public StringArgument keyStorePasswordArg = null;

  /**
   * The 'keyStorePasswordFile' global argument.
   */
  public FileBasedArgument keyStorePasswordFileArg = null;

  /**
   * The 'certNicknameArg' global argument.
   */
  public StringArgument certNicknameArg = null;

  /**
   * The 'useSSLArg' global argument.
   */
  public BooleanArgument useSSLArg = null;

  /**
   * The 'useStartTLSArg' global argument.
   */
  public BooleanArgument useStartTLSArg = null;

  /**
   * Argument indicating a SASL option.
   */
  public StringArgument  saslOptionArg = null;

  /**
   * Argument to specify the connection timeout.
   */
  public IntegerArgument connectTimeoutArg = null;

  /**
   * Private container for global arguments.
   */
  private LinkedHashSet<Argument> argList = null;

  // the trust manager.
  private ApplicationTrustManager trustManager;

  private boolean configurationInitialized = false;

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * End Of Line.
   */
  public static String EOL = System.getProperty("line.separator");

  /**
   * The Logger.
   */
  static private final Logger LOG =
    Logger.getLogger(SecureConnectionCliArgs.class.getName());

  // Defines if the CLI always use the SSL connection type.
  private boolean alwaysSSL = false;

  /**
   * Creates a new instance of secure arguments.
   *
   * @param alwaysSSL If true, always use the SSL connection type. In this case,
   * the arguments useSSL and startTLS are not present.
   */
  public SecureConnectionCliArgs(boolean alwaysSSL)
  {
    if (alwaysSSL) {
      this.alwaysSSL = true;
    }
  }

  /**
   * Indicates whether or not any of the arguments are present.
   *
   * @return boolean where true indicates that at least one of the
   *         arguments is present
   */
  public boolean argumentsPresent() {
    boolean present = false;
    if (argList != null) {
      for (Argument arg : argList) {
        if (arg.isPresent()) {
          present = true;
          break;
        }
      }
    }
    return present;
  }

  /**
   * Get the admin UID which has to be used for the command.
   *
   * @return The admin UID specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getAdministratorUID()
  {
    if (adminUidArg.isPresent())
    {
      return adminUidArg.getValue();
    }
    else
    {
      return adminUidArg.getDefaultValue();
    }
  }

  /**
   * Tells whether this parser uses the Administrator UID (instead of the
   * bind DN) or not.
   * @return <CODE>true</CODE> if this parser uses the Administrator UID and
   * <CODE>false</CODE> otherwise.
   */
  public boolean useAdminUID()
  {
    return !adminUidArg.isHidden();
  }

  /**
   * Get the bindDN which has to be used for the command.
   *
   * @return The bindDN specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getBindDN()
  {
    if (bindDnArg.isPresent())
    {
      return bindDnArg.getValue();
    }
    else
    {
      return bindDnArg.getDefaultValue();
    }
  }

  /**
   * Get the password which has to be used for the command.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @param clearArg
   *          The password StringArgument argument.
   * @param fileArg
   *          The password FileBased argument.
   * @return The password stored into the specified file on by the
   *         command line argument, or prompts it if not specified.
   */
  public String getBindPassword(String dn,
      OutputStream out, OutputStream err, StringArgument clearArg,
      FileBasedArgument fileArg)
  {
    if (clearArg.isPresent())
    {
      String bindPasswordValue = clearArg.getValue();
      if(bindPasswordValue != null && bindPasswordValue.equals("-"))
      {
        // read the password from the stdin.
        try
        {
          out.write(INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn).getBytes());
          out.flush();
          char[] pwChars = PasswordReader.readPassword();
          bindPasswordValue = new String(pwChars);
        } catch(Exception ex)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
          try
          {
            err.write(wrapText(ex.getMessage(), MAX_LINE_WIDTH).getBytes());
            err.write(EOL.getBytes());
          }
          catch (IOException e)
          {
          }
          return null;
        }
      }
      return bindPasswordValue;
    }
    else
      if (fileArg.isPresent())
      {
        return fileArg.getValue();
      }
      else
      {
        // read the password from the stdin.
        try
        {
          out.write(
              INFO_LDAPAUTH_PASSWORD_PROMPT.get(dn).toString().getBytes());
          out.flush();
          char[] pwChars = PasswordReader.readPassword();
          return new String(pwChars);
        }
        catch (Exception ex)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ex);
          }
          try
          {
            err.write(wrapText(ex.getMessage(), MAX_LINE_WIDTH).getBytes());
            err.write(EOL.getBytes());
          }
          catch (IOException e)
          {
          }
          return null;
        }
      }

  }

  /**
   * Get the password which has to be used for the command.
   *
   * @param dn
   *          The user DN for which to password could be asked.
   * @param out
   *          The input stream to used if we have to prompt to the
   *          user.
   * @param err
   *          The error stream to used if we have to prompt to the
   *          user.
   * @return The password stored into the specified file on by the
   *         command line argument, or prompts it if not specified.
   */
  public String getBindPassword(String dn, OutputStream out, OutputStream err)
  {
    return getBindPassword(dn, out, err, bindPasswordArg, bindPasswordFileArg);
  }

  /**
   * Get the password which has to be used for the command without prompting
   * the user.  If no password was specified, return null.
   *
   * @param clearArg
   *          The password StringArgument argument.
   * @param fileArg
   *          The password FileBased argument.
   * @return The password stored into the specified file on by the
   *         command line argument, or null it if not specified.
   */
  public String getBindPassword(StringArgument clearArg,
      FileBasedArgument fileArg)
  {
    String pwd;
    if (clearArg.isPresent())
    {
      pwd = clearArg.getValue();
    }
    else
      if (fileArg.isPresent())
      {
        pwd = fileArg.getValue();
      }
      else
      {
        pwd = null;
      }
    return pwd;
  }

  /**
   * Get the password which has to be used for the command without prompting
   * the user.  If no password was specified, return null.
   *
   * @return The password stored into the specified file on by the
   *         command line argument, or null it if not specified.
   */
  public String getBindPassword()
  {
    return getBindPassword(bindPasswordArg, bindPasswordFileArg);
  }

  /**
   * Initialize Global option.
   *
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   * @return a ArrayList with the options created.
   */
  public LinkedHashSet<Argument> createGlobalArguments()
  throws ArgumentException
  {
    argList = new LinkedHashSet<Argument>();

    useSSLArg = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
        OPTION_LONG_USE_SSL, INFO_DESCRIPTION_USE_SSL.get());
    useSSLArg.setPropertyName(OPTION_LONG_USE_SSL);
    if (!alwaysSSL) {
      argList.add(useSSLArg);
    } else {
      // simulate that the useSSL arg has been given in the CLI
      useSSLArg.setPresent(true);
    }

    useStartTLSArg = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
        OPTION_LONG_START_TLS,
        INFO_DESCRIPTION_START_TLS.get());
    useStartTLSArg.setPropertyName(OPTION_LONG_START_TLS);
    if (!alwaysSSL) {
      argList.add(useStartTLSArg);
    }

    String defaultHostName;
    try {
      defaultHostName = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      defaultHostName="Unknown (" + e + ")";
    }
    hostNameArg = new StringArgument("host", OPTION_SHORT_HOST,
        OPTION_LONG_HOST, false, false, true, INFO_HOST_PLACEHOLDER.get(),
        defaultHostName,
        null, INFO_DESCRIPTION_HOST.get());
    hostNameArg.setPropertyName(OPTION_LONG_HOST);
    argList.add(hostNameArg);


    Message portDescription = INFO_DESCRIPTION_PORT.get();
    if (alwaysSSL) {
      portDescription = INFO_DESCRIPTION_ADMIN_PORT.get();
    }

    portArg = new IntegerArgument("port", OPTION_SHORT_PORT, OPTION_LONG_PORT,
        false, false, true, INFO_PORT_PLACEHOLDER.get(),
        AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT, null,
        true, 1, true, 65535,
        portDescription);
    portArg.setPropertyName(OPTION_LONG_PORT);
    argList.add(portArg);

    bindDnArg = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
        OPTION_LONG_BINDDN, false, false, true, INFO_BINDDN_PLACEHOLDER.get(),
        "cn=Directory Manager", null, INFO_DESCRIPTION_BINDDN.get());
    bindDnArg.setPropertyName(OPTION_LONG_BINDDN);
    argList.add(bindDnArg);

    // It is up to the classes that required admin UID to make this argument
    // visible and add it.
    adminUidArg = new StringArgument("adminUID", 'I',
        OPTION_LONG_ADMIN_UID, false, false, true,
        INFO_ADMINUID_PLACEHOLDER.get(),
        Constants.GLOBAL_ADMIN_UID, null,
        INFO_DESCRIPTION_ADMIN_UID.get());
    adminUidArg.setPropertyName(OPTION_LONG_ADMIN_UID);
    adminUidArg.setHidden(true);

    bindPasswordArg = new StringArgument("bindPassword",
        OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD, false, false, true,
        INFO_BINDPWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_BINDPASSWORD.get());
    bindPasswordArg.setPropertyName(OPTION_LONG_BINDPWD);
    argList.add(bindPasswordArg);

    bindPasswordFileArg = new FileBasedArgument("bindPasswordFile",
        OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false, false,
        INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_BINDPASSWORDFILE.get());
    bindPasswordFileArg.setPropertyName(OPTION_LONG_BINDPWD_FILE);
    argList.add(bindPasswordFileArg);

    saslOptionArg = new StringArgument(
        "sasloption", OPTION_SHORT_SASLOPTION,
        OPTION_LONG_SASLOPTION, false,
        true, true,
        INFO_SASL_OPTION_PLACEHOLDER.get(), null, null,
        INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get());
    saslOptionArg.setPropertyName(OPTION_LONG_SASLOPTION);
    argList.add(saslOptionArg);

    trustAllArg = new BooleanArgument("trustAll", OPTION_SHORT_TRUSTALL,
        OPTION_LONG_TRUSTALL, INFO_DESCRIPTION_TRUSTALL.get());
    trustAllArg.setPropertyName(OPTION_LONG_TRUSTALL);
    argList.add(trustAllArg);

    trustStorePathArg = new StringArgument("trustStorePath",
        OPTION_SHORT_TRUSTSTOREPATH, OPTION_LONG_TRUSTSTOREPATH, false,
        false, true, INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TRUSTSTOREPATH.get());
    trustStorePathArg.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
    argList.add(trustStorePathArg);

    trustStorePasswordArg = new StringArgument("trustStorePassword",
        OPTION_SHORT_TRUSTSTORE_PWD, OPTION_LONG_TRUSTSTORE_PWD, false, false,
        true, INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
    trustStorePasswordArg.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
    argList.add(trustStorePasswordArg);

    trustStorePasswordFileArg = new FileBasedArgument("trustStorePasswordFile",
        OPTION_SHORT_TRUSTSTORE_PWD_FILE, OPTION_LONG_TRUSTSTORE_PWD_FILE,
        false, false, INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
    trustStorePasswordFileArg.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
    argList.add(trustStorePasswordFileArg);

    keyStorePathArg = new StringArgument("keyStorePath",
        OPTION_SHORT_KEYSTOREPATH, OPTION_LONG_KEYSTOREPATH, false, false,
        true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_KEYSTOREPATH.get());
    keyStorePathArg.setPropertyName(OPTION_LONG_KEYSTOREPATH);
    argList.add(keyStorePathArg);

    keyStorePasswordArg = new StringArgument("keyStorePassword",
        OPTION_SHORT_KEYSTORE_PWD,
        OPTION_LONG_KEYSTORE_PWD, false, false, true,
        INFO_KEYSTORE_PWD_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_KEYSTOREPASSWORD.get());
    keyStorePasswordArg.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
    argList.add(keyStorePasswordArg);

    keyStorePasswordFileArg = new FileBasedArgument("keystorePasswordFile",
        OPTION_SHORT_KEYSTORE_PWD_FILE, OPTION_LONG_KEYSTORE_PWD_FILE, false,
        false, INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
    keyStorePasswordFileArg.setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
    argList.add(keyStorePasswordFileArg);

    certNicknameArg = new StringArgument("certNickname",
        OPTION_SHORT_CERT_NICKNAME, OPTION_LONG_CERT_NICKNAME,
        false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null, null,
        INFO_DESCRIPTION_CERT_NICKNAME.get());
    certNicknameArg.setPropertyName(OPTION_LONG_CERT_NICKNAME);
    argList.add(certNicknameArg);

    int defaultTimeout = ConnectionUtils.getDefaultLDAPTimeout();
    connectTimeoutArg = new IntegerArgument(OPTION_LONG_CONNECT_TIMEOUT,
        null, OPTION_LONG_CONNECT_TIMEOUT,
        false, false, true, INFO_TIMEOUT_PLACEHOLDER.get(),
        defaultTimeout, null,
        true, 0, false, Integer.MAX_VALUE,
        INFO_DESCRIPTION_CONNECTION_TIMEOUT.get());
    connectTimeoutArg.setPropertyName(OPTION_LONG_CONNECT_TIMEOUT);
    argList.add(connectTimeoutArg);

    return argList;
  }

  /**
   * Get the host name which has to be used for the command.
   *
   * @return The host name specified by the command line argument, or
   *         the default value, if not specified.
   */
  public String getHostName()
  {
    if (hostNameArg.isPresent())
    {
      return hostNameArg.getValue();
    }
    else
    {
      return hostNameArg.getDefaultValue();
    }
  }

  /**
   * Get the port which has to be used for the command.
   *
   * @return The port specified by the command line argument, or the
   *         default value, if not specified.
   */
  public String getPort()
  {
    if (portArg.isPresent())
    {
      return portArg.getValue();
    }
    else
    {
      return portArg.getDefaultValue();
    }
  }

  /**
   * Indication if provided global options are validate.
   *
   * @param buf the MessageBuilder to write the error messages.
   * @return return code.
   */
  public int validateGlobalOptions(MessageBuilder buf)
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    // Couldn't have at the same time bindPassword and bindPasswordFile
    if (bindPasswordArg.isPresent() && bindPasswordFileArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          bindPasswordArg.getLongIdentifier(),
          bindPasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    // Couldn't have at the same time trustAll and
    // trustStore related arg
    if (trustAllArg.isPresent() && trustStorePathArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustAllArg.getLongIdentifier(),
          trustStorePathArg.getLongIdentifier());
      errors.add(message);
    }
    if (trustAllArg.isPresent() && trustStorePasswordArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustAllArg.getLongIdentifier(),
          trustStorePasswordArg.getLongIdentifier());
      errors.add(message);
    }
    if (trustAllArg.isPresent() && trustStorePasswordFileArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustAllArg.getLongIdentifier(),
          trustStorePasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    // Couldn't have at the same time trustStorePasswordArg and
    // trustStorePasswordFileArg
    if (trustStorePasswordArg.isPresent()
        && trustStorePasswordFileArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          trustStorePasswordArg.getLongIdentifier(),
          trustStorePasswordFileArg.getLongIdentifier());
      errors.add(message);
    }

    if (trustStorePathArg.isPresent())
    {
      // Check that the path exists and is readable
      String value = trustStorePathArg.getValue();
      if (!canRead(trustStorePathArg.getValue()))
      {
        Message message = ERR_CANNOT_READ_TRUSTSTORE.get(
            value);
        errors.add(message);
      }
    }

    if (keyStorePathArg.isPresent())
    {
      // Check that the path exists and is readable
      String value = keyStorePathArg.getValue();
      if (!canRead(trustStorePathArg.getValue()))
      {
        Message message = ERR_CANNOT_READ_KEYSTORE.get(
            value);
        errors.add(message);
      }
    }

    // Couldn't have at the same time startTLSArg and
    // useSSLArg
    if (useStartTLSArg.isPresent()
        && useSSLArg.isPresent()) {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
          useStartTLSArg
          .getLongIdentifier(), useSSLArg.getLongIdentifier());
      errors.add(message);
    }
    if (errors.size() > 0)
    {
      for (Message error : errors)
      {
        if (buf.length() > 0)
        {
          buf.append(EOL);
        }
        buf.append(error);
      }
      return CONFLICTING_ARGS.getReturnCode();
    }

    return SUCCESSFUL_NOP.getReturnCode();
  }
  /**
   * Indication if provided global options are validate.
   *
   * @param err the stream to be used to print error message.
   * @return return code.
   */
  public int validateGlobalOptions(PrintStream err)
  {
    MessageBuilder buf = new MessageBuilder();
    int returnValue = validateGlobalOptions(buf);
    if (buf.length() > 0)
    {
      err.println(wrapText(buf.toString(), MAX_LINE_WIDTH));
    }
    return returnValue;
  }


  /**
   * Indicate if the SSL mode is required.
   *
   * @return True if SSL mode is required
   */
  public boolean useSSL()
  {
    if (useSSLArg.isPresent() || alwaysSSL())
    {
      return true;
    }
    else
    {
      return false ;
    }
  }

  /**
   * Indicate if the startTLS mode is required.
   *
   * @return True if startTLS mode is required
   */
  public boolean useStartTLS()
  {
    if (useStartTLSArg.isPresent())
    {
      return true;
    }
    else
    {
      return false ;
    }
  }

  /**
   * Indicate if the SSL mode is always used.
   *
   * @return True if SSL mode is always used.
   */
  public boolean alwaysSSL()
  {
    return alwaysSSL;
  }

  /**
   * Handle TrustStore.
   *
   * @return The trustStore manager to be used for the command.
   */
  public ApplicationTrustManager getTrustManager()
  {
    if (trustManager == null)
    {
      KeyStore truststore = null ;
      if (trustAllArg.isPresent())
      {
        // Running a null TrustManager  will force createLdapsContext and
        // createStartTLSContext to use a bindTrustManager.
        return null ;
      }
      else
        if (trustStorePathArg.isPresent())
        {
          FileInputStream fos = null;

          try
          {
            fos = new FileInputStream(trustStorePathArg.getValue());
            String trustStorePasswordStringValue = null;
            char[] trustStorePasswordValue = null;
            if (trustStorePasswordArg.isPresent())
            {
              trustStorePasswordStringValue = trustStorePasswordArg.getValue();
            }
            else if (trustStorePasswordFileArg.isPresent())
            {
              trustStorePasswordStringValue =
                trustStorePasswordFileArg.getValue();
            }

            if (trustStorePasswordStringValue !=  null)
            {
              trustStorePasswordStringValue = System
              .getProperty("javax.net.ssl.trustStorePassword");
            }


            if (trustStorePasswordStringValue !=  null)
            {
              trustStorePasswordValue =
                trustStorePasswordStringValue.toCharArray();
            }

            truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            truststore.load(fos, trustStorePasswordValue);
          }
          catch (KeyStoreException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            LOG.log(Level.WARNING, "Error with the truststore", e);
          }
          catch (NoSuchAlgorithmException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            LOG.log(Level.WARNING, "Error with the truststore", e);
          }
          catch (CertificateException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            LOG.log(Level.WARNING, "Error with the truststore", e);
          }
          catch (IOException e)
          {
            // Nothing to do: if this occurs we will systematically refuse the
            // certificates.  Maybe we should avoid this and be strict, but we
            // are in a best effort mode.
            LOG.log(Level.WARNING, "Error with the truststore", e);
          }
          finally
          {
            if (fos != null)
            {
              try
              {
                fos.close();
              }
              catch (Exception e) {}
            }
          }
        }
      trustManager = new ApplicationTrustManager(truststore);
    }
    return trustManager;
  }

  /**
   * Handle KeyStore.
   *
   * @return The keyStore manager to be used for the command.
   */
  public KeyManager getKeyManager()
  {
    KeyStore keyStore = null;
    String keyStorePasswordStringValue = null;
    char[] keyStorePasswordValue = null;
    if (keyStorePathArg.isPresent())
    {
      FileInputStream fos = null;
      try
      {
        fos = new FileInputStream(keyStorePathArg.getValue());
        if (keyStorePasswordArg.isPresent())
        {
          keyStorePasswordStringValue = keyStorePasswordArg.getValue();
        }
        else if (keyStorePasswordFileArg.isPresent())
        {
          keyStorePasswordStringValue = keyStorePasswordFileArg.getValue();
        }
        if (keyStorePasswordStringValue != null)
        {
          keyStorePasswordValue = keyStorePasswordStringValue.toCharArray();
        }

        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(fos,keyStorePasswordValue);
      }
      catch (KeyStoreException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      catch (NoSuchAlgorithmException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      catch (CertificateException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      catch (IOException e)
      {
        // Nothing to do: if this occurs we will systematically refuse
        // the
        // certificates. Maybe we should avoid this and be strict, but
        // we are
        // in a best effort mode.
        LOG.log(Level.WARNING, "Error with the keystore", e);
      }
      finally
      {
        if (fos != null)
        {
          try {
            fos.close();
          }
          catch (Exception e) {}
        }
      }

      char[] password = null;
      if (keyStorePasswordStringValue != null)
      {
        password = keyStorePasswordStringValue.toCharArray();
      }
      ApplicationKeyManager akm = new ApplicationKeyManager(keyStore,password);
      if (certNicknameArg.isPresent())
      {
        return new SelectableCertificateKeyManager(akm, certNicknameArg
            .getValue());
      }
      else
      {
        return akm;
      }
    }
    else
    {
      return null;
    }
  }

  /**
   * Returns <CODE>true</CODE> if we can read on the provided path and
   * <CODE>false</CODE> otherwise.
   * @param path the path.
   * @return <CODE>true</CODE> if we can read on the provided path and
   * <CODE>false</CODE> otherwise.
   */
  private boolean canRead(String path)
  {
    boolean canRead;
    File file = new File(path);
    if (file.exists())
    {
      canRead = file.canRead();
    }
    else
    {
      canRead = false;
    }
    return canRead;
  }

  /**
   *  Returns the absolute path of the trust store file that appears on the
   *  config.  Returns <CODE>null</CODE> if the trust store is not defined or
   *  it does not exist.
   *
   *  @return the absolute path of the trust store file that appears on the
   *  config.
   *  @throws ConfigException if there is an error reading the configuration.
   */
  public String getTruststoreFileFromConfig() throws ConfigException
  {
    String truststoreFileAbsolute = null;
    TrustManagerProviderCfg trustManagerCfg = null;
    AdministrationConnectorCfg administrationConnectorCfg = null;

    boolean couldInitializeConfig = configurationInitialized;
    // Initialization for admin framework
    if (!configurationInitialized) {
      couldInitializeConfig = initializeConfiguration();
    }
    if (couldInitializeConfig)
    {
      // Get the Directory Server configuration handler and use it.
      RootCfg root =
        ServerManagementContext.getInstance().getRootConfiguration();
      administrationConnectorCfg = root.getAdministrationConnector();

      String trustManagerStr =
        administrationConnectorCfg.getTrustManagerProvider();
      trustManagerCfg = root.getTrustManagerProvider(trustManagerStr);
      if (trustManagerCfg instanceof FileBasedTrustManagerProviderCfg) {
        FileBasedTrustManagerProviderCfg fileBasedTrustManagerCfg =
          (FileBasedTrustManagerProviderCfg) trustManagerCfg;
        String truststoreFile = fileBasedTrustManagerCfg.getTrustStoreFile();
        // Check the file
        if (truststoreFile.startsWith(File.separator)) {
          truststoreFileAbsolute = truststoreFile;
        } else {
          truststoreFileAbsolute =
            DirectoryServer.getInstanceRoot() + File.separator + truststoreFile;
        }
        File f = new File(truststoreFileAbsolute);
        if (!f.exists() || !f.canRead() || f.isDirectory())
        {
          truststoreFileAbsolute = null;
        }
        else
        {
          // Try to get the canonical path.
          try
          {
            truststoreFileAbsolute = f.getCanonicalPath();
          }
          catch (Throwable t)
          {
            // We can ignore this error.
          }
        }
      }
    }
    return truststoreFileAbsolute;
  }

  /**
   * Returns the admin port from the configuration.
   * @return the admin port from the configuration.
   * @throws ConfigException if an error occurs reading the configuration.
   */
  public int getAdminPortFromConfig() throws ConfigException
  {
    int port;
    // Initialization for admin framework
    boolean couldInitializeConfiguration = configurationInitialized;
    if (!configurationInitialized) {
      couldInitializeConfiguration = initializeConfiguration();
    }
    if (couldInitializeConfiguration)
    {
      RootCfg root =
        ServerManagementContext.getInstance().getRootConfiguration();
      port = root.getAdministrationConnector().getListenPort();
    }
    else
    {
      port = AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
    }
    return port;
  }

  private boolean initializeConfiguration() {
    // check if the initialization is required
    try {
      ServerManagementContext.getInstance().getRootConfiguration().
      getAdministrationConnector();
    } catch (java.lang.Throwable th) {
      try {
        DirectoryServer.bootstrapClient();
        DirectoryServer.initializeJMX();
        DirectoryServer.getInstance().initializeConfiguration();
      } catch (Exception ex) {
        // do nothing
        return false;
      }
    }
    configurationInitialized = true;
    return true;
  }

  /**
   * Returns the port to be used according to the configuration and the
   * arguments provided by the user.
   * This method should be called after the arguments have been parsed.
   * @return the port to be used according to the configuration and the
   * arguments provided by the user.
   */
  public int getPortFromConfig()
  {
    int portNumber;
    if (alwaysSSL()) {
      portNumber =
        AdministrationConnector.DEFAULT_ADMINISTRATION_CONNECTOR_PORT;
      // Try to get the port from the config file
      try
      {
        portNumber = getAdminPortFromConfig();
      } catch (ConfigException ex) {
        // nothing to do
      }
    } else {
      portNumber = 636;
    }
    return portNumber;
  }

  /**
   * Updates the default values of the port and the trust store with what is
   * read in the configuration.
   * @throws ConfigException if there is an error reading the configuration.
   */
  public void initArgumentsWithConfiguration() throws ConfigException
  {
    int portNumber = getPortFromConfig();
    portArg.setDefaultValue(String.valueOf(portNumber));

    String truststoreFileAbsolute = getTruststoreFileFromConfig();
    if (truststoreFileAbsolute != null)
    {
      trustStorePathArg.setDefaultValue(truststoreFileAbsolute);
    }
  }
}

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
 *      Portions Copyright 2011-2012 ForgeRock AS
 */
package org.opends.server.extensions;



import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;

import org.ietf.jgss.GSSException;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.
  GSSAPISASLMechanismHandlerCfgDefn.QualityOfProtection;
import org.opends.server.admin.std.server.GSSAPISASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;



/**
 * This class provides an implementation of a SASL mechanism that
 * authenticates clients through Kerberos v5 over GSSAPI.
 */
public class GSSAPISASLMechanismHandler extends
    SASLMechanismHandler<GSSAPISASLMechanismHandlerCfg> implements
    ConfigurationChangeListener<GSSAPISASLMechanismHandlerCfg>, CallbackHandler
{

  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();

  // The DN of the configuration entry for this SASL mechanism
  // handler.
  private DN configEntryDN;

  // The current configuration for this SASL mechanism handler.
  private GSSAPISASLMechanismHandlerCfg configuration;

  // The identity mapper that will be used to map identities.
  private IdentityMapper<?> identityMapper;

  // The properties to use when creating a SASL server to process the
  // GSSAPI authentication.
  private HashMap<String, String> saslProps;

  // The fully qualified domain name used when creating the SASL
  // server.
  private String serverFQDN;

  // The login context used to perform server-side authentication.
  private volatile LoginContext loginContext;
  private final Object loginContextLock = new Object();



  /**
   * Creates a new instance of this SASL mechanism handler. No
   * initialization should be done in this method, as it should all be
   * performed in the <CODE>initializeSASLMechanismHandler</CODE>
   * method.
   */
  public GSSAPISASLMechanismHandler()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(
      GSSAPISASLMechanismHandlerCfg configuration) throws ConfigException,
      InitializationException {
    try {
      initialize(configuration);
      DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_GSSAPI, this);
      configuration.addGSSAPIChangeListener(this);
      this.configuration = configuration;
      Message msg = INFO_GSSAPI_STARTED.get();
      logError(msg);
    }
    catch (UnknownHostException unhe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, unhe);
      }
      Message message = ERR_SASL_CANNOT_GET_SERVER_FQDN.get(String
          .valueOf(configEntryDN), getExceptionMessage(unhe));
      throw new InitializationException(message, unhe);
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
      }
      Message message = ERR_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG
          .get(getExceptionMessage(ioe));
      throw new InitializationException(message, ioe);
    }
  }



  /**
   * Checks to make sure that the ds-cfg-kdc-address and dc-cfg-realm
   * are both defined in the configuration. If only one is set, then
   * that is an error. If both are defined, or, both are null that is
   * fine.
   *
   * @param configuration
   *          The configuration to use.
   * @throws InitializationException
   *           If the properties violate the requirements.
   */
  private void getKdcRealm(GSSAPISASLMechanismHandlerCfg configuration)
      throws InitializationException
  {
    String kdcAddress = configuration.getKdcAddress();
    String realm = configuration.getRealm();
    if ((kdcAddress != null && realm == null)
        || (kdcAddress == null && realm != null))
    {
      Message message = ERR_SASLGSSAPI_KDC_REALM_NOT_DEFINED.get();
      throw new InitializationException(message);
    }
    else if (kdcAddress != null && realm != null)
    {
      System.setProperty(KRBV_PROPERTY_KDC, kdcAddress);
      System.setProperty(KRBV_PROPERTY_REALM, realm);

    }
  }



  /**
   * During login, callbacks are usually used to prompt for passwords.
   * All of the GSSAPI login information is provided in the properties
   * and login.conf file, so callbacks are ignored.
   *
   * @param callbacks
   *          An array of callbacks to process.
   * @throws UnsupportedCallbackException
   *           if an error occurs.
   */
  public void handle(Callback[] callbacks) throws UnsupportedCallbackException
  {
  }



  /**
   * Returns the fully qualified name either defined in the
   * configuration, or, determined by examining the system
   * configuration.
   *
   * @param configuration
   *          The configuration to check.
   * @return The fully qualified hostname of the server.
   * @throws UnknownHostException
   *           If the name cannot be determined from the system
   *           configuration.
   */
  private String getFQDN(GSSAPISASLMechanismHandlerCfg configuration)
      throws UnknownHostException
  {
    String serverName = configuration.getServerFqdn();
    if (serverName == null)
    {
      serverName = InetAddress.getLocalHost().getCanonicalHostName();
    }
    return serverName;
  }

  /**
   *
   * Return the login context. If it's not been initialized yet,
   * create a login context or login using the principal and keytab
   * information specified in the configuration.
   *
   * @return the login context
   * @throws LoginException
   *           If a login context cannot be created.
   */
  private LoginContext getLoginContext() throws LoginException
  {
    if (loginContext == null)
    {
      synchronized (loginContextLock)
      {
        if (loginContext == null)
        {
          loginContext = new LoginContext(
                GSSAPISASLMechanismHandler.class.getName(), this);
          loginContext.login();
        }
      }
    }
    return loginContext;
  }



  /**
   * Logout of the current login context.
   */
  private void logout()
  {
    try
    {
      synchronized (loginContextLock)
      {
        if (loginContext != null)
        {
          loginContext.logout();
          loginContext = null;
        }
      }
    }
    catch (LoginException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Creates an login.conf file from information in the specified
   * configuration. This file is used during the login phase.
   *
   * @param configuration
   *          The new configuration to use.
   * @return The filename of the new configuration file.
   * @throws IOException
   *           If the configuration file cannot be created.
   */
  private String configureLoginConfFile(
      GSSAPISASLMechanismHandlerCfg configuration)
  throws IOException, InitializationException {
    String configFileName;
    File tempFile = File.createTempFile("login", "conf");
    configFileName = tempFile.getAbsolutePath();
    tempFile.deleteOnExit();
    BufferedWriter w = new BufferedWriter(new FileWriter(tempFile, false));
    w.write(getClass().getName() + " {");
    w.newLine();
    w.write("  com.sun.security.auth.module.Krb5LoginModule required "
        + "storeKey=true useKeyTab=true doNotPrompt=true ");
    String keyTabFilePath = configuration.getKeytab();
    if(keyTabFilePath == null) {
      String home = System.getProperty("user.home");
      String sep = System.getProperty("file.separator");
      keyTabFilePath = home+sep+"krb5.keytab";
    }
    File keyTabFile = new File(keyTabFilePath);
    if(!keyTabFile.exists()) {
      Message msg = ERR_SASL_GSSAPI_KEYTAB_INVALID.get(keyTabFilePath);
      throw new InitializationException(msg);
    }
    w.write("keyTab=\"" + keyTabFile + "\" ");
    StringBuilder principal = new StringBuilder();
    String principalName = configuration.getPrincipalName();
    String realm = configuration.getRealm();
    if (principalName != null)
    {
      principal.append("principal=\"").append(principalName);
    }
    else
    {
      principal.append("principal=\"ldap/").append(serverFQDN);
    }
    if (realm != null)
    {
      principal.append("@").append(realm);
    }
    w.write(principal.toString());
    Message msg = INFO_GSSAPI_PRINCIPAL_NAME.get(principal.toString());
    logError(msg);
    w.write("\" isInitiator=false;");
    w.newLine();
    w.write("};");
    w.newLine();
    w.flush();
    w.close();
    return configFileName;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler() {
    logout();
    if(configuration != null)
    {
      configuration.removeGSSAPIChangeListener(this);
    }
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_GSSAPI);
    clearProperties();
    Message msg = INFO_GSSAPI_STOPPED.get();
    logError(msg);
  }


private void clearProperties() {
  System.clearProperty(KRBV_PROPERTY_KDC);
  System.clearProperty(KRBV_PROPERTY_REALM);
  System.clearProperty(JAAS_PROPERTY_CONFIG_FILE);
  System.clearProperty(JAAS_PROPERTY_SUBJECT_CREDS_ONLY);
}

  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOp)
  {
    ClientConnection clientConnection = bindOp.getClientConnection();
    if (clientConnection == null)
    {
      Message message = ERR_SASLGSSAPI_NO_CLIENT_CONNECTION.get();
      bindOp.setAuthFailureReason(message);
      bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
      return;
    }
    ClientConnection clientConn = bindOp.getClientConnection();
    SASLContext saslContext = (SASLContext) clientConn.getSASLAuthStateInfo();
    if (saslContext == null) {
      try {
        saslContext = SASLContext.createSASLContext(saslProps, serverFQDN,
                                  SASL_MECHANISM_GSSAPI, identityMapper);
      } catch (SaslException ex) {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        Message msg;
        GSSException gex = (GSSException) ex.getCause();
        if(gex != null) {
          msg = ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_GSSAPI,
              getGSSExceptionMessage(gex));
        } else {
          msg = ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_GSSAPI,
              getExceptionMessage(ex));
        }
        clientConn.setSASLAuthStateInfo(null);
        bindOp.setAuthFailureReason(msg);
        bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
        return;
      }
    }
    try
    {
      saslContext.performAuthentication(getLoginContext(), bindOp);
    }
    catch (LoginException ex)
    {
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      Message message = ERR_SASLGSSAPI_CANNOT_CREATE_LOGIN_CONTEXT
            .get(getExceptionMessage(ex));
      // Log a configuration error.
      logError(message);
      clientConn.setSASLAuthStateInfo(null);
      bindOp.setAuthFailureReason(message);
      bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
      return;

    }
  }


  /**
   * Get the underlying GSSException messages that really tell what the
   * problem is. The major code is the GSS-API status and the minor is the
   * mechanism specific error.
   *
   * @param gex The GSSExcption thrown.
   *
   * @return The message containing the major and (optional) minor codes and
   *         strings.
   */
  public static Message getGSSExceptionMessage(GSSException gex) {
    MessageBuilder message = new MessageBuilder();
    message.append("major code (" + Integer.valueOf(gex.getMajor()).toString()
                  + ") " +  gex.getMajorString());
    if(gex.getMinor() != 0)
      message.append(", minor code (" +
                      Integer.valueOf(gex.getMinor()).toString()
                       +  ") "  + gex.getMinorString());
    return message.toMessage();
  }

  /**
   * Retrieves the user account for the user associated with the
   * provided authorization ID.
   *
   * @param bindOperation
   *          The bind operation from which the provided authorization
   *          ID was derived.
   * @param authzID
   *          The authorization ID for which to retrieve the
   *          associated user.
   * @return The user entry for the user with the specified
   *         authorization ID, or {@code null} if none is identified.
   * @throws DirectoryException
   *           If a problem occurs while searching the directory for
   *           the associated user, or if multiple matching entries
   *           are found.
   */
  public Entry getUserForAuthzID(BindOperation bindOperation, String authzID)
      throws DirectoryException
  {
    return identityMapper.getEntryForID(authzID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {
    // This is not a password-based mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {
    // This may be considered a secure mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(
      SASLMechanismHandlerCfg configuration, List<Message> unacceptableReasons)
  {
    GSSAPISASLMechanismHandlerCfg newConfig =
      (GSSAPISASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(newConfig, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      GSSAPISASLMechanismHandlerCfg newConfiguration,
      List<Message> unacceptableReasons) {
    boolean isAcceptable = true;

    try
    {
      getFQDN(newConfiguration);
    }
    catch (UnknownHostException ex)
    {
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      Message message = ERR_SASL_CANNOT_GET_SERVER_FQDN.get(String
          .valueOf(configEntryDN), getExceptionMessage(ex));
      unacceptableReasons.add(message);
      isAcceptable = false;
    }

    String keyTabFilePath = newConfiguration.getKeytab();
    if(keyTabFilePath == null) {
      String home = System.getProperty("user.home");
      String sep = System.getProperty("file.separator");
      keyTabFilePath = home+sep+"krb5.keytab";
    }
    File keyTabFile = new File(keyTabFilePath);
    if(!keyTabFile.exists()) {
      Message message = ERR_SASL_GSSAPI_KEYTAB_INVALID.get(keyTabFilePath);
      unacceptableReasons.add(message);
      if (debugEnabled())
        TRACER.debugError(message.toString());
      isAcceptable = false;
    }

    String kdcAddress = newConfiguration.getKdcAddress();
    String realm = newConfiguration.getRealm();
    if ((kdcAddress != null && realm == null)
        || (kdcAddress == null && realm != null))
    {
      Message message = ERR_SASLGSSAPI_KDC_REALM_NOT_DEFINED.get();
      unacceptableReasons.add(message);
      if (debugEnabled())
        TRACER.debugError(message.toString());
      isAcceptable = false;
    }

    return isAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      GSSAPISASLMechanismHandlerCfg newConfiguration) {
    ArrayList<Message> messages = new ArrayList<Message>();
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    try
    {
      logout();
      clearProperties();
      initialize(newConfiguration);
      this.configuration = newConfiguration;
    }
    catch (InitializationException ex) {
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      Message message = ex.getMessageObject();
      messages.add(message);
      clearProperties();
      resultCode = ResultCode.OTHER;
    } catch (UnknownHostException ex) {
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        Message message = ERR_SASL_CANNOT_GET_SERVER_FQDN.get(String
          .valueOf(configEntryDN), getExceptionMessage(ex));
      messages.add(message);
      clearProperties();
      resultCode = ResultCode.OTHER;
    } catch (IOException ex) {
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      Message message = ERR_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG
        .get(getExceptionMessage(ex));
      messages.add(message);
      clearProperties();
      resultCode = ResultCode.OTHER;
    }
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

/**
 * Try to initialize the GSSAPI mechanism handler with the specified config.
 *
 * @param config The configuration to use.
 *
 * @throws UnknownHostException If a host name does not resolve.
 * @throws IOException If there was a problem creating the login file.
 * @throws LoginException If the context could not login.
 * @throws InitializationException If the keytab file does not exist.
 */
private void initialize(GSSAPISASLMechanismHandlerCfg config)
throws UnknownHostException,
       IOException,
       InitializationException {
    configEntryDN = config.dn();
    DN identityMapperDN = config.getIdentityMapperDN();
    identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
    serverFQDN = getFQDN(config);
    Message msg = INFO_GSSAPI_SERVER_FQDN.get(serverFQDN);
    logError(msg);
    saslProps = new HashMap<String, String>();
    saslProps.put(Sasl.QOP, getQOP(config));
    saslProps.put(Sasl.REUSE, "false");
    String configFileName = configureLoginConfFile(config);
    System.setProperty(JAAS_PROPERTY_CONFIG_FILE, configFileName);
    System.setProperty(JAAS_PROPERTY_SUBJECT_CREDS_ONLY, "false");
    getKdcRealm(config);
}

  /**
   * Retrieves the QOP (quality-of-protection) from the specified
   * configuration.
   *
   * @param configuration
   *          The new configuration to use.
   * @return A string representing the quality-of-protection.
   */
  private String getQOP(GSSAPISASLMechanismHandlerCfg configuration)
  {
    QualityOfProtection QOP = configuration.getQualityOfProtection();
    if (QOP.equals(QualityOfProtection.CONFIDENTIALITY))
      return "auth-conf";
    else if (QOP.equals(QualityOfProtection.INTEGRITY))
      return "auth-int";
    else
      return "auth";
  }
}

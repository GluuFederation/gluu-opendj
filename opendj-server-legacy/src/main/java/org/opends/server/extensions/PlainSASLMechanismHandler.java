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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.List;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.PlainSASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;

/**
 * This class provides an implementation of a SASL mechanism that uses
 * plain-text authentication.  It is based on the proposal defined in
 * draft-ietf-sasl-plain-08 in which the SASL credentials are in the form:
 * <BR>
 * <BLOCKQUOTE>[authzid] UTF8NULL authcid UTF8NULL passwd</BLOCKQUOTE>
 * <BR>
 * Note that this is a weak mechanism by itself and does not offer any
 * protection for the password, so it may need to be used in conjunction with a
 * connection security provider to prevent exposing the password.
 */
public class PlainSASLMechanismHandler
       extends SASLMechanismHandler<PlainSASLMechanismHandlerCfg>
       implements ConfigurationChangeListener<
                       PlainSASLMechanismHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The identity mapper that will be used to map ID strings to user entries.*/
  private IdentityMapper<?> identityMapper;

  /** The current configuration for this SASL mechanism handler. */
  private PlainSASLMechanismHandlerCfg currentConfig;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public PlainSASLMechanismHandler()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializeSASLMechanismHandler(
                   PlainSASLMechanismHandlerCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addPlainChangeListener(this);
    currentConfig = configuration;


    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_PLAIN, this);
  }



  /** {@inheritDoc} */
  @Override
  public void finalizeSASLMechanismHandler()
  {
    currentConfig.removePlainChangeListener(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_PLAIN);
  }




  /** {@inheritDoc} */
  @Override
  public void processSASLBind(BindOperation bindOperation)
  {
    // Get the SASL credentials provided by the user and decode them.
    String authzID  = null;
    String authcID  = null;
    String password = null;

    ByteString saslCredentials = bindOperation.getSASLCredentials();
    if (saslCredentials == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_NO_SASL_CREDENTIALS.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    String credString = saslCredentials.toString();
    int    length     = credString.length();
    int    nullPos1   = credString.indexOf('\u0000');
    if (nullPos1 < 0)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_NO_NULLS_IN_CREDENTIALS.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    if (nullPos1 > 0)
    {
      authzID = credString.substring(0, nullPos1);
    }


    int nullPos2 = credString.indexOf('\u0000', nullPos1+1);
    if (nullPos2 < 0)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_NO_SECOND_NULL.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    if (nullPos2 == (nullPos1+1))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_ZERO_LENGTH_AUTHCID.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    if (nullPos2 == (length-1))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_ZERO_LENGTH_PASSWORD.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    authcID  = credString.substring(nullPos1+1, nullPos2);
    password = credString.substring(nullPos2+1);


    // Get the user entry for the authentication ID.  Allow for an
    // authentication ID that is just a username (as per the SASL PLAIN spec),
    // but also allow a value in the authzid form specified in RFC 2829.
    Entry  userEntry    = null;
    String lowerAuthcID = toLowerCase(authcID);
    if (lowerAuthcID.startsWith("dn:"))
    {
      // Try to decode the user DN and retrieve the corresponding entry.
      DN userDN;
      try
      {
        userDN = DN.valueOf(authcID.substring(3));
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        LocalizableMessage message = ERR_SASLPLAIN_CANNOT_DECODE_AUTHCID_AS_DN.get(
                authcID, de.getMessageObject());
        bindOperation.setAuthFailureReason(message);
        return;
      }

      if (userDN.isRootDN())
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        LocalizableMessage message = ERR_SASLPLAIN_AUTHCID_IS_NULL_DN.get();
        bindOperation.setAuthFailureReason(message);
        return;
      }

      DN rootDN = DirectoryServer.getActualRootBindDN(userDN);
      if (rootDN != null)
      {
        userDN = rootDN;
      }

      try
      {
        userEntry = DirectoryServer.getEntry(userDN);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        LocalizableMessage message = ERR_SASLPLAIN_CANNOT_GET_ENTRY_BY_DN.get(userDN, de.getMessageObject());
        bindOperation.setAuthFailureReason(message);
        return;
      }
    }
    else
    {
      // Use the identity mapper to resolve the username to an entry.
      if (lowerAuthcID.startsWith("u:"))
      {
        authcID = authcID.substring(2);
      }

      try
      {
        userEntry = identityMapper.getEntryForID(authcID);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        LocalizableMessage message = ERR_SASLPLAIN_CANNOT_MAP_USERNAME.get(authcID, de.getMessageObject());
        bindOperation.setAuthFailureReason(message);
        return;
      }
    }


    // At this point, we should have a user entry.  If we don't then fail.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_NO_MATCHING_ENTRIES.get(authcID);
      bindOperation.setAuthFailureReason(message);
      return;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    // If an authorization ID was provided, then make sure that it is
    // acceptable.
    Entry authZEntry = userEntry;
    if (authzID != null)
    {
      String lowerAuthzID = toLowerCase(authzID);
      if (lowerAuthzID.startsWith("dn:"))
      {
        DN authzDN;
        try
        {
          authzDN = DN.valueOf(authzID.substring(3));
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_INVALID_DN.get(
                  authzID, de.getMessageObject());
          bindOperation.setAuthFailureReason(message);
          return;
        }

        DN actualAuthzDN = DirectoryServer.getActualRootBindDN(authzDN);
        if (actualAuthzDN != null)
        {
          authzDN = actualAuthzDN;
        }

        if (! authzDN.equals(userEntry.getName()))
        {
          AuthenticationInfo tempAuthInfo =
            new AuthenticationInfo(userEntry,
                     DirectoryServer.isRootDN(userEntry.getName()));
          InternalClientConnection tempConn =
               new InternalClientConnection(tempAuthInfo);
          if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOperation))
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_INSUFFICIENT_PRIVILEGES.get(userEntry.getName());
            bindOperation.setAuthFailureReason(message);
            return;
          }

          if (authzDN.isRootDN())
          {
            authZEntry = null;
          }
          else
          {
            try
            {
              authZEntry = DirectoryServer.getEntry(authzDN);
              if (authZEntry == null)
              {
                bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_NO_SUCH_ENTRY.get(authzDN);
                bindOperation.setAuthFailureReason(message);
                return;
              }
            }
            catch (DirectoryException de)
            {
              logger.traceException(de);

              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_CANNOT_GET_ENTRY.get(authzDN, de.getMessageObject());
              bindOperation.setAuthFailureReason(message);
              return;
            }
          }
        }
      }
      else
      {
        String idStr;
        if (lowerAuthzID.startsWith("u:"))
        {
          idStr = authzID.substring(2);
        }
        else
        {
          idStr = authzID;
        }

        if (idStr.length() == 0)
        {
          authZEntry = null;
        }
        else
        {
          try
          {
            authZEntry = identityMapper.getEntryForID(idStr);
            if (authZEntry == null)
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_NO_MAPPED_ENTRY.get(
                      authzID);
              bindOperation.setAuthFailureReason(message);
              return;
            }
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_CANNOT_MAP_AUTHZID.get(
                    authzID, de.getMessageObject());
            bindOperation.setAuthFailureReason(message);
            return;
          }
        }

        if (authZEntry == null || !authZEntry.getName().equals(userEntry.getName()))
        {
          AuthenticationInfo tempAuthInfo =
            new AuthenticationInfo(userEntry,
                     DirectoryServer.isRootDN(userEntry.getName()));
          InternalClientConnection tempConn =
               new InternalClientConnection(tempAuthInfo);
          if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOperation))
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            LocalizableMessage message = ERR_SASLPLAIN_AUTHZID_INSUFFICIENT_PRIVILEGES.get(userEntry.getName());
            bindOperation.setAuthFailureReason(message);
            return;
          }
        }
      }
    }


    // Get the password policy for the user and use it to determine if the
    // provided password was correct.
    try
    {
      // FIXME: we should store store the auth state in with the bind operation
      // so that any state updates, such as cached passwords, are persisted to
      // the user's entry when the bind completes.
      AuthenticationPolicyState authState = AuthenticationPolicyState.forUser(
          userEntry, false);

      if (authState.isDisabled())
      {
        // Check to see if the user is administratively disabled or locked.
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
        LocalizableMessage message = ERR_BIND_OPERATION_ACCOUNT_DISABLED.get();
        bindOperation.setAuthFailureReason(message);
        return;
      }

      if (!authState.passwordMatches(ByteString.valueOf(password)))
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
        LocalizableMessage message = ERR_SASLPLAIN_INVALID_PASSWORD.get();
        bindOperation.setAuthFailureReason(message);
        return;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLPLAIN_CANNOT_CHECK_PASSWORD_VALIDITY.get(userEntry.getName(), e);
      bindOperation.setAuthFailureReason(message);
      return;
    }


    // If we've gotten here, then the authentication was successful.
    bindOperation.setResultCode(ResultCode.SUCCESS);

    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry, authZEntry, SASL_MECHANISM_PLAIN,
                                bindOperation.getSASLCredentials(),
                                DirectoryServer.isRootDN(userEntry.getName()));
    bindOperation.setAuthenticationInfo(authInfo);
    return;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isPasswordBased(String mechanism)
  {
    // This is a password-based mechanism.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isSecure(String mechanism)
  {
    // This is not a secure mechanism.
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    PlainSASLMechanismHandlerCfg config =
         (PlainSASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      PlainSASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
              PlainSASLMechanismHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
    currentConfig  = configuration;

    return ccr;
  }
}

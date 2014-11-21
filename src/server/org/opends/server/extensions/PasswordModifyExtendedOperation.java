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
 */
package org.opends.server.extensions;

import java.util.*;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ExtendedOperationHandlerCfg;
import org.opends.server.admin.std.server.
            PasswordModifyExtendedOperationHandlerCfg;
import org.opends.server.api.*;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.PasswordPolicyWarningType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements the password modify extended operation defined in RFC
 * 3062.  It includes support for requiring the user's current password as well
 * as for generating a new password if none was provided.
 */
public class PasswordModifyExtendedOperation
       extends ExtendedOperationHandler<
                    PasswordModifyExtendedOperationHandlerCfg>
       implements ConfigurationChangeListener<
                    PasswordModifyExtendedOperationHandlerCfg>
{
  // The following attachments may be used by post-op plugins (e.g. Samba) in
  // order to avoid re-decoding the request parameters and also to enforce
  // atomicity.

  /**
   * The name of the attachment which will be used to store the fully resolved
   * target entry.
   */
  public static final String AUTHZ_DN_ATTACHMENT;

  /**
   * The name of the attachment which will be used to store the password
   * attribute.
   */
  public static final String PWD_ATTRIBUTE_ATTACHMENT;

  /**
   * The clear text password, which may not be present if the provided password
   * was pre-encoded.
   */
  public static final String CLEAR_PWD_ATTACHMENT;

  /**
   * A list containing the encoded passwords: plugins can perform changes
   * atomically via CAS.
   */
  public static final String ENCODED_PWD_ATTACHMENT;

  static
  {
    final String PREFIX = PasswordModifyExtendedOperation.class.getName();
    AUTHZ_DN_ATTACHMENT = PREFIX + ".AUTHZ_DN";
    PWD_ATTRIBUTE_ATTACHMENT = PREFIX + ".PWD_ATTRIBUTE";
    CLEAR_PWD_ATTACHMENT = PREFIX + ".CLEAR_PWD";
    ENCODED_PWD_ATTACHMENT = PREFIX + ".ENCODED_PWD";
  }

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The current configuration state.
  private PasswordModifyExtendedOperationHandlerCfg currentConfig;

  // The DN of the identity mapper.
  private DN identityMapperDN;

  // The reference to the identity mapper.
  private IdentityMapper<?> identityMapper;

  // The default set of supported control OIDs for this extended
  private Set<String> supportedControlOIDs = new HashSet<String>(0);



  /**
   * Create an instance of this password modify extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public PasswordModifyExtendedOperation()
  {
    super();

  }




  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param   config      The configuration that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  @Override
  public void initializeExtendedOperationHandler(
       PasswordModifyExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    try
    {
      identityMapperDN = config.getIdentityMapperDN();
      identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
      if (identityMapper == null)
      {
        Message message = ERR_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER.get(
            String.valueOf(identityMapperDN), String.valueOf(config.dn()));
        throw new ConfigException(message);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER.get(
          String.valueOf(config.dn()), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }


    supportedControlOIDs = new HashSet<String>();
    supportedControlOIDs.add(OID_LDAP_NOOP_OPENLDAP_ASSIGNED);
    supportedControlOIDs.add(OID_PASSWORD_POLICY_CONTROL);


    // Save this configuration for future reference.
    currentConfig = config;

    // Register this as a change listener.
    config.addPasswordModifyChangeListener(this);

    DirectoryServer.registerSupportedExtension(OID_PASSWORD_MODIFY_REQUEST,
                                               this);

    registerControlsAndFeatures();
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  @Override
  public void finalizeExtendedOperationHandler()
  {
    currentConfig.removePasswordModifyChangeListener(this);

    DirectoryServer.deregisterSupportedExtension(OID_PASSWORD_MODIFY_REQUEST);

    deregisterControlsAndFeatures();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<String> getSupportedControls()
  {
    return supportedControlOIDs;
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  @Override
  public void processExtendedOperation(ExtendedOperation operation)
  {
    // Initialize the variables associated with components that may be included
    // in the request.
    ByteString userIdentity = null;
    ByteString oldPassword  = null;
    ByteString newPassword  = null;


    // Look at the set of controls included in the request, if there are any.
    boolean                   noOpRequested        = false;
    boolean                   pwPolicyRequested    = false;
    int                       pwPolicyWarningValue = 0;
    PasswordPolicyErrorType   pwPolicyErrorType    = null;
    PasswordPolicyWarningType pwPolicyWarningType  = null;
    List<Control> controls = operation.getRequestControls();
    if (controls != null)
    {
      for (Control c : controls)
      {
        String oid = c.getOID();
        if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
        {
          noOpRequested = true;
        }
        else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
        {
          pwPolicyRequested = true;
        }
      }
    }


    // Parse the encoded request, if there is one.
    ByteString requestValue = operation.getRequestValue();
    if (requestValue != null)
    {
      try
      {
        ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();
        if(reader.hasNextElement() &&
            reader.peekType() == TYPE_PASSWORD_MODIFY_USER_ID)
        {
          userIdentity = reader.readOctetString();
        }
        if(reader.hasNextElement() &&
            reader.peekType() == TYPE_PASSWORD_MODIFY_OLD_PASSWORD)
        {
          oldPassword = reader.readOctetString();
        }
        if(reader.hasNextElement() &&
            reader.peekType() == TYPE_PASSWORD_MODIFY_NEW_PASSWORD)
        {
          newPassword = reader.readOctetString();
        }
        reader.readEndSequence();
      }
      catch (Exception ae)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ae);
        }

        operation.setResultCode(ResultCode.PROTOCOL_ERROR);

        Message message = ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST.get(
                getExceptionMessage(ae));
        operation.appendErrorMessage(message);

        return;
      }
    }


    // Get the entry for the user that issued the request.
    Entry requestorEntry = operation.getAuthorizationEntry();


    // See if a user identity was provided.  If so, then try to resolve it to
    // an actual user.
    DN    userDN    = null;
    Entry userEntry = null;
    Lock  userLock  = null;

    try
    {
      if (userIdentity == null)
      {
        // This request must be targeted at changing the password for the
        // currently-authenticated user.  Make sure that the user actually is
        // authenticated.
        ClientConnection   clientConnection = operation.getClientConnection();
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if ((! authInfo.isAuthenticated()) || (requestorEntry == null))
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          operation.appendErrorMessage(
                  ERR_EXTOP_PASSMOD_NO_AUTH_OR_USERID.get());

          return;
        }


        // Retrieve a write lock on that user's entry.
        userDN = requestorEntry.getDN();

        userLock = LockManager.lockWrite(userDN);
        if (userLock == null)
        {
          operation.setResultCode(ResultCode.BUSY);
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_LOCK_USER_ENTRY
              .get(String.valueOf(userDN)));
          return;
        }


        userEntry = requestorEntry;
      }
      else
      {
        // There was a userIdentity field in the request.
        String authzIDStr      = userIdentity.toString();
        String lowerAuthzIDStr = toLowerCase(authzIDStr);
        if (lowerAuthzIDStr.startsWith("dn:"))
        {
          try
          {
            userDN = DN.decode(authzIDStr.substring(3));
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            operation.setResultCode(ResultCode.INVALID_DN_SYNTAX);

            operation.appendErrorMessage(
                    ERR_EXTOP_PASSMOD_CANNOT_DECODE_AUTHZ_DN.get(authzIDStr));

            return;
          }

          // If the provided DN is an alternate DN for a root user, then replace
          // it with the actual root DN.
          DN actualRootDN = DirectoryServer.getActualRootBindDN(userDN);
          if (actualRootDN != null)
          {
            userDN = actualRootDN;
          }

          userEntry = getEntryByDN(operation, userDN);
          if (userEntry == null)
          {
            return;
          }
        }
        else if (lowerAuthzIDStr.startsWith("u:"))
        {
          try
          {
            userEntry = identityMapper.getEntryForID(authzIDStr.substring(2));
            if (userEntry == null)
            {
              operation.setResultCode(ResultCode.NO_SUCH_OBJECT);
              operation.appendErrorMessage(
                      ERR_EXTOP_PASSMOD_CANNOT_MAP_USER.get(authzIDStr));
              return;
            }
            else
            {
              userDN = userEntry.getDN();
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            //Encountered an exception while resolving identity.
            operation.setResultCode(de.getResultCode());
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_ERROR_MAPPING_USER
                    .get(authzIDStr,de.getMessageObject()));
            return;
          }
        }
        // the userIdentity provided does not follow Authorization Identity
        // form. RFC3062 declaration "may or may not be an LDAPDN" allows
        // for pretty much anything in that field. we gonna try to parse it
        // as DN first then if that fails as user ID.
        else
        {
          try
          {
            userDN = DN.decode(authzIDStr);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }
            // IGNORE.
          }

          if ((userDN != null) && (!userDN.isNullDN())) {
            // If the provided DN is an alternate DN for a root user,
            // then replace it with the actual root DN.
            DN actualRootDN = DirectoryServer.getActualRootBindDN(userDN);
            if (actualRootDN != null) {
              userDN = actualRootDN;
            }
            userEntry = getEntryByDN(operation, userDN);
          } else {
            try
            {
              userEntry = identityMapper.getEntryForID(authzIDStr);
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }
              // IGNORE.
            }
          }

          if (userEntry == null) {
            // The userIdentity was invalid.
            operation.setResultCode(ResultCode.PROTOCOL_ERROR);
            operation.appendErrorMessage(
              ERR_EXTOP_PASSMOD_INVALID_AUTHZID_STRING.get(authzIDStr));
            return;
          }
          else
          {
            userDN = userEntry.getDN();
          }
        }
      }


      // At this point, we should have the user entry.  Get the associated
      // password policy.
      PasswordPolicyState pwPolicyState;
      try
      {
        AuthenticationPolicy policy = AuthenticationPolicy.forUser(userEntry,
            false);
        if (!policy.isPasswordPolicy())
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_ACCOUNT_NOT_LOCAL
              .get(String.valueOf(userDN)));
          return;
        }
        pwPolicyState = (PasswordPolicyState) policy
          .createAuthenticationPolicyState(userEntry);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        operation.setResultCode(DirectoryServer.getServerErrorResultCode());

        operation.appendErrorMessage(
                ERR_EXTOP_PASSMOD_CANNOT_GET_PW_POLICY.get(
                        String.valueOf(userDN),
                        de.getMessageObject()));
        return;
      }



      // Determine whether the user is changing his own password or if it's an
      // administrative reset.  If it's an administrative reset, then the
      // requester must have the PASSWORD_RESET privilege.
      boolean selfChange;
      if (userIdentity == null)
      {
        selfChange = true;
      }
      else if (requestorEntry == null)
      {
        selfChange = (oldPassword != null);
      }
      else
      {
        selfChange = userDN.equals(requestorEntry.getDN());
      }

      if (! selfChange)
      {
        ClientConnection clientConnection = operation.getClientConnection();
        if (! clientConnection.hasPrivilege(Privilege.PASSWORD_RESET,
                                            operation))
        {
          operation.appendErrorMessage(
                  ERR_EXTOP_PASSMOD_INSUFFICIENT_PRIVILEGES.get());
          operation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          return;
        }
      }


      // See if the account is locked.  If so, then reject the request.
      if (pwPolicyState.isDisabled())
      {
        if (pwPolicyRequested)
        {
          pwPolicyErrorType =
               PasswordPolicyErrorType.ACCOUNT_LOCKED;
          operation.addResponseControl(
               new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                 pwPolicyWarningValue,
                                                 pwPolicyErrorType));
        }

        Message message = ERR_EXTOP_PASSMOD_ACCOUNT_DISABLED.get();

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(message);

        return;
      }
      else if (selfChange &&
               (pwPolicyState.lockedDueToFailures() ||
                pwPolicyState.lockedDueToIdleInterval() ||
                pwPolicyState.lockedDueToMaximumResetAge()))
      {
        if (pwPolicyRequested)
        {
          pwPolicyErrorType =
               PasswordPolicyErrorType.ACCOUNT_LOCKED;
          operation.addResponseControl(
               new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                 pwPolicyWarningValue,
                                                 pwPolicyErrorType));
        }

        Message message = ERR_EXTOP_PASSMOD_ACCOUNT_LOCKED.get();

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(message);

        return;
      }


      // If the current password was provided, then we'll need to verify whether
      // it was correct.  If it wasn't provided but this is a self change, then
      // make sure that's OK.
      if (oldPassword == null)
      {
        if (selfChange
            && pwPolicyState.getAuthenticationPolicy()
                .isPasswordChangeRequiresCurrentPassword())
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          operation.appendErrorMessage(
                  ERR_EXTOP_PASSMOD_REQUIRE_CURRENT_PW.get());

          if (pwPolicyRequested)
          {
            pwPolicyErrorType =
                 PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD;
            operation.addResponseControl(
                 new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                   pwPolicyWarningValue,
                                                   pwPolicyErrorType));
          }

          return;
        }
      }
      else
      {
        if (pwPolicyState.getAuthenticationPolicy()
            .isRequireSecureAuthentication()
            && (!operation.getClientConnection().isSecure()))
        {
          operation.setResultCode(ResultCode.CONFIDENTIALITY_REQUIRED);
          operation.addAdditionalLogItem(AdditionalLogItem.quotedKeyValue(
              getClass(), "additionalInfo",
              ERR_EXTOP_PASSMOD_SECURE_AUTH_REQUIRED.get()));
          return;
        }

        if (pwPolicyState.passwordMatches(oldPassword))
        {
          pwPolicyState.setLastLoginTime();
        }
        else
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);
          operation.addAdditionalLogItem(AdditionalLogItem.quotedKeyValue(
              getClass(), "additionalInfo",
              ERR_EXTOP_PASSMOD_INVALID_OLD_PASSWORD.get()));

          pwPolicyState.updateAuthFailureTimes();
          List<Modification> mods = pwPolicyState.getModifications();
          if (! mods.isEmpty())
          {
            InternalClientConnection conn =
                 InternalClientConnection.getRootConnection();
            conn.processModify(userDN, mods);
          }

          return;
        }
      }


      // If it is a self password change and we don't allow that, then reject
      // the request.
      if (selfChange
          && (!pwPolicyState.getAuthenticationPolicy()
              .isAllowUserPasswordChanges()))
      {
        if (pwPolicyRequested)
        {
          pwPolicyErrorType =
               PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
          operation.addResponseControl(
               new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                 pwPolicyWarningValue,
                                                 pwPolicyErrorType));
        }

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

        operation.appendErrorMessage(
                ERR_EXTOP_PASSMOD_USER_PW_CHANGES_NOT_ALLOWED.get());
        return;
      }


      // If we require secure password changes and the connection isn't secure,
      // then reject the request.
      if (pwPolicyState.getAuthenticationPolicy()
          .isRequireSecurePasswordChanges()
          && (!operation.getClientConnection().isSecure()))
      {
        operation.setResultCode(ResultCode.CONFIDENTIALITY_REQUIRED);

        operation.appendErrorMessage(
                ERR_EXTOP_PASSMOD_SECURE_CHANGES_REQUIRED.get());
        return;
      }


      // If it's a self-change request and the user is within the minimum age,
      // then reject it.
      if (selfChange && pwPolicyState.isWithinMinimumAge())
      {
        if (pwPolicyRequested)
        {
          pwPolicyErrorType =
               PasswordPolicyErrorType.PASSWORD_TOO_YOUNG;
          operation.addResponseControl(
               new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                 pwPolicyWarningValue,
                                                 pwPolicyErrorType));
        }

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_IN_MIN_AGE.get());

        return;
      }


      // If the user's password is expired and it's a self-change request, then
      // see if that's OK.
      if ((selfChange && pwPolicyState.isPasswordExpired() && (!pwPolicyState
          .getAuthenticationPolicy().isAllowExpiredPasswordChanges())))
      {
        if (pwPolicyRequested)
        {
          pwPolicyErrorType =
               PasswordPolicyErrorType.PASSWORD_EXPIRED;
          operation.addResponseControl(
               new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                 pwPolicyWarningValue,
                                                 pwPolicyErrorType));
        }

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

        operation.appendErrorMessage(
                ERR_EXTOP_PASSMOD_PASSWORD_IS_EXPIRED.get());
        return;
      }



      // If the a new password was provided, then perform any appropriate
      // validation on it.  If not, then see if we can generate one.
      boolean generatedPassword = false;
      boolean isPreEncoded      = false;
      if (newPassword == null)
      {
        try
        {
          newPassword = pwPolicyState.generatePassword();
          if (newPassword == null)
          {
            operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

            operation.appendErrorMessage(
                    ERR_EXTOP_PASSMOD_NO_PW_GENERATOR.get());
            return;
          }
          else
          {
            generatedPassword = true;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          operation.setResultCode(de.getResultCode());

          operation.appendErrorMessage(
                  ERR_EXTOP_PASSMOD_CANNOT_GENERATE_PW.get(
                          de.getMessageObject()));
          return;
        }
      }
      else
      {
        if (pwPolicyState.passwordIsPreEncoded(newPassword))
        {
          // The password modify extended operation isn't intended to be invoked
          // by an internal operation or during synchronization, so we don't
          // need to check for those cases.
          isPreEncoded = true;
          if (!pwPolicyState.getAuthenticationPolicy()
              .isAllowPreEncodedPasswords())
          {
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

            operation.appendErrorMessage(
                    ERR_EXTOP_PASSMOD_PRE_ENCODED_NOT_ALLOWED.get());
            return;
          }
        }
        else
        {
          // Run the new password through the set of password validators.
          if (selfChange
              || (!pwPolicyState.getAuthenticationPolicy()
                  .isSkipValidationForAdministrators()))
          {
            HashSet<ByteString> clearPasswords;
            if (oldPassword == null)
            {
              clearPasswords =
                   new HashSet<ByteString>(pwPolicyState.getClearPasswords());
            }
            else
            {
              clearPasswords = new HashSet<ByteString>();
              clearPasswords.add(oldPassword);
              for (ByteString pw : pwPolicyState.getClearPasswords())
              {
                if (! pw.equals(oldPassword))
                {
                  clearPasswords.add(pw);
                }
              }
            }

            MessageBuilder invalidReason = new MessageBuilder();
            if (! pwPolicyState.passwordIsAcceptable(operation, userEntry,
                                                     newPassword,
                                                     clearPasswords,
                                                     invalidReason))
            {
              if (pwPolicyRequested)
              {
                pwPolicyErrorType =
                     PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
                operation.addResponseControl(
                     new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                       pwPolicyWarningValue,
                                                       pwPolicyErrorType));
              }

              operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

              operation.appendErrorMessage(
                      ERR_EXTOP_PASSMOD_UNACCEPTABLE_PW.get(
                              String.valueOf(invalidReason)));
              return;
            }
          }


          // Prepare to update the password history, if necessary.
          if (pwPolicyState.maintainHistory())
          {
            if (pwPolicyState.isPasswordInHistory(newPassword))
            {
              if (selfChange || (! pwPolicyState.getAuthenticationPolicy().
                                      isSkipValidationForAdministrators()))
              {
                operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                operation.appendErrorMessage(
                        ERR_EXTOP_PASSMOD_PW_IN_HISTORY.get());
                return;
              }
            }
            else
            {
              pwPolicyState.updatePasswordHistory();
            }
          }
        }
      }


      // Get the encoded forms of the new password.
      List<ByteString> encodedPasswords;
      if (isPreEncoded)
      {
        encodedPasswords = new ArrayList<ByteString>(1);
        encodedPasswords.add(newPassword);
      }
      else
      {
        try
        {
          encodedPasswords = pwPolicyState.encodePassword(newPassword);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          operation.setResultCode(de.getResultCode());

          operation.appendErrorMessage(
                  ERR_EXTOP_PASSMOD_CANNOT_ENCODE_PASSWORD.get(
                          de.getMessageObject()));
          return;
        }
      }


      // If the current password was provided, then remove all matching values
      // from the user's entry and replace them with the new password.
      // Otherwise replace all password values.
      AttributeType attrType = pwPolicyState.getAuthenticationPolicy()
          .getPasswordAttribute();
      List<Modification> modList = new ArrayList<Modification>();
      if (oldPassword != null)
      {
        // Remove all existing encoded values that match the old password.
        Set<AttributeValue> existingValues = pwPolicyState.getPasswordValues();
        LinkedHashSet<AttributeValue> deleteValues =
             new LinkedHashSet<AttributeValue>(existingValues.size());
        if (pwPolicyState.getAuthenticationPolicy().isAuthPasswordSyntax())
        {
          for (AttributeValue v : existingValues)
          {
            try
            {
              StringBuilder[] components =
                 AuthPasswordSyntax.decodeAuthPassword(v.getValue().toString());
              PasswordStorageScheme<?> scheme =
                   DirectoryServer.getAuthPasswordStorageScheme(
                        components[0].toString());
              if (scheme == null)
              {
                // The password is encoded using an unknown scheme.  Remove it
                // from the user's entry.
                deleteValues.add(v);
              }
              else
              {
                if (scheme.authPasswordMatches(oldPassword,
                                               components[1].toString(),
                                               components[2].toString()))
                {
                  deleteValues.add(v);
                }
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              // We couldn't decode the provided password value, so remove it
              // from the user's entry.
              deleteValues.add(v);
            }
          }
        }
        else
        {
          for (AttributeValue v : existingValues)
          {
            try
            {
              String[] components =
                 UserPasswordSyntax.decodeUserPassword(v.getValue().toString());
              PasswordStorageScheme<?> scheme =
                   DirectoryServer.getPasswordStorageScheme(
                        toLowerCase(components[0]));
              if (scheme == null)
              {
                // The password is encoded using an unknown scheme.  Remove it
                // from the user's entry.
                deleteValues.add(v);
              }
              else
              {
                if (scheme.passwordMatches(oldPassword,
                    ByteString.valueOf(components[1])))
                {
                  deleteValues.add(v);
                }
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              // We couldn't decode the provided password value, so remove it
              // from the user's entry.
              deleteValues.add(v);
            }
          }
        }

        AttributeBuilder builder = new AttributeBuilder(attrType);
        builder.addAll(deleteValues);
        Attribute deleteAttr = builder.toAttribute();
        modList.add(new Modification(ModificationType.DELETE, deleteAttr));


        builder = new AttributeBuilder(attrType);
        builder.addAll(toAttributeValues(attrType, encodedPasswords));
        Attribute addAttr = builder.toAttribute();
        modList.add(new Modification(ModificationType.ADD, addAttr));
      }
      else
      {
        AttributeBuilder builder = new AttributeBuilder(attrType);
        builder.addAll(toAttributeValues(attrType, encodedPasswords));
        Attribute addAttr = builder.toAttribute();
        modList.add(new Modification(ModificationType.REPLACE, addAttr));
      }


      // Update the password changed time for the user entry.
      pwPolicyState.setPasswordChangedTime();


      // If the password was changed by an end user, then clear any reset flag
      // that might exist.  If the password was changed by an administrator,
      // then see if we need to set the reset flag.
      if (selfChange)
      {
        pwPolicyState.setMustChangePassword(false);
      }
      else
      {
        pwPolicyState.setMustChangePassword(
             pwPolicyState.getAuthenticationPolicy().isForceChangeOnReset());
      }


      // Clear any record of grace logins, auth failures, and expiration
      // warnings.
      pwPolicyState.clearFailureLockout();
      pwPolicyState.clearGraceLoginTimes();
      pwPolicyState.clearWarnedTime();


      // If the LDAP no-op control was included in the request, then set the
      // appropriate response.  Otherwise, process the operation.
      if (noOpRequested)
      {
        operation.appendErrorMessage(WARN_EXTOP_PASSMOD_NOOP.get());

        operation.setResultCode(ResultCode.NO_OPERATION);
      }
      else
      {
        if (selfChange && (requestorEntry == null))
        {
          requestorEntry = userEntry;
        }

        // Get an internal connection and use it to perform the modification.
        boolean isRoot = DirectoryServer.isRootDN(requestorEntry.getDN());
        AuthenticationInfo authInfo = new AuthenticationInfo(requestorEntry,
                                                             isRoot);
        InternalClientConnection internalConnection = new
             InternalClientConnection(authInfo);

        ModifyOperation modifyOperation =
             internalConnection.processModify(userDN, modList);
        ResultCode resultCode = modifyOperation.getResultCode();
        if (resultCode != ResultCode.SUCCESS)
        {
          operation.setResultCode(resultCode);
          operation.setErrorMessage(modifyOperation.getErrorMessage());
          operation.setReferralURLs(modifyOperation.getReferralURLs());
          return;
        }


        // If there were any password policy state changes, we need to apply
        // them using a root connection because the end user may not have
        // sufficient access to apply them.  This is less efficient than
        // doing them all in the same modification, but it's safer.
        List<Modification> pwPolicyMods = pwPolicyState.getModifications();
        if (! pwPolicyMods.isEmpty())
        {
          InternalClientConnection rootConnection =
               InternalClientConnection.getRootConnection();
          ModifyOperation modOp =
               rootConnection.processModify(userDN, pwPolicyMods);
          if (modOp.getResultCode() != ResultCode.SUCCESS)
          {
            // At this point, the user's password is already changed so there's
            // not much point in returning a non-success result.  However, we
            // should at least log that something went wrong.
            Message message = WARN_EXTOP_PASSMOD_CANNOT_UPDATE_PWP_STATE.get(
                    String.valueOf(userDN),
                    String.valueOf(modOp.getResultCode()),
                    modOp.getErrorMessage());
            ErrorLogger.logError(message);
          }
        }


        // If we've gotten here, then everything is OK, so indicate that the
        // operation was successful.
        operation.setResultCode(ResultCode.SUCCESS);

        // Save attachments for post-op plugins (e.g. Samba password plugin).
        operation.setAttachment(AUTHZ_DN_ATTACHMENT, userDN);
        operation.setAttachment(PWD_ATTRIBUTE_ATTACHMENT, pwPolicyState
            .getAuthenticationPolicy().getPasswordAttribute());
        if (!isPreEncoded)
        {
          operation.setAttachment(CLEAR_PWD_ATTACHMENT, newPassword);
        }
        operation.setAttachment(ENCODED_PWD_ATTACHMENT, encodedPasswords);

        // If a password was generated, then include it in the response.
        if (generatedPassword)
        {
          ByteStringBuilder builder = new ByteStringBuilder();
          ASN1Writer writer = ASN1.getWriter(builder);

          try
          {
          writer.writeStartSequence();
          writer.writeOctetString(TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD,
                                   newPassword);
          writer.writeEndSequence();
          }
          catch(Exception e)
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          operation.setResponseValue(builder.toByteString());
        }


        // If this was a self password change, and the client is authenticated
        // as the user whose password was changed, then clear the "must change
        // password" flag in the client connection.  Note that we're using the
        // authentication DN rather than the authorization DN in this case to
        // avoid mistakenly clearing the flag for the wrong user.
        if (selfChange && (authInfo.getAuthenticationDN() != null) &&
            (authInfo.getAuthenticationDN().equals(userDN)))
        {
          operation.getClientConnection().setMustChangePassword(false);
        }


        // If the password policy control was requested, then add the
        // appropriate response control.
        if (pwPolicyRequested)
        {
          operation.addResponseControl(
               new PasswordPolicyResponseControl(pwPolicyWarningType,
                                                 pwPolicyWarningValue,
                                                 pwPolicyErrorType));
        }

        // Handle Account Status Notifications that may be needed.
        // They are not handled by the backend for internal operations.
        List<AttributeValue> currentPasswords = null;
        if (oldPassword != null)
        {
          currentPasswords = new ArrayList<AttributeValue>(1);
          currentPasswords.add(AttributeValues
                              .create(oldPassword, oldPassword));
        }
        List<AttributeValue> newPasswords = null;
        if (newPassword != null)
        {
          newPasswords = new ArrayList<AttributeValue>(1);
          newPasswords.add(AttributeValues
                           .create(newPassword, newPassword));
        }
        if (selfChange)
        {
          Message message = INFO_MODIFY_PASSWORD_CHANGED.get();
          pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.PASSWORD_CHANGED,
            userEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false,
                  -1, currentPasswords, newPasswords));
        }
        else
        {
          Message message = INFO_MODIFY_PASSWORD_RESET.get();
          pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.PASSWORD_RESET,
            userEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false,
                  -1, currentPasswords, newPasswords));
        }
      }
    }
    finally
    {
      if (userLock != null)
      {
        LockManager.unlock(userDN, userLock);
      }
    }
  }

  private Collection<AttributeValue> toAttributeValues(AttributeType attrType,
      Collection<ByteString> values)
  {
    Set<AttributeValue> results =
        new LinkedHashSet<AttributeValue>(values.size());
    for (ByteString s : values)
    {
      results.add(AttributeValues.create(attrType, s));
    }
    return results;
  }

  /**
   * Retrieves the entry for the specified user based on the provided DN.  If
   * any problem is encountered or the requested entry does not exist, then the
   * provided operation will be updated with appropriate result information and
   * this method will return <CODE>null</CODE>.  The caller must hold a write
   * lock on the specified entry.
   *
   * @param  operation  The extended operation being processed.
   * @param  entryDN    The DN of the user entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if there was no such
   *          entry or it could not be retrieved.
   */
  private Entry getEntryByDN(ExtendedOperation operation, DN entryDN)
  {
    // Retrieve the user's entry from the directory.  If it does not exist, then
    // fail.
    try
    {
      Entry userEntry = DirectoryServer.getEntry(entryDN);

      if (userEntry == null)
      {
        operation.setResultCode(ResultCode.NO_SUCH_OBJECT);

        operation.appendErrorMessage(
                ERR_EXTOP_PASSMOD_NO_USER_ENTRY_BY_AUTHZID.get(
                        String.valueOf(entryDN)));

        // See if one of the entry's ancestors exists.
        DN parentDN = entryDN.getParentDNInSuffix();
        while (parentDN != null)
        {
          try
          {
            if (DirectoryServer.entryExists(parentDN))
            {
              operation.setMatchedDN(parentDN);
              break;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            break;
          }

          parentDN = parentDN.getParentDNInSuffix();
        }

        return null;
      }

      return userEntry;
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      operation.setResultCode(de.getResultCode());
      operation.appendErrorMessage(de.getMessageObject());
      operation.setMatchedDN(de.getMatchedDN());
      operation.setReferralURLs(de.getReferralURLs());

      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(ExtendedOperationHandlerCfg
                                                configuration,
                                           List<Message> unacceptableReasons)
  {
    PasswordModifyExtendedOperationHandlerCfg config =
         (PasswordModifyExtendedOperationHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  config          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
       PasswordModifyExtendedOperationHandlerCfg config,
       List<Message> unacceptableReasons)
  {
    // Make sure that the specified identity mapper is OK.
    try
    {
      DN mapperDN = config.getIdentityMapperDN();
      IdentityMapper<?> mapper = DirectoryServer.getIdentityMapper(mapperDN);
      if (mapper == null)
      {
        Message message = ERR_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER.get(
                String.valueOf(mapperDN),
                String.valueOf(config.dn()));
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER.get(
              String.valueOf(config.dn()),
              getExceptionMessage(e));
      unacceptableReasons.add(message);
      return false;
    }


    // If we've gotten here, then everything is OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  config      The entry containing the new configuration to
   *                          apply for this component.
   *
   * @return  Information about the result of the configuration update.
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
       PasswordModifyExtendedOperationHandlerCfg config)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Make sure that the specified identity mapper is OK.
    DN             mapperDN = null;
    IdentityMapper<?> mapper   = null;
    try
    {
      mapperDN = config.getIdentityMapperDN();
      mapper   = DirectoryServer.getIdentityMapper(mapperDN);
      if (mapper == null)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;

        messages.add(ERR_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER.get(
                String.valueOf(mapperDN),
                String.valueOf(config.dn())));
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      resultCode = DirectoryServer.getServerErrorResultCode();

      messages.add(ERR_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER.get(
              String.valueOf(config.dn()),
              getExceptionMessage(e)));
    }


    // If all of the changes were acceptable, then apply them.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (! identityMapperDN.equals(mapperDN))
      {
        identityMapper   = mapper;
        identityMapperDN = mapperDN;
      }
    }


    // Save this configuration for future reference.
    currentConfig = config;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getExtendedOperationName()
  {
    return "Password Modify";
  }
}


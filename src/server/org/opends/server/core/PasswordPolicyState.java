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
package org.opends.server.core;



import java.text.SimpleDateFormat;
import java.util.*;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn;
import org.opends.server.api.*;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a data structure for holding password policy state
 * information for a user account.
 */
public final class PasswordPolicyState extends AuthenticationPolicyState
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The string representation of the user's DN.
  private final String userDNString;

  // The password policy with which the account is associated.
  private final PasswordPolicy passwordPolicy;

  // The current time for use in all password policy calculations.
  private final long currentTime;

  // The time that the user's password was last changed.
  private long passwordChangedTime = Long.MIN_VALUE;

  // Indicates whether the user's account is expired.
  private ConditionResult isAccountExpired = ConditionResult.UNDEFINED;

  // Indicates whether the user's password is expired.
  private ConditionResult isPasswordExpired = ConditionResult.UNDEFINED;

  // Indicates whether the warning to send to the client would be the first
  // warning for the user.
  private ConditionResult isFirstWarning = ConditionResult.UNDEFINED;

  // Indicates whether the user's account is locked by the idle lockout.
  private ConditionResult isIdleLocked = ConditionResult.UNDEFINED;

  // Indicates whether the user may use a grace login if the password is expired
  // and there are one or more grace logins remaining.
  private ConditionResult mayUseGraceLogin = ConditionResult.UNDEFINED;

  // Indicates whether the user's password must be changed.
  private ConditionResult mustChangePassword = ConditionResult.UNDEFINED;

  // Indicates whether the user should be warned of an upcoming expiration.
  private ConditionResult shouldWarn = ConditionResult.UNDEFINED;

  // The number of seconds until the user's account is automatically unlocked.
  private int secondsUntilUnlock = Integer.MIN_VALUE;

  // The set of authentication failure times for this user.
  private List<Long> authFailureTimes = null;

  // The set of grace login times for this user.
  private List<Long> graceLoginTimes = null;

  // The time that the user's account should expire (or did expire).
  private long accountExpirationTime = Long.MIN_VALUE;

  // The time that the user's entry was locked due to too many authentication
  // failures.
  private long failureLockedTime = Long.MIN_VALUE;

  // The time that the user last authenticated to the Directory Server.
  private long lastLoginTime = Long.MIN_VALUE;

  // The time that the user's password should expire (or did expire).
  private long passwordExpirationTime = Long.MIN_VALUE;

  // The last required change time with which the user complied.
  private long requiredChangeTime = Long.MIN_VALUE;

  // The time that the user was first warned about an upcoming expiration.
  private long warnedTime = Long.MIN_VALUE;

  // The set of modifications that should be applied to the user's entry.
  private LinkedList<Modification> modifications
       = new LinkedList<Modification>();



  /**
   * Creates a new password policy state object with the provided information.
   * Note that this version of the constructor should only be used for testing
   * purposes when the tests should be evaluated with a fixed time rather than
   * the actual current time. For all other purposes, the other constructor
   * should be used.
   *
   * @param policy
   *          The password policy associated with the state.
   * @param userEntry
   *          The entry with the user account.
   * @param currentTime
   *          The time to use as the current time for all time-related
   *          determinations.
   */
  PasswordPolicyState(PasswordPolicy policy, Entry userEntry, long currentTime)
  {
    super(userEntry);
    this.currentTime = currentTime;
    this.userDNString     = userEntry.getDN().toString();
    this.passwordPolicy   = policy;
  }



   /**
    * Retrieves the value of the specified attribute as a string.
    *
    * @param  attributeType  The attribute type whose value should be retrieved.
    *
    * @return  The value of the specified attribute as a string, or
    *          <CODE>null</CODE> if there is no such value.
    */
  private String getValue(AttributeType attributeType)
  {
    String stringValue = null;

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        if (a.isEmpty()) continue;

        stringValue = a.iterator().next().getValue().toString();
        break ;
      }
    }

    if (stringValue == null)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning null because attribute %s does not " +
            "exist in user entry %s",
            attributeType.getNameOrOID(), userDNString);
      }
    }
    else
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning value %s for user %s",
            stringValue, userDNString);
      }
    }

    return stringValue;
  }



  /**
   * Retrieves the set of values of the specified attribute from the user's
   * entry in generalized time format.
   *
   * @param  attributeType  The attribute type whose values should be parsed as
   *                        generalized time values.
   *
   * @return  The set of generalized time values, or an empty list if there are
   *          none.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              decode a value as a generalized time.
   */
  private List<Long> getGeneralizedTimes(AttributeType attributeType)
          throws DirectoryException
  {
    ArrayList<Long> timeValues = new ArrayList<Long>();

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          try
          {
            timeValues.add(GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                                                       v.getNormalizedValue()));
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);

              TRACER.debugWarning("Unable to decode value %s for attribute %s" +
                  "in user entry %s: %s",
                  v.getValue().toString(), attributeType.getNameOrOID(),
                  userDNString, stackTraceToSingleLineString(e));
            }

            Message message = ERR_PWPSTATE_CANNOT_DECODE_GENERALIZED_TIME.
                get(v.getValue().toString(), attributeType.getNameOrOID(),
                    userDNString, String.valueOf(e));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, e);
          }
        }
      }
    }

    if (timeValues.isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning an empty list because attribute %s " +
            "does not exist in user entry %s",
            attributeType.getNameOrOID(), userDNString);
      }
    }
    return timeValues;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PasswordPolicy getAuthenticationPolicy()
  {
    return passwordPolicy;
  }



  /**
   * Retrieves the time that the password was last changed.
   *
   * @return  The time that the password was last changed.
   */
  public long getPasswordChangedTime()
  {
    if (passwordChangedTime < 0)
    {
      // Get the password changed time for the user.
      AttributeType type = DirectoryServer.getAttributeType(
          OP_ATTR_PWPOLICY_CHANGED_TIME_LC, true);

      try
      {
        passwordChangedTime = getGeneralizedTime(userEntry, type);
      }
      catch (DirectoryException e)
      {
        /*
         * The password change time could not be parsed (but has been logged in
         * the debug log). The best effort we can do from here is to a) use the
         * current time, b) use the start of the epoch (1/1/1970), or c) use the
         * create time stamp. Lets treat this problem as if the change time
         * attribute did not exist and resort to the create time stamp.
         */
      }

      if (passwordChangedTime < 0)
      {
        // Get the time that the user's account was created.
        AttributeType createTimeType = DirectoryServer.getAttributeType(
            OP_ATTR_CREATE_TIMESTAMP_LC, true);
        try
        {
          passwordChangedTime = getGeneralizedTime(userEntry, createTimeType);
        }
        catch (DirectoryException e)
        {
          /*
           * The create time stamp could not be parsed (but has been logged in
           * the debug log). The best effort we can do from here is to a) use
           * the current time, or b) use the start of the epoch (1/1/1970). Lets
           * treat this problem as if the change time attribute did not exist
           * and use the start of the epoch. Doing so stands a greater chance of
           * forcing a password change.
           */
        }

        if (passwordChangedTime < 0)
        {
          passwordChangedTime = 0;

          if (debugEnabled())
          {
            TRACER.debugWarning(
                "Could not determine password changed time for " + "user %s.",
                userDNString);
          }
        }
      }
    }

    return passwordChangedTime;
  }



  /**
   * Retrieves the time that this password policy state object was created.
   *
   * @return  The time that this password policy state object was created.
   */
  public long getCurrentTime()
  {
    return currentTime;
  }



  /**
   * Retrieves the unmodifiable set of values for the password
   * attribute from the user entry.
   *
   * @return The unmodifiable set of values for the password attribute
   *         from the user entry.
   */
  public Set<AttributeValue> getPasswordValues()
  {
    List<Attribute> attrList = userEntry.getAttribute(passwordPolicy
        .getPasswordAttribute());
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        if (a.isEmpty()) continue;

        Set<AttributeValue> values =
          new LinkedHashSet<AttributeValue>(a.size());
        for (AttributeValue value : a)
        {
          values.add(value);
        }
        return Collections.unmodifiableSet(values);
      }
    }

    return Collections.emptySet();
  }



  /**
   * Sets a new value for the password changed time equal to the
   * current time.
   */
  public void setPasswordChangedTime()
  {
    setPasswordChangedTime(currentTime);
  }



  /**
   * Sets a new value for the password changed time equal to the specified time.
   * This method should generally only be used for testing purposes, since the
   * variant that uses the current time is preferred almost everywhere else.
   *
   * @param  passwordChangedTime  The time to use
   */
  public void setPasswordChangedTime(long passwordChangedTime)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Setting password changed time for user %s to " +
          "current time of %d", userDNString, currentTime);
    }

    // passwordChangedTime is computed in the constructor from values in the
    // entry.
    if (getPasswordChangedTime() != passwordChangedTime)
    {
      this.passwordChangedTime = passwordChangedTime;

      String timeValue = GeneralizedTimeSyntax.format(passwordChangedTime);
      Attribute a = Attributes.create(OP_ATTR_PWPOLICY_CHANGED_TIME,
          timeValue);

      modifications.add(new Modification(ModificationType.REPLACE, a, true));
    }
  }



  /**
   * Removes the password changed time value from the user's entry.  This should
   * only be used for testing purposes, as it can really mess things up if you
   * don't know what you're doing.
   */
  public void clearPasswordChangedTime()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing password changed time for user %s",
          userDNString);
    }

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_CHANGED_TIME_LC,
                                       true);
    Attribute a = Attributes.empty(type);
    modifications.add(new Modification(ModificationType.REPLACE, a, true));


    // Fall back to using the entry creation time as the password changed time,
    // if it's defined.  Otherwise, use a value of zero.
    AttributeType createTimeType =
         DirectoryServer.getAttributeType(OP_ATTR_CREATE_TIMESTAMP_LC, true);
    try
    {
      passwordChangedTime = getGeneralizedTime(userEntry, createTimeType);
      if (passwordChangedTime < 0)
      {
        passwordChangedTime = 0;
      }
    }
    catch (Exception e)
    {
      passwordChangedTime = 0;
    }
  }



  /**
   * Updates the user entry to indicate whether user account has been
   * administratively disabled.
   *
   * @param isDisabled
   *          Indicates whether the user account has been administratively
   *          disabled.
   */
  public void setDisabled(boolean isDisabled)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Updating user %s to set the disabled flag to %b",
          userDNString, isDisabled);
    }


    if (isDisabled == isDisabled())
    {
      return; // requested state matches current state
    }

    this.isDisabled = ConditionResult.inverseOf(this.isDisabled);

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_DISABLED, true);

    if (isDisabled)
    {
      Attribute a = Attributes.create(type, String.valueOf(true));
      modifications.add(new Modification(ModificationType.REPLACE, a, true));
    }
    else
    {
      // erase
      modifications.add(new Modification(ModificationType.REPLACE,
                                           Attributes.empty(type), true));
    }
  }



  /**
   * Indicates whether the user's account is currently expired.
   *
   * @return  <CODE>true</CODE> if the user's account is expired, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isAccountExpired()
  {
    if (isAccountExpired != ConditionResult.UNDEFINED)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning stored result of %b for user %s",
            (isAccountExpired == ConditionResult.TRUE), userDNString);
      }

      return isAccountExpired == ConditionResult.TRUE;
    }

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_EXPIRATION_TIME,
                                          true);

    try
    {
      accountExpirationTime = getGeneralizedTime(userEntry, type);
     }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      isAccountExpired = ConditionResult.TRUE;
      if (debugEnabled())
      {
          TRACER.debugWarning("User %s is considered to have an expired " +
               "account because an error occurred while attempting to make " +
               "the determination: %s.",
              userDNString, stackTraceToSingleLineString(e));
      }

      return true;
    }

    if (accountExpirationTime > currentTime)
    {
      // The user does have an expiration time, but it hasn't arrived yet.
      isAccountExpired = ConditionResult.FALSE;
      if (debugEnabled())
      {
        TRACER.debugInfo("The account for user %s is not expired because " +
            "the expiration time has not yet arrived.", userDNString);
      }
    }
    else if (accountExpirationTime >= 0)
    {
      // The user does have an expiration time, and it is in the past.
      isAccountExpired = ConditionResult.TRUE;
      if (debugEnabled())
      {
        TRACER.debugInfo("The account for user %s is expired because the " +
            "expiration time in that account has passed.", userDNString);
      }
    }
    else
    {
      // The user doesn't have an expiration time in their entry, so it
      // can't be expired.
      isAccountExpired = ConditionResult.FALSE;
      if (debugEnabled())
      {
        TRACER.debugInfo("The account for user %s is not expired because " +
            "there is no expiration time in the user's entry.",
            userDNString);
      }
    }

    return isAccountExpired == ConditionResult.TRUE;
  }



  /**
   * Retrieves the time at which the user's account will expire.
   *
   * @return  The time at which the user's account will expire, or -1 if it is
   *          not configured with an expiration time.
   */
  public long getAccountExpirationTime()
  {
    if (accountExpirationTime == Long.MIN_VALUE)
    {
      isAccountExpired();
    }

    return accountExpirationTime;
  }



  /**
   * Sets the user's account expiration time to the specified value.
   *
   * @param  accountExpirationTime  The time that the user's account should
   *                                expire.
   */
  public void setAccountExpirationTime(long accountExpirationTime)
  {
    if (accountExpirationTime < 0)
    {
      clearAccountExpirationTime();
    }
    else
    {
      String timeStr = GeneralizedTimeSyntax.format(accountExpirationTime);

      if (debugEnabled())
      {
        TRACER.debugInfo("Setting account expiration time for user %s to %s",
            userDNString, timeStr);
      }

      this.accountExpirationTime = accountExpirationTime;
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_EXPIRATION_TIME,
                                            true);

      Attribute a = Attributes.create(type, timeStr);
      modifications.add(new Modification(ModificationType.REPLACE, a, true));
    }
  }



  /**
   * Clears the user's account expiration time.
   */
  public void clearAccountExpirationTime()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing account expiration time for user %s",
          userDNString);
    }

    accountExpirationTime = -1;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_EXPIRATION_TIME,
                                          true);

    modifications.add(new Modification(ModificationType.REPLACE,
          Attributes.empty(type), true));
  }



  /**
   * Retrieves the set of times of failed authentication attempts for
   * the user. If authentication failure time expiration is enabled,
   * and there are expired times in the entry, these times are removed
   * from the instance field and an update is provided to delete those
   * values from the entry.
   *
   * @return The set of times of failed authentication attempts for
   *         the user, which will be an empty list in the case of no
   *         valid (unexpired) times in the entry.
   */
  public List<Long> getAuthFailureTimes()
  {
    if (authFailureTimes != null)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning stored auth failure time list of %d " +
            "elements for user %s",
            authFailureTimes.size(), userDNString);
      }

      return authFailureTimes;
    }

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
           OP_ATTR_PWPOLICY_FAILURE_TIME);
    }

    try
    {
      authFailureTimes = getGeneralizedTimes(type);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      if (debugEnabled())
      {
        TRACER.debugWarning("Error while processing auth failure times " +
             "for user %s: %s",
                     userDNString, stackTraceToSingleLineString(e));
      }

      authFailureTimes = new ArrayList<Long>();

      modifications.add(new Modification(ModificationType.REPLACE,
            Attributes.empty(type), true));

      return authFailureTimes;
    }

    if (authFailureTimes.isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning an empty auth failure time list for " +
            "user %s because the attribute is absent from the entry.",
            userDNString);
      }

      return authFailureTimes;
    }

    // Remove any expired failures from the list.
    if (passwordPolicy.getLockoutFailureExpirationInterval() > 0)
    {
      LinkedHashSet<AttributeValue> valuesToRemove = null;

      long expirationTime = currentTime -
           (passwordPolicy.getLockoutFailureExpirationInterval() * 1000L);
      Iterator<Long> iterator = authFailureTimes.iterator();
      while (iterator.hasNext())
      {
        long l = iterator.next();
        if (l < expirationTime)
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Removing expired auth failure time %d for " +
                "user %s", l, userDNString);
          }

          iterator.remove();

          if (valuesToRemove == null)
          {
            valuesToRemove = new LinkedHashSet<AttributeValue>();
          }

          valuesToRemove.add(AttributeValues.create(type,
                                              GeneralizedTimeSyntax.format(l)));
        }
      }

      if (valuesToRemove != null)
      {
        AttributeBuilder builder = new AttributeBuilder(type);
        builder.addAll(valuesToRemove);
        Attribute a = builder.toAttribute();
        modifications.add(new Modification(ModificationType.DELETE, a,
            true));
      }
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning auth failure time list of %d elements " +
          "for user %s", authFailureTimes.size(), userDNString);
    }

    return authFailureTimes;
  }



  /**
   * Updates the set of authentication failure times to include the current
   * time. If the number of failures reaches the policy configuration limit,
   * lock the account.
   */
  public void updateAuthFailureTimes()
  {
    if (passwordPolicy.getLockoutFailureCount() <= 0)
    {
      return;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Updating authentication failure times for user %s",
          userDNString);
    }


    List<Long> failureTimes = getAuthFailureTimes();
    // Note: failureTimes == this.authFailureTimes
    long highestFailureTime = -1;
    for (Long l : failureTimes)
    {
      highestFailureTime = Math.max(l, highestFailureTime);
    }

    if (highestFailureTime >= currentTime)
    {
      highestFailureTime++;
    }
    else
    {
      highestFailureTime = currentTime;
    }
    // Update the current policy state
    failureTimes.add(highestFailureTime);

    // And the attribute in the user entry
    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_FAILURE_TIME);
    }

    Attribute addAttr = Attributes.create(type, AttributeValues.create(type,
        GeneralizedTimeSyntax.format(highestFailureTime)));

    modifications.add(new Modification(ModificationType.ADD, addAttr, true));

    // Now check to see if there have been sufficient failures to lock the
    // account.
    int lockoutCount = passwordPolicy.getLockoutFailureCount();
    if ((lockoutCount > 0) && (lockoutCount <= authFailureTimes.size()))
    {
      setFailureLockedTime(highestFailureTime);
      if (debugEnabled())
      {
        TRACER.debugInfo("Locking user account %s due to too many failures.",
            userDNString);
      }
    }
  }



  /**
   * Explicitly specifies the auth failure times for the associated user.  This
   * should generally only be used for testing purposes.  Note that it will also
   * set or clear the locked time as appropriate.
   *
   * @param  authFailureTimes  The set of auth failure times to use for the
   *                           account.  An empty list or {@code null} will
   *                           clear the account of any existing failures.
   */
  public void setAuthFailureTimes(List<Long> authFailureTimes)
  {
    if ((authFailureTimes == null) || authFailureTimes.isEmpty())
    {
      clearAuthFailureTimes();
      clearFailureLockedTime();
      return;
    }

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC,
                                          true);
    this.authFailureTimes = authFailureTimes;

    AttributeBuilder builder = new AttributeBuilder(type);
    long highestFailureTime = -1;

    for (Long l : authFailureTimes)
    {
      highestFailureTime = Math.max(l, highestFailureTime);
      builder
          .add(AttributeValues.create(type, GeneralizedTimeSyntax.format(l)));
    }
    Attribute a = builder.toAttribute();

    modifications.add(new Modification(ModificationType.REPLACE, a, true));

    // Now check to see if there have been sufficient failures to lock the
    // account.
    int lockoutCount = passwordPolicy.getLockoutFailureCount();
    if ((lockoutCount > 0) && (lockoutCount <= authFailureTimes.size()))
    {
      setFailureLockedTime(highestFailureTime);
      if (debugEnabled())
      {
        TRACER.debugInfo("Locking user account %s due to too many failures.",
            userDNString);
      }
    }
  }



  /**
   * Updates the user entry to remove any record of previous authentication
   * failure times.
   */
  private void clearAuthFailureTimes()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing authentication failure times for user %s",
          userDNString);
    }

    List<Long> failureTimes = getAuthFailureTimes();
    if (failureTimes.isEmpty())
    {
      return;
    }

    failureTimes.clear(); // Note: failureTimes == this.authFailureTimes

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_FAILURE_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_FAILURE_TIME);
    }

    modifications.add(new Modification(ModificationType.REPLACE,
                                         Attributes.empty(type), true));
  }


  /**
   * Retrieves the time of an authentication failure lockout for the user.
   *
   * @return  The time of an authentication failure lockout for the user, or -1
   *          if no such time is present in the entry.
   */
  private long getFailureLockedTime()
  {
    if (failureLockedTime != Long.MIN_VALUE)
    {
      return failureLockedTime;
    }

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_LOCKED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
           OP_ATTR_PWPOLICY_LOCKED_TIME);
    }

    try
    {
      failureLockedTime = getGeneralizedTime(userEntry, type);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      failureLockedTime = currentTime;
      if (debugEnabled())
      {
        TRACER.debugWarning("Returning current time for user %s because an " +
            "error occurred: %s",
                     userDNString, stackTraceToSingleLineString(e));
      }

      return failureLockedTime;
    }

    // An expired locked time is handled in lockedDueToFailures.
    return failureLockedTime;
  }



  /**
    Sets the failure lockout attribute in the entry to the requested time.

    @param time  The time to which to set the entry's failure lockout attribute.
   */
  private void setFailureLockedTime(final long time)
  {
    if (time == getFailureLockedTime())
    {
      return;
    }

    failureLockedTime = time;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_LOCKED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_LOCKED_TIME);
    }

    Attribute a = Attributes.create(type, AttributeValues.create(type,
        GeneralizedTimeSyntax.format(failureLockedTime)));

    modifications.add(new Modification(ModificationType.REPLACE, a, true));
  }



  /**
   * Updates the user entry to remove any record of previous authentication
   * failure lockout.
   */
  private void clearFailureLockedTime()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing failure lockout time for user %s.",
          userDNString);
    }

    if (-1L == getFailureLockedTime())
    {
      return;
    }

    failureLockedTime = -1L;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_LOCKED_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_LOCKED_TIME);
    }

    modifications.add(new Modification(ModificationType.REPLACE,
                                         Attributes.empty(type), true));
  }



  /**
   * Indicates whether the associated user should be considered locked out as a
   * result of too many authentication failures. In the case of an expired
   * lock-out, this routine produces the update to clear the lock-out attribute
   * and the authentication failure timestamps.
   * In case the failure lockout time is absent from the entry, but sufficient
   * authentication failure timestamps are present in the entry, this routine
   * produces the update to set the lock-out attribute.
   *
   * @return  <CODE>true</CODE> if the user is currently locked out due to too
   *          many authentication failures, or <CODE>false</CODE> if not.
   */
  public boolean lockedDueToFailures()
  {
    // FIXME: Introduce a state field to cache the computed value of this
    // method. Note that only a cached "locked" status can be returned due to
    // the possibility of intervening updates to this.failureLockedTime by
    // updateAuthFailureTimes.

    // Check if the feature is enabled in the policy.
    final int maxFailures = passwordPolicy.getLockoutFailureCount();
    if (maxFailures <= 0)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false for user %s because lockout due " +
            "to failures is not enabled.", userDNString);
      }

      return false;
    }

    // Get the locked time from the user's entry. If it is present and not
    // expired, the account is locked. If it is absent, the failure timestamps
    // must be checked, since failure timestamps sufficient to lock the
    // account could be produced across the synchronization topology within the
    // synchronization latency. Also, note that IETF
    // draft-behera-ldap-password-policy-09 specifies "19700101000000Z" as
    // the value to be set under a "locked until reset" regime; however, this
    // implementation accepts the value as a locked entry, but observes the
    // lockout expiration policy for all values including this one.
    // FIXME: This "getter" is unusual in that it might produce an update to the
    // entry in two cases. Does it make sense to factor the methods so that,
    // e.g., an expired lockout is reported, and clearing the lockout is left to
    // the caller?
    if (getFailureLockedTime() < 0L)
    {
      // There was no locked time present in the entry; however, sufficient
      // failure times might have accumulated to trigger a lockout.
      if (getAuthFailureTimes().size() < maxFailures)
      {
        if (debugEnabled())
        {
          TRACER.debugInfo("Returning false for user %s because there is " +
              "no locked time.", userDNString);
        }

        return false;
      }

      // The account isn't locked but should be, so do so now.
      setFailureLockedTime(currentTime);// FIXME: set to max(failureTimes)?

      if (debugEnabled())
      {
        TRACER.debugInfo("Locking user %s because there were enough " +
            "existing failures even though there was no account locked time.",
            userDNString);
      }
      // Fall through...
    }

    // There is a failure locked time, but it may be expired.
    if (passwordPolicy.getLockoutDuration() > 0)
    {
      final long unlockTime = getFailureLockedTime() +
           (1000L * passwordPolicy.getLockoutDuration());
      if (unlockTime > currentTime)
      {
        secondsUntilUnlock = (int) ((unlockTime - currentTime) / 1000);

        if (debugEnabled())
        {
          TRACER.debugInfo("Returning true for user %s because there is a " +
              "locked time and the lockout duration has not been reached.",
              userDNString);
        }

        return true;
      }

      // The lockout in the entry has expired...
      clearFailureLockout();

      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false for user %s " +
            "because the existing lockout has expired.", userDNString);
      }

      assert -1L == getFailureLockedTime();
      return false;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning true for user %s " +
          "because there is a locked time and no lockout duration.",
          userDNString);
    }

    assert -1L <= getFailureLockedTime();
    return true;
  }



  /**
   * Retrieves the length of time in seconds until the user's account is
   * automatically unlocked.  This should only be called after calling
   * <CODE>lockedDueToFailures</CODE>.
   *
   * @return  The length of time in seconds until the user's account is
   *          automatically unlocked, or -1 if the account is not locked or the
   *          lockout requires administrative action to clear.
   */
  public int getSecondsUntilUnlock()
  {
    // secondsUntilUnlock is only set when failureLockedTime is present and
    // PasswordPolicy.getLockoutDuration is enabled; hence it is not
    // unreasonable to find secondsUntilUnlock uninitialized.
    assert failureLockedTime != Long.MIN_VALUE;

    return (secondsUntilUnlock < 0) ? -1 : secondsUntilUnlock;
  }



  /**
   * Updates the user account to remove any record of a previous lockout due to
   * failed authentications.
   */
  public void clearFailureLockout()
  {
    clearAuthFailureTimes();
    clearFailureLockedTime();
  }



  /**
   * Retrieves the time that the user last authenticated to the Directory
   * Server.
   *
   * @return  The time that the user last authenticated to the Directory Server,
   *          or -1 if it cannot be determined.
   */
  public long getLastLoginTime()
  {
    if (lastLoginTime != Long.MIN_VALUE)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning stored last login time of %d for " +
            "user %s.", lastLoginTime, userDNString);
      }

      return lastLoginTime;
    }

    // The policy configuration must be checked since the entry cannot be
    // evaluated without both an attribute name and timestamp format.
    AttributeType type   = passwordPolicy.getLastLoginTimeAttribute();
    String        format = passwordPolicy.getLastLoginTimeFormat();

    if ((type == null) || (format == null))
    {
      lastLoginTime = -1;
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning -1 for user %s because no last login " +
            "time will be maintained.", userDNString);
      }

      return lastLoginTime;
    }

    boolean isGeneralizedTime =
        type.getSyntax().getSyntaxName().equals(SYNTAX_GENERALIZED_TIME_NAME);
    lastLoginTime = -1;
    List<Attribute> attrList = userEntry.getAttribute(type);

    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        if (a.isEmpty()) continue;

        String valueString = a.iterator().next().getValue().toString();

        try
        {
          SimpleDateFormat dateFormat = new SimpleDateFormat(format);
          if (isGeneralizedTime)
          {
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
          }
          lastLoginTime = dateFormat.parse(valueString).getTime();

          if (debugEnabled())
          {
            TRACER.debugInfo("Returning last login time of %d for user %s" +
                "decoded using current last login time format.",
                lastLoginTime, userDNString);
          }

          return lastLoginTime;
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // This could mean that the last login time was encoded using a
          // previous format.
          for (String f : passwordPolicy.getPreviousLastLoginTimeFormats())
          {
            try
            {
              SimpleDateFormat dateFormat = new SimpleDateFormat(f);
              if (isGeneralizedTime)
              {
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
              }
              lastLoginTime = dateFormat.parse(valueString).getTime();

              if (debugEnabled())
              {
                TRACER.debugInfo("Returning last login time of %d for " +
                    "user %s decoded using previous last login time format " +
                    "of %s.", lastLoginTime, userDNString, f);
              }

              return lastLoginTime;
            }
            catch (Exception e2)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }

          assert lastLoginTime == -1;
          if (debugEnabled())
          {
              TRACER.debugWarning("Returning -1 for user %s because the " +
                  "last login time value %s could not be parsed using any " +
                  "known format.", userDNString, valueString);
          }

          return lastLoginTime;
        }
      }
    }

    assert lastLoginTime == -1;
    if (debugEnabled())
    {
      TRACER.debugInfo("Returning %d for user %s because no last " +
          "login time value exists.", lastLoginTime, userDNString);
    }

    return lastLoginTime;
  }



  /**
   * Updates the user entry to set the current time as the last login time.
   */
  public void setLastLoginTime()
  {
    setLastLoginTime(currentTime);
  }



  /**
   * Updates the user entry to use the specified last login time.  This should
   * be used primarily for testing purposes, as the variant that uses the
   * current time should be used most of the time.
   *
   * @param  lastLoginTime  The last login time to set in the user entry.
   */
  public void setLastLoginTime(long lastLoginTime)
  {
    AttributeType type = passwordPolicy.getLastLoginTimeAttribute();
    String format = passwordPolicy.getLastLoginTimeFormat();

    if ((type == null) || (format == null))
    {
      return;
    }

    String timestamp;
    try
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      // If the attribute has a Generalized Time syntax, make it UTC time.
      if (type.getSyntax().getSyntaxName()
          .equals(SYNTAX_GENERALIZED_TIME_NAME))
      {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      }
      timestamp = dateFormat.format(new Date(lastLoginTime));
      this.lastLoginTime = dateFormat.parse(timestamp).getTime();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      if (debugEnabled())
      {
        TRACER.debugWarning("Unable to set last login time for user %s " +
            "because an error occurred: %s",
                     userDNString, stackTraceToSingleLineString(e));
      }

      return;
    }


    String existingTimestamp = getValue(type);
    if ((existingTimestamp != null) && timestamp.equals(existingTimestamp))
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Not updating last login time for user %s " +
            "because the new value matches the existing value.",
            userDNString);
      }

      return;
    }


    Attribute a = Attributes.create(type, timestamp);
    modifications.add(new Modification(ModificationType.REPLACE, a, true));

    if (debugEnabled())
    {
      TRACER.debugInfo("Updated the last login time for user %s to %s",
          userDNString, timestamp);
    }
  }



  /**
   * Clears the last login time from the user's entry.  This should generally be
   * used only for testing purposes.
   */
  public void clearLastLoginTime()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing last login time for user %s", userDNString);
    }

    lastLoginTime = -1;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_LAST_LOGIN_TIME, true);

    modifications.add(new Modification(ModificationType.REPLACE,
                                         Attributes.empty(type), true));
  }



  /**
   * Indicates whether the user's account is currently locked because it has
   * been idle for too long.
   *
   * @return  <CODE>true</CODE> if the user's account is locked because it has
   *          been idle for too long, or <CODE>false</CODE> if not.
   */
  public boolean lockedDueToIdleInterval()
  {
    if (isIdleLocked != ConditionResult.UNDEFINED)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning stored result of %b for user %s",
            (isIdleLocked == ConditionResult.TRUE), userDNString);
      }

      return isIdleLocked == ConditionResult.TRUE;
    }

    // Return immediately if this feature is disabled, since the feature is not
    // responsible for any state attribute in the entry.
    if (passwordPolicy.getIdleLockoutInterval() <= 0)
    {
      isIdleLocked = ConditionResult.FALSE;

      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false for user %s because no idle " +
            "lockout interval is defined.", userDNString);
      }
      return false;
    }

    long lockTime = currentTime -
                         (1000L * passwordPolicy.getIdleLockoutInterval());
    if(lockTime < 0) lockTime = 0;

    long theLastLoginTime = getLastLoginTime();
    if (theLastLoginTime > lockTime || getPasswordChangedTime() > lockTime)
    {
      isIdleLocked = ConditionResult.FALSE;
      if (debugEnabled())
      {
        StringBuilder reason = new StringBuilder();
        if(theLastLoginTime > lockTime)
        {
          reason.append("the last login time is in an acceptable window");
        }
        else
        {
          if(theLastLoginTime < 0)
          {
            reason.append("there is no last login time, but ");
          }
          reason.append(
              "the password changed time is in an acceptable window");
        }
        TRACER.debugInfo("Returning false for user %s because %s.",
            userDNString, reason.toString());
      }
    }
    else
    {
      isIdleLocked = ConditionResult.TRUE;
      if (debugEnabled())
      {
        String reason = (theLastLoginTime < 0)
            ? "there is no last login time and the password " +
            "changed time is not in an acceptable window"
            : "neither last login time nor password " +
            "changed time are in an acceptable window";
        TRACER.debugInfo("Returning true for user %s because %s.",
            userDNString, reason);
      }
    }

    return isIdleLocked == ConditionResult.TRUE;
  }



/**
* Indicates whether the user's password must be changed before any other
* operation can be performed.
*
* @return  <CODE>true</CODE> if the user's password must be changed before
*          any other operation can be performed.
*/
  public boolean mustChangePassword()
  {
    if(mustChangePassword != ConditionResult.UNDEFINED)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning stored result of %b for user %s.",
            (mustChangePassword == ConditionResult.TRUE), userDNString);
      }

      return mustChangePassword == ConditionResult.TRUE;
    }

    // If the password policy doesn't use force change on add or force change on
    // reset, or if it forbids the user from changing his password, then return
    // false.
    // FIXME: the only getter responsible for a state attribute (pwdReset) that
    // considers the policy before checking the entry for the presence of the
    // attribute.
    if (! (passwordPolicy.isAllowUserPasswordChanges()
           && (passwordPolicy.isForceChangeOnAdd()
               || passwordPolicy.isForceChangeOnReset())))
    {
      mustChangePassword = ConditionResult.FALSE;
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false for user %s because neither " +
            "force change on add nor force change on reset is enabled, " +
            "or users are not allowed to self-modify passwords.",
            userDNString);

      }

      return false;
    }

    AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_RESET_REQUIRED_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
           OP_ATTR_PWPOLICY_RESET_REQUIRED);
    }

    try
    {
      mustChangePassword = getBoolean(userEntry, type);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        TRACER.debugWarning("Returning true for user %s because an error " +
            "occurred: %s", userDNString, stackTraceToSingleLineString(e));
      }

      mustChangePassword = ConditionResult.TRUE;

      return true;
    }

    if(mustChangePassword == ConditionResult.UNDEFINED)
    {
      mustChangePassword = ConditionResult.FALSE;
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning %b for user since the attribute \"%s\"" +
            " is not present in the entry.",
            false, userDNString, OP_ATTR_PWPOLICY_RESET_REQUIRED);
      }

      return false;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning %b for user %s.",
          (mustChangePassword == ConditionResult.TRUE), userDNString);
    }

    return mustChangePassword == ConditionResult.TRUE;
  }



/**
* Updates the user entry to indicate whether the user's password must be
* changed.
*
* @param  mustChangePassword  Indicates whether the user's password must be
*                             changed.
*/
  public void setMustChangePassword(boolean mustChangePassword)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Updating user %s to set the reset flag to %b",
          userDNString, mustChangePassword);
    }

    if (mustChangePassword == mustChangePassword())
    {
      return;  // requested state matches current state
    }

    this.mustChangePassword =
            ConditionResult.inverseOf(this.mustChangePassword);

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_RESET_REQUIRED_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_RESET_REQUIRED);
    }

    if (mustChangePassword)
    {
      Attribute a = Attributes.create(type, String.valueOf(true));
      modifications.add(new Modification(ModificationType.REPLACE, a, true));
    }
    else
    {
      modifications.add(new Modification(ModificationType.REPLACE,
                                           Attributes.empty(type), true));
    }
  }



  /**
   * Indicates whether the user's account is locked because the password has
   * been reset by an administrator but the user did not change the password in
   * a timely manner.
   *
   * @return  <CODE>true</CODE> if the user's account is locked because of the
   *          maximum reset age, or <CODE>false</CODE> if not.
   */
  public boolean lockedDueToMaximumResetAge()
  {
    // This feature is reponsible for neither a state field nor an entry state
    // attribute.
    if (passwordPolicy.getMaxPasswordResetAge() <= 0L)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false for user %s because there is no " +
            "maximum reset age.", userDNString);
      }

      return false;
    }

    if (! mustChangePassword())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false for user %s because the user's " +
            "password has not been reset.", userDNString);
      }

      return false;
    }

    long maxResetTime = getPasswordChangedTime() +
        (1000L * passwordPolicy.getMaxPasswordResetAge());
    boolean locked = (maxResetTime < currentTime);

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning %b for user %s after comparing the " +
          "current and max reset times.", locked, userDNString);
    }

    return locked;
  }



  /**
   * Retrieves the time that the user's password should expire (if the
   * expiration is in the future) or did expire (if the expiration was in the
   * past).  Note that this method should be called after the
   * <CODE>lockedDueToMaximumResetAge</CODE> method because grace logins will
   * not be allowed in the case that the maximum reset age has passed whereas
   * they may be used for expiration due to maximum password age or forced
   * change time.
   *
   * @return  The time that the user's password should/did expire, or -1 if it
   *          should not expire.
   */
  public long getPasswordExpirationTime()
  {
    if (passwordExpirationTime == Long.MIN_VALUE)
    {
      passwordExpirationTime = Long.MAX_VALUE;

      boolean checkWarning = false;

      long maxAge = passwordPolicy.getMaxPasswordAge();
      if (maxAge > 0L)
      {
        long expTime = getPasswordChangedTime() + (1000L*maxAge);
        if (expTime < passwordExpirationTime)
        {
          passwordExpirationTime = expTime;
          checkWarning   = true;
        }
      }

      long maxResetAge = passwordPolicy.getMaxPasswordResetAge();
      if (mustChangePassword() && (maxResetAge > 0L))
      {
        long expTime = getPasswordChangedTime() + (1000L*maxResetAge);
        if (expTime < passwordExpirationTime)
        {
          passwordExpirationTime = expTime;
          checkWarning   = false;
        }
      }

      long mustChangeTime = passwordPolicy.getRequireChangeByTime();
      if (mustChangeTime > 0)
      {
        long reqChangeTime = getRequiredChangeTime();
        if ((reqChangeTime != mustChangeTime) &&
            (mustChangeTime < passwordExpirationTime))
        {
          passwordExpirationTime = mustChangeTime;
          checkWarning   = true;
        }
      }

      if (passwordExpirationTime == Long.MAX_VALUE)
      {
        passwordExpirationTime = -1;
        shouldWarn             = ConditionResult.FALSE;
        isFirstWarning         = ConditionResult.FALSE;
        isPasswordExpired      = ConditionResult.FALSE;
        mayUseGraceLogin       = ConditionResult.TRUE;
      }
      else if (checkWarning)
      {
        mayUseGraceLogin = ConditionResult.TRUE;

        long warningInterval = passwordPolicy
            .getPasswordExpirationWarningInterval();
        if (warningInterval > 0L)
        {
          long shouldWarnTime =
                    passwordExpirationTime - (warningInterval*1000L);
          if (shouldWarnTime > currentTime)
          {
            // The warning time is in the future, so we know the password isn't
            // expired.
            shouldWarn        = ConditionResult.FALSE;
            isFirstWarning    = ConditionResult.FALSE;
            isPasswordExpired = ConditionResult.FALSE;
          }
          else
          {
            // We're at least in the warning period, but the password may be
            // expired.
            long theWarnedTime = getWarnedTime();

            if (passwordExpirationTime > currentTime)
            {
              // The password is not expired but we should warn the user.
              shouldWarn        = ConditionResult.TRUE;
              isPasswordExpired = ConditionResult.FALSE;

              if (theWarnedTime < 0)
              {
                isFirstWarning = ConditionResult.TRUE;
                setWarnedTime();

                if (! passwordPolicy.isExpirePasswordsWithoutWarning())
                {
                  passwordExpirationTime =
                       currentTime + (warningInterval*1000L);
                }
              }
              else
              {
                isFirstWarning = ConditionResult.FALSE;

                if (! passwordPolicy.isExpirePasswordsWithoutWarning())
                {
                  passwordExpirationTime =
                      theWarnedTime + (warningInterval*1000L);
                }
              }
            }
            else
            {
              // The expiration time has passed, but we may not actually be
              // expired if the user has not yet seen a warning.
              if (passwordPolicy.isExpirePasswordsWithoutWarning())
              {
                shouldWarn        = ConditionResult.FALSE;
                isFirstWarning    = ConditionResult.FALSE;
                isPasswordExpired = ConditionResult.TRUE;
              }
              else if (theWarnedTime > 0)
              {
                passwordExpirationTime =
                    theWarnedTime + (warningInterval*1000L);
                if (passwordExpirationTime > currentTime)
                {
                  shouldWarn        = ConditionResult.TRUE;
                  isFirstWarning    = ConditionResult.FALSE;
                  isPasswordExpired = ConditionResult.FALSE;
                }
                else
                {
                  shouldWarn        = ConditionResult.FALSE;
                  isFirstWarning    = ConditionResult.FALSE;
                  isPasswordExpired = ConditionResult.TRUE;
                }
              }
              else
              {
                shouldWarn             = ConditionResult.TRUE;
                isFirstWarning         = ConditionResult.TRUE;
                isPasswordExpired      = ConditionResult.FALSE;
                passwordExpirationTime = currentTime + (warningInterval*1000L);
              }
            }
          }
        }
        else
        {
          // There will never be a warning, and the user's password may be
          // expired.
          shouldWarn     = ConditionResult.FALSE;
          isFirstWarning = ConditionResult.FALSE;

          if (currentTime > passwordExpirationTime)
          {
            isPasswordExpired = ConditionResult.TRUE;
          }
          else
          {
            isPasswordExpired = ConditionResult.FALSE;
          }
        }
      }
      else
      {
        mayUseGraceLogin = ConditionResult.FALSE;
        shouldWarn       = ConditionResult.FALSE;
        isFirstWarning   = ConditionResult.FALSE;

        if (passwordExpirationTime < currentTime)
        {
          isPasswordExpired = ConditionResult.TRUE;
        }
        else
        {
          isPasswordExpired = ConditionResult.FALSE;
        }
      }
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning password expiration time of %d for user " +
          "%s.", passwordExpirationTime, userDNString);
    }

    return passwordExpirationTime;
  }



  /**
   * Indicates whether the user's password is currently expired.
   *
   * @return  <CODE>true</CODE> if the user's password is currently expired, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isPasswordExpired()
  {
    if ((isPasswordExpired == null) ||
        (isPasswordExpired == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    return isPasswordExpired == ConditionResult.TRUE;
  }



  /**
   * Indicates whether the user's last password change was within the minimum
   * password age.
   *
   * @return  <CODE>true</CODE> if the password minimum age is nonzero, the
   *          account is not in force-change mode, and the last password change
   *          was within the minimum age, or <CODE>false</CODE> otherwise.
   */
  public boolean isWithinMinimumAge()
  {
    // This feature is reponsible for neither a state field nor entry state
    // attribute.
    long minAge = passwordPolicy.getMinPasswordAge();
    if (minAge <= 0L)
    {
      // There is no minimum age, so the user isn't in it.
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false because there is no minimum age.");
      }

      return false;
    }
    else if ((getPasswordChangedTime() + (minAge*1000L)) < currentTime)
    {
      // It's been long enough since the user changed their password.
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false because the minimum age has " +
            "expired.");
      }

      return false;
    }
    else if (mustChangePassword())
    {
      // The user is in a must-change mode, so the minimum age doesn't apply.
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false because the account is in a " +
            "must-change state.");
      }

      return false;
    }
    else
    {
      // The user is within the minimum age.
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning true.");
      }

      return true;
    }
  }



  /**
   * Indicates whether the user may use a grace login if the password is expired
   * and there is at least one grace login remaining.  Note that this does not
   * check to see if the user's password is expired, does not verify that there
   * are any remaining grace logins, and does not update the set of grace login
   * times.
   *
   * @return  <CODE>true</CODE> if the user may use a grace login if the
   *          password is expired and there is at least one grace login
   *          remaining, or <CODE>false</CODE> if the user may not use a grace
   *          login for some reason.
   */
  public boolean mayUseGraceLogin()
  {
    if ((mayUseGraceLogin == null) ||
        (mayUseGraceLogin == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    return mayUseGraceLogin == ConditionResult.TRUE;
  }



  /**
   * Indicates whether the user should receive a warning notification that the
   * password is about to expire.
   *
   * @return  <CODE>true</CODE> if the user should receive a warning
   *          notification that the password is about to expire, or
   *          <CODE>false</CODE> if not.
   */
  public boolean shouldWarn()
  {
    if ((shouldWarn == null) || (shouldWarn == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    return shouldWarn == ConditionResult.TRUE;
  }



  /**
   * Indicates whether the warning that the user should receive would be the
   * first warning for the user.
   *
   * @return  <CODE>true</CODE> if the warning that should be sent to the user
   *          would be the first warning, or <CODE>false</CODE> if not.
   */
  public boolean isFirstWarning()
  {
    if ((isFirstWarning == null) ||
        (isFirstWarning == ConditionResult.UNDEFINED))
    {
      getPasswordExpirationTime();
    }

    return isFirstWarning == ConditionResult.TRUE;
  }



  /**
   * Retrieves the length of time in seconds until the user's password expires.
   *
   * @return  The length of time in seconds until the user's password expires,
   *          0 if the password is currently expired, or -1 if the password
   *          should not expire.
   */
  public int getSecondsUntilExpiration()
  {
    long expirationTime = getPasswordExpirationTime();
    if (expirationTime < 0)
    {
      return -1;
    }
    else if (expirationTime < currentTime)
    {
      return 0;
    }
    else
    {
      return (int) ((expirationTime - currentTime) / 1000);
    }
  }



  /**
   * Retrieves the timestamp for the last required change time that the user
   * complied with.
   *
   * @return  The timestamp for the last required change time that the user
   *          complied with, or -1 if the user's password has not been changed
   *          in compliance with this configuration.
   */
  public long getRequiredChangeTime()
  {
    if (requiredChangeTime != Long.MIN_VALUE)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning stored required change time of %d for " +
            "user %s", requiredChangeTime, userDNString);
      }

      return requiredChangeTime;
    }

    AttributeType type = DirectoryServer.getAttributeType(
                              OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME, true);

    try
    {
      requiredChangeTime = getGeneralizedTime(userEntry, type);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      requiredChangeTime = -1;
      if (debugEnabled())
      {
        TRACER.debugWarning("Returning %d for user %s because an error " +
            "occurred: %s", requiredChangeTime, userDNString,
                     stackTraceToSingleLineString(e));
      }

      return requiredChangeTime;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning required change time of %d for user %s",
          requiredChangeTime, userDNString);
    }

    return requiredChangeTime;
  }



  /**
   * Updates the user entry with a timestamp indicating that the password has
   * been changed in accordance with the require change time.
   */
  public void setRequiredChangeTime()
  {
    long requiredChangeByTimePolicy = passwordPolicy.getRequireChangeByTime();
    if (requiredChangeByTimePolicy > 0)
    {
      setRequiredChangeTime(requiredChangeByTimePolicy);
    }
  }



  /**
   * Updates the user entry with a timestamp indicating that the password has
   * been changed in accordance with the require change time.
   *
   * @param  requiredChangeTime  The timestamp to use for the required change
   *                             time value.
   */
  public void setRequiredChangeTime(long requiredChangeTime)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Updating required change time for user %s",
          userDNString);
    }

    if (getRequiredChangeTime() != requiredChangeTime)
    {
      this.requiredChangeTime = requiredChangeTime;

      AttributeType type = DirectoryServer.getAttributeType(
                               OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME, true);

      String timeValue = GeneralizedTimeSyntax.format(requiredChangeTime);
      Attribute a = Attributes.create(type, timeValue);

      modifications.add(new Modification(ModificationType.REPLACE, a, true));
    }
  }



  /**
   * Updates the user entry to remove any timestamp indicating that the password
   * has been changed in accordance with the required change time.
   */
  public void clearRequiredChangeTime()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing required change time for user %s",
          userDNString);
    }

    this.requiredChangeTime = Long.MIN_VALUE;

    AttributeType type = DirectoryServer.getAttributeType(
                             OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME, true);
    modifications.add(new Modification(ModificationType.REPLACE,
                                         Attributes.empty(type), true));
  }



  /**
   * Retrieves the time that the user was first warned about an upcoming
   * expiration.
   *
   * @return  The time that the user was first warned about an upcoming
   *          expiration, or -1 if the user has not been warned.
   */
  public long getWarnedTime()
  {
    if (warnedTime == Long.MIN_VALUE)
    {
      AttributeType type =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_WARNED_TIME, true);
      try
      {
        warnedTime = getGeneralizedTime(userEntry, type);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        if (debugEnabled())
        {
          TRACER.debugWarning("Unable to decode the warned time for user %s: " +
              "%s", userDNString, stackTraceToSingleLineString(e));
        }

        warnedTime = -1;
      }
    }


    if (debugEnabled())
    {
      TRACER.debugInfo("Returning a warned time of %d for user %s",
          warnedTime, userDNString);
    }

    return warnedTime;
  }



  /**
   * Updates the user entry to set the warned time to the current time.
   */
  public void setWarnedTime()
  {
    setWarnedTime(currentTime);
  }



  /**
   * Updates the user entry to set the warned time to the specified time.  This
   * method should generally only be used for testing purposes, since the
   * variant that uses the current time is preferred almost everywhere else.
   *
   * @param  warnedTime  The value to use for the warned time.
   */
  public void setWarnedTime(long warnedTime)
  {
    long warnTime = getWarnedTime();
    if (warnTime == warnedTime)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Not updating warned time for user %s because " +
            "the warned time is the same as the specified time.",
            userDNString);
      }

      return;
    }

    this.warnedTime = warnedTime;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_WARNED_TIME, true);
    Attribute a = Attributes.create(type, GeneralizedTimeSyntax
        .createGeneralizedTimeValue(currentTime));

    modifications.add(new Modification(ModificationType.REPLACE, a, true));

    if (debugEnabled())
    {
      TRACER.debugInfo("Updated the warned time for user %s", userDNString);
    }
  }



  /**
   * Updates the user entry to clear the warned time.
   */
  public void clearWarnedTime()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing warned time for user %s", userDNString);
    }

    if (getWarnedTime() < 0)
    {
      return;
    }
    warnedTime = -1;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_WARNED_TIME, true);
    Attribute a = Attributes.empty(type);
    modifications.add(new Modification(ModificationType.REPLACE, a, true));

    if (debugEnabled())
    {
      TRACER.debugInfo("Cleared the warned time for user %s", userDNString);
    }
  }



  /**
   * Retrieves the times that the user has authenticated to the server using a
   * grace login.
   *
   * @return  The times that the user has authenticated to the server using a
   *          grace login.
   */
  public List<Long> getGraceLoginTimes()
  {
    if (graceLoginTimes == null)
    {
      AttributeType type = DirectoryServer.getAttributeType(
                                OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC);
      if (type == null)
      {
        type = DirectoryServer.getDefaultAttributeType(
                                    OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME);
      }

      try
      {
        graceLoginTimes = getGeneralizedTimes(type);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        if (debugEnabled())
        {
          TRACER.debugWarning("Error while processing grace login times " +
               "for user %s: %s",
                       userDNString, stackTraceToSingleLineString(e));
        }

        graceLoginTimes = new ArrayList<Long>();

        modifications.add(new Modification(ModificationType.REPLACE,
              Attributes.empty(type), true));
      }
    }


    if (debugEnabled())
    {
      TRACER.debugInfo("Returning grace login times for user %s",
          userDNString);
    }

    return graceLoginTimes;
  }



  /**
   * Retrieves the number of grace logins that the user has left.
   *
   * @return  The number of grace logins that the user has left, or -1 if grace
   *          logins are not allowed.
   */
  public int getGraceLoginsRemaining()
  {
    int maxGraceLogins = passwordPolicy.getGraceLoginCount();
    if (maxGraceLogins <= 0)
    {
      return -1;
    }

    List<Long> theGraceLoginTimes = getGraceLoginTimes();
    return maxGraceLogins - theGraceLoginTimes.size();
  }



  /**
   * Updates the set of grace login times for the user to include the current
   * time.
   */
  public void updateGraceLoginTimes()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Updating grace login times for user %s",
          userDNString);
    }

    List<Long> graceTimes = getGraceLoginTimes();
    long highestGraceTime = -1;
    for (Long l : graceTimes)
    {
      highestGraceTime = Math.max(l, highestGraceTime);
    }

    if (highestGraceTime >= currentTime)
    {
      highestGraceTime++;
    }
    else
    {
      highestGraceTime = currentTime;
    }
    graceTimes.add(highestGraceTime); // graceTimes == this.graceLoginTimes

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME);
    }

    Attribute addAttr = Attributes.create(type, AttributeValues.create(
        type, GeneralizedTimeSyntax.format(highestGraceTime)));

    modifications.add(new Modification(ModificationType.ADD, addAttr, true));
  }



  /**
   * Specifies the set of grace login use times for the associated user.  If
   * the provided list is empty or {@code null}, then the set will be cleared.
   *
   * @param  graceLoginTimes  The grace login use times for the associated user.
   */
  public void setGraceLoginTimes(List<Long> graceLoginTimes)
  {
    if ((graceLoginTimes == null) || graceLoginTimes.isEmpty())
    {
      clearGraceLoginTimes();
      return;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Updating grace login times for user %s",
          userDNString);
    }

    this.graceLoginTimes = graceLoginTimes;

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC,
                                          true);
    AttributeBuilder builder = new AttributeBuilder(type);
    for (Long l : graceLoginTimes)
    {
      builder
          .add(AttributeValues.create(type, GeneralizedTimeSyntax.format(l)));
    }
    Attribute a = builder.toAttribute();

    modifications.add(new Modification(ModificationType.REPLACE, a, true));
  }



  /**
   * Updates the user entry to remove any record of previous grace logins.
   */
  public void clearGraceLoginTimes()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing grace login times for user %s",
          userDNString);
    }

    List<Long> graceTimes = getGraceLoginTimes();
    if (graceTimes.isEmpty())
    {
      return;
    }
    graceTimes.clear(); // graceTimes == this.graceLoginTimes

    AttributeType type =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(
                                  OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME);
    }

    modifications.add(new Modification(ModificationType.REPLACE,
                                         Attributes.empty(type), true));
  }



  /**
   * Retrieves a list of the clear-text passwords for the user.  If the user
   * does not have any passwords in the clear, then the list will be empty.
   *
   * @return  A list of the clear-text passwords for the user.
   */
  public List<ByteString> getClearPasswords()
  {
    LinkedList<ByteString> clearPasswords = new LinkedList<ByteString>();

    List<Attribute> attrList =
         userEntry.getAttribute(passwordPolicy.getPasswordAttribute());

    if (attrList == null)
    {
      return clearPasswords;
    }

    for (Attribute a : attrList)
    {
      boolean usesAuthPasswordSyntax = passwordPolicy.isAuthPasswordSyntax();

      for (AttributeValue v : a)
      {
        try
        {
          StringBuilder[] pwComponents;
          if (usesAuthPasswordSyntax)
          {
            pwComponents =
                 AuthPasswordSyntax.decodeAuthPassword(v.getValue().toString());
          }
          else
          {
            String[] userPwComponents =
                 UserPasswordSyntax.decodeUserPassword(v.getValue().toString());
            pwComponents = new StringBuilder[userPwComponents.length];
            for (int i = 0; i < userPwComponents.length; ++i)
            {
              pwComponents[i] = new StringBuilder(userPwComponents[i]);
            }
          }

          String schemeName = pwComponents[0].toString();
          PasswordStorageScheme<?> scheme = (usesAuthPasswordSyntax)
                    ? DirectoryServer.getAuthPasswordStorageScheme(schemeName)
                    : DirectoryServer.getPasswordStorageScheme(schemeName);
          if (scheme == null)
          {
            if (debugEnabled())
            {
              TRACER.debugWarning("User entry %s contains a password with " +
                  "scheme %s that is not defined in the server.",
                                  userDNString, schemeName);
            }

            continue;
          }

          if (scheme.isReversible())
          {
            ByteString clearValue = (usesAuthPasswordSyntax)
                         ? scheme.getAuthPasswordPlaintextValue(
                               pwComponents[1].toString(),
                               pwComponents[2].toString())
                         : scheme.getPlaintextValue(
                ByteString.valueOf(pwComponents[1].toString()));
            clearPasswords.add(clearValue);
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          if (debugEnabled())
          {
            TRACER.debugWarning("Cannot get clear password value foruser %s: " +
                "%s", userDNString, e);
          }
        }
      }
    }

    return clearPasswords;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean passwordMatches(ByteString password)
  {
    List<Attribute> attrList =
         userEntry.getAttribute(passwordPolicy.getPasswordAttribute());
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false because user %s does not have " +
            "any values for password attribute %s", userDNString,
            passwordPolicy.getPasswordAttribute().getNameOrOID());
      }

      return false;
    }

    for (Attribute a : attrList)
    {
      boolean usesAuthPasswordSyntax = passwordPolicy.isAuthPasswordSyntax();

      for (AttributeValue v : a)
      {
        try
        {
          StringBuilder[] pwComponents;
          if (usesAuthPasswordSyntax)
          {
            pwComponents =
                 AuthPasswordSyntax.decodeAuthPassword(v.getValue().toString());
          }
          else
          {
            String[] userPwComponents =
                 UserPasswordSyntax.decodeUserPassword(v.getValue().toString());
            pwComponents = new StringBuilder[userPwComponents.length];
            for (int i = 0; i < userPwComponents.length; ++i)
            {
              pwComponents[i] = new StringBuilder(userPwComponents[i]);
            }
          }

          String schemeName = pwComponents[0].toString();
          PasswordStorageScheme<?> scheme = (usesAuthPasswordSyntax)
                     ? DirectoryServer.getAuthPasswordStorageScheme(schemeName)
                     : DirectoryServer.getPasswordStorageScheme(schemeName);
          if (scheme == null)
          {
            if (debugEnabled())
            {
              TRACER.debugWarning("User entry %s contains a password with " +
                  "scheme %s that is not defined in the server.",
                                  userDNString, schemeName);
            }

            continue;
          }

          boolean passwordMatches = (usesAuthPasswordSyntax)
                     ? scheme.authPasswordMatches(password,
                                                  pwComponents[1].toString(),
                                                  pwComponents[2].toString())
                     : scheme.passwordMatches(password,
              ByteString.valueOf(pwComponents[1].toString()));
          if (passwordMatches)
          {
            if (debugEnabled())
            {
              TRACER.debugInfo("Returning true for user %s because the " +
                  "provided password matches a value encoded with scheme %s",
                  userDNString, schemeName);
            }

            return true;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          if (debugEnabled())
          {
            TRACER.debugWarning("An error occurred while attempting to " +
                "process a password value for user %s: %s",
                     userDNString, stackTraceToSingleLineString(e));
          }
        }
      }
    }

    // If we've gotten here, then we couldn't find a match.
    if (debugEnabled())
    {
      TRACER.debugInfo("Returning false because the provided password does " +
          "not match any of the stored password values for user %s",
          userDNString);
    }

    return false;
  }



  /**
   * Indicates whether the provided password value is pre-encoded.
   *
   * @param  passwordValue  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided password value is pre-encoded,
   *          or <CODE>false</CODE> if it is not.
   */
  public boolean passwordIsPreEncoded(ByteString passwordValue)
  {
    if (passwordPolicy.isAuthPasswordSyntax())
    {
      return AuthPasswordSyntax.isEncoded(passwordValue);
    }
    else
    {
      return UserPasswordSyntax.isEncoded(passwordValue);
    }
  }



  /**
   * Encodes the provided password using the default storage schemes (using the
   * appropriate syntax for the password attribute).
   *
   * @param  password  The password to be encoded.
   *
   * @return  The password encoded using the default schemes.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to encode
   *                              the password.
   */
  public List<ByteString> encodePassword(ByteString password)
         throws DirectoryException
  {
    List<PasswordStorageScheme<?>> schemes =
         passwordPolicy.getDefaultPasswordStorageSchemes();
    List<ByteString> encodedPasswords =
         new ArrayList<ByteString>(schemes.size());

    if (passwordPolicy.isAuthPasswordSyntax())
    {
      for (PasswordStorageScheme<?> s : schemes)
      {
        encodedPasswords.add(s.encodeAuthPassword(password));
      }
    }
    else
    {
      for (PasswordStorageScheme<?> s : schemes)
      {
        encodedPasswords.add(s.encodePasswordWithScheme(password));
      }
    }

    return encodedPasswords;
  }



  /**
   * Indicates whether the provided password appears to be acceptable according
   * to the password validators.
   *
   * @param  operation         The operation that provided the password.
   * @param  userEntry         The user entry in which the password is used.
   * @param  newPassword       The password to be validated.
   * @param  currentPasswords  The set of clear-text current passwords for the
   *                           user (this may be a subset if not all of them are
   *                           available in the clear, or empty if none of them
   *                           are available in the clear).
   * @param  invalidReason     A buffer that may be used to hold the invalid
   *                           reason if the password is rejected.
   *
   * @return  <CODE>true</CODE> if the password is acceptable for use, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean passwordIsAcceptable(Operation operation, Entry userEntry,
      ByteString newPassword, Set<ByteString> currentPasswords,
      MessageBuilder invalidReason)
  {
    for (PasswordValidator<?> validator : passwordPolicy
        .getPasswordValidators())
    {
      if (!validator.passwordIsAcceptable(newPassword, currentPasswords,
          operation, userEntry, invalidReason))
      {
        if (debugEnabled())
        {
          TRACER.debugInfo("The password provided for user %s failed "
              + "validation: %s", userDNString, invalidReason.toString());
        }
        return false;
      }
    }
    return true;
  }



  /**
   * Performs any processing that may be necessary to remove deprecated storage
   * schemes from the user's entry that match the provided password and
   * re-encodes them using the default schemes.
   *
   * @param  password  The clear-text password provided by the user.
   */
  public void handleDeprecatedStorageSchemes(ByteString password)
  {
    if (passwordPolicy.getDefaultPasswordStorageSchemes().isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Doing nothing for user %s because no " +
            "deprecated storage schemes have been defined.", userDNString);
      }

      return;
    }


    AttributeType type = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = userEntry.getAttribute(type);
    if ((attrList == null) || attrList.isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Doing nothing for entry %s because no password " +
            "values were found.", userDNString);
      }

      return;
    }


    HashSet<String> existingDefaultSchemes = new HashSet<String>();
    LinkedHashSet<AttributeValue> removedValues =
         new LinkedHashSet<AttributeValue>();
    LinkedHashSet<AttributeValue> updatedValues =
         new LinkedHashSet<AttributeValue>();

    boolean usesAuthPasswordSyntax = passwordPolicy.isAuthPasswordSyntax();

    for (Attribute a : attrList)
    {
      Iterator<AttributeValue> iterator = a.iterator();
      while (iterator.hasNext())
      {
        AttributeValue v = iterator.next();

        try
        {
          StringBuilder[] pwComponents;
          if (usesAuthPasswordSyntax)
          {
            pwComponents =
                 AuthPasswordSyntax.decodeAuthPassword(v.getValue().toString());
          }
          else
          {
            String[] userPwComponents =
                 UserPasswordSyntax.decodeUserPassword(v.getValue().toString());
            pwComponents = new StringBuilder[userPwComponents.length];
            for (int i = 0; i < userPwComponents.length; ++i)
            {
              pwComponents[i] = new StringBuilder(userPwComponents[i]);
            }
          }

          String schemeName = pwComponents[0].toString();
          PasswordStorageScheme<?> scheme = (usesAuthPasswordSyntax)
                    ? DirectoryServer.getAuthPasswordStorageScheme(schemeName)
                    : DirectoryServer.getPasswordStorageScheme(schemeName);
          if (scheme == null)
          {
            if (debugEnabled())
            {
              TRACER.debugWarning("Skipping password value for user %s " +
                  "because the associated storage scheme %s is not " +
                  "configured for use.", userDNString, schemeName);
            }

            continue;
          }

          boolean passwordMatches = (usesAuthPasswordSyntax)
                     ? scheme.authPasswordMatches(password,
                                                  pwComponents[1].toString(),
                                                  pwComponents[2].toString())
                     : scheme.passwordMatches(password,
              ByteString.valueOf(pwComponents[1].toString()));
          if (passwordMatches)
          {
            if (passwordPolicy.isDefaultPasswordStorageScheme(schemeName))
            {
              existingDefaultSchemes.add(schemeName);
              updatedValues.add(v);
            }
            else if (passwordPolicy
                .isDeprecatedPasswordStorageScheme(schemeName))
            {
              if (debugEnabled())
              {
                TRACER.debugInfo("Marking password with scheme %s for " +
                    "removal from user entry %s.", schemeName, userDNString);
              }

              removedValues.add(v);
            }
            else
            {
              updatedValues.add(v);
            }
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugWarning("Skipping password value for user %s because " +
                "an error occurred while attempting to decode it based on " +
                "the user password syntax: %s",
                userDNString, stackTraceToSingleLineString(e));
          }
        }
      }
    }

    if (removedValues.isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("User entry %s does not have any password values " +
            "encoded using deprecated schemes.", userDNString);
      }

      return;
    }

    LinkedHashSet<AttributeValue> addedValues = new
         LinkedHashSet<AttributeValue>();
    for (PasswordStorageScheme<?> s :
         passwordPolicy.getDefaultPasswordStorageSchemes())
    {
      if (! existingDefaultSchemes.contains(
           toLowerCase(s.getStorageSchemeName())))
      {
        try
        {
          ByteString encodedPassword = (usesAuthPasswordSyntax)
                                       ? s.encodeAuthPassword(password)
                                       : s.encodePasswordWithScheme(password);
          AttributeValue v =
              AttributeValues.create(type, encodedPassword);
          addedValues.add(v);
          updatedValues.add(v);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          if (debugEnabled())
          {
            TRACER.debugWarning("Unable to encode password for user %s using " +
                 "default scheme %s: %s",
                         userDNString, s.getStorageSchemeName(),
                         stackTraceToSingleLineString(e));
          }
        }
      }
    }

    if (updatedValues.isEmpty())
    {
      if (debugEnabled())
      {
        TRACER.debugWarning("Not updating user entry %s because removing " +
             "deprecated schemes would leave the user without a password.",
                     userDNString);
      }

      return;
    }

    AttributeBuilder builder = new AttributeBuilder(type);
    builder.addAll(removedValues);
    Attribute a = builder.toAttribute();
    modifications.add(new Modification(ModificationType.DELETE, a, true));

    if (! addedValues.isEmpty())
    {
      builder = new AttributeBuilder(type);
      builder.addAll(addedValues);
      Attribute a2 = builder.toAttribute();
      modifications.add(new Modification(ModificationType.ADD, a2, true));
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Updating user entry %s to replace password values " +
          "encoded with deprecated schemes with values encoded " +
          "with the default schemes.", userDNString);
    }
  }



  /**
   * Indicates whether password history information should be matained for this
   * user.
   *
   * @return  {@code true} if password history information should be maintained
   *          for this user, or {@code false} if not.
   */
  public boolean maintainHistory()
  {
    return ((passwordPolicy.getPasswordHistoryCount() > 0) ||
            (passwordPolicy.getPasswordHistoryDuration() > 0));
  }



  /**
   * Indicates whether the provided password is equal to any of the current
   * passwords, or any of the passwords in the history.
   *
   * @param  password  The password for which to make the determination.
   *
   * @return  {@code true} if the provided password is equal to any of the
   *          current passwords or any of the passwords in the history, or
   *          {@code false} if not.
   */
  public boolean isPasswordInHistory(ByteString password)
  {
    if (! maintainHistory())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning false because password history " +
            "checking is disabled.");
      }

      // Password history checking is disabled, so we don't care if it is in the
      // list or not.
      return false;
    }


    // Check to see if the provided password is equal to any of the current
    // passwords.  If so, then we'll consider it to be in the history.
    if (passwordMatches(password))
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning true because the provided password " +
            "is currently in use.");
      }

      return true;
    }


    // Get the attribute containing the history and check to see if any of the
    // values is equal to the provided password.  However, first prune the list
    // by size and duration if necessary.
    TreeMap<Long,AttributeValue> historyMap = getSortedHistoryValues(null);

    int historyCount = passwordPolicy.getPasswordHistoryCount();
    if ((historyCount > 0) && (historyMap.size() > historyCount))
    {
      int numToDelete = historyMap.size() - historyCount;
      Iterator<Long> iterator = historyMap.keySet().iterator();
      while ((iterator.hasNext()) && (numToDelete > 0))
      {
        iterator.next();
        iterator.remove();
        numToDelete--;
      }
    }

    long historyDuration = passwordPolicy.getPasswordHistoryDuration();
    if (historyDuration > 0L)
    {
      long retainDate = currentTime - (1000 * historyDuration);
      Iterator<Long> iterator = historyMap.keySet().iterator();
      while (iterator.hasNext())
      {
        long historyDate = iterator.next();
        if (historyDate < retainDate)
        {
          iterator.remove();
        }
        else
        {
          break;
        }
      }
    }

    for (AttributeValue v : historyMap.values())
    {
      if (historyValueMatches(password, v))
      {
        if (debugEnabled())
        {
          TRACER.debugInfo("Returning true because the password is in " +
              "the history.");
        }

        return true;
      }
    }


    // If we've gotten here, then the password isn't in the history.
    if (debugEnabled())
    {
      TRACER.debugInfo("Returning false because the password isn't in the " +
          "history.");
    }

    return false;
  }



  /**
   * Gets a sorted list of the password history values contained in the user's
   * entry.  The values will be sorted by timestamp.
   *
   * @param  removeAttrs  A list into which any values will be placed that could
   *                      not be properly decoded.  It may be {@code null} if
   *                      this is not needed.
   */
  private TreeMap<Long,AttributeValue> getSortedHistoryValues(List<Attribute>
                                                                   removeAttrs)
  {
    TreeMap<Long,AttributeValue> historyMap =
         new TreeMap<Long,AttributeValue>();
    AttributeType historyType =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_HISTORY_LC, true);
    List<Attribute> attrList = userEntry.getAttribute(historyType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          String histStr = v.getValue().toString();
          int    hashPos = histStr.indexOf('#');
          if (hashPos <= 0)
          {
            if (debugEnabled())
            {
              TRACER.debugInfo("Found value " + histStr + " in the " +
                  "history with no timestamp.  Marking it " +
                  "for removal.");
            }

            if (removeAttrs != null)
            {
              removeAttrs.add(Attributes.create(a.getAttributeType(), v));
            }
          }
          else
          {
            try
            {
              long timestamp =
                   GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                       ByteString.valueOf(histStr.substring(0, hashPos)));
              historyMap.put(timestamp, v);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);

                TRACER.debugInfo("Could not decode the timestamp in " +
                    "history value " + histStr + " -- " + e +
                    ".  Marking it for removal.");
              }

              if (removeAttrs != null)
              {
                removeAttrs.add(Attributes
                    .create(a.getAttributeType(), v));
              }
            }
          }
        }
      }
    }

    return historyMap;
  }



  /**
   * Indicates whether the provided password matches the given history value.
   *
   * @param  password      The clear-text password for which to make the
   *                       determination.
   * @param  historyValue  The encoded history value to compare against the
   *                       clear-text password.
   *
   * @return  {@code true} if the provided password matches the history value,
   *          or {@code false} if not.
   */
  private boolean historyValueMatches(ByteString password,
                                      AttributeValue historyValue)
  {
    // According to draft-behera-ldap-password-policy, password history values
    // should be in the format time#syntaxoid#encodedvalue.  In this method,
    // we only care about the syntax OID and encoded password.
    try
    {
      String histStr  = historyValue.getValue().toString();
      int    hashPos1 = histStr.indexOf('#');
      if (hashPos1 <= 0)
      {
        if (debugEnabled())
        {
          TRACER.debugInfo("Returning false because the password history " +
              "value didn't include any hash characters.");
        }

        return false;
      }

      int hashPos2 = histStr.indexOf('#', hashPos1+1);
      if (hashPos2 < 0)
      {
        if (debugEnabled())
        {
          TRACER.debugInfo("Returning false because the password history " +
              "value only had one hash character.");
        }

        return false;
      }

      String syntaxOID = toLowerCase(histStr.substring(hashPos1+1, hashPos2));
      if (syntaxOID.equals(SYNTAX_AUTH_PASSWORD_OID))
      {
        StringBuilder[] authPWComponents =
             AuthPasswordSyntax.decodeAuthPassword(
                  histStr.substring(hashPos2+1));
        PasswordStorageScheme<?> scheme =
             DirectoryServer.getAuthPasswordStorageScheme(
                  authPWComponents[0].toString());
        if (scheme.authPasswordMatches(password, authPWComponents[1].toString(),
                                       authPWComponents[2].toString()))
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Returning true because the auth password " +
                "history value matched.");
          }

          return true;
        }
        else
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Returning false because the auth password " +
                "history value did not match.");
          }

          return false;
        }
      }
      else if (syntaxOID.equals(SYNTAX_USER_PASSWORD_OID))
      {
        String[] userPWComponents =
             UserPasswordSyntax.decodeUserPassword(
                  histStr.substring(hashPos2+1));
        PasswordStorageScheme<?> scheme =
             DirectoryServer.getPasswordStorageScheme(userPWComponents[0]);
        if (scheme.passwordMatches(password,
            ByteString.valueOf(userPWComponents[1])))
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Returning true because the user password " +
                "history value matched.");
          }

          return true;
        }
        else
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Returning false because the user password " +
                "history value did not match.");
          }

          return false;
        }
      }
      else
      {
        if (debugEnabled())
        {
          TRACER.debugInfo("Returning false because the syntax OID " +
              syntaxOID + " didn't match for either the auth " +
              "or user password syntax.");
        }

        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        if (debugEnabled())
        {
          TRACER.debugInfo("Returning false because of an exception:  " +
                           stackTraceToSingleLineString(e));
        }
      }

      return false;
    }
  }



  /**
   * Updates the password history information for this user by adding all
   * current passwords to it.
   */
  public void updatePasswordHistory()
  {
    List<Attribute> attrList =
         userEntry.getAttribute(passwordPolicy.getPasswordAttribute());
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          addPasswordToHistory(v.getValue().toString());
        }
      }
    }
  }



  /**
   * Adds the provided password to the password history.  If appropriate, one or
   * more old passwords may be evicted from the list if the total size would
   * exceed the configured count, or if passwords are older than the configured
   * duration.
   *
   * @param  encodedPassword  The encoded password (in either user password or
   *                          auth password format) to be added to the history.
   */
  private void addPasswordToHistory(String encodedPassword)
  {
    if (! maintainHistory())
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Not doing anything because password history " +
            "maintenance is disabled.");
      }

      return;
    }


    // Get a sorted list of the existing values to see if there are any that
    // should be removed.
    LinkedList<Attribute> removeAttrs = new LinkedList<Attribute>();
    TreeMap<Long,AttributeValue> historyMap =
         getSortedHistoryValues(removeAttrs);


    // If there is a maximum number of values to retain and we would be over the
    // limit with the new value, then get rid of enough values (oldest first)
    // to satisfy the count.
    AttributeType historyType =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_HISTORY_LC, true);
    int historyCount = passwordPolicy.getPasswordHistoryCount();
    if  ((historyCount > 0) && (historyMap.size() >= historyCount))
    {
      int numToDelete = (historyMap.size() - historyCount) + 1;
      LinkedHashSet<AttributeValue> removeValues =
           new LinkedHashSet<AttributeValue>(numToDelete);
      Iterator<AttributeValue> iterator = historyMap.values().iterator();
      while (iterator.hasNext() && (numToDelete > 0))
      {
        AttributeValue v = iterator.next();
        removeValues.add(v);
        iterator.remove();
        numToDelete--;

        if (debugEnabled())
        {
          TRACER.debugInfo("Removing history value " +
              v.getValue().toString() + " to preserve the history count.");
        }
      }

      if (! removeValues.isEmpty())
      {
        AttributeBuilder builder = new AttributeBuilder(historyType);
        builder.addAll(removeValues);
        removeAttrs.add(builder.toAttribute());
      }
    }


    // If there is a maximum duration, then get rid of any values that would be
    // over the duration.
    long historyDuration = passwordPolicy.getPasswordHistoryDuration();
    if (historyDuration > 0L)
    {
      long minAgeToKeep = currentTime - (1000L * historyDuration);
      Iterator<Long> iterator = historyMap.keySet().iterator();
      LinkedHashSet<AttributeValue> removeValues =
           new LinkedHashSet<AttributeValue>();
      while (iterator.hasNext())
      {
        long timestamp = iterator.next();
        if (timestamp < minAgeToKeep)
        {
          AttributeValue v = historyMap.get(timestamp);
          removeValues.add(v);
          iterator.remove();

          if (debugEnabled())
          {
            TRACER.debugInfo("Removing history value " +
                v.getValue().toString() +
                " to preserve the history duration.");
          }
        }
        else
        {
          break;
        }
      }

      if (! removeValues.isEmpty())
      {
        AttributeBuilder builder = new AttributeBuilder(historyType);
        builder.addAll(removeValues);
        removeAttrs.add(builder.toAttribute());
      }
    }


    // At this point, we can add the new value.  However, we want to make sure
    // that its timestamp (which is the current time) doesn't conflict with any
    // value already in the list.  If there is a conflict, then simply add one
    // to it until we don't have any more conflicts.
    long newTimestamp = currentTime;
    while (historyMap.containsKey(newTimestamp))
    {
      newTimestamp++;
    }
    String newHistStr = GeneralizedTimeSyntax.format(newTimestamp) + "#" +
                        passwordPolicy.getPasswordAttribute().getSyntaxOID() +
                        "#" + encodedPassword;
    Attribute newHistAttr = Attributes.create(historyType, newHistStr);

    if (debugEnabled())
    {
      TRACER.debugInfo("Going to add history value " + newHistStr);
    }


    // Apply the changes, either by adding modifications or by directly updating
    // the entry.
    for (Attribute a : removeAttrs)
    {
      modifications.add(new Modification(ModificationType.DELETE, a, true));
    }

    modifications.add(new Modification(ModificationType.ADD, newHistAttr,
        true));
  }



  /**
   * Retrieves the password history state values for the user.  This is only
   * intended for testing purposes.
   *
   * @return  The password history state values for the user.
   */
  public String[] getPasswordHistoryValues()
  {
    ArrayList<String> historyValues = new ArrayList<String>();
    AttributeType historyType =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_HISTORY_LC, true);
    List<Attribute> attrList = userEntry.getAttribute(historyType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          historyValues.add(v.getValue().toString());
        }
      }
    }

    String[] historyArray = new String[historyValues.size()];
    return historyValues.toArray(historyArray);
  }



  /**
   * Clears the password history state information for the user.  This is only
   * intended for testing purposes.
   */
  public void clearPasswordHistory()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("Clearing password history for user %s", userDNString);
    }

    AttributeType type = DirectoryServer.getAttributeType(
                             OP_ATTR_PWPOLICY_HISTORY_LC, true);
    modifications.add(new Modification(ModificationType.REPLACE,
                                         Attributes.empty(type), true));
  }



  /**
   * Generates a new password for the user.
   *
   * @return  The new password that has been generated, or <CODE>null</CODE> if
   *          no password generator has been defined.
   *
   * @throws  DirectoryException  If an error occurs while attempting to
   *                              generate the new password.
   */
  public ByteString generatePassword()
      throws DirectoryException
  {
    PasswordGenerator<?> generator = passwordPolicy.getPasswordGenerator();
    if (generator == null)
    {
      if (debugEnabled())
      {
        TRACER.debugWarning("Unable to generate a new password for user " +
            "%s because no password generator has been defined in the " +
            "associated password policy.", userDNString);
      }

      return null;
    }

    return generator.generatePassword(userEntry);
  }



  /**
   * Generates an account status notification for this user.
   *
   * @param  notificationType        The type for the account status
   *                                 notification.
   * @param  userEntry               The entry for the user to which this
   *                                 notification applies.
   * @param  message                 The human-readable message for the
   *                                 notification.
   * @param  notificationProperties  The set of properties for the notification.
   */
  public void generateAccountStatusNotification(
          AccountStatusNotificationType notificationType,
          Entry userEntry, Message message,
          Map<AccountStatusNotificationProperty,List<String>>
               notificationProperties)
  {
    generateAccountStatusNotification(new AccountStatusNotification(
         notificationType, userEntry, message, notificationProperties));
  }



  /**
   * Generates an account status notification for this user.
   *
   * @param  notification  The account status notification that should be
   *                       generated.
   */
  public void generateAccountStatusNotification(
                   AccountStatusNotification notification)
  {
    Collection<AccountStatusNotificationHandler<?>> handlers =
         passwordPolicy.getAccountStatusNotificationHandlers();
    for (AccountStatusNotificationHandler<?> handler : handlers)
    {
      handler.handleStatusNotification(notification);
    }
  }



  /**
   * Retrieves the set of modifications that correspond to changes made in
   * password policy processing that may need to be applied to the user entry.
   *
   * @return  The set of modifications that correspond to changes made in
   *          password policy processing that may need to be applied to the user
   *          entry.
   */
  public List<Modification> getModifications()
  {
    return modifications;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeStateAfterBind()
         throws DirectoryException
  {
    // If there are no modifications, then there's nothing to do.
    if (modifications.isEmpty())
    {
      return;
    }


    // Convert the set of modifications to a set of LDAP modifications.
    ArrayList<RawModification> modList = new ArrayList<RawModification>();
    for (Modification m : modifications)
    {
      modList.add(RawModification.create(m.getModificationType(),
                       new LDAPAttribute(m.getAttribute())));
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation internalModify =
         conn.processModify(ByteString.valueOf(userDNString), modList);

    ResultCode resultCode = internalModify.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      Message message = ERR_PWPSTATE_CANNOT_UPDATE_USER_ENTRY.get(userDNString,
                            String.valueOf(internalModify.getErrorMessage()));

      // If this is a root user, or if the password policy says that we should
      // ignore these problems, then log a warning message.  Otherwise, cause
      // the bind to fail.
      if ((DirectoryServer.isRootDN(userEntry.getDN()) ||
          (passwordPolicy.getStateUpdateFailurePolicy() ==
           PasswordPolicyCfgDefn.StateUpdateFailurePolicy.IGNORE)))
      {
        ErrorLogger.logError(message);
      }
      else
      {
        throw new DirectoryException(resultCode, message);
      }
    }
  }
}


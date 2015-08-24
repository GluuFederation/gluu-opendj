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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.types;

import static org.opends.server.types.AccountStatusNotificationProperty.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PasswordPolicyState;

/**
 * This class defines a data type for storing information associated
 * with an account status notification.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class AccountStatusNotification
{
  /** The notification type for this account status notification. */
  private AccountStatusNotificationType notificationType;

  /** The entry for the user to whom this notification applies. */
  private Entry userEntry;

  /**
   * A set of additional properties that may be useful for this
   * notification.
   */
  private Map<AccountStatusNotificationProperty,List<String>>
               notificationProperties;

  /**
   * A message that provides additional information for this account
   * status notification.
   */
  private LocalizableMessage message;



  /**
   * Creates a new account status notification object with the
   * provided information.
   *
   * @param  notificationType        The type for this account status
   *                                 notification.
   * @param  userEntry               The entry for the user to whom
   *                                 this notification applies.
   * @param  message                 The human-readable message for
   *                                 this notification.
   * @param  notificationProperties  A set of properties that may
   *                                 include additional information
   *                                 about this notification.
   */
  public AccountStatusNotification(
              AccountStatusNotificationType notificationType,
              Entry userEntry, LocalizableMessage message,
              Map<AccountStatusNotificationProperty,List<String>>
                   notificationProperties)
  {
    this.notificationType = notificationType;
    this.userEntry        = userEntry;
    this.message          = message;

    if (notificationProperties == null)
    {
      this.notificationProperties = new HashMap<>(0);
    }
    else
    {
      this.notificationProperties = notificationProperties;
    }
  }



  /**
   * Retrieves the notification type for this account status
   * notification.
   *
   * @return  The notification type for this account status
   *          notification.
   */
  public AccountStatusNotificationType getNotificationType()
  {
    return notificationType;
  }



  /**
   * Retrieves the DN of the user entry to which this notification
   * applies.
   *
   * @return  The DN of the user entry to which this notification
   *          applies.
   */
  public DN getUserDN()
  {
    return userEntry.getName();
  }



  /**
   * Retrieves user entry for whom this notification applies.
   *
   * @return  The user entry for whom this notification applies.
   */
  public Entry getUserEntry()
  {
    return userEntry;
  }



  /**
   * Retrieves a message that provides additional information for this
   * account status notification.
   *
   * @return  A message that provides additional information for this
   *          account status notification.
   */
  public LocalizableMessage getMessage()
  {
    return message;
  }



  /**
   * Retrieves a set of properties that may provide additional
   * information for this account status notification.
   *
   * @return  A set of properties that may provide additional
   *          information for this account status notification.
   */
  public Map<AccountStatusNotificationProperty,List<String>>
              getNotificationProperties()
  {
    return notificationProperties;
  }



  /**
   * Retrieves the set of values for the specified account status
   * notification property.
   *
   * @param  property  The account status notification property for
   *                   which to retrieve the associated values.
   *
   * @return  The set of values for the specified account status
   *          notification property, or {@code null} if the specified
   *          property is not defined for this account status
   *          notification.
   */
  public List<String> getNotificationProperty(
                           AccountStatusNotificationProperty property)
  {
    return notificationProperties.get(property);
  }



  /**
   * Creates a set of account status notification properties from the
   * provided information.
   *
   * @param  pwPolicyState     The password policy state for the user
   *                           associated with the notification.
   * @param  tempLocked        Indicates whether the user's account
   *                           has been temporarily locked.
   * @param  timeToExpiration  The length of time in seconds until the
   *                           user's password expires, or -1 if it's
   *                           not about to expire.
   * @param  oldPasswords      The set of old passwords for the user,
   *                           or {@code null} if this is not
   *                           applicable.
   * @param  newPasswords      The set of new passwords for the user,
   *                           or {@code null} if this is not
   *                           applicable.
   *
   * @return  The created set of account status notification
   *          properties.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=false)
  public static Map<AccountStatusNotificationProperty,List<String>>
                     createProperties(
                          PasswordPolicyState pwPolicyState,
                          boolean tempLocked, int timeToExpiration,
                          List<ByteString> oldPasswords,
                          List<ByteString> newPasswords)
  {
    HashMap<AccountStatusNotificationProperty,List<String>> props = new HashMap<>(4);

    PasswordPolicy policy = pwPolicyState.getAuthenticationPolicy();
    props.put(PASSWORD_POLICY_DN, newArrayList(policy.getDN().toString()));

    if (tempLocked)
    {
      long secondsUntilUnlock = policy.getLockoutDuration();
      if (secondsUntilUnlock > 0L)
      {
        props.put(SECONDS_UNTIL_UNLOCK, newArrayList(String.valueOf(secondsUntilUnlock)));

        String string = secondsToTimeString(secondsUntilUnlock).toString();
        props.put(TIME_UNTIL_UNLOCK, newArrayList(string));

        long unlockTime = System.currentTimeMillis() + (1000 * secondsUntilUnlock);
        props.put(ACCOUNT_UNLOCK_TIME, newArrayList(new Date(unlockTime).toString()));
      }
    }

    if (timeToExpiration >= 0)
    {
      props.put(SECONDS_UNTIL_EXPIRATION, newArrayList(String.valueOf(timeToExpiration)));

      String string = secondsToTimeString(timeToExpiration).toString();
      props.put(TIME_UNTIL_EXPIRATION, newArrayList(string));

      long expTime = System.currentTimeMillis() + (1000 * timeToExpiration);
      props.put(PASSWORD_EXPIRATION_TIME, newArrayList(new Date(expTime).toString()));
    }

    if (oldPasswords != null && !oldPasswords.isEmpty())
    {
      props.put(OLD_PASSWORD, toStrings(oldPasswords));
    }
    if (newPasswords != null && !newPasswords.isEmpty())
    {
      props.put(NEW_PASSWORD, toStrings(newPasswords));
    }

    return props;
  }

  private static ArrayList<String> toStrings(List<ByteString> byteStrings)
  {
    ArrayList<String> results = new ArrayList<>(byteStrings.size());
    for (ByteString v : byteStrings)
    {
      results.add(v.toString());
    }
    return results;
  }



  /**
   * Retrieves a string representation of this account status
   * notification.
   *
   * @return  A string representation of this account status
   *          notification.
   */
  @Override
  public String toString()
  {
    return "AccountStatusNotification(type=" +
           notificationType.getName() + ",dn=" + userEntry.getName() +
           ",message=" + message + ")";
  }
}

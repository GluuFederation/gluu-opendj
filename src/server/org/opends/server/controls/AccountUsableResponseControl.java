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
 */
package org.opends.server.controls;
import org.opends.messages.Message;


import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the account usable response control.  This is a
 * Sun-defined control with OID 1.3.6.1.4.1.42.2.27.9.5.8.  The value of this
 * control is composed according to the following BNF:
 * <BR>
 * <PRE>
 * ACCOUNT_USABLE_RESPONSE ::= CHOICE {
 *      is_available           [0] INTEGER, -- Seconds before expiration --
 *      is_not_available       [1] MORE_INFO }
 *
 * MORE_INFO ::= SEQUENCE {
 *      inactive               [0] BOOLEAN DEFAULT FALSE,
 *      reset                  [1] BOOLEAN DEFAULT FALSE,
 *      expired                [2] BOOLEAN DEFAULT_FALSE,
 *      remaining_grace        [3] INTEGER OPTIONAL,
 *      seconds_before_unlock  [4] INTEGER OPTIONAL }
 * </PRE>
 */
public class AccountUsableResponseControl
    extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<AccountUsableResponseControl>
  {
    /**
     * {@inheritDoc}
     */
    public AccountUsableResponseControl decode(boolean isCritical,
                                               ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        // The response control must always have a value.
        Message message = ERR_ACCTUSABLERES_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      try
      {
        ASN1Reader reader = ASN1.getReader(value);
        switch (reader.peekType())
        {
          case TYPE_SECONDS_BEFORE_EXPIRATION:
            int secondsBeforeExpiration = (int)reader.readInteger();
            return new AccountUsableResponseControl(isCritical,
                secondsBeforeExpiration);
          case TYPE_MORE_INFO:
            boolean isInactive = false;
            boolean isReset = false;
            boolean isExpired = false;
            boolean isLocked = false;
            int     remainingGraceLogins = -1;
            int     secondsBeforeUnlock = 0;

            reader.readStartSequence();
            if(reader.hasNextElement() &&
                reader.peekType() == TYPE_INACTIVE)
            {
              isInactive = reader.readBoolean();
            }
            if(reader.hasNextElement() &&
                reader.peekType() == TYPE_RESET)
            {
              isReset = reader.readBoolean();
            }
            if(reader.hasNextElement() &&
                reader.peekType() == TYPE_EXPIRED)
            {
              isExpired = reader.readBoolean();
            }
            if(reader.hasNextElement() &&
                reader.peekType() == TYPE_REMAINING_GRACE_LOGINS)
            {
              remainingGraceLogins = (int)reader.readInteger();
            }
            if(reader.hasNextElement() &&
                reader.peekType() == TYPE_SECONDS_BEFORE_UNLOCK)
            {
              isLocked = true;
              secondsBeforeUnlock = (int)reader.readInteger();
            }
            reader.readEndSequence();

            return new AccountUsableResponseControl(isCritical,
                isInactive, isReset,
                isExpired,
                remainingGraceLogins,
                isLocked,
                secondsBeforeUnlock);

          default:
            Message message = ERR_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE.get(
                byteToHex(reader.peekType()));
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
        }
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_ACCTUSABLERES_DECODE_ERROR.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }
    }

    public String getOID()
    {
      return OID_ACCOUNT_USABLE_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<AccountUsableResponseControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * The BER type to use for the seconds before expiration when the account is
   * available.
   */
  public static final byte TYPE_SECONDS_BEFORE_EXPIRATION = (byte) 0x80;



  /**
   * The BER type to use for the MORE_INFO sequence when the account is not
   * available.
   */
  public static final byte TYPE_MORE_INFO = (byte) 0xA1;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * account has been inactivated.
   */
  public static final byte TYPE_INACTIVE = (byte) 0x80;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * password has been administratively reset.
   */
  public static final byte TYPE_RESET = (byte) 0x81;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * user's password is expired.
   */
  public static final byte TYPE_EXPIRED = (byte) 0x82;



  /**
   * The BER type to use for the MORE_INFO element that provides the number of
   * remaining grace logins.
   */
  public static final byte TYPE_REMAINING_GRACE_LOGINS = (byte) 0x83;



  /**
   * The BER type to use for the MORE_INFO element that indicates that the
   * password has been administratively reset.
   */
  public static final byte TYPE_SECONDS_BEFORE_UNLOCK = (byte) 0x84;



  // Indicates whether the user's account is usable.
  private boolean isUsable;

  // Indicates whether the user's password is expired.
  private boolean isExpired;

  // Indicates whether the user's account is inactive.
  private boolean isInactive;

  // Indicates whether the user's account is currently locked.
  private boolean isLocked;

  // Indicates whether the user's password has been reset and must be changed
  // before anything else can be done.
  private boolean isReset;

  // The number of remaining grace logins, if available.
  private int remainingGraceLogins;

  // The length of time in seconds before the user's password expires, if
  // available.
  private int secondsBeforeExpiration;

  // The length of time before the user's account is unlocked, if available.
  private int secondsBeforeUnlock;



  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is available and provide the number of seconds
   * until expiration.  It will use the default OID and criticality.
   *
   * @param  secondsBeforeExpiration  The length of time in seconds until the
   *                                  user's password expires, or -1 if the
   *                                  user's password will not expire or the
   *                                  expiration time is unknown.
   */
  public AccountUsableResponseControl(int secondsBeforeExpiration)
  {
    this(false, secondsBeforeExpiration);
  }

  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is available and provide the number of seconds
   * until expiration.  It will use the default OID and criticality.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param  secondsBeforeExpiration  The length of time in seconds until the
   *                                  user's password expires, or -1 if the
   *                                  user's password will not expire or the
   *                                  expiration time is unknown.
   */
  public AccountUsableResponseControl(boolean isCritical,
                                      int secondsBeforeExpiration)
  {
    super(OID_ACCOUNT_USABLE_CONTROL, isCritical);


    this.secondsBeforeExpiration = secondsBeforeExpiration;

    isUsable             = true;
    isInactive           = false;
    isReset              = false;
    isExpired            = false;
    remainingGraceLogins = -1;
    isLocked             = false;
    secondsBeforeUnlock  = 0;
  }



  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is not available and provide information about
   * the underlying reason.  It will use the default OID and criticality.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param  isInactive            Indicates whether the user's account has been
   *                               inactivated by an administrator.
   * @param  isReset               Indicates whether the user's password has
   *                               been reset by an administrator.
   * @param  isExpired             Indicates whether the user's password is
   *                               expired.
   * @param  remainingGraceLogins  The number of grace logins remaining.  A
   *                               value of zero indicates that there are none
   *                               remaining.  A value of -1 indicates that
   *                               grace login functionality is not enabled.
   * @param  isLocked              Indicates whether the user's account is
   *                               currently locked out.
   * @param  secondsBeforeUnlock   The length of time in seconds until the
   *                               account is unlocked.  A value of -1 indicates
   *                               that the account will not be automatically
   *                               unlocked and must be reset by an
   *                               administrator.
   */
  public AccountUsableResponseControl(boolean isCritical, boolean isInactive,
                                      boolean isReset,
                                      boolean isExpired,
                                      int remainingGraceLogins,
                                      boolean isLocked, int secondsBeforeUnlock)
  {
    super(OID_ACCOUNT_USABLE_CONTROL, isCritical);


    this.isInactive           = isInactive;
    this.isReset              = isReset;
    this.isExpired            = isExpired;
    this.remainingGraceLogins = remainingGraceLogins;
    this.isLocked             = isLocked;
    this.secondsBeforeUnlock  = secondsBeforeUnlock;

    isUsable                = false;
    secondsBeforeExpiration = -1;
  }

  /**
   * Creates a new account usability response control that may be used to
   * indicate that the account is not available and provide information about
   * the underlying reason.  It will use the default OID and criticality.
   *
   * @param  isInactive            Indicates whether the user's account has been
   *                               inactivated by an administrator.
   * @param  isReset               Indicates whether the user's password has
   *                               been reset by an administrator.
   * @param  isExpired             Indicates whether the user's password is
   *                               expired.
   * @param  remainingGraceLogins  The number of grace logins remaining.  A
   *                               value of zero indicates that there are none
   *                               remaining.  A value of -1 indicates that
   *                               grace login functionality is not enabled.
   * @param  isLocked              Indicates whether the user's account is
   *                               currently locked out.
   * @param  secondsBeforeUnlock   The length of time in seconds until the
   *                               account is unlocked.  A value of -1 indicates
   *                               that the account will not be automatically
   *                               unlocked and must be reset by an
   *                               administrator.
   */
  public AccountUsableResponseControl(boolean isInactive, boolean isReset,
                                      boolean isExpired,
                                      int remainingGraceLogins,
                                      boolean isLocked, int secondsBeforeUnlock)
  {
    this(false, isInactive, isReset, isExpired, remainingGraceLogins,
        isLocked, secondsBeforeUnlock);
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    if(isUsable)
    {
      writer.writeInteger(TYPE_SECONDS_BEFORE_EXPIRATION,
          secondsBeforeExpiration);
    }
    else
    {
      writer.writeStartSequence(TYPE_MORE_INFO);
      if (isInactive)
      {
        writer.writeBoolean(TYPE_INACTIVE, true);
      }

      if (isReset)
      {
        writer.writeBoolean(TYPE_RESET, true);
      }

      if (isExpired)
      {
        writer.writeBoolean(TYPE_EXPIRED, true);

        if (remainingGraceLogins >= 0)
        {
          writer.writeInteger(TYPE_REMAINING_GRACE_LOGINS,
              remainingGraceLogins);
        }
      }

      if (isLocked)
      {
        writer.writeInteger(TYPE_SECONDS_BEFORE_UNLOCK,
            secondsBeforeUnlock);
      }
      writer.writeEndSequence();
    }

    writer.writeEndSequence();
  }




  /**
   * Indicates whether the associated user account is available for use.
   *
   * @return  <CODE>true</CODE> if the associated user account is available, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isUsable()
  {
    return isUsable;
  }



  /**
   * Retrieves the length of time in seconds before the user's password expires.
   * This value is unreliable if the account is not available.
   *
   * @return  The length of time in seconds before the user's password expires,
   *          or -1 if it is unknown or password expiration is not enabled for
   *          the user.
   */
  public int getSecondsBeforeExpiration()
  {
    return secondsBeforeExpiration;
  }



  /**
   * Indicates whether the user's account has been inactivated by an
   * administrator.
   *
   * @return  <CODE>true</CODE> if the user's account has been inactivated by
   *          an administrator, or <CODE>false</CODE> if not.
   */
  public boolean isInactive()
  {
    return isInactive;
  }



  /**
   * Indicates whether the user's password has been administratively reset and
   * the user must change that password before any other operations will be
   * allowed.
   *
   * @return  <CODE>true</CODE> if the user's password has been administratively
   *          reset, or <CODE>false</CODE> if not.
   */
  public boolean isReset()
  {
    return isReset;
  }



  /**
   * Indicates whether the user's password is expired.
   *
   * @return  <CODE>true</CODE> if the user's password is expired, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isExpired()
  {
    return isExpired;
  }



  /**
   * Retrieves the number of remaining grace logins for the user.  This value is
   * unreliable if the user's password is not expired.
   *
   * @return  The number of remaining grace logins for the user, or -1 if the
   *          grace logins feature is not enabled for the user.
   */
  public int getRemainingGraceLogins()
  {
    return remainingGraceLogins;
  }



  /**
   * Indicates whether the user's account is locked for some reason.
   *
   * @return  <CODE>true</CODE> if the user's account is locked, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isLocked()
  {
    return isLocked;
  }



  /**
   * Retrieves the length of time in seconds before the user's account is
   * automatically unlocked.  This value is unreliable is the user's account is
   * not locked.
   *
   * @return  The length of time in seconds before the user's account is
   *          automatically unlocked, or -1 if it requires administrative action
   *          to unlock the account.
   */
  public int getSecondsBeforeUnlock()
  {
    return secondsBeforeUnlock;
  }



  /**
   * Appends a string representation of this password policy response control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AccountUsableResponseControl(isUsable=");
    buffer.append(isUsable);

    if (isUsable)
    {
      buffer.append(",secondsBeforeExpiration=");
      buffer.append(secondsBeforeExpiration);
    }
    else
    {
      buffer.append(",isInactive=");
      buffer.append(isInactive);
      buffer.append(",isReset=");
      buffer.append(isReset);
      buffer.append(",isExpired=");
      buffer.append(isExpired);
      buffer.append(",remainingGraceLogins=");
      buffer.append(remainingGraceLogins);
      buffer.append(",isLocked=");
      buffer.append(isLocked);
      buffer.append(",secondsBeforeUnlock=");
      buffer.append(secondsBeforeUnlock);
    }

    buffer.append(")");
  }
}


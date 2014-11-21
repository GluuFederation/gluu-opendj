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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;

import static org.opends.messages.QuickSetupMessages.*;

import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;


/**
 * This is the exception that is thrown in ADSContext.
 * @see org.opends.admin.ads.ADSContext
 *
 */
public class ADSContextException extends OpenDsException {

  private static final long serialVersionUID = 1984039711031042813L;

  private String toString;

  /**
   * The enumeration containing the different error types.
   *
   */
  public enum ErrorType
  {
    /**
     * The host name is missing.
     */
    MISSING_HOSTNAME(),
    /**
     * The host name is not valid.
     */
    NOVALID_HOSTNAME(),
    /**
     * The installation path is missing.
     */
    MISSING_IPATH(),
    /**
     * The installation path is not valid.
     */
    NOVALID_IPATH(),
    /**
     * An access permission error.
     */
    ACCESS_PERMISSION(),
    /**
     * The entity is already registered.
     */
    ALREADY_REGISTERED(),
    /**
     * The installation is broken.
     */
    BROKEN_INSTALL(),
    /**
     * The entity is not yet registered.
     */
    NOT_YET_REGISTERED(),
    /**
     * The port is missing.
     */
    MISSING_PORT(),
    /**
     * The port is not valid.
     */
    NOVALID_PORT(),
    /**
     * The name is missing.
     */
    MISSING_NAME(),
    /**
     * The administration UID is missing.
     */
    MISSING_ADMIN_UID(),
    /**
     * The administrator password is missing.
     */
    MISSING_ADMIN_PASSWORD(),
    /**
     * There is already a backend with the name of the ADS backend but not
     * of the expected type.
     */
    UNEXPECTED_ADS_BACKEND_TYPE(),
    /**
     * Error merging with another ADSContext.
     */
    ERROR_MERGING,
    /**
     * Unexpected error (potential bug).
     */
    ERROR_UNEXPECTED();
  };

  ErrorType error;
  Throwable embeddedException;

  /**
   * Creates an ADSContextException of the given error type.
   * @param error the error type.
   */
  public ADSContextException(ErrorType error)
  {
    this(error, null);
  }

  /**
   * Creates an ADSContextException of the given error type with the provided
   * error cause.
   * @param error the error type.
   * @param x the throwable that generated this exception.
   */
  public ADSContextException(ErrorType error, Throwable x)
  {
    this(error, getMessage(error, x), x);
  }

  /**
   * Creates an ADSContextException of the given error type with the provided
   * error cause and message.
   * @param error the error type.
   * @param msg the message describing the error.
   * @param x the throwable that generated this exception.
   */
  public ADSContextException(ErrorType error, Message msg, Throwable x)
  {
    super(msg);
    this.error = error;
    this.embeddedException = x;
    toString = "ADSContextException: error type "+error+".";
    if (getCause() != null)
    {
      toString += "  Root cause: "+getCause().toString();
    }
  }

  /**
   * Returns the error type of this exception.
   * @return the error type of this exception.
   */
  public ErrorType getError()
  {
    return error;
  }

  /**
   * Returns the throwable that caused this exception.  It might be null.
   * @return the throwable that caused this exception.
   */
  public Throwable getCause()
  {
    return embeddedException;
  }

  /**
   * {@inheritDoc}
   */
  public void printStackTrace()
  {
    super.printStackTrace();
    if (embeddedException != null)
    {
      System.out.println("embeddedException = {");
      embeddedException.printStackTrace();
      System.out.println("}");
    }
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return toString;
  }

  private static Message getMessage(ErrorType error, Throwable x)
  {
    Message msg;
    if (x != null)
    {
      if (x instanceof OpenDsException)
      {
        msg = INFO_ADS_CONTEXT_EXCEPTION_WITH_DETAILS_MSG.get(error.toString(),
            ((OpenDsException)x).getMessageObject());
      }
      else
      {
        msg = INFO_ADS_CONTEXT_EXCEPTION_WITH_DETAILS_MSG.get(error.toString(),
            x.toString());
      }
    }
    else
    {
      msg = INFO_ADS_CONTEXT_EXCEPTION_MSG.get(error.toString());
    }
    return msg;
  }
}

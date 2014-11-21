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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;
import org.opends.messages.Message;



import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;



/**
 * This class defines a set of methods that are available for use by
 * pre-operation plugins for bind operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreOperationBindOperation
       extends PreOperationOperation
{
  /**
   * Retrieves the authentication type for this bind operation.
   *
   * @return  The authentication type for this bind operation.
   */
  public AuthenticationType getAuthenticationType();



  /**
   * Retrieves a string representation of the protocol version
   * associated with this bind request.
   *
   * @return  A string representation of the protocol version
   *          associated with this bind request.
   */
  public String getProtocolVersion();



  /**
   * Retrieves the raw, unprocessed bind DN for this bind operation as
   * contained in the client request.  The value may not actually
   * contain a valid DN, as no validation will have been performed.
   *
   * @return  The raw, unprocessed bind DN for this bind operation as
   *          contained in the client request.
   */
  public ByteString getRawBindDN();



  /**
   * Retrieves the bind DN for this bind operation.
   *
   * @return  The bind DN for this bind operation.
   */
  public DN getBindDN();



  /**
   * Retrieves the simple authentication password for this bind
   * operation.
   *
   * @return  The simple authentication password for this bind
   *          operation.
   */
  public ByteString getSimplePassword();



  /**
   * Retrieves the SASL mechanism for this bind operation.
   *
   * @return  The SASL mechanism for this bind operation, or
   *          <CODE>null</CODE> if the bind does not use SASL
   *          authentication.
   */
  public String getSASLMechanism();



  /**
   * Retrieves the SASL credentials for this bind operation.
   *
   * @return  The SASL credentials for this bind operation, or
   *          <CODE>null</CODE> if there are none or if the bind does
   *          not use SASL authentication.
   */
  public ByteString getSASLCredentials();



  /**
   * Specifies the set of server SASL credentials to include in the
   * bind response.
   *
   * @param  serverSASLCredentials  The set of server SASL credentials
   *                                to include in the bind response.
   */
  public void setServerSASLCredentials(ByteString
                                            serverSASLCredentials);



  /**
   * Specifies the reason that the authentication failed.
   *
   * @param  reason  A human-readable message providing the reason
   *                 that the authentication failed.
   */
  public void setAuthFailureReason(Message reason);



  /**
   * Retrieves the user entry DN for this bind operation.  It will
   * only be available for simple bind operations (and may be
   * different than the bind DN from the client request).
   *
   * @return  The user entry DN for this bind operation, or
   *          <CODE>null</CODE> if the bind processing has not
   *          progressed far enough to identify the user or if the
   *          user DN could not be determined.
   */
  public DN getUserEntryDN();
}


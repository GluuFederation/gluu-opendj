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
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.types.operation;
import org.opends.messages.Message;



import java.util.List;

import org.opends.server.types.*;

import org.opends.messages.MessageBuilder;


/**
 * This class defines a set of methods that are available for use by
 * post-operation plugins for all types of operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PostOperationOperation
       extends PluginOperation
{
  /**
   * Adds the provided control to the set of controls to include in
   * the response to the client.
   *
   * @param  control  The control to add to the set of controls to
   *                  include in the response to the client.
   */
  public void addResponseControl(Control control);



  /**
   * Removes the provided control from the set of controls to include
   * in the response to the client.
   *
   * @param  control  The control to remove from the set of controls
   *                  to include in the response to the client.
   */
  public void removeResponseControl(Control control);



  /**
   * Retrieves the result code for this operation.
   *
   * @return  The result code associated for this operation, or
   *          <CODE>UNDEFINED</CODE> if the operation has not yet
   *          completed.
   */
  public ResultCode getResultCode();



  /**
   * Specifies the result code for this operation.
   *
   * @param  resultCode  The result code for this operation.
   */
  public void setResultCode(ResultCode resultCode);



  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by the caller.
   *
   * @return  The error message for this operation.
   */
  public MessageBuilder getErrorMessage();



  /**
   * Specifies the error message for this operation.
   *
   * @param  errorMessage  The error message for this operation.
   */
  public void setErrorMessage(MessageBuilder errorMessage);



  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.
   *
   * @param  message  The message to append to the error message
   */
  public void appendErrorMessage(Message message);



  /**
   * Retrieves the matched DN for this operation.
   *
   * @return  The matched DN for this operation, or <CODE>null</CODE>
   *          if the operation has not yet completed or does not have
   *          a matched DN.
   */
  public DN getMatchedDN();



  /**
   * Specifies the matched DN for this operation.
   *
   * @param  matchedDN  The matched DN for this operation.
   */
  public void setMatchedDN(DN matchedDN);



  /**
   * Retrieves the set of referral URLs for this operation.  Its
   * contents must not be altered by the caller.
   *
   * @return  The set of referral URLs for this operation, or
   *          <CODE>null</CODE> if the operation is not yet complete
   *          or does not have a set of referral URLs.
   */
  public List<String> getReferralURLs();



  /**
   * Specifies the set of referral URLs for this operation.
   *
   * @param  referralURLs  The set of referral URLs for this
   *                       operation.
   */
  public void setReferralURLs(List<String> referralURLs);



  /**
   * Sets the response elements for this operation based on the
   * information contained in the provided
   * <CODE>DirectoryException</CODE> object.
   *
   * @param  directoryException  The exception containing the
   *                             information to use for the response
   *                             elements.
   */
  public void setResponseData(DirectoryException directoryException);



  /**
   * Retrieves the authorization DN for this operation.  In many
   * cases, it will be the same as the DN of the authenticated user
   * for the underlying connection, or the null DN if no
   * authentication has been performed on that connection.  However,
   * it may be some other value if special processing has been
   * requested (e.g., the operation included a proxied authorization
   * control).
   *
   * @return  The authorization DN for this operation.
   */
  public DN getAuthorizationDN();



  /**
   * Returns an unmodifiable list containing the additional log items for this
   * operation, which should be written to the log but not included in the
   * response to the client.
   *
   * @return An unmodifiable list containing the additional log items for this
   *         operation.
   */
  public List<AdditionalLogItem> getAdditionalLogItems();



  /**
   * Adds an additional log item to this operation, which should be written to
   * the log but not included in the response to the client. This method may not
   * be called by post-response plugins.
   *
   * @param item
   *          The additional log item for this operation.
   */
  public void addAdditionalLogItem(AdditionalLogItem item);
}


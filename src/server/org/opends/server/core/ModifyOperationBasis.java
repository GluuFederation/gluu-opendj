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
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.core;

import org.opends.messages.MessageBuilder;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ENTRY_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ERROR_MESSAGE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_MATCHED_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_PROCESSING_TIME;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_REFERRAL_URLS;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_RESULT_CODE;
import static org.opends.server.loggers.AccessLogger.logModifyRequest;
import static org.opends.server.loggers.AccessLogger.logModifyResponse;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.messages.CoreMessages.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.workflowelement.localbackend.*;
import org.opends.server.protocols.ldap.LDAPResultCode;


/**
 * This class defines an operation that may be used to modify an entry in the
 * Directory Server.
 */
public class ModifyOperationBasis
       extends AbstractOperation implements ModifyOperation,
       PreParseModifyOperation,
       PostResponseModifyOperation
       {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // The raw, unprocessed entry DN as included by the client request.
  private ByteString rawEntryDN;

  // The DN of the entry for the modify operation.
  private DN entryDN;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The set of response controls for this modify operation.
  private List<Control> responseControls;

  // The raw, unprocessed set of modifications as included in the client
  // request.
  private List<RawModification> rawModifications;

  // The set of modifications for this modify operation.
  private List<Modification> modifications;

  // The change number that has been assigned to this operation.
  private long changeNumber;

  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed DN of the entry to modify,
   *                           as included in the client request.
   * @param  rawModifications  The raw, unprocessed set of modifications for
   *                           this modify operation as included in the client
   *                           request.
   */
  public ModifyOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      ByteString rawEntryDN,
      List<RawModification> rawModifications)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN       = rawEntryDN;
    this.rawModifications = rawModifications;

    entryDN          = null;
    modifications    = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
  }

  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for the modify operation.
   * @param  modifications     The set of modifications for this modify
   *                           operation.
   */
  public ModifyOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      DN entryDN, List<Modification> modifications)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN       = entryDN;
    this.modifications = modifications;

    rawEntryDN = ByteString.valueOf(entryDN.toString());

    rawModifications = new ArrayList<RawModification>(modifications.size());
    for (Modification m : modifications)
    {
      rawModifications.add(new LDAPModification(m.getModificationType(),
          new LDAPAttribute(m.getAttribute())));
    }

    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
  }

  /**
   * {@inheritDoc}
   */
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }

  /**
   * {@inheritDoc}
   */
  public final DN getEntryDN()
  {
    if (entryDN == null){
      try {
        entryDN = DN.decode(rawEntryDN);
      }
      catch (DirectoryException de) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getMessageObject());
      }
    }
    return entryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final List<RawModification> getRawModifications()
  {
    return rawModifications;
  }

  /**
   * {@inheritDoc}
   */
  public final void addRawModification(RawModification rawModification)
  {
    rawModifications.add(rawModification);

    modifications = null;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawModifications(List<RawModification> rawModifications)
  {
    this.rawModifications = rawModifications;

    modifications = null;
  }

  /**
   * {@inheritDoc}
   */
  public final List<Modification> getModifications()
  {
    if (modifications == null)
    {
      modifications = new ArrayList<Modification>(rawModifications.size());
      try {
        for (RawModification m : rawModifications)
        {
           Modification mod = m.toModification();
           Attribute attr = mod.getAttribute();
           AttributeType type = attr.getAttributeType();

           if(type.isBinary())
           {
             if(!attr.hasOption("binary"))
             {
               //A binary option wasn't provided by the client so add it.
               AttributeBuilder builder = new AttributeBuilder(attr);
               builder.setOption("binary");
               attr = builder.toAttribute();
               mod.setAttribute(attr);
             }
           }
           else
           {
             // binary option is not honored for non-BER-encodable attributes.
             if(attr.hasOption("binary"))
             {
               throw new LDAPException(LDAPResultCode.UNDEFINED_ATTRIBUTE_TYPE,
                       ERR_ADD_ATTR_IS_INVALID_OPTION.get(
                       String.valueOf(entryDN),
                       attr.getName()));
             }
           }

           modifications.add(mod);
        }
      }
      catch (LDAPException le)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, le);
        }
        setResultCode(ResultCode.valueOf(le.getResultCode()));
        appendErrorMessage(le.getMessageObject());
        modifications = null;
      }
    }
    return modifications;
  }

  /**
   * {@inheritDoc}
   */
  public final void addModification(Modification modification)
  throws DirectoryException
  {
    modifications.add(modification);
  }

  /**
   * {@inheritDoc}
   */
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.MODIFY;
  }

  /**
   * {@inheritDoc}
   */
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
                        {
        new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) }
                        };
  }

  /**
   * {@inheritDoc}
   */
  public final String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String resultCode = String.valueOf(getResultCode().getIntValue());

    String errorMessage;
    MessageBuilder errorMessageBuffer = getErrorMessage();
    if (errorMessageBuffer == null)
    {
      errorMessage = null;
    }
    else
    {
      errorMessage = errorMessageBuffer.toString();
    }

    String matchedDNStr;
    DN matchedDN = getMatchedDN();
    if (matchedDN == null)
    {
      matchedDNStr = null;
    }
    else
    {
      matchedDNStr = matchedDN.toString();
    }

    String referrals;
    List<String> referralURLs = getReferralURLs();
    if ((referralURLs == null) || referralURLs.isEmpty())
    {
      referrals = null;
    }
    else
    {
      StringBuilder buffer = new StringBuilder();
      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      referrals = buffer.toString();
    }

    String processingTime =
      String.valueOf(getProcessingTime());

    return new String[][]
                        {
        new String[] { LOG_ELEMENT_RESULT_CODE, resultCode },
        new String[] { LOG_ELEMENT_ERROR_MESSAGE, errorMessage },
        new String[] { LOG_ELEMENT_MATCHED_DN, matchedDNStr },
        new String[] { LOG_ELEMENT_REFERRAL_URLS, referrals },
        new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
                        };
  }

  /**
   * {@inheritDoc}
   */
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  /**
   * {@inheritDoc}
   */
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  /**
   * {@inheritDoc}
   */
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  /**
   * {@inheritDoc}
   */
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  /**
   * {@inheritDoc}
   */
  public final void toString(StringBuilder buffer)
  {
    buffer.append("ModifyOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
  }

  /**
   * {@inheritDoc}
   */
  public final long getChangeNumber(){
    return changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  public void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    // Log the modify request message.
    logModifyRequest(this);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
        DirectoryServer.getPluginConfigManager();

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;


    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse modify plugins.
      PluginResult.PreParse preParseResult =
          pluginConfigManager.invokePreParseModifyPlugins(this);

      if(!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
        return;
      }

      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);


      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the modify processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
        return;
      }


      // Retrieve the network group attached to the client connection
      // and get a workflow to process the operation.
      NetworkGroup ng = getClientConnection().getNetworkGroup();
      Workflow workflow = ng.getWorkflowCandidate(entryDN);
      if (workflow == null)
      {
        // We have found no workflow for the requested base DN, just return
        // a no such entry result code and stop the processing.
        updateOperationErrMsgAndResCode();
        return;
      }
      workflow.execute(this);
      workflowExecuted = true;

    }
    catch(CanceledOperationException coe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, coe);
      }

      setResultCode(ResultCode.CANCELED);
      cancelResult = new CancelResult(ResultCode.CANCELED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the modify response.
      logModifyResponse(this);

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }

      // Invoke the post-response callbacks.
      if (workflowExecuted) {
        invokePostResponseCallbacks();
      }

      // Invoke the post-response add plugins.
      invokePostResponsePlugins(workflowExecuted);

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
  }


  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the worklfow, otherwise invoke the post reponse plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been
   *                         executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // Invoke the post response plugins that have been registered by
      // the workflow elements
      @SuppressWarnings("unchecked")
      List<LocalBackendModifyOperation> localOperations =
          (List<LocalBackendModifyOperation>) getAttachment(
              Operation.LOCALBACKENDOPERATIONS);
      if (localOperations != null)
      {
        for (LocalBackendModifyOperation localOperation : localOperations)
        {
          pluginConfigManager.invokePostResponseModifyPlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      pluginConfigManager.invokePostResponseModifyPlugins(this);
    }
  }



  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflows were found to process
   * the operation.
   */
  private void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(
            ERR_MODIFY_NO_SUCH_ENTRY.get(String.valueOf(getEntryDN())));
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getCurrentEntry() {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public List<AttributeValue> getCurrentPasswords()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getModifiedEntry()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public List<AttributeValue> getNewPasswords()
  {
    return null;
  }

}


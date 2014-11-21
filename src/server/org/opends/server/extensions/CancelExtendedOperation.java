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
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.extensions;



import org.opends.messages.Message;
import org.opends.server.admin.std.server.CancelExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the LDAP cancel extended operation defined in RFC 3909.
 * It is similar to the LDAP abandon operation, with the exception that it
 * requires a response for both the operation that is cancelled and the cancel
 * request (whereas an abandon request never has a response, and if it is
 * successful the abandoned operation won't get one either).
 */
public class CancelExtendedOperation
       extends ExtendedOperationHandler<CancelExtendedOperationHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Create an instance of this cancel extended operation.  All initialization
   * should be performed in the <CODE>initializeExtendedOperationHandler</CODE>
   * method.
   */
  public CancelExtendedOperation()
  {
    super();
  }


  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param  config       The configuration that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeExtendedOperationHandler(
                   CancelExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    // No special configuration is required.

    DirectoryServer.registerSupportedExtension(OID_CANCEL_REQUEST, this);

    registerControlsAndFeatures();
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  @Override
  public void finalizeExtendedOperationHandler()
  {
    DirectoryServer.deregisterSupportedExtension(OID_CANCEL_REQUEST);

    deregisterControlsAndFeatures();
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public void processExtendedOperation(ExtendedOperation operation)
  {
    // The value of the request must be a sequence containing an integer element
    // that holds the message ID of the operation to cancel.  If there is no
    // value or it cannot be decoded, then fail.
    int idToCancel;
    ByteString requestValue = operation.getRequestValue();
    if (requestValue == null)
    {
      operation.setResultCode(ResultCode.PROTOCOL_ERROR);

      operation.appendErrorMessage(ERR_EXTOP_CANCEL_NO_REQUEST_VALUE.get());
      return;
    }
    else
    {
      try
      {
        ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();
        idToCancel = (int)reader.readInteger();
        reader.readEndSequence();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        operation.setResultCode(ResultCode.PROTOCOL_ERROR);

        Message message = ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE.get(
                getExceptionMessage(e));
        operation.appendErrorMessage(message);

        return;
      }
    }


    // Create the cancel request for the target operation.
    Message cancelReason =
        INFO_EXTOP_CANCEL_REASON.get(operation.getMessageID());
    CancelRequest cancelRequest = new CancelRequest(true, cancelReason);


    // Get the client connection and attempt the cancel.
    ClientConnection clientConnection = operation.getClientConnection();
    CancelResult cancelResult = clientConnection.cancelOperation(idToCancel,
                                                                 cancelRequest);


    // Update the result of the extended operation and return.
    ResultCode resultCode = cancelResult.getResultCode();
    operation.setResultCode(resultCode == ResultCode.CANCELED
                                ? ResultCode.SUCCESS : resultCode);
    operation.appendErrorMessage(cancelResult.getResponseMessage());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getExtendedOperationName()
  {
    return "Cancel";
  }
}


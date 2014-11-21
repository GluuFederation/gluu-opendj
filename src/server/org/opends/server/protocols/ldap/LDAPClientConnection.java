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
 *      Portions Copyright 2010-2013 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;



import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLException;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.*;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.extensions.ConnectionSecurityProvider;
import org.opends.server.extensions.RedirectingByteChannel;
import org.opends.server.extensions.TLSByteChannel;
import org.opends.server.extensions.TLSCapableConnection;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1ByteChannelReader;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;


/**
 * This class defines an LDAP client connection, which is a type of
 * client connection that will be accepted by an instance of the LDAP
 * connection handler and have its requests decoded by an LDAP request
 * handler.
 */
public final class LDAPClientConnection extends ClientConnection implements
    TLSCapableConnection
{

  /**
   * A runnable whose task is to close down all IO related channels
   * associated with a client connection after a small delay.
   */
  private static final class ConnectionFinalizerJob implements Runnable
  {
    /** The client connection ASN1 reader. */
    private final ASN1Reader asn1Reader;

    /** The client connection socket channel. */
    private final SocketChannel socketChannel;

    /** Creates a new connection finalizer job. */
    private ConnectionFinalizerJob(ASN1Reader asn1Reader,
        SocketChannel socketChannel)
    {
      this.asn1Reader = asn1Reader;
      this.socketChannel = socketChannel;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
      try
      {
        asn1Reader.close();
      }
      catch (Exception e)
      {
        // In general, we don't care about any exception that might be
        // thrown here.
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      try
      {
        socketChannel.close();
      }
      catch (Exception e)
      {
        // In general, we don't care about any exception that might be
        // thrown here.
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /**
   * Channel that writes the contents of the provided buffer to the client,
   * throwing an exception if the write is unsuccessful for too
   * long (e.g., if the client is unresponsive or there is a network
   * problem). If possible, it will attempt to use the selector returned
   * by the {@code ClientConnection.getWriteSelector} method, but it is
   * capable of working even if that method returns {@code null}. <BR>
   *
   * Note that the original position and limit values will not be
   * preserved, so if that is important to the caller, then it should
   * record them before calling this method and restore them after it
   * returns.
   */
  private class TimeoutWriteByteChannel implements ByteChannel
  {
    /** Synchronize concurrent writes to the same connection. */
    private final Lock writeLock = new ReentrantLock();

    @Override
    public int read(ByteBuffer byteBuffer) throws IOException
    {
      int bytesRead = clientChannel.read(byteBuffer);
      if (bytesRead > 0 && keepStats)
      {
        statTracker.updateBytesRead(bytesRead);
      }
      return bytesRead;
    }

    @Override
    public boolean isOpen()
    {
      return clientChannel.isOpen();
    }

    @Override
    public void close() throws IOException
    {
      clientChannel.close();
    }



    @Override
    public int write(ByteBuffer byteBuffer) throws IOException
    {
      writeLock.lock();
      try
      {
        int bytesToWrite = byteBuffer.remaining();
        int bytesWritten = clientChannel.write(byteBuffer);
        if (bytesWritten > 0 && keepStats)
        {
          statTracker.updateBytesWritten(bytesWritten);
        }
        if (!byteBuffer.hasRemaining())
        {
          return bytesToWrite;
        }

        long startTime = System.currentTimeMillis();
        long waitTime = getMaxBlockedWriteTimeLimit();
        if (waitTime <= 0)
        {
          // We won't support an infinite time limit, so fall back to using
          // five minutes, which is a very long timeout given that we're
          // blocking a worker thread.
          waitTime = 300000L;
        }
        long stopTime = startTime + waitTime;

        Selector selector = getWriteSelector();
        if (selector == null)
        {
          // The client connection does not provide a selector, so we'll
          // fall back to a more inefficient way that will work without a
          // selector.
          while (byteBuffer.hasRemaining()
              && (System.currentTimeMillis() < stopTime))
          {
            bytesWritten = clientChannel.write(byteBuffer);
            if (bytesWritten < 0)
            {
              // The client connection has been closed.
              throw new ClosedChannelException();
            }
            if (bytesWritten > 0 && keepStats)
            {
              statTracker.updateBytesWritten(bytesWritten);
            }
          }

          if (byteBuffer.hasRemaining())
          {
            // If we've gotten here, then the write timed out.
            throw new ClosedChannelException();
          }

          return bytesToWrite;
        }

        // Register with the selector for handling write operations.
        SelectionKey key = clientChannel.register(selector,
            SelectionKey.OP_WRITE);
        try
        {
          selector.select(waitTime);
          while (byteBuffer.hasRemaining())
          {
            long currentTime = System.currentTimeMillis();
            if (currentTime >= stopTime)
            {
              // We've been blocked for too long.
              throw new ClosedChannelException();
            }
            else
            {
              waitTime = stopTime - currentTime;
            }

            Iterator<SelectionKey> iterator = selector.selectedKeys()
                .iterator();
            while (iterator.hasNext())
            {
              SelectionKey k = iterator.next();
              if (k.isWritable())
              {
                bytesWritten = clientChannel.write(byteBuffer);
                if (bytesWritten < 0)
                {
                  // The client connection has been closed.
                  throw new ClosedChannelException();
                }
                if (bytesWritten > 0 && keepStats)
                {
                  statTracker.updateBytesWritten(bytesWritten);
                }

                iterator.remove();
              }
            }

            if (byteBuffer.hasRemaining())
            {
              selector.select(waitTime);
            }
          }

          return bytesToWrite;
        }
        finally
        {
          if (key.isValid())
          {
            key.cancel();
            selector.selectNow();
          }
        }
      }
      finally
      {
        writeLock.unlock();
      }
    }
  }


  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Thread local ASN1Writer and buffer.
   */
  private static final class ASN1WriterHolder
  {
    private final ASN1Writer writer;
    private final ByteStringBuilder buffer;
    private final int maxBufferSize;

    private ASN1WriterHolder()
    {
      this.buffer = new ByteStringBuilder();
      this.maxBufferSize = getMaxInternalBufferSize();
      this.writer = ASN1.getWriter(buffer, maxBufferSize);
    }
  }

  /**
   * Cached ASN1 writer: a thread can only write to one connection at a time.
   */
  private static final ThreadLocal<ASN1WriterHolder> ASN1_WRITER_CACHE =
      new ThreadLocal<ASN1WriterHolder>()
  {
    /**
     * {@inheritDoc}
     */
    @Override
    protected ASN1WriterHolder initialValue()
    {
      return new ASN1WriterHolder();
    }
  };

  private ASN1WriterHolder getASN1Writer()
  {
    ASN1WriterHolder holder = ASN1_WRITER_CACHE.get();
    if (holder.maxBufferSize != getMaxInternalBufferSize())
    {
      // Setting has changed, so recreate the holder.
      holder = new ASN1WriterHolder();
      ASN1_WRITER_CACHE.set(holder);
    }
    return holder;
  }

  /** The time that the last operation was completed. */
  private final AtomicLong lastCompletionTime;

  /** The next operation ID that should be used for this connection. */
  private final AtomicLong nextOperationID;

  /** The selector that may be used for write operations. */
  private final AtomicReference<Selector> writeSelector;

  /**
   * Indicates whether the Directory Server believes this connection to be valid
   * and available for communication.
   */
  private volatile boolean connectionValid;

  /**
   * Indicates whether this connection is about to be closed. This will be used
   * to prevent accepting new requests while a disconnect is in progress.
   */
  private boolean disconnectRequested;

  /**
   * Indicates whether the connection should keep statistics regarding the
   * operations that it is performing.
   */
  private final boolean keepStats;

  /** The set of all operations currently in progress on this connection. */
  private final ConcurrentHashMap<Integer, Operation> operationsInProgress;

  /**
   * The number of operations performed on this connection. Used to compare with
   * the resource limits of the network group.
   */
  private final AtomicLong operationsPerformed;

  /** The port on the client from which this connection originated. */
  private final int clientPort;

  /**
   * The LDAP version that the client is using to communicate with the server.
   */
  private int ldapVersion;

  /** The port on the server to which this client has connected. */
  private final int serverPort;

  /** The reference to the connection handler that accepted this connection. */
  private final LDAPConnectionHandler connectionHandler;

  /** The statistics tracker associated with this client connection. */
  private final LDAPStatistics statTracker;
  private boolean useNanoTime=false;


  /** The connection ID assigned to this connection. */
  private final long connectionID;

  /**
   * The lock used to provide threadsafe access to the set of operations in
   * progress.
   */
  private final Object opsInProgressLock;

  /** The socket channel with which this client connection is associated. */
  private final SocketChannel clientChannel;

  /** The byte channel used for blocking writes with time out. */
  private final ByteChannel timeoutClientChannel;

  /** The string representation of the address of the client. */
  private final String clientAddress;

  /**
   * The name of the protocol that the client is using to communicate with the
   * server.
   */
  private final String protocol;

  /**
   * The string representation of the address of the server to which the client
   * has connected.
   */
  private final String serverAddress;



  private ASN1ByteChannelReader asn1Reader;
  private final int bufferSize;
  private final RedirectingByteChannel saslChannel;
  private final RedirectingByteChannel tlsChannel;
  private volatile ConnectionSecurityProvider activeProvider = null;
  private volatile ConnectionSecurityProvider tlsPendingProvider = null;
  private volatile ConnectionSecurityProvider saslPendingProvider = null;


  /**
   * Creates a new LDAP client connection with the provided information.
   *
   * @param connectionHandler
   *          The connection handler that accepted this connection.
   * @param clientChannel
   *          The socket channel that may be used to communicate with
   *          the client.
   * @param  protocol String representing the protocol (LDAP or LDAP+SSL).
   * @throws DirectoryException If SSL initialisation fails.
   */
  LDAPClientConnection(LDAPConnectionHandler connectionHandler,
      SocketChannel clientChannel, String protocol) throws DirectoryException
  {
    this.connectionHandler = connectionHandler;
    if (connectionHandler.isAdminConnectionHandler())
    {
      setNetworkGroup(NetworkGroup.getAdminNetworkGroup());
    }

    this.clientChannel = clientChannel;
    timeoutClientChannel = new TimeoutWriteByteChannel();
    opsInProgressLock = new Object();
    ldapVersion = 3;
    lastCompletionTime = new AtomicLong(TimeThread.getTime());
    nextOperationID = new AtomicLong(0);
    connectionValid = true;
    disconnectRequested = false;
    operationsInProgress = new ConcurrentHashMap<Integer, Operation>();
    operationsPerformed = new AtomicLong(0);
    keepStats = connectionHandler.keepStats();
    this.protocol = protocol;
    writeSelector = new AtomicReference<Selector>();
    clientAddress =
        clientChannel.socket().getInetAddress().getHostAddress();
    clientPort = clientChannel.socket().getPort();
    serverAddress =
        clientChannel.socket().getLocalAddress().getHostAddress();
    serverPort = clientChannel.socket().getLocalPort();

    statTracker =
            this.connectionHandler.getStatTracker();

    if (keepStats)
    {
      statTracker.updateConnect();
      this.useNanoTime=DirectoryServer.getUseNanoTime();
    }

    bufferSize = connectionHandler.getBufferSize();

    tlsChannel =
        RedirectingByteChannel.getRedirectingByteChannel(
            timeoutClientChannel);
    saslChannel =
        RedirectingByteChannel.getRedirectingByteChannel(tlsChannel);
    this.asn1Reader =
        ASN1.getReader(saslChannel, bufferSize, connectionHandler
            .getMaxRequestSize());

    if (connectionHandler.useSSL())
    {
      enableSSL(connectionHandler.getTLSByteChannel(timeoutClientChannel));
    }

    connectionID = DirectoryServer.newConnectionAccepted(this);
  }

  /**
   * Retrieves the connection ID assigned to this connection.
   *
   * @return The connection ID assigned to this connection.
   */
  @Override
  public long getConnectionID()
  {
    return connectionID;
  }



  /**
   * Retrieves the connection handler that accepted this client
   * connection.
   *
   * @return The connection handler that accepted this client
   *         connection.
   */
  @Override
  public ConnectionHandler<?> getConnectionHandler()
  {
    return connectionHandler;
  }



  /**
   * Retrieves the socket channel that can be used to communicate with
   * the client.
   *
   * @return The socket channel that can be used to communicate with the
   *         client.
   */
  @Override
  public SocketChannel getSocketChannel()
  {
    return clientChannel;
  }



  /**
   * Retrieves the protocol that the client is using to communicate with
   * the Directory Server.
   *
   * @return The protocol that the client is using to communicate with
   *         the Directory Server.
   */
  @Override
  public String getProtocol()
  {
    return protocol;
  }



  /**
   * Retrieves a string representation of the address of the client.
   *
   * @return A string representation of the address of the client.
   */
  @Override
  public String getClientAddress()
  {
    return clientAddress;
  }



  /**
   * Retrieves the port number for this connection on the client system.
   *
   * @return The port number for this connection on the client system.
   */
  @Override
  public int getClientPort()
  {
    return clientPort;
  }



  /**
   * Retrieves a string representation of the address on the server to
   * which the client connected.
   *
   * @return A string representation of the address on the server to
   *         which the client connected.
   */
  @Override
  public String getServerAddress()
  {
    return serverAddress;
  }



  /**
   * Retrieves the port number for this connection on the server system.
   *
   * @return The port number for this connection on the server system.
   */
  @Override
  public int getServerPort()
  {
    return serverPort;
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> associated with the
   * remote client system.
   *
   * @return The <CODE>java.net.InetAddress</CODE> associated with the
   *         remote client system. It may be <CODE>null</CODE> if the
   *         client is not connected over an IP-based connection.
   */
  @Override
  public InetAddress getRemoteAddress()
  {
    return clientChannel.socket().getInetAddress();
  }



  /**
   * Retrieves the <CODE>java.net.InetAddress</CODE> for the Directory
   * Server system to which the client has established the connection.
   *
   * @return The <CODE>java.net.InetAddress</CODE> for the Directory
   *         Server system to which the client has established the
   *         connection. It may be <CODE>null</CODE> if the client is
   *         not connected over an IP-based connection.
   */
  @Override
  public InetAddress getLocalAddress()
  {
    return clientChannel.socket().getLocalAddress();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConnectionValid()
  {
    return this.connectionValid;
  }

  /**
   * Indicates whether this client connection is currently using a
   * secure mechanism to communicate with the server. Note that this may
   * change over time based on operations performed by the client or
   * server (e.g., it may go from <CODE>false</CODE> to
   * <CODE>true</CODE> if the client uses the StartTLS extended
   * operation).
   *
   * @return <CODE>true</CODE> if the client connection is currently
   *         using a secure mechanism to communicate with the server, or
   *         <CODE>false</CODE> if not.
   */
  @Override
  public boolean isSecure()
  {
    if (activeProvider != null)
      return activeProvider.isSecure();
    else
      return false;
  }



  /**
   * Sends a response to the client based on the information in the
   * provided operation.
   *
   * @param operation
   *          The operation for which to send the response.
   */
  @Override
  public void sendResponse(Operation operation)
  {
    // Since this is the final response for this operation, we can go
    // ahead and remove it from the "operations in progress" list. It
    // can't be canceled after this point, and this will avoid potential
    // race conditions in which the client immediately sends another
    // request with the same message ID as was used for this operation.

    if (keepStats) {
        long time;
        if (useNanoTime) {
            time = operation.getProcessingNanoTime();
        } else {
            time = operation.getProcessingTime();
        }
        this.statTracker.updateOperationMonitoringData(
                operation.getOperationType(),
                time);
    }

    // Avoid sending the response if one has already been sent. This may happen
    // if operation processing encounters a run-time exception after sending the
    // response: the worker thread exception handling code will attempt to send
    // an error result to the client indicating that a problem occurred.
    if (removeOperationInProgress(operation.getMessageID()))
    {
      LDAPMessage message = operationToResponseLDAPMessage(operation);
      if (message != null)
      {
        sendLDAPMessage(message);
      }
    }
  }



  /**
   * Retrieves an LDAPMessage containing a response generated from the
   * provided operation.
   *
   * @param operation
   *          The operation to use to generate the response LDAPMessage.
   * @return An LDAPMessage containing a response generated from the
   *         provided operation.
   */
  private LDAPMessage operationToResponseLDAPMessage(Operation operation)
  {
    ResultCode resultCode = operation.getResultCode();
    if (resultCode == null)
    {
      // This must mean that the operation has either not yet completed
      // or that it completed without a result for some reason. In any
      // case, log a message and set the response to "operations error".
      logError(ERR_LDAP_CLIENT_SEND_RESPONSE_NO_RESULT_CODE.get(
          operation.getOperationType().toString(), operation
              .getConnectionID(), operation.getOperationID()));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    MessageBuilder errorMessage = operation.getErrorMessage();
    DN matchedDN = operation.getMatchedDN();

    // Referrals are not allowed for LDAPv2 clients.
    List<String> referralURLs;
    if (ldapVersion == 2)
    {
      referralURLs = null;

      if (resultCode == ResultCode.REFERRAL)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        errorMessage.append(ERR_LDAPV2_REFERRAL_RESULT_CHANGED.get());
      }

      List<String> opReferrals = operation.getReferralURLs();
      if ((opReferrals != null) && (!opReferrals.isEmpty()))
      {
        StringBuilder referralsStr = new StringBuilder();
        Iterator<String> iterator = opReferrals.iterator();
        referralsStr.append(iterator.next());

        while (iterator.hasNext())
        {
          referralsStr.append(", ");
          referralsStr.append(iterator.next());
        }

        errorMessage.append(ERR_LDAPV2_REFERRALS_OMITTED.get(String
            .valueOf(referralsStr)));
      }
    }
    else
    {
      referralURLs = operation.getReferralURLs();
    }

    ProtocolOp protocolOp;
    switch (operation.getOperationType())
    {
    case ADD:
      protocolOp =
          new AddResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs);
      break;
    case BIND:
      ByteString serverSASLCredentials =
          ((BindOperationBasis) operation).getServerSASLCredentials();
      protocolOp =
          new BindResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs,
              serverSASLCredentials);
      break;
    case COMPARE:
      protocolOp =
          new CompareResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs);
      break;
    case DELETE:
      protocolOp =
          new DeleteResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs);
      break;
    case EXTENDED:
      // If this an LDAPv2 client, then we can't send this.
      if (ldapVersion == 2)
      {
        logError(ERR_LDAPV2_SKIPPING_EXTENDED_RESPONSE.get(
            getConnectionID(), operation.getOperationID(), String
                .valueOf(operation)));
        return null;
      }

      ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
      protocolOp =
          new ExtendedResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs, extOp
                  .getResponseOID(), extOp.getResponseValue());
      break;
    case MODIFY:
      protocolOp =
          new ModifyResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs);
      break;
    case MODIFY_DN:
      protocolOp =
          new ModifyDNResponseProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs);
      break;
    case SEARCH:
      protocolOp =
          new SearchResultDoneProtocolOp(resultCode.getIntValue(),
              errorMessage.toMessage(), matchedDN, referralURLs);
      break;
    default:
      // This must be a type of operation that doesn't have a response.
      // This shouldn't happen, so log a message and return.
      logError(ERR_LDAP_CLIENT_SEND_RESPONSE_INVALID_OP.get(String
          .valueOf(operation.getOperationType()), getConnectionID(),
          operation.getOperationID(), String.valueOf(operation)));
      return null;
    }

    // Controls are not allowed for LDAPv2 clients.
    List<Control> controls;
    if (ldapVersion == 2)
    {
      controls = null;
    }
    else
    {
      controls = operation.getResponseControls();
    }

    return new LDAPMessage(operation.getMessageID(), protocolOp,
        controls);
  }



  /**
   * Sends the provided search result entry to the client.
   *
   * @param searchOperation
   *          The search operation with which the entry is associated.
   * @param searchEntry
   *          The search result entry to be sent to the client.
   */
  @Override
  public void sendSearchEntry(SearchOperation searchOperation,
      SearchResultEntry searchEntry)
  {
    SearchResultEntryProtocolOp protocolOp =
        new SearchResultEntryProtocolOp(searchEntry, ldapVersion);

    sendLDAPMessage(new LDAPMessage(searchOperation.getMessageID(),
        protocolOp, searchEntry.getControls()));
  }



  /**
   * Sends the provided search result reference to the client.
   *
   * @param searchOperation
   *          The search operation with which the reference is
   *          associated.
   * @param searchReference
   *          The search result reference to be sent to the client.
   * @return <CODE>true</CODE> if the client is able to accept
   *         referrals, or <CODE>false</CODE> if the client cannot
   *         handle referrals and no more attempts should be made to
   *         send them for the associated search operation.
   */
  @Override
  public boolean sendSearchReference(SearchOperation searchOperation,
      SearchResultReference searchReference)
  {
    // Make sure this is not an LDAPv2 client. If it is, then they can't
    // see referrals so we'll not send anything. Also, throw an
    // exception so that the core server will know not to try sending
    // any more referrals to this client for the rest of the operation.
    if (ldapVersion == 2)
    {
      Message message =
          ERR_LDAPV2_SKIPPING_SEARCH_REFERENCE.get(getConnectionID(),
              searchOperation.getOperationID(), String
                  .valueOf(searchReference));
      logError(message);
      return false;
    }

    SearchResultReferenceProtocolOp protocolOp =
        new SearchResultReferenceProtocolOp(searchReference);

    sendLDAPMessage(new LDAPMessage(searchOperation.getMessageID(),
        protocolOp, searchReference.getControls()));
    return true;
  }



  /**
   * Sends the provided intermediate response message to the client.
   *
   * @param intermediateResponse
   *          The intermediate response message to be sent.
   * @return <CODE>true</CODE> if processing on the associated operation
   *         should continue, or <CODE>false</CODE> if not.
   */
  @Override
  protected boolean sendIntermediateResponseMessage(
      IntermediateResponse intermediateResponse)
  {
    IntermediateResponseProtocolOp protocolOp =
        new IntermediateResponseProtocolOp(intermediateResponse
            .getOID(), intermediateResponse.getValue());

    Operation operation = intermediateResponse.getOperation();

    LDAPMessage message =
        new LDAPMessage(operation.getMessageID(), protocolOp,
            intermediateResponse.getControls());
    sendLDAPMessage(message);

    // The only reason we shouldn't continue processing is if the
    // connection is closed.
    return connectionValid;
  }



  /**
   * Sends the provided LDAP message to the client.
   *
   * @param message
   *          The LDAP message to send to the client.
   */
  private void sendLDAPMessage(LDAPMessage message)
  {
    // Use a thread local writer.
    final ASN1WriterHolder holder = getASN1Writer();
    try
    {
      message.write(holder.writer);
      holder.buffer.copyTo(saslChannel);

      if (debugEnabled())
      {
        TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
          message.toString());
      }

      if (keepStats)
      {
        statTracker.updateMessageWritten(message);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // FIXME -- Log a message or something
      disconnect(DisconnectReason.SERVER_ERROR, false, null);
      return;
    }
    finally
    {
      // Clear and reset all of the internal buffers ready for the next usage.
      // The ASN1Writer is based on a ByteStringBuilder so closing will cause
      // the internal buffers to be resized if needed.
      close(holder.writer);
    }
 }



  /**
   * Closes the connection to the client, optionally sending it a
   * message indicating the reason for the closure. Note that the
   * ability to send a notice of disconnection may not be available for
   * all protocols or under all circumstances.
   *
   * @param disconnectReason
   *          The disconnect reason that provides the generic cause for
   *          the disconnect.
   * @param sendNotification
   *          Indicates whether to try to provide notification to the
   *          client that the connection will be closed.
   * @param message
   *          The message to include in the disconnect notification
   *          response. It may be <CODE>null</CODE> if no message is to
   *          be sent.
   */
  @Override
  public void disconnect(DisconnectReason disconnectReason,
      boolean sendNotification, Message message)
  {
    // Set a flag indicating that the connection is being terminated so
    // that no new requests will be accepted. Also cancel all operations
    // in progress.
    synchronized (opsInProgressLock)
    {
      // If we are already in the middle of a disconnect, then don't
      // do anything.
      if (disconnectRequested)
      {
        return;
      }

      disconnectRequested = true;
    }

    if (keepStats)
    {
      statTracker.updateDisconnect();
    }

    if (connectionID >= 0)
    {
      DirectoryServer.connectionClosed(this);
    }

    // Indicate that this connection is no longer valid.
    connectionValid = false;

    if (message != null)
    {
      MessageBuilder msgBuilder = new MessageBuilder();
      msgBuilder.append(disconnectReason.getClosureMessage());
      msgBuilder.append(": ");
      msgBuilder.append(message);
      cancelAllOperations(new CancelRequest(true, msgBuilder
          .toMessage()));
    }
    else
    {
      cancelAllOperations(new CancelRequest(true, disconnectReason
          .getClosureMessage()));
    }
    finalizeConnectionInternal();

    // If there is a write selector for this connection, then close it.
    Selector selector = writeSelector.get();
    close(selector);

    // See if we should send a notification to the client. If so, then
    // construct and send a notice of disconnection unsolicited
    // response. Note that we cannot send this notification to an LDAPv2
    // client.
    if (sendNotification && (ldapVersion != 2))
    {
      try
      {
        int resultCode;
        switch (disconnectReason)
        {
        case PROTOCOL_ERROR:
          resultCode = LDAPResultCode.PROTOCOL_ERROR;
          break;
        case SERVER_SHUTDOWN:
          resultCode = LDAPResultCode.UNAVAILABLE;
          break;
        case SERVER_ERROR:
          resultCode =
              DirectoryServer.getServerErrorResultCode().getIntValue();
          break;
        case ADMIN_LIMIT_EXCEEDED:
        case IDLE_TIME_LIMIT_EXCEEDED:
        case MAX_REQUEST_SIZE_EXCEEDED:
        case IO_TIMEOUT:
          resultCode = LDAPResultCode.ADMIN_LIMIT_EXCEEDED;
          break;
        case CONNECTION_REJECTED:
          resultCode = LDAPResultCode.CONSTRAINT_VIOLATION;
          break;
        case INVALID_CREDENTIALS:
          resultCode = LDAPResultCode.INVALID_CREDENTIALS;
          break;
        default:
          resultCode = LDAPResultCode.OTHER;
          break;
        }

        Message errMsg;
        if (message == null)
        {
          errMsg =
              INFO_LDAP_CLIENT_GENERIC_NOTICE_OF_DISCONNECTION.get();
        }
        else
        {
          errMsg = message;
        }

        ExtendedResponseProtocolOp notificationOp =
            new ExtendedResponseProtocolOp(resultCode, errMsg, null,
                null, OID_NOTICE_OF_DISCONNECTION, null);

        sendLDAPMessage(new LDAPMessage(0, notificationOp, null));
      }
      catch (Exception e)
      {
        // NYI -- Log a message indicating that we couldn't send the
        // notice of disconnection.
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // Enqueue the connection channels for closing by the finalizer.
    Runnable r = new ConnectionFinalizerJob(asn1Reader, clientChannel);
    connectionHandler.registerConnectionFinalizer(r);

    // NYI -- Deregister the client connection from any server components that
    // might know about it.

    // Log a disconnect message.
    logDisconnect(this, disconnectReason, message);

    try
    {
      PluginConfigManager pluginManager =
          DirectoryServer.getPluginConfigManager();
      pluginManager.invokePostDisconnectPlugins(this, disconnectReason,
          message);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Retrieves the set of operations in progress for this client
   * connection. This list must not be altered by any caller.
   *
   * @return The set of operations in progress for this client
   *         connection.
   */
  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    return operationsInProgress.values();
  }



  /**
   * Retrieves the operation in progress with the specified message ID.
   *
   * @param messageID
   *          The message ID for the operation to retrieve.
   * @return The operation in progress with the specified message ID, or
   *         <CODE>null</CODE> if no such operation could be found.
   */
  @Override
  public Operation getOperationInProgress(int messageID)
  {
    return operationsInProgress.get(messageID);
  }



  /**
   * Adds the provided operation to the set of operations in progress
   * for this client connection.
   *
   * @param operation
   *          The operation to add to the set of operations in progress
   *          for this client connection.
   * @throws DirectoryException
   *           If the operation is not added for some reason (e.g., the
   *           client already has reached the maximum allowed concurrent
   *           requests).
   */
  private void addOperationInProgress(Operation operation)
      throws DirectoryException
  {
    int messageID = operation.getMessageID();

    // We need to grab a lock to ensure that no one else can add
    // operations to the queue while we are performing some preliminary
    // checks.
    try
    {
      synchronized (opsInProgressLock)
      {
        // If we're already in the process of disconnecting the client,
        // then reject the operation.
        if (disconnectRequested)
        {
          Message message = WARN_CLIENT_DISCONNECT_IN_PROGRESS.get();
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
              message);
        }

        // Add the operation to the list of operations in progress for
        // this connection.
        Operation op = operationsInProgress.putIfAbsent(messageID, operation);

        // See if there is already an operation in progress with the
        // same message ID. If so, then we can't allow it.
        if (op != null)
        {
          Message message =
            WARN_LDAP_CLIENT_DUPLICATE_MESSAGE_ID.get(messageID);
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
              message);
        }
      }

      // Try to add the operation to the work queue,
      // or run it synchronously (typically for the administration
      // connector)
      connectionHandler.getQueueingStrategy().enqueueRequest(
          operation);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      operationsInProgress.remove(messageID);
      lastCompletionTime.set(TimeThread.getTime());

      throw de;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
        WARN_LDAP_CLIENT_CANNOT_ENQUEUE.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer
          .getServerErrorResultCode(), message, e);
    }
  }



  /**
   * Removes the provided operation from the set of operations in
   * progress for this client connection. Note that this does not make
   * any attempt to cancel any processing that may already be in
   * progress for the operation.
   *
   * @param messageID
   *          The message ID of the operation to remove from the set of
   *          operations in progress.
   * @return <CODE>true</CODE> if the operation was found and removed
   *         from the set of operations in progress, or
   *         <CODE>false</CODE> if not.
   */
  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    Operation operation = operationsInProgress.remove(messageID);
    if (operation == null)
    {
      return false;
    }

    if (operation.getOperationType() == OperationType.ABANDON)
    {
      if (keepStats
          && (operation.getResultCode() == ResultCode.CANCELED))
      {
        statTracker.updateAbandonedOperation();
      }
    }

    lastCompletionTime.set(TimeThread.getTime());
    return true;
  }



  /**
   * Attempts to cancel the specified operation.
   *
   * @param messageID
   *          The message ID of the operation to cancel.
   * @param cancelRequest
   *          An object providing additional information about how the
   *          cancel should be processed.
   * @return A cancel result that either indicates that the cancel was
   *         successful or provides a reason that it was not.
   */
  @Override
  public CancelResult cancelOperation(int messageID,
      CancelRequest cancelRequest)
  {
    Operation op = operationsInProgress.get(messageID);
    if (op == null)
    {
      // See if the operation is in the list of persistent searches.
      for (PersistentSearch ps : getPersistentSearches())
      {
        if (ps.getMessageID() == messageID)
        {
          // We only need to find the first persistent search
          // associated with the provided message ID. The persistent
          // search will ensure that all other related persistent
          // searches are cancelled.
          CancelResult cancelResult = ps.cancel();

          return cancelResult;
        }
      }

      return new CancelResult(ResultCode.NO_SUCH_OPERATION, null);
    }
    else
    {
      CancelResult cancelResult = op.cancel(cancelRequest);

      return cancelResult;
    }
  }



  /**
   * Attempts to cancel all operations in progress on this connection.
   *
   * @param cancelRequest
   *          An object providing additional information about how the
   *          cancel should be processed.
   */
  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    // Make sure that no one can add any new operations.
    synchronized (opsInProgressLock)
    {
      try
      {
        for (Operation o : operationsInProgress.values())
        {
          try
          {
            o.abort(cancelRequest);

            // TODO: Assume its cancelled?
            if (keepStats)
            {
              statTracker.updateAbandonedOperation();
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }

        if (!(operationsInProgress.isEmpty() && getPersistentSearches()
            .isEmpty()))
        {
          lastCompletionTime.set(TimeThread.getTime());
        }

        operationsInProgress.clear();

        for (PersistentSearch persistentSearch : getPersistentSearches())
        {
          persistentSearch.cancel();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Attempts to cancel all operations in progress on this connection
   * except the operation with the specified message ID.
   *
   * @param cancelRequest
   *          An object providing additional information about how the
   *          cancel should be processed.
   * @param messageID
   *          The message ID of the operation that should not be
   *          canceled.
   */
  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
      int messageID)
  {
    // Make sure that no one can add any new operations.
    synchronized (opsInProgressLock)
    {
      try
      {
        for (int msgID : operationsInProgress.keySet())
        {
          if (msgID == messageID)
          {
            continue;
          }

          Operation o = operationsInProgress.get(msgID);
          if (o != null)
          {
            try
            {
              o.abort(cancelRequest);

              // TODO: Assume its cancelled?
              if (keepStats)
              {
                statTracker.updateAbandonedOperation();
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }
          }

          operationsInProgress.remove(msgID);
          lastCompletionTime.set(TimeThread.getTime());
        }

        for (PersistentSearch persistentSearch : getPersistentSearches())
        {
          if (persistentSearch.getMessageID() == messageID)
          {
            continue;
          }

          persistentSearch.cancel();
          lastCompletionTime.set(TimeThread.getTime());
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Selector getWriteSelector()
  {
    Selector selector = writeSelector.get();
    if (selector == null)
    {
      try
      {
        selector = Selector.open();
        if (!writeSelector.compareAndSet(null, selector))
        {
          selector.close();
          selector = writeSelector.get();
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    return selector;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getMaxBlockedWriteTimeLimit()
  {
    return connectionHandler.getMaxBlockedWriteTimeLimit();
  }



  /**
   * Returns the total number of operations initiated on this
   * connection.
   *
   * @return the total number of operations on this connection
   */
  @Override
  public long getNumberOfOperations()
  {
    return operationsPerformed.get();
  }



  /**
   * Returns the ASN1 reader for this connection.
   *
   * @return the ASN1 reader for this connection
   */
  ASN1ByteChannelReader getASN1Reader()
  {
    return asn1Reader;
  }



  /**
   * Process data read.
   *
   * @return number of bytes read if this connection is still valid
   *         or negative integer to indicate an error otherwise
   */
  int processDataRead()
  {
    if (bindOrStartTLSInProgress.get())
    {
      // We should wait for the bind or startTLS to finish before
      // reading any more data off the socket.
      return 0;
    }

    try
    {
      int result = asn1Reader.processChannelData();
      if (result < 0)
      {
        // The connection has been closed by the client. Disconnect
        // and return.
        disconnect(DisconnectReason.CLIENT_DISCONNECT, false, null);
        return -1;
      }
      return result;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      if (asn1Reader.hasRemainingData() || (e instanceof SSLException))
      {
        // The connection failed, but there was an unread partial message so
        // interpret this as an IO error.
        Message m = ERR_LDAP_CLIENT_IO_ERROR_DURING_READ.get(String
            .valueOf(e));
        disconnect(DisconnectReason.IO_ERROR, true, m);
      }
      else
      {
        // The connection failed and there was no unread data, so interpret this
        // as indicating that the client aborted (reset) the connection. This
        // happens when a client configures closes a connection which has been
        // configured with SO_LINGER set to 0.
        Message m = ERR_LDAP_CLIENT_IO_ERROR_BEFORE_READ.get();
        disconnect(DisconnectReason.CLIENT_DISCONNECT, true, m);
      }

      return -1;
    }
  }



  /**
   * Processes the provided LDAP message read from the client and takes
   * whatever action is appropriate. For most requests, this will
   * include placing the operation in the work queue. Certain requests
   * (in particular, abandons and unbinds) will be processed directly.
   *
   * @param message
   *          The LDAP message to process.
   * @return <CODE>true</CODE> if the appropriate action was taken for
   *         the request, or <CODE>false</CODE> if there was a fatal
   *         error and the client has been disconnected as a result, or
   *         if the client unbound from the server.
   */
  boolean processLDAPMessage(LDAPMessage message)
  {
    if (keepStats)
    {
      statTracker.updateMessageRead(message);
      this.getNetworkGroup().updateMessageRead(message);
    }
    operationsPerformed.getAndIncrement();

    List<Control> opControls = message.getControls();

    // FIXME -- See if there is a bind in progress. If so, then deny
    // most kinds of operations.

    // Figure out what type of operation we're dealing with based on the
    // LDAP message. Abandon and unbind requests will be processed here.
    // All other types of requests will be encapsulated into operations
    // and append into the work queue to be picked up by a worker
    // thread. Any other kinds of LDAP messages (e.g., response
    // messages) are illegal and will result in the connection being
    // terminated.
    try
    {
      if(bindOrStartTLSInProgress.get() ||
          (saslBindInProgress.get() &&
              message.getProtocolOpType() != OP_TYPE_BIND_REQUEST))
      {
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_ENQUEUE_BIND_IN_PROGRESS.get());
      }

      boolean result;
      switch (message.getProtocolOpType())
      {
      case OP_TYPE_ABANDON_REQUEST:
        result = processAbandonRequest(message, opControls);
        return result;
      case OP_TYPE_ADD_REQUEST:
        result = processAddRequest(message, opControls);
        return result;
      case OP_TYPE_BIND_REQUEST:
        bindOrStartTLSInProgress.set(true);
        if(message.getBindRequestProtocolOp().
            getAuthenticationType() == AuthenticationType.SASL)
        {
          saslBindInProgress.set(true);
        }
        result = processBindRequest(message, opControls);
        if(!result)
        {
          bindOrStartTLSInProgress.set(false);
          if(message.getBindRequestProtocolOp().
              getAuthenticationType() == AuthenticationType.SASL)
          {
            saslBindInProgress.set(false);
          }
        }
        return result;
      case OP_TYPE_COMPARE_REQUEST:
        result = processCompareRequest(message, opControls);
        return result;
      case OP_TYPE_DELETE_REQUEST:
        result = processDeleteRequest(message, opControls);
        return result;
      case OP_TYPE_EXTENDED_REQUEST:
        if(message.getExtendedRequestProtocolOp().getOID().equals(
            OID_START_TLS_REQUEST))
        {
          bindOrStartTLSInProgress.set(true);
        }
        result = processExtendedRequest(message, opControls);
        if(!result &&
            message.getExtendedRequestProtocolOp().getOID().equals(
                OID_START_TLS_REQUEST))
        {
          bindOrStartTLSInProgress.set(false);
        }
        return result;
      case OP_TYPE_MODIFY_REQUEST:
        result = processModifyRequest(message, opControls);
        return result;
      case OP_TYPE_MODIFY_DN_REQUEST:
        result = processModifyDNRequest(message, opControls);
        return result;
      case OP_TYPE_SEARCH_REQUEST:
        result = processSearchRequest(message, opControls);
        return result;
      case OP_TYPE_UNBIND_REQUEST:
        result = processUnbindRequest(message, opControls);
        return result;
      default:
        Message msg =
            ERR_LDAP_DISCONNECT_DUE_TO_INVALID_REQUEST_TYPE.get(message
                .getProtocolOpName(), message.getMessageID());
        disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message msg =
          ERR_LDAP_DISCONNECT_DUE_TO_PROCESSING_FAILURE.get(message
              .getProtocolOpName(), message.getMessageID(), String
              .valueOf(e));
      disconnect(DisconnectReason.SERVER_ERROR, true, msg);
      return false;
    }
  }



  /**
   * Processes the provided LDAP message as an abandon request.
   *
   * @param message
   *          The LDAP message containing the abandon request to
   *          process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processAbandonRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    // Create the abandon operation and add it into the work queue.
    AbandonRequestProtocolOp protocolOp =
        message.getAbandonRequestProtocolOp();
    AbandonOperationBasis abandonOp =
        new AbandonOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getIDToAbandon());

    try
    {
      addOperationInProgress(abandonOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // Don't send an error response since abandon operations
      // don't have a response.
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as an add request.
   *
   * @param message
   *          The LDAP message containing the add request to process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processAddRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      AddResponseProtocolOp responseOp =
          new AddResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    // Create the add operation and add it into the work queue.
    AddRequestProtocolOp protocolOp = message.getAddRequestProtocolOp();
    AddOperationBasis addOp =
        new AddOperationBasis(this, nextOperationID.getAndIncrement(),
            message.getMessageID(), controls, protocolOp.getDN(),
            protocolOp.getAttributes());

    try
    {
      addOperationInProgress(addOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      AddResponseProtocolOp responseOp =
          new AddResponseProtocolOp(de.getResultCode().getIntValue(),
              de.getMessageObject(), de.getMatchedDN(), de
                  .getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, addOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a bind request.
   *
   * @param message
   *          The LDAP message containing the bind request to process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processBindRequest(LDAPMessage message,
      List<Control> controls)
  {
    BindRequestProtocolOp protocolOp =
        message.getBindRequestProtocolOp();

    // See if this is an LDAPv2 bind request, and if so whether that
    // should be allowed.
    String versionString;
    switch (ldapVersion = protocolOp.getProtocolVersion())
    {
    case 2:
      versionString = "2";

      if (!connectionHandler.allowLDAPv2())
      {
        BindResponseProtocolOp responseOp =
            new BindResponseProtocolOp(
                LDAPResultCode.PROTOCOL_ERROR,
                ERR_LDAPV2_CLIENTS_NOT_ALLOWED.get());
        sendLDAPMessage(new LDAPMessage(message.getMessageID(),
            responseOp));
        disconnect(DisconnectReason.PROTOCOL_ERROR, false,
            ERR_LDAPV2_CLIENTS_NOT_ALLOWED.get());
        return false;
      }

      if ((controls != null) && (!controls.isEmpty()))
      {
        // LDAPv2 clients aren't allowed to send controls.
        BindResponseProtocolOp responseOp =
            new BindResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
                ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
        sendLDAPMessage(new LDAPMessage(message.getMessageID(),
            responseOp));
        disconnect(DisconnectReason.PROTOCOL_ERROR, false,
            ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
        return false;
      }

      break;
    case 3:
      versionString = "3";
      break;
    default:
      // Unsupported protocol version. RFC4511 states that we MUST send
      // a protocol error back to the client.
      BindResponseProtocolOp responseOp =
          new BindResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAP_UNSUPPORTED_PROTOCOL_VERSION.get(ldapVersion));
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAP_UNSUPPORTED_PROTOCOL_VERSION.get(ldapVersion));
      return false;
    }

    ByteString bindDN = protocolOp.getDN();

    BindOperationBasis bindOp;
    switch (protocolOp.getAuthenticationType())
    {
    case SIMPLE:
      bindOp =
          new BindOperationBasis(this, nextOperationID
              .getAndIncrement(), message.getMessageID(), controls,
              versionString, bindDN, protocolOp.getSimplePassword());
      break;
    case SASL:
      bindOp =
          new BindOperationBasis(this, nextOperationID
              .getAndIncrement(), message.getMessageID(), controls,
              versionString, bindDN, protocolOp.getSASLMechanism(),
              protocolOp.getSASLCredentials());
      break;
    default:
      // This is an invalid authentication type, and therefore a
      // protocol error. As per RFC 2251, a protocol error in a bind
      // request must result in terminating the connection.
      Message msg =
          ERR_LDAP_INVALID_BIND_AUTH_TYPE.get(message.getMessageID(),
              String.valueOf(protocolOp.getAuthenticationType()));
      disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
      return false;
    }

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(bindOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      BindResponseProtocolOp responseOp =
          new BindResponseProtocolOp(de.getResultCode().getIntValue(),
              de.getMessageObject(), de.getMatchedDN(), de
                  .getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, bindOp.getResponseControls()));

      // If it was a protocol error, then terminate the connection.
      if (de.getResultCode() == ResultCode.PROTOCOL_ERROR)
      {
        Message msg =
            ERR_LDAP_DISCONNECT_DUE_TO_BIND_PROTOCOL_ERROR.get(message
                .getMessageID(), de.getMessageObject());
        disconnect(DisconnectReason.PROTOCOL_ERROR, true, msg);
      }
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a compare request.
   *
   * @param message
   *          The LDAP message containing the compare request to
   *          process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processCompareRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      CompareResponseProtocolOp responseOp =
          new CompareResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    CompareRequestProtocolOp protocolOp =
        message.getCompareRequestProtocolOp();
    CompareOperationBasis compareOp =
        new CompareOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getDN(), protocolOp.getAttributeType(),
            protocolOp.getAssertionValue());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(compareOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      CompareResponseProtocolOp responseOp =
          new CompareResponseProtocolOp(de.getResultCode()
              .getIntValue(), de.getMessageObject(), de.getMatchedDN(),
              de.getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, compareOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a delete request.
   *
   * @param message
   *          The LDAP message containing the delete request to process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processDeleteRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      DeleteResponseProtocolOp responseOp =
          new DeleteResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    DeleteRequestProtocolOp protocolOp =
        message.getDeleteRequestProtocolOp();
    DeleteOperationBasis deleteOp =
        new DeleteOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getDN());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(deleteOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      DeleteResponseProtocolOp responseOp =
          new DeleteResponseProtocolOp(
              de.getResultCode().getIntValue(), de.getMessageObject(),
              de.getMatchedDN(), de.getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, deleteOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as an extended request.
   *
   * @param message
   *          The LDAP message containing the extended request to
   *          process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processExtendedRequest(LDAPMessage message,
      List<Control> controls)
  {
    // See if this is an LDAPv2 client. If it is, then they should not
    // be issuing extended requests. We can't send a response that we
    // can be sure they can understand, so we have no choice but to
    // close the connection.
    if (ldapVersion == 2)
    {
      Message msg =
          ERR_LDAPV2_EXTENDED_REQUEST_NOT_ALLOWED.get(
              getConnectionID(), message.getMessageID());
      logError(msg);
      disconnect(DisconnectReason.PROTOCOL_ERROR, false, msg);
      return false;
    }

    // FIXME -- Do we need to handle certain types of request here?
    // -- StartTLS requests
    // -- Cancel requests

    ExtendedRequestProtocolOp protocolOp =
        message.getExtendedRequestProtocolOp();
    ExtendedOperationBasis extendedOp =
        new ExtendedOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getOID(), protocolOp.getValue());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(extendedOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      ExtendedResponseProtocolOp responseOp =
          new ExtendedResponseProtocolOp(de.getResultCode()
              .getIntValue(), de.getMessageObject(), de.getMatchedDN(),
              de.getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, extendedOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a modify request.
   *
   * @param message
   *          The LDAP message containing the modify request to process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processModifyRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      ModifyResponseProtocolOp responseOp =
          new ModifyResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    ModifyRequestProtocolOp protocolOp =
        message.getModifyRequestProtocolOp();
    ModifyOperationBasis modifyOp =
        new ModifyOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getDN(), protocolOp.getModifications());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(modifyOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      ModifyResponseProtocolOp responseOp =
          new ModifyResponseProtocolOp(
              de.getResultCode().getIntValue(), de.getMessageObject(),
              de.getMatchedDN(), de.getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, modifyOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a modify DN request.
   *
   * @param message
   *          The LDAP message containing the modify DN request to
   *          process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processModifyDNRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      ModifyDNResponseProtocolOp responseOp =
          new ModifyDNResponseProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    ModifyDNRequestProtocolOp protocolOp =
        message.getModifyDNRequestProtocolOp();
    ModifyDNOperationBasis modifyDNOp =
        new ModifyDNOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getEntryDN(), protocolOp.getNewRDN(), protocolOp
                .deleteOldRDN(), protocolOp.getNewSuperior());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(modifyDNOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      ModifyDNResponseProtocolOp responseOp =
          new ModifyDNResponseProtocolOp(de.getResultCode()
              .getIntValue(), de.getMessageObject(), de.getMatchedDN(),
              de.getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, modifyDNOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as a search request.
   *
   * @param message
   *          The LDAP message containing the search request to process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processSearchRequest(LDAPMessage message,
      List<Control> controls)
  {
    if ((ldapVersion == 2) && (controls != null)
        && (!controls.isEmpty()))
    {
      // LDAPv2 clients aren't allowed to send controls.
      SearchResultDoneProtocolOp responseOp =
          new SearchResultDoneProtocolOp(LDAPResultCode.PROTOCOL_ERROR,
              ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp));
      disconnect(DisconnectReason.PROTOCOL_ERROR, false,
          ERR_LDAPV2_CONTROLS_NOT_ALLOWED.get());
      return false;
    }

    SearchRequestProtocolOp protocolOp =
        message.getSearchRequestProtocolOp();
    SearchOperationBasis searchOp =
        new SearchOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls,
            protocolOp.getBaseDN(), protocolOp.getScope(), protocolOp
                .getDereferencePolicy(), protocolOp.getSizeLimit(),
            protocolOp.getTimeLimit(), protocolOp.getTypesOnly(),
            protocolOp.getFilter(), protocolOp.getAttributes());

    // Add the operation into the work queue.
    try
    {
      addOperationInProgress(searchOp);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      SearchResultDoneProtocolOp responseOp =
          new SearchResultDoneProtocolOp(de.getResultCode()
              .getIntValue(), de.getMessageObject(), de.getMatchedDN(),
              de.getReferralURLs());

      sendLDAPMessage(new LDAPMessage(message.getMessageID(),
          responseOp, searchOp.getResponseControls()));
    }

    return connectionValid;
  }



  /**
   * Processes the provided LDAP message as an unbind request.
   *
   * @param message
   *          The LDAP message containing the unbind request to process.
   * @param controls
   *          The set of pre-decoded request controls contained in the
   *          message.
   * @return <CODE>true</CODE> if the request was processed
   *         successfully, or <CODE>false</CODE> if not and the
   *         connection has been closed as a result (it is the
   *         responsibility of this method to close the connection).
   */
  private boolean processUnbindRequest(LDAPMessage message,
      List<Control> controls)
  {
    UnbindOperationBasis unbindOp =
        new UnbindOperationBasis(this, nextOperationID
            .getAndIncrement(), message.getMessageID(), controls);

    unbindOp.run();

    // The client connection will never be valid after an unbind.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorSummary()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("connID=\"");
    buffer.append(connectionID);
    buffer.append("\" connectTime=\"");
    buffer.append(getConnectTimeString());
    buffer.append("\" source=\"");
    buffer.append(clientAddress);
    buffer.append(":");
    buffer.append(clientPort);
    buffer.append("\" destination=\"");
    buffer.append(serverAddress);
    buffer.append(":");
    buffer.append(connectionHandler.getListenPort());
    buffer.append("\" ldapVersion=\"");
    buffer.append(ldapVersion);
    buffer.append("\" authDN=\"");

    DN authDN = getAuthenticationInfo().getAuthenticationDN();
    if (authDN != null)
    {
      authDN.toString(buffer);
    }

    buffer.append("\" security=\"");
    if (isSecure())
    {
      buffer.append(activeProvider.getName());
    }
    else
    {
      buffer.append("none");
    }

    buffer.append("\" opsInProgress=\"");
    buffer.append(operationsInProgress.size());
    buffer.append("\"");

    int countPSearch = getPersistentSearches().size();
    if (countPSearch > 0)
    {
      buffer.append(" persistentSearches=\"");
      buffer.append(countPSearch);
      buffer.append("\"");
    }
    return buffer.toString();
  }



  /**
   * Appends a string representation of this client connection to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAP client connection from ");
    buffer.append(clientAddress);
    buffer.append(":");
    buffer.append(clientPort);
    buffer.append(" to ");
    buffer.append(serverAddress);
    buffer.append(":");
    buffer.append(serverPort);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean prepareTLS(MessageBuilder unavailableReason)
  {
    if (isSecure() && "TLS".equals(activeProvider.getName()))
    {
      unavailableReason.append(ERR_LDAP_TLS_EXISTING_SECURITY_PROVIDER
          .get(activeProvider.getName()));
      return false;
    }
    // Make sure that the connection handler allows the use of the
    // StartTLS operation.
    if (!connectionHandler.allowStartTLS())
    {
      unavailableReason.append(ERR_LDAP_TLS_STARTTLS_NOT_ALLOWED.get());
      return false;
    }
    try
    {
      TLSByteChannel tlsByteChannel =
          connectionHandler.getTLSByteChannel(timeoutClientChannel);
      setTLSPendingProvider(tlsByteChannel);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
      unavailableReason.append(ERR_LDAP_TLS_CANNOT_CREATE_TLS_PROVIDER
          .get(stackTraceToSingleLineString(de)));
      return false;
    }
    return true;
  }



  /**
   * Retrieves the length of time in milliseconds that this client
   * connection has been idle. <BR>
   * <BR>
   * Note that the default implementation will always return zero.
   * Subclasses associated with connection handlers should override this
   * method if they wish to provided idle time limit functionality.
   *
   * @return The length of time in milliseconds that this client
   *         connection has been idle.
   */
  @Override
  public long getIdleTime()
  {
    if (operationsInProgress.isEmpty()
        && getPersistentSearches().isEmpty())
    {
      return (TimeThread.getTime() - lastCompletionTime.get());
    }
    else
    {
      // There's at least one operation in progress, so it's not idle.
      return 0L;
    }
  }



  /**
   * Set the connection provider that is not in use yet. Used in TLS
   * negotiation when a clear response is needed before the connection
   * provider is active.
   *
   * @param provider
   *          The provider that needs to be activated.
   */
  public void setTLSPendingProvider(ConnectionSecurityProvider provider)
  {
    tlsPendingProvider = provider;
  }



  /**
   * Set the connection provider that is not in use. Used in SASL
   * negotiation when a clear response is needed before the connection
   * provider is active.
   *
   * @param provider
   *          The provider that needs to be activated.
   */
  public void setSASLPendingProvider(ConnectionSecurityProvider provider)
  {
    saslPendingProvider = provider;
  }



  /**
   * Enable the provider that is inactive.
   */
  private void enableTLS()
  {
    activeProvider = tlsPendingProvider;
    tlsChannel.redirect(tlsPendingProvider);
    tlsPendingProvider = null;
  }



  /**
   * Set the security provider to the specified provider.
   *
   * @param sslProvider
   *          The provider to set the security provider to.
   */
  private void enableSSL(ConnectionSecurityProvider sslProvider)
  {
    activeProvider = sslProvider;
    tlsChannel.redirect(sslProvider);
  }



  /**
   * Enable the SASL provider that is currently inactive or pending.
   */
  private void enableSASL()
  {
    activeProvider = saslPendingProvider;
    saslChannel.redirect(saslPendingProvider);
    saslPendingProvider = null;
  }



  /**
   * Return the certificate chain array associated with a connection.
   *
   * @return The array of certificates associated with a connection.
   */
  public Certificate[] getClientCertificateChain()
  {
    if (activeProvider != null)
    {
      return activeProvider.getClientCertificateChain();
    }
    else
      return new Certificate[0];
  }



  /**
   * Retrieves the TLS redirecting byte channel used in a LDAP client
   * connection.
   *
   * @return The TLS redirecting byte channel.
   */
   @Override
   public ByteChannel getChannel() {
     return this.tlsChannel;
   }



  /**
   * {@inheritDoc}
   */
  @Override
  public int getSSF()
  {
    if (activeProvider != null)
      return activeProvider.getSSF();
    else
      return 0;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void finishBindOrStartTLS()
  {
    if(this.tlsPendingProvider != null)
    {
      enableTLS();
    }

    if (this.saslPendingProvider != null)
    {
      enableSASL();
    }

    super.finishBindOrStartTLS();
  }
}

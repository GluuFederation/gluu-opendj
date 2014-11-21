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
 */
package org.opends.server.protocols.ldap;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.logConnect;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import java.util.LinkedList;
import java.util.List;
import org.opends.messages.Message;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1ByteChannelReader;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDAPException;


/**
 * This class defines an LDAP request handler, which is associated with an LDAP
 * connection handler and is responsible for reading and decoding any requests
 * that LDAP clients may send to the server.  Multiple request handlers may be
 * used in conjunction with a single connection handler for better performance
 * and scalability.
 */
public class LDAPRequestHandler
       extends DirectoryThread
       implements ServerShutdownListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Indicates whether the Directory Server is in the process of shutting down.
  private volatile boolean shutdownRequested = false;

  // The current set of selection keys.
  private volatile SelectionKey[] keys = new SelectionKey[0];

  // The queue that will be used to hold the set of pending connections that
  // need to be registered with the selector.
  // TODO: revisit, see Issue 4202.
  private List<LDAPClientConnection> pendingConnections =
    new LinkedList<LDAPClientConnection>();

  // Lock object for synchronizing access to the pending connections queue.
  private final Object pendingConnectionsLock = new Object();

  // The list of connections ready for request processing.
  private LinkedList<LDAPClientConnection> readyConnections =
    new LinkedList<LDAPClientConnection>();

  // The selector that will be used to monitor the client connections.
  private final Selector selector;

  // The name to use for this request handler.
  private final String handlerName;



  /**
   * Creates a new LDAP request handler that will be associated with the
   * provided connection handler.
   *
   * @param  connectionHandler  The LDAP connection handler with which this
   *                            request handler is associated.
   * @param  requestHandlerID   The integer value that may be used to distingush
   *                            this request handler from others associated with
   *                            the same connection handler.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this request handler.
   */
  public LDAPRequestHandler(LDAPConnectionHandler connectionHandler,
                            int requestHandlerID)
         throws InitializationException
  {
    super("LDAP Request Handler " + requestHandlerID +
          " for connection handler " + connectionHandler.toString());


    handlerName        = getName();

    try
    {
      selector = Selector.open();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_REQHANDLER_OPEN_SELECTOR_FAILED.get(
          handlerName, String.valueOf(e));
      throw new InitializationException(message, e);
    }

    try
    {
      // Check to see if we get an error while trying to perform a select.  If
      // we do, then it's likely CR 6322825 and the server won't be able to
      // handle LDAP requests in its current state.
      selector.selectNow();
    }
    catch (IOException ioe)
    {
      StackTraceElement[] stackElements = ioe.getStackTrace();
      if ((stackElements != null) && (stackElements.length > 0))
      {
        StackTraceElement ste = stackElements[0];
        if (ste.getClassName().equals("sun.nio.ch.DevPollArrayWrapper") &&
            (ste.getMethodName().indexOf("poll") >= 0) &&
            ioe.getMessage().equalsIgnoreCase("Invalid argument"))
        {
          Message message = ERR_LDAP_REQHANDLER_DETECTED_JVM_ISSUE_CR6322825.
              get(String.valueOf(ioe));
          throw new InitializationException(message, ioe);
        }
      }
    }
  }



  /**
   * Operates in a loop, waiting for client requests to arrive and ensuring that
   * they are processed properly.
   */
  @Override
  public void run()
  {
    // Operate in a loop until the server shuts down.  Each time through the
    // loop, check for new requests, then check for new connections.
    while (!shutdownRequested)
    {
      LDAPClientConnection readyConnection = null;
      while ((readyConnection = readyConnections.poll()) != null)
      {
        try
        {
          ASN1ByteChannelReader asn1Reader = readyConnection.getASN1Reader();
          boolean ldapMessageProcessed = false;
          while (true)
          {
            if (asn1Reader.elementAvailable())
            {
              if (!ldapMessageProcessed)
              {
                if (readyConnection.processLDAPMessage(
                    LDAPReader.readMessage(asn1Reader)))
                {
                  ldapMessageProcessed = true;
                }
                else
                {
                  break;
                }
              }
              else
              {
                readyConnections.add(readyConnection);
                break;
              }
            }
            else
            {
              if (readyConnection.processDataRead() <= 0)
              {
                break;
              }
            }
          }
        }
        catch (ASN1Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          readyConnection.disconnect(DisconnectReason.PROTOCOL_ERROR, true,
            e.getMessageObject());
        }
        catch (LDAPException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          readyConnection.disconnect(DisconnectReason.PROTOCOL_ERROR, true,
            e.getMessageObject());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          readyConnection.disconnect(DisconnectReason.PROTOCOL_ERROR, true,
            Message.raw(e.toString()));
        }
      }

      // Check to see if we have any pending connections that need to be
      // registered with the selector.
      List<LDAPClientConnection> tmp = null;
      synchronized (pendingConnectionsLock)
      {
        if (!pendingConnections.isEmpty())
        {
          tmp = pendingConnections;
          pendingConnections = new LinkedList<LDAPClientConnection>();
        }
      }

      if (tmp != null)
      {
        for (LDAPClientConnection c : tmp)
        {
          try
          {
            SocketChannel socketChannel = c.getSocketChannel();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ, c);
            logConnect(c);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            c.disconnect(DisconnectReason.SERVER_ERROR, true,
                ERR_LDAP_REQHANDLER_CANNOT_REGISTER.get(handlerName,
                    String.valueOf(e)));
          }
        }
      }

      // Create a copy of the selection keys which can be used in a
      // thread-safe manner by getClientConnections. This copy is only
      // updated once per loop, so may not be accurate.
      keys = selector.keys().toArray(new SelectionKey[0]);

      int selectedKeys = 0;
      try
      {
        // We timeout every second so that we can refresh the key list.
        selectedKeys = selector.select(1000);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Should we do something else with this?
      }

      if (shutdownRequested)
      {
        // Avoid further processing and disconnect all clients.
        break;
      }

      if (selectedKeys > 0)
      {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext())
        {
          SelectionKey key = iterator.next();

          try
          {
            if (key.isReadable())
            {
              LDAPClientConnection clientConnection = null;

              try
              {
                clientConnection = (LDAPClientConnection) key.attachment();

                try
                {
                  int readResult = clientConnection.processDataRead();
                  if (readResult < 0)
                  {
                    key.cancel();
                  }
                  if (readResult > 0) {
                    readyConnections.add(clientConnection);
                  }
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  // Some other error occurred while we were trying to read data
                  // from the client.
                  // FIXME -- Should we log this?
                  key.cancel();
                  clientConnection.disconnect(DisconnectReason.SERVER_ERROR,
                                              false, null);
                }
              }
              catch (Exception e)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, e);
                }

                // We got some other kind of error.  If nothing else, cancel the
                // key, but if the client connection is available then
                // disconnect it as well.
                key.cancel();

                if (clientConnection != null)
                {
                  clientConnection.disconnect(DisconnectReason.SERVER_ERROR,
                                              false, null);
                }
              }
            }
            else if (! key.isValid())
            {
              key.cancel();
            }
          }
          catch (CancelledKeyException cke)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, cke);
            }

            // This could happen if a connection was closed between the time
            // that select returned and the time that we try to access the
            // associated channel.  If that was the case, we don't need to do
            // anything.
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // This should not happen, and it would have caused our reader
            // thread to die.  Log a severe error.
            Message message = ERR_LDAP_REQHANDLER_UNEXPECTED_SELECT_EXCEPTION.
                get(getName(), getExceptionMessage(e));
            ErrorLogger.logError(message);
          }
          finally
          {
            if (!key.isValid())
            {
              // Help GC - release the connection.
              key.attach(null);
            }

            iterator.remove();
          }
        }
      }
    }

    // Disconnect all active connections.
    SelectionKey[] keyArray = selector.keys().toArray(new SelectionKey[0]);
    for (SelectionKey key : keyArray)
    {
      LDAPClientConnection c = (LDAPClientConnection) key.attachment();

      try
      {
        key.channel().close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      try
      {
        key.cancel();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      try
      {
        c.disconnect(DisconnectReason.SERVER_SHUTDOWN, true,
            ERR_LDAP_REQHANDLER_DEREGISTER_DUE_TO_SHUTDOWN.get());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // Disconnect all pending connections.
    synchronized (pendingConnectionsLock)
    {
      for (LDAPClientConnection c : pendingConnections)
      {
        try
        {
          c.disconnect(DisconnectReason.SERVER_SHUTDOWN, true,
              ERR_LDAP_REQHANDLER_DEREGISTER_DUE_TO_SHUTDOWN.get());
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
  }



  /**
   * Registers the provided client connection with this request
   * handler so that any requests received from that client will be
   * processed.
   *
   * @param clientConnection
   *          The client connection to be registered with this request
   *          handler.
   * @return <CODE>true</CODE> if the client connection was properly
   *         registered with this request handler, or
   *         <CODE>false</CODE> if not.
   */
  public boolean registerClient(LDAPClientConnection clientConnection)
  {
    // FIXME -- Need to check if the maximum client limit has been reached.


    // If the server is in the process of shutting down, then we don't want to
    // accept it.
    if (shutdownRequested)
    {
      clientConnection.disconnect(DisconnectReason.SERVER_SHUTDOWN, true,
           ERR_LDAP_REQHANDLER_REJECT_DUE_TO_SHUTDOWN.get());
      return false;
    }

    // Try to add the new connection to the queue.  If it succeeds, then wake
    // up the selector so it will be picked up right away.  Otherwise,
    // disconnect the client.
    synchronized (pendingConnectionsLock)
    {
      pendingConnections.add(clientConnection);
    }

    selector.wakeup();
    return true;
  }



  /**
   * Retrieves the set of all client connections that are currently registered
   * with this request handler.
   *
   * @return  The set of all client connections that are currently registered
   *          with this request handler.
   */
  public Collection<LDAPClientConnection> getClientConnections()
  {
    ArrayList<LDAPClientConnection> connList =
      new ArrayList<LDAPClientConnection>(keys.length);
    for (SelectionKey key : keys)
    {
      LDAPClientConnection c = (LDAPClientConnection) key.attachment();

      // If the client has disconnected the attachment may be null.
      if (c != null)
      {
        connList.add(c);
      }
    }

    return connList;
  }



  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  public String getShutdownListenerName()
  {
    return handlerName;
  }



  /**
   * Causes this request handler to register itself as a shutdown listener with
   * the Directory Server.  This must be called if the connection handler is
   * shut down without closing all associated connections, otherwise the thread
   * would not be stopped by the server.
   */
  public void registerShutdownListener()
  {
    DirectoryServer.registerShutdownListener(this);
  }



  /**
   * Indicates that the Directory Server has received a request to stop running
   * and that this shutdown listener should take any action necessary to prepare
   * for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  public void processServerShutdown(Message reason)
  {
    shutdownRequested = true;
    selector.wakeup();
  }
}


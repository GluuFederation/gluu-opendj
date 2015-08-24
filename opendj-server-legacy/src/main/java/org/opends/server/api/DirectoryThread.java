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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.backends.task.Task;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a generic thread that should be the superclass
 * for all threads created by the Directory Server.  That is, instead
 * of having a class that "extends Thread", you should make it
 * "extends DirectoryThread".  This provides various value-added
 * capabilities, including:
 * <BR>
 * <UL>
 *   <LI>It helps make sure that all threads have a human-readable
 *       name so they are easier to identify in stack traces.</LI>
 *   <LI>It can capture a stack trace from the time that this thread
 *       was created that could be useful for debugging purposes.</LI>
 *   <LI>It plays an important role in ensuring that log messages
 *       generated as part of the processing for Directory Server
 *       tasks are properly captured and made available as part of
 *       that task.</LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=true,
     mayInvoke=true)
public class DirectoryThread extends Thread
{

  /**
   * Enumeration holding the "logical" (application) thread states, as opposed
   * to the operating system-level java.lang.Thread.State.
   */
  private static enum ThreadState
  {
    /** The current thread is currently not doing any useful work. */
    IDLE(false),
    /** The current thread is currently processing a task, doing useful work. */
    PROCESSING(false),
    /** The current thread is in the process of shutting down. */
    SHUTTING_DOWN(true),
    /**
     * The current thread has stopped running. Equivalent to
     * java.lang.Thread.State.TERMINATED.
     */
    STOPPED(true);

    /**
     * Whether this state implies a shutdown has been initiated or completed.
     */
    private final boolean shutdownInitiated;

    /**
     * Constructs an instance of this enum.
     *
     * @param shutdownInitiated
     *          whether this state implies a shutdown was initiated.
     */
    private ThreadState(boolean shutdownInitiated)
    {
      this.shutdownInitiated = shutdownInitiated;
    }

    /**
     * Returns whether the current thread started the shutdown process.
     *
     * @return true if the current thread started the shutdown process, false
     *         otherwise.
     */
    public boolean isShutdownInitiated()
    {
      return shutdownInitiated;
    }
  }

  /**
   * A factory which can be used by thread pool based services such as
   * {@code Executor}s to dynamically create new
   * {@code DirectoryThread} instances.
   */
  public static final class Factory implements ThreadFactory
  {
    /** The name prefix used for all threads created using this factory. */
    private final String threadNamePrefix;

    /** The ID to use for the next thread created using this factory. */
    private final AtomicInteger nextID = new AtomicInteger();


    /**
     * Creates a new directory thread factory using the provided
     * thread name prefix.
     *
     * @param threadNamePrefix
     *          The name prefix used for all threads created using this factory.
     */
    public Factory(String threadNamePrefix)
    {
      if (threadNamePrefix == null) {
        throw new NullPointerException("Null thread name prefix");
      }

      this.threadNamePrefix = threadNamePrefix;
    }



    /** {@inheritDoc} */
    @Override
    public Thread newThread(Runnable r)
    {
      return new DirectoryThread(r, threadNamePrefix + " "
          + nextID.getAndIncrement());
    }

  }
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The directory thread group that all directory threads will be a
   * member of.
   */
  public static final DirectoryThreadGroup DIRECTORY_THREAD_GROUP =
      new DirectoryThreadGroup();

  /** The stack trace taken at the time that this thread was created. */
  private StackTraceElement[] creationStackTrace;

  /** The task with which this thread is associated, if any. */
  private Task task;

  /** A reference to the thread that was used to create this thread. */
  private Thread parentThread;

  /** The current logical thread's state. */
  private volatile AtomicReference<ThreadState> threadState =
      new AtomicReference<>(ThreadState.IDLE);

  /**
   * A thread group for all directory threads. This implements a
   * custom unhandledException handler that logs the error.
   */
  private static class DirectoryThreadGroup extends ThreadGroup
      implements AlertGenerator
  {
    private final LinkedHashMap<String,String> alerts = new LinkedHashMap<>();

    /** Private constructor for DirectoryThreadGroup. */
    private DirectoryThreadGroup()
    {
      super("Directory Server Thread Group");
      alerts.put(ALERT_TYPE_UNCAUGHT_EXCEPTION,
          ALERT_DESCRIPTION_UNCAUGHT_EXCEPTION);
    }

    /** {@inheritDoc} */
    @Override
    public DN getComponentEntryDN() {
      return DN.NULL_DN;
    }

    /** {@inheritDoc} */
    @Override
    public String getClassName() {
      return DirectoryThread.class.getName();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getAlerts() {
      return alerts;
    }

    /**
     * Provides a means of handling a case in which a thread is about
     * to die because of an unhandled exception.  This method does
     * nothing to try to prevent the death of that thread, but will
     * at least log it so that it can be available for debugging
     * purposes.
     *
     * @param  t  The thread that threw the exception.
     * @param  e  The exception that was thrown but not properly
     *            handled.
     */
    @Override
    public void uncaughtException(Thread t, Throwable e)
    {
      if (e instanceof ThreadDeath)
      {
        // Ignore ThreadDeath errors that can happen when everything is being
        // shutdown.
        return;
      }
      logger.traceException(e);

      LocalizableMessage message = ERR_UNCAUGHT_THREAD_EXCEPTION.get(t.getName(), stackTraceToSingleLineString(e));
      logger.error(message);
      DirectoryServer.sendAlertNotification(this,
          ALERT_TYPE_UNCAUGHT_EXCEPTION, message);
    }
  }

  /**
   * Creates a new instance of this directory thread with the
   * specified name and with the specified target as its run object.
   *
   * @param  target      The target runnable object.
   * @param  threadName  The human-readable name to use for this
   *                     thread for debugging purposes.
   */
  public DirectoryThread(Runnable target, String threadName)
  {
    super(DIRECTORY_THREAD_GROUP, target, threadName);
    init();
  }

  /**
   * Creates a new instance of this directory thread with the
   * specified name.
   *
   * @param  threadName  The human-readable name to use for this
   *                     thread for debugging purposes.
   */
  protected DirectoryThread(String threadName)
  {
    super(DIRECTORY_THREAD_GROUP, threadName);
    init();
  }



  /**
   * Private method used to factorize constructor initialization.
   */
  private void init()
  {
    parentThread       = currentThread();
    creationStackTrace = parentThread.getStackTrace();

    if (parentThread instanceof DirectoryThread)
    {
      task = ((DirectoryThread) parentThread).task;
    }
    else
    {
      task = null;
    }

    if (DirectoryServer.getEnvironmentConfig().forceDaemonThreads())
    {
      setDaemon(true);
    }
  }



  /**
   * Retrieves the stack trace that was captured at the time that this
   * thread was created.
   *
   * @return  The stack trace that was captured at the time that this
   *          thread was created.
   */
  public StackTraceElement[] getCreationStackTrace()
  {
    return creationStackTrace;
  }



  /**
   * Retrieves a reference to the parent thread that created this
   * directory thread.  That parent thread may or may not be a
   * directory thread.
   *
   * @return  A reference to the parent thread that created this
   *          directory thread.
   */
  public Thread getParentThread()
  {
    return parentThread;
  }



  /**
   * Retrieves the task with which this thread is associated.  This
   * will only be available for threads that are used in the process
   * of running a task.
   *
   * @return  The task with which this thread is associated, or
   *          {@code null} if there is none.
   */
  public Task getAssociatedTask()
  {
    return task;
  }



  /**
   * Sets the task with which this thread is associated.  It may be
   * {@code null} to indicate that it is not associated with any task.
   *
   * @param  task  The task with which this thread is associated.
   */
  public void setAssociatedTask(Task task)
  {
    this.task = task;
  }


  /**
   * Retrieves any relevant debug information with which this tread is
   * associated so they can be included in debug messages.
   *
   * @return debug information about this thread as a string.
   */
  public Map<String, String> getDebugProperties()
  {
    Map<String, String> properties = new LinkedHashMap<>();

    properties.put("parentThread", parentThread.getName() +
        "(" + parentThread.getId() + ")");
    properties.put("isDaemon", String.valueOf(isDaemon()));

    return properties;
  }

  /**
   * Returns whether the shutdown process has been initiated on the current
   * thread. It also returns true when the thread is actually terminated.
   * <p>
   * Waiting for the thread to terminate should be done by invoking one of the
   * {@link Thread#join()} methods.
   *
   * @return true if the shutdown process has been initiated on the current
   *         thread, false otherwise.
   */
  public boolean isShutdownInitiated()
  {
    return getThreadState().get().isShutdownInitiated();
  }

  /**
   * Instructs the current thread to initiate the shutdown process. The actual
   * shutdown of the thread is a best effort and is dependent on the
   * implementation of the {@link Thread#run()} method.
   */
  public void initiateShutdown()
  {
    setThreadStateIfNotShuttingDown(ThreadState.SHUTTING_DOWN);
  }

  /**
   * Sets the current thread state to "processing" if the shutdown process was
   * not initiated.
   */
  public void startWork()
  {
    setThreadStateIfNotShuttingDown(ThreadState.PROCESSING);
  }

  /**
   * Sets the current thread state to "idle" if the shutdown process was not
   * initiated.
   */
  public void stopWork()
  {
    setThreadStateIfNotShuttingDown(ThreadState.IDLE);
  }

  /**
   * Sets this thread's current state to the passed in newState if the thread is
   * not already in a shutting down state.
   *
   * @param newState
   *          the new state to set
   */
  private void setThreadStateIfNotShuttingDown(ThreadState newState)
  {
    ThreadState currentState = this.threadState.get();
    while (!currentState.isShutdownInitiated())
    {
      if (this.threadState.compareAndSet(currentState, newState))
      {
        return;
      }
      currentState = this.threadState.get();
    }
  }

  /**
   * Returns the current thread state, possibly returning
   * {@link ThreadState#STOPPED} if the thread is not alive.
   *
   * @return an {@link AtomicReference} to a ThreadState. It can be passed down
   *         as a method call parameter.
   */
  private AtomicReference<ThreadState> getThreadState()
  {
    if (!isAlive())
    {
      this.threadState.set(ThreadState.STOPPED);
    }
    return this.threadState;
  }

}


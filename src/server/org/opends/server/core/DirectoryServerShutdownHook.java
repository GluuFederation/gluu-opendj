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
package org.opends.server.core;


import org.opends.server.api.DirectoryThread;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.CoreMessages.*;
/**
 * This class defines a shutdown hook that will be invoked automatically when
 * the JVM is shutting down.  It may be able to detect certain kinds of shutdown
 * events that are not invoked by the Directory Server itself (e.g., an
 * administrator killing the Java process).
 */
public class DirectoryServerShutdownHook
       extends DirectoryThread
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.DirectoryServerShutdownHook";



  /**
   * Creates a new shutdown hook that will stop the Directory Server when it is
   * determined that the JVM is shutting down.
   */
  public DirectoryServerShutdownHook()
  {
    super("Directory Server Shutdown Hook");

  }



  /**
   * Invokes the shutdown hook to signal the Directory Server to stop running.
   */
  public void run()
  {
    TRACER.debugInfo(
        "Directory Server shutdown hook has been invoked.");

    DirectoryServer.shutDown(CLASS_NAME,
                             ERR_SHUTDOWN_DUE_TO_SHUTDOWN_HOOK.get());
  }
}


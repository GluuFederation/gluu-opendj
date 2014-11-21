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
 *      Portions Copyright 2012-2013 ForgeRock AS
 */

package org.opends.server.tools;
import org.opends.messages.Message;

import java.io.OutputStream;
import java.io.PrintStream;

import org.opends.server.types.NullOutputStream;
import org.opends.server.util.SetupUtils;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;


/**
  * This class is used to stop the Windows service associated with this
  * instance on this machine.
  * This tool allows to stop OpenDS as a Windows service.
  */
public class StopWindowsService
{
  /**
    * The service was successfully stopped.
    */
  public static final int SERVICE_STOP_SUCCESSFUL = 0;
  /**
    * The service could not be found.
    */
  public static final int SERVICE_NOT_FOUND = 1;
  /**
    * The service could not be stopped.
    */
  public static final int SERVICE_STOP_ERROR = 3;

  /**
   * Invokes the net stop on the service corresponding to this server.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int result = stopWindowsService(System.out, System.err);

    System.exit(filterExitCode(result));
  }

  /**
   * Invokes the net stop on the service corresponding to this server, it writes
   * information and error messages in the provided streams.
   * @return <CODE>SERVICE_STOP_SUCCESSFUL</CODE>,
   * <CODE>SERVICE_NOT_FOUND</CODE> or <CODE>SERVICE_STOP_ERROR</CODE>
   * depending on whether the service could be stopped or not.
   * @param  outStream  The stream to write standard output messages.
   * @param  errStream  The stream to write error messages.
   */
  public static int stopWindowsService(OutputStream outStream,
                           OutputStream errStream)
  {
    int returnValue;
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    String serviceName = ConfigureWindowsService.getServiceName();
    if (serviceName == null)
    {

      Message message = ERR_WINDOWS_SERVICE_NOT_FOUND.get();
      err.println(message);
      returnValue = SERVICE_NOT_FOUND;
    }
    else
    {
      String[] cmd;
      if (SetupUtils.hasUAC())
      {
        cmd= new String[] {
            ConfigureWindowsService.getLauncherBinaryFullPath(),
            ConfigureWindowsService.LAUNCHER_OPTION,
            ConfigureWindowsService.getLauncherAdministratorBinaryFullPath(),
            ConfigureWindowsService.LAUNCHER_OPTION,
            "net",
            "stop",
            serviceName
        };
      }
      else
      {
        cmd= new String[] {
            "net",
            "stop",
            serviceName
        };
      }
      /* Check if is a running service */
      try
      {
        int resultCode = Runtime.getRuntime().exec(cmd).waitFor();
        if (resultCode == 0)
        {
          returnValue = SERVICE_STOP_SUCCESSFUL;
        }
        else if (resultCode == 2)
        {
          returnValue = SERVICE_STOP_SUCCESSFUL;
        }
        else
        {
          returnValue = SERVICE_STOP_ERROR;
        }
      }
      catch (Throwable t)
      {

        Message message = ERR_WINDOWS_SERVICE_STOP_ERROR.get();
        err.println(message);
        err.println("Exception:" + t.toString());
        returnValue = SERVICE_STOP_ERROR;
      }
    }
    return returnValue;
  }
}


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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2013 ForgeRock AS
 */
package org.opends.server.tools;



import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.FilePermission;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.OperatingSystem;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a tool that may be used to generate an RC script that
 * can be used to start, stop, and restart the Directory Server, as well as to
 * display its current status.  It is only intended for use on UNIX-based
 * systems that support the use of RC scripts in a location like /etc/init.d.
 */
public class CreateRCScript
{
  /**
   * Parse the command line arguments and create an RC script that can be used
   * to control the server.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int exitCode = main(args, System.out, System.err);
    if (exitCode != 0)
    {
      System.exit(exitCode);
    }
  }



  /**
   * Parse the command line arguments and create an RC script that can be used
   * to control the server.
   *
   * @param  args  The command-line arguments provided to this program.
   * @param  outStream  The output stream to which standard output should be
   *                    directed, or {@code null} if standard output should be
   *                    suppressed.
   * @param  errStream  The output stream to which standard error should be
   *                    directed, or {@code null} if standard error should be
   *                    suppressed.
   *
   * @return  Zero if all processing completed successfully, or nonzero if an
   *          error occurred.
   */
  public static int main(String[] args, OutputStream outStream,
                         OutputStream errStream)
  {
    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

    EmbeddedUtils.initializeForClientUse();

    OperatingSystem operatingSystem = DirectoryServer.getOperatingSystem();
    if (! OperatingSystem.isUNIXBased(operatingSystem))
    {
      err.println(ERR_CREATERC_ONLY_RUNS_ON_UNIX.get().toString());
      return 1;
    }

    File serverRoot = DirectoryServer.getEnvironmentConfig().getServerRoot();
    if (serverRoot == null)
    {
      err.println(ERR_CREATERC_UNABLE_TO_DETERMINE_SERVER_ROOT.get(
                       PROPERTY_SERVER_ROOT, ENV_VAR_INSTALL_ROOT).toString());
      return 1;
    }


    Message description = INFO_CREATERC_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser(CreateRCScript.class.getName(), description, false);

    BooleanArgument showUsage;
    StringArgument  javaArgs;
    StringArgument  javaHome;
    StringArgument  outputFile;
    StringArgument  userName;

    try
    {
      outputFile = new StringArgument("outputfile", 'f', "outputFile", true,
                                      false, true, INFO_PATH_PLACEHOLDER.get(),
                                      null, null,
                                      INFO_CREATERC_OUTFILE_DESCRIPTION.get());
      argParser.addArgument(outputFile);


      userName = new StringArgument("username", 'u', "userName", false, false,
                                    true, INFO_USER_NAME_PLACEHOLDER.get(),
                                    null, null,
                                    INFO_CREATERC_USER_DESCRIPTION.get());
      argParser.addArgument(userName);


      javaHome = new StringArgument("javahome", 'j', "javaHome", false, false,
                                    true, INFO_PATH_PLACEHOLDER.get(), null,
                                    null,
                                    INFO_CREATERC_JAVA_HOME_DESCRIPTION.get());
      argParser.addArgument(javaHome);


      javaArgs = new StringArgument("javaargs", 'J', "javaArgs", false, false,
                                    true, INFO_ARGS_PLACEHOLDER.get(), null,
                                    null,
                                    INFO_CREATERC_JAVA_ARGS_DESCRIPTION.get());
      argParser.addArgument(javaArgs);


      showUsage = new BooleanArgument("help", 'H', "help",
                                      INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      err.println(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      err.println(ERR_ERROR_PARSING_ARGS.get(ae.getMessage()).toString());
      return 1;
    }

    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    // Determine the path to the Java installation that should be used.
    String javaHomeDir;
    if (javaHome.isPresent())
    {
      File f = new File(javaHome.getValue());
      if (! (f.exists() && f.isDirectory()))
      {
        err.println(ERR_CREATERC_JAVA_HOME_DOESNT_EXIST.get(
                         javaHome.getValue()).toString());
        return 1;
      }

      javaHomeDir = f.getAbsolutePath();
    }
    else
    {
      javaHomeDir = System.getenv(SetupUtils.OPENDJ_JAVA_HOME);
    }


    String suString = "";
    String EscQuote1 = "\"";
    String EscQuote2 = "";

    if (userName.isPresent())
    {
      String suCmd = "/bin/su";
      File f = new File(suCmd);
      if (! f.exists())
      {
        suCmd = "/usr/bin/su";
        File f2 = new File(suCmd);
        if (! f2.exists())
        {
          // Default to /bin/su anyway
          suCmd = "/bin/su";
        }
      }
      suString = suCmd + " " + userName.getValue() + " -c ";
      EscQuote1 = "";
      EscQuote2 = "\"";
    }


    // Start writing the output file.
    try
    {
      File f = new File(outputFile.getValue());
      PrintWriter w = new PrintWriter(f);

      w.println("#!/bin/sh");
      w.println("#");

      for (String headerLine : CDDL_HEADER_LINES)
      {
        w.println("# " + headerLine);
      }

      w.println("# chkconfig: 345 95 5");
      w.println("# description: Control the " +
          SHORT_NAME + " Directory Server");
      w.println();
      w.println();

      w.println("# Set the path to the " + SHORT_NAME + " instance to manage");
      w.println("INSTALL_ROOT=\"" + serverRoot.getAbsolutePath() + "\"");
      w.println("export INSTALL_ROOT");
      w.println();
      w.println("cd ${INSTALL_ROOT}");
      w.println();

      if (javaHomeDir != null)
      {
        w.println("# Specify the path to the Java installation to use");
        w.println("OPENDJ_JAVA_HOME=\"" + javaHomeDir + "\"");
        w.println("export OPENDJ_JAVA_HOME");
        w.println();
      }

      if (javaArgs.isPresent())
      {
        w.println("# Specify arguments that should be provided to the JVM");
        w.println("OPENDJ_JAVA_ARGS=\"" + javaArgs.getValue() + "\"");
        w.println("export OPENDJ_JAVA_ARGS");
        w.println();
      }

      w.println("# Determine what action should be performed on the server");
      w.println("case \"${1}\" in");
      w.println("start)");
      w.println("  " + suString + "\"${INSTALL_ROOT}/bin/start-ds"+
          EscQuote1 + " --quiet" + EscQuote2);
      w.println("  exit ${?}");
      w.println("  ;;");
      w.println("stop)");
      w.println("  " + suString + "\"${INSTALL_ROOT}/bin/stop-ds"+
          EscQuote1 + " --quiet" + EscQuote2);
      w.println("  exit ${?}");
      w.println("  ;;");
      w.println("restart)");
      w.println("  " + suString + "\"${INSTALL_ROOT}/bin/stop-ds"+
          EscQuote1 + " --restart --quiet" + EscQuote2);
      w.println("  exit ${?}");
      w.println("  ;;");
      w.println("*)");
      w.println("  echo \"Usage:  $0 { start | stop | restart }\"");
      w.println("  exit 1");
      w.println("  ;;");
      w.println("esac");
      w.println();

      w.close();

      if (FilePermission.canSetPermissions())
      {
        FilePermission.setPermissions(f, FilePermission.decodeUNIXMode("755"));
      }
    }
    catch (Exception e)
    {
      err.println(ERR_CREATERC_CANNOT_WRITE.get(
                       getExceptionMessage(e)).toString());
      return 1;
    }


    // If we've gotten here, then everything has completed successfully.
    return 0;
  }
}


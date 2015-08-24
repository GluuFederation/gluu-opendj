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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.FilePermission;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.SetupUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.util.OperatingSystem;

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
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    if (! OperatingSystem.isUnixBased())
    {
      printWrappedText(err, ERR_CREATERC_ONLY_RUNS_ON_UNIX.get());
      return 1;
    }

    LocalizableMessage description = INFO_CREATERC_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser(CreateRCScript.class.getName(), description, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_CREATE_RC_SCRIPT.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

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


      showUsage = CommonArguments.getShowUsage();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }

    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    EmbeddedUtils.initializeForClientUse();
    File serverRoot = DirectoryServer.getEnvironmentConfig().getServerRoot();
    if (serverRoot == null)
    {
      printWrappedText(
          err, ERR_CREATERC_UNABLE_TO_DETERMINE_SERVER_ROOT.get(PROPERTY_SERVER_ROOT, ENV_VAR_INSTALL_ROOT));
      return 1;
    }

    // Determine the path to the Java installation that should be used.
    String javaHomeDir;
    if (javaHome.isPresent())
    {
      File f = new File(javaHome.getValue());
      if (!f.exists() || !f.isDirectory())
      {
        printWrappedText(err, ERR_CREATERC_JAVA_HOME_DOESNT_EXIST.get(javaHome.getValue()));
        return 1;
      }

      javaHomeDir = f.getAbsolutePath();
    }
    else
    {
      javaHomeDir = System.getenv(SetupUtils.OPENDJ_JAVA_HOME);
    }

    boolean isFreeBSD = OperatingSystem.getOperatingSystem() == OperatingSystem.FREEBSD;

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
      String asMeFlag = isFreeBSD ? " -m " : " ";
      suString = suCmd + asMeFlag + userName.getValue() + " -c ";
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

      if (isFreeBSD) {
        w.println("# PROVIDE: opendj");
        w.println("# REQUIRE: LOGIN");
        w.println("# KEYWORD: shutdown");
        w.println();
        w.println(". /etc/rc.subr");
        w.println("name=\"opendj\"");
        w.println("rcvar=opendj_enable");
        w.println();
        w.println("start_cmd=\"${name}_start\"");
        w.println("stop_cmd=\"${name}_stop\"");
        w.println("restart_cmd=\"${name}_restart\"");
        w.println("status_cmd=\"${name}_status\"");
        w.println();
        w.println("load_rc_config ${name}");
        w.println(": ${opendj_enable:=no}");
        w.println(": ${opendj_msg=\"OpenDJ not started.\"}");
      } else {
        w.println("# chkconfig: 345 95 5");
        w.println("# description: Control the " + SHORT_NAME + " Directory Server");
      }
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

      if (isFreeBSD) {
        w.println("if [ \"x${opendj_java_home}\" != \"x\" ]; then");
        w.println("  OPENDJ_JAVA_HOME=\"${opendj_java_home}\"");
        w.println("  export OPENDJ_JAVA_HOME");
        w.println("fi");
        w.println("if [ \"x${opendj_java_args}\" != \"x\" ]; then");
        w.println("  OPENDJ_JAVA_ARGS=\"${opendj_java_args}\"");
        w.println("  export OPENDJ_JAVA_ARGS");
        w.println("fi");
        w.println("if [ \"x${opendj_install_root}\" != \"x\" ]; then");
        w.println("  INSTALL_ROOT=\"${opendj_install_root}\"");
        w.println("  export INSTALL_ROOT");
        w.println("fi");
        w.println();
        w.println("opendj_chdir=\"${INSTALL_ROOT}\"");
        w.println("extra_commands=\"status\"");
        w.println();
        w.println("opendj_start()");
        w.println("{");
        w.println("  if [ -n \"$rc_quiet\" ]; then");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/start-ds" + EscQuote2);
        w.println("  else");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/start-ds" + EscQuote1 + " --quiet" + EscQuote2);
        w.println("  fi");
        w.println("}");
        w.println("opendj_stop()");
        w.println("{");
        w.println("  if [ -n \"$rc_quiet\" ]; then");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/stop-ds" + EscQuote2);
        w.println("  else");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/stop-ds" + EscQuote1 + " --quiet" + EscQuote2);
        w.println("  fi");
        w.println("}");
        w.println("opendj_restart()");
        w.println("{");
        w.println("  if [ -n \"$rc_quiet\" ]; then");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/stop-ds" + EscQuote1 + " --restart" + EscQuote2);
        w.println("  else");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/stop-ds" + EscQuote1 + " --restart --quiet" + EscQuote2);
        w.println("  fi");
        w.println("}");
        w.println("opendj_status()");
        w.println("{");
        w.println("    " + suString + "\"${INSTALL_ROOT}/bin/status" + EscQuote2);
        w.println("}");
        w.println();
        w.println("pidfile=\"${INSTALL_ROOT}/logs/server.pid\"");
        w.println();
        w.println("run_rc_command \"$1\"");
      } else {
        w.println("# Determine what action should be performed on the server");
        w.println("case \"${1}\" in");
        w.println("start)");
        w.println("  " + suString + "\"${INSTALL_ROOT}/bin/start-ds" + EscQuote1 + " --quiet" + EscQuote2);
        w.println("  exit ${?}");
        w.println("  ;;");
        w.println("stop)");
        w.println("  " + suString + "\"${INSTALL_ROOT}/bin/stop-ds" + EscQuote1 + " --quiet" + EscQuote2);
        w.println("  exit ${?}");
        w.println("  ;;");
        w.println("restart)");
        w.println("  " + suString + "\"${INSTALL_ROOT}/bin/stop-ds" + EscQuote1 + " --restart --quiet" + EscQuote2);
        w.println("  exit ${?}");
        w.println("  ;;");
        w.println("*)");
        w.println("  echo \"Usage:  $0 { start | stop | restart }\"");
        w.println("  exit 1");
        w.println("  ;;");
        w.println("esac");
        w.println();
      }
      w.close();

      FilePermission.setPermissions(f, FilePermission.decodeUNIXMode("755"));
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_CREATERC_CANNOT_WRITE.get(getExceptionMessage(e)));
      return 1;
    }

    // If we've gotten here, then everything has completed successfully.
    return 0;
  }
}


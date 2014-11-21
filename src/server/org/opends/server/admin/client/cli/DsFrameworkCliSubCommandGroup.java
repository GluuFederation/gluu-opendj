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
 package org.opends.server.admin.client.cli;

import java.io.OutputStream;
import java.util.Set;

import org.opends.admin.ads.ADSContextException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.SubCommand;

/**
 * This Interface defines method that a group of subcommand shoud implement.
 */
public interface DsFrameworkCliSubCommandGroup
{

  /**
   * Initialize subcommand related to server group management.
   *
   * @param argParser
   *          The parser in which we should be registered.
   * @param verboseArg
   *          The verbose Argument.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  public void initializeCliGroup(DsFrameworkCliParser argParser,
      BooleanArgument verboseArg) throws ArgumentException;

  /**
   * Indicates if the provided suncommand is part of this group.
   *
   * @param subCmd
   *          The actual subcommand with input parameter.
   * @return True if the provided suncommand is part of this group.
   */
  public boolean isSubCommand(SubCommand subCmd);

  /**
   * Handle the subcommand.
   * @param subCmd
   *          The actual subcommand with input parameter
   * @param  outStream         The output stream to use for standard output.
   * @param  errStream         The output stream to use for standard error.
   * @return the return code
   * @throws ADSContextException
   *           If there is a problem with when trying to perform the
   *           operation.
   * @throws ArgumentException
   *           If there is a problem with any of the parameters used
   *           to execute this subcommand.
   */
  public DsFrameworkCliReturnCode performSubCommand(SubCommand subCmd,
      OutputStream outStream, OutputStream errStream)
      throws ADSContextException, ArgumentException;

  /**
   * Get the subcommands list.
   * @return the subcommand list.
   */
  public Set<SubCommand> getSubCommands();

  /**
   * Indicates whether this subcommand group should be hidden from the usage
   * information.
   *
   * @return <CODE>true</CODE> if this subcommand group should be hidden
   *         from the usage information, or <CODE>false</CODE> if
   *         not.
   */
  public boolean isHidden();

  /**
   * Indicates subcommand group name.
   *
   * @return the subcommand group name
   */
  public String getGroupName();

}

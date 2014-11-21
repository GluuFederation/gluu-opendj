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
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.util.args;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.server.core.DirectoryServer;
import org.opends.server.util.SetupUtils;



/**
 * This class defines a variant of the argument parser that can be used with
 * applications that use subcommands to customize their behavior and that have a
 * different set of options per subcommand (e.g, "cvs checkout" takes different
 * options than "cvs commit").  This parser also has the ability to use global
 * options that will always be applicable regardless of the subcommand in
 * addition to the subcommand-specific arguments.  There must not be any
 * conflicts between the global options and the option for any subcommand, but
 * it is allowed to re-use subcommand-specific options for different purposes
 * between different subcommands.
 */
public class SubCommandArgumentParser extends ArgumentParser
{
  // The argument that will be used to trigger the display of usage information.
  private Argument usageArgument;

  // The arguments that will be used to trigger the display of usage
  // information for groups of sub-commands.
  private final Map<Argument, Collection<SubCommand>> usageGroupArguments;

  // The set of unnamed trailing arguments that were provided for this parser.
  private ArrayList<String> trailingArguments;

  // Indicates whether subcommand and long argument names should be treated in a
  // case-sensitive manner.
  private final boolean longArgumentsCaseSensitive;

  // Indicates whether the usage information has been displayed.
  private boolean usageOrVersionDisplayed;

  // The set of global arguments defined for this parser, referenced by short
  // ID.
  private final HashMap<Character,Argument> globalShortIDMap;

  //  The set of global arguments defined for this parser, referenced by
  // argument name.
  private final HashMap<String,Argument> globalArgumentMap;

  //  The set of global arguments defined for this parser, referenced by long
  // ID.
  private final HashMap<String,Argument> globalLongIDMap;

  // The set of subcommands defined for this parser, referenced by subcommand
  // name.
  private final SortedMap<String,SubCommand> subCommands;

  // The total set of global arguments defined for this parser.
  private final LinkedList<Argument> globalArgumentList;

  // The output stream to which usage information should be printed.
  private OutputStream usageOutputStream;

  // The fully-qualified name of the Java class that should be invoked to launch
  // the program with which this argument parser is associated.
  private final String mainClassName;

  // A human-readable description for the tool, which will be included when
  // displaying usage information.
  private final Message toolDescription;

  // The raw set of command-line arguments that were provided.
  private String[] rawArguments;

  // The subcommand requested by the user as part of the command-line arguments.
  private SubCommand subCommand;

  //Indicates whether the version argument was provided.
  private boolean versionPresent;

  private final static String INDENT = "    ";
  private final static int MAX_LENGTH = SetupUtils.isWindows() ? 79 : 80;


  /**
   * Creates a new instance of this subcommand argument parser with no
   * arguments.
   *
   * @param  mainClassName               The fully-qualified name of the Java
   *                                     class that should be invoked to launch
   *                                     the program with which this argument
   *                                     parser is associated.
   * @param  toolDescription             A human-readable description for the
   *                                     tool, which will be included when
   *                                     displaying usage information.
   * @param  longArgumentsCaseSensitive  Indicates whether subcommand and long
   *                                     argument names should be treated in a
   *                                     case-sensitive manner.
   */
  public SubCommandArgumentParser(String mainClassName, Message toolDescription,
                                  boolean longArgumentsCaseSensitive)
  {
    super(mainClassName, toolDescription, longArgumentsCaseSensitive);
    this.mainClassName              = mainClassName;
    this.toolDescription            = toolDescription;
    this.longArgumentsCaseSensitive = longArgumentsCaseSensitive;

    trailingArguments   = new ArrayList<String>();
    globalArgumentList  = new LinkedList<Argument>();
    globalArgumentMap   = new HashMap<String,Argument>();
    globalShortIDMap    = new HashMap<Character,Argument>();
    globalLongIDMap     = new HashMap<String,Argument>();
    usageGroupArguments = new HashMap<Argument, Collection<SubCommand>>();
    subCommands         = new TreeMap<String,SubCommand>();
    usageOrVersionDisplayed = false;
    rawArguments        = null;
    subCommand          = null;
    usageArgument       = null;
    usageOutputStream   = null;
  }



  /**
   * Retrieves the fully-qualified name of the Java class that should be invoked
   * to launch the program with which this argument parser is associated.
   *
   * @return  The fully-qualified name of the Java class that should be invoked
   *          to launch the program with which this argument parser is
   *          associated.
   */
  @Override
  public String getMainClassName()
  {
    return mainClassName;
  }



  /**
   * Retrieves a human-readable description for this tool, which should be
   * included at the top of the command-line usage information.
   *
   * @return  A human-readable description for this tool, or {@code null} if
   *          none is available.
   */
  @Override
  public Message getToolDescription()
  {
    return toolDescription;
  }



  /**
   * Indicates whether subcommand names and long argument strings should be
   * treated in a case-sensitive manner.
   *
   * @return  <CODE>true</CODE> if subcommand names and long argument strings
   *          should be treated in a case-sensitive manner, or
   *          <CODE>false</CODE> if they should not.
   */
  public boolean longArgumentsCaseSensitive()
  {
    return longArgumentsCaseSensitive;
  }



  /**
   * Retrieves the list of all global arguments that have been defined for this
   * argument parser.
   *
   * @return  The list of all global arguments that have been defined for this
   *          argument parser.
   */
  public LinkedList<Argument> getGlobalArgumentList()
  {
    return globalArgumentList;
  }



  /**
   * Indicates whether this argument parser contains a global argument with the
   * specified name.
   *
   * @param  argumentName  The name for which to make the determination.
   *
   * @return  <CODE>true</CODE> if a global argument exists with the specified
   *          name, or <CODE>false</CODE> if not.
   */
  public boolean hasGlobalArgument(String argumentName)
  {
    return globalArgumentMap.containsKey(argumentName);
  }



  /**
   * Retrieves the global argument with the specified name.
   *
   * @param  name  The name of the global argument to retrieve.
   *
   * @return  The global argument with the specified name, or <CODE>null</CODE>
   *          if there is no such argument.
   */
  public Argument getGlobalArgument(String name)
  {
    return globalArgumentMap.get(name);
  }



  /**
   * Retrieves the set of global arguments mapped by the short identifier that
   * may be used to reference them.  Note that arguments that do not have a
   * short identifier will not be present in this list.
   *
   * @return  The set of global arguments mapped by the short identifier that
   *          may be used to reference them.
   */
  public HashMap<Character,Argument> getGlobalArgumentsByShortID()
  {
    return globalShortIDMap;
  }



  /**
   * Indicates whether this argument parser has a global argument with the
   * specified short ID.
   *
   * @param  shortID  The short ID character for which to make the
   *                  determination.
   *
   * @return  <CODE>true</CODE> if a global argument exists with the specified
   *          short ID, or <CODE>false</CODE> if not.
   */
  public boolean hasGlobalArgumentWithShortID(Character shortID)
  {
    return globalShortIDMap.containsKey(shortID);
  }



  /**
   * Retrieves the global argument with the specified short identifier.
   *
   * @param  shortID  The short identifier for the global argument to retrieve.
   *
   * @return  The global argument with the specified short identifier, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getGlobalArgumentForShortID(Character shortID)
  {
    return globalShortIDMap.get(shortID);
  }



  /**
   * Retrieves the set of global arguments mapped by the long identifier that
   * may be used to reference them.  Note that arguments that do not have a long
   * identifier will not be present in this list.
   *
   * @return  The set of global arguments mapped by the long identifier that may
   *          be used to reference them.
   */
  public HashMap<String,Argument> getGlobalArgumentsByLongID()
  {
    return globalLongIDMap;
  }



  /**
   * Indicates whether this argument parser has a global argument with the
   * specified long ID.
   *
   * @param  longID  The long ID string for which to make the determination.
   *
   * @return  <CODE>true</CODE> if a global argument exists with the specified
   *          long ID, or <CODE>false</CODE> if not.
   */
  public boolean hasGlobalArgumentWithLongID(String longID)
  {
    return globalLongIDMap.containsKey(longID);
  }



  /**
   * Retrieves the global argument with the specified long identifier.
   *
   * @param  longID  The long identifier for the global argument to retrieve.
   *
   * @return  The global argument with the specified long identifier, or
   *          <CODE>null</CODE> if there is no such argument.
   */
  public Argument getGlobalArgumentForLongID(String longID)
  {
    return globalLongIDMap.get(longID);
  }



  /**
   * Retrieves the set of subcommands defined for this argument parser,
   * referenced by subcommand name.
   *
   * @return  The set of subcommands defined for this argument parser,
   *          referenced by subcommand name.
   */
  public SortedMap<String,SubCommand> getSubCommands()
  {
    return subCommands;
  }



  /**
   * Indicates whether this argument parser has a subcommand with the specified
   * name.
   *
   * @param  name  The subcommand name for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this argument parser has a subcommand with
   *          the specified name, or <CODE>false</CODE> if it does not.
   */
  public boolean hasSubCommand(String name)
  {
    return subCommands.containsKey(name);
  }



  /**
   * Retrieves the subcommand with the specified name.
   *
   * @param  name  The name of the subcommand to retrieve.
   *
   * @return  The subcommand with the specified name, or <CODE>null</CODE> if no
   *          such subcommand is defined.
   */
  public SubCommand getSubCommand(String name)
  {
    return subCommands.get(name);
  }



  /**
   * Retrieves the subcommand that was selected in the set of command-line
   * arguments.
   *
   * @return  The subcommand that was selected in the set of command-line
   *          arguments, or <CODE>null</CODE> if none was selected.
   */
  public SubCommand getSubCommand()
  {
    return subCommand;
  }



  /**
   * Retrieves the raw set of arguments that were provided.
   *
   * @return  The raw set of arguments that were provided, or <CODE>null</CODE>
   *          if the argument list has not yet been parsed.
   */
  @Override
  public String[] getRawArguments()
  {
    return rawArguments;
  }



  /**
   * Adds the provided argument to the set of global arguments handled by this
   * parser.
   *
   * @param  argument  The argument to be added.
   *
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             global or subcommand argument that has already
   *                             been defined.
   */
  public void addGlobalArgument(Argument argument)
         throws ArgumentException
  {
    addGlobalArgument(argument, null);
  }


  /**
   * Adds the provided argument to the set of global arguments handled by this
   * parser.
   *
   * @param  argument  The argument to be added.
   * @param  group     The argument group to which the argument belongs.
   * @throws  ArgumentException  If the provided argument conflicts with another
   *                             global or subcommand argument that has already
   *                             been defined.
   */
  public void addGlobalArgument(Argument argument, ArgumentGroup group)
         throws ArgumentException
  {

    String argumentName = argument.getName();
    if (globalArgumentMap.containsKey(argumentName))
    {
      Message message =
          ERR_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_NAME.get(argumentName);
      throw new ArgumentException(message);
    }
    for (SubCommand s : subCommands.values())
    {
      if (s.getArgumentForName(argumentName) != null)
      {
        Message message = ERR_SUBCMDPARSER_GLOBAL_ARG_NAME_SUBCMD_CONFLICT.get(
            argumentName, s.getName());
        throw new ArgumentException(message);
      }
    }


    Character shortID = argument.getShortIdentifier();
    if (shortID != null)
    {
      if (globalShortIDMap.containsKey(shortID))
      {
        String name = globalShortIDMap.get(shortID).getName();

        Message message = ERR_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_SHORT_ID.get(
            String.valueOf(shortID), argumentName, name);
        throw new ArgumentException(message);
      }

      for (SubCommand s : subCommands.values())
      {
        if (s.getArgument(shortID) != null)
        {
          String cmdName = s.getName();
          String name    = s.getArgument(shortID).getName();

          Message message = ERR_SUBCMDPARSER_GLOBAL_ARG_SHORT_ID_CONFLICT.get(
              String.valueOf(shortID), argumentName, name, cmdName);
          throw new ArgumentException(message);
        }
      }
    }


    String longID = argument.getLongIdentifier();
    if (longID != null)
    {
      if (! longArgumentsCaseSensitive)
      {
        longID = toLowerCase(longID);
      }

      if (globalLongIDMap.containsKey(longID))
      {
        String name = globalLongIDMap.get(longID).getName();

        Message message = ERR_SUBCMDPARSER_DUPLICATE_GLOBAL_ARG_LONG_ID.get(
            argument.getLongIdentifier(), argumentName, name);
        throw new ArgumentException(message);
      }

      for (SubCommand s : subCommands.values())
      {
        if (s.getArgument(longID) != null)
        {
          String cmdName = s.getName();
          String name    = s.getArgument(longID).getName();

          Message message = ERR_SUBCMDPARSER_GLOBAL_ARG_LONG_ID_CONFLICT.get(
              argument.getLongIdentifier(), argumentName, name, cmdName);
          throw new ArgumentException(message);
        }
      }
    }


    if (shortID != null)
    {
      globalShortIDMap.put(shortID, argument);
    }

    if (longID != null)
    {
      globalLongIDMap.put(longID, argument);
    }

    globalArgumentList.add(argument);

    if (group == null) {
      group = getStandardGroup(argument);
    }
    group.addArgument(argument);
    argumentGroups.add(group);
  }

  /**
   * Removes the provided argument from the set of global arguments handled by
   * this parser.
   *
   * @param  argument  The argument to be removed.
   */
  protected void removeGlobalArgument(Argument argument)
  {
    String argumentName = argument.getName();
    globalArgumentMap.remove(argumentName);

    Character shortID = argument.getShortIdentifier();
    if (shortID != null)
    {
      globalShortIDMap.remove(shortID);
    }

    String longID = argument.getLongIdentifier();
    if (longID != null)
    {
      if (! longArgumentsCaseSensitive)
      {
        longID = toLowerCase(longID);
      }

      globalLongIDMap.remove(longID);
    }

    globalArgumentList.remove(argument);
  }


  /**
   * Sets the provided argument as one which will automatically
   * trigger the output of full usage information if it is provided on
   * the command line and no further argument validation will be
   * performed.
   * <p>
   * If sub-command groups are defined using the
   * {@link #setUsageGroupArgument(Argument, Collection)} method, then
   * this usage argument, when specified, will result in usage
   * information being displayed which does not include information on
   * sub-commands.
   * <p>
   * Note that the caller will still need to add this argument to the
   * parser with the {@link #addGlobalArgument(Argument)} method, and
   * the argument should not be required and should not take a value.
   * Also, the caller will still need to check for the presence of the
   * usage argument after calling {@link #parseArguments(String[])} to
   * know that no further processing will be required.
   *
   * @param argument
   *          The argument whose presence should automatically trigger
   *          the display of full usage information.
   * @param outputStream
   *          The output stream to which the usage information should
   *          be written.
   */
  @Override
  public void setUsageArgument(Argument argument, OutputStream outputStream) {
    usageArgument = argument;
    usageOutputStream = outputStream;

    usageGroupArguments.put(argument, Collections.<SubCommand>emptySet());
  }



  /**
   * Sets the provided argument as one which will automatically
   * trigger the output of partial usage information if it is provided
   * on the command line and no further argument validation will be
   * performed.
   * <p>
   * Partial usage information will include a usage synopsis, a
   * summary of each of the sub-commands listed in the provided
   * sub-commands collection, and a summary of the global options.
   * <p>
   * Note that the caller will still need to add this argument to the
   * parser with the {@link #addGlobalArgument(Argument)} method, and
   * the argument should not be required and should not take a value.
   * Also, the caller will still need to check for the presence of the
   * usage argument after calling {@link #parseArguments(String[])} to
   * know that no further processing will be required.
   *
   * @param argument
   *          The argument whose presence should automatically trigger
   *          the display of partial usage information.
   * @param subCommands
   *          The list of sub-commands which should have their usage
   *          displayed.
   */
  public void setUsageGroupArgument(Argument argument,
      Collection<SubCommand> subCommands) {
    usageGroupArguments.put(argument, subCommands);
  }


  /**
   * Parses the provided set of arguments and updates the information associated
   * with this parser accordingly.
   *
   * @param  rawArguments  The raw set of arguments to parse.
   *
   * @throws  ArgumentException  If a problem was encountered while parsing the
   *                             provided arguments.
   */
  @Override
  public void parseArguments(String[] rawArguments)
         throws ArgumentException
  {
    parseArguments(rawArguments, null);
  }



  /**
   * Parses the provided set of arguments and updates the information associated
   * with this parser accordingly.  Default values for unspecified arguments
   * may be read from the specified properties file.
   *
   * @param  rawArguments           The set of raw arguments to parse.
   * @param  propertiesFile         The path to the properties file to use to
   *                                obtain default values for unspecified
   *                                properties.
   * @param  requirePropertiesFile  Indicates whether the parsing should fail if
   *                                the provided properties file does not exist
   *                                or is not accessible.
   *
   * @throws  ArgumentException  If a problem was encountered while parsing the
   *                             provided arguments or interacting with the
   *                             properties file.
   */
  @Override
  public void parseArguments(String[] rawArguments, String propertiesFile,
                             boolean requirePropertiesFile)
         throws ArgumentException
  {
    this.rawArguments = rawArguments;

    Properties argumentProperties = null;

    try
    {
      Properties p = new Properties();
      FileInputStream fis = new FileInputStream(propertiesFile);
      p.load(fis);
      fis.close();
      argumentProperties = p;
    }
    catch (Exception e)
    {
      if (requirePropertiesFile)
      {
        Message message = ERR_SUBCMDPARSER_CANNOT_READ_PROPERTIES_FILE.get(
            String.valueOf(propertiesFile), getExceptionMessage(e));
        throw new ArgumentException(message, e);
      }
    }

    parseArguments(rawArguments, argumentProperties);
  }



  /**
   * Parses the provided set of arguments and updates the information associated
   * with this parser accordingly.  Default values for unspecified arguments may
   * be read from the specified properties if any are provided.
   *
   * @param  rawArguments        The set of raw arguments to parse.
   * @param  argumentProperties  A set of properties that may be used to provide
   *                             default values for arguments not included in
   *                             the given raw arguments.
   *
   * @throws  ArgumentException  If a problem was encountered while parsing the
   *                             provided arguments.
   */
  @Override
  public void parseArguments(String[] rawArguments,
                             Properties argumentProperties)
         throws ArgumentException
  {
    this.rawArguments = rawArguments;
    this.subCommand = null;
    this.trailingArguments = new ArrayList<String>();
    this.usageOrVersionDisplayed = false;

    boolean inTrailingArgs = false;

    int numArguments = rawArguments.length;
    for (int i=0; i < numArguments; i++)
    {
      final String arg = rawArguments[i];

      if (inTrailingArgs)
      {
        trailingArguments.add(arg);

        if (subCommand == null)
        {
          throw new ArgumentException(ERR_ARG_SUBCOMMAND_INVALID.get());
        }

        if ((subCommand.getMaxTrailingArguments() > 0) &&
            (trailingArguments.size() > subCommand.getMaxTrailingArguments()))
        {
          Message message = ERR_ARGPARSER_TOO_MANY_TRAILING_ARGS.get(
              subCommand.getMaxTrailingArguments());
          throw new ArgumentException(message);
        }

        continue;
      }

      if (arg.equals("--"))
      {
        inTrailingArgs = true;
      }
      else if (arg.startsWith("--"))
      {
        // This indicates that we are using the long name to reference the
        // argument.  It may be in any of the following forms:
        // --name
        // --name value
        // --name=value

        String argName  = arg.substring(2);
        String argValue = null;
        int    equalPos = argName.indexOf('=');
        if (equalPos < 0)
        {
          // This is fine.  The value is not part of the argument name token.
        }
        else if (equalPos == 0)
        {
          // The argument starts with "--=", which is not acceptable.
          Message message = ERR_SUBCMDPARSER_LONG_ARG_WITHOUT_NAME.get(arg);
          throw new ArgumentException(message);
        }
        else
        {
          // The argument is in the form --name=value, so parse them both out.
          argValue = argName.substring(equalPos+1);
          argName  = argName.substring(0, equalPos);
        }

        // If we're not case-sensitive, then convert the name to lowercase.
        String origArgName = argName;
        if (! longArgumentsCaseSensitive)
        {
          argName = toLowerCase(argName);
        }

        // See if the specified name references a global argument.  If not, then
        // see if it references a subcommand argument.
        Argument a = globalLongIDMap.get(argName);
        if (a == null)
        {
          if (subCommand == null)
          {
            if (argName.equals("help"))
            {
              // "--help" will always be interpreted as requesting usage
              // information.
              try
              {
                getUsage(usageOutputStream);
              } catch (Exception e) {}

              return;
            }
            else
            if (argName.equals(OPTION_LONG_PRODUCT_VERSION))
            {
              // "--version" will always be interpreted as requesting usage
              // information.
              try
              {
                versionPresent = true;
                DirectoryServer.printVersion(usageOutputStream);
                usageOrVersionDisplayed = true ;
              } catch (Exception e) {}

              return;
            }
            else
            {
              // There is no such global argument.
              Message message =
                  ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_LONG_ID.get(
                      origArgName);
              throw new ArgumentException(message);
            }
          }
          else
          {
            a = subCommand.getArgument(argName);
            if (a == null)
            {
              if (argName.equals("help"))
              {
                // "--help" will always be interpreted as requesting usage
                // information.
                try
                {
                  getUsage(usageOutputStream);
                } catch (Exception e) {}

                return;
              }
              else
              if (argName.equals(OPTION_LONG_PRODUCT_VERSION))
              {
                // "--version" will always be interpreted as requesting usage
                // information.
                try
                {
                  versionPresent = true;
                  DirectoryServer.printVersion(usageOutputStream);
                  usageOrVersionDisplayed = true ;
                } catch (Exception e) {}

                return;
              }
              else
              {
                // There is no such global or subcommand argument.
                Message message =
                    ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_LONG_ID.get(origArgName);
                throw new ArgumentException(message);
              }
            }
          }
        }

        a.setPresent(true);

        // If this is a usage argument, then immediately stop and print
        // usage information.
        if (usageGroupArguments.containsKey(a))
        {
          try
          {
            getUsage(a, usageOutputStream);
          } catch (Exception e) {}

          return;
        }

        // See if the argument takes a value.  If so, then make sure one was
        // provided.  If not, then make sure none was provided.
        if (a.needsValue())
        {
          if (argValue == null)
          {
            if ((i+1) == numArguments)
            {
              Message message =
                  ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.
                    get(argName);
              throw new ArgumentException(message);
            }

            argValue = rawArguments[++i];
          }

          MessageBuilder invalidReason = new MessageBuilder();
          if (! a.valueIsAcceptable(argValue, invalidReason))
          {
            Message message = ERR_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_LONG_ID.
                get(argValue, argName, invalidReason.toString());
            throw new ArgumentException(message);
          }

          // If the argument already has a value, then make sure it is
          // acceptable to have more than one.
          if (a.hasValue() && (! a.isMultiValued()))
          {
            Message message =
                ERR_SUBCMDPARSER_NOT_MULTIVALUED_FOR_LONG_ID.get(origArgName);
            throw new ArgumentException(message);
          }

          a.addValue(argValue);
        }
        else
        {
          if (argValue != null)
          {
            Message message =
                ERR_SUBCMDPARSER_ARG_FOR_LONG_ID_DOESNT_TAKE_VALUE.get(
                    origArgName);
            throw new ArgumentException(message);
          }
        }
      }
      else if (arg.startsWith("-"))
      {
        // This indicates that we are using the 1-character name to reference
        // the argument.  It may be in any of the following forms:
        // -n
        // -nvalue
        // -n value
        if (arg.equals("-"))
        {
          Message message = ERR_SUBCMDPARSER_INVALID_DASH_AS_ARGUMENT.get();
          throw new ArgumentException(message);
        }

        char argCharacter = arg.charAt(1);
        String argValue;
        if (arg.length() > 2)
        {
          argValue = arg.substring(2);
        }
        else
        {
          argValue = null;
        }


        // Get the argument with the specified short ID.  It may be either a
        // global argument or a subcommand-specific argument.
        Argument a = globalShortIDMap.get(argCharacter);
        if (a == null)
        {
          if (subCommand == null)
          {
            if (argCharacter == '?')
            {
              // "-?" will always be interpreted as requesting usage.
              try
              {
                getUsage(usageOutputStream);
                if (usageArgument != null)
                {
                  usageArgument.setPresent(true);
                }
              } catch (Exception e) {}

              return;
            }
            else
            if (argCharacter == OPTION_SHORT_PRODUCT_VERSION)
            {
              //  "-V" will always be interpreted as requesting
              // version information except if it's already defined.
              boolean dashVAccepted = true;
              if (globalShortIDMap.containsKey(OPTION_SHORT_PRODUCT_VERSION))
              {
                dashVAccepted = false;
              }
              else
              {
                for (SubCommand subCmd : subCommands.values())
                {
                  if (subCmd.getArgument(OPTION_SHORT_PRODUCT_VERSION) != null)
                  {
                    dashVAccepted = false;
                    break;
                  }
                }
              }
              if (dashVAccepted)
              {
                usageOrVersionDisplayed = true;
                versionPresent = true;
                try
                {
                  DirectoryServer.printVersion(usageOutputStream);
                }
                catch (Exception e)
                {
                }
                return;
              }
              else
              {
                // -V is defined in another suncommand, so we can
                // accepted it as the version information argument
                Message message =
                    ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.
                      get(String.valueOf(argCharacter));
                throw new ArgumentException(message);
              }
            }
            else
            {
              // There is no such argument registered.
              Message message =
                  ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.
                    get(String.valueOf(argCharacter));
              throw new ArgumentException(message);
            }
          }
          else
          {
            a = subCommand.getArgument(argCharacter);
            if (a == null)
            {
              if (argCharacter == '?')
              {
                // "-?" will always be interpreted as requesting usage.
                try
                {
                  getUsage(usageOutputStream);
                } catch (Exception e) {}

                return;
              }
              else
              if (argCharacter == OPTION_SHORT_PRODUCT_VERSION)
              {
                  // "-V" will always be interpreted as requesting
                  // version information except if it's already defined.
                boolean dashVAccepted = true;
                if (globalShortIDMap.containsKey(OPTION_SHORT_PRODUCT_VERSION))
                {
                  dashVAccepted = false;
                }
                else
                {
                  for (SubCommand subCmd : subCommands.values())
                  {
                    if (subCmd.getArgument(OPTION_SHORT_PRODUCT_VERSION)!=null)
                    {
                      dashVAccepted = false;
                      break;
                    }
                  }
                }
                if (dashVAccepted)
                {
                  usageOrVersionDisplayed = true;
                  versionPresent = true;
                  try
                  {
                    DirectoryServer.printVersion(usageOutputStream);
                  }
                  catch (Exception e)
                  {
                  }
                  return;
                }
              }
              else
              {
                // There is no such argument registered.
                Message message = ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID.get(
                    String.valueOf(argCharacter));
                throw new ArgumentException(message);
              }
            }
          }
        }

        a.setPresent(true);

        // If this is the usage argument, then immediately stop and print
        // usage information.
        if (usageGroupArguments.containsKey(a))
        {
          try
          {
            getUsage(a, usageOutputStream);
          } catch (Exception e) {}

          return;
        }

        // See if the argument takes a value.  If so, then make sure one was
        // provided.  If not, then make sure none was provided.
        if (a.needsValue())
        {
          if (argValue == null)
          {
            if ((i+1) == numArguments)
            {
              Message message =
                  ERR_SUBCMDPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID.
                    get(String.valueOf(argCharacter));
              throw new ArgumentException(message);
            }

            argValue = rawArguments[++i];
          }

          MessageBuilder invalidReason = new MessageBuilder();
          if (! a.valueIsAcceptable(argValue, invalidReason))
          {
            Message message = ERR_SUBCMDPARSER_VALUE_UNACCEPTABLE_FOR_SHORT_ID.
                get(argValue, String.valueOf(argCharacter),
                    invalidReason.toString());
            throw new ArgumentException(message);
          }

          // If the argument already has a value, then make sure it is
          // acceptable to have more than one.
          if (a.hasValue() && (! a.isMultiValued()))
          {
            Message message = ERR_SUBCMDPARSER_NOT_MULTIVALUED_FOR_SHORT_ID.get(
                String.valueOf(argCharacter));
            throw new ArgumentException(message);
          }

          a.addValue(argValue);
        }
        else
        {
          if (argValue != null)
          {
            // If we've gotten here, then it means that we're in a scenario like
            // "-abc" where "a" is a valid argument that doesn't take a value.
            // However, this could still be valid if all remaining characters in
            // the value are also valid argument characters that don't take
            // values.
            int valueLength = argValue.length();
            for (int j=0; j < valueLength; j++)
            {
              char c = argValue.charAt(j);
              Argument b = globalShortIDMap.get(c);
              if (b == null)
              {
                if (subCommand == null)
                {
                  Message message =
                      ERR_SUBCMDPARSER_NO_GLOBAL_ARGUMENT_FOR_SHORT_ID.
                        get(String.valueOf(argCharacter));
                  throw new ArgumentException(message);
                }
                else
                {
                  b = subCommand.getArgument(c);
                  if (b == null)
                  {
                    Message message = ERR_SUBCMDPARSER_NO_ARGUMENT_FOR_SHORT_ID.
                        get(String.valueOf(argCharacter));
                    throw new ArgumentException(message);
                  }
                }
              }

              if (b.needsValue())
              {
                // This means we're in a scenario like "-abc" where b is a
                // valid argument that takes a value.  We don't support that.
                Message message = ERR_SUBCMDPARSER_CANT_MIX_ARGS_WITH_VALUES.
                    get(String.valueOf(argCharacter), argValue,
                        String.valueOf(c));
                throw new ArgumentException(message);
              }
              else
              {
                b.setPresent(true);

                // If this is the usage argument, then immediately stop and
                // print usage information.
                if (usageGroupArguments.containsKey(b))
                {
                  try
                  {
                    getUsage(b, usageOutputStream);
                  } catch (Exception e) {}

                  return;
                }
              }
            }
          }
        }
      }
      else if (subCommand != null)
      {
        // It's not a short or long identifier and the sub-command has
        // already been specified, so it must be the first trailing argument.
        if (subCommand.allowsTrailingArguments())
        {
          trailingArguments.add(arg);
          inTrailingArgs = true;
        }
        else
        {
          // Trailing arguments are not allowed for this sub-command.
          Message message = ERR_ARGPARSER_DISALLOWED_TRAILING_ARGUMENT.get(arg);
          throw new ArgumentException(message);
        }
      }
      else
      {
        // It must be the sub-command.
        String nameToCheck = arg;
        if (! longArgumentsCaseSensitive)
        {
          nameToCheck = toLowerCase(arg);
        }

        SubCommand sc = subCommands.get(nameToCheck);
        if (sc == null)
        {
          Message message = ERR_SUBCMDPARSER_INVALID_ARGUMENT.get(arg);
          throw new ArgumentException(message);
        }
        else
        {
          subCommand = sc;
        }
      }
    }

    // If we have a sub-command and it allows trailing arguments and
    // there is a minimum number, then make sure at least that many
    // were provided.
    if (subCommand != null)
    {
      int minTrailingArguments = subCommand.getMinTrailingArguments();
      if (subCommand.allowsTrailingArguments() && (minTrailingArguments > 0))
      {
        if (trailingArguments.size() < minTrailingArguments)
        {
          Message message = ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(
              minTrailingArguments);
          throw new ArgumentException(message);
        }
      }
    }

    // If we don't have the argumentProperties, try to load a properties file.
    if (argumentProperties == null)
    {
      argumentProperties = checkExternalProperties();
    }

    // Iterate through all the global arguments and make sure that they have
    // values or a suitable default is available.
    for (Argument a : globalArgumentList)
    {
      if (! a.isPresent())
      {
        // See if there is a value in the properties that can be used
        if ((argumentProperties != null) && (a.getPropertyName() != null))
        {
          String value = argumentProperties.getProperty(a.getPropertyName()
              .toLowerCase());
          MessageBuilder invalidReason =  new MessageBuilder();
          if (value != null)
          {
            Boolean addValue = true;
            if (!( a instanceof BooleanArgument))
            {
              addValue = a.valueIsAcceptable(value, invalidReason);
            }
            if (addValue)
            {
              a.addValue(value);
              if (a.needsValue())
              {
                a.setPresent(true);
              }
              a.setValueSetByProperty(true);
            }
          }
        }
      }

      if ((! a.isPresent()) && a.needsValue())
      {
        // ISee if the argument defines a default.
        if (a.getDefaultValue() != null)
        {
          a.addValue(a.getDefaultValue());
        }

        // If there is still no value and the argument is required, then that's
        // a problem.
        if ((! a.hasValue()) && a.isRequired())
        {
          Message message =
              ERR_SUBCMDPARSER_NO_VALUE_FOR_REQUIRED_ARG.get(a.getName());
          throw new ArgumentException(message);
        }
      }
    }


    // Iterate through all the subcommand-specific arguments and make sure that
    // they have values or a suitable default is available.
    if (subCommand != null)
    {
      for (Argument a : subCommand.getArguments())
      {
        if (! a.isPresent())
        {
          // See if there is a value in the properties that can be used
          if ((argumentProperties != null) && (a.getPropertyName() != null))
          {
            String value = argumentProperties.getProperty(a.getPropertyName()
                .toLowerCase());
            MessageBuilder invalidReason =  new MessageBuilder();
            if (value != null)
            {
              Boolean addValue = true;
              if (!( a instanceof BooleanArgument))
              {
                addValue = a.valueIsAcceptable(value, invalidReason);
              }
              if (addValue)
              {
                a.addValue(value);
                if (a.needsValue())
                {
                  a.setPresent(true);
                }
                a.setValueSetByProperty(true);
              }
            }
          }
        }

        if ((! a.isPresent()) && a.needsValue())
        {
          // See if the argument defines a default.
          if (a.getDefaultValue() != null)
          {
            a.addValue(a.getDefaultValue());
          }

          // If there is still no value and the argument is required, then
          // that's a problem.
          if ((! a.hasValue()) && a.isRequired())
          {
            Message message =
                ERR_SUBCMDPARSER_NO_VALUE_FOR_REQUIRED_ARG.get(a.getName());
            throw new ArgumentException(message);
          }
        }
      }
    }
  }



  /**
   * Appends usage information for the specified subcommand to the provided
   * buffer.
   *
   * @param  buffer      The buffer to which the usage information should be
   *                     appended.
   * @param  subCommand  The subcommand for which to display the usage
   *                     information.
   */
  public void getSubCommandUsage(MessageBuilder buffer, SubCommand subCommand)
  {
    usageOrVersionDisplayed = true;
    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if ((scriptName == null) || (scriptName.length() == 0))
    {
      scriptName = "java " + mainClassName;
    }
    buffer.append(INFO_ARGPARSER_USAGE.get());
    buffer.append("  ");
    buffer.append(scriptName);

    buffer.append(" ");
    buffer.append(subCommand.getName());
    buffer.append(" ").append(INFO_SUBCMDPARSER_OPTIONS.get());
    if (subCommand.allowsTrailingArguments()) {
      buffer.append(' ');
      buffer.append(subCommand.getTrailingArgumentsDisplayName());
    }
    buffer.append(EOL);
    buffer.append(subCommand.getDescription());
    buffer.append(EOL);

    if ( ! globalArgumentList.isEmpty())
    {
      buffer.append(EOL);
      buffer.append(INFO_GLOBAL_OPTIONS.get());
      buffer.append(EOL);
      buffer.append("    ");
      buffer.append(INFO_GLOBAL_OPTIONS_REFERENCE.get(scriptName));
      buffer.append(EOL);
    }

    if ( ! subCommand.getArguments().isEmpty() )
    {
      buffer.append(EOL);
      buffer.append(INFO_SUBCMD_OPTIONS.get());
      buffer.append(EOL);
    }
    for (Argument a : subCommand.getArguments())
    {
      // If this argument is hidden, then skip it.
      if (a.isHidden())
      {
        continue;
      }


      // Write a line with the short and/or long identifiers that may be used
      // for the argument.
      Character shortID = a.getShortIdentifier();
      String longID = a.getLongIdentifier();
      if (shortID != null)
      {
        int currentLength = buffer.length();

        if (a.equals(usageArgument))
        {
          buffer.append("-?, ");
        }

        buffer.append("-");
        buffer.append(shortID.charValue());

        if (a.needsValue() && longID == null)
        {
          buffer.append(" ");
          buffer.append(a.getValuePlaceholder());
        }

        if (longID != null)
        {
          StringBuilder newBuffer = new StringBuilder();
          newBuffer.append(", --");
          newBuffer.append(longID);

          if (a.needsValue())
          {
            newBuffer.append(" ");
            newBuffer.append(a.getValuePlaceholder());
          }

          int lineLength = (buffer.length() - currentLength) +
                           newBuffer.length();
          if (lineLength > MAX_LENGTH)
          {
            buffer.append(EOL);
            buffer.append(newBuffer.toString());
          }
          else
          {
            buffer.append(newBuffer.toString());
          }
        }

        buffer.append(EOL);
      }
      else
      {
        if (longID != null)
        {
          if (a.equals(usageArgument))
          {
            buffer.append("-?, ");
          }
          buffer.append("--");
          buffer.append(longID);

          if (a.needsValue())
          {
            buffer.append(" ");
            buffer.append(a.getValuePlaceholder());
          }

          buffer.append(EOL);
        }
      }


      // Write one or more lines with the description of the argument.  We will
      // indent the description five characters and try our best to wrap at or
      // before column 79 so it will be friendly to 80-column displays.
      Message description = a.getDescription();
      int maxLength = MAX_LENGTH - INDENT.length() - 1;
      if (description.length() <= maxLength)
      {
        buffer.append(INDENT);
        buffer.append(description);
        buffer.append(EOL);
      }
      else
      {
        String s = description.toString();
        while (s.length() > maxLength)
        {
          int spacePos = s.lastIndexOf(' ', maxLength);
          if (spacePos > 0)
          {
            buffer.append(INDENT);
            buffer.append(s.substring(0, spacePos).trim());
            s = s.substring(spacePos+1).trim();
            buffer.append(EOL);
          }
          else
          {
            // There are no spaces in the first 74 columns.  See if there is one
            // after that point.  If so, then break there.  If not, then don't
            // break at all.
            spacePos = s.indexOf(' ');
            if (spacePos > 0)
            {
              buffer.append(INDENT);
              buffer.append(s.substring(0, spacePos).trim());
              s = s.substring(spacePos+1).trim();
              buffer.append(EOL);
            }
            else
            {
              buffer.append(INDENT);
              buffer.append(s);
              s = "";
              buffer.append(EOL);
            }
          }
        }

        if (s.length() > 0)
        {
          buffer.append("    ");
          buffer.append(s);
          buffer.append(EOL);
        }
      }
      if (a.needsValue() && (a.getDefaultValue() != null) &&
          (a.getDefaultValue().length() > 0))
       {
         buffer.append(INDENT);
         buffer.append(INFO_ARGPARSER_USAGE_DEFAULT_VALUE.get(
             a.getDefaultValue()).toString());
         buffer.append(EOL);
       }
    }
  }



  /**
   * Retrieves a string containing usage information based on the defined
   * arguments.
   *
   * @return  A string containing usage information based on the defined
   *          arguments.
   */
  @Override
  public String getUsage()
  {
    MessageBuilder buffer = new MessageBuilder();

    if (subCommand == null) {
      if (System.getProperty("org.forgerock.opendj.gendoc") != null) {
        // Generate reference documentation for dsconfig subcommands
        for (SubCommand s : subCommands.values()) {
          buffer.append(toRefSect2(s));
        }
      } else if (usageGroupArguments.size() > 1) {
        // We have sub-command groups, so don't display any
        // sub-commands by default.
        getFullUsage(Collections.<SubCommand> emptySet(), true, buffer);
      } else {
        // No grouping, so display all sub-commands.
        getFullUsage(subCommands.values(), true, buffer);
      }
    } else {
      getSubCommandUsage(buffer, subCommand);
    }

    return buffer.toMessage().toString();
  }



  /**
   * Retrieves a string describing how the user can get more help.
   *
   * @return A string describing how the user can get more help.
   */
  public Message getHelpUsageReference()
  {
    usageOrVersionDisplayed = true;
    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if ((scriptName == null) || (scriptName.length() == 0))
    {
      scriptName = "java " + mainClassName;
    }

    MessageBuilder buffer = new MessageBuilder();
    buffer.append(INFO_GLOBAL_HELP_REFERENCE.get(scriptName));
    buffer.append(EOL);
    return buffer.toMessage();
  }



  /**
   * Retrieves the set of unnamed trailing arguments that were
   * provided on the command line.
   *
   * @return The set of unnamed trailing arguments that were provided
   *         on the command line.
   */
  @Override
  public ArrayList<String> getTrailingArguments()
  {
    return trailingArguments;
  }



  /**
   * Indicates whether the usage information has been displayed to the end user
   * either by an explicit argument like "-H" or "--help", or by a built-in
   * argument like "-?".
   *
   * @return  {@code true} if the usage information has been displayed, or
   *          {@code false} if not.
   */
  @Override
  public boolean usageOrVersionDisplayed()
  {
    return usageOrVersionDisplayed;
  }



  /**
   * Adds the provided subcommand to this argument parser.  This is only
   * intended for use by the <CODE>SubCommand</CODE> constructor and does not
   * do any validation of its own to ensure that there are no conflicts with the
   * subcommand or any of its arguments.
   *
   * @param  subCommand  The subcommand to add to this argument parser.
   */
  void addSubCommand(SubCommand subCommand)
  {
    subCommands.put(toLowerCase(subCommand.getName()), subCommand);
  }



  // Get usage for a specific usage argument.
  private void getUsage(Argument a, OutputStream outputStream)
      throws IOException {
    MessageBuilder buffer = new MessageBuilder();

    if (a.equals(usageArgument) && subCommand != null) {
      getSubCommandUsage(buffer, subCommand);
    } else if (a.equals(usageArgument) && usageGroupArguments.size() <= 1) {
      // No groups - so display all sub-commands.
      getFullUsage(subCommands.values(), true, buffer);
    } else if (a.equals(usageArgument)) {
      // Using groups - so display all sub-commands group help.
      getFullUsage(Collections.<SubCommand> emptySet(), true, buffer);
    } else {
      // Requested help on specific group - don't display global
      // options.
      getFullUsage(usageGroupArguments.get(a), false, buffer);
    }

    outputStream.write(buffer.toString().getBytes());
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void getUsage(OutputStream outputStream)
      throws IOException {
    outputStream.write(getUsage().getBytes());
  }



  // Appends complete usage information for the specified set of
  // sub-commands.
  private void getFullUsage(Collection<SubCommand> c,
      boolean showGlobalOptions, MessageBuilder buffer) {
    usageOrVersionDisplayed = true;
    if ((toolDescription != null) && (toolDescription.length() > 0))
    {
      buffer.append(wrapText(toolDescription, MAX_LENGTH - 1));
      buffer.append(EOL);
      buffer.append(EOL);
    }

    String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
    if ((scriptName == null) || (scriptName.length() == 0))
    {
      scriptName = "java " + mainClassName;
    }
    buffer.append(INFO_ARGPARSER_USAGE.get());
    buffer.append("  ");
    buffer.append(scriptName);

    if (subCommands.isEmpty())
    {
      buffer.append(" "+INFO_SUBCMDPARSER_OPTIONS.get());
    }
    else
    {
      buffer.append(" "+INFO_SUBCMDPARSER_SUBCMD_AND_OPTIONS.get());
    }

    if (!subCommands.isEmpty())
    {
      buffer.append(EOL);
      buffer.append(EOL);
      if (c.isEmpty())
      {
        buffer.append(INFO_SUBCMDPARSER_SUBCMD_HELP_HEADING.get());
      }
      else
      {
        buffer.append(INFO_SUBCMDPARSER_SUBCMD_HEADING.get());
      }
      buffer.append(EOL);
    }

    if (c.isEmpty()) {
      // Display usage arguments (except the default one).
      for (Argument a : globalArgumentList) {
        if (a.isHidden()) {
          continue;
        }

        if (usageGroupArguments.containsKey(a)) {
          if (!a.equals(usageArgument)) {
            printArgumentUsage(a, buffer);
          }
        }
      }
    } else {
      boolean isFirst = true;
      for (SubCommand sc : c) {
        if (sc.isHidden()) {
          continue;
        }
        if (isFirst)
        {
          buffer.append(EOL);
        }
        buffer.append(sc.getName());
        buffer.append(EOL);
        indentAndWrap(Message.raw(INDENT), sc.getDescription(), buffer);
        buffer.append(EOL);
        isFirst = false;
      }
    }

    buffer.append(EOL);

    if (showGlobalOptions) {
      if (subCommands.isEmpty())
      {
        buffer.append(INFO_SUBCMDPARSER_WHERE_OPTIONS_INCLUDE.get());
        buffer.append(EOL);
      }
      else
      {
        buffer.append(INFO_SUBCMDPARSER_GLOBAL_HEADING.get());
        buffer.append(EOL);
      }
      buffer.append(EOL);

      boolean printGroupHeaders = printUsageGroupHeaders();

      // Display non-usage arguments.
      for (ArgumentGroup argGroup : argumentGroups)
      {
        if (argGroup.containsArguments() && printGroupHeaders)
        {
          // Print the groups description if any
          Message groupDesc = argGroup.getDescription();
          if (groupDesc != null && !Message.EMPTY.equals(groupDesc)) {
            buffer.append(EOL);
            buffer.append(wrapText(groupDesc.toString(), MAX_LENGTH - 1));
            buffer.append(EOL);
            buffer.append(EOL);
          }
        }

        for (Argument a : argGroup.getArguments()) {
          if (a.isHidden()) {
            continue;
          }

          if (!usageGroupArguments.containsKey(a)) {
            printArgumentUsage(a, buffer);
          }
        }
      }

      // Finally print default usage argument.
      if (usageArgument != null) {
        printArgumentUsage(usageArgument, buffer);
      } else {
        buffer.append("-?");
      }
      buffer.append(EOL);
    }
  }



  /**
   * Appends argument usage information to the provided buffer.
   *
   * @param a
   *          The argument to handle.
   * @param buffer
   *          The buffer to which the usage information should be
   *          appended.
   */
  private void printArgumentUsage(Argument a, MessageBuilder buffer) {
    String value;
    if (a.needsValue())
    {
      Message pHolder = a.getValuePlaceholder();
      if (pHolder == null)
      {
        value = " {value}";
      }
      else
      {
        value = " " + pHolder;
      }
    }
    else
    {
      value = "";
    }

    Character shortIDChar = a.getShortIdentifier();
    if (shortIDChar != null)
    {
      if (a.equals(usageArgument))
      {
        buffer.append("-?, ");
      }
      buffer.append("-");
      buffer.append(shortIDChar);

      String longIDString = a.getLongIdentifier();
      if (longIDString != null)
      {
        buffer.append(", --");
        buffer.append(longIDString);
      }
      buffer.append(value);
    }
    else
    {
      String longIDString = a.getLongIdentifier();
      if (longIDString != null)
      {
        if (a.equals(usageArgument))
        {
          buffer.append("-?, ");
        }
        buffer.append("--");
        buffer.append(longIDString);
        buffer.append(value);
      }
    }

    buffer.append(EOL);
    indentAndWrap(Message.raw(INDENT), a.getDescription(), buffer);

    if (a.needsValue() && (a.getDefaultValue() != null) &&
        (a.getDefaultValue().length() > 0))
     {
       indentAndWrap(Message.raw(INDENT),
           INFO_ARGPARSER_USAGE_DEFAULT_VALUE.get(a.getDefaultValue()),
           buffer);
     }
  }



  /**
   * Write one or more lines with the description of the argument.  We will
   * indent the description five characters and try our best to wrap at or
   * before column 79 so it will be friendly to 80-column displays.
   */
  private void indentAndWrap(Message indent, Message text,
                             MessageBuilder buffer)
  {
    int actualSize = MAX_LENGTH - indent.length();
    if (text.length() <= actualSize)
    {
      buffer.append(indent);
      buffer.append(text);
      buffer.append(EOL);
    }
    else
    {
      String s = text.toString();
      while (s.length() > actualSize)
      {
        int spacePos = s.lastIndexOf(' ', actualSize);
        if (spacePos > 0)
        {
          buffer.append(indent);
          buffer.append(s.substring(0, spacePos).trim());
          s = s.substring(spacePos + 1).trim();
          buffer.append(EOL);
        }
        else
        {
          // There are no spaces in the first actualSize -1 columns. See
          // if there is one after that point. If so, then break there.
          // If not, then don't break at all.
          spacePos = s.indexOf(' ');
          if (spacePos > 0)
          {
            buffer.append(indent);
            buffer.append(s.substring(0, spacePos).trim());
            s = s.substring(spacePos + 1).trim();
            buffer.append(EOL);
          }
          else
          {
            buffer.append(indent);
            buffer.append(s);
            s = "";
            buffer.append(EOL);
          }
        }
      }

      if (s.length() > 0)
      {
        buffer.append(indent);
        buffer.append(s);
        buffer.append(EOL);
      }
    }
  }

  /**
   * Returns whether the usage argument was provided or not.  This method
   * should be called after a call to parseArguments.
   * @return <CODE>true</CODE> if the usage argument was provided and
   * <CODE>false</CODE> otherwise.
   */
  @Override
  public boolean isUsageArgumentPresent()
  {
    boolean isUsageArgumentPresent = false;
    if (usageArgument != null)
    {
      isUsageArgumentPresent = usageArgument.isPresent();
    }
    return isUsageArgumentPresent;
  }

  /**
   * Returns whether the version argument was provided or not.  This method
   * should be called after a call to parseArguments.
   * @return <CODE>true</CODE> if the version argument was provided and
   * <CODE>false</CODE> otherwise.
   */
  @Override
  public boolean isVersionArgumentPresent()
  {
    boolean isPresent;
    if (!super.isVersionArgumentPresent())
    {
      isPresent = versionPresent;
    }
    else
    {
      isPresent = true;
    }
    return isPresent;
  }

  /**
   * Generate reference documentation for dsconfig subcommands in DocBook 5 XML
   * format. As the number of categories is large, the subcommand entries are
   * sorted here by name for inclusion in a &lt;refsect1&gt; covering all
   * dsconfig Subcommands as part of the &lt;refentry&gt; for dsconfig (in
   * man-dsconfig.xml).
   * <p>
   * Although it would be possible to categorize subcommands in the same way as
   * they are categorized in dsconfig interactive mode, this generator does not
   * use the categories.
   * <p>
   * It would also be possible to generate the sort of information provided by
   * the configuration reference, such that this reference would not stop at
   * simply listing an option like --set {PROP:VAL}, but instead would also
   * provide the list of PROPs and their possible VALs. A future improvement
   * could no doubt merge the configuration reference with this content, though
   * perhaps the problem calls for hypertext rather than the linear structure
   * of a &lt;refentry&gt;.
   * <p>
   * Each individual subcommand results in a &lt;refsect2&gt; element similar
   * to the following.
   * <pre>
    &lt;refsect2 xml:id=&quot;dsconfig-create-local-db-index&quot;&gt;
     &lt;title&gt;dsconfig create-local-db-index&lt;/title&gt;
     &lt;para&gt;Creates Local DB Indexes&lt;/para&gt;
     &lt;variablelist&gt;
      &lt;varlistentry&gt;
       &lt;term&gt;&lt;option&gt;--backend-name {name}
        &lt;/option&gt;&lt;/term&gt;
       &lt;listitem&gt;
        &lt;para&gt;The name of the Local DB Backend&lt;/para&gt;
       &lt;/listitem&gt;
      &lt;/varlistentry&gt;
      &lt;varlistentry&gt;
       &lt;term&gt;&lt;option&gt;--index-name {OID}&lt;/option&gt;&lt;/term&gt;
       &lt;listitem&gt;
        &lt;para&gt;The name of the new Local DB Index which will also be used
        as the value of the &quot;attribute&quot; property: Specifies the name
        of the attribute for which the index is to be maintained.&lt;/para&gt;
       &lt;/listitem&gt;
      &lt;/varlistentry&gt;
      &lt;varlistentry&gt;
       &lt;term&gt;&lt;option&gt;--set {PROP:VALUE}&lt;/option&gt;&lt;/term&gt;
       &lt;listitem&gt;
        &lt;para&gt;Assigns a value to a property where PROP is the name of the
        property and VALUE is the single value to be assigned. Specify the same
        property multiple times in order to assign more than one value to
        it&lt;/para&gt;
       &lt;/listitem&gt;
      &lt;/varlistentry&gt;
     &lt;/variablelist&gt;
     &lt;/refsect2&gt;
   * </pre>
   * @param sc The SubCommand containing reference information.
   * @return Refsect2 representation of the subcommand.
   */
  private String toRefSect2(SubCommand sc)
  {
    String options = "";
    if (!sc.getArguments().isEmpty())
    {
      options += " <variablelist>" + EOL;
      for (Argument a : sc.getArguments())
      {
        options += "  <varlistentry>" + EOL;
        options += "   <term><option>";
        Character shortID = a.getShortIdentifier();
        if (shortID != null) options += "-" + shortID.charValue();
        String longID = a.getLongIdentifier();
        if (shortID != null && longID != null) options += " | ";
        if (longID != null) options += "--" + longID;
        if (a.needsValue()) options += " " + a.getValuePlaceholder();
        options += "</option></term>" + EOL;
        options += "   <listitem>"  + EOL;
        options += "    <para>";
        options += a.getDescription().toString();
        options += "</para>" + EOL;
        options += "   </listitem>" + EOL;
        options += "  </varlistentry>" + EOL;
      }
      options += " </variablelist>" + EOL;
    }

    return "<refsect2 xml:id=\"dsconfig-" + sc.getName() + "\">" + EOL +
      " <title>dsconfig " + sc.getName() + "</title>" + EOL +
      " <para>" + sc.getDescription().toString() + "</para>" + EOL +
      options +
      "</refsect2>" + EOL;
  }
}

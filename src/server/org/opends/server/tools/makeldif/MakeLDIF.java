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
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.makeldif;
import org.opends.messages.Message;



import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Random;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This class defines a program that can be used to generate LDIF content based
 * on a template.
 */
public class MakeLDIF
       implements EntryWriter
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.makeldif.MakeLDIF";



  // The LDIF writer that will be used to write the entries.
  private LDIFWriter ldifWriter;

  // The total number of entries that have been written.
  private long entriesWritten;

  private PrintStream out = System.out;
  private PrintStream err = System.err;

  /**
   * Invokes the <CODE>makeLDIFMain</CODE> method with the provided set of
   * arguments.
   *
   * @param  args  The command-line arguments provided for this program.
   */
  public static void main(String[] args)
  {
    MakeLDIF makeLDIF = new MakeLDIF();
    int returnCode = makeLDIF.makeLDIFMain(args);
    if (returnCode != 0)
    {
      System.exit(filterExitCode(returnCode));
    }
  }



  /**
   * Creates a new instance of this utility.  It should just be used for
   * invoking the <CODE>makeLDIFMain</CODE> method.
   */
  public MakeLDIF()
  {
    ldifWriter     = null;
    entriesWritten = 0L;
  }

  /**
   * Processes the provided set of command-line arguments and begins generating
   * the LDIF content.
   *
   * @param  args  The command-line arguments provided for this program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  initializeSchema  Indicates whether to initialize the schema.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   * @return  A result code of zero if all processing completed properly, or
   *          a nonzero result if a problem occurred.
   *
   */
  public int makeLDIFMain(String[] args, boolean initializeServer,
      boolean initializeSchema,
      OutputStream outStream,
      OutputStream errStream)
  {
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }

//  Create and initialize the argument parser for this program.
    Message toolDescription = INFO_MAKELDIF_TOOL_DESCRIPTION.get();
    ArgumentParser  argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                   false);
    BooleanArgument showUsage;
    IntegerArgument randomSeed;
    StringArgument  configClass;
    StringArgument  configFile;
    StringArgument  templatePath;
    StringArgument  ldifFile;
    StringArgument  resourcePath;

    try
    {
      configFile = new StringArgument("configfile", 'c', "configFile", true,
                                      false, true,
                                      INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                                      null,
                                      INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                                       OPTION_LONG_CONFIG_CLASS, false,
                                       false, true,
                                       INFO_CONFIGCLASS_PLACEHOLDER.get(), null,
                                       null,
                                       INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      resourcePath =
           new StringArgument("resourcepath", 'r', "resourcePath", true, false,
                              true, INFO_PATH_PLACEHOLDER.get(), null, null,
                              INFO_MAKELDIF_DESCRIPTION_RESOURCE_PATH.get());
      resourcePath.setHidden(true);
      argParser.addArgument(resourcePath);


      templatePath =
              new StringArgument("templatefile", 't', "templateFile",
                                 true, false, true, INFO_FILE_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_MAKELDIF_DESCRIPTION_TEMPLATE.get());
      argParser.addArgument(templatePath);


      ldifFile = new StringArgument("ldiffile", 'o', "ldifFile", true, false,
                                    true, INFO_FILE_PLACEHOLDER.get(), null,
                                    null, INFO_MAKELDIF_DESCRIPTION_LDIF.get());
      argParser.addArgument(ldifFile);


      randomSeed = new IntegerArgument("randomseed", OPTION_SHORT_RANDOM_SEED,
                                       OPTION_LONG_RANDOM_SEED, false,
                                       false, true, INFO_SEED_PLACEHOLDER.get(),
                                       0, null,
                                       INFO_MAKELDIF_DESCRIPTION_SEED.get());
      argParser.addArgument(randomSeed);


      showUsage = new BooleanArgument("help", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_MAKELDIF_DESCRIPTION_HELP.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    if (initializeServer)
    {
      // Initialize the Directory Server configuration handler using the
      // information that was provided.
      DirectoryServer directoryServer = DirectoryServer.getInstance();
      DirectoryServer.bootstrapClient();

      try
      {
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        Message message = ERR_MAKELDIF_CANNOT_INITIALIZE_JMX.get(
            String.valueOf(configFile.getValue()), e.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
            configFile.getValue());
      }
      catch (Exception e)
      {
        Message message = ERR_MAKELDIF_CANNOT_INITIALIZE_CONFIG.get(
            String.valueOf(configFile.getValue()), e.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    if (initializeSchema)
    {
      try
      {
        DirectoryServer.getInstance().initializeSchema();
      }
      catch (Exception e)
      {
        Message message = ERR_MAKELDIF_CANNOT_INITIALIZE_SCHEMA.get(
            String.valueOf(configFile.getValue()), e.getMessage());
        System.err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }


    // Create the random number generator that will be used for the generation
    // process.
    Random random;
    if (randomSeed.isPresent())
    {
      try
      {
        random = new Random(randomSeed.getIntValue());
      }
      catch (Exception e)
      {
        random = new Random();
      }
    }
    else
    {
      random = new Random();
    }


    // If a resource path was provided, then make sure it's acceptable.
    File resourceDir = new File(resourcePath.getValue());
    if (! resourceDir.exists())
    {
      Message message = ERR_MAKELDIF_NO_SUCH_RESOURCE_DIRECTORY.get(
              resourcePath.getValue());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Load and parse the template file.
    LinkedList<Message> warnings = new LinkedList<Message>();
    TemplateFile templateFile = new TemplateFile(resourcePath.getValue(),
                                                 random);
    try
    {
      templateFile.parse(templatePath.getValue(), warnings);
    }
    catch (IOException ioe)
    {
      Message message = ERR_MAKELDIF_IOEXCEPTION_DURING_PARSE.get(
              ioe.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      Message message = ERR_MAKELDIF_EXCEPTION_DURING_PARSE.get(
              e.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // If there were any warnings, then print them.
    if (! warnings.isEmpty())
    {
      for (Message s : warnings)
      {
        err.println(wrapText(s, MAX_LINE_WIDTH));
      }
    }


    // Create the LDIF writer that will be used to actually write the LDIF.
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(ldifFile.getValue(),
                              ExistingFileBehavior.OVERWRITE);
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (IOException ioe)
    {
      Message message = ERR_MAKELDIF_UNABLE_TO_CREATE_LDIF.get(
              ldifFile.getValue(), String.valueOf(ioe));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Generate the LDIF content.
    try
    {
      templateFile.generateLDIF(this);
    }
    catch (Exception e)
    {
      Message message = ERR_MAKELDIF_ERROR_WRITING_LDIF.get(
              ldifFile.getValue(), stackTraceToSingleLineString(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    finally
    {
      try
      {
        ldifWriter.close();
      } catch (Exception e) {}
    }


    // If we've gotten here, then everything was successful.
    return 0;
  }


  /**
   * Processes the provided set of command-line arguments and begins generating
   * the LDIF content.
   *
   * @param  args  The command-line arguments provided for this program.
   *
   * @return  A result code of zero if all processing completed properly, or
   *          a nonzero result if a problem occurred.
   */
  public int makeLDIFMain(String[] args)
  {
     return makeLDIFMain(args, true, true, System.out, System.err);
  }



  /**
   * Writes the provided entry to the appropriate target.
   *
   * @param  entry  The entry to be written.
   *
   * @return  <CODE>true</CODE> if the entry writer will accept more entries, or
   *          <CODE>false</CODE> if not.
   *
   * @throws  IOException  If a problem occurs while writing the entry to its
   *                       intended destination.
   *
   * @throws  MakeLDIFException  If some other problem occurs.
   */
  public boolean writeEntry(TemplateEntry entry)
         throws IOException, MakeLDIFException
  {
    try
    {
      if (entry.getDN() != null)
      {
        ldifWriter.writeTemplateEntry(entry);

        if ((++entriesWritten % 1000) == 0)
        {
          Message message =
            INFO_MAKELDIF_PROCESSED_N_ENTRIES.get(entriesWritten);
          out.println(wrapText(message, MAX_LINE_WIDTH));
        }
      }
      else
      {
        AttributeType[] rdnAttrs = entry.getTemplate().getRDNAttributes();
        String nullRdn = "";
        for (AttributeType att : rdnAttrs)
        {
          if (entry.getValue(att) == null)
          {
            nullRdn = att.getNameOrOID();
            break ;
          }
        }
        Message message =
          ERR_MAKELDIF_CANNOT_WRITE_ENTRY_WITHOUT_DN.get(nullRdn);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return true;
      }

      return true;
    }
    catch (IOException ioe)
    {
      throw ioe;
    }
    catch (Exception e)
    {
      Message message = ERR_MAKELDIF_CANNOT_WRITE_ENTRY.get(
          String.valueOf(entry.getDN()), stackTraceToSingleLineString(e));
      throw new MakeLDIFException(message, e);
    }
  }



  /**
   * Notifies the entry writer that no more entries will be provided and that
   * any associated cleanup may be performed.
   */
  public void closeEntryWriter()
  {
    Message message = INFO_MAKELDIF_PROCESSING_COMPLETE.get(entriesWritten);
    out.println(wrapText(message, MAX_LINE_WIDTH));
  }
}


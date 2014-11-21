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
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.tools;
import org.opends.messages.Message;



import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.MultiChoiceArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.MAX_LINE_WIDTH;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This class provides a program that may be used to search LDIF files.  It is
 * modeled after the LDAPSearch tool, with the primary differencing being that
 * all of its data comes from LDIF rather than communicating over LDAP.
 * However, it does have a number of differences that allow it to perform
 * multiple operations in a single pass rather than requiring multiple passes
 * through the LDIF.
 */
public class LDIFSearch
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDIFSearch";



  /**
   * The search scope string that will be used for baseObject searches.
   */
  private static final String SCOPE_STRING_BASE = "base";



  /**
   * The search scope string that will be used for singleLevel searches.
   */
  private static final String SCOPE_STRING_ONE = "one";



  /**
   * The search scope string that will be used for wholeSubtree searches.
   */
  private static final String SCOPE_STRING_SUB = "sub";



  /**
   * The search scope string that will be used for subordinateSubtree searches.
   */
  private static final String SCOPE_STRING_SUBORDINATE = "subordinate";



  /**
   * Provides the command line arguments to the <CODE>mainSearch</CODE> method
   * so that they can be processed.
   *
   * @param  args  The command line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int exitCode = mainSearch(args, true, System.out, System.err);
    if (exitCode != 0)
    {
      System.exit(filterExitCode(exitCode));
    }
  }



  /**
   * Parses the provided command line arguments and performs the appropriate
   * search operation.
   *
   * @param  args              The command line arguments provided to this
   *                           program.
   * @param  initializeServer  True if server initialization should be done.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   *
   * @return  The return code for this operation.  A value of zero indicates
   *          that all processing completed successfully.  A nonzero value
   *          indicates that some problem occurred during processing.
   */
  public static int mainSearch(String[] args, boolean initializeServer,
                               OutputStream outStream, OutputStream errStream)
  {
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

    LinkedHashSet<String> scopeStrings = new LinkedHashSet<String>(4);
    scopeStrings.add(SCOPE_STRING_BASE);
    scopeStrings.add(SCOPE_STRING_ONE);
    scopeStrings.add(SCOPE_STRING_SUB);
    scopeStrings.add(SCOPE_STRING_SUBORDINATE);


    BooleanArgument     dontWrap;
    BooleanArgument     overwriteExisting;
    BooleanArgument     showUsage;
    StringArgument      filterFile;
    IntegerArgument     sizeLimit;
    IntegerArgument     timeLimit;
    MultiChoiceArgument scopeString;
    StringArgument      baseDNString;
    StringArgument      configClass;
    StringArgument      configFile;
    StringArgument      ldifFile;
    StringArgument      outputFile;


    Message toolDescription = INFO_LDIFSEARCH_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false, true, 0, 0,
                                                  "[filter] [attributes ...]");

    try
    {
      ldifFile = new StringArgument(
              "ldiffile", 'l', "ldifFile", false, true,
              true, INFO_LDIFFILE_PLACEHOLDER.get(), null, null,
              INFO_LDIFSEARCH_DESCRIPTION_LDIF_FILE.get());
      argParser.addArgument(ldifFile);

      baseDNString = new StringArgument(
              "basedn", OPTION_SHORT_BASEDN,
              OPTION_LONG_BASEDN, false, true,
              true, INFO_BASEDN_PLACEHOLDER.get(), "", null,
              INFO_LDIFSEARCH_DESCRIPTION_BASEDN.get());
      argParser.addArgument(baseDNString);

      scopeString = new MultiChoiceArgument(
              "scope", 's', "searchScope", false, false,
              true, INFO_SCOPE_PLACEHOLDER.get(), SCOPE_STRING_SUB,
              null, scopeStrings, false,
              INFO_LDIFSEARCH_DESCRIPTION_SCOPE.get());
      argParser.addArgument(scopeString);

      configFile = new StringArgument(
              "configfile", 'c', "configFile", false,
              false, true, INFO_CONFIGFILE_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                             OPTION_LONG_CONFIG_CLASS, false,
                             false, true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
                             ConfigFileHandler.class.getName(), null,
                             INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      filterFile = new StringArgument("filterfile", 'f', "filterFile", false,
          false, true, INFO_FILTER_FILE_PLACEHOLDER.get(), null, null,
          INFO_LDIFSEARCH_DESCRIPTION_FILTER_FILE.get());
      argParser.addArgument(filterFile);

      outputFile = new StringArgument(
              "outputfile", 'o', "outputFile", false,
              false, true, INFO_OUTPUT_FILE_PLACEHOLDER.get(), null, null,
              INFO_LDIFSEARCH_DESCRIPTION_OUTPUT_FILE.get());
      argParser.addArgument(outputFile);

      overwriteExisting =
           new BooleanArgument(
                   "overwriteexisting", 'O',"overwriteExisting",
                   INFO_LDIFSEARCH_DESCRIPTION_OVERWRITE_EXISTING.get());
      argParser.addArgument(overwriteExisting);

      dontWrap = new BooleanArgument(
              "dontwrap", 'T', "dontWrap",
              INFO_LDIFSEARCH_DESCRIPTION_DONT_WRAP.get());
      argParser.addArgument(dontWrap);

      sizeLimit = new IntegerArgument(
              "sizelimit", 'z', "sizeLimit", false,
              false, true, INFO_SIZE_LIMIT_PLACEHOLDER.get(), 0, null,
              true, 0, false, 0,
              INFO_LDIFSEARCH_DESCRIPTION_SIZE_LIMIT.get());
      argParser.addArgument(sizeLimit);

      timeLimit = new IntegerArgument(
              "timelimit", 't', "timeLimit", false,
              false, true, INFO_TIME_LIMIT_PLACEHOLDER.get(), 0, null,
              true, 0, false, 0,
              INFO_LDIFSEARCH_DESCRIPTION_TIME_LIMIT.get());
      argParser.addArgument(timeLimit);


      showUsage = new BooleanArgument(
              "help", OPTION_SHORT_HELP,
              OPTION_LONG_HELP,
              INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      err.println(message);
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

      err.println(message);
      err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
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

    // Make sure that at least one filter was provided.  Also get the attribute
    // list at the same time because it may need to be specified in the same
    // way.
    boolean            allUserAttrs        = false;
    boolean            allOperationalAttrs = false;
    //Return objectclass attribute unless analysis of the arguments determines
    //otherwise.
    boolean            includeObjectclassAttrs = true;
    LinkedList<String> attributeNames;
    LinkedList<String> objectClassNames    = new LinkedList<String>();
    LinkedList<String> filterStrings = new LinkedList<String>();
    if (filterFile.isPresent())
    {
      BufferedReader in = null;
      try
      {
        String fileNameValue = filterFile.getValue();
        in = new BufferedReader(new FileReader(fileNameValue));
        String line = null;

        while ((line = in.readLine()) != null)
        {
          if(line.trim().equals(""))
          {
            // ignore empty lines.
            continue;
          }
          filterStrings.add(line);
        }
      } catch(Exception e)
      {
        err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
        return 1;
      }
      finally
      {
        if(in != null)
        {
          try
          {
           in.close();
          } catch (IOException ioe) {}
        }
      }

      ArrayList<String> trailingArguments = argParser.getTrailingArguments();
      if ((trailingArguments == null) || trailingArguments.isEmpty())
      {
        attributeNames = new LinkedList<String>();
      }
      else
      {
        attributeNames = new LinkedList<String>();
        for (String attributeName : trailingArguments)
        {
          String lowerName = toLowerCase(attributeName);
          if (lowerName.equals("*"))
          {
            allUserAttrs = true;
          }
          else if (lowerName.equals("+"))
          {
            allOperationalAttrs = true;
          }
          else if (lowerName.startsWith("@"))
          {
            objectClassNames.add(lowerName.substring(1));
          }
          else
          {
            attributeNames.add(lowerName);
          }
        }
      }
    }
    else
    {
      ArrayList<String> trailingArguments = argParser.getTrailingArguments();
      if ((trailingArguments == null) || trailingArguments.isEmpty())
      {
        Message message = ERR_LDIFSEARCH_NO_FILTER.get();
        err.println(message);
        return 1;
      }
      else
      {
        Iterator<String> iterator = trailingArguments.iterator();

        filterStrings = new LinkedList<String>();
        filterStrings.add(iterator.next());

        attributeNames = new LinkedList<String>();
        while (iterator.hasNext())
        {
          String lowerName = toLowerCase(iterator.next());
          if (lowerName.equals("*"))
          {
            allUserAttrs = true;
          }
          else if (lowerName.equals("+"))
          {
            allOperationalAttrs = true;
          }
          else if (lowerName.startsWith("@"))
          {
            objectClassNames.add(lowerName.substring(1));
          }
          else
          {
            attributeNames.add(lowerName);
          }
        }
      }
    }

    if (attributeNames.isEmpty() && objectClassNames.isEmpty() &&
        (! allOperationalAttrs))
    {
      // This will be true if no attributes were requested, which is effectively
      // all user attributes.  It will also be true if just "*" was included,
      // but the net result will be the same.
      allUserAttrs = true;
    }

    //Determine if objectclass attribute should be returned.
    if(!allUserAttrs) {
      //Single '+', never return objectclass.
      if(allOperationalAttrs && objectClassNames.isEmpty() &&
         attributeNames.isEmpty())
        includeObjectclassAttrs=false;
      //If "objectclass" isn't specified in the attributes to return, then
      //don't include objectclass attribiute.
      if(!attributeNames.isEmpty() && objectClassNames.isEmpty() &&
         !attributeNames.contains("objectclass"))
         includeObjectclassAttrs=false;
    }


    // Bootstrap the Directory Server configuration for use as a client.
    DirectoryServer directoryServer = DirectoryServer.getInstance();

    // If we're to use the configuration then initialize it, along with the
    // schema.
    boolean checkSchema = configFile.isPresent();

    if(initializeServer) {
     DirectoryServer.bootstrapClient();

    if (checkSchema)
    {
      try
      {
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFSEARCH_CANNOT_INITIALIZE_JMX.get(
                String.valueOf(configFile.getValue()),
                e.getMessage());
        err.println(message);
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFSEARCH_CANNOT_INITIALIZE_CONFIG.get(
                String.valueOf(configFile.getValue()),
                e.getMessage());
        err.println(message);
        return 1;
      }

      try
      {
        directoryServer.initializeSchema();
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFSEARCH_CANNOT_INITIALIZE_SCHEMA.get(
                String.valueOf(configFile.getValue()),
                e.getMessage());
        err.println(message);
        return 1;
      }
    }
    }

    // Choose the desired search scope.
    SearchScope searchScope;
    if (scopeString.isPresent())
    {
      String scopeStr = toLowerCase(scopeString.getValue());
      if (scopeStr.equals(SCOPE_STRING_BASE))
      {
        searchScope = SearchScope.BASE_OBJECT;
      }
      else if (scopeStr.equals(SCOPE_STRING_ONE))
      {
        searchScope = SearchScope.SINGLE_LEVEL;
      }
      else if (scopeStr.equals(SCOPE_STRING_SUBORDINATE))
      {
        searchScope = SearchScope.SUBORDINATE_SUBTREE;
      }
      else
      {
        searchScope = SearchScope.WHOLE_SUBTREE;
      }
    }
    else
    {
      searchScope = SearchScope.WHOLE_SUBTREE;
    }


    // Create the list of filters that will be used to process the searches.
    LinkedList<SearchFilter> searchFilters = new LinkedList<SearchFilter>();
    for (String filterString : filterStrings)
    {
      try
      {
        searchFilters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFSEARCH_CANNOT_PARSE_FILTER.get(
                filterString, e.getMessage());
        err.println(message);
        return 1;
      }
    }


    // Transform the attributes to return from strings to attribute types.
    LinkedHashSet<AttributeType> userAttributeTypes =
         new LinkedHashSet<AttributeType>();
    LinkedHashSet<AttributeType> operationalAttributeTypes =
         new LinkedHashSet<AttributeType>();
    for (String attributeName : attributeNames)
    {
      AttributeType t = DirectoryServer.getAttributeType(attributeName, true);
      if (t.isOperational())
      {
        operationalAttributeTypes.add(t);
      }
      else
      {
        userAttributeTypes.add(t);
      }
    }

    for (String objectClassName : objectClassNames)
    {
      ObjectClass c = DirectoryServer.getObjectClass(objectClassName, true);
      for (AttributeType t : c.getRequiredAttributeChain())
      {
        if (t.isOperational())
        {
          operationalAttributeTypes.add(t);
        }
        else
        {
          userAttributeTypes.add(t);
        }
      }

      for (AttributeType t : c.getOptionalAttributeChain())
      {
        if (t.isOperational())
        {
          operationalAttributeTypes.add(t);
        }
        else
        {
          userAttributeTypes.add(t);
        }
      }
    }


    // Set the base DNs for the import config.
    LinkedList<DN> baseDNs = new LinkedList<DN>();
    if (baseDNString.isPresent())
    {
      for (String dnString : baseDNString.getValues())
      {
        try
        {
          baseDNs.add(DN.decode(dnString));
        }
        catch (Exception e)
        {
          Message message = ERR_LDIFSEARCH_CANNOT_PARSE_BASE_DN.get(
                  dnString, e.getMessage());
          err.println(message);
          return 1;
        }
      }
    }
    else
    {
      baseDNs.add(DN.nullDN());
    }


    // Get the time limit in milliseconds.
    long timeLimitMillis;
    try
    {
      if (timeLimit.isPresent())
      {
        timeLimitMillis = 1000L * timeLimit.getIntValue();
      }
      else
      {
        timeLimitMillis = 0;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFSEARCH_CANNOT_PARSE_TIME_LIMIT.get(
              String.valueOf(e));
      err.println(message);
      return 1;
    }


    // Convert the size limit to an integer.
    int sizeLimitValue;
    try
    {
      if (sizeLimit.isPresent())
      {
        sizeLimitValue = sizeLimit.getIntValue();
      }
      else
      {
        sizeLimitValue =0;
      }
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFSEARCH_CANNOT_PARSE_SIZE_LIMIT.get(
              String.valueOf(e));
      err.println(message);
      return 1;
    }


    // Create the LDIF import configuration that will be used to read the source
    // data.
    LDIFImportConfig importConfig;
    if (ldifFile.isPresent())
    {
      importConfig = new LDIFImportConfig(ldifFile.getValues());
    }
    else
    {
      importConfig = new LDIFImportConfig(System.in);
    }


    // Create the LDIF export configuration that will be used to write the
    // matching entries.
    LDIFExportConfig exportConfig;
    if (outputFile.isPresent())
    {
      if (overwriteExisting.isPresent())
      {
        exportConfig = new LDIFExportConfig(outputFile.getValue(),
                                            ExistingFileBehavior.OVERWRITE);
      }
      else
      {
        exportConfig = new LDIFExportConfig(outputFile.getValue(),
                                            ExistingFileBehavior.APPEND);
      }
    }
    else
    {
      exportConfig = new LDIFExportConfig(out);
    }

    exportConfig.setIncludeObjectClasses(includeObjectclassAttrs);
    if (dontWrap.isPresent())
    {
      exportConfig.setWrapColumn(0);
    }
    else
    {
      exportConfig.setWrapColumn(75);
    }


    // Create the LDIF reader/writer from the import/export config.
    LDIFReader reader;
    LDIFWriter writer;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFSEARCH_CANNOT_CREATE_READER.get(
              String.valueOf(e));
      err.println(message);
      return 1;
    }

    try
    {
      writer = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      try
      {
        reader.close();
      } catch (Exception e2) {}

      Message message = ERR_LDIFSEARCH_CANNOT_CREATE_WRITER.get(
              String.valueOf(e));
      err.println(message);
      return 1;
    }


    // Start reading data from the LDIF reader.
    long startTime  = System.currentTimeMillis();
    long stopTime   = startTime + timeLimitMillis;
    long matchCount = 0;
    int  resultCode = LDAPResultCode.SUCCESS;
    while (true)
    {
      // If the time limit has been reached, then stop now.
      if ((timeLimitMillis > 0) && (System.currentTimeMillis() > stopTime))
      {
        resultCode = LDAPResultCode.TIME_LIMIT_EXCEEDED;

        Message message = WARN_LDIFSEARCH_TIME_LIMIT_EXCEEDED.get();
        err.println(message);
        break;
      }


      try
      {
        Entry entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          break;
        }


        // Check to see if the entry has an acceptable base and scope.
        boolean matchesBaseAndScope = false;
        for (DN baseDN : baseDNs)
        {
          if (entry.matchesBaseAndScope(baseDN, searchScope))
          {
            matchesBaseAndScope = true;
            break;
          }
        }

        if (! matchesBaseAndScope)
        {
          continue;
        }


        // Check to see if the entry matches any of the filters.
        boolean matchesFilter = false;
        for (SearchFilter filter : searchFilters)
        {
          if (filter.matchesEntry(entry))
          {
            matchesFilter = true;
            break;
          }
        }

        if (! matchesFilter)
        {
          continue;
        }


        // Prepare the entry to return to the client.
        if (! allUserAttrs)
        {
          Iterator<AttributeType> iterator =
               entry.getUserAttributes().keySet().iterator();
          while (iterator.hasNext())
          {
            if (! userAttributeTypes.contains(iterator.next()))
            {
              iterator.remove();
            }
          }
        }

        if (! allOperationalAttrs)
        {
          Iterator<AttributeType> iterator =
               entry.getOperationalAttributes().keySet().iterator();
          while (iterator.hasNext())
          {
            if (! operationalAttributeTypes.contains(iterator.next()))
            {
              iterator.remove();
            }
          }
        }


        // Write the entry to the client and increase the count.
        // FIXME -- Should we include a comment about which base+filter matched?
        writer.writeEntry(entry);
        writer.flush();

        matchCount++;
        if ((sizeLimitValue > 0) && (matchCount >= sizeLimitValue))
        {
          resultCode = LDAPResultCode.SIZE_LIMIT_EXCEEDED;

          Message message = WARN_LDIFSEARCH_SIZE_LIMIT_EXCEEDED.get();
          err.println(message);
          break;
        }
      }
      catch (LDIFException le)
      {
        if (le.canContinueReading())
        {
          Message message = ERR_LDIFSEARCH_CANNOT_READ_ENTRY_RECOVERABLE.get(
                  le.getMessage());
          err.println(message);
        }
        else
        {
          Message message = ERR_LDIFSEARCH_CANNOT_READ_ENTRY_FATAL.get(
                  le.getMessage());
          err.println(message);
          resultCode = LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
          break;
        }
      }
      catch (Exception e)
      {
        Message message = ERR_LDIFSEARCH_ERROR_DURING_PROCESSING.get(
                String.valueOf(e));
        err.println(message);
        resultCode = LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
        break;
      }
    }


    // Close the reader and writer.
    try
    {
      reader.close();
    } catch (Exception e) {}

    try
    {
      writer.close();
    } catch (Exception e) {}


    return resultCode;
  }
}


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
 *      Portions Copyright 2012 ForgeRock AS.
 */
package org.opends.server.tools;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.messages.Message;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.controls.*;
import org.opends.server.protocols.ldap.*;
import org.opends.server.util.Base64;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.MultiChoiceArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;


/**
 * This class provides a tool that can be used to issue search requests to the
 * Directory Server.
 */
public class LDAPSearch
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPSearch";



  // The set of response controls for the search.
  private List<Control> responseControls;

  // The message ID counter to use for requests.
  private final AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private final PrintStream err;

  // The print stream to use for standard output.
  private final PrintStream out;



  /**
   * Constructor for the LDAPSearch object.
   *
   * @param  nextMessageID  The message ID counter to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPSearch(AtomicInteger nextMessageID, PrintStream out,
                    PrintStream err)
  {
    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
    responseControls   = new ArrayList<Control>();
  }


  /**
   * Execute the search based on the specified input parameters.
   *
   * @param connection     The connection to use for the search.
   * @param baseDN         The base DN for the search request.
   * @param filters        The filters to use for the results.
   * @param attributes     The attributes to return in the results.
   * @param searchOptions  The constraints for the search.
   * @param wrapColumn     The column at which to wrap long lines.
   *
   * @return  The number of matching entries returned by the server.  If there
   *          were multiple search filters provided, then this will be the
   *          total number of matching entries for all searches.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public int executeSearch(LDAPConnection connection, String baseDN,
                           ArrayList<LDAPFilter> filters,
                           LinkedHashSet<String> attributes,
                           LDAPSearchOptions searchOptions,
                           int wrapColumn )
         throws IOException, LDAPException
  {
    int matchingEntries = 0;

    for (LDAPFilter filter: filters)
    {
      ByteString asn1OctetStr = ByteString.valueOf(baseDN);

      SearchRequestProtocolOp protocolOp =
        new SearchRequestProtocolOp(asn1OctetStr,
                                    searchOptions.getSearchScope(),
                                    searchOptions.getDereferencePolicy(),
                                    searchOptions.getSizeLimit(),
                                    searchOptions.getTimeLimit(),
                                    searchOptions.getTypesOnly(),
                                    filter, attributes);
      if(!searchOptions.showOperations())
      {
        try
        {
          boolean typesOnly = searchOptions.getTypesOnly();
          LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                protocolOp,
                                                searchOptions.getControls());
          connection.getLDAPWriter().writeMessage(message);

          byte opType;
          do
          {
            int resultCode = 0;
            Message errorMessage = null;
            DN matchedDN = null;
            LDAPMessage responseMessage =
                 connection.getLDAPReader().readMessage();
            responseControls = responseMessage.getControls();


            opType = responseMessage.getProtocolOpType();
            switch(opType)
            {
              case OP_TYPE_SEARCH_RESULT_ENTRY:
                for (Control c : responseControls)
                {
                  if (c.getOID().equals(OID_ENTRY_CHANGE_NOTIFICATION))
                  {
                    try
                    {
                      EntryChangeNotificationControl ecn =
                        EntryChangeNotificationControl.DECODER
                        .decode(c.isCritical(), ((LDAPControl) c).getValue());

                      out.println(INFO_LDAPSEARCH_PSEARCH_CHANGE_TYPE.get(
                              ecn.getChangeType().toString()));
                      DN previousDN = ecn.getPreviousDN();
                      if (previousDN != null)
                      {

                        out.println(INFO_LDAPSEARCH_PSEARCH_PREVIOUS_DN.get(
                                previousDN.toString()));
                      }
                    } catch (Exception e) {}
                  }
                  else if (c.getOID().equals(OID_ACCOUNT_USABLE_CONTROL))
                  {
                    try
                    {
                      AccountUsableResponseControl acrc =
                        AccountUsableResponseControl.DECODER
                        .decode(c.isCritical(), ((LDAPControl) c).getValue());

                      out.println(INFO_LDAPSEARCH_ACCTUSABLE_HEADER.get());
                      if (acrc.isUsable())
                      {

                        out.println(INFO_LDAPSEARCH_ACCTUSABLE_IS_USABLE.get());
                        if (acrc.getSecondsBeforeExpiration() > 0)
                        {
                          int timeToExp = acrc.getSecondsBeforeExpiration();
                          Message timeToExpStr = secondsToTimeString(timeToExp);

                          out.println(
                               INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION.
                                       get(timeToExpStr));
                        }
                      }
                      else
                      {

                        out.println(
                                INFO_LDAPSEARCH_ACCTUSABLE_NOT_USABLE.get());
                        if (acrc.isInactive())
                        {

                          out.println(
                               INFO_LDAPSEARCH_ACCTUSABLE_ACCT_INACTIVE.get());
                        }
                        if (acrc.isReset())
                        {
                          out.println(
                                  INFO_LDAPSEARCH_ACCTUSABLE_PW_RESET.get());
                        }
                        if (acrc.isExpired())
                        {

                          out.println(
                                  INFO_LDAPSEARCH_ACCTUSABLE_PW_EXPIRED.get());

                          if (acrc.getRemainingGraceLogins() > 0)
                          {

                            out.println(
                                    INFO_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE
                                         .get(acrc.getRemainingGraceLogins()));
                          }
                        }
                        if (acrc.isLocked())
                        {

                          out.println(INFO_LDAPSEARCH_ACCTUSABLE_LOCKED.get());
                          if (acrc.getSecondsBeforeUnlock() > 0)
                          {
                            int timeToUnlock = acrc.getSecondsBeforeUnlock();
                            Message timeToUnlockStr =
                                        secondsToTimeString(timeToUnlock);

                            out.println(
                                    INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK
                                            .get(timeToUnlockStr));
                          }
                        }
                      }
                    } catch (Exception e) {}
                  }
                  else if (c.getOID().equals(OID_ECL_COOKIE_EXCHANGE_CONTROL))
                  {
                    try
                    {
                      EntryChangelogNotificationControl ctrl =
                        EntryChangelogNotificationControl.DECODER.decode(
                          c.isCritical(), ((LDAPControl) c).getValue());
                      out.println(
                          INFO_LDAPSEARCH_PUBLIC_CHANGELOG_COOKIE_EXC.get(
                            c.getOID(), ctrl.getCookie()));
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

                SearchResultEntryProtocolOp searchEntryOp =
                     responseMessage.getSearchResultEntryProtocolOp();
                StringBuilder sb = new StringBuilder();
                toLDIF(searchEntryOp, sb, wrapColumn, typesOnly);
                out.print(sb.toString());
                matchingEntries++;
                break;

              case OP_TYPE_SEARCH_RESULT_REFERENCE:
                SearchResultReferenceProtocolOp searchRefOp =
                     responseMessage.getSearchResultReferenceProtocolOp();
                out.println(searchRefOp.toString());
                break;

              case OP_TYPE_SEARCH_RESULT_DONE:
                SearchResultDoneProtocolOp searchOp =
                     responseMessage.getSearchResultDoneProtocolOp();
                resultCode = searchOp.getResultCode();
                errorMessage = searchOp.getErrorMessage();
                matchedDN = searchOp.getMatchedDN();

                for (Control c : responseMessage.getControls())
                {
                  if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
                  {
                    try
                    {
                      ServerSideSortResponseControl sortResponse =
                        ServerSideSortResponseControl.DECODER
                        .decode(c.isCritical(), ((LDAPControl) c).getValue());
                      int rc = sortResponse.getResultCode();
                      if (rc != LDAPResultCode.SUCCESS)
                      {
                        Message msg   = WARN_LDAPSEARCH_SORT_ERROR.get(
                                LDAPResultCode.toString(rc));
                        err.println(msg);
                      }
                    }
                    catch (Exception e)
                    {
                      Message msg   =
                              WARN_LDAPSEARCH_CANNOT_DECODE_SORT_RESPONSE.get(
                                      getExceptionMessage(e));
                      err.println(msg);
                    }
                  }
                  else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
                  {
                    try
                    {
                      VLVResponseControl vlvResponse =
                          VLVResponseControl.DECODER.decode(c.isCritical(),
                              ((LDAPControl) c).getValue());
                      int rc = vlvResponse.getVLVResultCode();
                      if (rc == LDAPResultCode.SUCCESS)
                      {
                        Message msg = INFO_LDAPSEARCH_VLV_TARGET_OFFSET.get(
                                vlvResponse.getTargetPosition());
                        out.println(msg);


                        msg = INFO_LDAPSEARCH_VLV_CONTENT_COUNT.get(
                                vlvResponse.getContentCount());
                        out.println(msg);
                      }
                      else
                      {
                        Message msg = WARN_LDAPSEARCH_VLV_ERROR.get(
                                LDAPResultCode.toString(rc));
                        err.println(msg);
                      }
                    }
                    catch (Exception e)
                    {
                      Message msg   =
                              WARN_LDAPSEARCH_CANNOT_DECODE_VLV_RESPONSE.get(
                                      getExceptionMessage(e));
                      err.println(msg);
                    }
                  }
                }

                break;
              default:
                if(opType == OP_TYPE_EXTENDED_RESPONSE)
                {
                  ExtendedResponseProtocolOp op =
                    responseMessage.getExtendedResponseProtocolOp();
                  if(op.getOID().equals(OID_NOTICE_OF_DISCONNECTION))
                  {
                    resultCode = op.getResultCode();
                    errorMessage = op.getErrorMessage();
                    matchedDN = op.getMatchedDN();
                    break;
                  }
                }
                // FIXME - throw exception?
                Message msg = INFO_SEARCH_OPERATION_INVALID_PROTOCOL.get(
                        String.valueOf(opType));
                err.println(wrapText(msg, MAX_LINE_WIDTH));
            }

            if(resultCode != SUCCESS)
            {
              Message msg = INFO_OPERATION_FAILED.get("SEARCH");
              throw new LDAPException(resultCode, errorMessage, msg,
                                      matchedDN, null);
            }
            else if (errorMessage != null)
            {
              out.println();
              out.println(wrapText(errorMessage, MAX_LINE_WIDTH));
            }

          } while(opType != OP_TYPE_SEARCH_RESULT_DONE);

        } catch(ASN1Exception ae)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ae);
          }
          throw new IOException(ae.getMessage());
        }
      }
    }

    if (searchOptions.countMatchingEntries())
    {
      Message message =
              INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT.get(matchingEntries);
      out.println(message);
      out.println();
    }
    return matchingEntries;
  }

  /**
   * Appends an LDIF representation of the entry to the provided buffer.
   *
   * @param  entry       The entry to be written as LDIF.
   * @param  buffer      The buffer to which the entry should be appended.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   * @param  typesOnly   Indicates whether to include only attribute types
   *                     without values.
   */
  public void toLDIF(SearchResultEntryProtocolOp entry, StringBuilder buffer,
                     int wrapColumn, boolean typesOnly)
  {
    // Add the DN to the buffer.
    String dnString = entry.getDN().toString();
    int    colsRemaining;
    if (needsBase64Encoding(dnString))
    {
      dnString = Base64.encode(getBytes(dnString));
      buffer.append("dn:: ");

      colsRemaining = wrapColumn - 5;
    }
    else
    {
      buffer.append("dn: ");

      colsRemaining = wrapColumn - 4;
    }

    int dnLength = dnString.length();
    if ((dnLength <= colsRemaining) || (colsRemaining <= 0))
    {
      buffer.append(dnString);
      buffer.append(EOL);
    }
    else
    {
      buffer.append(dnString.substring(0, colsRemaining));
      buffer.append(EOL);

      int startPos = colsRemaining;
      while ((dnLength - startPos) > (wrapColumn - 1))
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos, (startPos+wrapColumn-1)));

        buffer.append(EOL);

        startPos += (wrapColumn-1);
      }

      if (startPos < dnLength)
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos));
        buffer.append(EOL);
      }
    }


    LinkedList<LDAPAttribute> attributes = entry.getAttributes();
    // Add the attributes to the buffer.
    for (LDAPAttribute a : attributes)
    {
      String name       = a.getAttributeType();
      int    nameLength = name.length();


      if(typesOnly)
      {
          buffer.append(name);
          buffer.append(EOL);
      } else
      {
        for (ByteString v : a.getValues())
        {
          String valueString;
          if (needsBase64Encoding(v))
          {
            valueString = Base64.encode(v);
            buffer.append(name);
            buffer.append(":: ");

            colsRemaining = wrapColumn - nameLength - 3;
          } else
          {
            valueString = v.toString();
            buffer.append(name);
            buffer.append(": ");

            colsRemaining = wrapColumn - nameLength - 2;
          }

          int valueLength = valueString.length();
          if ((valueLength <= colsRemaining) || (colsRemaining <= 0))
          {
            buffer.append(valueString);
            buffer.append(EOL);
          } else
          {
            buffer.append(valueString.substring(0, colsRemaining));
            buffer.append(EOL);

            int startPos = colsRemaining;
            while ((valueLength - startPos) > (wrapColumn - 1))
            {
              buffer.append(" ");
              buffer.append(valueString.substring(startPos,
                                                (startPos+wrapColumn-1)));
              buffer.append(EOL);

              startPos += (wrapColumn-1);
            }

            if (startPos < valueLength)
            {
              buffer.append(" ");
              buffer.append(valueString.substring(startPos));
              buffer.append(EOL);
            }
          }
        }
      }
    }


    // Make sure to add an extra blank line to ensure that there will be one
    // between this entry and the next.
    buffer.append(EOL);
  }

  /**
   * Retrieves the set of response controls included in the last search result
   * done message.
   *
   * @return  The set of response controls included in the last search result
   *          done message.
   */
  public List<Control> getResponseControls()
  {
    return responseControls;
  }

  /**
   * The main method for LDAPSearch tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainSearch(args, true, false, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapsearch tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainSearch(String[] args)
  {
    return mainSearch(args, true, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapsearch tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return The error code.
   */
  public static int mainSearch(String[] args, boolean initializeServer,
                               OutputStream outStream, OutputStream errStream)
  {
    return mainSearch(args, initializeServer, true, outStream, errStream);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapsearch tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  returnMatchingEntries whether when the option --countEntries is
   *                           specified, the number of matching entries should
   *                           be returned or not.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return The error code.
   */

  public static int mainSearch(String[] args, boolean initializeServer,
      boolean returnMatchingEntries, OutputStream outStream,
      OutputStream errStream)
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

    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    LDAPSearchOptions searchOptions = new LDAPSearchOptions();
    LDAPConnection connection = null;
    ArrayList<LDAPFilter> filters = new ArrayList<LDAPFilter>();
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();

    BooleanArgument   continueOnError          = null;
    BooleanArgument   countEntries             = null;
    BooleanArgument   dontWrap                 = null;
    BooleanArgument   noop                     = null;
    BooleanArgument   reportAuthzID            = null;
    BooleanArgument   saslExternal             = null;
    BooleanArgument   showUsage                = null;
    BooleanArgument   trustAll                 = null;
    BooleanArgument   usePasswordPolicyControl = null;
    BooleanArgument   useSSL                   = null;
    BooleanArgument   startTLS                 = null;
    BooleanArgument   typesOnly                = null;
    BooleanArgument   verbose                  = null;
    FileBasedArgument bindPasswordFile         = null;
    FileBasedArgument keyStorePasswordFile     = null;
    FileBasedArgument trustStorePasswordFile   = null;
    IntegerArgument   port                     = null;
    IntegerArgument   simplePageSize           = null;
    IntegerArgument   sizeLimit                = null;
    IntegerArgument   timeLimit                = null;
    IntegerArgument   version                  = null;
    StringArgument    assertionFilter          = null;
    StringArgument    baseDN                   = null;
    StringArgument    bindDN                   = null;
    StringArgument    bindPassword             = null;
    StringArgument    certNickname             = null;
    StringArgument    controlStr               = null;
    StringArgument    dereferencePolicy        = null;
    StringArgument    encodingStr              = null;
    StringArgument    filename                 = null;
    StringArgument    hostName                 = null;
    StringArgument    keyStorePath             = null;
    StringArgument    keyStorePassword         = null;
    StringArgument    matchedValuesFilter      = null;
    StringArgument    proxyAuthzID             = null;
    StringArgument    pSearchInfo              = null;
    StringArgument    saslOptions              = null;
    MultiChoiceArgument searchScope              = null;
    StringArgument    sortOrder                = null;
    StringArgument    trustStorePath           = null;
    StringArgument    trustStorePassword       = null;
    IntegerArgument   connectTimeout           = null;
    StringArgument    vlvDescriptor            = null;
    StringArgument    effectiveRightsUser      = null;
    StringArgument    effectiveRightsAttrs     = null;
    StringArgument    propertiesFileArgument   = null;
    BooleanArgument   noPropertiesFileArgument = null;
    BooleanArgument   subEntriesArgument       = null;


    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_LDAPSEARCH_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false, true, 0, 0,
                                                  "[filter] [attributes ...]");

    try
    {
      propertiesFileArgument = new StringArgument("propertiesFilePath",
          null, OPTION_LONG_PROP_FILE_PATH,
          false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      hostName = new StringArgument("host", OPTION_SHORT_HOST,
                                    OPTION_LONG_HOST, false, false, true,
                                    INFO_HOST_PLACEHOLDER.get(), "localhost",
                                    null,
                                    INFO_DESCRIPTION_HOST.get());
      hostName.setPropertyName(OPTION_LONG_HOST);
      argParser.addArgument(hostName);

      port = new IntegerArgument("port", OPTION_SHORT_PORT,
                                 OPTION_LONG_PORT, false, false, true,
                                 INFO_PORT_PLACEHOLDER.get(), 389, null,
                                 true, 1, true, 65535,
                                 INFO_DESCRIPTION_PORT.get());
      port.setPropertyName(OPTION_LONG_PORT);
      argParser.addArgument(port);

      useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   INFO_DESCRIPTION_USE_SSL.get());
      useSSL.setPropertyName(OPTION_LONG_USE_SSL);
      argParser.addArgument(useSSL);

      startTLS = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
                                    OPTION_LONG_START_TLS,
                                    INFO_DESCRIPTION_START_TLS.get());
      startTLS.setPropertyName(OPTION_LONG_START_TLS);
      argParser.addArgument(startTLS);

      bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  INFO_BINDDN_PLACEHOLDER.get(), null, null,
                                  INFO_DESCRIPTION_BINDDN.get());
      bindDN.setPropertyName(OPTION_LONG_BINDDN);
      argParser.addArgument(bindDN);

      bindPassword = new StringArgument("bindPassword", OPTION_SHORT_BINDPWD,
                                        OPTION_LONG_BINDPWD,
                                        false, false, true,
                                        INFO_BINDPWD_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_DESCRIPTION_BINDPASSWORD.get());
      bindPassword.setPropertyName(OPTION_LONG_BINDPWD);
      argParser.addArgument(bindPassword);

      bindPasswordFile =
           new FileBasedArgument("bindPasswordFile", OPTION_SHORT_BINDPWD_FILE,
                                 OPTION_LONG_BINDPWD_FILE,
                                 false, false,
                                 INFO_BINDPWD_FILE_PLACEHOLDER.get(), null,
                                 null, INFO_DESCRIPTION_BINDPASSWORDFILE.get());
      bindPasswordFile.setPropertyName(OPTION_LONG_BINDPWD_FILE);
      argParser.addArgument(bindPasswordFile);

      baseDN = new StringArgument("baseDN", OPTION_SHORT_BASEDN,
                                  OPTION_LONG_BASEDN, true, false, true,
                                  INFO_BASEDN_PLACEHOLDER.get(), null, null,
                                  INFO_SEARCH_DESCRIPTION_BASEDN.get());
      baseDN.setPropertyName(OPTION_LONG_BASEDN);
      argParser.addArgument(baseDN);

      HashSet<String> allowedScopes = new HashSet<String>();
      allowedScopes.add("base");
      allowedScopes.add("one");
      allowedScopes.add("sub");
      allowedScopes.add("subordinate");
      searchScope = new MultiChoiceArgument(
              "searchScope", 's', "searchScope", false,
              true, INFO_SEARCH_SCOPE_PLACEHOLDER.get(), allowedScopes,
              false,
              INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get());
      searchScope.setPropertyName("searchScope");
      searchScope.setDefaultValue("sub");
      argParser.addArgument(searchScope);

      filename = new StringArgument("filename", OPTION_SHORT_FILENAME,
                                    OPTION_LONG_FILENAME, false, false,
                                    true, INFO_FILE_PLACEHOLDER.get(), null,
                                    null,
                                    INFO_SEARCH_DESCRIPTION_FILENAME.get());
      searchScope.setPropertyName(OPTION_LONG_FILENAME);
      argParser.addArgument(filename);

      saslExternal = new BooleanArgument(
              "useSASLExternal", 'r',
              "useSASLExternal",
              INFO_DESCRIPTION_USE_SASL_EXTERNAL.get());
      saslExternal.setPropertyName("useSASLExternal");
      argParser.addArgument(saslExternal);

      saslOptions = new StringArgument("saslOption", OPTION_SHORT_SASLOPTION,
                                       OPTION_LONG_SASLOPTION, false,
                                       true, true,
                                       INFO_SASL_OPTION_PLACEHOLDER.get(), null,
                                       null,
                                       INFO_DESCRIPTION_SASL_PROPERTIES.get());
      saslOptions.setPropertyName(OPTION_LONG_SASLOPTION);
      argParser.addArgument(saslOptions);

      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                    INFO_DESCRIPTION_TRUSTALL.get());
      trustAll.setPropertyName("trustAll");
      argParser.addArgument(trustAll);

      keyStorePath = new StringArgument("keyStorePath",
                                  OPTION_SHORT_KEYSTOREPATH,
                                  OPTION_LONG_KEYSTOREPATH, false, false, true,
                                  INFO_KEYSTOREPATH_PLACEHOLDER.get(), null,
                                  null,
                                  INFO_DESCRIPTION_KEYSTOREPATH.get());
      keyStorePath.setPropertyName(OPTION_LONG_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);

      keyStorePassword = new StringArgument("keyStorePassword",
                                  OPTION_SHORT_KEYSTORE_PWD,
                                  OPTION_LONG_KEYSTORE_PWD, false, false,
                                  true, INFO_KEYSTORE_PWD_PLACEHOLDER.get(),
                                  null, null,
                                  INFO_DESCRIPTION_KEYSTOREPASSWORD.get());
      keyStorePassword.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
      argParser.addArgument(keyStorePassword);

      keyStorePasswordFile =
           new FileBasedArgument("keystorepasswordfile",
                                 OPTION_SHORT_KEYSTORE_PWD_FILE,
                                 OPTION_LONG_KEYSTORE_PWD_FILE,
                                 false, false,
                                 INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
      keyStorePasswordFile.setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
      argParser.addArgument(keyStorePasswordFile);

      certNickname = new StringArgument(
              "certnickname", OPTION_SHORT_CERT_NICKNAME,
              OPTION_LONG_CERT_NICKNAME,
              false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      certNickname.setPropertyName(OPTION_LONG_CERT_NICKNAME);
      argParser.addArgument(certNickname);

      trustStorePath = new StringArgument("trustStorePath",
                                  OPTION_SHORT_TRUSTSTOREPATH,
                                  OPTION_LONG_TRUSTSTOREPATH,
                                  false, false, true,
                                  INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null,
                                  null,
                                  INFO_DESCRIPTION_TRUSTSTOREPATH.get());
      trustStorePath.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);

      trustStorePassword =
           new StringArgument("trustStorePassword", null,
                              OPTION_LONG_TRUSTSTORE_PWD,
                              false, false, true,
                              INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(),
                              null,
                              null, INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
      trustStorePassword.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
      argParser.addArgument(trustStorePassword);

      trustStorePasswordFile =
           new FileBasedArgument(
                   "truststorepasswordfile",
                   OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                   OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                   INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null, null,
                   INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
      trustStorePasswordFile.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
      argParser.addArgument(trustStorePasswordFile);

      proxyAuthzID = new StringArgument("proxy_authzid",
                                        OPTION_SHORT_PROXYAUTHID,
                                        OPTION_LONG_PROXYAUTHID, false,
                                        false, true,
                                        INFO_PROXYAUTHID_PLACEHOLDER.get(),
                                        null, null,
                                        INFO_DESCRIPTION_PROXY_AUTHZID.get());
      proxyAuthzID.setPropertyName(OPTION_LONG_PROXYAUTHID);
      argParser.addArgument(proxyAuthzID);

      reportAuthzID = new BooleanArgument(
              "reportauthzid", 'E', OPTION_LONG_REPORT_AUTHZ_ID,
              INFO_DESCRIPTION_REPORT_AUTHZID.get());
      reportAuthzID.setPropertyName(OPTION_LONG_REPORT_AUTHZ_ID);
      argParser.addArgument(reportAuthzID);

      usePasswordPolicyControl = new BooleanArgument(
              "usepwpolicycontrol", null,
              OPTION_LONG_USE_PW_POLICY_CTL,
              INFO_DESCRIPTION_USE_PWP_CONTROL.get());
      usePasswordPolicyControl.setPropertyName(OPTION_LONG_USE_PW_POLICY_CTL);
      argParser.addArgument(usePasswordPolicyControl);

      pSearchInfo = new StringArgument("psearchinfo", 'C', "persistentSearch",
                             false, false, true,
                             INFO_PSEARCH_PLACEHOLDER.get(),
                              null, null, INFO_DESCRIPTION_PSEARCH_INFO.get());
      pSearchInfo.setPropertyName("persistentSearch");
      argParser.addArgument(pSearchInfo);

      simplePageSize = new IntegerArgument(
              "simplepagesize", null,
              "simplePageSize", false, false, true,
              INFO_NUM_ENTRIES_PLACEHOLDER.get(), 1000, null, true, 1,
              false, 0,
              INFO_DESCRIPTION_SIMPLE_PAGE_SIZE.get());
      simplePageSize.setPropertyName("simplePageSize");
      argParser.addArgument(simplePageSize);

      assertionFilter = new StringArgument(
              "assertionfilter", null,
              OPTION_LONG_ASSERTION_FILE,
              false, false,
              true, INFO_ASSERTION_FILTER_PLACEHOLDER.get(),
              null, null,
              INFO_DESCRIPTION_ASSERTION_FILTER.get());
      assertionFilter.setPropertyName(OPTION_LONG_ASSERTION_FILE);
      argParser.addArgument(assertionFilter);

      matchedValuesFilter = new StringArgument(
              "matchedvalues", null,
              "matchedValuesFilter", false, true, true,
              INFO_FILTER_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_MATCHED_VALUES_FILTER.get());
      matchedValuesFilter.setPropertyName("matchedValuesFilter");
      argParser.addArgument(matchedValuesFilter);

      sortOrder = new StringArgument(
              "sortorder", 'S', "sortOrder", false,
              false, true, INFO_SORT_ORDER_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_SORT_ORDER.get());
      sortOrder.setPropertyName("sortOrder");
      argParser.addArgument(sortOrder);

      vlvDescriptor =
           new StringArgument(
                   "vlvdescriptor", 'G', "virtualListView", false,
                   false, true,
                   INFO_VLV_PLACEHOLDER.get(),
                   null, null, INFO_DESCRIPTION_VLV.get());
      vlvDescriptor.setPropertyName("virtualListView");
      argParser.addArgument(vlvDescriptor);

      controlStr =
           new StringArgument("control", 'J', "control", false, true, true,
                    INFO_LDAP_CONTROL_PLACEHOLDER.get(),
                    null, null, INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);

      subEntriesArgument = new BooleanArgument("subEntries",
              OPTION_SHORT_SUBENTRIES, OPTION_LONG_SUBENTRIES,
              INFO_DESCRIPTION_SUBENTRIES.get());
      useSSL.setPropertyName(OPTION_LONG_SUBENTRIES);
      argParser.addArgument(subEntriesArgument);

      effectiveRightsUser =
              new StringArgument("effectiveRightsUser",
                      OPTION_SHORT_EFFECTIVERIGHTSUSER,
                      OPTION_LONG_EFFECTIVERIGHTSUSER, false, false, true,
                      INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
                      INFO_DESCRIPTION_EFFECTIVERIGHTS_USER.get( ));
      effectiveRightsUser.setPropertyName(OPTION_LONG_EFFECTIVERIGHTSUSER);
      argParser.addArgument(effectiveRightsUser);

      effectiveRightsAttrs =
              new StringArgument("effectiveRightsAttrs",
                      OPTION_SHORT_EFFECTIVERIGHTSATTR,
                      OPTION_LONG_EFFECTIVERIGHTSATTR, false, true, true,
                      INFO_ATTRIBUTE_PLACEHOLDER.get(), null, null,
                      INFO_DESCRIPTION_EFFECTIVERIGHTS_ATTR.get( ));
      effectiveRightsAttrs.setPropertyName(OPTION_LONG_EFFECTIVERIGHTSATTR);
      argParser.addArgument(effectiveRightsAttrs);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                                    OPTION_LONG_PROTOCOL_VERSION, false, false,
                                    true,
                                    INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3,
                                    null, INFO_DESCRIPTION_VERSION.get());
      version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
      argParser.addArgument(version);

      int defaultTimeout = ConnectionUtils.getDefaultLDAPTimeout();
      connectTimeout = new IntegerArgument(OPTION_LONG_CONNECT_TIMEOUT,
          null, OPTION_LONG_CONNECT_TIMEOUT,
          false, false, true, INFO_TIMEOUT_PLACEHOLDER.get(),
          defaultTimeout, null,
          true, 0, false, Integer.MAX_VALUE,
          INFO_DESCRIPTION_CONNECTION_TIMEOUT.get());
      connectTimeout.setPropertyName(OPTION_LONG_CONNECT_TIMEOUT);
      argParser.addArgument(connectTimeout);

      encodingStr = new StringArgument("encoding", 'i', "encoding", false,
                                       false, true,
                                       INFO_ENCODING_PLACEHOLDER.get(), null,
                                       null,
                                       INFO_DESCRIPTION_ENCODING.get());
      encodingStr.setPropertyName("encoding");
      argParser.addArgument(encodingStr);

      dereferencePolicy =
           new StringArgument("derefpolicy", 'a', "dereferencePolicy", false,
                              false, true,
                              INFO_DEREFERENCE_POLICE_PLACEHOLDER.get(), null,
                              null,
                              INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY.get());
      dereferencePolicy.setPropertyName("dereferencePolicy");
      argParser.addArgument(dereferencePolicy);

      typesOnly = new BooleanArgument("typesOnly", 'A', "typesOnly",
                                      INFO_DESCRIPTION_TYPES_ONLY.get());
      typesOnly.setPropertyName("typesOnly");
      argParser.addArgument(typesOnly);

      sizeLimit = new IntegerArgument("sizeLimit", 'z', "sizeLimit", false,
                                      false, true,
                                      INFO_SIZE_LIMIT_PLACEHOLDER.get(), 0,
                                      null,
                                      INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get());
      sizeLimit.setPropertyName("sizeLimit");
      argParser.addArgument(sizeLimit);

      timeLimit = new IntegerArgument("timeLimit", 'l', "timeLimit", false,
                                      false, true,
                                      INFO_TIME_LIMIT_PLACEHOLDER.get(), 0,
                                      null,
                                      INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get());
      timeLimit.setPropertyName("timeLimit");
      argParser.addArgument(timeLimit);

      dontWrap = new BooleanArgument("dontwrap", 'T',
                                     "dontWrap",
                                     INFO_DESCRIPTION_DONT_WRAP.get());
      dontWrap.setPropertyName("dontWrap");
      argParser.addArgument(dontWrap);

      countEntries = new BooleanArgument("countentries", null, "countEntries",
                                         INFO_DESCRIPTION_COUNT_ENTRIES.get());
      countEntries.setPropertyName("countEntries");
      argParser.addArgument(countEntries);

      continueOnError =
           new BooleanArgument("continueOnError", 'c', "continueOnError",
                               INFO_DESCRIPTION_CONTINUE_ON_ERROR.get());
      continueOnError.setPropertyName("continueOnError");
      argParser.addArgument(continueOnError);

      noop = new BooleanArgument("noop", OPTION_SHORT_DRYRUN,
          OPTION_LONG_DRYRUN, INFO_DESCRIPTION_NOOP.get());
      noop.setPropertyName(OPTION_LONG_DRYRUN);
      argParser.addArgument(noop);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
                                    OPTION_LONG_HELP,
                                    INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {

      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    ArrayList<String> filterAndAttributeStrings =
      argParser.getTrailingArguments();
    if(filterAndAttributeStrings.size() > 0)
    {
      // the list of trailing arguments should be structured as follow:
      // - If a filter file is present, trailing arguments are considered
      //   as attributes
      // - If filter file is not present, the first trailing argument is
      // considered the filter, the other as attributes.
      if (! filename.isPresent())
      {
        String filterString = filterAndAttributeStrings.remove(0);

        try
        {
          filters.add(LDAPFilter.decode(filterString));
        } catch (LDAPException le)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, le);
          }
          err.println(wrapText(le.getMessage(), MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
      // The rest are attributes
      for(String s : filterAndAttributeStrings)
      {
        attributes.add(s);
      }

    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      Message message =
              ERR_TOOL_CONFLICTING_ARGS.get(
                      bindPassword.getLongIdentifier(),
                      bindPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (useSSL.isPresent() && startTLS.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              useSSL.getLongIdentifier(),
              startTLS.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (keyStorePassword.isPresent() && keyStorePasswordFile.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              keyStorePassword.getLongIdentifier(),
              keyStorePasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (trustStorePassword.isPresent() && trustStorePasswordFile.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              trustStorePassword.getLongIdentifier(),
              trustStorePasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // Read the LDAP version number.
    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {

        err.println(wrapText(ERR_DESCRIPTION_INVALID_VERSION.get(
                String.valueOf(versionNumber)), MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // Indicate whether we should report the authorization ID and/or use the
    // password policy control.
    connectionOptions.setReportAuthzID(reportAuthzID.isPresent());
    connectionOptions.setUsePasswordPolicyControl(
         usePasswordPolicyControl.isPresent());


    String baseDNValue = baseDN.getValue();
    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue = bindPassword.getValue();
    if(bindPasswordValue != null && bindPasswordValue.equals("-")  ||
      (!bindPasswordFile.isPresent()  &&
      (bindDNValue != null && bindPasswordValue == null)))
    {
      // read the password from the stdin.
      try
      {
        out.print(INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDNValue));
        char[] pwChars = PasswordReader.readPassword();
        bindPasswordValue = new String(pwChars);
        //As per rfc 4513(section-5.1.2) a client should avoid sending
        //an empty password to the server.
        while(pwChars.length ==0)
        {
          err.println(wrapText(
                  INFO_LDAPAUTH_NON_EMPTY_PASSWORD.get(),
                  MAX_LINE_WIDTH));
          out.print(INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDNValue));
          pwChars = PasswordReader.readPassword();
        }
        bindPasswordValue = new String(pwChars);
      } catch(Exception ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        err.println(wrapText(ex.getMessage(), MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }
    else if(bindPasswordValue == null)
    {
      // Read from file if it exists.
      bindPasswordValue = bindPasswordFile.getValue();
    }

    String keyStorePathValue = keyStorePath.getValue();
    String trustStorePathValue = trustStorePath.getValue();

    String keyStorePasswordValue = null;
    if (keyStorePassword.isPresent())
    {
      keyStorePasswordValue = keyStorePassword.getValue();
    }
    else if (keyStorePasswordFile.isPresent())
    {
      keyStorePasswordValue = keyStorePasswordFile.getValue();
    }

    String trustStorePasswordValue = null;
    if (trustStorePassword.isPresent())
    {
      trustStorePasswordValue = trustStorePassword.getValue();
    }
    else if (trustStorePasswordFile.isPresent())
    {
      trustStorePasswordValue = trustStorePasswordFile.getValue();
    }

    searchOptions.setTypesOnly(typesOnly.isPresent());
    searchOptions.setShowOperations(noop.isPresent());
    searchOptions.setVerbose(verbose.isPresent());
    searchOptions.setContinueOnError(continueOnError.isPresent());
    searchOptions.setEncoding(encodingStr.getValue());
    searchOptions.setCountMatchingEntries(countEntries.isPresent());
    try
    {
      searchOptions.setTimeLimit(timeLimit.getIntValue());
      searchOptions.setSizeLimit(sizeLimit.getIntValue());
    } catch(ArgumentException ex1)
    {
      err.println(wrapText(ex1.getMessage(), MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }
    boolean val = searchOptions.setSearchScope(searchScope.getValue(), err);
    if(val == false)
    {
      return CLIENT_SIDE_PARAM_ERROR;
    }
    val = searchOptions.setDereferencePolicy(dereferencePolicy.getValue(), err);
    if(val == false)
    {
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if(controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        Control ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          Message message = ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(argParser.getUsage());
          return CLIENT_SIDE_PARAM_ERROR;
        }
        searchOptions.getControls().add(ctrl);
      }
    }

    if(effectiveRightsUser.isPresent()) {
      String authzID=effectiveRightsUser.getValue();
      if (!authzID.startsWith("dn:")) {
        Message message = ERR_EFFECTIVERIGHTS_INVALID_AUTHZID.get(authzID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return CLIENT_SIDE_PARAM_ERROR;
      }
      Control effectiveRightsControl =
          new GetEffectiveRightsRequestControl(false, authzID.substring(3),
              effectiveRightsAttrs.getValues());
      searchOptions.getControls().add(effectiveRightsControl);
    }

    if (proxyAuthzID.isPresent())
    {
      Control proxyControl =
          new ProxiedAuthV2Control(true,
              ByteString.valueOf(proxyAuthzID.getValue()));
      searchOptions.getControls().add(proxyControl);
    }

    if (pSearchInfo.isPresent())
    {
      String infoString = toLowerCase(pSearchInfo.getValue().trim());
      HashSet<PersistentSearchChangeType> changeTypes =
           new HashSet<PersistentSearchChangeType>();
      boolean changesOnly = true;
      boolean returnECs = true;

      StringTokenizer tokenizer = new StringTokenizer(infoString, ":");

      if (! tokenizer.hasMoreTokens())
      {
        Message message = ERR_PSEARCH_MISSING_DESCRIPTOR.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      else
      {
        String token = tokenizer.nextToken();
        if (! token.equals("ps"))
        {
          Message message = ERR_PSEARCH_DOESNT_START_WITH_PS.get(
                  String.valueOf(infoString));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }

      if (tokenizer.hasMoreTokens())
      {
        StringTokenizer st = new StringTokenizer(tokenizer.nextToken(), ", ");
        while (st.hasMoreTokens())
        {
          String token = st.nextToken();
          if (token.equals("add"))
          {
            changeTypes.add(PersistentSearchChangeType.ADD);
          }
          else if (token.equals("delete") || token.equals("del"))
          {
            changeTypes.add(PersistentSearchChangeType.DELETE);
          }
          else if (token.equals("modify") || token.equals("mod"))
          {
            changeTypes.add(PersistentSearchChangeType.MODIFY);
          }
          else if (token.equals("modifydn") || token.equals("moddn") ||
                   token.equals("modrdn"))
          {
            changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
          }
          else if (token.equals("any") || token.equals("all"))
          {
            changeTypes.add(PersistentSearchChangeType.ADD);
            changeTypes.add(PersistentSearchChangeType.DELETE);
            changeTypes.add(PersistentSearchChangeType.MODIFY);
            changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
          }
          else
          {
            Message message =
                    ERR_PSEARCH_INVALID_CHANGE_TYPE.get(String.valueOf(token));
            err.println(wrapText(message, MAX_LINE_WIDTH));
            return CLIENT_SIDE_PARAM_ERROR;
          }
        }
      }

      if (changeTypes.isEmpty())
      {
        changeTypes.add(PersistentSearchChangeType.ADD);
        changeTypes.add(PersistentSearchChangeType.DELETE);
        changeTypes.add(PersistentSearchChangeType.MODIFY);
        changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
      }

      if (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();
        if (token.equals("1") || token.equals("true") || token.equals("yes"))
        {
          changesOnly = true;
        }
        else if (token.equals("0") || token.equals("false") ||
                 token.equals("no"))
        {
          changesOnly = false;
        }
        else
        {
          Message message = ERR_PSEARCH_INVALID_CHANGESONLY.get(
                  String.valueOf(token));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }

      if (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();
        if (token.equals("1") || token.equals("true") || token.equals("yes"))
        {
          returnECs = true;
        }
        else if (token.equals("0") || token.equals("false") ||
                 token.equals("no"))
        {
          returnECs = false;
        }
        else
        {
          Message message = ERR_PSEARCH_INVALID_RETURN_ECS.get(
                  String.valueOf(token));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }

      PersistentSearchControl psearchControl =
           new PersistentSearchControl(changeTypes, changesOnly, returnECs);
      searchOptions.getControls().add(psearchControl);
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(filterString);

        // FIXME -- Change this to the correct OID when the official one is
        //          assigned.
        Control assertionControl =
            new LDAPAssertionRequestControl(true, filter);
        searchOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        Message message = ERR_LDAP_ASSERTION_INVALID_FILTER.get(
                le.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (matchedValuesFilter.isPresent())
    {
      LinkedList<String> mvFilterStrings = matchedValuesFilter.getValues();
      ArrayList<MatchedValuesFilter> mvFilters =
           new ArrayList<MatchedValuesFilter>();
      for (String s : mvFilterStrings)
      {
        try
        {
          LDAPFilter f = LDAPFilter.decode(s);
          mvFilters.add(MatchedValuesFilter.createFromLDAPFilter(f));
        }
        catch (LDAPException le)
        {
          Message message = ERR_LDAP_MATCHEDVALUES_INVALID_FILTER.get(
                  le.getMessage());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }

      MatchedValuesControl mvc = new MatchedValuesControl(true, mvFilters);
      searchOptions.getControls().add(mvc);
    }

    if (sortOrder.isPresent())
    {
      try
      {
        searchOptions.getControls().add(
            new ServerSideSortRequestControl(sortOrder.getValue()));
      }
      catch (LDAPException le)
      {
        Message message = ERR_LDAP_SORTCONTROL_INVALID_ORDER.get(
                le.getErrorMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (vlvDescriptor.isPresent())
    {
      if (! sortOrder.isPresent())
      {
        Message message = ERR_LDAPSEARCH_VLV_REQUIRES_SORT.get(
                vlvDescriptor.getLongIdentifier(),
                sortOrder.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }

      StringTokenizer tokenizer =
           new StringTokenizer(vlvDescriptor.getValue(), ":");
      int numTokens = tokenizer.countTokens();
      if (numTokens == 3)
      {
        try
        {
          int beforeCount = Integer.parseInt(tokenizer.nextToken());
          int afterCount  = Integer.parseInt(tokenizer.nextToken());
          ByteString assertionValue = ByteString.valueOf(tokenizer.nextToken());
          searchOptions.getControls().add(
              new VLVRequestControl(beforeCount, afterCount, assertionValue));
        }
        catch (Exception e)
        {
          Message message = ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else if (numTokens == 4)
      {
        try
        {
          int beforeCount  = Integer.parseInt(tokenizer.nextToken());
          int afterCount   = Integer.parseInt(tokenizer.nextToken());
          int offset       = Integer.parseInt(tokenizer.nextToken());
          int contentCount = Integer.parseInt(tokenizer.nextToken());
          searchOptions.getControls().add(
              new VLVRequestControl(beforeCount, afterCount, offset,
                  contentCount));
        }
        catch (Exception e)
        {
          Message message = ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get();
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else
      {
        Message message = ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (subEntriesArgument.isPresent())
    {
      Control subentriesControl =
          new SubentriesControl(true, true);
      searchOptions.getControls().add(subentriesControl);
    }

    // Set the connection options.
    connectionOptions.setSASLExternal(saslExternal.isPresent());
    if(saslOptions.isPresent())
    {
      LinkedList<String> values = saslOptions.getValues();
      for(String saslOption : values)
      {
        if(saslOption.startsWith("mech="))
        {
          boolean mechValue = connectionOptions.setSASLMechanism(saslOption);
          if(mechValue == false)
          {
            return CLIENT_SIDE_PARAM_ERROR;
          }
        } else
        {
          boolean propValue = connectionOptions.addSASLProperty(saslOption);
          if(propValue == false)
          {
            return CLIENT_SIDE_PARAM_ERROR;
          }
        }
      }
    }
    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(startTLS.isPresent());

    if(connectionOptions.useSASLExternal())
    {
      if(!connectionOptions.useSSL() && !connectionOptions.useStartTLS())
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      if(keyStorePathValue == null)
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    connectionOptions.setVerbose(verbose.isPresent());

    // Read the filter strings.
    if(fileNameValue != null)
    {
      BufferedReader in = null;
      try
      {
        in = new BufferedReader(new FileReader(fileNameValue));
        String line = null;

        while ((line = in.readLine()) != null)
        {
          if(line.trim().equals(""))
          {
            // ignore empty lines.
            continue;
          }
          LDAPFilter ldapFilter = LDAPFilter.decode(line);
          filters.add(ldapFilter);
        }
      } catch(Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
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

    }

    if(filters.isEmpty())
    {

      err.println(wrapText(ERR_SEARCH_NO_FILTERS.get(), MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    int wrapColumn = 80;
    if (dontWrap.isPresent())
    {
      wrapColumn = 0;
    }

    LDAPSearch ldapSearch = null;
    try
    {
      if (initializeServer)
      {
        // Bootstrap and initialize directory data structures.
        EmbeddedUtils.initializeForClientUse();
      }

      // Connect to the specified host with the supplied userDN and password.
      SSLConnectionFactory sslConnectionFactory = null;
      if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
      {
        String clientAlias;
        if (certNickname.isPresent())
        {
          clientAlias = certNickname.getValue();
        }
        else
        {
          clientAlias = null;
        }

        sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStorePathValue,
                                  keyStorePasswordValue, clientAlias,
                                  trustStorePathValue, trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      if (noop.isPresent())
      {
        // We don't actually need to open a connection or perform the search,
        // so we're done.  We should return 0 to either mean that the processing
        // was successful or that there were no matching entries, based on
        // countEntries.isPresent() (but in either case the return value should
        // be zero).
        return 0;
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      int timeout = pSearchInfo.isPresent()?0:connectTimeout.getIntValue();
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID,
          timeout);


      int matchingEntries = 0;
      if (simplePageSize.isPresent())
      {
        if (filters.size() > 1)
        {
          Message message = ERR_PAGED_RESULTS_REQUIRES_SINGLE_FILTER.get();
          throw new LDAPException(CLIENT_SIDE_PARAM_ERROR, message);
        }

        int pageSize = simplePageSize.getIntValue();
        ByteString cookieValue = null;
        ArrayList<Control> origControls = searchOptions.getControls();

        while (true)
        {
          ArrayList<Control> newControls =
               new ArrayList<Control>(origControls.size()+1);
          newControls.addAll(origControls);
          newControls.add(new PagedResultsControl(true, pageSize, cookieValue));
          searchOptions.setControls(newControls);

          ldapSearch = new LDAPSearch(nextMessageID, out, err);
          matchingEntries += ldapSearch.executeSearch(connection, baseDNValue,
                                                      filters, attributes,
                                                      searchOptions,
                                                      wrapColumn);

          List<Control> responseControls =
               ldapSearch.getResponseControls();
          boolean responseFound = false;
          for (Control c : responseControls)
          {
            if (c.getOID().equals(OID_PAGED_RESULTS_CONTROL))
            {
              try
              {
                PagedResultsControl control = PagedResultsControl.DECODER
                    .decode(c.isCritical(), ((LDAPControl) c).getValue());
                responseFound = true;
                cookieValue = control.getCookie();
                break;
              }
              catch (DirectoryException de)
              {
                Message message =
                    ERR_PAGED_RESULTS_CANNOT_DECODE.get(de.getMessage());
                throw new LDAPException(
                        CLIENT_SIDE_DECODING_ERROR, message, de);
              }
            }
          }

          if (! responseFound)
          {
            Message message = ERR_PAGED_RESULTS_RESPONSE_NOT_FOUND.get();
            throw new LDAPException(CLIENT_SIDE_CONTROL_NOT_FOUND, message);
          }
          else if (cookieValue.length() == 0)
          {
            break;
          }
        }
      }
      else
      {
        ldapSearch = new LDAPSearch(nextMessageID, out, err);
        matchingEntries = ldapSearch.executeSearch(connection, baseDNValue,
                                                   filters, attributes,
                                                   searchOptions, wrapColumn);
      }

      if (countEntries.isPresent() && returnMatchingEntries)
      {
        return matchingEntries;
      }
      else
      {
        return 0;
      }

    } catch(LDAPException le)
    {
      int code = le.getResultCode();
      if (code == REFERRAL)
      {
        out.println();
        out.println(wrapText(le.getErrorMessage(), MAX_LINE_WIDTH));
      }
      else
      {
      if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, le);
        }

        LDAPToolUtils.printErrorMessage(err, le.getMessageObject(), code,
            le.getErrorMessage(), le.getMatchedDN());
      }
      return code;
    } catch(LDAPConnectionException lce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, lce);
      }
      LDAPToolUtils.printErrorMessage(err,
                                      lce.getMessageObject(),
                                      lce.getResultCode(),
                                      lce.getErrorMessage(),
                                      lce.getMatchedDN());
      int code = lce.getResultCode();
      return code;
    } catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    } finally
    {
      if(connection != null)
      {
        if (ldapSearch == null)
        {
          connection.close(null);
        }
        else
        {
          connection.close(ldapSearch.nextMessageID);
        }
      }
    }
  }
}


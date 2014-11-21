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
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.schema;



import java.util.HashSet;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteSequence;


import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class implements the teletex terminal identifier attribute syntax, which
 * contains a printable string (the terminal identifier) followed by zero or
 * more parameters, which start with a dollar sign and are followed by a
 * parameter name, a colon, and a value.  The parameter value should consist of
 * any string of bytes (the dollar sign and backslash must be escaped with a
 * preceding backslash), and the parameter name must be one of the following
 * strings:
 * <UL>
 *   <LI>graphic</LI>
 *   <LI>control</LI>
 *   <LI>misc</LI>
 *   <LI>page</LI>
 *   <LI>private</LI>
 * </UL>
 */
public class TeletexTerminalIdentifierSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  /**
   * The set of allowed fax parameter values, formatted entirely in lowercase
   * characters.
   */
  public static final HashSet<String> ALLOWED_TTX_PARAMETERS =
       new HashSet<String>(5);

  static
  {
    ALLOWED_TTX_PARAMETERS.add("graphic");
    ALLOWED_TTX_PARAMETERS.add("control");
    ALLOWED_TTX_PARAMETERS.add("misc");
    ALLOWED_TTX_PARAMETERS.add("page");
    ALLOWED_TTX_PARAMETERS.add("private");
  }



  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public TeletexTerminalIdentifierSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_CASE_IGNORE_OID, SYNTAX_TELETEX_TERM_ID_NAME));
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_CASE_IGNORE_OID, SYNTAX_TELETEX_TERM_ID_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_OID, SYNTAX_TELETEX_TERM_ID_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_TELETEX_TERM_ID_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_TELETEX_TERM_ID_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_TELETEX_TERM_ID_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if ordering
   *          matches will not be allowed for this type by default.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }



  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if substring
   *          matches will not be allowed for this type by default.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * Retrieves the default approximate matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if approximate
   *          matches will not be allowed for this type by default.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   MessageBuilder invalidReason)
  {
    // Get a lowercase string representation of the value and find its length.
    String valueString = value.toString();
    int    valueLength = valueString.length();


    // The value must contain at least one character.
    if (valueLength == 0)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_EMPTY.get());
      return false;
    }


    // The first character must be a printable string character.
    char c = valueString.charAt(0);
    if (! PrintableString.isPrintableCharacter(c))
    {

      invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_NOT_PRINTABLE.get(
              valueString, String.valueOf(c), 0));
      return false;
    }


    // Continue reading until we find a dollar sign or the end of the string.
    // Every intermediate character must be a printable string character.
    int pos = 1;
    for ( ; pos < valueLength; pos++)
    {
      c = valueString.charAt(pos);
      if (c == '$')
      {
        pos++;
        break;
      }
      else
      {
        if (! PrintableString.isPrintableCharacter(c))
        {

          invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_NOT_PRINTABLE.get(
                  valueString, String.valueOf(c), pos));
        }
      }
    }

    if (pos >= valueLength)
    {
      // We're at the end of the value, so it must be valid unless the last
      // character was a dollar sign.
      if (c == '$')
      {

        invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_END_WITH_DOLLAR.get(
                valueString));
        return false;
      }
      else
      {
        return true;
      }
    }


    // Continue reading until we find the end of the string.  Each substring
    // must be a valid teletex terminal identifier parameter followed by a colon
    // and the value.  Dollar signs must be escaped
    int paramStartPos = pos;
    boolean escaped = false;
    while (pos < valueLength)
    {
      if (escaped)
      {
        pos++;
        continue;
      }

      c = valueString.charAt(pos++);
      if (c == '\\')
      {
        escaped = true;
        continue;
      }
      else if (c == '$')
      {
        String paramStr = valueString.substring(paramStartPos, pos);

        int colonPos = paramStr.indexOf(':');
        if (colonPos < 0)
        {

          invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_PARAM_NO_COLON.get(
                  valueString));
          return false;
        }

        String paramName = paramStr.substring(0, colonPos);
        if (! ALLOWED_TTX_PARAMETERS.contains(paramName))
        {

          invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_ILLEGAL_PARAMETER.get(
                  valueString, paramName));
          return false;
        }

        paramStartPos = pos;
      }
    }


    // We must be at the end of the value.  Read the last parameter and make
    // sure it is valid.
    String paramStr = valueString.substring(paramStartPos);
    int colonPos = paramStr.indexOf(':');
    if (colonPos < 0)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_PARAM_NO_COLON.get(
              valueString));
      return false;
    }

    String paramName = paramStr.substring(0, colonPos);
    if (! ALLOWED_TTX_PARAMETERS.contains(paramName))
    {

      invalidReason.append(ERR_ATTR_SYNTAX_TELETEXID_ILLEGAL_PARAMETER.get(
              valueString, paramName));
      return false;
    }


    // If we've gotten here, then the value must be valid.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBinary()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isHumanReadable()
  {
    return true;
  }
}


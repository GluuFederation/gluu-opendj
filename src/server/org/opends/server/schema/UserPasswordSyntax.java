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
import org.opends.messages.Message;



import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;


import org.opends.server.types.*;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This class defines an attribute syntax used for storing values that have been
 * encoded using a password storage scheme.  The format for attribute values
 * with this syntax is the concatenation of the following elements in the given
 * order:
 * <BR>
 * <UL>
 *   <LI>An opening curly brace ("{") character.</LI>
 *   <LI>The name of the storage scheme used to encode the value.</LI>
 *   <LI>A closing curly brace ("}") character.</LI>
 *   <LI>The encoded value.</LI>
 * </UL>
 */
public class UserPasswordSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public UserPasswordSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_USER_PASSWORD_EXACT_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_USER_PASSWORD_EXACT_NAME, SYNTAX_USER_PASSWORD_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_USER_PASSWORD_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_USER_PASSWORD_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_USER_PASSWORD_DESCRIPTION;
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
    // There is no ordering matching rule by default.
    return null;
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
    // There is no substring matching rule by default.
    return null;
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
    // We have to accept any value here because in many cases the value will not
    // have been encoded by the time this method is called.
    return true;
  }



  /**
   * Decodes the provided user password value into its component parts.
   *
   * @param  userPasswordValue  The user password value to be decoded.
   *
   * @return  A two-element string array whose elements are the storage scheme
   *          name (in all lowercase characters) and the encoded value, in that
   *          order.
   *
   * @throws  DirectoryException  If a problem is encountered while attempting
   *                              to decode the value.
   */
  public static String[] decodeUserPassword(String userPasswordValue)
         throws DirectoryException
  {
    // Make sure that there actually is a value to decode.
    if ((userPasswordValue == null) || (userPasswordValue.length() == 0))
    {
      Message message = ERR_ATTR_SYNTAX_USERPW_NO_VALUE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The first character of an encoded value must be an opening curly brace.
    if (userPasswordValue.charAt(0) != '{')
    {
      Message message = ERR_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // There must be a corresponding closing brace.
    int closePos = userPasswordValue.indexOf('}');
    if (closePos < 0)
    {
      Message message = ERR_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Get the storage scheme name and encoded value.
    String schemeName   = userPasswordValue.substring(1, closePos);
    String encodedValue = userPasswordValue.substring(closePos+1);

    if (schemeName.length() == 0)
    {
      Message message = ERR_ATTR_SYNTAX_USERPW_NO_SCHEME.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    return new String[] { toLowerCase(schemeName), encodedValue };
  }



  /**
   * Indicates whether the provided value is encoded using the user password
   * syntax.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the value appears to be encoded using the
   *          user password syntax, or <CODE>false</CODE> if not.
   */
  public static boolean isEncoded(ByteSequence value)
  {
    // If the value is null or empty, then it's not.
    if ((value == null) || value.length() == 0)
    {
      return false;
    }


    // If the value doesn't start with an opening curly brace, then it's not.
    if (value.byteAt(0) != '{')
    {
      return false;
    }


    // There must be a corresponding closing curly brace, and there must be at
    // least one character inside the brace.
    int closingBracePos = -1;
    for (int i=1; i < value.length(); i++)
    {
      if (value.byteAt(i) == '}')
      {
        closingBracePos = i;
        break;
      }
    }

    if ((closingBracePos < 0) || (closingBracePos == 1))
    {
      return false;
    }


    // The closing curly brace must not be the last character of the password.
    if (closingBracePos == (value.length() - 1))
    {
      return false;
    }


    // If we've gotten here, then it looks to be encoded.
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


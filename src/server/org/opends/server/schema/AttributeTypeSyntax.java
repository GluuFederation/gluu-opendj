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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.schema;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.std.server.*;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import static org.opends.messages.SchemaMessages.*;

import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.config.ConfigConstants.*;



/**
 * This class defines the attribute type description syntax, which is used to
 * hold attribute type definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
public class AttributeTypeSyntax
       extends AttributeSyntax<AttributeTypeDescriptionAttributeSyntaxCfg>
       implements
       ConfigurationChangeListener<AttributeTypeDescriptionAttributeSyntaxCfg> {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The reference to the configuration for this attribute type description
  // syntax.
  private AttributeTypeDescriptionAttributeSyntaxCfg currentConfig;



  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;

  // If true strip the suggested minimum upper bound from the syntax OID.
  private static boolean stripMinimumUpperBound=false;


  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public AttributeTypeSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void
  initializeSyntax(AttributeTypeDescriptionAttributeSyntaxCfg configuration)
         throws ConfigException, InitializationException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      Message message = ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_CASE_IGNORE_OID, SYNTAX_ATTRIBUTE_TYPE_NAME);
      throw new InitializationException(message);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      Message message = ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_CASE_IGNORE_OID, SYNTAX_ATTRIBUTE_TYPE_NAME);
      throw new InitializationException(message);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      Message message = ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_OID, SYNTAX_ATTRIBUTE_TYPE_NAME);
      throw new InitializationException(message);
    }

    // This syntax is one of the Directory Server's core syntaxes and therefore
    // it may be instantiated at times without a configuration entry.  If that
    // is the case, then we'll exit now before doing anything that could require
    // access to that entry.
    if (configuration == null)
    {
      return;
    }

    currentConfig = configuration;
    currentConfig.addAttributeTypeDescriptionChangeListener(this);
    stripMinimumUpperBound=configuration.isStripSyntaxMinUpperBound();
  }



  /**
   * {@inheritDoc}
   */
  public String getSyntaxName()
  {
    return SYNTAX_ATTRIBUTE_TYPE_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public String getOID()
  {
    return SYNTAX_ATTRIBUTE_TYPE_OID;
  }



  /**
   * {@inheritDoc}
   */
  public String getDescription()
  {
    return SYNTAX_ATTRIBUTE_TYPE_DESCRIPTION;
  }



  /**
   * {@inheritDoc}
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   MessageBuilder invalidReason)
  {
    // We'll use the decodeAttributeType method to determine if the value is
    // acceptable.
    try
    {
      decodeAttributeType(value, DirectoryServer.getSchema(), true);
      return true;
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      invalidReason.append(de.getMessageObject());
      return false;
    }
  }



  /**
   * Decodes the contents of the provided ASN.1 octet string as an attribute
   * type definition according to the rules of this syntax.  Note that the
   * provided octet string value does not need to be normalized (and in fact, it
   * should not be in order to allow the desired capitalization to be
   * preserved).
   *
   * @param  value                 The ASN.1 octet string containing the value
   *                               to decode (it does not need to be
   *                               normalized).
   * @param  schema                The schema to use to resolve references to
   *                               other schema elements.
   * @param  allowUnknownElements  Indicates whether to allow values that
   *                               reference a superior attribute type which are
   *                               not defined in the server schema. This should
   *                               only be true when called by
   *                               {@code valueIsAcceptable}.
   *
   * @return  The decoded attribute type definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              attribute type definition.
   */
  public static AttributeType decodeAttributeType(ByteSequence value,
                                                  Schema schema,
                                                  boolean allowUnknownElements)
         throws DirectoryException
  {
    // Get string representations of the provided value using the provided form
    // and with all lowercase characters.
    String valueStr = value.toString();
    String lowerStr = toLowerCase(valueStr);


    // We'll do this a character at a time.  First, skip over any leading
    // whitespace.
    int pos    = 0;
    int length = valueStr.length();
    while ((pos < length) && (valueStr.charAt(pos) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the value was empty or contained only whitespace.  That
      // is illegal.
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS.get(
          valueStr, (pos-1), String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Skip over any spaces immediately following the opening parenthesis.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next set of characters must be the OID.  Strictly speaking, this
    // should only be a numeric OID, but we'll also allow for the
    // "attrname-oid" case as well.  Look at the first character to figure out
    // which we will be using.
    int oidStartPos = pos;
    if (isDigit(c))
    {
      // This must be a numeric OID.  In that case, we will accept only digits
      // and periods, but not consecutive periods.
      boolean lastWasPeriod = false;
      while ((pos < length) && ((c = valueStr.charAt(pos)) != ' ')
              && (c = valueStr.charAt(pos)) != ')')
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            Message message =
              ERR_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID.
                  get(valueStr, (pos-1));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // This must have been an illegal character.
          Message message =
            ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID.
                get(valueStr, String.valueOf(c), (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        else
        {
          lastWasPeriod = false;
        }
        pos++;
      }
    }
    else
    {
      // This must be a "fake" OID.  In this case, we will only accept
      // alphabetic characters, numeric digits, and the hyphen.
      while ((pos < length) && ((c = valueStr.charAt(pos)) != ' ')
              && (c=valueStr.charAt(pos))!=')')
      {
        if (isAlpha(c) || isDigit(c) || (c == '-') ||
            ((c == '_') && DirectoryServer.allowAttributeNameExceptions()))
        {
          // This is fine.  It is an acceptable character.
          pos++;
        }
        else
        {
          // This must have been an illegal character.
          Message message = ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_STRING_OID.
              get(valueStr, String.valueOf(c), (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }


    // If we're at the end of the value, then it isn't a valid attribute type
    // description.  Otherwise, parse out the OID.
    String oid;
    if (pos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }
    else
    {
      oid = lowerStr.substring(oidStartPos, pos);
    }


    // Skip over the space(s) after the OID.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // At this point, we should have a pretty specific syntax that describes
    // what may come next, but some of the components are optional and it would
    // be pretty easy to put something in the wrong order, so we will be very
    // flexible about what we can accept.  Just look at the next token, figure
    // out what it is and how to treat what comes after it, then repeat until
    // we get to the end of the value.  But before we start, set default values
    // for everything else we might need to know.
    String  primaryName = oid;
    List<String> typeNames = new LinkedList<String>();
    String description = null;
    AttributeType superiorType = null;
    AttributeSyntax<?> syntax = DirectoryServer.getDefaultAttributeSyntax();
    ApproximateMatchingRule approximateMatchingRule = null;
    EqualityMatchingRule equalityMatchingRule = null;
    OrderingMatchingRule orderingMatchingRule = null;
    SubstringMatchingRule substringMatchingRule = null;
    AttributeUsage attributeUsage = AttributeUsage.USER_APPLICATIONS;
    boolean isCollective = false;
    boolean isNoUserModification = false;
    boolean isObsolete = false;
    boolean isSingleValue = false;
    HashMap<String,List<String>> extraProperties =
         new LinkedHashMap<String,List<String>>();


    while (true)
    {
      StringBuilder tokenNameBuffer = new StringBuilder();
      pos = readTokenName(valueStr, tokenNameBuffer, pos);
      String tokenName = tokenNameBuffer.toString();
      String lowerTokenName = toLowerCase(tokenName);
      if (tokenName.equals(")"))
      {
        // We must be at the end of the value.  If not, then that's a problem.
        if (pos < length)
        {
          Message message =
            ERR_ATTR_SYNTAX_ATTRTYPE_UNEXPECTED_CLOSE_PARENTHESIS.
                get(valueStr, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }

        break;
      }
      else if (lowerTokenName.equals("name"))
      {
        // This specifies the set of names for the attribute type.  It may be a
        // single name in single quotes, or it may be an open parenthesis
        // followed by one or more names in single quotes separated by spaces.
        c = valueStr.charAt(pos++);
        if (c == '\'')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 (pos-1));
          primaryName = userBuffer.toString();
          typeNames.add(primaryName);
        }
        else if (c == '(')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 pos);
          primaryName = userBuffer.toString();
          typeNames.add(primaryName);


          while (true)
          {
            if (valueStr.charAt(pos) == ')')
            {
              // Skip over any spaces after the parenthesis.
              pos++;
              while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
              {
                pos++;
              }

              break;
            }
            else
            {
              userBuffer  = new StringBuilder();
              lowerBuffer = new StringBuilder();

              pos = readQuotedString(valueStr, lowerStr, userBuffer,
                                     lowerBuffer, pos);
              typeNames.add(userBuffer.toString());
            }
          }
        }
        else
        {
          // This is an illegal character.
          Message message =
              ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR.get(
                      valueStr, String.valueOf(c), (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        //RFC 2251: A specification may also assign one or more textual names
        //for an attribute type.  These names MUST begin with a letter, and
        //only contain ASCII letters, digit characters and hyphens.

        //The global config hasn't been read so far. Allow the name exceptions
        //during startup.
        boolean allowExceptions = DirectoryServer.isRunning()?
                           DirectoryServer.allowAttributeNameExceptions():true;
        //Iterate over all the names and throw an exception if it is invalid.
        for(String name : typeNames)
        {
          for(int index=0; index < name.length(); index++)
          {
            char ch = name.charAt(index);
            switch(ch)
            {
              case '-':
              //hyphen is allowed but not as the first byte.
                if (index==0)
                {
                  Message msg = ERR_ATTR_SYNTAX_ATTR_ILLEGAL_INITIAL_DASH.
                        get(value.toString());
                  throw new DirectoryException(
                          ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                               msg);
                }
                break;
              case '_':
              // This will never be allowed as the first character.  It
              // may be allowed for subsequent characters if the attribute
              // name exceptions option is enabled.
                if (index==0)
                {
                  Message msg =
                          ERR_ATTR_SYNTAX_ATTR_ILLEGAL_INITIAL_UNDERSCORE.
                        get(value.toString(),
                            ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
                  throw new DirectoryException(
                          ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                               msg);
                }
                else if (!allowExceptions)
                {
                  Message msg = ERR_ATTR_SYNTAX_ATTR_ILLEGAL_UNDERSCORE_CHAR.
                        get(value.toString(),
                            ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
                  throw new DirectoryException(
                          ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                               msg);
                }
                break;

              default:
              //Only digits and ascii letters are allowed but the first byte
              //can not be a digit.
                if(index ==0 && isDigit(ch) && !allowExceptions)
                {
                  Message message = ERR_ATTR_SYNTAX_ATTR_ILLEGAL_INITIAL_DIGIT.
                    get(value.toString(), ch,
                        ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
                  throw new DirectoryException(
                          ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                             message);
                }
                else if(!((ch>='0' && ch<='9') || (ch>='A' && ch<='Z') ||
                        (ch>='a' && ch<='z')))
                {
                  Message msg = ERR_ATTR_SYNTAX_ATTR_ILLEGAL_CHAR.get(
                            value.toString(), ch, index);
                  throw new DirectoryException(
                          ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       msg);
                }
                break;
            }
          }

        }

      }
      else if (lowerTokenName.equals("desc"))
      {
        // This specifies the description for the attribute type.  It is an
        // arbitrary string of characters enclosed in single quotes.
        StringBuilder descriptionBuffer = new StringBuilder();
        pos = readQuotedString(valueStr, descriptionBuffer, pos);
        description = descriptionBuffer.toString();
      }
      else if (lowerTokenName.equals("obsolete"))
      {
        // This indicates whether the attribute type should be considered
        // obsolete.  We do not need to do any more parsing for this token.
        isObsolete = true;
      }
      else if (lowerTokenName.equals("sup"))
      {
        // This specifies the name or OID of the superior attribute type from
        // which this attribute type should inherit its properties.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        superiorType = schema.getAttributeType(woidBuffer.toString());
        if (superiorType == null)
        {
          if (allowUnknownElements)
          {
            superiorType = DirectoryServer.getDefaultAttributeType(
                                                woidBuffer.toString());
          }
          else
          {
            // This is bad because we don't know what the superior attribute
            // type is so we can't base this attribute type on it.
            Message message = WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUPERIOR_TYPE.
                get(String.valueOf(oid), String.valueOf(woidBuffer));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }


        // Use the information in the superior type to provide defaults for the
        // rest of the components in this attribute type description.
        // Technically, the definition of the superior type should be provided
        // before the matching rule, syntax, single-value, collective,
        // no-user-modification, and usage components, and in that case we won't
        // undo something else that has already been set by an earlier
        // definition.  However, if the information is provided out-of-order,
        // then it is possible that this could overwrite some desired setting
        // that is different from that of the supertype.
        approximateMatchingRule = superiorType.getApproximateMatchingRule();
        equalityMatchingRule    = superiorType.getEqualityMatchingRule();
        orderingMatchingRule    = superiorType.getOrderingMatchingRule();
        substringMatchingRule   = superiorType.getSubstringMatchingRule();
        syntax                  = superiorType.getSyntax();
        isSingleValue           = superiorType.isSingleValue();
        isCollective            = superiorType.isCollective();
        isNoUserModification    = superiorType.isNoUserModification();
        attributeUsage          = superiorType.getUsage();
      }
      else if (lowerTokenName.equals("equality"))
      {
        // This specifies the name or OID of the equality matching rule to use
        // for this attribute type.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        EqualityMatchingRule emr =
             schema.getEqualityMatchingRule(woidBuffer.toString());
        if (emr == null)
        {
          // This is bad because we have no idea what the equality matching
          // rule should be.
          Message message = WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_EQUALITY_MR.get(
              String.valueOf(oid), String.valueOf(woidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message);
        }
        else
        {
          equalityMatchingRule = emr;
        }
      }
      else if (lowerTokenName.equals("ordering"))
      {
        // This specifies the name or OID of the ordering matching rule to use
        // for this attribute type.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        OrderingMatchingRule omr =
             schema.getOrderingMatchingRule(woidBuffer.toString());
        if (omr == null)
        {
          // This is bad because we have no idea what the ordering matching
          // rule should be.
          Message message = WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_ORDERING_MR.get(
              String.valueOf(oid), String.valueOf(woidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message);
        }
        else
        {
          orderingMatchingRule = omr;
        }
      }
      else if (lowerTokenName.equals("substr"))
      {
        // This specifies the name or OID of the substring matching rule to use
        // for this attribute type.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        SubstringMatchingRule smr =
             schema.getSubstringMatchingRule(woidBuffer.toString());
        if (smr == null)
        {
          // This is bad because we have no idea what the substring matching
          // rule should be.
          Message message = WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUBSTRING_MR.get(
              String.valueOf(oid), String.valueOf(woidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message);
        }
        else
        {
          substringMatchingRule = smr;
        }
      }
      else if (lowerTokenName.equals("syntax"))
      {
        // This specifies the numeric OID of the syntax for this matching rule.
        // It may optionally be immediately followed by an open curly brace, an
        // integer value, and a close curly brace to suggest the minimum number
        // of characters that should be allowed in values of that type.  This
        // implementation will ignore any such length because it does not
        // impose any practical limit on the length of attribute values.
        boolean inBrace         = false;
        boolean lastWasPeriod   = false;
        StringBuilder oidBuffer = new StringBuilder();
        while (pos < length)
        {
          c = lowerStr.charAt(pos++);
          if (inBrace)
          {
            // The only thing we'll allow here will be numeric digits and the
            // closing curly brace.
            if (c == '}')
            {
              // The next character must be a space or a closing parenthesis.
              if ((c = lowerStr.charAt(pos)) != ' '
                      &&  (c = lowerStr.charAt(pos)) != ')')
              {
                Message message =
                  ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID.
                      get(valueStr, String.valueOf(c), (pos-1));
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
              }

              break;
            }
            else if (! isDigit(c))
            {
              Message message =
                ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID.
                    get(valueStr, String.valueOf(c), (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message);
            }
          }
          else
          {
            if (isDigit(c))
            {
              oidBuffer.append(c);
              lastWasPeriod = false;
            }
            else if (c == '.')
            {
              if (lastWasPeriod)
              {
                Message message =
                    ERR_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID.
                      get(valueStr, (pos-1));
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
              }
              else
              {
                oidBuffer.append(c);
                lastWasPeriod = true;
              }
            }
            else if (c == '{')
            {
              // It's the start of the length specification.
              inBrace = true;
            }
            else if (c == ' ')
            {
              // It's the end of the value.
              break;
            }
            else if(c == ')')
            {
              // As per RFC 4512 (4.1.2) it is end of the value.
              --pos;
              break;
            }
            else
            {
              Message message =
                ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID.
                    get(valueStr, String.valueOf(c), (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message);
            }
          }
        }

        syntax = schema.getSyntax(oidBuffer.toString());
        if (syntax == null)
        {
          Message message = WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SYNTAX.get(
              String.valueOf(oid), String.valueOf(oidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message);
        }

        if (approximateMatchingRule == null)
        {
          approximateMatchingRule = syntax.getApproximateMatchingRule();
        }

        if (equalityMatchingRule == null)
        {
          equalityMatchingRule = syntax.getEqualityMatchingRule();
        }

        if (orderingMatchingRule == null)
        {
          orderingMatchingRule = syntax.getOrderingMatchingRule();
        }

        if (substringMatchingRule == null)
        {
          substringMatchingRule = syntax.getSubstringMatchingRule();
        }
      }
      else if (lowerTokenName.equals("single-value"))
      {
        // This indicates that attributes of this type are allowed to have at
        // most one value.  We do not need any more parsing for this token.
        isSingleValue = true;
      }
      else if (lowerTokenName.equals("collective"))
      {
        // This indicates that attributes of this type are collective (i.e.,
        // have their values generated dynamically in some way).  We do not need
        // any more parsing for this token.
        isCollective = true;
      }
      else if (lowerTokenName.equals("no-user-modification"))
      {
        // This indicates that the values of attributes of this type are not to
        // be modified by end users.  We do not need any more parsing for this
        // token.
        isNoUserModification = true;
      }
      else if (lowerTokenName.equals("usage"))
      {
        // This specifies the usage string for this attribute type.  It should
        // be followed by one of the strings "userApplications",
        // "directoryOperation", "distributedOperation", or "dSAOperation".
        StringBuilder usageBuffer = new StringBuilder();
        while (pos < length)
        {
          c = lowerStr.charAt(pos++);
          if (c == ' ')
          {
            break;
          }
          else if(c == ')')
          {
            pos--;
            break;
          }
          else
          {
            usageBuffer.append(c);
          }
        }

        String usageStr = usageBuffer.toString();
        if (usageStr.equals("userapplications"))
        {
          attributeUsage = AttributeUsage.USER_APPLICATIONS;
        }
        else if (usageStr.equals("directoryoperation"))
        {
          attributeUsage = AttributeUsage.DIRECTORY_OPERATION;
        }
        else if (usageStr.equals("distributedoperation"))
        {
          attributeUsage = AttributeUsage.DISTRIBUTED_OPERATION;
        }
        else if (usageStr.equals("dsaoperation"))
        {
          attributeUsage = AttributeUsage.DSA_OPERATION;
        }
        else
        {
          // This must be an illegal usage.
          attributeUsage = AttributeUsage.USER_APPLICATIONS;

          Message message = WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE.
              get(String.valueOf(oid), usageStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
      else
      {
        // This must be a non-standard property and it must be followed by
        // either a single value in single quotes or an open parenthesis
        // followed by one or more values in single quotes separated by spaces
        // followed by a close parenthesis.
        List<String> valueList = new ArrayList<String>();
        pos = readExtraParameterValues(valueStr, valueList, pos);
        extraProperties.put(tokenName, valueList);
      }
    }

    List<String> approxRules = extraProperties.get(SCHEMA_PROPERTY_APPROX_RULE);
    if ((approxRules != null) && (! approxRules.isEmpty()))
    {
      String ruleName  = approxRules.get(0);
      String lowerName = toLowerCase(ruleName);
      ApproximateMatchingRule amr =
           schema.getApproximateMatchingRule(lowerName);
      if (amr == null)
      {
        // This is bad because we have no idea what the approximate matching
        // rule should be.
        Message message = WARN_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_APPROXIMATE_MR.get(
            String.valueOf(oid), String.valueOf(ruleName));
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }
      else
      {
        approximateMatchingRule = amr;
      }
    }


    // If there is a superior type, then it must have the same usage as the
    // subordinate type.  Also, if the superior type is collective, then so must
    // the subordinate type be collective.
    if (superiorType != null)
    {
      if (superiorType.getUsage() != attributeUsage)
      {
        Message message = WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_SUPERIOR_USAGE.get(
            oid, String.valueOf(attributeUsage), superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
      }

      if (superiorType.isCollective())
      {
        if (!isCollective)
        {
          Message message =
                  WARN_ATTR_SYNTAX_ATTRTYPE_NONCOLLECTIVE_FROM_COLLECTIVE.get(
                    oid, superiorType.getNameOrOID());
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                  message);
        }
      }
    }


    // If the attribute type is NO-USER-MODIFICATION, then it must not have a
    // usage of userApplications.
    if (isNoUserModification &&
        (attributeUsage == AttributeUsage.USER_APPLICATIONS))
    {
      Message message =
          WARN_ATTR_SYNTAX_ATTRTYPE_NO_USER_MOD_NOT_OPERATIONAL.get(oid);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    CommonSchemaElements.checkSafeProperties(extraProperties);

    return new AttributeType(value.toString(), primaryName, typeNames, oid,
                             description, superiorType, syntax,
                             approximateMatchingRule, equalityMatchingRule,
                             orderingMatchingRule, substringMatchingRule,
                             attributeUsage, isCollective, isNoUserModification,
                             isObsolete, isSingleValue, extraProperties);
  }



  /**
   * Reads the next token name from the attribute type definition, skipping over
   * any leading or trailing spaces, and appends it to the provided buffer.
   *
   * @param  valueStr   The string representation of the attribute type
   *                    definition.
   * @param  tokenName  The buffer into which the token name will be written.
   * @param  startPos   The position in the provided string at which to start
   *                    reading the token name.
   *
   * @return  The position of the first character that is not part of the token
   *          name or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              token name.
   */
  private static int readTokenName(String valueStr, StringBuilder tokenName,
                                   int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Read until we find the next space.
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) != ' ')
            && (c = valueStr.charAt(startPos)) != ')')
    {
      tokenName.append(c);
      startPos++;
    }

    //We may be left with only ')' which is not part of the token yet.
    //Let us see if it is the case.
    if(tokenName.length()==0 && c == ')')
    {
      tokenName.append(c);
      startPos++;
    }

    // Skip over any trailing spaces after the value.
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping over the
   * quotes and any leading or trailing spaces, and appending the string to the
   * provided buffer.
   *
   * @param  valueStr     The user-provided representation of the attribute type
   *                      definition.
   * @param  valueBuffer  The buffer into which the user-provided representation
   *                      of the value will be placed.
   * @param  startPos     The position in the provided string at which to start
   *                      reading the quoted string.
   *
   * @return  The position of the first character that is not part of the quoted
   *          string or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              quoted string.
   */
  private static int readQuotedString(String valueStr,
                                      StringBuilder valueBuffer, int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      Message message = WARN_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE_AT_POS.get(
          valueStr, startPos, String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Read until we find the closing quote.
    startPos++;
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) != '\''))
    {
      valueBuffer.append(c);
      startPos++;
    }


    // Skip over any trailing spaces after the value.
    startPos++;
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping over the
   * quotes and any leading or trailing spaces, and appending the string to the
   * provided buffer.
   *
   * @param  valueStr     The user-provided representation of the attribute type
   *                      definition.
   * @param  lowerStr     The all-lowercase representation of the attribute type
   *                      definition.
   * @param  userBuffer   The buffer into which the user-provided representation
   *                      of the value will be placed.
   * @param  lowerBuffer  The buffer into which the all-lowercase representation
   *                      of the value will be placed.
   * @param  startPos     The position in the provided string at which to start
   *                      reading the quoted string.
   *
   * @return  The position of the first character that is not part of the quoted
   *          string or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              quoted string.
   */
  private static int readQuotedString(String valueStr, String lowerStr,
                                      StringBuilder userBuffer,
                                      StringBuilder lowerBuffer, int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = lowerStr.length();
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      Message message = WARN_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE_AT_POS.get(
          valueStr, startPos, String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Read until we find the closing quote.
    startPos++;
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) != '\''))
    {
      lowerBuffer.append(c);
      userBuffer.append(valueStr.charAt(startPos));
      startPos++;
    }


    // Skip over any trailing spaces after the value.
    startPos++;
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the attribute description or numeric OID from the provided string,
   * skipping over any leading or trailing spaces, and appending the value to
   * the provided buffer.
   *
   * @param  lowerStr    The string from which the name or OID is to be read.
   * @param  woidBuffer  The buffer into which the name or OID should be
   *                     appended.
   * @param  startPos    The position at which to start reading.
   *
   * @return  The position of the first character after the name or OID that is
   *          not a space.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              name or OID.
   */
  private static int readWOID(String lowerStr, StringBuilder woidBuffer,
                              int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = lowerStr.length();
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be either numeric (for an OID) or alphabetic (for
    // an attribute description).
    if (isDigit(c))
    {
      // This must be a numeric OID.  In that case, we will accept only digits
      // and periods, but not consecutive periods.
      boolean lastWasPeriod = false;
      while ((startPos < length) && ((c = lowerStr.charAt(startPos++)) != ' '))
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            Message message =
              ERR_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID.
                  get(lowerStr, (startPos-1));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          else
          {
            woidBuffer.append(c);
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // Technically, this must be an illegal character.  However, it is
          // possible that someone just got sloppy and did not include a space
          // between the name/OID and a closing parenthesis.  In that case,
          // we'll assume it's the end of the value.  What's more, we'll have
          // to prematurely return to nasty side effects from stripping off
          // additional characters.
          if (c == ')')
          {
            return (startPos-1);
          }

          // This must have been an illegal character.
          Message message =
              ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID.
                get(lowerStr, String.valueOf(c), (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        else
        {
          woidBuffer.append(c);
          lastWasPeriod = false;
        }
      }
    }
    else if (isAlpha(c))
    {
      // This must be an attribute description.  In this case, we will only
      // accept alphabetic characters, numeric digits, and the hyphen.
      while ((startPos < length) && ((c = lowerStr.charAt(startPos++)) != ' '))
      {
        if (isAlpha(c) || isDigit(c) || (c == '-') ||
            ((c == '_') && DirectoryServer.allowAttributeNameExceptions()))
        {
          woidBuffer.append(c);
        }
        else
        {
          // Technically, this must be an illegal character.  However, it is
          // possible that someone just got sloppy and did not include a space
          // between the name/OID and a closing parenthesis.  In that case,
          // we'll assume it's the end of the value.  What's more, we'll have
          // to prematurely return to nasty side effects from stripping off
          // additional characters.
          if (c == ')')
          {
            return (startPos-1);
          }

          // This must have been an illegal character.
          Message message = ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_STRING_OID.
              get(lowerStr, String.valueOf(c), (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }
    else
    {
      Message message =
          ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR.
                  get(lowerStr, String.valueOf(c), startPos);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Skip over any trailing spaces after the value.
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      Message message = ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value for an "extra" parameter.  It will handle a single unquoted
   * word (which is technically illegal, but we'll allow it), a single quoted
   * string, or an open parenthesis followed by a space-delimited set of quoted
   * strings or unquoted words followed by a close parenthesis.
   *
   * @param  valueStr   The string containing the information to be read.
   * @param  valueList  The list of "extra" parameter values read so far.
   * @param  startPos   The position in the value string at which to start
   *                    reading.
   *
   * @return  The "extra" parameter value that was read.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to read
   *                              the value.
   */
  private static int readExtraParameterValues(String valueStr,
                          List<String> valueList, int startPos)
          throws DirectoryException
  {
    // Skip over any leading spaces.
    int length = valueStr.length();
    char c = '\u0000';
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message =
          ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Look at the next character.  If it is a quote, then parse until the next
    // quote and end.  If it is an open parenthesis, then parse individual
    // values until the close parenthesis and end.  Otherwise, parse until the
    // next space and end.
    if (c == '\'')
    {
      // Parse until the closing quote.
      StringBuilder valueBuffer = new StringBuilder();
      startPos++;
      while ((startPos < length) && ((c = valueStr.charAt(startPos)) != '\''))
      {
        valueBuffer.append(c);
        startPos++;
      }
      startPos++;
      valueList.add(valueBuffer.toString());
    }
    else if (c == '(')
    {
      startPos++;
      // We're expecting a list of values. Quoted, space separated.
      while (true)
      {
        // Skip over any leading spaces;
        while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
        {
          startPos++;
        }

        if (startPos >= length)
        {
          Message message =
              ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }

        if (c == ')')
        {
          // This is the end of the list.
          startPos++;
          break;
        }
        else if (c == '(')
        {
          // This is an illegal character.
          Message message =
              ERR_ATTR_SYNTAX_ATTRSYNTAX_EXTENSION_INVALID_CHARACTER.get(
                      valueStr, startPos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        else if (c == '\'')
        {
          // We have a quoted string
          StringBuilder valueBuffer = new StringBuilder();
          startPos++;
          while ((startPos < length) &&
              ((c = valueStr.charAt(startPos)) != '\''))
          {
            valueBuffer.append(c);
            startPos++;
          }

          valueList.add(valueBuffer.toString());
          startPos++;
        }
        else
        {
          //Consider unquoted string
          StringBuilder valueBuffer = new StringBuilder();
          while ((startPos < length) &&
              ((c = valueStr.charAt(startPos)) != ' '))
          {
            valueBuffer.append(c);
            startPos++;
          }

          valueList.add(valueBuffer.toString());
        }

        if (startPos >= length)
        {
          Message message =
              ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }
    else
    {
      // Parse until the next space.
      StringBuilder valueBuffer = new StringBuilder();
      while ((startPos < length) && ((c = valueStr.charAt(startPos)) != ' '))
      {
        valueBuffer.append(c);
        startPos++;
      }

      valueList.add(valueBuffer.toString());
    }

    // Skip over any trailing spaces.
    while ((startPos < length) && (valueStr.charAt(startPos) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message =
          ERR_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }

    return startPos;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              AttributeTypeDescriptionAttributeSyntaxCfg configuration)
  {
    currentConfig = configuration;
    stripMinimumUpperBound = configuration.isStripSyntaxMinUpperBound();

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      AttributeTypeDescriptionAttributeSyntaxCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }

  /**
   * Boolean that indicates that the minimum upper bound value should be
   * stripped from the Attrbute Type Syntax Description.
   *
   * @return True if the minimum upper bound value should be stripped.
   */
  public static boolean isStripSyntaxMinimumUpperBound() {
    return stripMinimumUpperBound;
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

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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 *      Portions Copyright 2013-2014 Manuel Gaupp
 */
package org.opends.server.types;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;

/**
 * This class defines a data structure for storing and interacting
 * with a search filter that may serve as criteria for locating
 * entries in the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class SearchFilter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static SearchFilter objectClassPresent;

  /** The attribute type for this filter. */
  private final AttributeType attributeType;

  /** The assertion value for this filter. */
  private final ByteString assertionValue;

  /** Indicates whether to match on DN attributes for extensible match filters. */
  private final boolean dnAttributes;

  /** The subInitial element for substring filters. */
  private final ByteString subInitialElement;
  /** The set of subAny components for substring filters. */
  private final List<ByteString> subAnyElements;
  /** The subFinal element for substring filters. */
  private final ByteString subFinalElement;

  /** The search filter type for this filter. */
  private final FilterType filterType;

  /** The set of filter components for AND and OR filters. */
  private final LinkedHashSet<SearchFilter> filterComponents;
  /** The not filter component for this search filter. */
  private final SearchFilter notComponent;

  /** The set of options for the attribute type in this filter. */
  private final Set<String> attributeOptions;

  /** The matching rule ID for this search filter. */
  private final String matchingRuleID;



  /**
   * Creates a new search filter with the provided information.
   *
   * @param  filterType         The filter type for this search
   *                            filter.
   * @param  filterComponents   The set of filter components for AND
   *                            and OR filters.
   * @param  notComponent       The filter component for NOT filters.
   * @param  attributeType      The attribute type for this filter.
   * @param  attributeOptions   The set of attribute options for the
   *                            associated attribute type.
   * @param  assertionValue     The assertion value for this filter.
   * @param  subInitialElement  The subInitial element for substring
   *                            filters.
   * @param  subAnyElements     The subAny elements for substring
   *                            filters.
   * @param  subFinalElement    The subFinal element for substring
   *                            filters.
   * @param  matchingRuleID     The matching rule ID for this search
   *                            filter.
   * @param  dnAttributes       Indicates whether to match on DN
   *                            attributes for extensible match
   *                            filters.
   *
   * FIXME: this should be private.
   */
  public SearchFilter(FilterType filterType,
                      Collection<SearchFilter> filterComponents,
                      SearchFilter notComponent,
                      AttributeType attributeType,
                      Set<String> attributeOptions,
                      ByteString assertionValue,
                      ByteString subInitialElement,
                      List<ByteString> subAnyElements,
                      ByteString subFinalElement,
                      String matchingRuleID, boolean dnAttributes)
  {
    // This used to happen in getSubAnyElements, but we do it here
    // so that we can make this.subAnyElements final.
    if (subAnyElements == null) {
      subAnyElements = new ArrayList<>(0);
    }

    // This used to happen in getFilterComponents, but we do it here
    // so that we can make this.filterComponents final.
    if (filterComponents == null) {
      filterComponents = Collections.emptyList();
    }

    this.filterType        = filterType;
    this.filterComponents  = new LinkedHashSet<>(filterComponents);
    this.notComponent      = notComponent;
    this.attributeType     = attributeType;
    this.attributeOptions  = attributeOptions;
    this.assertionValue    = assertionValue;
    this.subInitialElement = subInitialElement;
    this.subAnyElements    = subAnyElements;
    this.subFinalElement   = subFinalElement;
    this.matchingRuleID    = matchingRuleID;
    this.dnAttributes      = dnAttributes;
  }


  /**
   * Creates a new AND search filter with the provided information.
   *
   * @param  filterComponents  The set of filter components for the
   * AND filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createANDFilter(Collection<SearchFilter>
                                                  filterComponents)
  {
    return new SearchFilter(FilterType.AND, filterComponents, null,
                            null, null, null, null, null, null, null,
                            false);
  }



  /**
   * Creates a new OR search filter with the provided information.
   *
   * @param  filterComponents  The set of filter components for the OR
   *                           filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createORFilter(Collection<SearchFilter>
                                                 filterComponents)
  {
    return new SearchFilter(FilterType.OR, filterComponents, null,
                            null, null, null, null, null, null, null,
                            false);
  }



  /**
   * Creates a new NOT search filter with the provided information.
   *
   * @param  notComponent  The filter component for this NOT filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createNOTFilter(
                                  SearchFilter notComponent)
  {
    return new SearchFilter(FilterType.NOT, null, notComponent, null,
                            null, null, null, null, null, null,
                            false);
  }



  /**
   * Creates a new equality search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this equality
   *                         filter.
   * @param  assertionValue  The assertion value for this equality
   *                         filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createEqualityFilter(
                                  AttributeType attributeType,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.EQUALITY, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates a new equality search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this equality
   *                           filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           equality filter.
   * @param  assertionValue    The assertion value for this equality
   *                           filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createEqualityFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.EQUALITY, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates a new substring search filter with the provided
   * information.
   *
   * @param  attributeType      The attribute type for this filter.
   * @param  subInitialElement  The subInitial element for substring
   *                            filters.
   * @param  subAnyElements     The subAny elements for substring
   *                            filters.
   * @param  subFinalElement    The subFinal element for substring
   *                            filters.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter
       createSubstringFilter(AttributeType attributeType,
                             ByteString subInitialElement,
                             List<ByteString> subAnyElements,
                             ByteString subFinalElement)
  {
    return new SearchFilter(FilterType.SUBSTRING, null, null,
                            attributeType, null, null,
                            subInitialElement, subAnyElements,
                            subFinalElement, null, false);
  }



  /**
   * Creates a new substring search filter with the provided
   * information.
   *
   * @param  attributeType      The attribute type for this filter.
   * @param  attributeOptions   The set of attribute options for this
   *                            search filter.
   * @param  subInitialElement  The subInitial element for substring
   *                            filters.
   * @param  subAnyElements     The subAny elements for substring
   *                            filters.
   * @param  subFinalElement    The subFinal element for substring
   *                            filters.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter
       createSubstringFilter(AttributeType attributeType,
                             Set<String> attributeOptions,
                             ByteString subInitialElement,
                             List<ByteString> subAnyElements,
                             ByteString subFinalElement)
  {
    return new SearchFilter(FilterType.SUBSTRING, null, null,
                            attributeType, attributeOptions, null,
                            subInitialElement, subAnyElements,
                            subFinalElement, null, false);
  }



  /**
   * Creates a greater-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this
   *                         greater-or-equal filter.
   * @param  assertionValue  The assertion value for this
   *                         greater-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createGreaterOrEqualFilter(
                                  AttributeType attributeType,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.GREATER_OR_EQUAL, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates a greater-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this
   *                           greater-or-equal filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           search filter.
   * @param  assertionValue    The assertion value for this
   *                           greater-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createGreaterOrEqualFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.GREATER_OR_EQUAL, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates a less-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this less-or-equal
   *                         filter.
   * @param  assertionValue  The assertion value for this
   *                         less-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createLessOrEqualFilter(
                                  AttributeType attributeType,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.LESS_OR_EQUAL, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates a less-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this
   *                           less-or-equal filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           search filter.
   * @param  assertionValue    The assertion value for this
   *                           less-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createLessOrEqualFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.LESS_OR_EQUAL, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates a presence search filter with the provided information.
   *
   * @param  attributeType  The attribute type for this presence
   *                        filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createPresenceFilter(
                                  AttributeType attributeType)
  {
    return new SearchFilter(FilterType.PRESENT, null, null,
                            attributeType, null, null, null, null,
                            null, null, false);
  }



  /**
   * Creates a presence search filter with the provided information.
   *
   * @param  attributeType     The attribute type for this presence
   *                           filter.
   * @param  attributeOptions  The attribute options for this presence
   *                           filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createPresenceFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions)
  {
    return new SearchFilter(FilterType.PRESENT, null, null,
                            attributeType, attributeOptions, null,
                            null, null, null, null, false);
  }



  /**
   * Creates an approximate search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this approximate
   *                         filter.
   * @param  assertionValue  The assertion value for this approximate
   *                         filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createApproximateFilter(
                                  AttributeType attributeType,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.APPROXIMATE_MATCH, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates an approximate search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this approximate
   *                           filter.
   * @param  attributeOptions  The attribute options for this
   *                           approximate filter.
   * @param  assertionValue    The assertion value for this
   *                           approximate filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createApproximateFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  ByteString assertionValue)
  {
    return new SearchFilter(FilterType.APPROXIMATE_MATCH, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates an extensible matching filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this extensible
   *                         match filter.
   * @param  assertionValue  The assertion value for this extensible
   *                         match filter.
   * @param  matchingRuleID  The matching rule ID for this search
   *                         filter.
   * @param  dnAttributes    Indicates whether to match on DN
   *                         attributes for extensible match filters.
   *
   * @return  The constructed search filter.
   *
   * @throws  DirectoryException  If the provided information is not
   *                              sufficient to create an extensible
   *                              match filter.
   */
  public static SearchFilter createExtensibleMatchFilter(
                                  AttributeType attributeType,
                                  ByteString assertionValue,
                                  String matchingRuleID,
                                  boolean dnAttributes)
         throws DirectoryException
  {
    if (attributeType == null && matchingRuleID == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_CREATE_EXTENSIBLE_MATCH_NO_AT_OR_MR.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }

    return new SearchFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, matchingRuleID, dnAttributes);
  }



  /**
   * Creates an extensible matching filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this extensible
   *                           match filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           extensible match filter.
   * @param  assertionValue    The assertion value for this extensible
   *                           match filter.
   * @param  matchingRuleID    The matching rule ID for this search
   *                           filter.
   * @param  dnAttributes      Indicates whether to match on DN
   *                           attributes for extensible match
   *                           filters.
   *
   * @return  The constructed search filter.
   *
   * @throws  DirectoryException  If the provided information is not
   *                              sufficient to create an extensible
   *                              match filter.
   */
  public static SearchFilter createExtensibleMatchFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  ByteString assertionValue,
                                  String matchingRuleID,
                                  boolean dnAttributes)
         throws DirectoryException
  {
    if (attributeType == null && matchingRuleID == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_CREATE_EXTENSIBLE_MATCH_NO_AT_OR_MR.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }

    return new SearchFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null,
                            matchingRuleID, dnAttributes);
  }



  /**
   * Decodes the provided filter string as a search filter.
   *
   * @param  filterString  The filter string to be decoded as a search
   *                       filter.
   *
   * @return  The search filter decoded from the provided string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the provided string as a
   *                              search filter.
   */
  public static SearchFilter createFilterFromString(
                                  String filterString)
         throws DirectoryException
  {
    if (filterString == null)
    {
      LocalizableMessage message = ERR_SEARCH_FILTER_NULL.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    try
    {
      return createFilterFromString(filterString, 0,
                                    filterString.length());
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      throw de;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SEARCH_FILTER_UNCAUGHT_EXCEPTION.get(filterString, e);
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
    }
  }



  /**
   * Creates a new search filter from the specified portion of the
   * provided string.
   *
   * @param  filterString  The string containing the filter
   *                       information to be decoded.
   * @param  startPos      The index of the first character in the
   *                       string that is part of the search filter.
   * @param  endPos        The index of the first character after the
   *                       start position that is not part of the
   *                       search filter.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the provided string as a
   *                              search filter.
   */
  private static SearchFilter createFilterFromString(
                                   String filterString, int startPos,
                                   int endPos)
          throws DirectoryException
  {
    // Make sure that the length is sufficient for a valid search
    // filter.
    int length = endPos - startPos;
    if (length <= 0)
    {
      LocalizableMessage message = ERR_SEARCH_FILTER_NULL.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    // If the filter is surrounded by parentheses (which it should
    // be), then strip them off.
    if (filterString.charAt(startPos) == '(')
    {
      if (filterString.charAt(endPos-1) == ')')
      {
        startPos++;
        endPos--;
      }
      else
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_MISMATCHED_PARENTHESES.
            get(filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
    }


    // Look at the first character.  If it is a '&' then it is an AND
    // search.  If it is a '|' then it is an OR search.  If it is a
    // '!' then it is a NOT search.
    char c = filterString.charAt(startPos);
    if (c == '&')
    {
      return decodeCompoundFilter(FilterType.AND, filterString,
                                  startPos+1, endPos);
    }
    else if (c == '|')
    {
      return decodeCompoundFilter(FilterType.OR, filterString,
                                  startPos+1, endPos);
    }
    else if (c == '!')
    {
      return decodeCompoundFilter(FilterType.NOT, filterString,
                                  startPos+1, endPos);
    }


    // If we've gotten here, then it must be a simple filter.  It must
    // have an equal sign at some point, so find it.
    int equalPos = -1;
    for (int i=startPos; i < endPos; i++)
    {
      if (filterString.charAt(i) == '=')
      {
        equalPos = i;
        break;
      }
    }

    if (equalPos <= startPos)
    {
      LocalizableMessage message = ERR_SEARCH_FILTER_NO_EQUAL_SIGN.get(
          filterString, startPos, endPos);
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                   message);
    }


    // Look at the character immediately before the equal sign,
    // because it may help determine the filter type.
    int attrEndPos;
    FilterType filterType;
    switch (filterString.charAt(equalPos-1))
    {
      case '~':
        filterType = FilterType.APPROXIMATE_MATCH;
        attrEndPos = equalPos-1;
        break;
      case '>':
        filterType = FilterType.GREATER_OR_EQUAL;
        attrEndPos = equalPos-1;
        break;
      case '<':
        filterType = FilterType.LESS_OR_EQUAL;
        attrEndPos = equalPos-1;
        break;
      case ':':
        return decodeExtensibleMatchFilter(filterString, startPos,
                                           equalPos, endPos);
      default:
        filterType = FilterType.EQUALITY;
        attrEndPos = equalPos;
        break;
    }


    // The part of the filter string before the equal sign should be
    // the attribute type (with or without options).  Decode it.
    String attrType = filterString.substring(startPos, attrEndPos);
    StringBuilder lowerType = new StringBuilder(attrType.length());
    Set<String> attributeOptions = new HashSet<>();

    int semicolonPos = attrType.indexOf(';');
    if (semicolonPos < 0)
    {
      for (int i=0; i < attrType.length(); i++)
      {
        lowerType.append(Character.toLowerCase(attrType.charAt(i)));
      }
    }
    else
    {
      for (int i=0; i < semicolonPos; i++)
      {
        lowerType.append(Character.toLowerCase(attrType.charAt(i)));
      }

      int nextPos = attrType.indexOf(';', semicolonPos+1);
      while (nextPos > 0)
      {
        attributeOptions.add(attrType.substring(semicolonPos+1,
                                                nextPos));
        semicolonPos = nextPos;
        nextPos = attrType.indexOf(';', semicolonPos+1);
      }

      attributeOptions.add(attrType.substring(semicolonPos+1));
    }

    // Get the attribute value.
    AttributeType attributeType = getAttributeType(attrType, lowerType);
    String valueStr = filterString.substring(equalPos+1, endPos);
    if (valueStr.length() == 0)
    {
      return new SearchFilter(filterType, null, null, attributeType,
                    attributeOptions, ByteString.empty(),
                    null, null, null, null, false);
    }
    else if (valueStr.equals("*"))
    {
      return new SearchFilter(FilterType.PRESENT, null, null,
                              attributeType, attributeOptions, null,
                              null, null, null, null, false);
    }
    else if (valueStr.indexOf('*') >= 0)
    {
      return decodeSubstringFilter(filterString, attributeType,
                                   attributeOptions, equalPos,
                                   endPos);
    }
    else
    {
      boolean hasEscape = false;
      byte[] valueBytes = getBytes(valueStr);
      for (byte valueByte : valueBytes)
      {
        if (valueByte == 0x5C) // The backslash character
        {
          hasEscape = true;
          break;
        }
      }

      ByteString userValue;
      if (hasEscape)
      {
        ByteStringBuilder valueBuffer =
            new ByteStringBuilder(valueStr.length());
        for (int i=0; i < valueBytes.length; i++)
        {
          if (valueBytes[i] == 0x5C) // The backslash character
          {
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if (i + 2 >= valueBytes.length)
            {
              LocalizableMessage message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            valueBuffer.append(byteValue);
          }
          else
          {
            valueBuffer.append(valueBytes[i]);
          }
        }

        userValue = valueBuffer.toByteString();
      }
      else
      {
        userValue = ByteString.wrap(valueBytes);
      }

      return new SearchFilter(filterType, null, null, attributeType,
                              attributeOptions, userValue, null, null,
                              null, null, false);
    }
  }



  /**
   * Decodes a set of filters from the provided filter string within
   * the indicated range.
   *
   * @param  filterType    The filter type for this compound filter.
   *                       It must be an AND, OR or NOT filter.
   * @param  filterString  The string containing the filter
   *                       information to decode.
   * @param  startPos      The position of the first character in the
   *                       set of filters to decode.
   * @param  endPos        The position of the first character after
   *                       the end of the set of filters to decode.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the compound filter.
   */
  private static SearchFilter decodeCompoundFilter(
                                   FilterType filterType,
                                   String filterString, int startPos,
                                   int endPos)
          throws DirectoryException
  {
    // Create a list to hold the returned components.
    List<SearchFilter> filterComponents = new ArrayList<>();


    // If the end pos is equal to the start pos, then there are no components.
    if (startPos == endPos)
    {
      if (filterType == FilterType.NOT)
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_NOT_EXACTLY_ONE.get(
            filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }
      else
      {
        // This is valid and will be treated as a TRUE/FALSE filter.
        return new SearchFilter(filterType, filterComponents, null,
                                null, null, null, null, null, null,
                                null, false);
      }
    }


    // The first and last characters must be parentheses.  If not,
    // then that's an error.
    if (filterString.charAt(startPos) != '(' ||
        filterString.charAt(endPos-1) != ')')
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_COMPOUND_MISSING_PARENTHESES.
            get(filterString, startPos, endPos);
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    // Iterate through the characters in the value.  Whenever an open
    // parenthesis is found, locate the corresponding close
    // parenthesis by counting the number of intermediate open/close
    // parentheses.
    int pendingOpens = 0;
    int openPos = -1;
    for (int i=startPos; i < endPos; i++)
    {
      char c = filterString.charAt(i);
      if (c == '(')
      {
        if (openPos < 0)
        {
          openPos = i;
        }

        pendingOpens++;
      }
      else if (c == ')')
      {
        pendingOpens--;
        if (pendingOpens == 0)
        {
          filterComponents.add(createFilterFromString(filterString,
                                                      openPos, i+1));
          openPos = -1;
        }
        else if (pendingOpens < 0)
        {
          LocalizableMessage message =
              ERR_SEARCH_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS.
                get(filterString, i);
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }
      }
      else if (pendingOpens <= 0)
      {
        LocalizableMessage message =
            ERR_SEARCH_FILTER_COMPOUND_MISSING_PARENTHESES.
              get(filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
    }


    // At this point, we have parsed the entire set of filter
    // components.  The list of open parenthesis positions must be
    // empty.
    if (pendingOpens != 0)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS.
            get(filterString, openPos);
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    // We should have everything we need, so return the list.
    if (filterType == FilterType.NOT)
    {
      if (filterComponents.size() != 1)
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_NOT_EXACTLY_ONE.get(
            filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
      SearchFilter notComponent = filterComponents.get(0);
      return new SearchFilter(filterType, null, notComponent, null,
                              null, null, null, null, null, null,
                              false);
    }
    else
    {
      return new SearchFilter(filterType, filterComponents, null,
                              null, null, null, null, null, null,
                              null, false);
    }
  }


  /**
   * Decodes a substring search filter component based on the provided
   * information.
   *
   * @param  filterString  The filter string containing the
   *                       information to decode.
   * @param  attrType      The attribute type for this substring
   *                       filter component.
   * @param  options       The set of attribute options for the
   *                       associated attribute type.
   * @param  equalPos      The location of the equal sign separating
   *                       the attribute type from the value.
   * @param  endPos        The position of the first character after
   *                       the end of the substring value.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the substring filter.
   */
  private static SearchFilter decodeSubstringFilter(
                                   String filterString,
                                   AttributeType attrType,
                                   Set<String> options, int equalPos,
                                   int endPos)
          throws DirectoryException
  {
    // Get a binary representation of the value.
    byte[] valueBytes =
         getBytes(filterString.substring(equalPos+1, endPos));


    // Find the locations of all the asterisks in the value.  Also,
    // check to see if there are any escaped values, since they will
    // need special treatment.
    boolean hasEscape = false;
    LinkedList<Integer> asteriskPositions = new LinkedList<>();
    for (int i=0; i < valueBytes.length; i++)
    {
      if (valueBytes[i] == 0x2A) // The asterisk.
      {
        asteriskPositions.add(i);
      }
      else if (valueBytes[i] == 0x5C) // The backslash.
      {
        hasEscape = true;
      }
    }


    // If there were no asterisks, then this isn't a substring filter.
    if (asteriskPositions.isEmpty())
    {
      LocalizableMessage message = ERR_SEARCH_FILTER_SUBSTRING_NO_ASTERISKS.get(
          filterString, equalPos+1, endPos);
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }
    else
    {
      // The rest of the processing will be only on the value bytes,
      // so re-adjust the end position.
      endPos = valueBytes.length;
    }


    // If the value starts with an asterisk, then there is no
    // subInitial component.  Otherwise, parse out the subInitial.
    ByteString subInitial;
    int firstPos = asteriskPositions.removeFirst();
    if (firstPos == 0)
    {
      subInitial = null;
    }
    else
    {
      if (hasEscape)
      {
        ByteStringBuilder buffer = new ByteStringBuilder(firstPos);
        for (int i=0; i < firstPos; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if (i + 2 >= valueBytes.length)
            {
              LocalizableMessage message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            buffer.append(byteValue);
          }
          else
          {
            buffer.append(valueBytes[i]);
          }
        }

        subInitial = buffer.toByteString();
      }
      else
      {
        subInitial = ByteString.wrap(valueBytes, 0, firstPos);
      }
    }


    // Next, process through the rest of the asterisks to get the subAny values.
    List<ByteString> subAny = new ArrayList<>();
    for (int asteriskPos : asteriskPositions)
    {
      int length = asteriskPos - firstPos - 1;

      if (hasEscape)
      {
        ByteStringBuilder buffer = new ByteStringBuilder(length);
        for (int i=firstPos+1; i < asteriskPos; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if (i + 2 >= valueBytes.length)
            {
              LocalizableMessage message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            buffer.append(byteValue);
          }
          else
          {
            buffer.append(valueBytes[i]);
          }
        }

        subAny.add(buffer.toByteString());
        buffer.clear();
      }
      else
      {
        subAny.add(ByteString.wrap(valueBytes, firstPos+1, length));
      }


      firstPos = asteriskPos;
    }


    // Finally, see if there is anything after the last asterisk,
    // which would be the subFinal value.
    ByteString subFinal;
    if (firstPos == (endPos-1))
    {
      subFinal = null;
    }
    else
    {
      int length = endPos - firstPos - 1;

      if (hasEscape)
      {
        ByteStringBuilder buffer = new ByteStringBuilder(length);
        for (int i=firstPos+1; i < endPos; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if (i + 2 >= valueBytes.length)
            {
              LocalizableMessage message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                LocalizableMessage message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
            }

            buffer.append(byteValue);
          }
          else
          {
            buffer.append(valueBytes[i]);
          }
        }

        subFinal = buffer.toByteString();
      }
      else
      {
        subFinal = ByteString.wrap(valueBytes, firstPos+1, length);
      }
    }


    return new SearchFilter(FilterType.SUBSTRING, null, null,
                            attrType, options, null, subInitial,
                            subAny, subFinal, null, false);
  }



  /**
   * Decodes an extensible match filter component based on the
   * provided information.
   *
   * @param  filterString  The filter string containing the
   *                       information to decode.
   * @param  startPos      The position in the filter string of the
   *                       first character in the extensible match
   *                       filter.
   * @param  equalPos      The position of the equal sign in the
   *                       extensible match filter.
   * @param  endPos        The position of the first character after
   *                       the end of the extensible match filter.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the extensible match
   *                              filter.
   */
  private static SearchFilter decodeExtensibleMatchFilter(
                                   String filterString, int startPos,
                                   int equalPos, int endPos)
          throws DirectoryException
  {
    AttributeType attributeType    = null;
    Set<String>   attributeOptions = new HashSet<>();
    boolean       dnAttributes     = false;
    String        matchingRuleID   = null;


    // Look at the first character.  If it is a colon, then it must be
    // followed by either the string "dn" or the matching rule ID.  If
    // it is not, then it must be the attribute type.
    String lowerLeftStr =
         toLowerCase(filterString.substring(startPos, equalPos));
    if (filterString.charAt(startPos) == ':')
    {
      // See if it starts with ":dn".  Otherwise, it much be the
      // matching rule
      // ID.
      if (lowerLeftStr.startsWith(":dn:"))
      {
        dnAttributes = true;

        matchingRuleID =
             filterString.substring(startPos+4, equalPos-1);
      }
      else
      {
        matchingRuleID =
             filterString.substring(startPos+1, equalPos-1);
      }
    }
    else
    {
      int colonPos = filterString.indexOf(':',startPos);
      if (colonPos < 0)
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_COLON.
            get(filterString, startPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }


      String attrType = filterString.substring(startPos, colonPos);
      StringBuilder lowerType = new StringBuilder(attrType.length());

      int semicolonPos = attrType.indexOf(';');
      if (semicolonPos <0)
      {
        for (int i=0; i < attrType.length(); i++)
        {
          lowerType.append(Character.toLowerCase(attrType.charAt(i)));
        }
      }
      else
      {
        for (int i=0; i < semicolonPos; i++)
        {
          lowerType.append(Character.toLowerCase(attrType.charAt(i)));
        }

        int nextPos = attrType.indexOf(';', semicolonPos+1);
        while (nextPos > 0)
        {
          attributeOptions.add(attrType.substring(semicolonPos+1,
                                                  nextPos));
          semicolonPos = nextPos;
          nextPos = attrType.indexOf(';', semicolonPos+1);
        }

        attributeOptions.add(attrType.substring(semicolonPos+1));
      }


      // Get the attribute type for the specified name.
      attributeType = getAttributeType(attrType, lowerType);

      // If there is anything left, then it should be ":dn" and/or ":"
      // followed by the matching rule ID.
      if (colonPos < equalPos-1)
      {
        if (lowerLeftStr.startsWith(":dn:", colonPos))
        {
          dnAttributes = true;

          if (colonPos+4 < equalPos-1)
          {
            matchingRuleID =
                 filterString.substring(colonPos+4, equalPos-1);
          }
        }
        else
        {
          matchingRuleID =
               filterString.substring(colonPos+1, equalPos-1);
        }
      }
    }


    // Parse out the attribute value.
    byte[] valueBytes = getBytes(filterString.substring(equalPos+1,
                                                        endPos));
    boolean hasEscape = false;
    for (byte valueByte : valueBytes)
    {
      if (valueByte == 0x5C)
      {
        hasEscape = true;
        break;
      }
    }

    ByteString userValue;
    if (hasEscape)
    {
      ByteStringBuilder valueBuffer =
          new ByteStringBuilder(valueBytes.length);
      for (int i=0; i < valueBytes.length; i++)
      {
        if (valueBytes[i] == 0x5C) // The backslash character
        {
          // The next two bytes must be the hex characters that
          // comprise the binary value.
          if (i + 2 >= valueBytes.length)
          {
            LocalizableMessage message = ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                get(filterString, equalPos+i+1);
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                         message);
          }

          byte byteValue = 0;
          switch (valueBytes[++i])
          {
            case 0x30: // '0'
              break;
            case 0x31: // '1'
              byteValue = (byte) 0x10;
              break;
            case 0x32: // '2'
              byteValue = (byte) 0x20;
              break;
            case 0x33: // '3'
              byteValue = (byte) 0x30;
              break;
            case 0x34: // '4'
              byteValue = (byte) 0x40;
              break;
            case 0x35: // '5'
              byteValue = (byte) 0x50;
              break;
            case 0x36: // '6'
              byteValue = (byte) 0x60;
              break;
            case 0x37: // '7'
              byteValue = (byte) 0x70;
              break;
            case 0x38: // '8'
              byteValue = (byte) 0x80;
              break;
            case 0x39: // '9'
              byteValue = (byte) 0x90;
              break;
            case 0x41: // 'A'
            case 0x61: // 'a'
              byteValue = (byte) 0xA0;
              break;
            case 0x42: // 'B'
            case 0x62: // 'b'
              byteValue = (byte) 0xB0;
              break;
            case 0x43: // 'C'
            case 0x63: // 'c'
              byteValue = (byte) 0xC0;
              break;
            case 0x44: // 'D'
            case 0x64: // 'd'
              byteValue = (byte) 0xD0;
              break;
            case 0x45: // 'E'
            case 0x65: // 'e'
              byteValue = (byte) 0xE0;
              break;
            case 0x46: // 'F'
            case 0x66: // 'f'
              byteValue = (byte) 0xF0;
              break;
            default:
              LocalizableMessage message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
          }

          switch (valueBytes[++i])
          {
            case 0x30: // '0'
              break;
            case 0x31: // '1'
              byteValue |= (byte) 0x01;
              break;
            case 0x32: // '2'
              byteValue |= (byte) 0x02;
              break;
            case 0x33: // '3'
              byteValue |= (byte) 0x03;
              break;
            case 0x34: // '4'
              byteValue |= (byte) 0x04;
              break;
            case 0x35: // '5'
              byteValue |= (byte) 0x05;
              break;
            case 0x36: // '6'
              byteValue |= (byte) 0x06;
              break;
            case 0x37: // '7'
              byteValue |= (byte) 0x07;
              break;
            case 0x38: // '8'
              byteValue |= (byte) 0x08;
              break;
            case 0x39: // '9'
              byteValue |= (byte) 0x09;
              break;
            case 0x41: // 'A'
            case 0x61: // 'a'
              byteValue |= (byte) 0x0A;
              break;
            case 0x42: // 'B'
            case 0x62: // 'b'
              byteValue |= (byte) 0x0B;
              break;
            case 0x43: // 'C'
            case 0x63: // 'c'
              byteValue |= (byte) 0x0C;
              break;
            case 0x44: // 'D'
            case 0x64: // 'd'
              byteValue |= (byte) 0x0D;
              break;
            case 0x45: // 'E'
            case 0x65: // 'e'
              byteValue |= (byte) 0x0E;
              break;
            case 0x46: // 'F'
            case 0x66: // 'f'
              byteValue |= (byte) 0x0F;
              break;
            default:
              LocalizableMessage message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
          }

          valueBuffer.append(byteValue);
        }
        else
        {
          valueBuffer.append(valueBytes[i]);
        }
      }

      userValue = valueBuffer.toByteString();
    }
    else
    {
      userValue = ByteString.wrap(valueBytes);
    }

    // Make sure that the filter contains at least one of an attribute
    // type or a matching rule ID.  Also, construct the appropriate
    // attribute  value.
    if (attributeType == null)
    {
      if (matchingRuleID == null)
      {
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
            ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_AD_OR_MR.get(filterString, startPos));
      }

      MatchingRule mr = DirectoryServer.getMatchingRule(toLowerCase(matchingRuleID));
      if (mr == null)
      {
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
            ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_SUCH_MR.get(filterString, startPos, matchingRuleID));
      }
    }

    return new SearchFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                            attributeType, attributeOptions, userValue,
                            null, null, null, matchingRuleID,
                            dnAttributes);
  }

  private static AttributeType getAttributeType(String attrType, StringBuilder lowerType)
  {
    AttributeType attributeType = DirectoryServer.getAttributeType(lowerType.toString());
    if (attributeType == null)
    {
      String typeStr = attrType.substring(0, lowerType.length());
      attributeType = DirectoryServer.getDefaultAttributeType(typeStr);
    }
    return attributeType;
  }

  /**
   * Retrieves the filter type for this search filter.
   *
   * @return  The filter type for this search filter.
   */
  public FilterType getFilterType()
  {
    return filterType;
  }



  /**
   * Retrieves the set of filter components for this AND or OR filter.
   * The returned list can be modified by the caller.
   *
   * @return  The set of filter components for this AND or OR filter.
   */
  public Set<SearchFilter> getFilterComponents()
  {
    return filterComponents;
  }



  /**
   * Retrieves the filter component for this NOT filter.
   *
   * @return  The filter component for this NOT filter, or
   *          <CODE>null</CODE> if this is not a NOT filter.
   */
  public SearchFilter getNotComponent()
  {
    return notComponent;
  }



  /**
   * Retrieves the attribute type for this filter.
   *
   * @return  The attribute type for this filter, or <CODE>null</CODE>
   *          if there is none.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Retrieves the assertion value for this filter.
   *
   * @return  The assertion value for this filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getAssertionValue()
  {
    return assertionValue;
  }

  /**
   * Retrieves the subInitial element for this substring filter.
   *
   * @return  The subInitial element for this substring filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getSubInitialElement()
  {
    return subInitialElement;
  }



  /**
   * Retrieves the set of subAny elements for this substring filter.
   * The returned list may be altered by the caller.
   *
   * @return  The set of subAny elements for this substring filter.
   */
  public List<ByteString> getSubAnyElements()
  {
    return subAnyElements;
  }



  /**
   * Retrieves the subFinal element for this substring filter.
   *
   * @return  The subFinal element for this substring filter.
   */
  public ByteString getSubFinalElement()
  {
    return subFinalElement;
  }



  /**
   * Retrieves the matching rule ID for this extensible matching
   * filter.
   *
   * @return  The matching rule ID for this extensible matching
   *          filter.
   */
  public String getMatchingRuleID()
  {
    return matchingRuleID;
  }



  /**
   * Retrieves the dnAttributes flag for this extensible matching
   * filter.
   *
   * @return  The dnAttributes flag for this extensible matching
   *          filter.
   */
  public boolean getDNAttributes()
  {
    return dnAttributes;
  }



  /**
   * Indicates whether this search filter matches the provided entry.
   *
   * @param  entry  The entry for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this search filter matches the
   *          provided entry, or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  public boolean matchesEntry(Entry entry)
         throws DirectoryException
  {
    ConditionResult result = matchesEntryInternal(this, entry, 0);
    switch (result)
    {
      case TRUE:
        return true;
      case FALSE:
      case UNDEFINED:
        return false;
      default:
        logger.error(ERR_SEARCH_FILTER_INVALID_RESULT_TYPE, entry.getName(), this, result);
        return false;
    }
  }



  /**
   * Indicates whether the this filter matches the provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   * @param  depth           The current depth of the evaluation,
   *                         which is used to prevent infinite
   *                         recursion due to highly nested filters
   *                         and eventually running out of stack
   *                         space.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult matchesEntryInternal(
                               SearchFilter completeFilter,
                               Entry entry, int depth)
          throws DirectoryException
  {
    switch (filterType)
    {
      case AND:
        return processAND(completeFilter, entry, depth);

      case OR:
        return processOR(completeFilter, entry, depth);

      case NOT:
        return processNOT(completeFilter, entry, depth);

      case EQUALITY:
        return processEquality(completeFilter, entry);

      case SUBSTRING:
        return processSubstring(completeFilter, entry);

      case GREATER_OR_EQUAL:
        return processGreaterOrEqual(completeFilter, entry);

      case LESS_OR_EQUAL:
        return processLessOrEqual(completeFilter, entry);

      case PRESENT:
        return processPresent(completeFilter, entry);

      case APPROXIMATE_MATCH:
        return processApproximate(completeFilter, entry);

      case EXTENSIBLE_MATCH:
        return processExtensibleMatch(completeFilter, entry);


      default:
        // This is an invalid filter type.
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
            ERR_SEARCH_FILTER_INVALID_FILTER_TYPE.get(entry.getName(), this, filterType));
    }
  }



  /**
   * Indicates whether the this AND filter matches the provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   * @param  depth           The current depth of the evaluation,
   *                         which is used to prevent infinite
   *                         recursion due to highly nested filters
   *                         and eventually running out of stack
   *                         space.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processAND(SearchFilter completeFilter,
                                     Entry entry, int depth)
          throws DirectoryException
  {
    if (filterComponents == null)
    {
      // The set of subcomponents was null.  This is not allowed.
      LocalizableMessage message =
          ERR_SEARCH_FILTER_COMPOUND_COMPONENTS_NULL.
            get(entry.getName(), completeFilter, filterType);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
    else if (filterComponents.isEmpty())
    {
      // An AND filter with no elements like "(&)" is specified as
      // "undefined" in RFC 2251, but is considered one of the
      // TRUE/FALSE filters in RFC 4526, in which case we should
      // always return true.
      if (logger.isTraceEnabled())
      {
        logger.trace("Returning TRUE for LDAP TRUE " +
            "filter (&)");
      }
      return ConditionResult.TRUE;
    }
    else
    {
      // We will have to evaluate one or more subcomponents.  In
      // this case, first check our depth to make sure we're not
      // nesting too deep.
      if (depth >= MAX_NESTED_FILTER_DEPTH)
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_NESTED_TOO_DEEP.
            get(entry.getName(), completeFilter);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
      }

      for (SearchFilter f : filterComponents)
      {
        ConditionResult result =
             f.matchesEntryInternal(completeFilter, entry, depth + 1);
        switch (result)
        {
          case TRUE:
            break;
          case FALSE:
            if (logger.isTraceEnabled())
            {
              logger.trace(
                  "Returning FALSE for AND component %s in " +
                  "filter %s for entry %s",
                           f, completeFilter, entry.getName());
            }
            return result;
          case UNDEFINED:
            if (logger.isTraceEnabled())
            {
              logger.trace(
             "Undefined result for AND component %s in filter " +
             "%s for entry %s", f, completeFilter, entry.getName());
            }
            return result;
          default:
            LocalizableMessage message =
                ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                  get(entry.getName(), completeFilter, result);
            throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
        }
      }

      // If we have gotten here, then all the components must have
      // matched.
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Returning TRUE for AND component %s in filter %s " +
            "for entry %s", this, completeFilter, entry.getName());
      }
      return ConditionResult.TRUE;
    }
  }



  /**
   * Indicates whether the this OR filter matches the provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   * @param  depth           The current depth of the evaluation,
   *                         which is used to prevent infinite
   *                         recursion due to highly nested filters
   *                         and eventually running out of stack
   *                         space.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processOR(SearchFilter completeFilter,
                                    Entry entry, int depth)
          throws DirectoryException
  {
    if (filterComponents == null)
    {
      // The set of subcomponents was null.  This is not allowed.
      LocalizableMessage message =
          ERR_SEARCH_FILTER_COMPOUND_COMPONENTS_NULL.
            get(entry.getName(), completeFilter, filterType);
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
                     message);
    }
    else if (filterComponents.isEmpty())
    {
      // An OR filter with no elements like "(|)" is specified as
      // "undefined" in RFC 2251, but is considered one of the
      // TRUE/FALSE filters in RFC 4526, in which case we should
      // always return false.
      if (logger.isTraceEnabled())
      {
        logger.trace("Returning FALSE for LDAP FALSE " +
            "filter (|)");
      }
      return ConditionResult.FALSE;
    }
    else
    {
      // We will have to evaluate one or more subcomponents.  In
      // this case, first check our depth to make sure we're not
      // nesting too deep.
      if (depth >= MAX_NESTED_FILTER_DEPTH)
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_NESTED_TOO_DEEP.
            get(entry.getName(), completeFilter);
        throw new DirectoryException(
                       DirectoryServer.getServerErrorResultCode(),
                       message);
      }

      ConditionResult result = ConditionResult.FALSE;
      for (SearchFilter f : filterComponents)
      {
        switch (f.matchesEntryInternal(completeFilter, entry,
                               depth+1))
        {
          case TRUE:
            if (logger.isTraceEnabled())
            {
              logger.trace(
                "Returning TRUE for OR component %s in filter " +
                "%s for entry %s",
                f, completeFilter, entry.getName());
            }
            return ConditionResult.TRUE;
          case FALSE:
            break;
          case UNDEFINED:
            if (logger.isTraceEnabled())
            {
              logger.trace(
              "Undefined result for OR component %s in filter " +
              "%s for entry %s",
              f, completeFilter, entry.getName());
            }
            result = ConditionResult.UNDEFINED;
            break;
          default:
            LocalizableMessage message =
                ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                  get(entry.getName(), completeFilter, result);
            throw new
                 DirectoryException(
                      DirectoryServer.getServerErrorResultCode(),
                      message);
        }
      }


      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Returning %s for OR component %s in filter %s for " +
            "entry %s", result, this, completeFilter,
                        entry.getName());
      }
      return result;
    }
  }



  /**
   * Indicates whether the this NOT filter matches the provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   * @param  depth           The current depth of the evaluation,
   *                         which is used to prevent infinite
   *                         recursion due to highly nested filters
   *                         and eventually running out of stack
   *                         space.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processNOT(SearchFilter completeFilter,
                                     Entry entry, int depth)
          throws DirectoryException
  {
    if (notComponent == null)
    {
      // The NOT subcomponent was null.  This is not allowed.
      LocalizableMessage message = ERR_SEARCH_FILTER_NOT_COMPONENT_NULL.
          get(entry.getName(), completeFilter);
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
                     message);
    }
    else
    {
      // The subcomponent for the NOT filter can be an AND, OR, or
      // NOT filter that would require more nesting.  Make sure
      // that we don't go too deep.
      if (depth >= MAX_NESTED_FILTER_DEPTH)
      {
        LocalizableMessage message = ERR_SEARCH_FILTER_NESTED_TOO_DEEP.
            get(entry.getName(), completeFilter);
        throw new DirectoryException(
                       DirectoryServer.getServerErrorResultCode(),
                       message);
      }

      ConditionResult result =
           notComponent.matchesEntryInternal(completeFilter,
                                             entry, depth+1);
      switch (result)
      {
        case TRUE:
          if (logger.isTraceEnabled())
          {
            logger.trace(
               "Returning FALSE for NOT component %s in filter " +
               "%s for entry %s",
               notComponent, completeFilter, entry.getName());
          }
          return ConditionResult.FALSE;
        case FALSE:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Returning TRUE for NOT component %s in filter " +
                "%s for entry %s",
                notComponent, completeFilter, entry.getName());
          }
          return ConditionResult.TRUE;
        case UNDEFINED:
          if (logger.isTraceEnabled())
          {
            logger.trace(
              "Undefined result for NOT component %s in filter " +
              "%s for entry %s",
              notComponent, completeFilter, entry.getName());
          }
          return ConditionResult.UNDEFINED;
        default:
          LocalizableMessage message = ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
              get(entry.getName(), completeFilter, result);
          throw new
               DirectoryException(
                    DirectoryServer.getServerErrorResultCode(),
                    message);
      }
    }
  }



  /**
   * Indicates whether the this equality filter matches the provided
   * entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processEquality(SearchFilter completeFilter,
                                          Entry entry)
          throws DirectoryException
  {
    // Make sure that an attribute type has been defined.
    if (attributeType == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_EQUALITY_NO_ATTRIBUTE_TYPE.
            get(entry.getName(), toString());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // Make sure that an assertion value has been defined.
    if (assertionValue == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_EQUALITY_NO_ASSERTION_VALUE.
            get(entry.getName(), toString(), attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // See if the entry has an attribute with the requested type.
    List<Attribute> attrs = entry.getAttribute(attributeType,
                                               attributeOptions);
    if (attrs == null || attrs.isEmpty())
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Returning FALSE for equality component %s in " +
            "filter %s because entry %s didn't have attribute " +
            "type %s",
                     this, completeFilter, entry.getName(),
                     attributeType.getNameOrOID());
      }
      return ConditionResult.FALSE;
    }

    // Get the equality matching rule for the given attribute type
    MatchingRule matchingRule = attributeType.getEqualityMatchingRule();
    if (matchingRule == null)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(
         "Attribute type %s does not have an equality matching " +
         "rule -- returning undefined.",
         attributeType.getNameOrOID());
      }
      return ConditionResult.UNDEFINED;
    }

    // Iterate through all the attributes and see if we can find a match.
    ConditionResult result = ConditionResult.FALSE;
    for (Attribute a : attrs)
    {
      final ConditionResult cr = a.matchesEqualityAssertion(assertionValue);
      if (cr == ConditionResult.TRUE)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
              "Returning TRUE for equality component %s in filter %s " +
                  "for entry %s", this, completeFilter, entry.getName());
        }
        return ConditionResult.TRUE;
      }
      else if (cr == ConditionResult.UNDEFINED)
      {
        result = ConditionResult.UNDEFINED;
      }
    }

    if (logger.isTraceEnabled())
    {
      logger.trace(
          "Returning %s for equality component %s in filter %s " +
              "because entry %s didn't have attribute type %s with value %s",
          result, this, completeFilter, entry.getName(), attributeType.getNameOrOID(), assertionValue);
    }
    return result;
  }



  /**
   * Indicates whether the this substring filter matches the provided
   * entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processSubstring(
                                SearchFilter completeFilter,
                                Entry entry)
          throws DirectoryException
  {
    // Make sure that an attribute type has been defined.
    if (attributeType == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_SUBSTRING_NO_ATTRIBUTE_TYPE.
            get(entry.getName(), toString());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // Make sure that at least one substring element has been defined.
    if (subInitialElement == null &&
        subFinalElement == null &&
        (subAnyElements == null || subAnyElements.isEmpty()))
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_SUBSTRING_NO_SUBSTRING_COMPONENTS.
            get(entry.getName(), toString(), attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // See if the entry has an attribute with the requested type.
    List<Attribute> attrs = entry.getAttribute(attributeType, attributeOptions);
    if (attrs == null || attrs.isEmpty())
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Returning FALSE for substring component %s in " +
            "filter %s because entry %s didn't have attribute " +
            "type %s",
                     this, completeFilter, entry.getName(),
                     attributeType.getNameOrOID());
      }
      return ConditionResult.FALSE;
    }

    // Iterate through all the attributes and see if we can find a
    // match.
    ConditionResult result = ConditionResult.FALSE;
    for (Attribute a : attrs)
    {
      switch (a.matchesSubstring(subInitialElement,
                                 subAnyElements,
                                 subFinalElement))
      {
        case TRUE:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Returning TRUE for substring component %s in " +
                "filter %s for entry %s",
                         this, completeFilter, entry.getName());
          }
          return ConditionResult.TRUE;
        case FALSE:
          break;
        case UNDEFINED:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Undefined result encountered for substring " +
                "component %s in filter %s for entry %s",
                         this, completeFilter, entry.getName());
          }
          result = ConditionResult.UNDEFINED;
          break;
        default:
      }
    }

    if (logger.isTraceEnabled())
    {
      logger.trace(
          "Returning %s for substring component %s in filter " +
          "%s for entry %s",
          result, this, completeFilter, entry.getName());
    }
    return result;
  }



  /**
   * Indicates whether the this greater-or-equal filter matches the
   * provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processGreaterOrEqual(
                                SearchFilter completeFilter,
                                Entry entry)
          throws DirectoryException
  {
    // Make sure that an attribute type has been defined.
    if (attributeType == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_GREATER_OR_EQUAL_NO_ATTRIBUTE_TYPE.
            get(entry.getName(), toString());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // Make sure that an assertion value has been defined.
    if (assertionValue == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_GREATER_OR_EQUAL_NO_VALUE.
            get(entry.getName(), toString(), attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // See if the entry has an attribute with the requested type.
    List<Attribute> attrs = entry.getAttribute(attributeType, attributeOptions);
    if (attrs == null || attrs.isEmpty())
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("Returning FALSE for " +
            "greater-or-equal component %s in filter %s " +
            "because entry %s didn't have attribute type %s",
                     this, completeFilter, entry.getName(),
                     attributeType.getNameOrOID());
      }
      return ConditionResult.FALSE;
    }

    // Iterate through all the attributes and see if we can find a
    // match.
    ConditionResult result = ConditionResult.FALSE;
    for (Attribute a : attrs)
    {
      switch (a.greaterThanOrEqualTo(assertionValue))
      {
        case TRUE:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Returning TRUE for greater-or-equal component " +
                "%s in filter %s for entry %s",
                         this, completeFilter, entry.getName());
          }
          return ConditionResult.TRUE;
        case FALSE:
          break;
        case UNDEFINED:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Undefined result encountered for " +
                "greater-or-equal component %s in filter %s " +
                "for entry %s", this, completeFilter,
                entry.getName());
          }
          result = ConditionResult.UNDEFINED;
          break;
        default:
      }
    }

    if (logger.isTraceEnabled())
    {
      logger.trace(
          "Returning %s for greater-or-equal component %s in " +
          "filter %s for entry %s",
                   result, this, completeFilter, entry.getName());
    }
    return result;
  }



  /**
   * Indicates whether the this less-or-equal filter matches the
   * provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processLessOrEqual(
                                SearchFilter completeFilter,
                                Entry entry)
          throws DirectoryException
  {
    // Make sure that an attribute type has been defined.
    if (attributeType == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_LESS_OR_EQUAL_NO_ATTRIBUTE_TYPE.
            get(entry.getName(), toString());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // Make sure that an assertion value has been defined.
    if (assertionValue == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_LESS_OR_EQUAL_NO_ASSERTION_VALUE.
            get(entry.getName(), toString(), attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // See if the entry has an attribute with the requested type.
    List<Attribute> attrs =
         entry.getAttribute(attributeType, attributeOptions);
    if (attrs == null || attrs.isEmpty())
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Returning FALSE for less-or-equal component %s in " +
            "filter %s because entry %s didn't have attribute " +
            "type %s", this, completeFilter, entry.getName(),
                       attributeType.getNameOrOID());
      }
      return ConditionResult.FALSE;
    }

    // Iterate through all the attributes and see if we can find a
    // match.
    ConditionResult result = ConditionResult.FALSE;
    for (Attribute a : attrs)
    {
      switch (a.lessThanOrEqualTo(assertionValue))
      {
        case TRUE:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Returning TRUE for less-or-equal component %s " +
                "in filter %s for entry %s",
                         this, completeFilter, entry.getName());
          }
          return ConditionResult.TRUE;
        case FALSE:
          break;
        case UNDEFINED:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Undefined result encountered for " +
                    "less-or-equal component %s in filter %s " +
                    "for entry %s",
                    this, completeFilter, entry.getName());
          }
          result = ConditionResult.UNDEFINED;
          break;
        default:
      }
    }

    if (logger.isTraceEnabled())
    {
      logger.trace(
          "Returning %s for less-or-equal component %s in " +
          "filter %s for entry %s",
                   result, this, completeFilter, entry.getName());
    }
    return result;
  }



  /**
   * Indicates whether the this present filter matches the provided
   * entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processPresent(SearchFilter completeFilter,
                                         Entry entry)
          throws DirectoryException
  {
    // Make sure that an attribute type has been defined.
    if (attributeType == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_PRESENCE_NO_ATTRIBUTE_TYPE.
            get(entry.getName(), toString());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }


    // See if the entry has an attribute with the requested type.
    // If so, then it's a match.  If not, then it's not a match.
    ConditionResult result = ConditionResult.valueOf(
        entry.hasAttribute(attributeType, attributeOptions));
    if (logger.isTraceEnabled())
    {
      logger.trace(
          "Returning %s for presence component %s in filter %s for entry %s",
          result, this, completeFilter, entry.getName());
    }
    return result;
  }



  /**
   * Indicates whether the this approximate filter matches the
   * provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult processApproximate(
                                SearchFilter completeFilter,
                                Entry entry)
          throws DirectoryException
  {
    // Make sure that an attribute type has been defined.
    if (attributeType == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_APPROXIMATE_NO_ATTRIBUTE_TYPE.
            get(entry.getName(), toString());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // Make sure that an assertion value has been defined.
    if (assertionValue == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_APPROXIMATE_NO_ASSERTION_VALUE.
            get(entry.getName(), toString(), attributeType.getNameOrOID());
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }

    // See if the entry has an attribute with the requested type.
    List<Attribute> attrs =
         entry.getAttribute(attributeType, attributeOptions);
    if (attrs == null || attrs.isEmpty())
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Returning FALSE for approximate component %s in " +
            "filter %s because entry %s didn't have attribute " +
            "type %s", this, completeFilter, entry.getName(),
                       attributeType.getNameOrOID());
      }
      return ConditionResult.FALSE;
    }

    // Iterate through all the attributes and see if we can find a
    // match.
    ConditionResult result = ConditionResult.FALSE;
    for (Attribute a : attrs)
    {
      switch (a.approximatelyEqualTo(assertionValue))
      {
        case TRUE:
          if (logger.isTraceEnabled())
          {
            logger.trace(
               "Returning TRUE for approximate component %s in " +
               "filter %s for entry %s",
               this, completeFilter, entry.getName());
          }
          return ConditionResult.TRUE;
        case FALSE:
          break;
        case UNDEFINED:
          if (logger.isTraceEnabled())
          {
            logger.trace(
                "Undefined result encountered for approximate " +
                "component %s in filter %s for entry %s",
                         this, completeFilter, entry.getName());
          }
          result = ConditionResult.UNDEFINED;
          break;
        default:
      }
    }

    if (logger.isTraceEnabled())
    {
      logger.trace(
          "Returning %s for approximate component %s in filter " +
          "%s for entry %s",
          result, this, completeFilter, entry.getName());
    }
    return result;
  }



  /**
   * Indicates whether this extensibleMatch filter matches the
   * provided entry.
   *
   * @param  completeFilter  The complete filter in which this
   *                         extensibleMatch filter may be a
   *                         subcomponent.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return <CODE>TRUE</CODE> if this extensibleMatch filter matches
   *         the provided entry, <CODE>FALSE</CODE> if it does not, or
   *         <CODE>UNDEFINED</CODE> if the result cannot be
   *         determined.
   *
   * @throws  DirectoryException  If a problem occurs while evaluating
   *                              this filter against the provided
   *                              entry.
   */
  private ConditionResult processExtensibleMatch(
                               SearchFilter completeFilter,
                               Entry entry)
          throws DirectoryException
  {
    // We must have an assertion value for which to make the
    // determination.
    if (assertionValue == null)
    {
      LocalizableMessage message =
          ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_ASSERTION_VALUE.
            get(entry.getName(), completeFilter);
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                   message);
    }


    MatchingRule matchingRule = null;

    if (matchingRuleID != null)
    {
      matchingRule =
           DirectoryServer.getMatchingRule(
                toLowerCase(matchingRuleID));
      if (matchingRule == null)
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
              "Unknown matching rule %s defined in extensibleMatch " +
              "component of filter %s -- returning undefined.",
                    matchingRuleID, this);
        }
        return ConditionResult.UNDEFINED;
      }
    }
    else
    {
      if (attributeType == null)
      {
        LocalizableMessage message =
            ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_RULE_OR_TYPE.
              get(entry.getName(), completeFilter);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
      else
      {
        matchingRule = attributeType.getEqualityMatchingRule();
        if (matchingRule == null)
        {
          if (logger.isTraceEnabled())
          {
            logger.trace(
             "Attribute type %s does not have an equality matching " +
             "rule -- returning undefined.",
             attributeType.getNameOrOID());
          }
          return ConditionResult.UNDEFINED;
        }
      }
    }


    // If there is an attribute type, then check to see if there is a
    // corresponding matching rule use for the matching rule and
    // determine if it allows that attribute type.
    if (attributeType != null)
    {
      MatchingRuleUse mru =
           DirectoryServer.getMatchingRuleUse(matchingRule);
      if (mru != null && !mru.appliesToAttribute(attributeType))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace(
              "Attribute type %s is not allowed for use with " +
              "matching rule %s because of matching rule use " +
              "definition %s", attributeType.getNameOrOID(),
              matchingRule.getNameOrOID(), mru.getNameOrOID());
        }
        return ConditionResult.UNDEFINED;
      }
    }


    // Normalize the assertion value using the matching rule.
    Assertion assertion;
    try
    {
      assertion = matchingRule.getAssertion(assertionValue);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // We can't normalize the assertion value, so the result must be
      // undefined.
      return ConditionResult.UNDEFINED;
    }


    // If there is an attribute type, then we should only check for
    // that attribute.  Otherwise, we should check against all
    // attributes in the entry.
    ConditionResult result = ConditionResult.FALSE;
    if (attributeType == null)
    {
      for (List<Attribute> attrList :
           entry.getUserAttributes().values())
      {
        for (Attribute a : attrList)
        {
          for (ByteString v : a)
          {
            try
            {
              ByteString nv = matchingRule.normalizeAttributeValue(v);
              ConditionResult r = assertion.matches(nv);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  LocalizableMessage message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(entry.getName(), completeFilter, r);
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
            catch (Exception e)
            {
              logger.traceException(e);

              // We couldn't normalize one of the values.  If we don't
              // find a definite match, then we should return
              // undefined.
              result = ConditionResult.UNDEFINED;
            }
          }
        }
      }

      for (List<Attribute> attrList :
           entry.getOperationalAttributes().values())
      {
        for (Attribute a : attrList)
        {
          for (ByteString v : a)
          {
            try
            {
              ByteString nv = matchingRule.normalizeAttributeValue(v);
              ConditionResult r = assertion.matches(nv);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  LocalizableMessage message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(entry.getName(), completeFilter, r);
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
            catch (Exception e)
            {
              logger.traceException(e);

              // We couldn't normalize one of the values.  If we don't
              // find a definite match, then we should return
              // undefined.
              result = ConditionResult.UNDEFINED;
            }
          }
        }
      }

      Attribute a = entry.getObjectClassAttribute();
      for (ByteString v : a)
      {
        try
        {
          ByteString nv = matchingRule.normalizeAttributeValue(v);
          ConditionResult r = assertion.matches(nv);
          switch (r)
          {
            case TRUE:
              return ConditionResult.TRUE;
            case FALSE:
              break;
            case UNDEFINED:
              result = ConditionResult.UNDEFINED;
              break;
            default:
              LocalizableMessage message = ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                  get(entry.getName(), completeFilter, r);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // We couldn't normalize one of the values.  If we don't
          // find a definite match, then we should return undefined.
          result = ConditionResult.UNDEFINED;
        }
      }
    }
    else
    {
      List<Attribute> attrList = entry.getAttribute(attributeType,
                                                    attributeOptions);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (ByteString v : a)
          {
            try
            {
              ByteString nv = matchingRule.normalizeAttributeValue(v);
              ConditionResult r = assertion.matches(nv);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  LocalizableMessage message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(entry.getName(), completeFilter, r);
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
            catch (Exception e)
            {
              logger.traceException(e);

              // We couldn't normalize one of the values.  If we don't
              // find a definite match, then we should return
              // undefined.
              result = ConditionResult.UNDEFINED;
            }
          }
        }
      }
    }


    // If we've gotten here, then we know that there is no definite
    // match in the set of attributes.  If we should check DN
    // attributes, then do so.
    if (dnAttributes)
    {
      DN entryDN = entry.getName();
      int count = entryDN.size();
      for (int rdnIndex = 0; rdnIndex < count; rdnIndex++)
      {
        RDN rdn = entryDN.getRDN(rdnIndex);
        int numAVAs = rdn.getNumValues();
        for (int i=0; i < numAVAs; i++)
        {
          try
          {
            if (attributeType == null || attributeType.equals(rdn.getAttributeType(i)))
            {
              ByteString v = rdn.getAttributeValue(i);
              ByteString nv = matchingRule.normalizeAttributeValue(v);
              ConditionResult r = assertion.matches(nv);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  LocalizableMessage message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(entry.getName(), completeFilter, r);
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            // We couldn't normalize one of the values.  If we don't
            // find a definite match, then we should return undefined.
            result = ConditionResult.UNDEFINED;
          }
        }
      }
    }


    // If we've gotten here, then there is no definitive match, so
    // we'll either return FALSE or UNDEFINED.
    return result;
  }


  /**
   * Indicates whether this search filter is equal to the provided
   * object.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provide object is equal to this
   *          search filter, or <CODE>false</CODE> if it is not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (o == null)
    {
      return false;
    }

    if (o == this)
    {
      return true;
    }

    if (! (o instanceof SearchFilter))
    {
      return false;
    }


    SearchFilter f = (SearchFilter) o;
    if (filterType != f.filterType)
    {
      return false;
    }


    switch (filterType)
    {
      case AND:
      case OR:
        return andOrEqual(f);
      case NOT:
        return notComponent.equals(f.notComponent);
      case EQUALITY:
        return typeAndOptionsAndAssertionEqual(f);
      case SUBSTRING:
        return substringEqual(f);
      case GREATER_OR_EQUAL:
        return typeAndOptionsAndAssertionEqual(f);
      case LESS_OR_EQUAL:
        return typeAndOptionsAndAssertionEqual(f);
      case PRESENT:
        return attributeType.equals(f.attributeType) &&
                optionsEqual(attributeOptions, f.attributeOptions);
      case APPROXIMATE_MATCH:
        return typeAndOptionsAndAssertionEqual(f);
      case EXTENSIBLE_MATCH:
        return extensibleEqual(f);
      default:
        return false;
    }
  }

  private boolean andOrEqual(SearchFilter f)
  {
    if (filterComponents.size() != f.filterComponents.size())
    {
      return false;
    }

outerComponentLoop:
    for (SearchFilter outerFilter : filterComponents)
    {
      for (SearchFilter innerFilter : f.filterComponents)
      {
        if (outerFilter.equals(innerFilter))
        {
          continue outerComponentLoop;
        }
      }
      return false;
    }
    return true;
  }


  private boolean typeAndOptionsAndAssertionEqual(SearchFilter f)
  {
    return attributeType.equals(f.attributeType)
        && optionsEqual(attributeOptions, f.attributeOptions)
        && assertionValue.equals(f.assertionValue);
  }


  private boolean substringEqual(SearchFilter other)
  {
    if (! attributeType.equals(other.attributeType))
    {
      return false;
    }

    MatchingRule rule = attributeType.getSubstringMatchingRule();
    if (rule == null)
    {
      return false;
    }
    if (! optionsEqual(attributeOptions, other.attributeOptions))
    {
      return false;
    }

    boolean initialCheck = subInitialElement == null ?
        other.subInitialElement == null : subInitialElement.equals(other.subInitialElement);
    if (!initialCheck)
    {
      return false;
    }
    boolean finalCheck = subFinalElement == null ?
        other.subFinalElement == null : subFinalElement.equals(other.subFinalElement);
    if (!finalCheck)
    {
      return false;
    }
    boolean anyCheck = subAnyElements == null ?
        other.subAnyElements == null : subAnyElements.size() == other.subAnyElements.size();
    if (!anyCheck)
    {
      return false;
    }
    if (subAnyElements != null)
    {
      for (int i = 0; i < subAnyElements.size(); i++)
      {
        if (! subAnyElements.get(i).equals(other.subAnyElements.get(i)))
        {
          return false;
        }
      }
    }
    return true;
  }

  private boolean extensibleEqual(SearchFilter f)
  {
    if (attributeType == null)
    {
      if (f.attributeType != null)
      {
        return false;
      }
    }
    else
    {
      if (! attributeType.equals(f.attributeType))
      {
        return false;
      }

      if (! optionsEqual(attributeOptions, f.attributeOptions))
      {
        return false;
      }
    }

    if (dnAttributes != f.dnAttributes)
    {
      return false;
    }

    if (matchingRuleID == null)
    {
      if (f.matchingRuleID != null)
      {
        return false;
      }
    }
    else
    {
      if (! matchingRuleID.equals(f.matchingRuleID))
      {
        return false;
      }
    }

    if (assertionValue == null)
    {
      if (f.assertionValue != null)
      {
        return false;
      }
    }
    else
    {
      if (matchingRuleID == null)
      {
        if (! assertionValue.equals(f.assertionValue))
        {
          return false;
        }
      }
      else
      {
        MatchingRule mrule = DirectoryServer.getMatchingRule(toLowerCase(matchingRuleID));
        if (mrule == null)
        {
          return false;
        }
        else
        {
          try
          {
            Assertion assertion = mrule.getAssertion(f.assertionValue);
            return assertion.matches(mrule.normalizeAttributeValue(assertionValue)).toBoolean();
          }
          catch (Exception e)
          {
            return false;
          }
        }
      }
    }

    return true;
  }


  /**
   * Indicates whether the two provided sets of attribute options
   * should be considered equal.
   *
   * @param  options1  The first set of attribute options for which to
   *                   make the determination.
   * @param  options2  The second set of attribute options for which
   *                   to make the determination.
   *
   * @return  {@code true} if the sets of attribute options are equal,
   *          or {@code false} if not.
   */
  private static boolean optionsEqual(Set<String> options1,
                                      Set<String> options2)
  {
    if (options1 == null || options1.isEmpty())
    {
      return options2 == null || options2.isEmpty();
    }
    else if (options2 == null || options2.isEmpty())
    {
      return false;
    }
    else
    {
      if (options1.size() != options2.size())
      {
        return false;
      }

      HashSet<String> lowerOptions = new HashSet<>(options1.size());
      for (String option : options1)
      {
        lowerOptions.add(toLowerCase(option));
      }

      for (String option : options2)
      {
        if (! lowerOptions.remove(toLowerCase(option)))
        {
          return false;
        }
      }

      return lowerOptions.isEmpty();
    }
  }


  /**
   * Retrieves the hash code for this search filter.
   *
   * @return  The hash code for this search filter.
   */
  @Override
  public int hashCode()
  {
    switch (filterType)
    {
      case AND:
      case OR:
        int hashCode = 0;

        for (SearchFilter filterComp : filterComponents)
        {
          hashCode += filterComp.hashCode();
        }

        return hashCode;
      case NOT:
        return notComponent.hashCode();
      case EQUALITY:
        return typeAndAssertionHashCode();
      case SUBSTRING:
        return substringHashCode();
      case GREATER_OR_EQUAL:
        return typeAndAssertionHashCode();
      case LESS_OR_EQUAL:
        return typeAndAssertionHashCode();
      case PRESENT:
        return attributeType.hashCode();
      case APPROXIMATE_MATCH:
        return typeAndAssertionHashCode();
      case EXTENSIBLE_MATCH:
        return extensibleHashCode();
      default:
        return 1;
    }
  }


  /** Returns the hash code for extensible filter. */
  private int extensibleHashCode()
  {
    int hashCode = 0;

    if (attributeType != null)
    {
      hashCode += attributeType.hashCode();
    }

    if (dnAttributes)
    {
      hashCode++;
    }

    if (matchingRuleID != null)
    {
      hashCode += matchingRuleID.hashCode();
    }

    if (assertionValue != null)
    {
      hashCode += assertionValue.hashCode();
    }
    return hashCode;
  }


  private int typeAndAssertionHashCode()
  {
    return attributeType.hashCode() + assertionValue.hashCode();
  }

  /** Returns hash code to use for substring filter. */
  private int substringHashCode()
  {
    int hashCode = attributeType.hashCode();
    if (subInitialElement != null)
    {
      hashCode += subInitialElement.hashCode();
    }
    if (subAnyElements != null)
    {
      for (ByteString e : subAnyElements)
      {
        hashCode += e.hashCode();
      }
    }
    if (subFinalElement != null)
    {
      hashCode += subFinalElement.hashCode();
    }
    return hashCode;
  }



  /**
   * Retrieves a string representation of this search filter.
   *
   * @return  A string representation of this search filter.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this search filter to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    switch (filterType)
    {
      case AND:
        buffer.append("(&");
        for (SearchFilter f : filterComponents)
        {
          f.toString(buffer);
        }
        buffer.append(")");
        break;
      case OR:
        buffer.append("(|");
        for (SearchFilter f : filterComponents)
        {
          f.toString(buffer);
        }
        buffer.append(")");
        break;
      case NOT:
        buffer.append("(!");
        notComponent.toString(buffer);
        buffer.append(")");
        break;
      case EQUALITY:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());
        appendOptions(buffer);
        buffer.append("=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case SUBSTRING:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());
        appendOptions(buffer);
        buffer.append("=");

        if (subInitialElement != null)
        {
          valueToFilterString(buffer, subInitialElement);
        }

        if (subAnyElements != null && !subAnyElements.isEmpty())
        {
          for (ByteString s : subAnyElements)
          {
            buffer.append("*");
            valueToFilterString(buffer, s);
          }
        }

        buffer.append("*");

        if (subFinalElement != null)
        {
          valueToFilterString(buffer, subFinalElement);
        }

        buffer.append(")");
        break;
      case GREATER_OR_EQUAL:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());
        appendOptions(buffer);
        buffer.append(">=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case LESS_OR_EQUAL:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());
        appendOptions(buffer);
        buffer.append("<=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case PRESENT:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());
        appendOptions(buffer);
        buffer.append("=*)");
        break;
      case APPROXIMATE_MATCH:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());
        appendOptions(buffer);
        buffer.append("~=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case EXTENSIBLE_MATCH:
        buffer.append("(");

        if (attributeType != null)
        {
          buffer.append(attributeType.getNameOrOID());
          appendOptions(buffer);
        }

        if (dnAttributes)
        {
          buffer.append(":dn");
        }

        if (matchingRuleID != null)
        {
          buffer.append(":");
          buffer.append(matchingRuleID);
        }

        buffer.append(":=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
    }
  }


  private void appendOptions(StringBuilder buffer)
  {
    if (attributeOptions != null && !attributeOptions.isEmpty())
    {
      for (String option : attributeOptions)
      {
        buffer.append(";");
        buffer.append(option);
      }
    }
  }



  /**
   * Appends a properly-cleaned version of the provided value to the
   * given buffer so that it can be safely used in string
   * representations of this search filter.  The formatting changes
   * that may be performed will be in compliance with the
   * specification in RFC 2254.
   *
   * @param  buffer  The buffer to which the "safe" version of the
   *                 value will be appended.
   * @param  value   The value to be appended to the buffer.
   */
  private void valueToFilterString(StringBuilder buffer,
                                   ByteString value)
  {
    if (value == null)
    {
      return;
    }


    // Get the binary representation of the value and iterate through
    // it to see if there are any unsafe characters.  If there are,
    // then escape them and replace them with a two-digit hex
    // equivalent.
    buffer.ensureCapacity(buffer.length() + value.length());
    byte b;
    for (int i = 0; i < value.length(); i++)
    {
      b = value.byteAt(i);
      if (((b & 0x7F) != b) ||  // Not 7-bit clean
          (b <= 0x1F) ||        // Below the printable character range
          (b == 0x28) ||        // Open parenthesis
          (b == 0x29) ||        // Close parenthesis
          (b == 0x2A) ||        // Asterisk
          (b == 0x5C) ||        // Backslash
          (b == 0x7F))          // Delete character
      {
        buffer.append("\\");
        buffer.append(byteToHex(b));
      }
      else
      {
        buffer.append((char) b);
      }
    }
  }

  /**
   * Returns the {@code objectClass} presence filter {@code (objectClass=*)}.
   *
   * @return The {@code objectClass} presence filter {@code (objectClass=*)}.
   */
  public static SearchFilter objectClassPresent()
  {
    if (objectClassPresent == null)
    {
      try
      {
        objectClassPresent = SearchFilter.createFilterFromString("(objectclass=*)");
      }
      catch (DirectoryException canNeverHappen)
      {
        logger.traceException(canNeverHappen);
      }
    }
    return objectClassPresent;
  }
}

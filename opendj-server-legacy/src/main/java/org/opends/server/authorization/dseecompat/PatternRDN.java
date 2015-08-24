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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;

/**
 * This class is used to match RDN patterns containing wildcards in either
 * the attribute types or the attribute values.
 * Substring matching on the attribute types is not supported.
 */
public class PatternRDN
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicate whether the RDN contains a wildcard in any of its attribute types. */
  private boolean hasTypeWildcard;
  /** The set of attribute type patterns. */
  private String[] typePatterns;
  /**
   * The set of attribute value patterns.
   * The value pattern is split into a list according to the positions of any
   * wildcards.  For example, the value "A*B*C" is represented as a
   * list of three elements A, B and C.  The value "A" is represented as
   * a list of one element A.  The value "*A*" is represented as a list
   * of three elements "", A and "".
   */
  private ArrayList<ArrayList<ByteString>> valuePatterns;
  /** The number of attribute-value pairs in this RDN pattern. */
  private int numValues;


  /**
   * Create a new RDN pattern composed of a single attribute-value pair.
   * @param type The attribute type pattern.
   * @param valuePattern The attribute value pattern.
   * @param dnString The DN pattern containing the attribute-value pair.
   * @throws DirectoryException If the attribute-value pair is not valid.
   */
  public PatternRDN(String type, ArrayList<ByteString> valuePattern, String dnString)
       throws DirectoryException
  {
    // Only Whole-Type wildcards permitted.
    if (type.contains("*"))
    {
      if (!type.equals("*"))
      {
        LocalizableMessage message =
            WARN_PATTERN_DN_TYPE_CONTAINS_SUBSTRINGS.get(dnString);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
      hasTypeWildcard = true;
    }

    numValues = 1;
    typePatterns = new String[] { type };
    valuePatterns = newArrayList(valuePattern);
  }


  /**
   * Add another attribute-value pair to the pattern.
   * @param type The attribute type pattern.
   * @param valuePattern The attribute value pattern.
   * @param dnString The DN pattern containing the attribute-value pair.
   * @throws DirectoryException If the attribute-value pair is not valid.
   * @return  <CODE>true</CODE> if the type-value pair was added to
   *          this RDN, or <CODE>false</CODE> if it was not (e.g., it
   *          was already present).
   */
  public boolean addValue(String type, ArrayList<ByteString> valuePattern,
                          String dnString)
       throws DirectoryException
  {
    // No type wildcards permitted in multi-valued patterns.
    if (hasTypeWildcard || type.contains("*"))
    {
      LocalizableMessage message =
          WARN_PATTERN_DN_TYPE_WILDCARD_IN_MULTIVALUED_RDN.get(dnString);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }

    numValues++;

    String[] newTypes = new String[numValues];
    System.arraycopy(typePatterns, 0, newTypes, 0,
                     typePatterns.length);
    newTypes[typePatterns.length] = type;
    typePatterns = newTypes;

    valuePatterns.add(valuePattern);

    return true;
  }


  /**
   * Retrieves the number of attribute-value pairs contained in this
   * RDN pattern.
   *
   * @return  The number of attribute-value pairs contained in this
   *          RDN pattern.
   */
  public int getNumValues()
  {
    return numValues;
  }


  /**
   * Determine whether a given RDN matches the pattern.
   * @param rdn The RDN to be matched.
   * @return true if the RDN matches the pattern.
   */
  public boolean matchesRDN(RDN rdn)
  {
    if (getNumValues() == 1)
    {
      // Check for ",*," matching any RDN.
      if (typePatterns[0].equals("*") && valuePatterns.get(0) == null)
      {
        return true;
      }

      if (rdn.getNumValues() != 1)
      {
        return false;
      }

      AttributeType thatType = rdn.getAttributeType(0);
      if (!typePatterns[0].equals("*"))
      {
        AttributeType thisType =
             DirectoryServer.getAttributeType(typePatterns[0].toLowerCase());
        if (thisType == null || !thisType.equals(thatType))
        {
          return false;
        }
      }

      return matchValuePattern(valuePatterns.get(0), thatType,
                               rdn.getAttributeValue(0));
    }

    if (hasTypeWildcard)
    {
      return false;
    }

    if (numValues != rdn.getNumValues())
    {
      return false;
    }

    // Sort the attribute-value pairs by attribute type.
    TreeMap<String,ArrayList<ByteString>> patternMap = new TreeMap<>();
    TreeMap<String, ByteString> rdnMap = new TreeMap<>();

    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      rdnMap.put(rdn.getAttributeType(i).getNameOrOID(),
                 rdn.getAttributeValue(i));
    }

    for (int i = 0; i < numValues; i++)
    {
      String lowerName = typePatterns[i].toLowerCase();
      AttributeType type = DirectoryServer.getAttributeType(lowerName);
      if (type == null)
      {
        return false;
      }
      patternMap.put(type.getNameOrOID(), valuePatterns.get(i));
    }

    Set<String> patternKeys = patternMap.keySet();
    Set<String> rdnKeys = rdnMap.keySet();
    Iterator<String> patternKeyIter = patternKeys.iterator();
    for (String rdnKey : rdnKeys)
    {
      if (!rdnKey.equals(patternKeyIter.next()))
      {
        return false;
      }

      if (!matchValuePattern(patternMap.get(rdnKey),
                             DirectoryServer.getAttributeType(rdnKey),
                             rdnMap.get(rdnKey)))
      {
        return false;
      }
    }

    return true;
  }


  /**
   * Determine whether a value pattern matches a given attribute-value pair.
   * @param pattern The value pattern where each element of the list is a
   *                substring of the pattern appearing between wildcards.
   * @param type The attribute type of the attribute-value pair.
   * @param value The value of the attribute-value pair.
   * @return true if the value pattern matches the attribute-value pair.
   */
  private boolean matchValuePattern(List<ByteString> pattern,
                                    AttributeType type,
                                    ByteString value)
  {
    if (pattern == null)
    {
      return true;
    }

    try
    {
      if (pattern.size() == 1)
      {
        // Handle this just like an equality filter.
        MatchingRule rule = type.getEqualityMatchingRule();
        ByteString thatNormValue = rule.normalizeAttributeValue(value);
        return rule.getAssertion(pattern.get(0)).matches(thatNormValue).toBoolean();
      }

      // Handle this just like a substring filter.
      ByteString subInitial = pattern.get(0);
      if (subInitial.length() == 0)
      {
        subInitial = null;
      }

      ByteString subFinal = pattern.get(pattern.size() - 1);
      if (subFinal.length() == 0)
      {
        subFinal = null;
      }

      List<ByteString> subAnyElements;
      if (pattern.size() > 2)
      {
        subAnyElements = pattern.subList(1, pattern.size()-1);
      }
      else
      {
        subAnyElements = null;
      }

      Attribute attr = Attributes.create(type, value);
      return attr.matchesSubstring(subInitial, subAnyElements, subFinal).toBoolean();
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return false;
    }
  }

}

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
package org.opends.server.schema;



import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;



/**
 * This class implements the authPasswordMatch matching rule defined in RFC
 * 3112.
 */
class AuthPasswordEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * Creates a new instance of this authPasswordMatch matching rule.
   */
  public AuthPasswordEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return EMR_AUTH_PASSWORD_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_AUTH_PASSWORD_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return EMR_AUTH_PASSWORD_DESCRIPTION;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_AUTH_PASSWORD_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    // We will not alter the value in any way.
    return value.toByteString();
  }



  /**
   * Indicates whether the provided attribute value should be considered a match
   * for the given assertion value.  This will only be used for the purpose of
   * extensible matching.  Other forms of matching against equality matching
   * rules should use the <CODE>areEqual</CODE> method.
   *
   * @param  attributeValue  The attribute value in a form that has been
   *                         normalized according to this matching rule.
   * @param  assertionValue  The assertion value in a form that has been
   *                         normalized according to this matching rule.
   *
   * @return  <CODE>true</CODE> if the attribute value should be considered a
   *          match for the provided assertion value, or <CODE>false</CODE> if
   *          not.
   */
  @Override
  public ConditionResult valuesMatch(ByteSequence attributeValue,
                                     ByteSequence assertionValue)
  {
    // We must be able to decode the attribute value using the authentication
    // password syntax.
    StringBuilder[] authPWComponents;
    try
    {
      authPWComponents =
           AuthPasswordSyntax.decodeAuthPassword(attributeValue.toString());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return ConditionResult.FALSE;
    }


    // The first element of the array will be the scheme.  Make sure that we
    // support the requested scheme.
    PasswordStorageScheme storageScheme =
         DirectoryServer.getAuthPasswordStorageScheme(
              authPWComponents[0].toString());
    if (storageScheme == null)
    {
      // It's not a scheme that we can support.
      return ConditionResult.FALSE;
    }


    // We support the scheme, so make the determination.
    if (storageScheme.authPasswordMatches(assertionValue,
                                          authPWComponents[1].toString(),
                                          authPWComponents[2].toString()))
    {
      return ConditionResult.TRUE;
    }
    else
    {
      return ConditionResult.FALSE;
    }
  }



  /**
   * Generates a hash code for the provided attribute value.  This version of
   * the method will simply create a hash code from the normalized form of the
   * attribute value.  For matching rules explicitly designed to work in cases
   * where byte-for-byte comparisons of normalized values is not sufficient for
   * determining equality (e.g., if the associated attribute syntax is based on
   * hashed or encrypted values), then this method must be overridden to provide
   * an appropriate implementation for that case.
   *
   * @param  attributeValue  The attribute value for which to generate the hash
   *                         code.
   *
   * @return  The hash code generated for the provided attribute value.
   */
  @Override
  public int generateHashCode(ByteSequence attributeValue)
  {
    // Because of the variable encoding that may be used, we have no way of
    // comparing two auth password values by hash code and therefore we'll
    // always return the same value so that the valuesMatch method will be
    // invoked to make the determination.
    return 1;
  }
}


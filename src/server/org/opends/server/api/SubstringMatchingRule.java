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
 */
package org.opends.server.api;



import java.util.List;

import org.opends.server.types.*;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for substring matching.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class SubstringMatchingRule
        extends AbstractMatchingRule
        implements MatchingRule
{
  /**
   * Normalizes the provided value fragment into a form that can be
   * used to efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DirectoryException  If the provided value fragment is
   *                              not acceptable according to the
   *                              associated syntax.
   */
  public abstract ByteString normalizeSubstring(
      ByteSequence substring) throws DirectoryException;



  /**
   * Determines whether the provided value matches the given substring
   * filter components.  Note that any of the substring filter
   * components may be {@code null} but at least one of them must be
   * non-{@code null}.
   *
   * @param  value           The normalized value against which to
   *                         compare the substring components.
   * @param  subInitial      The normalized substring value fragment
   *                         that should appear at the beginning of
   *                         the target value.
   * @param  subAnyElements  The normalized substring value fragments
   *                         that should appear in the middle of the
   *                         target value.
   * @param  subFinal        The normalized substring value fragment
   *                         that should appear at the end of the
   *                         target value.
   *
   * @return  {@code true} if the provided value does match the given
   *          substring components, or {@code false} if not.
   */
  public boolean valueMatchesSubstring(ByteSequence value,
                                    ByteSequence subInitial,
                                    List<ByteSequence> subAnyElements,
                                    ByteSequence subFinal)
  {
    int valueLength = value.length();

    int pos = 0;
    if (subInitial != null)
    {
      int initialLength = subInitial.length();
      if (initialLength > valueLength)
      {
        return false;
      }

      for (; pos < initialLength; pos++)
      {
        if (subInitial.byteAt(pos) != value.byteAt(pos))
        {
          return false;
        }
      }
    }


    if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
    {
      for (ByteSequence element : subAnyElements)
      {
        int anyLength = element.length();
        if(anyLength == 0)
            continue;
        int end = valueLength - anyLength;
        boolean match = false;
        for (; pos <= end; pos++)
        {
          if (element.byteAt(0) == value.byteAt(pos))
          {
            boolean subMatch = true;
            for (int i=1; i < anyLength; i++)
            {
              if (element.byteAt(i) != value.byteAt(pos+i))
              {
                subMatch = false;
                break;
              }
            }

            if (subMatch)
            {
              match = subMatch;
              break;
            }
          }
        }

        if (match)
        {
          pos += anyLength;
        }
        else
        {
          return false;
        }
      }
    }


    if (subFinal != null)
    {
      int finalLength = subFinal.length();

      if ((valueLength - finalLength) < pos)
      {
        return false;
      }

      pos = valueLength - finalLength;
      for (int i=0; i < finalLength; i++,pos++)
      {
        if (subFinal.byteAt(i) != value.byteAt(pos))
        {
          return false;
        }
      }
    }


    return true;
  }
}


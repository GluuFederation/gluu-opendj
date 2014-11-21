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
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.types;



import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;


/**
 * This class defines a data structure for storing and interacting
 * with the distinguished names associated with entries in the
 * Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class DN
       implements Comparable<DN>, Serializable
{
/*
 * NOTE:  Any changes to the set of non-static public methods defined
 *        in this class or the arguments that they contain must also
 *        be made in the org.opends.server.interop.LazyDN package to
 *        ensure continued interoperability with third-party
 *        applications that rely on that functionality.
 */



  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * A singleton instance of the null DN (a DN with no components).
   */
  public static final DN NULL_DN = new DN();



  /**
   * The serial version identifier required to satisfy the compiler
   * because this class implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was
   * generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = 1184263456768819888L;



  // The number of RDN components that comprise this DN.
  private final int numComponents;

  // The set of RDN components that comprise this DN, arranged with
  // the suffix as the last element.
  private final RDN[] rdnComponents;

  // The string representation of this DN.
  private String dnString;

  // The normalized string representation of this DN.
  private String normalizedDN;



  /**
   * Creates a new DN with no RDN components (i.e., a null DN or root
   * DSE).
   */
  public DN()
  {
    this(new RDN[0]);
  }



  /**
   * Creates a new DN with the provided set of RDNs, arranged with the
   * suffix as the last element.
   *
   * @param  rdnComponents  The set of RDN components that make up
   *                        this DN.
   */
  public DN(RDN[] rdnComponents)
  {
    if (rdnComponents == null)
    {
      this.rdnComponents = new RDN[0];
    }
    else
    {
      this.rdnComponents = rdnComponents;
    }

    numComponents = this.rdnComponents.length;
    dnString      = null;
    normalizedDN  = null;
  }



  /**
   * Creates a new DN with the provided set of RDNs, arranged with the
   * suffix as the last element.
   *
   * @param  rdnComponents  The set of RDN components that make up
   *                        this DN.
   */
  public DN(List<RDN> rdnComponents)
  {
    if ((rdnComponents == null) || rdnComponents.isEmpty())
    {
      this.rdnComponents = new RDN[0];
    }
    else
    {
      this.rdnComponents = new RDN[rdnComponents.size()];
      rdnComponents.toArray(this.rdnComponents);
    }

    numComponents = this.rdnComponents.length;
    dnString      = null;
    normalizedDN  = null;
  }



  /**
   * Creates a new DN with the given RDN below the specified parent.
   *
   * @param  rdn       The RDN to use for the new DN.  It must not be
   *                   {@code null}.
   * @param  parentDN  The DN of the entry below which the new DN
   *                   should exist. It must not be {@code null}.
   */
  public DN(RDN rdn, DN parentDN)
  {
    ensureNotNull(rdn, parentDN);
    if (parentDN.isNullDN())
    {
      rdnComponents = new RDN[] { rdn };
    }
    else
    {
      rdnComponents = new RDN[parentDN.numComponents + 1];
      rdnComponents[0] = rdn;
      System.arraycopy(parentDN.rdnComponents, 0, rdnComponents, 1,
                       parentDN.numComponents);
    }

    numComponents = this.rdnComponents.length;
    dnString      = null;
    normalizedDN  = null;
  }



  /**
   * Retrieves a singleton instance of the null DN.
   *
   * @return  A singleton instance of the null DN.
   */
  public static DN nullDN()
  {
    return NULL_DN;
  }



  /**
   * Indicates whether this represents a null DN.  This could target
   * the root DSE for the Directory Server, or the authorization DN
   * for an anonymous or unauthenticated client.
   *
   * @return  <CODE>true</CODE> if this does represent a null DN, or
   *          <CODE>false</CODE> if it does not.
   */
  public boolean isNullDN()
  {
    return (numComponents == 0);
  }



  /**
   * Retrieves the number of RDN components for this DN.
   *
   * @return  The number of RDN components for this DN.
   */
  public int getNumComponents()
  {
    return numComponents;
  }



  /**
   * Retrieves the outermost RDN component for this DN (i.e., the one
   * that is furthest from the suffix).
   *
   * @return  The outermost RDN component for this DN, or
   *          <CODE>null</CODE> if there are no RDN components in the
   *          DN.
   */
  public RDN getRDN()
  {
    if (numComponents == 0)
    {
      return null;
    }
    else
    {
      return rdnComponents[0];
    }
  }



  /**
   * Retrieves the RDN component at the specified position in the set
   * of components for this DN.
   *
   * @param  pos  The position of the RDN component to retrieve.
   *
   * @return  The RDN component at the specified position in the set
   *          of components for this DN.
   */
  public RDN getRDN(int pos)
  {
    return rdnComponents[pos];
  }



  /**
   * Retrieves the DN of the entry that is the immediate parent for
   * this entry.  Note that this method does not take the server's
   * naming context configuration into account when making the
   * determination.
   *
   * @return  The DN of the entry that is the immediate parent for
   *          this entry, or <CODE>null</CODE> if the entry with this
   *          DN does not have a parent.
   */
  public DN getParent()
  {
    if (numComponents <= 1)
    {
      return null;
    }

    RDN[] parentComponents = new RDN[numComponents-1];
    System.arraycopy(rdnComponents, 1, parentComponents, 0,
                     numComponents-1);
    return new DN(parentComponents);
  }



  /**
   * Retrieves the DN of the entry that is the immediate parent for
   * this entry.  This method does take the server's naming context
   * configuration into account, so if the current DN is a naming
   * context for the server, then it will not be considered to have a
   * parent.
   *
   * @return  The DN of the entry that is the immediate parent for
   *          this entry, or <CODE>null</CODE> if the entry with this
   *          DN does not have a parent (either because there is only
   *          a single RDN component or because this DN is a suffix
   *          defined in the server).
   */
  public DN getParentDNInSuffix()
  {
    if ((numComponents <= 1) ||
        DirectoryServer.isNamingContext(this))
    {
      return null;
    }

    RDN[] parentComponents = new RDN[numComponents-1];
    System.arraycopy(rdnComponents, 1, parentComponents, 0,
                     numComponents-1);
    return new DN(parentComponents);
  }



  /**
   * Creates a new DN that is a child of this DN, using the specified
   * RDN.
   *
   * @param  rdn  The RDN for the child of this DN.
   *
   * @return  A new DN that is a child of this DN, using the specified
   *          RDN.
   */
  public DN concat(RDN rdn)
  {
    RDN[] newComponents = new RDN[rdnComponents.length+1];
    newComponents[0] = rdn;
    System.arraycopy(rdnComponents, 0, newComponents, 1,
                     rdnComponents.length);

    return new DN(newComponents);
  }



  /**
   * Creates a new DN that is a descendant of this DN, using the
   * specified RDN components.
   *
   * @param  rdnComponents  The RDN components for the descendant of
   *                        this DN.
   *
   * @return  A new DN that is a descendant of this DN, using the
   *          specified RDN components.
   */
  public DN concat(RDN[] rdnComponents)
  {
    RDN[] newComponents =
         new RDN[rdnComponents.length+this.rdnComponents.length];
    System.arraycopy(rdnComponents, 0, newComponents, 0,
                     rdnComponents.length);
    System.arraycopy(this.rdnComponents, 0, newComponents,
                     rdnComponents.length, this.rdnComponents.length);

    return new DN(newComponents);
  }



  /**
   * Creates a new DN that is a descendant of this DN, using the
   * specified DN as a relative base DN.  That is, the resulting DN
   * will first have the components of the provided DN followed by the
   * components of this DN.
   *
   * @param  relativeBaseDN  The relative base DN to concatenate onto
   *                         this DN.
   *
   * @return  A new DN that is a descendant of this DN, using the
   *          specified DN as a relative base DN.
   */
  public DN concat(DN relativeBaseDN)
  {
    RDN[] newComponents =
               new RDN[rdnComponents.length+
                       relativeBaseDN.rdnComponents.length];

    System.arraycopy(relativeBaseDN.rdnComponents, 0, newComponents,
                     0, relativeBaseDN.rdnComponents.length);
    System.arraycopy(rdnComponents, 0, newComponents,
                     relativeBaseDN.rdnComponents.length,
                     rdnComponents.length);

    return new DN(newComponents);
  }



  /**
   * Indicates whether this DN is a descendant of the provided DN
   * (i.e., that the RDN components of the provided DN are the
   * same as the last RDN components for this DN).  Note that if
   * this DN equals the provided DN it is still considered to be
   * a descendant of the provided DN by this method as both then
   * reside within the same subtree.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this DN is a descendant of the
   *          provided DN, or <CODE>false</CODE> if not.
   */
  public boolean isDescendantOf(DN dn)
  {
    int offset = numComponents - dn.numComponents;
    if (offset < 0)
    {
      return false;
    }

    for (int i=0; i < dn.numComponents; i++)
    {
      if (! rdnComponents[i+offset].equals(dn.rdnComponents[i]))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Indicates whether this DN is an ancestor of the provided DN
   * (i.e., that the RDN components of this DN are the same as the
   * last RDN components for the provided DN).
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this DN is an ancestor of the
   *          provided DN, or <CODE>false</CODE> if not.
   */
  public boolean isAncestorOf(DN dn)
  {
    int offset = dn.numComponents - numComponents;
    if (offset < 0)
    {
      return false;
    }

    for (int i=0; i < numComponents; i++)
    {
      if (! rdnComponents[i].equals(dn.rdnComponents[i+offset]))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Indicates whether this entry falls within the range of the
   * provided search base DN and scope.
   *
   * @param  baseDN  The base DN for which to make the determination.
   * @param  scope   The search scope for which to make the
   *                 determination.
   *
   * @return  <CODE>true</CODE> if this entry is within the given
   *          base and scope, or <CODE>false</CODE> if it is not.
   */
  public boolean matchesBaseAndScope(DN baseDN, SearchScope scope)
  {
    switch (scope)
    {
      case BASE_OBJECT:
        // The base DN must equal this DN.
        return equals(baseDN);

      case SINGLE_LEVEL:
        // The parent DN must equal the base DN.
        return baseDN.equals(getParent());

      case WHOLE_SUBTREE:
        // This DN must be a descendant of the provided base DN.
        return isDescendantOf(baseDN);

      case SUBORDINATE_SUBTREE:
        // This DN must be a descendant of the provided base DN, but
        // not equal to it.
        return ((! equals(baseDN)) && isDescendantOf(baseDN));

      default:
        // This is a scope that we don't recognize.
        return false;
    }
  }



  /**
   * Decodes the provided ASN.1 octet string as a DN.
   *
   * @param  dnString  The ASN.1 octet string to decode as a DN.
   *
   * @return  The decoded DN.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              decode the provided ASN.1 octet
   *                              string as a DN.
   */
  public static DN decode(ByteString dnString)
         throws DirectoryException
  {
    // A null or empty DN is acceptable.
    if (dnString == null)
    {
      return NULL_DN;
    }

    int    length  = dnString.length();
    if (length == 0)
    {
      return NULL_DN;
    }


    // See if we are dealing with any non-ASCII characters, or any
    // escaped characters.  If so, then the easiest and safest
    // approach is to convert the DN to a string and decode it that
    // way.
    byte b;
    for (int i = 0; i < length; i++)
    {
      b = dnString.byteAt(i);
      if (((b & 0x7F) != b) || (b == '\\'))
      {
        return decode(dnString.toString());
      }
    }


    // Iterate through the DN string.  The first thing to do is to get
    // rid of any leading spaces.
    ByteSequenceReader dnReader = dnString.asReader();
    b = ' ';
    while (dnReader.remaining() > 0 && (b = dnReader.get()) == ' ')
    {}

    if(b == ' ')
    {
      // This means that the DN was completely comprised of spaces
      // and therefore should be considered the same as a null or
      // empty DN.
      return NULL_DN;
    }

    dnReader.skip(-1);
    // We know that it's not an empty DN, so we can do the real
    // processing.  Create a loop and iterate through all the RDN
    // components.
    boolean allowExceptions =
         DirectoryServer.allowAttributeNameExceptions();
    LinkedList<RDN> rdnComponents = new LinkedList<RDN>();
    while (true)
    {
      ByteString attributeName =
          parseAttributeName(dnReader, allowExceptions);


      // Make sure that we're not at the end of the DN string because
      // that would be invalid.
      if (dnReader.remaining() <= 0)
      {
        Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(
            dnString.toString(), attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // Skip over any spaces between the attribute name and its
      // value.
      b = ' ';
      while (dnReader.remaining() > 0 && (b = dnReader.get()) == ' ')
      {}

      if(b == ' ')
      {
        // This means that we hit the end of the value before
        // finding a '='.  This is illegal because there is no
        // attribute-value separator.
        Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(
            dnString.toString(), attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message);
      }

      // The next character must be an equal sign.  If it is not,
      // then that's an error.
      if (b != '=')
      {
        Message message = ERR_ATTR_SYNTAX_DN_NO_EQUAL.
            get(dnString.toString(), attributeName.toString(),
                (char) b);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // Skip over any spaces after the equal sign.
      b = ' ';
      while (dnReader.remaining() > 0 && (b = dnReader.get()) == ' ')
      {}


      // If we are at the end of the DN string, then that must mean
      // that the attribute value was empty.  This will probably never
      // happen in a real-world environment, but technically isn't
      // illegal.  If it does happen, then go ahead and create the RDN
      // component and return the DN.
      if (b == ' ')
      {
        StringBuilder lowerName = new StringBuilder();
        toLowerCase(attributeName, lowerName, true);
        AttributeType attrType  =
             DirectoryServer.getAttributeType(lowerName.toString());
        String attributeNameString = attributeName.toString();

        if (attrType == null)
        {
          // This must be an attribute type that we don't know about.
          // In that case, we'll create a new attribute using the
          // default syntax.  If this is a problem, it will be caught
          // later either by not finding the target entry or by not
          // allowing the entry to be added.
          attrType = DirectoryServer.getDefaultAttributeType(
              attributeNameString);
        }

        AttributeValue value =
            AttributeValues.create(ByteString.empty(),
                               ByteString.empty());
        rdnComponents.add(
            new RDN(attrType, attributeNameString, value));
        return new DN(rdnComponents);
      }

      dnReader.skip(-1);

      // Parse the value for this RDN component.
      ByteString parsedValue = parseAttributeValue(dnReader);


      // Create the new RDN with the provided information.
      StringBuilder lowerName = new StringBuilder();
      toLowerCase(attributeName, lowerName, true);
      AttributeType attrType  =
          DirectoryServer.getAttributeType(lowerName.toString());
      String attributeNameString = attributeName.toString();
      if (attrType == null)
      {
        // This must be an attribute type that we don't know about.
        // In that case, we'll create a new attribute using the
        // default syntax.  If this is a problem, it will be caught
        // later either by not finding the target entry or by not
        // allowing the entry to be added.
        attrType = DirectoryServer.getDefaultAttributeType(
            attributeNameString);
      }

      AttributeValue value =
           AttributeValues.create(attrType, parsedValue);
      RDN rdn = new RDN(attrType, attributeNameString, value);


      // Skip over any spaces that might be after the attribute value.
      b = ' ';
      while (dnReader.remaining() > 0 && (b = dnReader.get()) == ' ')
      {}


      // Most likely, we will be at either the end of the RDN
      // component or the end of the DN.  If so, then handle that
      // appropriately.
      if (b == ' ')
      {
        // We're at the end of the DN string and should have a valid
        // DN so return it.
        rdnComponents.add(rdn);
        return new DN(rdnComponents);
      }
      else if ((b == ',') || (b == ';'))
      {
        // We're at the end of the RDN component, so add it to the
        // list, skip over the comma/semicolon, and start on the next
        // component.
        rdnComponents.add(rdn);
        continue;
      }
      else if (b != '+')
      {
        // This should not happen.  At any rate, it's an illegal
        // character, so throw an exception.
        Message message = ERR_ATTR_SYNTAX_DN_INVALID_CHAR.get(
            dnReader.toString(), (char) b, dnReader.position()-1);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // If we have gotten here, then this must be a multi-valued RDN.
      // In that case, parse the remaining attribute/value pairs and
      // add them to the RDN that we've already created.
      while (true)
      {
        // Skip over the plus sign and any spaces that may follow it
        // before the next attribute name.
        b = ' ';
        while (dnReader.remaining() > 0 &&
            (b = dnReader.get()) == ' ')
        {}

        dnReader.skip(-1);
        // Parse the attribute name from the DN string.
        attributeName = parseAttributeName(dnReader, allowExceptions);


        // Make sure that we're not at the end of the DN string
        // because that would be invalid.
        if (b == ' ')
        {
          Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(
              dnString.toString(), attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }


        // Skip over any spaces between the attribute name and its
        // value.
        b = ' ';
        while (dnReader.remaining() > 0 &&
            (b = dnReader.get()) == ' ')
        {}

        if(b == ' ')
        {
          // This means that we hit the end of the value before
          // finding a '='.  This is illegal because there is no
          // attribute-value separator.
          Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.
              get(dnString.toString(), attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message);
        }


        // The next character must be an equal sign.  If it is not,
        // then that's an error.
        if (b != '=')
        {
          Message message = ERR_ATTR_SYNTAX_DN_NO_EQUAL.
              get(dnString.toString(), attributeName.toString(),
                  (char) b);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }


        // Skip over any spaces after the equal sign.
        b = ' ';
        while (dnReader.remaining() > 0 &&
            (b = dnReader.get()) == ' ')
        {}


        // If we are at the end of the DN string, then that must mean
        // that the attribute value was empty.  This will probably
        // never happen in a real-world environment, but technically
        // isn't illegal.  If it does happen, then go ahead and create
        // the RDN component and return the DN.
        if (b == ' ')
        {
          lowerName = new StringBuilder();
          toLowerCase(attributeName, lowerName, true);
          attrType =
              DirectoryServer.getAttributeType(lowerName.toString());
          attributeNameString = attributeName.toString();

          if (attrType == null)
          {
            // This must be an attribute type that we don't know
            // about.  In that case, we'll create a new attribute
            // using the default syntax.  If this is a problem, it
            // will be caught later either by not finding the target
            // entry or by not allowing the entry to be added.
            attrType = DirectoryServer.getDefaultAttributeType(
                attributeNameString);
          }

          value = AttributeValues.create(ByteString.empty(),
                                     ByteString.empty());
          rdn.addValue(attrType, attributeNameString, value);
          rdnComponents.add(rdn);
          return new DN(rdnComponents);
        }

        dnReader.skip(-1);

        // Parse the value for this RDN component.
        parsedValue = parseAttributeValue(dnReader);


        lowerName = new StringBuilder();
        toLowerCase(attributeName, lowerName, true);
        attrType =
            DirectoryServer.getAttributeType(lowerName.toString());
        attributeNameString = attributeName.toString();
        if (attrType == null)
        {
          // This must be an attribute type that we don't know about.
          // In that case, we'll create a new attribute using the
          // default syntax.  If this is a problem, it will be caught
          // later either by not finding the target entry or by not
          // allowing the entry to be added.
          attrType = DirectoryServer.getDefaultAttributeType(
              attributeNameString);
        }

        value = AttributeValues.create(attrType, parsedValue);
        rdn.addValue(attrType, attributeNameString, value);


        // Skip over any spaces that might be after the attribute
        // value.
        // Skip over any spaces that might be after the attribute
        // value.
        b = ' ';
        while (dnReader.remaining() > 0 &&
            (b = dnReader.get()) == ' ')
        {}


        // Most likely, we will be at either the end of the RDN
        // component or the end of the DN.  If so, then handle that
        // appropriately.
        if (b == ' ')
        {
          // We're at the end of the DN string and should have a valid
          // DN so return it.
          rdnComponents.add(rdn);
          return new DN(rdnComponents);
        }
        else if ((b == ',') || (b == ';'))
        {
          // We're at the end of the RDN component, so add it to the
          // list, skip over the comma/semicolon, and start on the
          // next component.
          rdnComponents.add(rdn);
          break;
        }
        else if (b != '+')
        {
          // This should not happen.  At any rate, it's an illegal
          // character, so throw an exception.
          Message message = ERR_ATTR_SYNTAX_DN_INVALID_CHAR.get(
              dnString.toString(), (char) b, dnReader.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message);
        }
      }
    }
  }



  /**
   * Decodes the provided string as a DN.
   *
   * @param  dnString  The string to decode as a DN.
   *
   * @return  The decoded DN.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              decode the provided string as a DN.
   */
  public static DN decode(String dnString)
         throws DirectoryException
  {
    // A null or empty DN is acceptable.
    if (dnString == null)
    {
      return NULL_DN;
    }

    int length = dnString.length();
    if (length == 0)
    {
      return NULL_DN;
    }


    // Iterate through the DN string.  The first thing to do is to get
    // rid of any leading spaces.
    int pos = 0;
    char c = dnString.charAt(pos);
    while (c == ' ')
    {
      pos++;
      if (pos == length)
      {
        // This means that the DN was completely comprised of spaces
        // and therefore should be considered the same as a null or
        // empty DN.
        return NULL_DN;
      }
      else
      {
        c = dnString.charAt(pos);
      }
    }


    // We know that it's not an empty DN, so we can do the real
    // processing.  Create a loop and iterate through all the RDN
    // components.
    boolean allowExceptions =
         DirectoryServer.allowAttributeNameExceptions();
    LinkedList<RDN> rdnComponents = new LinkedList<RDN>();
    while (true)
    {
      StringBuilder attributeName = new StringBuilder();
      pos = parseAttributeName(dnString, pos, attributeName,
                               allowExceptions);


      // Make sure that we're not at the end of the DN string because
      // that would be invalid.
      if (pos >= length)
      {
        Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(
            dnString, attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // Skip over any spaces between the attribute name and its
      // value.
      c = dnString.charAt(pos);
      while (c == ' ')
      {
        pos++;
        if (pos >= length)
        {
          // This means that we hit the end of the value before
          // finding a '='.  This is illegal because there is no
          // attribute-value separator.
          Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(
              dnString, attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
        else
        {
          c = dnString.charAt(pos);
        }
      }


      // The next character must be an equal sign.  If it is not, then
      // that's an error.
      if (c == '=')
      {
        pos++;
      }
      else
      {
        Message message = ERR_ATTR_SYNTAX_DN_NO_EQUAL.get(
            dnString, attributeName.toString(), c);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // Skip over any spaces after the equal sign.
      while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // If we are at the end of the DN string, then that must mean
      // that the attribute value was empty.  This will probably never
      // happen in a real-world environment, but technically isn't
      // illegal.  If it does happen, then go ahead and create the
      // RDN component and return the DN.
      if (pos >= length)
      {
        String        name      = attributeName.toString();
        String        lowerName = toLowerCase(name);
        AttributeType attrType  =
             DirectoryServer.getAttributeType(lowerName);

        if (attrType == null)
        {
          // This must be an attribute type that we don't know about.
          // In that case, we'll create a new attribute using the
          // default syntax.  If this is a problem, it will be caught
          // later either by not finding the target entry or by not
          // allowing the entry to be added.
          attrType = DirectoryServer.getDefaultAttributeType(name);
        }

        AttributeValue value =
            AttributeValues.create(ByteString.empty(),
                                ByteString.empty());
        rdnComponents.add(new RDN(attrType, name, value));
        return new DN(rdnComponents);
      }


      // Parse the value for this RDN component.
      ByteStringBuilder parsedValue = new ByteStringBuilder(0);
      pos = parseAttributeValue(dnString, pos, parsedValue);


      // Create the new RDN with the provided information.
      String name            = attributeName.toString();
      String lowerName       = toLowerCase(name);
      AttributeType attrType =
           DirectoryServer.getAttributeType(lowerName);
      if (attrType == null)
      {
        // This must be an attribute type that we don't know about.
        // In that case, we'll create a new attribute using the
        // default syntax.  If this is a problem, it will be caught
        // later either by not finding the target entry or by not
        // allowing the entry to be added.
        attrType = DirectoryServer.getDefaultAttributeType(name);
      }

      AttributeValue value =
          AttributeValues.create(attrType,
              parsedValue.toByteString());
      RDN rdn = new RDN(attrType, name, value);


      // Skip over any spaces that might be after the attribute value.
      while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // Most likely, we will be at either the end of the RDN
      // component or the end of the DN.  If so, then handle that
      // appropriately.
      if (pos >= length)
      {
        // We're at the end of the DN string and should have a valid
        // DN so return it.
        rdnComponents.add(rdn);
        return new DN(rdnComponents);
      }
      else if ((c == ',') || (c == ';'))
      {
        // We're at the end of the RDN component, so add it to the
        // list, skip over the comma/semicolon, and start on the next
        // component.
        rdnComponents.add(rdn);
        pos++;
        continue;
      }
      else if (c != '+')
      {
        // This should not happen.  At any rate, it's an illegal
        // character, so throw an exception.
        Message message =
            ERR_ATTR_SYNTAX_DN_INVALID_CHAR.get(dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // If we have gotten here, then this must be a multi-valued RDN.
      // In that case, parse the remaining attribute/value pairs and
      // add them to the RDN that we've already created.
      while (true)
      {
        // Skip over the plus sign and any spaces that may follow it
        // before the next attribute name.
        pos++;
        while ((pos < length) && (dnString.charAt(pos) == ' '))
        {
          pos++;
        }


        // Parse the attribute name from the DN string.
        attributeName = new StringBuilder();
        pos = parseAttributeName(dnString, pos, attributeName,
                                 allowExceptions);


        // Make sure that we're not at the end of the DN string
        // because that would be invalid.
        if (pos >= length)
        {
          Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.get(
              dnString, attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }


        // Skip over any spaces between the attribute name and its
        // value.
        c = dnString.charAt(pos);
        while (c == ' ')
        {
          pos++;
          if (pos >= length)
          {
            // This means that we hit the end of the value before
            // finding a '='.  This is illegal because there is no
            // attribute-value separator.
            Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME.
                get(dnString, attributeName.toString());
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message);
          }
          else
          {
            c = dnString.charAt(pos);
          }
        }


        // The next character must be an equal sign.  If it is not,
        // then that's an error.
        if (c == '=')
        {
          pos++;
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_DN_NO_EQUAL.get(
              dnString, attributeName.toString(), c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }


        // Skip over any spaces after the equal sign.
        while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
        {
          pos++;
        }


        // If we are at the end of the DN string, then that must mean
        // that the attribute value was empty.  This will probably
        // never happen in a real-world environment, but technically
        // isn't illegal.  If it does happen, then go ahead and create
        // the RDN component and return the DN.
        if (pos >= length)
        {
          name      = attributeName.toString();
          lowerName = toLowerCase(name);
          attrType  = DirectoryServer.getAttributeType(lowerName);

          if (attrType == null)
          {
            // This must be an attribute type that we don't know
            // about.  In that case, we'll create a new attribute
            // using the default syntax.  If this is a problem, it
            // will be caught later either by not finding the target
            // entry or by not allowing the entry to be added.
            attrType = DirectoryServer.getDefaultAttributeType(name);
          }

          value = AttributeValues.create(ByteString.empty(),
                                     ByteString.empty());
          rdn.addValue(attrType, name, value);
          rdnComponents.add(rdn);
          return new DN(rdnComponents);
        }


        // Parse the value for this RDN component.
        parsedValue.clear();
        pos = parseAttributeValue(dnString, pos, parsedValue);


        // Create the new RDN with the provided information.
        name      = attributeName.toString();
        lowerName = toLowerCase(name);
        attrType  = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          // This must be an attribute type that we don't know about.
          // In that case, we'll create a new attribute using the
          // default syntax.  If this is a problem, it will be caught
          // later either by not finding the target entry or by not
          // allowing the entry to be added.
          attrType = DirectoryServer.getDefaultAttributeType(name);
        }

        value = AttributeValues.create(attrType,
            parsedValue.toByteString());
        rdn.addValue(attrType, name, value);


        // Skip over any spaces that might be after the attribute
        // value.
        while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
        {
          pos++;
        }


        // Most likely, we will be at either the end of the RDN
        // component or the end of the DN.  If so, then handle that
        // appropriately.
        if (pos >= length)
        {
          // We're at the end of the DN string and should have a valid
          // DN so return it.
          rdnComponents.add(rdn);
          return new DN(rdnComponents);
        }
        else if ((c == ',') || (c == ';'))
        {
          // We're at the end of the RDN component, so add it to the
          // list, skip over the comma/semicolon, and start on the
          // next component.
          rdnComponents.add(rdn);
          pos++;
          break;
        }
        else if (c != '+')
        {
          // This should not happen.  At any rate, it's an illegal
          // character, so throw an exception.
          Message message =
              ERR_ATTR_SYNTAX_DN_INVALID_CHAR.get(dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
      }
    }
  }



  /**
   * Parses an attribute name from the provided DN string starting at
   * the specified location.
   *
   * @param  dnBytes          The byte array containing the DN to
   *                          parse.
   * @param  allowExceptions  Indicates whether to allow certain
   *                          exceptions to the strict requirements
   *                          for attribute names.
   *
   * @return  The parsed attribute name.
   *
   * @throws  DirectoryException  If it was not possible to parse a
   *                              valid attribute name from the
   *                              provided DN string.
   */
  static ByteString parseAttributeName(ByteSequenceReader dnBytes,
                                boolean allowExceptions)
          throws DirectoryException
  {
    // Skip over any leading spaces.
    while(dnBytes.remaining() > 0 && dnBytes.get() == ' ')
    {}

    if(dnBytes.remaining() <= 0)
    {
      // This means that the remainder of the DN was completely
      // comprised of spaces.  If we have gotten here, then we
      // know that there is at least one RDN component, and
      // therefore the last non-space character of the DN must
      // have been a comma. This is not acceptable.
      Message message = ERR_ATTR_SYNTAX_DN_END_WITH_COMMA.get(
          dnBytes.toString());
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          message);
    }

    dnBytes.skip(-1);
    int nameStartPos = dnBytes.position();
    ByteString nameBytes = null;

    // Next, we should find the attribute name for this RDN component.
    // It may either be a name (with only letters, digits, and dashes
    // and starting with a letter) or an OID (with only digits and
    // periods, optionally prefixed with "oid."), and there is also a
    // special case in which we will allow underscores.  Because of
    // the complexity involved, read the entire name first with
    // minimal validation and then do more thorough validation later.
    boolean       checkForOID   = false;
    boolean       endOfName     = false;
    while (dnBytes.remaining() > 0)
    {
      // To make the switch more efficient, we'll include all ASCII
      // characters in the range of allowed values and then reject the
      // ones that aren't allowed.
      byte b = dnBytes.get();
      switch (b)
      {
        case ' ':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;


        case '!':
        case '"':
        case '#':
        case '$':
        case '%':
        case '&':
        case '\'':
        case '(':
        case ')':
        case '*':
        case '+':
        case ',':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          Message msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);


        case '-':
          // This will be allowed as long as it isn't the first
          // character in the attribute name.
          if (dnBytes.position() == nameStartPos + 1)
          {
            msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DASH.
                  get(dnBytes.toString());
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         msg);
          }
          break;


        case '.':
          // The period could be allowed if the attribute name is
          // actually expressed as an OID.  We'll accept it for now,
          // but make sure to check it later.
          checkForOID = true;
          break;


        case '/':
          // This is not allowed in an attribute name or any character
          // immediately following it.
          msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);


        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          // Digits are always allowed if they are not the first
          // character.  However, they may be allowed if they are the
          // first character if the valid is an OID or if the
          // attribute name exceptions option is enabled.  Therefore,
          // we'll accept it now and check it later.
          break;


        case ':':
        case ';': // NOTE:  attribute options are not allowed in a DN.
        case '<':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);


        case '=':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;


        case '>':
        case '?':
        case '@':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);


        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
        case 'G':
        case 'H':
        case 'I':
        case 'J':
        case 'K':
        case 'L':
        case 'M':
        case 'N':
        case 'O':
        case 'P':
        case 'Q':
        case 'R':
        case 'S':
        case 'T':
        case 'U':
        case 'V':
        case 'W':
        case 'X':
        case 'Y':
        case 'Z':
          // These will always be allowed.
          break;


        case '[':
        case '\\':
        case ']':
        case '^':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);


        case '_':
          // This will never be allowed as the first character.  It
          // may be allowed for subsequent characters if the attribute
          // name exceptions option is enabled.
          if (dnBytes.position() == nameStartPos + 1)
          {
            msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_UNDERSCORE.
                  get(dnBytes.toString(),
                      ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         msg);
          }
          else if (!allowExceptions)
          {
            msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_UNDERSCORE_CHAR.
                  get(dnBytes.toString(),
                      ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         msg);
          }
          break;


        case '`':
          // This is not allowed in an attribute name or any character
          // immediately following it.
          msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);


        case 'a':
        case 'b':
        case 'c':
        case 'd':
        case 'e':
        case 'f':
        case 'g':
        case 'h':
        case 'i':
        case 'j':
        case 'k':
        case 'l':
        case 'm':
        case 'n':
        case 'o':
        case 'p':
        case 'q':
        case 'r':
        case 's':
        case 't':
        case 'u':
        case 'v':
        case 'w':
        case 'x':
        case 'y':
        case 'z':
          // These will always be allowed.
          break;


        default:
          // This is not allowed in an attribute name or any character
          // immediately following it.
          msg = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnBytes.toString(), (char) b, dnBytes.position()-1);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       msg);
      }


      if (endOfName)
      {
        int nameEndPos = dnBytes.position() - 1;
        dnBytes.position(nameStartPos);
        nameBytes =
            dnBytes.getByteString(nameEndPos - nameStartPos);
        break;
      }
    }


    // We should now have the full attribute name.  However, we may
    // still need to perform some validation, particularly if the name
    // contains a period or starts with a digit.  It must also have at
    // least one character.
    if (nameBytes == null || nameBytes.length() == 0)
    {
      Message message =
          ERR_ATTR_SYNTAX_DN_ATTR_NO_NAME.get(dnBytes.toString());
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }
    else if (checkForOID)
    {
      boolean validOID = true;

      int namePos = 0;
      int nameLength = nameBytes.length();
      byte ch = nameBytes.byteAt(0);
      if ((ch == 'o') || (ch == 'O'))
      {
        if (nameLength <= 4)
        {
          validOID = false;
        }
        else
        {
          if ((((ch = nameBytes.byteAt(1)) == 'i') ||
               (ch == 'I')) &&
              (((ch = nameBytes.byteAt(2)) == 'd') ||
               (ch == 'D')) &&
              (nameBytes.byteAt(3) == '.'))
          {
            nameBytes = nameBytes.subSequence(4, nameBytes.length());
            nameLength -= 4;
          }
          else
          {
            validOID = false;
          }
        }
      }

      while (validOID && (namePos < nameLength))
      {
        ch = nameBytes.byteAt(namePos++);
        if (isDigit((char)ch))
        {
          while (validOID && (namePos < nameLength) &&
                 isDigit((char)nameBytes.byteAt(namePos)))
          {
            namePos++;
          }

          if ((namePos < nameLength) &&
              (nameBytes.byteAt(namePos) != '.'))
          {
            validOID = false;
          }
        }
        else if (ch == '.')
        {
          if ((namePos == 1) ||
              (nameBytes.byteAt(namePos-2) == '.'))
          {
            validOID = false;
          }
        }
        else
        {
          validOID = false;
        }
      }


      if (validOID && (nameBytes.byteAt(nameLength-1) == '.'))
      {
        validOID = false;
      }


      if (! validOID)
      {
        Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_PERIOD.get(
            dnBytes.toString(), nameBytes.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
    }
    else if (isDigit((char)nameBytes.byteAt(0)) && (!allowExceptions))
    {
      Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DIGIT.
          get(dnBytes.toString(), ((char)nameBytes.byteAt(0)),
              ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }


    return nameBytes;
  }



  /**
   * Parses an attribute name from the provided DN string starting at
   * the specified location.
   *
   * @param  dnString         The DN string to be parsed.
   * @param  pos              The position at which to start parsing
   *                          the attribute name.
   * @param  attributeName    The buffer to which to append the parsed
   *                          attribute name.
   * @param  allowExceptions  Indicates whether to allow certain
   *                          exceptions to the strict requirements
   *                          for attribute names.
   *
   * @return  The position of the first character that is not part of
   *          the attribute name.
   *
   * @throws  DirectoryException  If it was not possible to parse a
   *                              valid attribute name from the
   *                              provided DN string.
   */
  static int parseAttributeName(String dnString, int pos,
                                StringBuilder attributeName,
                                boolean allowExceptions)
          throws DirectoryException
  {
    int length = dnString.length();


    // Skip over any leading spaces.
    if (pos < length)
    {
      while (dnString.charAt(pos) == ' ')
      {
        pos++;
        if (pos == length)
        {
          // This means that the remainder of the DN was completely
          // comprised of spaces.  If we have gotten here, then we
          // know that there is at least one RDN component, and
          // therefore the last non-space character of the DN must
          // have been a comma. This is not acceptable.
          Message message =
              ERR_ATTR_SYNTAX_DN_END_WITH_COMMA.get(dnString);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
      }
    }

    // Next, we should find the attribute name for this RDN component.
    // It may either be a name (with only letters, digits, and dashes
    // and starting with a letter) or an OID (with only digits and
    // periods, optionally prefixed with "oid."), and there is also a
    // special case in which we will allow underscores.  Because of
    // the complexity involved, read the entire name first with
    // minimal validation and then do more thorough validation later.
    boolean       checkForOID   = false;
    boolean       endOfName     = false;
    while (pos < length)
    {
      // To make the switch more efficient, we'll include all ASCII
      // characters in the range of allowed values and then reject the
      // ones that aren't allowed.
      char c = dnString.charAt(pos);
      switch (c)
      {
        case ' ':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;


        case '!':
        case '"':
        case '#':
        case '$':
        case '%':
        case '&':
        case '\'':
        case '(':
        case ')':
        case '*':
        case '+':
        case ',':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);


        case '-':
          // This will be allowed as long as it isn't the first
          // character in the attribute name.
          if (attributeName.length() > 0)
          {
            attributeName.append(c);
          }
          else
          {
            message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DASH.
                  get(dnString);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message);
          }
          break;


        case '.':
          // The period could be allowed if the attribute name is
          // actually expressed as an OID.  We'll accept it for now,
          // but make sure to check it later.
          attributeName.append(c);
          checkForOID = true;
          break;


        case '/':
          // This is not allowed in an attribute name or any character
          // immediately following it.
          message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);


        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          // Digits are always allowed if they are not the first
          // character. However, they may be allowed if they are the
          // first character if the valid is an OID or if the
          // attribute name exceptions option is enabled.  Therefore,
          // we'll accept it now and check it later.
          attributeName.append(c);
          break;


        case ':':
        case ';': // NOTE:  attribute options are not allowed in a DN.
        case '<':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);


        case '=':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;


        case '>':
        case '?':
        case '@':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);


        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
        case 'G':
        case 'H':
        case 'I':
        case 'J':
        case 'K':
        case 'L':
        case 'M':
        case 'N':
        case 'O':
        case 'P':
        case 'Q':
        case 'R':
        case 'S':
        case 'T':
        case 'U':
        case 'V':
        case 'W':
        case 'X':
        case 'Y':
        case 'Z':
          // These will always be allowed.
          attributeName.append(c);
          break;


        case '[':
        case '\\':
        case ']':
        case '^':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);


        case '_':
          // This will never be allowed as the first character.  It
          // may be allowed for subsequent characters if the attribute
          // name exceptions option is enabled.
          if (attributeName.length() == 0)
          {
            message =
                   ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_UNDERSCORE.
                  get(dnString, ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message);
          }
          else if (allowExceptions)
          {
            attributeName.append(c);
          }
          else
          {
            message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_UNDERSCORE_CHAR.
                  get(dnString, ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message);
          }
          break;


        case '`':
          // This is not allowed in an attribute name or any character
          // immediately following it.
          message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);


        case 'a':
        case 'b':
        case 'c':
        case 'd':
        case 'e':
        case 'f':
        case 'g':
        case 'h':
        case 'i':
        case 'j':
        case 'k':
        case 'l':
        case 'm':
        case 'n':
        case 'o':
        case 'p':
        case 'q':
        case 'r':
        case 's':
        case 't':
        case 'u':
        case 'v':
        case 'w':
        case 'x':
        case 'y':
        case 'z':
          // These will always be allowed.
          attributeName.append(c);
          break;


        default:
          // This is not allowed in an attribute name or any character
          // immediately following it.
          message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
              dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
      }


      if (endOfName)
      {
        break;
      }

      pos++;
    }


    // We should now have the full attribute name.  However, we may
    // still need to perform some validation, particularly if the
    // name contains a period or starts with a digit.  It must also
    // have at least one character.
    if (attributeName.length() == 0)
    {
      Message message = ERR_ATTR_SYNTAX_DN_ATTR_NO_NAME.get(dnString);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }
    else if (checkForOID)
    {
      boolean validOID = true;

      int namePos = 0;
      int nameLength = attributeName.length();
      char ch = attributeName.charAt(0);
      if ((ch == 'o') || (ch == 'O'))
      {
        if (nameLength <= 4)
        {
          validOID = false;
        }
        else
        {
          if ((((ch = attributeName.charAt(1)) == 'i') ||
               (ch == 'I')) &&
              (((ch = attributeName.charAt(2)) == 'd') ||
               (ch == 'D')) &&
              (attributeName.charAt(3) == '.'))
          {
            attributeName.delete(0, 4);
            nameLength -= 4;
          }
          else
          {
            validOID = false;
          }
        }
      }

      while (validOID && (namePos < nameLength))
      {
        ch = attributeName.charAt(namePos++);
        if (isDigit(ch))
        {
          while (validOID && (namePos < nameLength) &&
                 isDigit(attributeName.charAt(namePos)))
          {
            namePos++;
          }

          if ((namePos < nameLength) &&
              (attributeName.charAt(namePos) != '.'))
          {
            validOID = false;
          }
        }
        else if (ch == '.')
        {
          if ((namePos == 1) ||
              (attributeName.charAt(namePos-2) == '.'))
          {
            validOID = false;
          }
        }
        else
        {
          validOID = false;
        }
      }


      if (validOID && (attributeName.charAt(nameLength-1) == '.'))
      {
        validOID = false;
      }


      if (! validOID)
      {
        Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_PERIOD.get(
            dnString, attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
    }
    else if (isDigit(attributeName.charAt(0)) &&
             (! allowExceptions))
    {
      Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DIGIT.
          get(dnString, attributeName.charAt(0),
              ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }


    return pos;
  }



  /**
   * Parses the attribute value from the provided DN string starting
   * at the specified location.  When the value has been parsed, it
   * will be assigned to the provided ASN.1 octet string.
   *
   * @param  dnBytes         The byte array containing the DN to be
   *                         parsed.
   *
   * @return  The parsed attribute value.
   *
   * @throws  DirectoryException  If it was not possible to parse a
   *                              valid attribute value from the
   *                              provided DN string.
   */
  static ByteString parseAttributeValue(ByteSequenceReader dnBytes)
          throws DirectoryException
  {
    // All leading spaces have already been stripped so we can start
    // reading the value.  However, it may be empty so check for that.
    if (dnBytes.remaining() <= 0)
    {
      return ByteString.empty();
    }


    // Look at the first character.  If it is an octothorpe (#), then
    // that means that the value should be a hex string.
    byte b = dnBytes.get();
    if (b == '#')
    {
      // The first two characters must be hex characters.
      StringBuilder hexString = new StringBuilder();
      if (dnBytes.remaining() < 2)
      {
        Message message = ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(
            dnBytes.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }

      for (int i=0; i < 2; i++)
      {
        b = dnBytes.get();
        if (isHexDigit(b))
        {
          hexString.append((char) b);
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(
              dnBytes.toString(), (char) b);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
      }


      // The rest of the value must be a multiple of two hex
      // characters.  The end of the value may be designated by the
      // end of the DN, a comma or semicolon, a plus sign, or a space.
      while (dnBytes.remaining() > 0)
      {
        b = dnBytes.get();
        if (isHexDigit(b))
        {
          hexString.append((char) b);

          if (dnBytes.remaining() > 0)
          {
            b = dnBytes.get();
            if (isHexDigit(b))
            {
              hexString.append((char) b);
            }
            else
            {
              Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.
                  get(dnBytes.toString(), (char) b);
              throw new DirectoryException(
                             ResultCode.INVALID_DN_SYNTAX, message);
            }
          }
          else
          {
            Message message = ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.
                get(dnBytes.toString());
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message);
          }
        }
        else if ((b == ' ') || (b == ',') || (b == ';') || (b == '+'))
        {
          // This denotes the end of the value.
          dnBytes.skip(-1);
          break;
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(
              dnBytes.toString(), (char) b);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
      }


      // At this point, we should have a valid hex string.  Convert it
      // to a byte array and set that as the value of the provided
      // octet string.
      try
      {
        return ByteString.wrap(hexStringToByteArray(
                                     hexString.toString()));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.
              get(dnBytes.toString(), String.valueOf(e));
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
    }


    // If the first character is a quotation mark, then the value
    // should continue until the corresponding closing quotation mark.
    else if (b == '"')
    {
      int valueStartPos = dnBytes.position();

      // Keep reading until we find a closing quotation mark.
      while (true)
      {
        if (dnBytes.remaining() <= 0)
        {
          // We hit the end of the DN before the closing quote.
          // That's an error.
          Message message = ERR_ATTR_SYNTAX_DN_UNMATCHED_QUOTE.get(
              dnBytes.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }

        if (dnBytes.get() == '"')
        {
          // This is the end of the value.
          break;
        }
      }

      int valueEndPos = dnBytes.position();
      dnBytes.position(valueStartPos);
      ByteString bs =
          dnBytes.getByteString(valueEndPos - valueStartPos - 1);
      dnBytes.skip(1);
      return bs;
    }

    else if(b == '+' || b == ',')
    {
      //We don't allow an empty attribute value. So do not allow the
      // first character to be a '+' or ',' since it is not escaped
      // by the user.
      Message message =
             ERR_ATTR_SYNTAX_DN_INVALID_REQUIRES_ESCAPE_CHAR.get(
                      dnBytes.toString(),dnBytes.position());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
    }

    // Otherwise, use general parsing to find the end of the value.
    else
    {
      // Keep reading until we find a comma/semicolon, a plus sign, or
      // the end of the DN.
      int valueEndPos = dnBytes.position();
      int valueStartPos = valueEndPos - 1;
      while (true)
      {
        if (dnBytes.remaining() <= 0)
        {
          // This is the end of the DN and therefore the end of the
          // value.
          break;
        }

        b = dnBytes.get();
        if ((b == ',') || (b == ';') || (b == '+'))
        {
          dnBytes.skip(-1);
          break;
        }

        if(b != ' ')
        {
          valueEndPos = dnBytes.position();
        }
      }


      // Convert the byte buffer to an array.
      dnBytes.position(valueStartPos);
      return dnBytes.getByteString(valueEndPos - valueStartPos);
    }
  }



  /**
   * Parses the attribute value from the provided DN string starting
   * at the specified location.  When the value has been parsed, it
   * will be assigned to the provided ASN.1 octet string.
   *
   * @param  dnString        The DN string to be parsed.
   * @param  pos             The position of the first character in
   *                         the attribute value to parse.
   * @param  attributeValue  The ASN.1 octet string whose value should
   *                         be set to the parsed attribute value when
   *                         this method completes successfully.
   *
   * @return  The position of the first character that is not part of
   *          the attribute value.
   *
   * @throws  DirectoryException  If it was not possible to parse a
   *                              valid attribute value from the
   *                              provided DN string.
   */
  static int parseAttributeValue(String dnString, int pos,
                                 ByteStringBuilder attributeValue)
          throws DirectoryException
  {
    // All leading spaces have already been stripped so we can start
    // reading the value.  However, it may be empty so check for that.
    int length = dnString.length();
    if (pos >= length)
    {
      attributeValue.append("");
      return pos;
    }


    // Look at the first character.  If it is an octothorpe (#), then
    // that means that the value should be a hex string.
    char c = dnString.charAt(pos++);
    if (c == '#')
    {
      // The first two characters must be hex characters.
      StringBuilder hexString = new StringBuilder();
      if ((pos+2) > length)
      {
        Message message =
            ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(dnString);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }

      for (int i=0; i < 2; i++)
      {
        c = dnString.charAt(pos++);
        if (isHexDigit(c))
        {
          hexString.append(c);
        }
        else
        {
          Message message =
              ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
      }


      // The rest of the value must be a multiple of two hex
      // characters.  The end of the value may be designated by the
      // end of the DN, a comma or semicolon, or a space.
      while (pos < length)
      {
        c = dnString.charAt(pos++);
        if (isHexDigit(c))
        {
          hexString.append(c);

          if (pos < length)
          {
            c = dnString.charAt(pos++);
            if (isHexDigit(c))
            {
              hexString.append(c);
            }
            else
            {
              Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.
                  get(dnString, c);
              throw new DirectoryException(
                             ResultCode.INVALID_DN_SYNTAX, message);
            }
          }
          else
          {
            Message message =
                ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT.get(dnString);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message);
          }
        }
        else if ((c == ' ') || (c == ',') || (c == ';'))
        {
          // This denotes the end of the value.
          pos--;
          break;
        }
        else
        {
          Message message =
              ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT.get(dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }
      }


      // At this point, we should have a valid hex string.  Convert it
      // to a byte array and set that as the value of the provided
      // octet string.
      try
      {
        attributeValue.append(
            hexStringToByteArray(hexString.toString()));
        return pos;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.
              get(dnString, String.valueOf(e));
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
    }


    // If the first character is a quotation mark, then the value
    // should continue until the corresponding closing quotation mark.
    else if (c == '"')
    {
      // Keep reading until we find an unescaped closing quotation
      // mark.
      boolean escaped = false;
      StringBuilder valueString = new StringBuilder();
      while (true)
      {
        if (pos >= length)
        {
          // We hit the end of the DN before the closing quote.
          // That's an error.
          Message message =
              ERR_ATTR_SYNTAX_DN_UNMATCHED_QUOTE.get(dnString);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
        }

        c = dnString.charAt(pos++);
        if (escaped)
        {
          // The previous character was an escape, so we'll take this
          // one no matter what.
          valueString.append(c);
          escaped = false;
        }
        else if (c == '\\')
        {
          // The next character is escaped.  Set a flag to denote
          // this, but don't include the backslash.
          escaped = true;
        }
        else if (c == '"')
        {
          // This is the end of the value.
          break;
        }
        else
        {
          // This is just a regular character that should be in the
          // value.
          valueString.append(c);
        }
      }

      attributeValue.append(valueString.toString());
      return pos;
    }
    else if(c == '+' || c == ',')
    {
      //We don't allow an empty attribute value. So do not allow the
      // first character to be a '+' or ',' since it is not escaped
      // by the user.
      Message message =
             ERR_ATTR_SYNTAX_DN_INVALID_REQUIRES_ESCAPE_CHAR.get(
                      dnString,pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
    }


    // Otherwise, use general parsing to find the end of the value.
    else
    {
      boolean escaped;
      StringBuilder valueString = new StringBuilder();
      StringBuilder hexChars    = new StringBuilder();

      if (c == '\\')
      {
        escaped = true;
      }
      else
      {
        escaped = false;
        valueString.append(c);
      }


      // Keep reading until we find an unescaped comma or plus sign or
      // the end of the DN.
      while (true)
      {
        if (pos >= length)
        {
          // This is the end of the DN and therefore the end of the
          // value.  If there are any hex characters, then we need to
          // deal with them accordingly.
          appendHexChars(dnString, valueString, hexChars);
          break;
        }

        c = dnString.charAt(pos++);
        if (escaped)
        {
          // The previous character was an escape, so we'll take this
          // one.  However, this could be a hex digit, and if that's
          // the case then the escape would actually be in front of
          // two hex digits that should be treated as a special
          // character.
          if (isHexDigit(c))
          {
            // It is a hexadecimal digit, so the next digit must be
            // one too.  However, this could be just one in a series
            // of escaped hex pairs that is used in a string
            // containing one or more multi-byte UTF-8 characters so
            // we can't just treat this byte in isolation.  Collect
            // all the bytes together and make sure to take care of
            // these hex bytes before appending anything else to the
            // value.
            if (pos >= length)
            {
              Message message =
                ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID.
                    get(dnString);
              throw new DirectoryException(
                             ResultCode.INVALID_DN_SYNTAX, message);
            }
            else
            {
              char c2 = dnString.charAt(pos++);
              if (isHexDigit(c2))
              {
                hexChars.append(c);
                hexChars.append(c2);
              }
              else
              {
                Message message =
                  ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID.
                      get(dnString);
                throw new DirectoryException(
                               ResultCode.INVALID_DN_SYNTAX, message);
              }
            }
          }
          else
          {
            appendHexChars(dnString, valueString, hexChars);
            valueString.append(c);
          }

          escaped = false;
        }
        else if (c == '\\')
        {
          escaped = true;
        }
        else if ((c == ',') || (c == ';'))
        {
          appendHexChars(dnString, valueString, hexChars);
          pos--;
          break;
        }
        else if (c == '+')
        {
          appendHexChars(dnString, valueString, hexChars);
          pos--;
          break;
        }
        else
        {
          appendHexChars(dnString, valueString, hexChars);
          valueString.append(c);
        }
      }


      // Strip off any unescaped spaces that may be at the end of the
      // value.
      if (pos > 2 && dnString.charAt(pos-1) == ' ' &&
           dnString.charAt(pos-2) != '\\')
      {
        int lastPos = valueString.length() - 1;
        while (lastPos > 0)
        {
          if (valueString.charAt(lastPos) == ' ')
          {
            valueString.delete(lastPos, lastPos+1);
            lastPos--;
          }
          else
          {
            break;
          }
        }
      }


      attributeValue.append(valueString.toString());
      return pos;
    }
  }



  /**
   * Decodes a hexadecimal string from the provided
   * <CODE>hexChars</CODE> buffer, converts it to a byte array, and
   * then converts that to a UTF-8 string.  The resulting UTF-8 string
   * will be appended to the provided <CODE>valueString</CODE> buffer,
   * and the <CODE>hexChars</CODE> buffer will be cleared.
   *
   * @param  dnString     The DN string that is being decoded.
   * @param  valueString  The buffer containing the value to which the
   *                      decoded string should be appended.
   * @param  hexChars     The buffer containing the hexadecimal
   *                      characters to decode to a UTF-8 string.
   *
   * @throws  DirectoryException  If any problem occurs during the
   *                              decoding process.
   */
  private static void appendHexChars(String dnString,
                                     StringBuilder valueString,
                                     StringBuilder hexChars)
          throws DirectoryException
  {
    if (hexChars.length() == 0)
    {
      return;
    }

    try
    {
      byte[] hexBytes = hexStringToByteArray(hexChars.toString());
      valueString.append(new String(hexBytes, "UTF-8"));
      hexChars.delete(0, hexChars.length());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE.
          get(dnString, String.valueOf(e));
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }
  }



  /**
   * Indicates whether the provided object is equal to this DN.  In
   * order for the object to be considered equal, it must be a DN with
   * the same number of RDN components and each corresponding RDN
   * component must be equal.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is a DN that is
   *          equal to this DN, or <CODE>false</CODE> if it is not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if (!(o instanceof DN))
    {
      return false;
    }

    try
    {
      return (toNormalizedString().equals(
          ((DN) o).toNormalizedString()));
    }
    catch (Exception e)
    {
      // This most likely means that the object was null or wasn't a
      // DN.  In either case, it's faster to assume that it is and
      // return false on an exception than to perform the checks to
      // see if it meets the appropriate
      // conditions.
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return false;
    }
  }



  /**
   * Retrieves the hash code for this DN.  The hash code will be the
   * sum of the hash codes for all the RDN components.
   *
   * @return  The hash code for this DN.
   */
  @Override
  public int hashCode()
  {
    return toNormalizedString().hashCode();
  }



  /**
   * Retrieves a string representation of this DN.
   *
   * @return  A string representation of this DN.
   */
  @Override
  public String toString()
  {
    if (dnString == null)
    {
      if (numComponents == 0)
      {
        dnString = "";
      }
      else
      {
        StringBuilder buffer = new StringBuilder();
        rdnComponents[0].toString(buffer);

        for (int i=1; i < numComponents; i++)
        {
          buffer.append(",");
          rdnComponents[i].toString(buffer);
        }

        dnString = buffer.toString();
      }
    }

    return dnString;
  }



  /**
   * Appends a string representation of this DN to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(toString());
  }



  /**
   * Retrieves a normalized representation of the DN with the provided
   * components.
   *
   * @param  rdnComponents  The RDN components for which to obtain the
   *                        normalized string representation.
   *
   * @return  The normalized string representation of the provided RDN
   *          components.
   */
  private static String normalize(RDN[] rdnComponents)
  {
    if (rdnComponents.length == 0)
    {
      return "";
    }

    StringBuilder buffer = new StringBuilder();
    rdnComponents[0].toNormalizedString(buffer);

    for (int i=1; i < rdnComponents.length; i++)
    {
      buffer.append(',');
      rdnComponents[i].toNormalizedString(buffer);
    }

    return buffer.toString();
  }



  /**
   * Retrieves a normalized string representation of this DN.
   *
   * @return  A normalized string representation of this DN.
   */
  public String toNormalizedString()
  {
    if(normalizedDN == null)
    {
      normalizedDN = normalize(this.rdnComponents);
    }
    return normalizedDN;
  }



  /**
   * Appends a normalized string representation of this DN to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toNormalizedString(StringBuilder buffer)
  {
    buffer.append(toNormalizedString());
  }



  /**
   * Compares this DN with the provided DN based on a natural order.
   * This order will be first hierarchical (ancestors will come before
   * descendants) and then alphabetical by attribute name(s) and
   * value(s).
   *
   * @param  dn  The DN against which to compare this DN.
   *
   * @return  A negative integer if this DN should come before the
   *          provided DN, a positive integer if this DN should come
   *          after the provided DN, or zero if there is no difference
   *          with regard to ordering.
   */
  @Override
  public int compareTo(DN dn)
  {
    if (equals(dn))
    {
      return 0;
    }
    else if (isNullDN())
    {
      return -1;
    }
    else if (dn.isNullDN())
    {
      return 1;
    }
    else if (isAncestorOf(dn))
    {
      return -1;
    }
    else if (isDescendantOf(dn))
    {
      return 1;
    }
    else
    {
      int minComps = Math.min(numComponents, dn.numComponents);
      for (int i=0; i < minComps; i++)
      {
        RDN r1 = rdnComponents[rdnComponents.length-1-i];
        RDN r2 = dn.rdnComponents[dn.rdnComponents.length-1-i];
        int result = r1.compareTo(r2);
        if (result != 0)
        {
          return result;
        }
      }

      return 0;
    }
  }
}


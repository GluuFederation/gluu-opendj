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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class represents RFC 3672 subentries and RFC 3671
 * collective attribute subentries objects.
 */
public class SubEntry {
  /**
   * Defines the set of permissible values for the conflict behavior.
   * Specifies the behavior that the server is to exhibit for entries
   * that already contain one or more real values for the associated
   * collective attribute.
   */
  public static enum CollectiveConflictBehavior {
    /**
     * Indicates that the virtual attribute provider is to preserve
     * any real values contained in the entry and merge them with the
     * set of generated virtual values so that both the real and
     * virtual values are used.
     */
    MERGE_REAL_AND_VIRTUAL("merge-real-and-virtual"),

    /**
     * Indicates that any real values contained in the entry are
     * preserved and used, and virtual values are not generated.
     */
    REAL_OVERRIDES_VIRTUAL("real-overrides-virtual"),

    /**
     * Indicates that the virtual attribute provider suppresses any
     * real values contained in the entry and generates virtual values
     * and uses them.
     */
    VIRTUAL_OVERRIDES_REAL("virtual-overrides-real");

    /** String representation of the value. */
    private final String name;

    /**
     * Private constructor.
     * @param name for this conflict behavior.
     */
    private CollectiveConflictBehavior(String name)
    {
      this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return name;
    }
  }

  /**
   * The name of the "collectiveConflictBehavior" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_COLLECTIVE_CONFLICT_BEHAVIOR =
          "collectiveconflictbehavior";

  /**
   * The name of the "inheritFromDNAttribute" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_INHERIT_COLLECTIVE_FROM_DN =
          "inheritfromdnattribute";

  /**
   * The name of the "inheritFromRDNAttribute" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_INHERIT_COLLECTIVE_FROM_RDN =
          "inheritfromrdnattribute";

  /**
   * The name of the "inheritFromRDNType" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_INHERIT_COLLECTIVE_FROM_RDN_TYPE =
          "inheritfromrdntype";

  /**
   * The name of the "inheritFromBaseRDN" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_INHERIT_COLLECTIVE_FROM_BASE =
          "inheritfrombaserdn";

  /**
   * The name of the "inheritAttribute" attribute type,
   * formatted in all lowercase characters.
   */
  public static final String ATTR_INHERIT_COLLECTIVE_ATTR =
          "inheritattribute";

  /** Attribute option to mark attributes collective. */
  private static final String ATTR_OPTION_COLLECTIVE =
          "collective";

  /** Entry object. */
  private Entry entry;

  /** Subtree specification. */
  private SubtreeSpecification subTreeSpec;

  /** Collective subentry flag. */
  private boolean isCollective;
  /** Inherited collective subentry flag. */
  private boolean isInheritedCollective;
  /** Inherited collective from DN subentry flag. */
  private boolean isInheritedFromDNCollective;
  /** Inherited collective from RDN subentry flag. */
  private boolean isInheritedFromRDNCollective;

  /** Inherited collective DN attribute type. */
  private AttributeType inheritFromDNType;
  /** Inherited collective RDN attribute type. */
  private AttributeType inheritFromRDNAttrType;
  /** Inherited collective RDN type attribute type. */
  private AttributeType inheritFromRDNType;

  /** Inherited collective RDN attribute value. */
  private ByteString inheritFromRDNAttrValue;
  /** Inherited collective from DN value. */
  private ByteString inheritFromDNAttrValue;

  /** Inherited collective from base DN. */
  private DN inheritFromBaseDN;

  /** Collective attributes. */
  private List<Attribute> collectiveAttributes;

  /** Conflict behavior. */
  private CollectiveConflictBehavior conflictBehavior =
          CollectiveConflictBehavior.REAL_OVERRIDES_VIRTUAL;

  /**
   * Constructs a subentry object from a given entry object.
   * @param  entry LDAP subentry to construct from.
   * @throws DirectoryException if there is a problem with
   *         constructing a subentry from a given entry.
   */
  public SubEntry(Entry entry) throws DirectoryException
  {
    this.entry = entry;

    // Process subtree specification.
    this.subTreeSpec = null;
    String specString = null;
    boolean isValidSpec = true;
    AttributeType specAttrType = DirectoryServer.getAttributeTypeOrDefault(ATTR_SUBTREE_SPEC_LC);
    List<Attribute> specAttrList = entry.getAttribute(specAttrType);
    if (specAttrList != null)
    {
      for (Attribute attr : specAttrList)
      {
        for (ByteString value : attr)
        {
          specString = value.toString();
          try
          {
            this.subTreeSpec = SubtreeSpecification.valueOf(
                    entry.getName().parent(), specString);
            isValidSpec = true;
          }
          catch (DirectoryException de)
          {
            isValidSpec = false;
          }
          if (this.subTreeSpec != null)
          {
            break;
          }
        }
        if (this.subTreeSpec != null)
        {
          break;
        }
      }
    }

    // Check that the subtree spec is flagged as valid. If it is not
    // that means all parsers have failed and it is invalid syntax.
    if (!isValidSpec)
    {
      LocalizableMessage message =
        ERR_ATTR_SYNTAX_SUBTREE_SPECIFICATION_INVALID.get(
          specString);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }

    // Subentry has to to have a subtree specification.
    if (this.subTreeSpec == null)
    {
      // There is none for some reason eg this could be
      // old Draft based ldapSubEntry so create a dummy.
      this.subTreeSpec = new SubtreeSpecification(entry.getName().parent(),
          null, -1, -1, null, null, null);
    }

    // Determine if this subentry is collective attribute subentry.
    this.isCollective = entry.isCollectiveAttributeSubentry();

    // Determine if this subentry is inherited collective
    // attribute subentry and if so what kind.
    this.isInheritedCollective =
            entry.isInheritedCollectiveAttributeSubentry();
    if (this.isInheritedCollective)
    {
      this.isInheritedFromDNCollective =
              entry.isInheritedFromDNCollectiveAttributeSubentry();
      this.isInheritedFromRDNCollective =
              entry.isInheritedFromRDNCollectiveAttributeSubentry();
    }

    // Process collective attributes.
    this.collectiveAttributes = new ArrayList<>();
    if (this.isCollective)
    {
      List<Attribute> subAttrList = entry.getAttributes();
      for (Attribute subAttr : subAttrList)
      {
        AttributeType attrType = subAttr.getAttributeType();
        if (attrType.isCollective())
        {
          CollectiveVirtualAttribute collectiveAttr =
                  new CollectiveVirtualAttribute(subAttr);
          this.collectiveAttributes.add(collectiveAttr);
        }
        else if (subAttr.hasOption(ATTR_OPTION_COLLECTIVE))
        {
          AttributeBuilder builder = new AttributeBuilder(subAttr.getAttributeType());
          builder.addAll(subAttr);
          Set<String> options = new LinkedHashSet<>(subAttr.getOptions());
          options.remove(ATTR_OPTION_COLLECTIVE);
          builder.setOptions(options);
          Attribute attr = builder.toAttribute();
          CollectiveVirtualAttribute collectiveAttr = new CollectiveVirtualAttribute(attr);
          this.collectiveAttributes.add(collectiveAttr);
        }
      }
    }

    // Process inherited collective attributes.
    if (this.isInheritedCollective)
    {
      if (this.isInheritedFromDNCollective)
      {
        List<Attribute> attrList = entry.getAttribute(
                ATTR_INHERIT_COLLECTIVE_FROM_DN);
        if (attrList != null && !attrList.isEmpty())
        {
          for (Attribute attr : attrList)
          {
            for (ByteString value : attr)
            {
              this.inheritFromDNType = DirectoryServer.getAttributeTypeOrDefault(value.toString().toLowerCase());
              this.inheritFromDNAttrValue = value;
              break;
            }
          }
        }
      }

      if (this.isInheritedFromRDNCollective)
      {
        List<Attribute> attrList = entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_RDN);
        if (attrList != null && !attrList.isEmpty())
        {
          for (Attribute attr : attrList)
          {
            for (ByteString value : attr)
            {
              this.inheritFromRDNAttrType = DirectoryServer.getAttributeTypeOrDefault(value.toString().toLowerCase());
              this.inheritFromRDNAttrValue = value;
              break;
            }
          }
        }
        attrList = entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_RDN_TYPE);
        if (attrList != null && !attrList.isEmpty())
        {
          for (Attribute attr : attrList)
          {
            for (ByteString value : attr)
            {
              this.inheritFromRDNType = DirectoryServer.getAttributeTypeOrDefault(value.toString().toLowerCase());
              break;
            }
          }
        }
        attrList = entry.getAttribute(ATTR_INHERIT_COLLECTIVE_FROM_BASE);
        if (attrList != null && !attrList.isEmpty())
        {
          for (Attribute attr : attrList)
          {
            for (ByteString value : attr)
            {
              // Has to have a parent since subentry itself
              // cannot be a suffix entry within the server.
              this.inheritFromBaseDN = getDN().parent().child(DN.decode(value));
              break;
            }
          }
        }
      }

      List<Attribute> attrList = entry.getAttribute(
              ATTR_INHERIT_COLLECTIVE_ATTR);
      if (attrList != null && !attrList.isEmpty())
      {
        for (Attribute attr : attrList)
        {
          for (ByteString value : attr)
          {
            CollectiveVirtualAttribute collectiveAttr =
              new CollectiveVirtualAttribute(
                Attributes.empty(value.toString()));
            this.collectiveAttributes.add(collectiveAttr);
          }
        }
      }
    }

    // Establish collective attribute conflict behavior.
    if (this.isCollective || this.isInheritedCollective)
    {
      List<Attribute> attrList = entry.getAttribute(
              ATTR_COLLECTIVE_CONFLICT_BEHAVIOR);
      if (attrList != null && !attrList.isEmpty())
      {
        for (Attribute attr : attrList)
        {
          for (ByteString value : attr)
          {
            for (CollectiveConflictBehavior behavior :
              CollectiveConflictBehavior.values())
            {
              if (behavior.toString().equals(value.toString()))
              {
                this.conflictBehavior = behavior;
                break;
              }
            }
          }
        }
      }
    }
  }

  /**
   * Retrieves the distinguished name for this subentry.
   * @return  The distinguished name for this subentry.
   */
  public final DN getDN()
  {
    return this.entry.getName();
  }

  /**
   * Getter to retrieve the actual entry object
   * for this subentry.
   * @return entry object for this subentry.
   */
  public final Entry getEntry()
  {
    return this.entry;
  }

  /**
   * Indicates whether or not this subentry is
   * a collective attribute subentry.
   * @return <code>true</code> if collective,
   *         <code>false</code> otherwise.
   */
  public boolean isCollective()
  {
    return this.isCollective;
  }

  /**
   * Indicates whether or not this subentry is
   * an inherited collective attribute subentry.
   * @return <code>true</code> if inherited
   *         collective, <code>false</code>
   *         otherwise.
   */
  public boolean isInheritedCollective()
  {
    return this.isInheritedCollective;
  }

  /**
   * Indicates whether or not this subentry is
   * an inherited from DN collective attribute
   * subentry.
   * @return <code>true</code> if inherited
   *         from DN collective,
   *         <code>false</code> otherwise.
   */
  public boolean isInheritedFromDNCollective()
  {
    return this.isInheritedFromDNCollective;
  }

  /**
   * Indicates whether or not this subentry is
   * an inherited from RDN collective attribute
   * subentry.
   * @return <code>true</code> if inherited
   *         from RDN collective,
   *         <code>false</code> otherwise.
   */
  public boolean isInheritedFromRDNCollective()
  {
    return this.isInheritedFromRDNCollective;
  }

  /**
   * Getter to retrieve inheritFromDNAttribute type
   * for inherited collective attribute subentry.
   * @return Type of inheritFromDNAttribute or,
   *         <code>null</code> if there is none.
   */
  public AttributeType getInheritFromDNType()
  {
    return this.inheritFromDNType;
  }

  /**
   * Getter to retrieve inheritFromRDNAttribute type
   * for inherited collective attribute subentry.
   * @return Type of inheritFromRDNAttribute or,
   *         <code>null</code> if there is none.
   */
  public AttributeType getInheritFromRDNAttrType()
  {
    return this.inheritFromRDNAttrType;
  }

  /**
   * Getter to retrieve inheritFromRDNAttribute value
   * for inherited collective attribute subentry.
   * @return ByteString of inheritFromRDNAttribute
   *         or, <code>null</code> if there is none.
   */
  public ByteString getInheritFromRDNAttrValue()
  {
    return this.inheritFromRDNAttrValue;
  }

  /**
   * Getter to retrieve RDN type of inheritFromRDNType
   * for inherited collective attribute subentry.
   * @return RDN Type of inheritFromRDNAttribute or,
   *         <code>null</code> if there is none.
   */
  public AttributeType getInheritFromRDNType()
  {
    return this.inheritFromRDNType;
  }

  /**
   * Getter to retrieve inheritFromDNAttribute value
   * for inherited collective attribute subentry.
   * @return ByteString of inheritFromDNAttribute
   *         or, <code>null</code> if there is none.
   */
  public ByteString getInheritFromDNAttrValue()
  {
    return this.inheritFromDNAttrValue;
  }

  /**
   * Getter to retrieve inheritFromBaseRDN DN
   * for inherited collective attribute subentry.
   * @return DN of inheritFromBaseRDN or,
   *         <code>null</code> if there is none.
   */
  public DN getInheritFromBaseDN()
  {
    return this.inheritFromBaseDN;
  }

  /**
   * Getter for subentry subtree specification.
   * @return subtree specification for this subentry.
   */
  public SubtreeSpecification getSubTreeSpecification()
  {
    return this.subTreeSpec;
  }

  /**
   * Getter for collective attributes contained within this subentry.
   * @return collective attributes contained within this subentry.
   */
  public List<Attribute> getCollectiveAttributes()
  {
    return this.collectiveAttributes;
  }

  /**
   * Getter for collective conflict behavior defined for this
   * collective attributes subentry.
   * @return conflict behavior for this collective attributes
   *         subentry.
   */
  public CollectiveConflictBehavior getConflictBehavior()
  {
    return this.conflictBehavior;
  }
}

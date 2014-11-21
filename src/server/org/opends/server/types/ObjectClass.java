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
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.types;



import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.schema.ObjectClassSyntax;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a data structure for storing and interacting
 * with an objectclass, which contains a collection of attributes that
 * must and/or may be present in an entry with that objectclass.
 * <p>
 * Any methods which accesses the set of names associated with this
 * object class, will retrieve the primary name as the first name,
 * regardless of whether or not it was contained in the original set
 * of <code>names</code> passed to the constructor.
 * <p>
 * Where ordered sets of names, attribute types, or extra properties
 * are provided, the ordering will be preserved when the associated
 * fields are accessed via their getters or via the
 * {@link #toString()} methods.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class ObjectClass
       extends CommonSchemaElements
       implements SchemaFileElement
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of optional attribute types for this objectclass.
  private final Set<AttributeType> optionalAttributes;

  // The set of optional attribute types for this objectclass and its
  // superclasses.
  private final Set<AttributeType> optionalAttributesChain;

  // The set of required attribute types for this objectclass.
  private final Set<AttributeType> requiredAttributes;

  // The set of required attribute types for this objectclass and its
  // superclasses.
  private final Set<AttributeType> requiredAttributesChain;

  // The set of required and optional attributes for this objectclass
  // and its superclasses.
  private final Set<AttributeType> requiredAndOptionalChain;

  // The reference to one or more superior objectclasses.
  private final Set<ObjectClass> superiorClasses;

  // The objectclass type for this objectclass.
  private final ObjectClassType objectClassType;

  // Indicates whether or not this object class is allowed to
  // contain any attribute.
  private final boolean isExtensibleObject;

  // The definition string used to create this objectclass.
  private final String definition;

  // True once this object class has been removed from the schema.
  private volatile boolean isDirty = false;



  /**
   * Creates a new objectclass definition with the provided
   * information.
   * <p>
   * If no <code>primaryName</code> is specified, but a set of
   * <code>names</code> is specified, then the first name retrieved
   * from the set of <code>names</code> will be used as the primary
   * name.
   *
   * @param definition
   *          The definition string used to create this objectclass.
   *          It must not be {@code null}.
   * @param primaryName
   *          The primary name for this objectclass, or
   *          {@code null} if there is no primary name.
   * @param names
   *          The set of names that may be used to reference this
   *          objectclass.
   * @param oid
   *          The OID for this objectclass.  It must not be
   *          {@code null}.
   * @param description
   *          The description for this objectclass, or {@code null} if
   *          there is no description.
   * @param superiorClasses
   *          The superior classes for this objectclass, or
   *          {@code null} if there is no superior object class.
   * @param requiredAttributes
   *          The set of required attribute types for this
   *          objectclass.
   * @param optionalAttributes
   *          The set of optional attribute types for this
   *          objectclass.
   * @param objectClassType
   *          The objectclass type for this objectclass, or
   *          {@code null} to default to structural.
   * @param isObsolete
   *          Indicates whether this objectclass is declared
   *          "obsolete".
   * @param extraProperties
   *          A set of extra properties for this objectclass.
   */
  public ObjectClass(String definition, String primaryName,
                     Collection<String> names, String oid,
                     String description,
                     Set<ObjectClass> superiorClasses,
                     Set<AttributeType> requiredAttributes,
                     Set<AttributeType> optionalAttributes,
                     ObjectClassType objectClassType,
                     boolean isObsolete,
                     Map<String, List<String>> extraProperties)
  {
    super(primaryName, names, oid, description, isObsolete,
        extraProperties);


    ensureNotNull(definition, oid);

    // Construct unmodifiable views of the superior classes.
    if (superiorClasses != null) {
      this.superiorClasses =  Collections
          .unmodifiableSet(new LinkedHashSet<ObjectClass>(
              superiorClasses));
    } else {
      this.superiorClasses = Collections.emptySet();
    }

    int schemaFilePos = definition.indexOf(SCHEMA_PROPERTY_FILENAME);
    if (schemaFilePos > 0)
    {
      String defStr;
      try
      {
        int firstQuotePos = definition.indexOf('\'', schemaFilePos);
        int secondQuotePos = definition.indexOf('\'',
                                                firstQuotePos+1);

        defStr = definition.substring(0, schemaFilePos).trim() + " " +
                 definition.substring(secondQuotePos+1).trim();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        defStr = definition;
      }

      this.definition = defStr;
    }
    else
    {
      this.definition = definition;
    }

    // Set flag indicating whether or not this object class allows any
    // attributes.
    if (hasName(OC_EXTENSIBLE_OBJECT_LC)
        || oid.equals(OID_EXTENSIBLE_OBJECT)) {
      this.isExtensibleObject = true;
    } else {
      this.isExtensibleObject = false;
    }

    // Construct unmodifiable views of the required attributes.
    if (requiredAttributes != null) {
      this.requiredAttributes = Collections
          .unmodifiableSet(new LinkedHashSet<AttributeType>(
              requiredAttributes));
    } else {
      this.requiredAttributes = Collections.emptySet();
    }

    if (this.superiorClasses.isEmpty()) {
      this.requiredAttributesChain = this.requiredAttributes;
    } else {
      Set<AttributeType> tmp = new HashSet<AttributeType>(
          this.requiredAttributes);
      for(ObjectClass oc: this.superiorClasses)
      {
        tmp.addAll(oc.getRequiredAttributeChain());
      }
      this.requiredAttributesChain = Collections.unmodifiableSet(tmp);
    }

    // Construct unmodifiable views of the optional attributes.
    if (optionalAttributes != null) {
      this.optionalAttributes = Collections
          .unmodifiableSet(new LinkedHashSet<AttributeType>(
              optionalAttributes));
    } else {
      this.optionalAttributes = Collections.emptySet();
    }

    if (this.superiorClasses.isEmpty()) {
      this.optionalAttributesChain = this.optionalAttributes;
    } else {
      Set<AttributeType> tmp = new HashSet<AttributeType>(
          this.optionalAttributes);
      for(ObjectClass oc : this.superiorClasses)
      {
        tmp.addAll(oc.getOptionalAttributeChain());
      }
      this.optionalAttributesChain = Collections.unmodifiableSet(tmp);
    }

    // Construct unmodifiable views of the required and optional
    // attribute chains.
    HashSet<AttributeType> reqAndOptSet =
         new HashSet<AttributeType>(requiredAttributesChain.size() +
                                    optionalAttributesChain.size());
    reqAndOptSet.addAll(requiredAttributesChain);
    reqAndOptSet.addAll(optionalAttributesChain);
    requiredAndOptionalChain =
         Collections.<AttributeType>unmodifiableSet(reqAndOptSet);

    // Object class type defaults to structural.
    if (objectClassType != null) {
      this.objectClassType = objectClassType;
    } else {
      this.objectClassType = ObjectClassType.STRUCTURAL;
    }
  }



  /**
   * Retrieves the definition string used to create this objectclass.
   *
   * @return  The definition string used to create this objectclass.
   */
  public String getDefinition()
  {
    return definition;
  }



  /**
   * Retrieves the definition string used to create this objectclass
   * including the X-SCHEMA-FILE extension.
   *
   * @return  The definition string used to create this objectclass
   *          including the X-SCHEMA-FILE extension.
   */
  public String getDefinitionWithFileName()
  {
    if (getSchemaFile() != null)
    {
      int pos = definition.lastIndexOf(')');
      String defStr = definition.substring(0, pos).trim() + " " +
                      SCHEMA_PROPERTY_FILENAME + " '" +
                      getSchemaFile() + "' )";
      return defStr;
    }
    else
      return definition;
  }



  /**
   * {@inheritDoc}
   */
  public ObjectClass recreateFromDefinition(Schema schema)
         throws DirectoryException
  {
    ByteString value  = ByteString.valueOf(definition);
    ObjectClass oc = ObjectClassSyntax.decodeObjectClass(value,
                                            schema, false);
    oc.setSchemaFile(getSchemaFile());
    return oc;
  }



  /**
   * Retrieves an unmodifiable view of the set of direct superior
   * classes for this objectclass.
   *
   * @return An unmodifiable view of the set of  direct superior
   *                classes for this objectlass,
   */
  public Set<ObjectClass> getSuperiorClasses() {
    return superiorClasses;
  }



  /**
   * Indicates whether this objectclass is a descendant of the
   * provided class.
   *
   * @param objectClass
   *          The objectClass for which to make the determination.
   * @return <code>true</code> if this objectclass is a descendant
   *         of the provided class, or <code>false</code> if not.
   */
  public boolean isDescendantOf(ObjectClass objectClass) {

    for(ObjectClass oc : superiorClasses) {
      if(oc.equals(objectClass) || oc.isDescendantOf(objectClass)) {
        return true;
      }
    }
    return false;
  }



  /**
   * Retrieves an unmodifiable view of the set of required attributes
   * for this objectclass. Note that this set will not automatically
   * include any required attributes for superior objectclasses.
   *
   * @return Returns an unmodifiable view of the set of required
   *         attributes for this objectclass.
   */
  public Set<AttributeType> getRequiredAttributes() {

    return requiredAttributes;
  }



  /**
   * Retrieves an unmodifiable view of the set of all required
   * attributes for this objectclass and any superior objectclasses
   * that it might have.
   *
   * @return Returns an unmodifiable view of the set of all required
   *         attributes for this objectclass and any superior
   *         objectclasses that it might have.
   */
  public Set<AttributeType> getRequiredAttributeChain() {

    return requiredAttributesChain;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * required attribute list for this or any of its superior
   * objectclasses.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the provided attribute type is
   *         required by this objectclass or any of its superior
   *         classes, or <code>false</code> if not.
   */
  public boolean isRequired(AttributeType attributeType) {

    return requiredAttributesChain.contains(attributeType);
  }



  /**
   * Retrieves an unmodifiable view of the set of optional attributes
   * for this objectclass. Note that this list will not automatically
   * include any optional attributes for superior objectclasses.
   *
   * @return Returns an unmodifiable view of the set of optional
   *         attributes for this objectclass.
   */
  public Set<AttributeType> getOptionalAttributes() {

    return optionalAttributes;
  }



  /**
   * Retrieves an unmodifiable view of the set of optional attributes
   * for this objectclass and any superior objectclasses that it might
   * have.
   *
   * @return Returns an unmodifiable view of the set of optional
   *         attributes for this objectclass and any superior
   *         objectclasses that it might have.
   */
  public Set<AttributeType> getOptionalAttributeChain() {

    return optionalAttributesChain;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * optional attribute list for this or any of its superior
   * objectclasses.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the provided attribute type is
   *         optional for this objectclass or any of its superior
   *         classes, or <code>false</code> if not.
   */
  public boolean isOptional(AttributeType attributeType) {

    if (optionalAttributesChain.contains(attributeType)) {
      return true;
    }

    if (isExtensibleObject
        && !requiredAttributesChain.contains(attributeType)) {
      // FIXME -- Do we need to do other checks here, like whether the
      // attribute type is actually defined in the schema?
      // What about DIT content rules?
      return true;
    }

    return false;
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this objectclass or any of
   * its superior classes.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the provided attribute type is
   *         required or allowed for this objectclass or any of its
   *         superior classes, or <code>false</code> if it is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType) {

    // FIXME -- Do we need to do any other checks here, like whether
    // the attribute type is actually defined in the schema?
    return (isExtensibleObject ||
            requiredAndOptionalChain.contains(attributeType));
  }



  /**
   * Retrieves the objectclass type for this objectclass.
   *
   * @return The objectclass type for this objectclass.
   */
  public ObjectClassType getObjectClassType() {

    return objectClassType;
  }



  /**
   * Indicates whether this objectclass is the extensibleObject
   * objectclass.
   *
   * @return <code>true</code> if this objectclass is the
   *         extensibleObject objectclass, or <code>false</code> if
   *         it is not.
   */
  public boolean isExtensibleObject() {

    return isExtensibleObject;
  }



  /**
   * Appends a string representation of this schema definition's
   * non-generic properties to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  protected void toStringContent(StringBuilder buffer) {

    if (!superiorClasses.isEmpty()) {
      buffer.append(" SUP ");
      Iterator<ObjectClass> iterator = superiorClasses.iterator();
      ObjectClass oc =  iterator.next();

      if(iterator.hasNext()) {
        buffer.append("( ");
        buffer.append(oc.getNameOrOID());

        while(iterator.hasNext()) {
          buffer.append(" $ ");
          buffer.append(iterator.next().getNameOrOID());
        }

        buffer.append(" )");
      } else {
        buffer.append(oc.getNameOrOID());
      }
    }

    if (objectClassType != null) {
      buffer.append(" ");
      buffer.append(objectClassType.toString());
    }

    if (!requiredAttributes.isEmpty()) {
      Iterator<AttributeType> iterator = requiredAttributes
          .iterator();

      String firstName = iterator.next().getNameOrOID();
      if (iterator.hasNext()) {
        buffer.append(" MUST ( ");
        buffer.append(firstName);

        while (iterator.hasNext()) {
          buffer.append(" $ ");
          buffer.append(iterator.next().getNameOrOID());
        }

        buffer.append(" )");
      } else {
        buffer.append(" MUST ");
        buffer.append(firstName);
      }
    }

    if (!optionalAttributes.isEmpty()) {
      Iterator<AttributeType> iterator = optionalAttributes
          .iterator();

      String firstName = iterator.next().getNameOrOID();
      if (iterator.hasNext()) {
        buffer.append(" MAY ( ");
        buffer.append(firstName);

        while (iterator.hasNext()) {
          buffer.append(" $ ");
          buffer.append(iterator.next().getNameOrOID());
        }

        buffer.append(" )");
      } else {
        buffer.append(" MAY ");
        buffer.append(firstName);
      }
    }
  }



  /**
   * Marks this object class as dirty, indicating that it has been removed or
   * replaced in the schema.
   *
   * @return A reference to this object class.
   */
  public ObjectClass setDirty()
  {
    isDirty = true;
    return this;
  }



  /**
   * Returns {@code true} if this object class has been removed or replaced in
   * the schema.
   *
   * @return {@code true} if this object class has been removed or replaced in
   *         the schema.
   */
  public boolean isDirty()
  {
    return isDirty;
  }
}

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

package org.opends.server.admin;

import org.opends.server.authorization.dseecompat.Aci;
import org.opends.server.authorization.dseecompat.AciException;
import org.opends.server.types.DN;
import org.forgerock.opendj.ldap.ByteString;
import static org.forgerock.util.Reject.ifNull;

import java.util.EnumSet;

/**
 * ACI property definition.
 */
public class ACIPropertyDefinition extends PropertyDefinition<Aci> {


  /**
   * An interface for incrementally constructing ACI property
   * definitions.
   */
  public static class Builder extends
      AbstractBuilder<Aci, ACIPropertyDefinition> {

    /** Private constructor. */
    private Builder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
      super(d, propertyName);
    }

    /** {@inheritDoc} */
    @Override
    protected ACIPropertyDefinition buildInstance(
        AbstractManagedObjectDefinition<?, ?> d,
        String propertyName, EnumSet<PropertyOption> options,
        AdministratorAction adminAction,
        DefaultBehaviorProvider<Aci> defaultBehavior) {
      return new ACIPropertyDefinition(d, propertyName, options,
          adminAction, defaultBehavior);
    }
  }


  /**
   * Create a ACI property definition builder.
   *
   * @param d
   *          The managed object definition associated with this
   *          property definition.
   * @param propertyName
   *          The property name.
   * @return Returns the new ACI property definition builder.
   */
  public static Builder createBuilder(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
    return new Builder(d, propertyName);
  }


  /** Private constructor. */
  private ACIPropertyDefinition(
      AbstractManagedObjectDefinition<?, ?> d, String propertyName,
      EnumSet<PropertyOption> options,
      AdministratorAction adminAction,
      DefaultBehaviorProvider<Aci> defaultBehavior) {
    super(d, Aci.class, propertyName, options, adminAction,
        defaultBehavior);
  }


  /** {@inheritDoc} */
  @Override
  public void validateValue(Aci value)
      throws PropertyException {
    ifNull(value);

    // No additional validation required.
  }

  /** {@inheritDoc} */
  @Override
  public Aci decodeValue(String value)
      throws PropertyException {
    ifNull(value);

    try {
      return Aci.decode(ByteString.valueOf(value), DN.NULL_DN);
    } catch (AciException e) {
      // TODO: it would be nice to throw the cause.
      throw PropertyException.illegalPropertyValueException(this, value);
    }
  }


  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
    return v.visitACI(this, p);
  }

  /** {@inheritDoc} */
  @Override
  public <R, P> R accept(PropertyValueVisitor<R, P> v, Aci value, P p) {
    return v.visitACI(this, value, p);
  }


  /** {@inheritDoc} */
  @Override
  public int compare(Aci o1, Aci o2) {
    return o1.toString().compareTo(o2.toString());
  }
}

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
 */

package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.AdminMessages.ERR_DEFAULT_BEHAVIOR_PROPERTY_EXCEPTION;
import static com.forgerock.opendj.ldap.AdminMessages.ERR_ILLEGAL_PROPERTY_VALUE_EXCEPTION;
import static com.forgerock.opendj.ldap.AdminMessages.ERR_PROPERTY_IS_MANDATORY_EXCEPTION;
import static com.forgerock.opendj.ldap.AdminMessages.ERR_PROPERTY_IS_READ_ONLY_EXCEPTION;
import static com.forgerock.opendj.ldap.AdminMessages.ERR_PROPERTY_IS_SINGLE_VALUED_EXCEPTION;
import static com.forgerock.opendj.ldap.AdminMessages.ERR_UNKNOWN_PROPERTY_DEFINITION_EXCEPTION;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Exceptions thrown as a result of errors that occurred when decoding and
 * modifying property values.
 */
public final class PropertyException extends RuntimeException implements LocalizableException {

    /**
     * Version ID required by serializable classes.
     */
    private static final long serialVersionUID = -8465109598081914482L;

    /**
     * Creates a new default behavior exception with a cause.
     *
     * @param pd
     *            The property definition whose default values could not be
     *            determined.
     * @param cause
     *            The exception that prevented the default values from being
     *            determined.
     * @return A new default behavior exception with a cause.
     */
    public static PropertyException defaultBehaviorException(final PropertyDefinition<?> pd,
            final Throwable cause) {
        return new PropertyException(pd, ERR_DEFAULT_BEHAVIOR_PROPERTY_EXCEPTION.get(pd.getName()),
                cause);
    }

    /**
     * Creates a new illegal property value exception.
     *
     * @param pd
     *            The property definition.
     * @param value
     *            The illegal property value.
     * @return A new illegal property value exception.
     */
    public static PropertyException illegalPropertyValueException(final PropertyDefinition<?> pd,
            final Object value) {
        return new PropertyException(pd, createMessage(pd, value));
    }

    /**
     * Creates a new illegal property value exception.
     *
     * @param pd
     *            The property definition.
     * @param value
     *            The illegal property value.
     * @param cause
     *            The cause.
     * @return A new illegal property value exception.
     */
    public static PropertyException illegalPropertyValueException(final PropertyDefinition<?> pd,
            final Object value, final Throwable cause) {
        return new PropertyException(pd, createMessage(pd, value), cause);
    }

    /**
     * Creates a new property is mandatory exception.
     *
     * @param pd
     *            The property definition.
     * @return A new property is mandatory exception.
     */
    public static PropertyException propertyIsMandatoryException(final PropertyDefinition<?> pd) {
        return new PropertyException(pd, ERR_PROPERTY_IS_MANDATORY_EXCEPTION.get(pd.getName()));
    }

    /**
     * Creates a new property is read-only exception.
     *
     * @param pd
     *            The property definition.
     * @return A new property is read-only exception.
     */
    public static PropertyException propertyIsReadOnlyException(final PropertyDefinition<?> pd) {
        return new PropertyException(pd, ERR_PROPERTY_IS_READ_ONLY_EXCEPTION.get(pd.getName()));
    }

    /**
     * Creates a new property is single valued exception.
     *
     * @param pd
     *            The property definition.
     * @return A new property is single valued exception.
     */
    public static PropertyException propertyIsSingleValuedException(final PropertyDefinition<?> pd) {
        return new PropertyException(pd, ERR_PROPERTY_IS_SINGLE_VALUED_EXCEPTION.get(pd.getName()));
    }

    /**
     * Creates a new unknown property definition exception.
     *
     * @param pd
     *            The unknown property definition.
     * @param p
     *            The visitor parameter if there was one.
     * @return A new unknown property definition exception.
     */
    public static PropertyException unknownPropertyDefinitionException(
            final PropertyDefinition<?> pd, final Object p) {
        return new PropertyException(pd, ERR_UNKNOWN_PROPERTY_DEFINITION_EXCEPTION.get(
                pd.getName(), pd.getClass().getName()));
    }

    /** Create the message. */
    private static LocalizableMessage createMessage(final PropertyDefinition<?> pd,
            final Object value) {
        final PropertyDefinitionUsageBuilder builder = new PropertyDefinitionUsageBuilder(true);
        return ERR_ILLEGAL_PROPERTY_VALUE_EXCEPTION.get(String.valueOf(value), pd.getName(),
                builder.getUsage(pd));
    }

    /** LocalizableMessage that explains the problem. */
    private final LocalizableMessage message;

    /**
     * The property definition associated with the property that caused
     * the exception.
     */
    private final PropertyDefinition<?> pd;

    private PropertyException(final PropertyDefinition<?> pd, final LocalizableMessage message) {
        super(message.toString());
        this.message = message;
        this.pd = pd;
    }

    private PropertyException(final PropertyDefinition<?> pd, final LocalizableMessage message,
            final Throwable cause) {
        super(message.toString(), cause);
        this.message = message;
        this.pd = pd;
    }

    /** {@inheritDoc} */
    public LocalizableMessage getMessageObject() {
        return message;
    }

    /**
     * Returns the property definition associated with the property that caused
     * the exception.
     *
     * @return The property definition associated with the property that caused
     *         the exception.
     */
    public final PropertyDefinition<?> getPropertyDefinition() {
        return pd;
    }

}

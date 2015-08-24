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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;

import java.util.HashSet;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;

/**
 * A class representing an ACI's targetattr keyword.
 */
class TargetAttr {
    /** Enumeration representing the targetattr operator. */
    private final EnumTargetOperator operator;

    /** Flags that is set if all user attributes pattern seen "*". */
    private boolean allUserAttributes;
    /** Flags that is set if all operational attributes pattern seen "+". */
    private boolean allOpAttributes;
    /** Set of the attribute types parsed by the constructor. */
    private final HashSet<AttributeType> attributes = new HashSet<>();
    /** Set of the operational attribute types parsed by the constructor. */
    private final HashSet<AttributeType> opAttributes = new HashSet<>();

    /**
     * Regular expression that matches one or more ATTR_NAME's separated by
     * the "||" token.
     */
    private static final String attrListRegex  =  ZERO_OR_MORE_WHITESPACE +
           ATTR_NAME + ZERO_OR_MORE_WHITESPACE + "(" +
            LOGICAL_OR + ZERO_OR_MORE_WHITESPACE + ATTR_NAME +
            ZERO_OR_MORE_WHITESPACE + ")*";

    /**
     * Constructor creating a class representing a targetattr keyword of an ACI.
     * @param operator The operation enumeration of the targetattr
     * expression (=, !=).
     * @param attrString A string representing the attributes specified in
     * the targetattr expression (ie, dn || +).
     * @throws AciException If the attrs string is invalid.
     */
    private TargetAttr(EnumTargetOperator operator, String attrString)
    throws AciException {
        this.operator = operator;
        if (attrString != null) {
            if (Pattern.matches(ALL_USER_ATTRS_WILD_CARD, attrString)) {
                allUserAttributes = true ;
            } else if (Pattern.matches(ALL_OP_ATTRS_WILD_CARD, attrString)) {
                allOpAttributes = true ;
            } else if (Pattern.matches(ZERO_OR_MORE_WHITESPACE, attrString)) {
                allUserAttributes = false;
                allOpAttributes = false;
            } else if (Pattern.matches(attrListRegex, attrString)) {
                // Remove the spaces in the attr string and
                // split the list.
                Pattern separatorPattern = Pattern.compile(LOGICAL_OR);
                attrString = attrString.replaceAll(ZERO_OR_MORE_WHITESPACE, "");
                String[] attributeArray = separatorPattern.split(attrString);
                // Add each element of array to appropriate HashSet
                // after conversion to AttributeType.
                arrayToAttributeTypes(attributeArray, attrString);
            } else {
                throw new AciException(WARN_ACI_SYNTAX_INVALID_TARGETATTRKEYWORD_EXPRESSION.get(attrString));
            }
        }
    }


    /**
     * Converts each element of an array of attribute strings
     * to attribute types and adds them to either the user attributes HashSet or
     * the operational attributes HashSet. Also, scan for the shorthand tokens
     * "*" for all user attributes and "+" for all operational attributes.
     *
     * @param attributeArray The array of attribute type strings.
     * @param attrStr String used in error message if an Aci Exception
     *                is thrown.
     * @throws AciException If the one of the attribute checks fails.
     */
    private void arrayToAttributeTypes(String[] attributeArray, String attrStr)
            throws AciException {
        for (String attr : attributeArray) {
            String attribute = attr.toLowerCase();
            if(attribute.equals("*")) {
                if(!allUserAttributes)
                {
                  allUserAttributes=true;
                }
                else {
                    LocalizableMessage message =
                        WARN_ACI_TARGETATTR_INVALID_ATTR_TOKEN.get(attrStr);
                    throw new AciException(message);
                }
            } else if(attribute.equals("+")) {
                if(!allOpAttributes)
                {
                  allOpAttributes=true;
                }
                else {
                    LocalizableMessage message =
                        WARN_ACI_TARGETATTR_INVALID_ATTR_TOKEN.get(attrStr);
                    throw new AciException(message);
                }
            } else {
                AttributeType attrType = DirectoryServer.getAttributeTypeOrDefault(attribute);
                if(attrType.isOperational())
                {
                  opAttributes.add(attrType);
                }
                else
                {
                  attributes.add(attrType);
                }
            }
        }
    }

    /**
     * Returns the operator enumeration of the targetattr expression.
     * @return The operator enumeration.
     */
    public EnumTargetOperator getOperator() {
        return operator;
    }

    /**
     * This flag is set if the parsing code saw:
     * targetattr="*" or targetattr != "*".
     * @return True if all attributes was seen.
     */
    public boolean isAllUserAttributes() {
        return allUserAttributes;
    }

    /**
     * This flag is set if the parsing code saw:
     * targetattr="+" or targetattr != "+".
     * @return True if all attributes was seen.
     */
    public boolean isAllOpAttributes() {
        return allOpAttributes;
    }

    /**
     * Decodes an targetattr expression string into a targetattr class suitable
     * for evaluation.
     * @param operator The operator enumeration of the expression.
     * @param expr The expression string to be decoded.
     * @return A TargetAttr suitable to evaluate this ACI's targetattrs.
     * @throws AciException If the expression string is invalid.
     */
    static TargetAttr decode(EnumTargetOperator operator, String expr) throws AciException  {
        return new TargetAttr(operator, expr);
    }

    /**
     * Performs test to see if the specified attribute type is applicable
     * to the specified TargetAttr. First a check if the TargetAttr parsing
     * code saw an expression like:
     *
     *  (targetattrs="+ || *"), (targetattrs != "* || +")
     *
     * where both shorthand tokens where parsed. IF so then the attribute type
     * matches automatically (or not matches if NOT_EQUALITY).
     *
     * If there isn't a match, then the method evalAttrType is called to further
     * evaluate the attribute type and targetAttr combination.
     *
     *
     * @param a The attribute type to evaluate.
     * @param targetAttr The ACI's TargetAttr class to evaluate against.
     * @return The boolean result of the above tests and application
     * TargetAttr's operator value applied to the test result.
     */
    static boolean isApplicable(AttributeType a, TargetAttr targetAttr) {
        if(targetAttr.isAllUserAttributes() && targetAttr.isAllOpAttributes()) {
            return !targetAttr.getOperator().equals(EnumTargetOperator.NOT_EQUALITY);
        } else {
            return evalAttrType(a, targetAttr);
        }
    }

    /**
     * First check is to see if the attribute type is operational. If so then
     * a match is true if the allOpAttributes boolean is true or if the
     * attribute type is found in the operational attributes HashSet.
     * Both results can be negated if the expression operator is NOT_EQUALITY).
     *
     * Second check is similar to above, except the user attributes boolean
     * and HashSet is examined.
     *
     *
     * @param a The attribute type to evaluate.
     * @param targetAttr The targetAttr to apply to the attribute type.
     * @return True if the attribute type is applicable to the targetAttr.
     */
    private static boolean evalAttrType(AttributeType a, TargetAttr targetAttr) {
        final EnumTargetOperator op = targetAttr.getOperator();
        if(a.isOperational()) {
            return evalAttrType(a, targetAttr.isAllOpAttributes(), targetAttr.opAttributes, op);
        } else {
            return evalAttrType(a, targetAttr.isAllUserAttributes(), targetAttr.attributes, op);
        }
      }

    private static boolean evalAttrType(AttributeType attrType, boolean allAttrs,
            HashSet<AttributeType> attrs, EnumTargetOperator op) {
        boolean ret = allAttrs || attrs.contains(attrType);
        if ((allAttrs || !attrs.isEmpty())
            && op.equals(EnumTargetOperator.NOT_EQUALITY))
        {
          return !ret;
        }
        return ret;
    }
}

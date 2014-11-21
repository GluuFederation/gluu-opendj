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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.AttributeType;
import org.opends.server.api.Group;

import java.net.InetAddress;
import java.util.LinkedList;

/**
 * Interface that provides a view of the AciContainer that is
 * used by the ACI evaluation code to evaluate an ACI.
 */
public interface AciEvalContext
{
    /**
     * Get client DN. The client DN is the authorization DN.
     * @return   The client DN.
     */
    public DN getClientDN();

    /**
     * Get the client entry. The client entry is the entry that corresponds
     * to the client DN.
     * @return The client entry corresponding to the client DN.
     */
    public Entry getClientEntry();

    /**
     * Get the resource DN. The resource DN is the DN of the entry being
     * evaluated.
     * @return   The resource DN.
     */
    public DN getResourceDN();

    /**
     * Get the list of deny ACIs.
     * @return The deny ACI list.
     */
    public LinkedList<Aci> getDenyList();

    /**
     * Get the list allow ACIs.
     * @return The allow ACI list.
     */
    public LinkedList<Aci> getAllowList();

    /**
     * Set when the deny list is being evaluated.
     * @param v True if deny's are being evaluated.
     */
    public void setDenyEval(boolean v);

    /**
     * Returns true if the deny list is being evaluated.
     * @return True if the deny list is being evaluated.
     */
    public boolean isDenyEval();

    /**
     * Check if the remote client is bound anonymously.
     * @return {@code true} if client is bound anonymously.
     */
    public boolean isAnonymousUser();

    /**
     * Return the rights set for this container's LDAP operation.
     * @return  The rights set for the container's LDAP operation.
     */
    public int getRights();

    /**
     * Return the entry being evaluated
     * .
     * @return The evaluation entry.
     */
    public Entry getResourceEntry();

    /**
     * Get the hostname of the bound connection.
     * @return The hostname of the connection.
     */
    public String getHostName();

    /**
     * Determine whether the client connection has been authenticated using
     * a specified authentication method.  This method is used for the
     * authmethod bind rule keyword.
     *
     * @param authMethod The required authentication method.
     * @param saslMech The required SASL mechanism if the authentication method
     * is SASL.
     *
     * @return An evaluation result indicating whether the client connection
     * has been authenticated using the required authentication method.
     */
    public EnumEvalResult hasAuthenticationMethod(EnumAuthMethod authMethod,
                                                  String saslMech);

    /**
     * Get the  address of the bound connection.
     * @return The address of the bound connection.
     */
    public InetAddress getRemoteAddress();

    /**
     * Return true if this is an add operation needed by the userattr
     * USERDN parent inheritance level 0 processing.
     *
     * @return {@code true} if this is an add operation.
     */
    public boolean isAddOperation();

    /**
     * Return true if the operation associated with this evaluation
     * context is a member of the specified group. Calls the
     * ClientConnection.isMemberOf() method, which checks authorization
     * DN membership in the specified group.
     * @param group The group to check membership in.
     * @return {@code true} if the authorization DN of the operation is a
     * member of the specified group.
     */
    public boolean isMemberOf(Group<?> group);

  /**
   * Returns true if the hashtable of ACIs that matched the targattrfilters
   * keyword evaluation is empty.  Used in a geteffectiverights control
   * evaluation to determine the access value to put in the "write" rights
   * evaluation field.
   *
   * @return {@code true} if there were not any ACIs that matched
   *         targattrfilters keyword evaluation.
   */
    public boolean isTargAttrFilterMatchAciEmpty();

  /**
   * The context maintains a hashtable of ACIs that matched the targattrfilters
   * keyword evaluation.  The hasTargAttrFiltersMatchAci method returns true if
   * the specified ACI is contained in that hashtable. Used in a
   * geteffectiverights control evaluation to determine the access value to put
   * in the "write" rights evaluation field.
   *
   * @param aci The ACI that to evaluate if it contains a match during
   *            targattrfilters keyword evaluation.
   *
   * @return {@code true} if a specified ACI matched targattrfilters evaluation.
   */
    public boolean hasTargAttrFiltersMatchAci(Aci aci);

  /**
   * Return true if an ACI that evaluated to deny or allow has an
   * targattrfilters keyword. Used by geteffectiverights control
   * evaluation to determine the access value to put in the "write" rights
   * evaluation field.
   *
   * @param flag  The integer value specifying either a deny or allow, but not
   * both.
   *
   * @return  {@code true} if the ACI has an targattrfilters keyword.
   */
    public boolean hasTargAttrFiltersMatchOp(int flag);

  /**
   * Returns {@code true} if the evaluation context is being used in a
   * geteffectiverights control evaluation.
   *
   * @return  {@code true} if the evaluation context is being used in a
   * geteffectiverights control evaluation.
   */
    public boolean isGetEffectiveRightsEval();

  /**
   * Set the name of the ACI that last matched a targattrfilters rule. Used
   * in geteffectiverights control targattrfilters "write" evaluation.
   *
   * @param name The ACI name string matching the targattrfilters rule.
   */
    public void setTargAttrFiltersAciName(String name);

  /**
   * Set a flag that specifies that a ACI that evaluated to either deny or
   * allow contains a targattrfilters keyword. Used by geteffectiverights
   * control evaluation to determine the access value to put in the "write"
   * rights evaluation field.
   *
   * @param flag Either the integer value representing an allow or a deny,
   *             but not both.
   */
    public void setTargAttrFiltersMatchOp(int flag);

  /**
   * Set the reason the last access evaluation was evaluated the way it
   * was. Used by geteffectiverights control evaluation to eventually build the
   * summary string.
   *
   * @param reason  The enumeration representing the reason of the last access
   * evaluation.
   */
    public void setEvalReason(EnumEvalReason reason);

  /**
   * Return the reason the last access evaluation was evaluated the way it
   * was. Used by geteffectiverights control evaluation to build the summary
   * string.
   *
   * @return The enumeration representing the reason of the last access
   * evaluation.
   */
    public EnumEvalReason getEvalReason();

  /**
   * Set the ACI that decided that last access evaluation. Used by
   * geteffectiverights control evaluation to the build summary string.
   *
   * @param aci The ACI that decided the last access evaluation.
   */
    public void setDecidingAci(Aci aci);

  /**
   * Check if an evaluation context contains a set of access rights.
   *
   * @param rights The rights mask to check.
   *
   * @return {@code true} if the evaluation context contains a access right set.
   */
    public boolean hasRights(int rights);

  /**
   * Return the name of the ACI that decided the last access evaluation. Used
   * by geteffectiverights control evaluation to build the summary string.
   *
   * @return The name of the ACI that decided the last access evaluation.
   */
    public String getDecidingAciName();

  /**
   * Return true if a evaluation context is being used in proxied authorization
   * control evaluation.
   *
   * @return  {@code true} if evaluation context is being used in proxied
   *          authorization control evaluation.
   */
    public boolean isProxiedAuthorization();

    /**
     * Get the current attribute type being evaluated.
     *
     * @return  The attribute type currently being evaluated.
     */
    public AttributeType getCurrentAttributeType();

  /**
   * Set the value of the summary string to the specified string.
   * Used in get effective rights evaluation to build summary string.
   *
   * @param summary The string to set the summary string to
   */
    public void setEvalSummary(String summary);

  /**
   * Return the access evaluation summary string. Used in a geteffectiverights
   * control evaluation when an aclRightsInfo attribute was specified in a
   * search request.
   *
   * @return   The string describing the access evaluation.
   */
    public String getEvalSummary();

  /**
   * Return a string representation of the current right being evaluated.
   * Used in geteffectiverights control evaluation to build summary string.
   *
   * @return  String representation of the current right being evaluated.
   */
    public String rightToString();

    /**
     * Return the name of the ACI that last matched a targattrfilters rule. Used
     * in geteffectiverights control evaluation.
     *
     * @return The name of the ACI that last matched a targattrfilters rule.
     */
    public String getTargAttrFiltersAciName();


    /**
     * Return the current SSF (Security Strength Factor) of the underlying
     * connection.
     *
     * @return The current SSF of the connection.
     */
    public int getCurrentSSF();
}

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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.opends.server.authorization.dseecompat;
import org.opends.messages.Message;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents the body of an ACI. The body of the ACI is the
 * version, name, and permission-bind rule pairs.
 */
public class AciBody {

    /*
     * Regular expression group position for the version string.
     */
    private static final int VERSION = 1;

    /*
     * Regular expression group position for the name string.
     */
    private static final int NAME = 2;

    /*
     * Regular expression group position for the permission string.
     */
    private static final int PERM = 1;

    /*
     * Regular expression group position for the rights string.
     */
    private static final int RIGHTS = 2;

    /*
     * Regular expression group position for the bindrule string.
     */
    private static final int BINDRULE = 3;

    /*
     * Index into the ACI string where the ACI body starts.
     */
    private int startPos=0;

    /*
    * The name of the ACI, currently not used but parsed.
    */
    private String name = null;

    /*
    * The version of the ACi, current not used but parsed and checked
    * for 3.0.
    */
    private String version = null;

    /*
     This structure represents a permission-bind rule pairs. There can be
     several of these.
    */
    private List<PermBindRulePair> permBindRulePairs;

    /*
     * Regular expression used to match the access type group (allow, deny) and
     * the rights group "(read, write, ...)". The last pattern looks for a group
     * surrounded by parenthesis. The group must contain at least one
     * non-paren character.
     */
    private static final
    String permissionRegex =
               WORD_GROUP + ZERO_OR_MORE_WHITESPACE + "\\(([^()]+)\\)";

    /*
     * Regular expression that matches a bind rule group at a coarse level. It
     * matches any character one or more times, a single quotation and
     * an optional right parenthesis.
     */
    private static final String bindRuleRegex =
            "(.+?\"[)]*)" + ACI_STATEMENT_SEPARATOR;

    /*
     * Regular expression used to match the actions of the ACI. The actions
     * are permissions and matching bind rules.
     */
    private static final String actionRegex =
            ZERO_OR_MORE_WHITESPACE + permissionRegex +
            ZERO_OR_MORE_WHITESPACE + bindRuleRegex;

    /*
     * Regular expression used to match the version value (digit.digit).
     */
    private static final String versionRegex = "(\\d\\.\\d)";

    /*
     * Regular expression used to match the version token. Case insensitive.
     */
    private static final String versionToken = "(?i)version(?-i)";

    /*
     * Regular expression used to match the acl token. Case insensitive.
     */
    private static final String aclToken = "(?i)acl(?-i)";

    /**
     * Regular expression used to match the body of an ACI. This pattern is
     * a general verification check.
     */
    public static final String bodyRegx =
        "\\(" + ZERO_OR_MORE_WHITESPACE + versionToken +
        ZERO_OR_MORE_WHITESPACE + versionRegex +
        ACI_STATEMENT_SEPARATOR + aclToken + ZERO_OR_MORE_WHITESPACE +
        "\"([^\"]*)\"" + ACI_STATEMENT_SEPARATOR + actionRegex +
        ZERO_OR_MORE_WHITESPACE  + "\\)";

    /*
     * Regular expression used to match the header of the ACI body. The
     * header is version and acl name.
     */
    private static final String header =
       OPEN_PAREN + ZERO_OR_MORE_WHITESPACE + versionToken +
       ZERO_OR_MORE_WHITESPACE +
       versionRegex + ACI_STATEMENT_SEPARATOR + aclToken +
       ZERO_OR_MORE_WHITESPACE +  "\"(.*?)\"" + ACI_STATEMENT_SEPARATOR;

    /**
     * Construct an ACI body from the specified version, name and
     * permission-bind rule pairs.
     *
     * @param verision The version of the ACI.
     * @param name The name of the ACI.
     * @param startPos The start position in the string of the ACI body.
     * @param permBindRulePairs The set of fully parsed permission-bind rule
     * pairs pertaining to this ACI.
     */
    private AciBody(String verision, String name, int startPos,
            List<PermBindRulePair> permBindRulePairs) {
        this.version=verision;
        this.name=name;
        this.startPos=startPos;
        this.permBindRulePairs=permBindRulePairs;
    }

    /**
     * Decode an ACI string representing the ACI body.
     *
     * @param input String representation of the ACI body.
     * @return An AciBody class representing the decoded ACI body string.
     * @throws AciException If the provided string contains errors.
     */
    public static AciBody decode(String input)
    throws AciException {
        String version=null, name=null;
        int startPos=0;
        List<PermBindRulePair> permBindRulePairs=
                new ArrayList<PermBindRulePair>();
        Pattern bodyPattern = Pattern.compile(header);
        Matcher bodyMatcher = bodyPattern.matcher(input);
        if(bodyMatcher.find()) {
            startPos=bodyMatcher.start();
            version  = bodyMatcher.group(VERSION);
            if (!version.equalsIgnoreCase(supportedVersion)) {
                Message message = WARN_ACI_SYNTAX_INVAILD_VERSION.get(version);
                throw new AciException(message);
            }
            name = bodyMatcher.group(NAME);
            input = input.substring(bodyMatcher.end());
        }

        Pattern bodyPattern1 = Pattern.compile("\\G" + actionRegex);
        Matcher bodyMatcher1 = bodyPattern1.matcher(input);

        /*
         * The may be many permission-bind rule pairs.
         */
        int lastIndex = -1;
        while(bodyMatcher1.find()) {
         String perm=bodyMatcher1.group(PERM);
         String rights=bodyMatcher1.group(RIGHTS);
         String bRule=bodyMatcher1.group(BINDRULE);
         PermBindRulePair pair = PermBindRulePair.decode(perm, rights, bRule);
         permBindRulePairs.add(pair);
         lastIndex = bodyMatcher1.end();
        }

        if (lastIndex >= 0 && input.charAt(lastIndex) != ')')
        {
          Message message = WARN_ACI_SYNTAX_GENERAL_PARSE_FAILED.get(input);
          throw new AciException(message);
        }

        return new AciBody(version, name, startPos, permBindRulePairs);
    }

    /**
     * Checks all of the permissions in this body for a specific access type.
     * Need to walk down each permission-bind rule pair and call it's
     * hasAccessType method.
     *
     * @param accessType The access type enumeration to search for.
     * @return True if the access type is found in a permission of
     * a permission bind rule pair.
     */
    public boolean hasAccessType(EnumAccessType accessType) {
        List<PermBindRulePair>pairs=getPermBindRulePairs();
         for(PermBindRulePair p : pairs) {
             if(p.hasAccessType(accessType))
                 return true;
         }
         return false;
    }

    /**
     * Search through each permission bind rule associated with this body and
     * try and match a single right of the specified rights.
     *
     * @param rights The rights that are used in the match.
     * @return True if a one or more right of the specified rights matches
     * a body's permission rights.
     */
    public boolean hasRights(int rights) {
        List<PermBindRulePair>pairs=getPermBindRulePairs();
        for(PermBindRulePair p : pairs) {
            if(p.hasRights(rights))
                return true;
        }
        return false;
    }

    /**
     * Retrieve the permission-bind rule pairs of this ACI body.
     *
     * @return The permission-bind rule pairs.
     */
    private List<PermBindRulePair> getPermBindRulePairs() {
        return permBindRulePairs;
    }

    /**
     * Get the start position in the ACI string of the ACI body.
     *
     * @return Index into the ACI string of the ACI body.
     */
    public int getMatcherStartPos() {
        return startPos;
    }

    /**
     * Performs an evaluation of the permission-bind rule pairs
     * using the evaluation context. The method walks down
     * each PermBindRulePair object and:
     *
     *  1. Skips a pair if the evaluation context rights don't
     *     apply to that ACI. For example, an LDAP search would skip
     *     an ACI pair that allows writes.
     *
     *  2. The pair's bind rule is evaluated using the evaluation context.
     *  3. The result of the evaluation is itself evaluated. See comments
     *     below in the code.
     *
     * @param evalCtx The evaluation context to evaluate against.
     * @return An enumeration result of the evaluation.
     */
    public  EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult res=EnumEvalResult.FALSE;
        List<PermBindRulePair>pairs=getPermBindRulePairs();
        for(PermBindRulePair p : pairs) {
            if(evalCtx.isDenyEval() &&
                    (p.hasAccessType(EnumAccessType.ALLOW)))
                continue;
            if(!p.hasRights(getEvalRights(evalCtx)))
                continue;
            res=p.getBindRule().evaluate(evalCtx);
            // The evaluation result could be FAIL. Stop processing and return
            //FAIL. Maybe an internal search failed.
            if((res != EnumEvalResult.TRUE) &&
                    (res != EnumEvalResult.FALSE)) {
                res=EnumEvalResult.FAIL;
                break;
                //If the access type is DENY and the pair evaluated to TRUE,
                //then stop processing and return TRUE. A deny pair
                //succeeded.
            } else if((p.hasAccessType(EnumAccessType.DENY)) &&
                    (res == EnumEvalResult.TRUE)) {
                res=EnumEvalResult.TRUE;
                break;
                //An allow access type evaluated TRUE, stop processing
                //and return TRUE.
            } else if((p.hasAccessType(EnumAccessType.ALLOW) &&
                    (res == EnumEvalResult.TRUE))) {
                res=EnumEvalResult.TRUE;
                break;
            }
        }
        return res;
    }

  /**
   * Returns the name string.
   * @return The name string.
   */
  public String getName() {
      return this.name;
    }


  /**
   * Mainly used because geteffectiverights adds flags to the rights that aren't
   * needed in the actual evaluation of the ACI. This routine returns only the
   * rights needed in the evaluation. The order does matter, ACI_SELF evaluation
   * needs to be before ACI_WRITE.
   *
   * @param evalCtx  The evaluation context to determine the rights of.
   * @return  The evaluation rights to used in the evaluation.
   */
  private int getEvalRights(AciEvalContext evalCtx) {
    if(evalCtx.hasRights(ACI_WRITE) &&
            evalCtx.hasRights(ACI_SELF))
      return ACI_SELF;
    else  if(evalCtx.hasRights(ACI_COMPARE))
      return ACI_COMPARE;
    else if(evalCtx.hasRights(ACI_SEARCH))
      return ACI_SEARCH;
    else if(evalCtx.hasRights(ACI_READ))
      return ACI_READ;
    else if(evalCtx.hasRights(ACI_DELETE))
      return ACI_DELETE;
    else if(evalCtx.hasRights(ACI_ADD))
      return ACI_ADD;
    else if(evalCtx.hasRights(ACI_WRITE))
      return ACI_WRITE;
    else if(evalCtx.hasRights(ACI_PROXY))
      return ACI_PROXY;
    else if(evalCtx.hasRights(ACI_IMPORT))
      return ACI_IMPORT;
    else if(evalCtx.hasRights(ACI_EXPORT))
      return ACI_EXPORT;
    return ACI_NULL;
  }

  /**
   * Return version string of the ACI.
   *
   * @return The ACI version string.
   */
  public String getVersion () {
    return version;
  }
}

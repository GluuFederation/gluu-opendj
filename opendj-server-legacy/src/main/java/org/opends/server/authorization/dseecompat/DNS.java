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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * This class implements the dns bind rule keyword.
 */
public class DNS implements KeywordBindRule {
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** List of patterns to match against. */
    private List<String> patterns;

    /** The enumeration representing the bind rule type of the DNS rule. */
    private EnumBindRuleType type;

    /** Regular expression group used to match a dns rule. */
    private static final String valueRegex = "([a-zA-Z0-9\\.\\-\\*]+)";

    /** Regular expression group used to match one or more DNS values. */
    private static final String valuesRegExGroup =
            valueRegex + ZERO_OR_MORE_WHITESPACE +
            "(," +  ZERO_OR_MORE_WHITESPACE  +  valueRegex  +  ")*";

    /**
     * Create a class representing a dns bind rule keyword.
     * @param patterns List of dns patterns to match against.
     * @param type An enumeration representing the bind rule type.
     */
    DNS(List<String> patterns, EnumBindRuleType type) {
        this.patterns=patterns;
        this.type=type;
    }

    /**
     * Decode an string representing a dns bind rule.
     * @param expr A string representation of the bind rule.
     * @param type  An enumeration representing the bind rule type.
     * @return  A keyword bind rule class that can be used to evaluate
     * this bind rule.
     * @throws AciException  If the expression string is invalid.
     */
    public static DNS decode(String expr,  EnumBindRuleType type)
    throws AciException
    {
        if (!Pattern.matches(valuesRegExGroup, expr)) {
            LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_DNS_EXPRESSION.get(expr);
            throw new AciException(message);
        }
        List<String> dns = new LinkedList<>();
        int valuePos = 1;
        Pattern valuePattern = Pattern.compile(valueRegex);
        Matcher valueMatcher = valuePattern.matcher(expr);
        while (valueMatcher.find()) {
            String hn=valueMatcher.group(valuePos);
            String[] hnArray=hn.split("\\.", -1);
            for(int i=1, n=hnArray.length; i < n; i++) {
                if(hnArray[i].equals("*")) {
                    LocalizableMessage message =
                        WARN_ACI_SYNTAX_INVALID_DNS_WILDCARD.get(expr);
                    throw new AciException(message);
                }
            }

            // If the provided hostname does not contain any wildcard
            // characters, then it must be the canonical hostname for the
            // associated IP address.  If it is not, then it will not match the
            // intended target, and we should generate a warning message to let
            // the administrator know about it.  If the provided value does not
            // match the canonical name for the associated IP address, and the
            // given hostname is "localhost", then we should treat it specially
            // and also match the canonical hostname.  This is necessary because
            // "localhost" is likely to be very commonly used in these kinds of
            // rules and on some systems the canonical representation is
            // configured to be "localhost.localdomain" which may not be known
            // to the administrator.
            if (!hn.contains("*"))
            {
              try
              {
                for (InetAddress addr : InetAddress.getAllByName(hn))
                {
                  String canonicalName = addr.getCanonicalHostName();
                  if (! hn.equalsIgnoreCase(canonicalName))
                  {
                    if (hn.equalsIgnoreCase("localhost")
                        && !dns.contains(canonicalName))
                    {
                      dns.add(canonicalName);

                      logger.warn(WARN_ACI_LOCALHOST_DOESNT_MATCH_CANONICAL_VALUE, expr, hn, canonicalName);
                    }
                    else
                    {
                      logger.warn(WARN_ACI_HOSTNAME_DOESNT_MATCH_CANONICAL_VALUE, expr, hn, addr.getHostAddress(),
                                addr.getCanonicalHostName());
                    }
                  }
                }
              }
              catch (Exception e)
              {
                logger.traceException(e);

                logger.warn(WARN_ACI_ERROR_CHECKING_CANONICAL_HOSTNAME, hn, expr, getExceptionMessage(e));
              }
            }

            dns.add(hn);
        }
        return new DNS(dns, type);
    }

    /**
     * Performs evaluation of dns keyword bind rule using the provided
     * evaluation context.
     * @param evalCtx  An evaluation context to use in the evaluation.
     * @return An enumeration evaluation result.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        String[] remoteHost = evalCtx.getHostName().split("\\.", -1);
        for(String p : patterns) {
          String[] pat = p.split("\\.", -1);
          if(evalHostName(remoteHost, pat)) {
              matched=EnumEvalResult.TRUE;
              break;
          }
        }
        return matched.getRet(type, false);
    }

    /**
     * Checks an array containing the remote client's hostname against
     * patterns specified in the bind rule expression. Wild-cards are
     * only permitted in the leftmost field and the rest of the domain
     * name array components must match. A single wild-card matches any
     * hostname.
     * @param remoteHostName  Array containing components of the remote clients
     * hostname (split on ".").
     * @param pat  An array containing the pattern specified in
     * the bind rule expression. The first array slot may be a wild-card "*".
     * @return  True if the remote hostname matches the pattern.
     */
    boolean evalHostName(String[] remoteHostName, String[] pat) {
      boolean wildCard=pat[0].equals("*");
      //Check if there is a single wild-card.
      if(pat.length == 1 && wildCard) {
        return true;
      }
      int remoteHnIndex=remoteHostName.length-pat.length;
      if(remoteHnIndex < 0) {
        return false;
      }
      int patternIndex=0;
      if(!wildCard) {
        remoteHnIndex=0;
      } else {
          patternIndex=1;
          remoteHnIndex++;
      }
      for(int i=remoteHnIndex ;i<remoteHostName.length;i++) {
        if(!pat[patternIndex++].equalsIgnoreCase(remoteHostName[i])) {
          return false;
        }
      }
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public final void toString(StringBuilder buffer) {
        buffer.append(super.toString());
    }

}

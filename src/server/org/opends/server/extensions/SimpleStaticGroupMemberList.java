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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.util.Iterator;
import java.util.ArrayList;
import java.util.Set;

import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.Validator.*;



/**
 * This class provides an implementation of the {@code MemberList} class that
 * may be used in conjunction when static groups when no additional criteria is
 * to be used to select a subset of the group members.
 */
public class SimpleStaticGroupMemberList
       extends MemberList
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The DN of the static group with which this member list is associated.
  private DN groupDN;

  // The iterator used to traverse the set of member DNs.
  private Iterator<ByteString> memberDNIterator;

  // The set of DNs for the users that are members of the associated static
  // group.
  private ArrayList<ByteString> memberDNs;



  /**
   * Creates a new simple static group member list with the provided set of
   * member DNs.
   *
   * @param  groupDN    The DN of the static group with which this member list
   *                    is associated.
   * @param  memberDNs  The set of DNs for the users that are members of the
   *                    associated static group.
   */
  public SimpleStaticGroupMemberList(DN groupDN, Set<ByteString> memberDNs)
  {
    ensureNotNull(groupDN, memberDNs);

    this.groupDN   = groupDN;
    this.memberDNs = new ArrayList<ByteString>(memberDNs);
    memberDNIterator = memberDNs.iterator();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasMoreMembers()
  {
    return memberDNIterator.hasNext();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN nextMemberDN()
         throws MembershipException
  {
    DN dn = null;

    if (memberDNIterator.hasNext())
    {
      try{
        dn = DN.decode(memberDNIterator.next());
      }
      catch (DirectoryException de)
      {
        // Should not happen
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
        Message message = ERR_STATICMEMBERS_CANNOT_DECODE_DN.
            get(String.valueOf(dn), String.valueOf(groupDN),
                String.valueOf(de.getMessageObject()));
        throw new MembershipException(message, true, de);
      }
    }

    return dn;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry nextMemberEntry()
         throws MembershipException
  {
    if (memberDNIterator.hasNext())
    {
      ByteString memberDN = memberDNIterator.next();

      try
      {
        Entry memberEntry = DirectoryConfig.getEntry(DN.decode(memberDN));
        if (memberEntry == null)
        {
          Message message = ERR_STATICMEMBERS_NO_SUCH_ENTRY.get(
              String.valueOf(memberDN), String.valueOf(groupDN));
          throw new MembershipException(message, true);
        }

        return memberEntry;
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        Message message = ERR_STATICMEMBERS_CANNOT_GET_ENTRY.
            get(String.valueOf(memberDN), String.valueOf(groupDN),
                String.valueOf(de.getMessageObject()));
        throw new MembershipException(message, true, de);
      }
    }

    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void close()
  {
    // No implementation is required.
  }
}


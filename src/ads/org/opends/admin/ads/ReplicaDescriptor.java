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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock, AS.
 */

package org.opends.admin.ads;

import java.util.HashSet;
import java.util.Set;


/**
 * The object of this class represent a Replica (i.e. a suffix in a given
 * server).
 */
public class ReplicaDescriptor
{
  private SuffixDescriptor suffix;
  private int entries = -1;
  private ServerDescriptor server;
  private Set<String> replicationServers = new HashSet<String>();
  private int replicationId = -1;
  private int missingChanges = -1;
  private long ageOfOldestMissingChange = -1;
  private String backendName;

  /**
   * Returns the number of entries contained in the replica.
   * @return the number of entries contained in the replica.
   */
  public int getEntries()
  {
    return entries;
  }

  /**
   * Returns whether this replica is replicated or not.
   * @return <CODE>true</CODE> if the replica is replicated and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isReplicated()
  {
    return replicationId != -1;
  }

  /**
   * Returns whether replication is replicated on this server or not.
   * @return <CODE>true</CODE> if replication is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isReplicationEnabled()
  {
    return server.isReplicationEnabled();
  }

  /**
   * Sets the number of entries contained in the replica.
   * @param entries the number of entries contained in the replica.
   */
  public void setEntries(int entries)
  {
    this.entries = entries;
  }

  /**
   * Returns the ServerDescriptor object associated with the server where this
   * replica is located.
   * @return the ServerDescriptor object associated with the server where this
   * replica is located.
   */
  public ServerDescriptor getServer()
  {
    return server;
  }

  /**
   * Sets the server where this replica is located.
   * @param server the ServerDescriptor object associated with the server where
   * this replica is located.
   */
  public void setServer(ServerDescriptor server)
  {
    this.server = server;
  }

  /**
   * Returns the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   * @return the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   */
  public SuffixDescriptor getSuffix()
  {
    return suffix;
  }

  /**
   * Sets the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   * @param suffix the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   */
  public void setSuffix(SuffixDescriptor suffix)
  {
    this.suffix = suffix;
  }

  /**
   * Returns a set containing the String representation of the replication
   * servers that are defined in the replication domain for this replica.
   * @return a set containing the String representation of the replication
   * servers that are defined in the replication domain for this replica.
   */
  public Set<String> getReplicationServers()
  {
    HashSet<String> copy = new HashSet<String>();
    copy.addAll(replicationServers);
    return copy;
  }

  /**
   * Sets the list of replication servers (in their String representation) that
   * are defined in the replication domain for this replica.
   * @param replicationServers the list of replication servers (in their String
   * representation) that are defined in the replication domain for this
   * replica.
   */
  public void setReplicationServers(Set<String> replicationServers)
  {
    this.replicationServers.clear();
    this.replicationServers.addAll(replicationServers);
  }

  /**
   * Returns the replication id for the replication domain associated
   * with this replica.
   * @return the replication id for the replication domain associated
   * with this replica.
   */
  public int getReplicationId()
  {
    return replicationId;
  }

  /**
   * Sets the replication id for the replication domain associated
   * with this replica.
   * @param replicationId the replication id for the replication domain
   * associated with this replica.
   */
  public void setReplicationId(int replicationId)
  {
    this.replicationId = replicationId;
  }

  /**
   * Returns the age of the oldest missing change.
   * @return the age of the oldest missing change.
   */
  public long getAgeOfOldestMissingChange()
  {
    return ageOfOldestMissingChange;
  }

  /**
   * Sets the age of the oldest missing change.
   * @param ageOfOldestMissingChange the age of the oldest missing change.
   */
  public void setAgeOfOldestMissingChange(long ageOfOldestMissingChange)
  {
    this.ageOfOldestMissingChange = ageOfOldestMissingChange;
  }

  /**
   * Returns the number of missing changes.
   * @return the number of missing changes.
   */
  public int getMissingChanges()
  {
    return missingChanges;
  }

  /**
   * Sets the number of missing changes.
   * @param missingChanges the number of missing changes.
   */
  public void setMissingChanges(int missingChanges)
  {
    this.missingChanges = missingChanges;
  }

  /**
   * Returns the name of the backend where this replica is defined.
   * @return the name of the backend where this replica is defined.
   */
  public String getBackendName()
  {
    return backendName;
  }

  /**
   * Sets the name of the backend where this replica is defined.
   * @param backendName the name of the backend.
   */
  public void setBackendName(String backendName)
  {
    this.backendName = backendName;
  }
}

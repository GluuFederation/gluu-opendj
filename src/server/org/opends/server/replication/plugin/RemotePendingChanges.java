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
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.types.DN;
import org.opends.server.types.Operation;

/**
 *
 * This class is used to store the list of remote changes received
 * from a replication server and that are either currently being replayed
 * or that are waiting for being replayed.
 *
 * It is used to know when the ServerState must be updated and to compute
 * the dependencies between operations.
 *
 * One of this object is instantiated for each ReplicationDomain.
 *
 */
public final class RemotePendingChanges
{
  /**
   * A map used to store the pending changes.
   */
  private SortedMap<ChangeNumber, PendingChange> pendingChanges =
    new TreeMap<ChangeNumber, PendingChange>();

  /**
   * A sorted set containing the list of PendingChanges that have
   * not been replayed correctly because they are dependent on
   * another change to be completed.
   */
  private SortedSet<PendingChange> dependentChanges =
    new TreeSet<PendingChange>();

  /**
   * The ServerState that will be updated when LDAPUpdateMsg are fully replayed.
   */
  private ServerState state;

  /**
   * Creates a new RemotePendingChanges using the provided ServerState.
   *
   * @param state   The ServerState that will be updated when LDAPUpdateMsg
   *                have been fully replayed.
   */
  public RemotePendingChanges(ServerState state)
  {
    this.state = state;
  }

  /**
   * Returns the number of changes currently in this list.
   *
   * @return The number of changes currently in this list.
   */
  public synchronized int getQueueSize()
  {
    return pendingChanges.size();
  }

  /**
   * Add a new LDAPUpdateMsg that was received from the replication server
   * to the pendingList.
   *
   * @param update The LDAPUpdateMsg that was received from the replication
   *               server and that will be added to the pending list.
   */
  public synchronized void putRemoteUpdate(LDAPUpdateMsg update)
  {
    ChangeNumber changeNumber = update.getChangeNumber();
    pendingChanges.put(changeNumber, new PendingChange(changeNumber, null,
                                                        update));
  }

  /**
   * Mark an update message as committed.
   *
   * @param changeNumber The ChangeNumber of the update message that must be
   *                     set as committed.
   */
  public synchronized void commit(ChangeNumber changeNumber)
  {
    PendingChange curChange = pendingChanges.get(changeNumber);
    if (curChange == null)
    {
      throw new NoSuchElementException();
    }
    curChange.setCommitted(true);

    ChangeNumber firstChangeNumber = pendingChanges.firstKey();
    PendingChange firstChange = pendingChanges.get(firstChangeNumber);

    while ((firstChange != null) && firstChange.isCommitted())
    {
      state.update(firstChangeNumber);
      pendingChanges.remove(firstChangeNumber);

      if (pendingChanges.isEmpty())
      {
        firstChange = null;
      }
      else
      {
        firstChangeNumber = pendingChanges.firstKey();
        firstChange = pendingChanges.get(firstChangeNumber);
      }
    }
  }

  /**
   * Get the first update in the list that have some dependencies cleared.
   *
   * @return The LDAPUpdateMsg to be handled.
   */
  public synchronized LDAPUpdateMsg getNextUpdate()
  {
    /*
     * Parse the list of Update with dependencies and check if the dependencies
     * are now cleared until an Update without dependencies is found.
     */
    for (PendingChange change : dependentChanges)
    {
      if (change.dependenciesIsCovered(state))
      {
        dependentChanges.remove(change);
        return change.getMsg();
      }
    }
    return null;
  }

  /**
   * Mark the first pendingChange as dependent on the second PendingChange.
   * @param dependentChange The PendingChange that depend on the second
   *                        PendingChange.
   * @param pendingChange   The PendingChange on which the first PendingChange
   *                        is dependent.
   */
  private void addDependency(
      PendingChange dependentChange, PendingChange pendingChange)
  {
    dependentChange.addDependency(pendingChange.getChangeNumber());
    dependentChanges.add(dependentChange);
  }

  /**
   * Check if the given AddOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   * AddOperation depends on
   *
   * - DeleteOperation done on the same DN
   * - ModifyDnOperation with the same target DN as the ADD DN
   * - ModifyDnOperation with new DN equals to the ADD DN parent
   * - AddOperation done on the parent DN of the ADD DN
   *
   * @param op The AddOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(AddOperation op)
  {
    boolean hasDependencies = false;
    DN targetDn = op.getEntryDN();
    ChangeNumber changeNumber = OperationContext.getChangeNumber(op);
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        LDAPUpdateMsg pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            /*
             * Check is the operation to be run is a deleteOperation on the
             * same DN.
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            /*
             * Check if the operation to be run is an addOperation on a
             * parent of the current AddOperation.
             */
            if (pendingChange.getTargetDN().isAncestorOf(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            /*
             * Check if the operation to be run is ModifyDnOperation with
             * the same target DN as the ADD DN
             * or a ModifyDnOperation with new DN equals to the ADD DN parent
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
            else
            {
              ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingChange.getMsg();
              if (pendingModDn.newDNIsParent(targetDn))
              {
                hasDependencies = true;
                addDependency(change, pendingChange);
              }
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given ModifyOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * ModifyOperation depends on
   * - AddOperation done on the same DN
   *
   * @param op The ModifyOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(ModifyOperation op)
  {
    boolean hasDependencies = false;
    DN targetDn = op.getEntryDN();
    ChangeNumber changeNumber = OperationContext.getChangeNumber(op);
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        LDAPUpdateMsg pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof AddMsg)
          {
            /*
             * Check if the operation to be run is an addOperation on a
             * same DN.
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given ModifyDNMsg has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * Modify DN Operation depends on
   * - AddOperation done on the same DN as the target DN of the MODDN operation
   * - AddOperation done on the new parent of the MODDN  operation
   * - DeleteOperation done on the new DN of the MODDN operation
   * - ModifyDNOperation done from the new DN of the MODDN operation
   *
   * @param msg The ModifyDNMsg to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(ModifyDNMsg msg)
  {
    boolean hasDependencies = false;
    ChangeNumber changeNumber = msg.getChangeNumber();
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    DN targetDn = change.getTargetDN();


    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        LDAPUpdateMsg pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            // Check if the target of the Delete is the same
            // as the new DN of this ModifyDN
            if (msg.newDNIsEqual(pendingChange.getTargetDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            // Check if the Add Operation was done on the new parent of
            // the MODDN  operation
            if (msg.newParentIsEqual(pendingChange.getTargetDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
            // Check if the AddOperation was done on the same DN as the
            // target DN of the MODDN operation
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            // Check if the ModifyDNOperation was done from the new DN of
            // the MODDN operation
            if (msg.newDNIsEqual(pendingChange.getTargetDN()))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
      }
    }
    return hasDependencies;
  }

  /**
   * Check if the given DeleteOperation has some dependencies on any
   * currently running previous operation.
   * Update the dependency list in the associated PendingChange if
   * there are some dependencies.
   *
   * DeleteOperation depends on
   * - DeleteOperation done on children DN
   * - ModifyDnOperation with target DN that are children of the DEL DN
   * - AddOperation done on the same DN
   *
   *
   * @param op The DeleteOperation to be checked.
   *
   * @return A boolean indicating if this operation has some dependencies.
   */
  public synchronized boolean checkDependencies(DeleteOperation op)
  {
    boolean hasDependencies = false;
    DN targetDn = op.getEntryDN();
    ChangeNumber changeNumber = OperationContext.getChangeNumber(op);
    PendingChange change = pendingChanges.get(changeNumber);
    if (change == null)
      return false;

    for (PendingChange pendingChange : pendingChanges.values())
    {
      if (pendingChange.getChangeNumber().older(changeNumber))
      {
        LDAPUpdateMsg pendingMsg = pendingChange.getMsg();
        if (pendingMsg != null)
        {
          if (pendingMsg instanceof DeleteMsg)
          {
            /*
             * Check if the operation to be run is a deleteOperation on a
             * children of the current DeleteOperation.
             */
            if (pendingChange.getTargetDN().isDescendantOf(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof AddMsg)
          {
            /*
             * Check if the operation to be run is an addOperation on a
             * parent of the current DeleteOperation.
             */
            if (pendingChange.getTargetDN().equals(targetDn))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
          else if (pendingMsg instanceof ModifyDNMsg)
          {
            ModifyDNMsg pendingModDn = (ModifyDNMsg) pendingChange.getMsg();
            /*
             * Check if the operation to be run is an ModifyDNOperation
             * on a children of the current DeleteOperation
             */
            if ((pendingChange.getTargetDN().isDescendantOf(targetDn)) ||
                (pendingModDn.newDNIsParent(targetDn)))
            {
              hasDependencies = true;
              addDependency(change, pendingChange);
            }
          }
        }
      }
      else
      {
        // We reached an operation that is newer than the operation
        // for which we are doing the dependency check so it is
        // not possible to find another operation with some dependency.
        // break the loop to avoid going through the potentially large
        // list of pending changes.
        break;
      }
    }
    return hasDependencies;
  }

  /**
   * Check the dependencies of a given Operation/UpdateMsg.
   *
   * @param op   The Operation for which dependencies must be checked.
   * @param msg  The Message for which dependencies must be checked.
   * @return     A boolean indicating if an operation cannot be replayed
   *             because of dependencies.
   */
  public boolean checkDependencies(Operation op, LDAPUpdateMsg msg)
  {
    if (op instanceof ModifyOperation)
    {
      ModifyOperation newOp = (ModifyOperation) op;
      return checkDependencies(newOp);

    } else if (op instanceof DeleteOperation)
    {
      DeleteOperation newOp = (DeleteOperation) op;
      return checkDependencies(newOp);

    } else if (op instanceof AddOperation)
    {
      AddOperation newOp = (AddOperation) op;
      return checkDependencies(newOp);
    } else if (op instanceof ModifyDNOperationBasis)
    {
      ModifyDNMsg newMsg = (ModifyDNMsg) msg;
      return checkDependencies(newMsg);
    } else
    {
      return true;  // unknown type of operation ?!
    }
  }
}

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
 */

package org.opends.server.authorization.dseecompat;
import org.opends.messages.Message;

import org.opends.server.api.Backend;
import static org.opends.server.authorization.dseecompat.AciHandler.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.messages.AccessControlMessages.*;
import org.opends.server.api.DITCacheMap;
import org.opends.server.types.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The AciList class performs caching of the ACI attribute values
 * using the entry DN as the key.
 */
public class AciList {

  /*
   * A map containing all the ACIs.
   * We use the copy-on-write technique to avoid locking when reading.
   */
  private volatile DITCacheMap<List<Aci>> aciList =
       new DITCacheMap<List<Aci>>();

  /*
   * Lock to protect internal data structures.
   */
  private final ReentrantReadWriteLock lock =
          new ReentrantReadWriteLock();

  /*
  * The configuration DN used to compare against the global ACI entry DN.
  */
  private DN configDN;

  /**
   * Constructor to create an ACI list to cache ACI attribute types.
   * @param configDN The configuration entry DN.
   */
  public AciList(DN configDN) {
     this.configDN=configDN;
  }

  /**
   * Using the base DN, return a list of ACIs that are candidates for
   * evaluation by walking up from the base DN towards the root of the
   * DIT gathering ACIs on parents. Global ACIs use the NULL DN as the key
   * and are included in the candidate set only if they have no
   * "target" keyword rules, or if the target keyword rule matches for
   * the specified base DN.
   *
   * @param baseDN  The DN to check.
   * @return A list of candidate ACIs that might be applicable.
   */
  public LinkedList<Aci> getCandidateAcis(DN baseDN) {
    LinkedList<Aci> candidates = new LinkedList<Aci>();
    if(baseDN == null)
    {
      return candidates;
    }

    lock.readLock().lock();
    try
    {
      //Save the baseDN in case we need to evaluate a global ACI.
      DN entryDN=baseDN;
      while (baseDN != null) {
        List<Aci> acis = aciList.get(baseDN);
        if (acis != null) {
          //Check if there are global ACIs. Global ACI has a NULL DN.
          if (baseDN.isNullDN()) {
            for (Aci aci : acis) {
              AciTargets targets = aci.getTargets();
              //If there is a target, evaluate it to see if this ACI should
              //be included in the candidate set.
              if (targets != null) {
                boolean ret = AciTargets.isTargetApplicable(aci, targets,
                        entryDN);
                if (ret) {
                  candidates.add(aci);  //Add this ACI to the candidates.
                }
              }
            }
          } else {
            candidates.addAll(acis);
          }
        }
        if(baseDN.isNullDN()) {
          break;
        }
        DN parentDN=baseDN.getParent();
        if(parentDN == null) {
          baseDN=DN.nullDN();
        } else {
          baseDN=parentDN;
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return candidates;
  }

  /**
   * Add all the ACI from a set of entries to the ACI list. There is no need
   * to check for global ACIs since they are processe by the AciHandler at
   * startup using the addACi single entry method.
   * @param entries The set of entries containing the "aci" attribute values.
   * @param failedACIMsgs List that will hold error messages from ACI decode
   *                      exceptions.
   * @return The number of valid ACI attribute values added to the ACI list.
   */
  public int addAci(List<? extends Entry> entries,
                                 LinkedList<Message> failedACIMsgs)
  {
    int validAcis=0;

    lock.writeLock().lock();
    try
    {
      for (Entry entry : entries) {
        DN dn=entry.getDN();
        List<Attribute> attributeList =
             entry.getOperationalAttribute(AciHandler.aciType);
        validAcis += addAciAttributeList(aciList, dn, configDN,
                                         attributeList, failedACIMsgs);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }

    return validAcis;
  }

  /**
   * Add a set of ACIs to the ACI list. This is usually used a startup, when
   * global ACIs are processed.
   *
   * @param dn The DN to add the ACIs under.
   *
   * @param acis A set of ACIs to add to the ACI list.
   *
   */
  public void addAci(DN dn, SortedSet<Aci> acis) {
    lock.writeLock().lock();
    try
    {
      aciList.put(dn, new LinkedList<Aci>(acis));
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * Add all of an entry's ACI (global or regular) attribute values to the
   * ACI list.
   * @param entry The entry containing the ACI attributes.
   * @param hasAci True if the "aci" attribute type was seen in the entry.
   * @param hasGlobalAci True if the "ds-cfg-global-aci" attribute type was
   * seen in the entry.
   * @param failedACIMsgs List that will hold error messages from ACI decode
   *                      exceptions.
   * @return The number of valid ACI attribute values added to the ACI list.
   */
  public int addAci(Entry entry,  boolean hasAci,
                                 boolean hasGlobalAci,
                                 LinkedList<Message> failedACIMsgs) {
    int validAcis=0;

    lock.writeLock().lock();
    try
    {
      //Process global "ds-cfg-global-aci" attribute type. The oldentry
      //DN is checked to verify it is equal to the config DN. If not those
      //attributes are skipped.
      if(hasGlobalAci && entry.getDN().equals(configDN)) {
          List<Attribute> attributeList = entry.getAttribute(globalAciType);
          validAcis = addAciAttributeList(aciList, DN.nullDN(), configDN,
                                          attributeList, failedACIMsgs);
      }

      if(hasAci) {
          List<Attribute> attributeList = entry.getAttribute(aciType);
          validAcis += addAciAttributeList(aciList, entry.getDN(), configDN,
                                           attributeList, failedACIMsgs);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }

    return validAcis;
  }

  /**
   * Add an ACI's attribute type values to the ACI list. There is a chance that
   * an ACI will throw an exception if it has an invalid syntax. If that
   * happens a message will be logged and the ACI skipped.  A count is
   * returned of the number of valid ACIs added.
   * @param aciList The ACI list to which the ACI is to be added.
   * @param dn The DN to use as the key in the ACI list.
   * @param configDN The DN of the configuration entry used to configure the
   *                 ACI handler. Used if a global ACI has an decode exception.
   * @param attributeList List of attributes containing the ACI attribute
   * values.
   * @param failedACIMsgs List that will hold error messages from ACI decode
   *                      exceptions.
   * @return The number of valid attribute values added to the ACI list.
   */
  private static int addAciAttributeList(DITCacheMap<List<Aci>> aciList,
                                         DN dn, DN configDN,
                                         List<Attribute> attributeList,
                                         LinkedList<Message> failedACIMsgs) {

    if (attributeList == null) {
      return 0;
    }

    int validAcis=0;
    ArrayList<Aci> acis = new ArrayList<Aci>();
    for (Attribute attribute : attributeList) {
      for (AttributeValue value : attribute) {
        try {
          Aci aci= Aci.decode(value.getValue(),dn);
          acis.add(aci);
          validAcis++;
        } catch (AciException ex) {
          DN msgDN=dn;
          if(dn == DN.nullDN()) {
            msgDN=configDN;
          }
          Message message = WARN_ACI_ADD_LIST_FAILED_DECODE.get(
                  value.getValue().toString(),
                  String.valueOf(msgDN),
                  ex.getMessage());
          failedACIMsgs.add(message);
        }
      }
    }
    addAci(aciList, dn, acis);
    return validAcis;
  }

  /**
   * Remove all of the ACIs related to the old entry and then add all of the
   * ACIs related to the new entry. This method locks/unlocks the list.
   * In the case of global ACIs the DN of the entry is checked to make sure it
   * is equal to the config DN. If not, the global ACI attribute type is
   * silently skipped.
   * @param oldEntry The old entry possibly containing old ACI attribute
   * values.
   * @param newEntry The new entry possibly containing new ACI attribute
   * values.
   * @param hasAci True if the "aci" attribute type was seen in the entry.
   * @param hasGlobalAci True if the "ds-cfg-global-aci" attribute type was
   * seen in the entry.
   */
  public void modAciOldNewEntry(Entry oldEntry, Entry newEntry,
                                             boolean hasAci,
                                             boolean hasGlobalAci) {

    lock.writeLock().lock();
    try
    {
      LinkedList<Message>failedACIMsgs=new LinkedList<Message>();
      //Process "aci" attribute types.
      if(hasAci) {
          aciList.remove(oldEntry.getDN());
          List<Attribute> attributeList =
                  newEntry.getOperationalAttribute(aciType);
          addAciAttributeList(aciList,newEntry.getDN(), configDN,
                              attributeList, failedACIMsgs);
      }
      //Process global "ds-cfg-global-aci" attribute type. The oldentry
      //DN is checked to verify it is equal to the config DN. If not those
      //attributes are skipped.
      if(hasGlobalAci && oldEntry.getDN().equals(configDN)) {
          aciList.remove(DN.nullDN());
          List<Attribute> attributeList =
                  newEntry.getAttribute(globalAciType);
          addAciAttributeList(aciList, DN.nullDN(), configDN,
                              attributeList, failedACIMsgs);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * Add ACI using the DN as a key. If the DN already
   * has ACI(s) on the list, then the new ACI is added to the
   * end of the array.
   * @param aciList The set of ACIs to which ACI is to be added.
   * @param dn The DN to use as the key.
   * @param acis The ACI to be added.
   */
  private static void addAci(DITCacheMap<List<Aci>> aciList, DN dn,
                             List<Aci> acis)
  {
    if(aciList.containsKey(dn)) {
      List<Aci> tmpAci = aciList.get(dn);
      tmpAci.addAll(acis);
    } else {
      aciList.put(dn, acis);
    }
  }

  /**
   * Remove global and regular ACIs from the list. It's possible that an entry
   * could have both attribute types (aci and ds-cfg-global-aci). Global ACIs
   * use the NULL DN for the key.  In the case of global ACIs the DN of the
   * entry is checked to make sure it is equal to the config DN. If not, the
   * global ACI attribute type is silently skipped.
   * @param entry The entry containing the global ACIs.
   * @param hasAci True if the "aci" attribute type was seen in the entry.
   * @param hasGlobalAci True if the "ds-cfg-global-aci" attribute type was
   * seen in the entry.
   * @return  True if the ACI set was deleted.
   */
  public boolean removeAci(Entry entry,  boolean hasAci,
                                                      boolean hasGlobalAci) {
    DN entryDN = entry.getDN();

    lock.writeLock().lock();
    try
    {
      if (hasGlobalAci && entryDN.equals(configDN) &&
          aciList.remove(DN.nullDN()) == null)
      {
        return false;
      }
      if (hasAci && aciList.remove(entryDN) == null)
      {
        return false;
      }
      if (!hasGlobalAci && !hasAci)
      {
        return aciList.removeSubtree(entryDN, null);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }

    return true;
  }

  /**
   * Remove all ACIs related to a backend.
   * @param backend  The backend to check if each DN is handled by that
   * backend.
   */
  public void removeAci(Backend backend) {

    lock.writeLock().lock();
    try
    {
      Iterator<Map.Entry<DN,List<Aci>>> iterator =
              aciList.entrySet().iterator();
      while (iterator.hasNext())
      {
        Map.Entry<DN,List<Aci>> mapEntry = iterator.next();
        if (backend.handlesEntry(mapEntry.getKey()))
        {
          iterator.remove();
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * Rename all ACIs under the specified old DN to the new DN. A simple
   * interation over the entire list is performed.
   * @param oldDN The DN of the original entry that was moved.
   * @param newDN The DN of the new entry.
   */
  public void renameAci(DN oldDN, DN newDN ) {

    int oldRDNCount=oldDN.getNumComponents();
    int newRDNCount=newDN.getNumComponents();

    lock.writeLock().lock();
    try
    {
      Map<DN,List<Aci>> tempAciList = new HashMap<DN,List<Aci>>();
      Iterator<Map.Entry<DN,List<Aci>>> iterator =
              aciList.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<DN,List<Aci>> hashEntry = iterator.next();
        if(hashEntry.getKey().isDescendantOf(oldDN)) {
          int keyRDNCount=hashEntry.getKey().getNumComponents();
          int keepRDNCount=keyRDNCount - oldRDNCount;
          RDN[] newRDNs = new RDN[keepRDNCount + newRDNCount];
          for (int i=0; i < keepRDNCount; i++) {
            newRDNs[i] = hashEntry.getKey().getRDN(i);
          }
          for (int i=keepRDNCount, j=0; j < newRDNCount; i++,j++) {
            newRDNs[i] = newDN.getRDN(j);
          }
          DN relocateDN=new DN(newRDNs);
          List<Aci> acis = new LinkedList<Aci>();
          for(Aci aci : hashEntry.getValue()) {
            try {
               Aci newAci =
                 Aci.decode(ByteString.valueOf(aci.toString()), relocateDN);
               acis.add(newAci);
            } catch (AciException ex) {
              //This should never happen since only a copy of the
              //ACI with a new DN is being made. Log a message if it does and
              //keep going.
              Message message = WARN_ACI_ADD_LIST_FAILED_DECODE.get(
                  aci.toString(), String.valueOf(relocateDN), ex.getMessage());
              logError(message);
            }
          }
          tempAciList.put(relocateDN, acis);
          iterator.remove();
        }
      }
      aciList.putAll(tempAciList);
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }
}

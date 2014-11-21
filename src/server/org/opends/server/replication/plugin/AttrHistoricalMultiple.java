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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

/**
 * This class is used to store historical information for multiple valued
 * attributes.
 * One object of this type is created for each attribute that was changed in
 * the entry.
 * It allows to record the last time a given value was added, the last
 * time a given value was deleted and the last time the whole attribute was
 * deleted.
 */
public class AttrHistoricalMultiple extends AttrHistorical
{
  /** Last time when the attribute was deleted. */
  private ChangeNumber deleteTime;
  /** Last time the attribute was modified. */
  private ChangeNumber lastUpdateTime;
  private final Map<AttrValueHistorical,AttrValueHistorical> valuesHist;

   /**
    * Create a new object from the provided informations.
    * @param deleteTime the last time this attribute was deleted
    * @param updateTime the last time this attribute was updated
    * @param valuesHist the new attribute values when updated.
    */
   public AttrHistoricalMultiple(ChangeNumber deleteTime,
       ChangeNumber updateTime,
       Map<AttrValueHistorical,AttrValueHistorical> valuesHist)
   {
     this.deleteTime = deleteTime;
     this.lastUpdateTime = updateTime;
     if (valuesHist == null)
       this.valuesHist =
         new LinkedHashMap<AttrValueHistorical,AttrValueHistorical>();
     else
       this.valuesHist = valuesHist;
   }

   /**
    * Create a new object.
    */
   public AttrHistoricalMultiple()
   {
     this.deleteTime = null;
     this.lastUpdateTime = null;
     this.valuesHist =
         new LinkedHashMap<AttrValueHistorical,AttrValueHistorical>();
   }

   /**
    * Returns the last time when the attribute was updated.
    * @return the last time when the attribute was updated
    */
   private ChangeNumber getLastUpdateTime()
   {
     return lastUpdateTime;
   }

   /**
    * Returns the last time when the attribute was deleted.
    * @return the last time when the attribute was deleted
    */
   @Override
   public ChangeNumber getDeleteTime()
   {
     return deleteTime;
   }

   /**
    * Duplicate an object.
    * ChangeNumber are duplicated by references
    * @return the duplicated object.
    *
    * Method only called in tests
    */
   AttrHistoricalMultiple duplicate()
   {
     return new AttrHistoricalMultiple(this.deleteTime, this.lastUpdateTime,
         this.valuesHist);
   }

   /**
    * Delete all historical information that is older than
    * the provided ChangeNumber for this attribute type.
    * Add the delete attribute state information
    * @param CN time when the delete was done
    */
   protected void delete(ChangeNumber CN)
   {
     // iterate through the values in the valuesInfo
     // and suppress all the values that have not been added
     // after the date of this delete.
     Iterator<AttrValueHistorical> it = valuesHist.keySet().iterator();
     while (it.hasNext())
     {
       AttrValueHistorical info = it.next();
       if (CN.newerOrEquals(info.getValueUpdateTime()) &&
           CN.newerOrEquals(info.getValueDeleteTime()))
         it.remove();
     }

     if (CN.newer(deleteTime))
     {
       deleteTime = CN;
     }

     if (CN.newer(lastUpdateTime))
     {
       lastUpdateTime = CN;
     }
   }

   /**
    * Update the historical of this attribute after a delete value.
    *
    * @param val value that was deleted
    * @param CN time when the delete was done
    */
   protected void delete(AttributeValue val, ChangeNumber CN)
   {
     AttrValueHistorical info = new AttrValueHistorical(val, null, CN);
     valuesHist.remove(info);
     valuesHist.put(info, info);
     if (CN.newer(lastUpdateTime))
     {
       lastUpdateTime = CN;
     }
   }

   /**
     * Update the historical of this attribute after deleting a set of values.
     *
     * @param attr
     *          the attribute containing the set of values that were
     *          deleted
     * @param CN
     *          time when the delete was done
     */
  protected void delete(Attribute attr, ChangeNumber CN)
  {
    for (AttributeValue val : attr)
    {
      AttrValueHistorical info = new AttrValueHistorical(val, null, CN);
      valuesHist.remove(info);
      valuesHist.put(info, info);
      if (CN.newer(lastUpdateTime))
      {
        lastUpdateTime = CN;
      }
    }
  }

   /**
     * Update the historical information when a value is added.
     *
     * @param addedValue
     *          values that was added
     * @param CN
     *          time when the value was added
     */
   protected void add(AttributeValue addedValue, ChangeNumber CN)
   {
     AttrValueHistorical info = new AttrValueHistorical(addedValue, CN, null);
     valuesHist.remove(info);
     valuesHist.put(info, info);
     if (CN.newer(lastUpdateTime))
     {
       lastUpdateTime = CN;
     }
   }

   /**
     * Update the historical information when values are added.
     *
     * @param attr
     *          the attribute containing the set of added values
     * @param CN
     *          time when the add is done
     */
  private void add(Attribute attr, ChangeNumber CN)
  {
    for (AttributeValue val : attr)
    {
      AttrValueHistorical info = new AttrValueHistorical(val, CN, null);
      valuesHist.remove(info);
      valuesHist.put(info, info);
      if (CN.newer(lastUpdateTime))
      {
        lastUpdateTime = CN;
      }
    }
  }

  /**
   * Get the list of historical informations for the values.
   *
   * @return the list of historical informations for the values.
   */
  @Override
  public Map<AttrValueHistorical,AttrValueHistorical> getValuesHistorical()
  {
    return valuesHist;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean replayOperation(
      Iterator<Modification> modsIterator, ChangeNumber changeNumber,
      Entry modifiedEntry, Modification m)
  {
    // We are replaying an operation that was already done
    // on another master server and this operation has a potential
    // conflict with some more recent operations on this same entry
    // we need to take the more complex path to solve them
    if ((ChangeNumber.compare(changeNumber, getLastUpdateTime()) < 0) ||
        (m.getModificationType() != ModificationType.REPLACE))
    {
      // the attribute was modified after this change -> conflict

      switch (m.getModificationType())
      {
      case DELETE:
        if (changeNumber.older(getDeleteTime()))
        {
          /* this delete is already obsoleted by a more recent delete
           * skip this mod
           */
          modsIterator.remove();
          break;
        }

        if (!conflictDelete(changeNumber, m, modifiedEntry))
        {
          modsIterator.remove();
        }
        break;

      case ADD:
        conflictAdd(changeNumber, m, modsIterator);
        break;

      case REPLACE:
        if (changeNumber.older(getDeleteTime()))
        {
          /* this replace is already obsoleted by a more recent delete
           * skip this mod
           */
          modsIterator.remove();
          break;
        }

        /* save the values that are added by the replace operation
         * into addedValues
         * first process the replace as a delete operation -> this generate
         * a list of values that should be kept
         * then process the addedValues as if they were coming from a add
         * -> this generate the list of values that needs to be added
         * concatenate the 2 generated lists into a replace
         */
        Attribute addedValues = m.getAttribute();
        m.setAttribute(new AttributeBuilder(addedValues, true).toAttribute());

        conflictDelete(changeNumber, m, modifiedEntry);
        Attribute keptValues = m.getAttribute();

        m.setAttribute(addedValues);
        conflictAdd(changeNumber, m, modsIterator);

        AttributeBuilder builder = new AttributeBuilder(keptValues);
        builder.addAll(m.getAttribute());
        m.setAttribute(builder.toAttribute());
        break;

      case INCREMENT:
        // TODO : FILL ME
        break;
      }
      return true;
    }
    else
    {
      processLocalOrNonConflictModification(changeNumber, m);
      return false;// the attribute was not modified more recently
    }
  }

  /**
   * This method calculate the historical information and update the hist
   * attribute to store the historical information for modify operation that
   * does not conflict with previous operation.
   * This is the usual path and should therefore be optimized.
   *
   * It does not check if the operation to process is conflicting or not with
   * previous operations. The caller is responsible for this.
   *
   * @param changeNumber The changeNumber of the operation to process
   * @param mod The modify operation to process.
   */
  @Override
  public void processLocalOrNonConflictModification(ChangeNumber changeNumber,
      Modification mod)
  {
    /*
     * The operation is either a non-conflicting operation or a local
     * operation so there is no need to check the historical information
     * for conflicts.
     * If this is a local operation, then this code is run after
     * the pre-operation phase.
     * If this is a non-conflicting replicated operation, this code is run
     * during the handleConflictResolution().
     */

    Attribute modAttr = mod.getAttribute();
    AttributeType type = modAttr.getAttributeType();

    switch (mod.getModificationType())
    {
    case DELETE:
      if (modAttr.isEmpty())
      {
        delete(changeNumber);
      }
      else
      {
        delete(modAttr, changeNumber);
      }
      break;

    case ADD:
      if (type.isSingleValue())
      {
        delete(changeNumber);
      }
      add(modAttr, changeNumber);
      break;

    case REPLACE:
      /* TODO : can we replace specific attribute values ????? */
      delete(changeNumber);
      add(modAttr, changeNumber);
      break;

    case INCREMENT:
      /* FIXME : we should update ChangeNumber */
      break;
    }
  }

  /**
   * Process a delete attribute values that is conflicting with a previous
   * modification.
   *
   * @param changeNumber The changeNumber of the currently processed change
   * @param m the modification that is being processed
   * @param modifiedEntry the entry that is modified (before current mod)
   * @return false if there is nothing to do
   */
  private boolean conflictDelete(ChangeNumber changeNumber, Modification m,
      Entry modifiedEntry)
  {
    /*
     * We are processing a conflicting DELETE modification
     *
     * This code is written on the assumption that conflict are
     * rare. We therefore don't care much about the performance
     * However since it is rarely executed this code needs to be
     * as simple as possible to make sure that all paths are tested.
     * In this case the most simple seem to change the DELETE
     * in a REPLACE modification that keeps all values
     * more recent that the DELETE.
     * we are therefore going to change m into a REPLACE that will keep
     * all the values that have been updated after the DELETE time
     * If a value is present in the entry without any state information
     * it must be removed so we simply ignore them
     */

    Attribute modAttr = m.getAttribute();
    if (modAttr.isEmpty())
    {
      /*
       * We are processing a DELETE attribute modification
       */
      m.setModificationType(ModificationType.REPLACE);
      AttributeBuilder builder = new AttributeBuilder(modAttr, true);

      Iterator<AttrValueHistorical> it = valuesHist.keySet().iterator();
      while (it.hasNext())
      {
        AttrValueHistorical valInfo; valInfo = it.next();

        if (changeNumber.older(valInfo.getValueUpdateTime()))
        {
          /*
           * this value has been updated after this delete, therefore
           * this value must be kept
           */
          builder.add(valInfo.getAttributeValue());
        }
        else
        {
          /*
           * this value is going to be deleted, remove it from historical
           * information unless it is a Deleted attribute value that is
           * more recent than this DELETE
           */
          if (changeNumber.newerOrEquals(valInfo.getValueDeleteTime()))
          {
            it.remove();
          }
        }
      }

      m.setAttribute(builder.toAttribute());

      if (changeNumber.newer(getDeleteTime()))
      {
        deleteTime = changeNumber;
      }
      if (changeNumber.newer(getLastUpdateTime()))
      {
        lastUpdateTime = changeNumber;
      }
    }
    else
    {
      // we are processing DELETE of some attribute values
      AttributeBuilder builder = new AttributeBuilder(modAttr);

      for (AttributeValue val : modAttr)
      {
        boolean deleteIt = true;  // true if the delete must be done
        boolean addedInCurrentOp = false;

        /* update historical information */
        AttrValueHistorical valInfo =
          new AttrValueHistorical(val, null, changeNumber);
        AttrValueHistorical oldValInfo = valuesHist.get(valInfo);
        if (oldValInfo != null)
        {
          /* this value already exist in the historical information */
          if (changeNumber.equals(oldValInfo.getValueUpdateTime()))
          {
            // This value was added earlier in the same operation
            // we need to keep the delete.
            addedInCurrentOp = true;
          }
          if (changeNumber.newerOrEquals(oldValInfo.getValueDeleteTime()) &&
              changeNumber.newerOrEquals(oldValInfo.getValueUpdateTime()))
          {
            valuesHist.remove(oldValInfo);
            valuesHist.put(valInfo, valInfo);
          }
          else if (oldValInfo.isUpdate())
          {
            deleteIt = false;
          }
        }
        else
        {
          valuesHist.remove(oldValInfo);
          valuesHist.put(valInfo, valInfo);
        }

        /* if the attribute value is not to be deleted
         * or if attribute value is not present suppress it from the
         * MOD to make sure the delete is going to succeed
         */
        if (!deleteIt
            || (!modifiedEntry.hasValue(modAttr.getAttributeType(), modAttr
                .getOptions(), val) && ! addedInCurrentOp))
        {
          // this value was already deleted before and therefore
          // this should not be replayed.
          builder.remove(val);
          if (builder.isEmpty())
          {
            // This was the last values in the set of values to be deleted.
            // this MOD must therefore be skipped.
            return false;
          }
        }
      }

      m.setAttribute(builder.toAttribute());

      if (changeNumber.newer(getLastUpdateTime()))
      {
        lastUpdateTime = changeNumber;
      }
    }

    return true;
  }

  /**
   * Process a add attribute values that is conflicting with a previous
   * modification.
   *
   * @param changeNumber  the historical info associated to the entry
   * @param m the modification that is being processed
   * @param modsIterator iterator on the list of modification
   * @return false if operation becomes empty and must not be processed
   */
  private boolean conflictAdd(ChangeNumber changeNumber, Modification m,
      Iterator<Modification> modsIterator)
  {
    /*
     * if historicalattributedelete is newer forget this mod else find
     * attr value if does not exist add historicalvalueadded timestamp
     * add real value in entry else if timestamp older and already was
     * historicalvalueadded update historicalvalueadded else if
     * timestamp older and was historicalvaluedeleted change
     * historicalvaluedeleted into historicalvalueadded add value in
     * real entry
     */

    if (changeNumber.older(getDeleteTime()))
    {
      /* A delete has been done more recently than this add
       * forget this MOD ADD
       */
      modsIterator.remove();
      return false;
    }

    AttributeBuilder builder = new AttributeBuilder(m.getAttribute());
    for (AttributeValue addVal : m.getAttribute())
    {
      AttrValueHistorical valInfo =
        new AttrValueHistorical(addVal, changeNumber, null);
      AttrValueHistorical oldValInfo = valuesHist.get(valInfo);
      if (oldValInfo == null)
      {
        /* this value does not exist yet
         * add it in the historical information
         * let the operation process normally
         */
        valuesHist.put(valInfo, valInfo);
      }
      else
      {
        if  (oldValInfo.isUpdate())
        {
          /* if the value is already present
           * check if the updateTime must be updated
           * in all cases suppress this value from the value list
           * as it is already present in the entry
           */
          if (changeNumber.newer(oldValInfo.getValueUpdateTime()))
          {
            valuesHist.remove(oldValInfo);
            valuesHist.put(valInfo, valInfo);
          }
          builder.remove(addVal);
        }
        else
        {
          /* this value is marked as a deleted value
           * check if this mod is more recent the this delete
           */
          if (changeNumber.newerOrEquals(oldValInfo.getValueDeleteTime()))
          {
            /* this add is more recent,
             * remove the old delete historical information
             * and add our more recent one
             * let the operation process
             */
            valuesHist.remove(oldValInfo);
            valuesHist.put(valInfo, valInfo);
          }
          else
          {
            /* the delete that is present in the historical information
             * is more recent so it must win,
             * remove this value from the list of values to add
             * don't update the historical information
             */
            builder.remove(addVal);
          }
        }
      }
    }

    Attribute attr = builder.toAttribute();
    m.setAttribute(attr);

    if (attr.isEmpty())
    {
      modsIterator.remove();
    }

    if (changeNumber.newer(getLastUpdateTime()))
    {
      lastUpdateTime = changeNumber;
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void assign(HistAttrModificationKey histKey, AttributeValue value,
      ChangeNumber cn)
  {
    switch (histKey)
    {
    case ADD:
      if (value != null)
      {
        add(value, cn);
      }
      break;

    case DEL:
      if (value != null)
      {
        delete(value, cn);
      }
      break;

    case REPL:
      delete(cn);
      if (value != null)
      {
        add(value, cn);
      }
      break;

    case DELATTR:
        delete(cn);
      break;
    }
  }
}



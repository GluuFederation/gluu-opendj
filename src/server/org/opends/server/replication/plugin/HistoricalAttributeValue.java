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
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.*;


/**
 * This class stores an internal usable representation of the value of
 * the historical related to an entry.
 * It encodes/decodes from the String form stored in the DB attribute
 * from/to the internal usable form.
 *
 *
 * an historical attribute value looks like :
 *  description:00000108b3a6554100000001:add:added_value
 *  or
 *  description:00000108b3a6cbb800000001:del:deleted_value
 *  or
 *  description:00000108b3a6cbb800000001:repl:new_value
 *  or
 *  description:00000108b3a6cbb800000001:delAttr
 *  or
 *  description:00000108b3a6554100000001:add
 *  or
 *  dn:00000108b3a6554100000001:add (ADD operation)
 *  or
 *  dn:00000108b3a6554100000001:moddn (MODIFYDN operation)
 *
 *  so after split
 *  token[0] will contain the attribute name
 *  token[1] will contain the change number
 *  token[2] will contain the type of historical information
 *  token[3] will contain the attribute value
 *
 *  options are stored with the attribute names using; as a separator
 *  example :
 *  description;FR;France:00000108b3a6554100000001:add:added_value
 *
 *
 */
public class HistoricalAttributeValue
{
  private AttributeType attrType;
  private String attrString;
  private AttributeValue attributeValue;
  private ChangeNumber cn;
  private LinkedHashSet<String> options;
  private HistAttrModificationKey histKey;
  private String stringValue;

  // This flag indicates that this value was generated to store the last date
  // when the entry was renamed.
  private boolean ismodDN = false;

  /**
   * Create a new object from the String encoded form.
   *
   * @param strVal The String encoded form of historical attribute value.
   */
  public HistoricalAttributeValue(String strVal)
  {
    String[] token = strVal.split(":", 4);

    options = new LinkedHashSet<String>();
    if (token[0].contains(";"))
    {
      String[] optionsToken = token[0].split(";");
      int index = 1;
      while (index < optionsToken.length)
      {
        options.add(optionsToken[index]);
        index ++;
      }
      attrString = optionsToken[0];
    }
    else
    {
      attrString = token[0];
    }

    if (attrString.compareTo("dn") != 0)
    {
      // This HistVal was used to store the date when some
       // modifications were done to the entries.
      attrType = DirectoryServer.getSchema().getAttributeType(attrString);
      if (attrType == null)
        attrType = DirectoryServer.getDefaultAttributeType(attrString);
    }
    else
    {
      // This HistVal is used to store the date when the entry
      // was added to the directory or when it was last renamed.
      attrType = null;
      if ((token.length >= 3) && (token[2].compareTo("moddn") == 0))
      {
        ismodDN = true;
      }
    }

    cn = new ChangeNumber(token[1]);
    histKey = HistAttrModificationKey.decodeKey(token[2]);
    stringValue = null;
    if (histKey != HistAttrModificationKey.DELATTR)
    {
      if (token.length == 4)
      {
        stringValue = token[3];
        attributeValue = AttributeValues.create(attrType, stringValue);
      }
      else
        attributeValue = null;
    }
    else
    {
      stringValue = null;
      attributeValue = null;
    }
  }

  /**
   * Get the String form of the attribute type.
   *
   * @return Returns the String form of the attribute type.
   */
  public String getAttrString()
  {
    return attrString;
  }

  /**
   * Get the type of this HistVal.
   *
   * @return Returns the type of this HistVal.
   *         Can return NULL if the HistVal was generated for a ADD Operation.
   */
  public AttributeType getAttrType()
  {
    return attrType;
  }

  /**
   * Get the ChangeNUmber of this HistVal.
   * @return Returns the ChangeNumber of this HistVal.
   */
  public ChangeNumber getCn()
  {
    return cn;
  }

  /**
   * Get the HistKey.
   * @return Returns the histKey.
   */
  public HistAttrModificationKey getHistKey()
  {
    return histKey;
  }

  /**
   * Get the options or an empty set if there are no options.
   * @return Returns the options.
   */
  public Set<String> getOptions()
  {
    return options;
  }

  /**
   * Get the Attribute Value.
   * @return The Attribute Value.
   */
  public AttributeValue getAttributeValue()
  {
    return attributeValue;
  }

  /**
   * Generate a Modification equivalent to this value of the historical
   * attribute.
   *
   * @return A modification equivalent to this value of the historical
   * attribute.
   */
  public Modification generateMod()
  {
    AttributeBuilder builder = new AttributeBuilder(attrType, attrString);
    builder.setOptions(options);

    if (histKey != HistAttrModificationKey.DELATTR)
    {
      builder.add(attributeValue);
    }
    Attribute attr = builder.toAttribute();

    Modification mod;
    switch (histKey)
    {
    case ADD:
      mod = new Modification(ModificationType.ADD, attr);
      break;
    case DEL:
      mod = new Modification(ModificationType.DELETE, attr);
      break;
    case REPL:
      mod = new Modification(ModificationType.REPLACE, attr);
      break;
    case DELATTR:
      mod = new Modification(ModificationType.DELETE, attr);
      break;
    default:
      mod = null;
    }
    return mod;
  }

  /**
   * Indicates if this value of the historical attribute was generated
   * for a ADD operation.
   *
   * @return a boolean indicating if this was generated for a ADD
   *         operation.
   */
  public boolean isADDOperation()
  {
    return attrType == null && !ismodDN;
  }

  /**
   * Indicates if this value of the historical attribute was generated
   * for a MODDN operation.
   *
   * @return a boolean indicating if this was generated for a ADDMODDN
   *         operation.
   */
  public boolean isMODDNOperation()
  {
    return attrType == null && ismodDN;
  }
}

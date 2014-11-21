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
 */
package org.opends.server.replication.plugin;

/**
 * Enumeration used for storing type of attribute modification
 * in the value of the replication historical information.
 *
 * Example of ds-sync-hist values:
 * ds-sync-hist: attrName1:changeNumber1:repl:newReplacingValue
 * ds-sync-hist: attrName1:changeNumber2:del:deletedValue
 * ds-sync-hist: attrName3:changeNumber3:add:newAddedvalue
 * ds-sync-hist: attrName3:changeNumber4:attrDel
 *
 */
public enum HistAttrModificationKey
{
  /**
   * The key for attribute value deletion.
   */
  DEL("del"),

  /**
   * The key for attribute deletion.
   */
  DELATTR("delAttr"),

  /**
   * The key for attribute replace.
   */
  REPL("repl"),

  /**
   * The key for attribute value addition.
   */
  ADD("add");

  // The string representation of this key.
  private String key;

  /**
   * Creates a new HistKey type with the provided key string.
   *
   * @param histkey The key string
   */
  private HistAttrModificationKey(String histkey)
  {
    this.key = histkey;
  }

  /**
   * Get a key from the String representation.
   *
   * @param histkey the String to decode
   * @return the key from the enum type
   */
  public static HistAttrModificationKey decodeKey(String histkey)
  {
     if (histkey == null)
       return null;

     if (histkey.compareTo("repl") == 0)
       return HistAttrModificationKey.REPL;

     if (histkey.compareTo("add") == 0)
       return HistAttrModificationKey.ADD;

     if (histkey.compareTo("del") == 0)
       return HistAttrModificationKey.DEL;

     if (histkey.compareTo("attrDel") == 0)
       return HistAttrModificationKey.DELATTR;

     return null;
  }

  /**
   * Retrieves the human-readable name for this HistKey.
   *
   * @return  The human-readable name for this HistKey.
   */
  public String getKey()
  {
    return key;
  }

  /**
   * Retrieves a string representation of this HistKey.
   *
   * @return  A string representation of this HistKey.
   */
  public String toString()
  {
    return key;
  }

}

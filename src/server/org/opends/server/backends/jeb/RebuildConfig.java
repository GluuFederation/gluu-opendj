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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import org.opends.server.types.DN;

import java.util.ArrayList;

/**
 * Configuration for the indexType rebuild process.
 */
public class RebuildConfig
{
  /**
   * Identifies how indexes will be selected for rebuild.
   */
  public static enum RebuildMode
  {
    /**
     * Rebuild all indexes, including system indexes.
     */
    ALL,

    /**
     * Rebuild all degraded indexes, including system indexes.
     */
    DEGRADED,

    /**
     * Rebuild used defined list of indexes.
     */
    USER_DEFINED;
  }

  /**
   * The base DN to rebuild.
   */
  private DN baseDN;

  /**
   * The names of indexes to rebuild.
   */
  private ArrayList<String> rebuildList;

  private RebuildMode rebuildMode = RebuildMode.USER_DEFINED;

  private String tmpDirectory;

  private boolean isClearDegradedState;

  /**
   * Create a new rebuild configuration.
   */
  public RebuildConfig()
  {
    rebuildList = new ArrayList<String>();
  }

  /**
   * Get the base DN to rebuild.
   *
   * @return The base DN to rebuild.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Set the base DN to rebuild.
   *
   * @param baseDN
   *          The base DN to rebuild.
   */
  public void setBaseDN(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  /**
   * Get the list of indexes to rebuild in this configuration.
   *
   * @return The list of indexes to rebuild.
   */
  public ArrayList<String> getRebuildList()
  {
    return rebuildList;
  }

  /**
   * Add an index to be rebuilt into the configuration. Duplicate index names
   * will be ignored. Adding an index that causes a mix of complete and partial
   * rebuild for the same attribute index in the configuration will remove the
   * partial and just keep the complete attribute index name. (ie. uid and
   * uid.presence).
   *
   * @param index
   *          The index to add.
   */
  public void addRebuildIndex(String index)
  {
    String[] newIndexParts = index.split("\\.");

    for (String s : new ArrayList<String>(rebuildList))
    {
      String[] existingIndexParts = s.split("\\.");
      if (existingIndexParts[0].equalsIgnoreCase(newIndexParts[0]))
      {
        if (newIndexParts.length == 1 && existingIndexParts.length == 1)
        {
          return;
        }
        else if (newIndexParts.length > 1 && existingIndexParts.length == 1)
        {
          return;
        }
        else if (newIndexParts.length == 1 && existingIndexParts.length > 1)
        {
          rebuildList.remove(s);
        }
        else if (newIndexParts[1].equalsIgnoreCase(existingIndexParts[1]))
        {
          return;
        }
      }
    }

    this.rebuildList.add(index);
  }

  /**
   * Check the given config for conflicts with this config. A conflict is
   * detected if both configs specify the same indexType/database to be rebuilt.
   *
   * @param config
   *          The rebuild config to check against.
   * @return the name of the indexType causing the conflict or null if no
   *         conflict is detected.
   */
  public String checkConflicts(RebuildConfig config)
  {
    //If they specify different base DNs, no conflicts can occur.
    if (this.baseDN.equals(config.baseDN))
    {
      for (String thisIndex : this.rebuildList)
      {
        for (String thatIndex : config.rebuildList)
        {
          String[] existingIndexParts = thisIndex.split("\\.");
          String[] newIndexParts = thatIndex.split("\\.");
          if (existingIndexParts[0].equalsIgnoreCase(newIndexParts[0]))
          {
            if (newIndexParts.length == 1 && existingIndexParts.length == 1)
            {
              return thatIndex;
            }
            else if (newIndexParts.length > 1 && existingIndexParts.length == 1)
            {
              return thatIndex;
            }
            else if (newIndexParts.length == 1 && existingIndexParts.length > 1)
            {
              return thatIndex;
            }
            else if (newIndexParts[1].equalsIgnoreCase(existingIndexParts[1]))
            {
              return thatIndex;
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * Test if this rebuild config includes any system indexes to rebuild.
   *
   * @return True if rebuilding of system indexes are included. False otherwise.
   * @throws InitializationException
   */
  public boolean includesSystemIndex()
  {
    for (String index : rebuildList)
    {
      // Removed because the id2entry is not A system indexes is THE
      // primary system index. It cannot be rebuilt.
      /*if (index.equalsIgnoreCase("id2entry"))
      {
        return true;
      }*/
      if (index.equalsIgnoreCase("dn2id"))
      {
        return true;
      }
      if (index.equalsIgnoreCase("dn2uri"))
      {
        return true;
      }
    }

    return false;
  }

  /**
   * Set the temporary directory to the specified path.
   *
   * @param path
   *          The path to set the temporary directory to.
   */
  public void setTmpDirectory(String path)
  {
    tmpDirectory = path;
  }

  /**
   * Return the temporary directory path.
   *
   * @return The temporary directory string.
   */
  public String getTmpDirectory()
  {
    return tmpDirectory;
  }

  /**
   * Sets the rebuild mode.
   *
   * @param mode
   *          The new rebuild mode.
   */
  public void setRebuildMode(RebuildMode mode)
  {
    rebuildMode = mode;
  }

  /**
   * Returns the rebuild mode.
   *
   * @return The rebuild mode.
   */
  public RebuildMode getRebuildMode()
  {
    return rebuildMode;
  }

  /**
   * Returns {@code true} if indexes should be forcefully marked as valid even
   * if they are currently degraded.
   *
   * @return {@code true} if index should be forcefully marked as valid.
   */
  public boolean isClearDegradedState()
  {
    return isClearDegradedState;
  }

  /**
   * Sets the 'clear degraded index' status.
   *
   * @param isClearDegradedState
   *          {@code true} if indexes should be forcefully marked as valid even
   *          if they are currently degraded.
   */
  public void isClearDegradedState(boolean isClearDegradedState)
  {
    this.isClearDegradedState = isClearDegradedState;
  }

}

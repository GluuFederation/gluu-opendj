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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.CollectionUtils.*;

/**
 * The abstract table model used to display all the network groups.
 */
public class DBEnvironmentMonitoringTableModel extends SortableTableModel
implements Comparator<BackendDescriptor>
{
  private static final long serialVersionUID = 548035716525600536L;
  private Set<BackendDescriptor> data = new HashSet<>();
  private ArrayList<String[]> dataArray = new ArrayList<>();
  private ArrayList<BackendDescriptor> dataSourceArray = new ArrayList<>();

  private String[] columnNames = {};
  private LocalizableMessage NO_VALUE_SET = INFO_CTRL_PANEL_NO_MONITORING_VALUE.get();
  private LocalizableMessage NOT_IMPLEMENTED = INFO_CTRL_PANEL_NOT_IMPLEMENTED.get();

  /** The operations to be displayed. */
  private LinkedHashSet<String> attributes = new LinkedHashSet<>();
  /** The sort column of the table. */
  private int sortColumn;
  /** Whether the sorting is ascending or descending. */
  private boolean sortAscending = true;

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   */
  public void setData(Set<BackendDescriptor> newData)
  {
    if (!newData.equals(data))
    {
      data.clear();
      data.addAll(newData);
      updateDataArray();
      fireTableDataChanged();
    }
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  @Override
  public void forceResort()
  {
    updateDataArray();
    fireTableDataChanged();
  }

  /**
   * Updates the table model contents, sorts its contents depending on the
   * sort options set by the user and updates the column structure.
   */
  public void forceDataStructureChange()
  {
    updateDataArray();
    fireTableStructureChanged();
    fireTableDataChanged();
  }

  /** {@inheritDoc} */
  @Override
  public int getColumnCount()
  {
    return columnNames.length;
  }

  /** {@inheritDoc} */
  @Override
  public int getRowCount()
  {
    return dataArray.size();
  }

  /** {@inheritDoc} */
  @Override
  public Object getValueAt(int row, int col)
  {
    return dataArray.get(row)[col];
  }

  /** {@inheritDoc} */
  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  /** {@inheritDoc} */
  @Override
  public int compare(BackendDescriptor desc1, BackendDescriptor desc2)
  {
    CustomSearchResult monitor1 = desc1.getMonitoringEntry();
    CustomSearchResult monitor2 = desc2.getMonitoringEntry();

    ArrayList<Integer> possibleResults = newArrayList(getName(desc1).compareTo(getName(desc2)));
    computeMonitoringPossibleResults(monitor1, monitor2, possibleResults, attributes);

    int result = possibleResults.get(getSortColumn());
    if (result == 0)
    {
      result = getFirstNonZero(possibleResults);
    }
    if (!isSortAscending())
    {
      result = -result;
    }
    return result;
  }

  private int getFirstNonZero(ArrayList<Integer> possibleResults)
  {
    for (int i : possibleResults)
    {
      if (i != 0)
      {
        return i;
      }
    }
    return 0;
  }

  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  @Override
  public boolean isSortAscending()
  {
    return sortAscending;
  }

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  @Override
  public void setSortAscending(boolean sortAscending)
  {
    this.sortAscending = sortAscending;
  }

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  @Override
  public int getSortColumn()
  {
    return sortColumn;
  }

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  @Override
  public void setSortColumn(int sortColumn)
  {
    this.sortColumn = sortColumn;
  }

  /**
   * Returns the operations displayed by this table model.
   * @return the operations displayed by this table model.
   */
  public Collection<String> getAttributes()
  {
    return attributes;
  }

  /**
   * Sets the operations displayed by this table model.
   * @param operations the operations displayed by this table model.
   */
  public void setAttributes(LinkedHashSet<String> operations)
  {
    this.attributes.clear();
    this.attributes.addAll(operations);
    columnNames = new String[operations.size() + 1];
    columnNames[0] = INFO_CTRL_PANEL_DB_HEADER.get().toString();
    int i = 1;
    for (String operation : operations)
    {
      columnNames[i] = operation;
      i++;
    }
  }

  /**
   * Updates the array data.  This includes resorting it.
   */
  private void updateDataArray()
  {
    TreeSet<BackendDescriptor> sortedSet = new TreeSet<>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    dataSourceArray.clear();
    for (BackendDescriptor ach : sortedSet)
    {
      String[] s = getLine(ach);
      dataArray.add(s);
      dataSourceArray.add(ach);
    }

    // Add the total: always at the end

    String[] line = new String[attributes.size() + 1];
    line[0] = "<html><b>" + INFO_CTRL_PANEL_TOTAL_LABEL.get() + "</b>";
    for (int i=1; i<line.length; i++)
    {
      boolean valueSet = false;
      boolean notImplemented = false;
      long totalValue = 0;
      for (int j=0; j<dataArray.size(); j++)
      {
        String[] l = dataArray.get(j);
        String value = l[i];
        try
        {
          long v = Long.parseLong(value);
          totalValue += v;
          valueSet = true;
        }
        catch (Throwable t)
        {
          try
          {
            double v = Double.parseDouble(value);
            totalValue += v;
            valueSet = true;
          }
          catch (Throwable t2)
          {
            notImplemented = NOT_IMPLEMENTED.toString().equals(value);
          }
        }
      }
      if (notImplemented)
      {
        line[i] = NOT_IMPLEMENTED.toString();
      }
      else if (valueSet)
      {
        line[i] = String.valueOf(totalValue);
      }
      else
      {
        line[i] = NO_VALUE_SET.toString();
      }
    }
    dataArray.add(line);
  }

  /**
   * Returns the label to be used for the provided backend.
   * @param backend the backend.
   * @return the label to be used for the provided backend.
   */
  protected String getName(BackendDescriptor backend)
  {
    return backend.getBackendID();
  }

  /**
   * Returns the monitoring entry associated with the provided backend.
   * @param backend the backend.
   * @return the monitoring entry associated with the provided backend.  Returns
   * <CODE>null</CODE> if there is no monitoring entry associated.
   */
  protected CustomSearchResult getMonitoringEntry(BackendDescriptor backend)
  {
    return backend.getMonitoringEntry();
  }

  private String[] getLine(BackendDescriptor backend)
  {
    String[] line = new String[attributes.size() + 1];
    line[0] = getName(backend);
    int i = 1;
    CustomSearchResult monitoringEntry = getMonitoringEntry(backend);
    for (String attr : attributes)
    {
      String o = getFirstValueAsString(monitoringEntry, attr);
      if (o != null)
      {
        line[i] = o;
      }
      else
      {
        line[i] = NO_VALUE_SET.toString();
      }
      i++;
    }
    return line;
  }

}

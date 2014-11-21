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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JLabel;

import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.BasicMonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.MonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.util.Utilities;
/**
 * The panel displaying the entry caches monitor panel.
 */
public class EntryCachesMonitoringPanel extends GeneralMonitoringPanel
{
  private static final long serialVersionUID = 9031734563700069830L;
  static List<MonitoringAttributes> ngOperations =
    new ArrayList<MonitoringAttributes>();
  {
    ngOperations.add(BasicMonitoringAttributes.ENTRY_CACHE_TRIES);
    ngOperations.add(BasicMonitoringAttributes.ENTRY_CACHE_HITS);
    ngOperations.add(BasicMonitoringAttributes.ENTRY_CACHE_HIT_RATIO);
    ngOperations.add(BasicMonitoringAttributes.CURRENT_ENTRY_CACHE_SIZE);
    ngOperations.add(BasicMonitoringAttributes.MAX_ENTRY_CACHE_SIZE);
    ngOperations.add(BasicMonitoringAttributes.CURRENT_ENTRY_CACHE_COUNT);
    ngOperations.add(BasicMonitoringAttributes.MAX_ENTRY_CACHE_COUNT);
  }
  private ArrayList<JLabel> monitoringLabels =
    new ArrayList<JLabel>();
  {
    for (int i=0; i<ngOperations.size(); i++)
    {
      monitoringLabels.add(Utilities.createDefaultLabel());
    }
  }
  private ArrayList<JLabel> labels = new ArrayList<JLabel>();
  {
    for (int i=0; i<ngOperations.size(); i++)
    {
      labels.add(Utilities.createPrimaryLabel(getLabel(ngOperations.get(i))));
    }
  }

  /**
   * Default constructor.
   */
  public EntryCachesMonitoringPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return monitoringLabels.get(0);
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JLabel lTitle = Utilities.createTitleLabel(
        INFO_CTRL_PANEL_ENTRY_CACHES.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    add(lTitle, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 1;
    for (int i=0; i<ngOperations.size(); i++)
    {
      gbc.gridy ++;
      gbc.insets.left = 0;
      gbc.gridx = 0;
      gbc.weightx = 0.0;
      gbc.gridwidth = 1;
      add(labels.get(i), gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      gbc.gridwidth = 2;
      add(monitoringLabels.get(i), gbc);
    }

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 3;
    add(Box.createGlue(), gbc);

    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel.
   *
   */
  public void updateContents()
  {
    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
    }
    CustomSearchResult csr = null;
    if (server != null)
    {
      csr = server.getEntryCachesMonitor();
    }
    if (csr != null)
    {
      updateMonitoringInfo(ngOperations, monitoringLabels, csr);
      int index = 0;
      for (MonitoringAttributes attr : ngOperations)
      {
        if (Utilities.getFirstMonitoringValue(csr, attr.getAttributeName())
            == null)
        {
          monitoringLabels.get(index).setVisible(false);
          labels.get(index).setVisible(false);
        }
        index ++;
      }
      revalidate();
      repaint();
    }
    else
    {
      for (JLabel l : monitoringLabels)
      {
        l.setText(NO_VALUE_SET.toString());
      }
    }
  }
}

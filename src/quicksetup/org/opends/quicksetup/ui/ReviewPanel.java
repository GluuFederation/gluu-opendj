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

package org.opends.quicksetup.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Abstract class for rendering a review panel with fields and value
 * that the user can use to confirm an application's operation.
 */
public abstract class ReviewPanel extends QuickSetupStepPanel {

  private static final long serialVersionUID = 509534079919269557L;

  /**
   * Creates an instance.
   * @param application GuiApplication this panel represents
   */
  public ReviewPanel(GuiApplication application) {
    super(application);
  }

  /**
   * Creates the panel containing field names and values.
   * @return JPanel containing fields and values
   */
  protected abstract JPanel createFieldsPanel();

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel panel = UIFactory.makeJPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(createFieldsPanel(), gbc);

    addVerticalGlue(panel);

    JComponent chk = getBottomComponent();
    if (chk != null) {
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.weighty = 0.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      panel.add(chk, gbc);
    }

    return panel;
  }

  /**
   * Returns the component that will placed at the bottom of the panel.
   * In the case of the installer and the uninstaller this is basically the
   * start server check box.
   * If it does not exist creates the component.
   * @return the component that will placed at the bottom of the panel.
   */
  protected abstract JComponent getBottomComponent();
}

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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;

import javax.swing.JPanel;

import org.opends.quicksetup.UserData;

/**
 * This class is an abstract class that provides some commodity methods.
 *
 */
abstract class QuickSetupPanel extends JPanel
{
  private static final long serialVersionUID = 2096518919339628055L;

  private GuiApplication application;

  private QuickSetup quickSetup;

  /**
   * The basic constructor to be called by the subclasses.
   * @param application Application this panel represents
   */
  protected QuickSetupPanel(GuiApplication application)
  {
    super();
    this.application = application;
    setOpaque(false);
  }

  /**
   * Sets the instance of <code>QuickSetup</code> acting as controller.
   * @param qs QuickSetup instance
   */
  void setQuickSetup(QuickSetup qs) {
    this.quickSetup = qs;
  }

  /**
   * Gets the instance of <code>QuickSetup</code> acting as controller.
   * @return QuickSetup instance
   */
  protected QuickSetup getQuickSetup() {
    return this.quickSetup;
  }

  /**
   * Returns the frame or window containing this panel.
   * @return the frame or window containing this panel.
   */
  public Component getMainWindow()
  {
    Component w = null;
    Component c = this;

    while (w == null)
    {
      if (c instanceof Frame || c instanceof Window)
      {
        w = c;
      }
      if (c.getParent() == null)
      {
        w = c;
      } else
      {
        c = c.getParent();
      }
    }

    return w;
  }

  /**
   * Gets the application this panel represents.
   * @return GuiApplication this panel represents
   */
  protected GuiApplication getApplication() {
    return this.application;
  }

  /**
   * Gets the user data associated with the current application.
   * @return UserData user specified data
   */
  protected UserData getUserData() {
    return application.getUserData();
  }

}

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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.io.File;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The task called when we want to start the server.
 *
 */
public class StopServerTask extends StartStopTask
{

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   */
  public StopServerTask(ControlPanelInfo info, ProgressDialog dlg)
  {
    super(info, dlg);
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.STOP_SERVER;
  }


  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_STOP_SERVER_TASK_DESCRIPTION.get();
  }

  /**
   * {@inheritDoc}
   */
  public void runTask()
  {
    super.runTask();
    if (state == State.FINISHED_SUCCESSFULLY)
    {
      // Verify that the server is actually stopped
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont(
              "<b>"+INFO_CTRL_PANEL_SERVER_STOPPED.get()+"</b><br><br>",
              ColorAndFontConstants.progressFont));
        }
      });
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return getCommandLinePath("stop-ds");
  }

  /**
   * Method called just after calling the command-line.  To be overwritten
   * by the inheriting classes.
   */
  protected void postCommandLine()
  {
    if (returnCode != 0)
    {
      state = State.FINISHED_WITH_ERROR;
    }
    else
    {
      File f = new File(getInfo().getServerDescriptor().getInstancePath());
      // Check that the server is actually stopped.
      boolean stopped = !Utilities.isServerRunning(f);
      int nTries = 20;
      while (!stopped && nTries > 0)
      {
        try
        {
          Thread.sleep(700);
        }
        catch (Throwable t)
        {
        }
        stopped = !Utilities.isServerRunning(f);
        nTries --;
      }
      if (!stopped)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.applyFont(
                    "<br>"+
                    ERR_CTRL_PANEL_STOPPING_SERVER_POST_CMD_LINE.get(
                        getCommandLinePath("stop-ds"))+"<br>",
                        ColorAndFontConstants.progressFont));
          }
        });
        returnCode = -1;
        state = State.FINISHED_WITH_ERROR;
      }
      else
      {
        state = State.FINISHED_SUCCESSFULLY;
      }
    }
  }
}

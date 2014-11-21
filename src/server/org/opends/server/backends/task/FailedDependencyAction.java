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
package org.opends.server.backends.task;

import org.opends.messages.Message;
import static org.opends.messages.TaskMessages.*;


/**
 * This enumeration defines the various ways that a task can behave if it is
 * dependent upon another task and that earlier task is done running but did not
 * complete successfully.
 */
public enum FailedDependencyAction
{
  /**
   * The action that indicates that the dependent task should be processed
   * anyway.
   */
  PROCESS(INFO_FAILED_DEPENDENCY_ACTION_PROCESS.get()),



  /**
   * The action that indicates that the dependent task should be canceled.
   */
  CANCEL(INFO_FAILED_DEPENDENCY_ACTION_CANCEL.get()),



  /**
   * The action that indicates that the dependent task should be disabled so
   * that an administrator will have to re-enable it before it can start.
   */
  DISABLE(INFO_FAILED_DEPENDENCY_ACTION_DISABLE.get());


  /**
   * Returns the default action.
   *
   * @return the default action
   */
  public static FailedDependencyAction defaultValue() {
    return CANCEL;
  }

  /**
   * Retrieves the failed dependency action that corresponds to the provided
   * string value.
   *
   * @param  s  The string value for which to retrieve the corresponding
   *            failed dependency action.
   *
   * @return  The corresponding failed dependency action, or <CODE>null</CODE>
   *          if none could be associated with the provided string.
   */
  public static FailedDependencyAction fromString(String s)
  {
    String lowerString = s.toLowerCase();
    if (lowerString.equals("process") ||
            lowerString.equals(INFO_FAILED_DEPENDENCY_ACTION_PROCESS.get().
                    toString().toLowerCase()))
    {
      return PROCESS;
    }
    else if (lowerString.equals("cancel") ||
            lowerString.equals(INFO_FAILED_DEPENDENCY_ACTION_CANCEL.get().
                    toString().toLowerCase()))
    {
      return CANCEL;
    }
    else if (lowerString.equals("disable") ||
            lowerString.equals(INFO_FAILED_DEPENDENCY_ACTION_DISABLE.get().
                    toString().toLowerCase()))
    {
      return DISABLE;
    }
    else
    {
      return null;
    }
  }

  private Message name;

  /**
   * Gets the display name of this action.
   *
   * @return Message representing the name of this action
   */
  public Message getDisplayName() {
    return name;
  }

  private FailedDependencyAction(Message name) {
    this.name = name;
  }
}


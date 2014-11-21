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

package org.opends.quicksetup.event;

import java.util.EventObject;

import org.opends.quicksetup.ButtonName;

/**
 * The event that is generated when the user clicks in one of the wizard buttons
 * specified in org.opends.quicksetup.ButtonName.
 *
 */
public class ButtonEvent extends EventObject
{
  private static final long serialVersionUID = -4411929136433332009L;

  private ButtonName buttonName;

  /**
   * Constructor.
   * @param source the button that generated the event
   * @param buttonName the button name.
   */
  public ButtonEvent(Object source, ButtonName buttonName)
  {
    super(source);
    this.buttonName = buttonName;
  }

  /**
   * Gets the ButtonName of the button that generated the event.
   * @return the ButtonName of the button that generated the event.
   */
  public ButtonName getButtonName()
  {
    return buttonName;
  }
}

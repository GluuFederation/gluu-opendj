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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.event;

/**
 * The event used to notify that a new configuration element was created.  It
 * is used by index and schema editors to notify changes of the index
 * configuration and in the schema.
 *
 */
public class ConfigurationElementCreatedEvent
{
  private Object source;
  private Object configurationObject;

  /**
   * Constructor of the event.
   * @param source the source of the event.
   * @param configurationObject the newly created configuration object.
   */
  public ConfigurationElementCreatedEvent(Object source,
      Object configurationObject)
  {
    this.source = source;
    this.configurationObject = configurationObject;
  }

  /**
   * Returns the newly created configuration object.
   * @return the newly created configuration object.
   */
  public Object getConfigurationObject()
  {
    return configurationObject;
  }

  /**
   * Returns the source of the event.
   * @return the source of the event.
   */
  public Object getSource()
  {
    return source;
  }
}

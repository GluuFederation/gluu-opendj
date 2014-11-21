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
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ConfigChangeResult;
import org.opends.messages.MessageBuilder;


/**
 * This interface defines the methods that a Directory Server
 * component should implement if it wishes to be able to receive
 * notification of changes to a configuration entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface ConfigChangeListener
{
  /**
   * Indicates whether the configuration entry that will result from a
   * proposed modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will
   *                             result from the requested update.
   * @param  unacceptableReason  A buffer to which this method can
   *                             append a human-readable message
   *                             explaining why the proposed change is
   *                             not acceptable.
   *
   * @return  {@code true} if the proposed entry contains an
   *          acceptable configuration, or {@code false} if it does
   *          not.
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
                      MessageBuilder unacceptableReason);



  /**
   * Attempts to apply a new configuration to this Directory Server
   * component based on the provided changed entry.
   *
   * @param  configEntry  The configuration entry that containing the
   *                      updated configuration for this component.
   *
   * @return  Information about the result of processing the
   *          configuration change.
   */
  public ConfigChangeResult applyConfigurationChange(
                                 ConfigEntry configEntry);
}


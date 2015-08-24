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
 *      Copyright 2013-2015 ForgeRock AS
 */

package org.opends.server.tools.upgrade;

import com.forgerock.opendj.cli.ClientException;

/**
 * An upgrade task.
 */
public interface UpgradeTask
{

  /**
   * Defines the different types of upgrade tasks.
   */
  public enum TaskType {
    /**
     * Defines a standard task.
     */
    NORMAL,
    /**
     * Defines a task which require a standard user interaction.
     */
    NEED_USER_INTERACTION,
    /**
     * Defines a critical task which require an imperative user interaction.
     */
    MANDATORY_USER_INTERACTION,
    /**
     * Defines a task which take a long time to complete.
     */
    TAKE_LONG_TIME_TO_COMPLETE,
    /**
     * Defines a task which cannot be reverted once started.
     */
    CANNOT_BE_REVERTED
  }

  /**
   * Performs any preparation work required before performing the upgrade task, including
   * interacting with the user where needed (e.g. in order to ask for confirmation), and throw a
   * {@code ClientException} if the upgrade cannot proceed.
   *
   * @param context
   *          Context through which tasks can interact with the server installation.
   * @throws ClientException
   *           If the upgrade cannot proceed.
   */
  void prepare(UpgradeContext context) throws ClientException;

  /**
   * Performs this upgrade task.
   *
   * @param context
   *          Context through which tasks can interact with the server installation.
   * @throws ClientException
   *           If an error occurred while performing the task.
   */
  void perform(UpgradeContext context) throws ClientException;

  /**
   * This method will be invoked after all upgrade tasks have completed
   * successfully The post upgrade tasks are processes which should be launched
   * after a successful upgrade.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @throws ClientException
   *           If the task cannot proceed.
   */
  void postUpgrade(UpgradeContext context) throws ClientException;

  /**
   * This method will be invoked only if one of the previous post upgrade task
   * has failed.
   *
   * @param context
   *          Context through which tasks can interact with the server
   *          installation.
   * @throws ClientException
   *           If the task cannot proceed.
   */
  void postponePostUpgrade(UpgradeContext context) throws ClientException;
}

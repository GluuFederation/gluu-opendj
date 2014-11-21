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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */

package org.opends.quicksetup;

import org.opends.admin.ads.ADSContext;

/**
 * Defines common constants.
 */
public class Constants {

  /** Platform appropriate line separator. */
  static public final String LINE_SEPARATOR =
          System.getProperty("line.separator");

  /** HTML line break tag. */
  public static final String HTML_LINE_BREAK = "<br>";

  /** HTML bold open tag. */
  public static final String HTML_BOLD_OPEN = "<b>";

  /** HTML bold close tag. */
  public static final String HTML_BOLD_CLOSE = "</b>";

  /** HTML list item open tag. */
  public static final String HTML_LIST_ITEM_OPEN = "<li>";

  /** HTML list item close tag. */
  public static final String HTML_LIST_ITEM_CLOSE = "</li>";

  /** Default dynamic name of directory manager. */
  public static final String DIRECTORY_MANAGER_DN = "cn=Directory Manager";

  /** Default global admin UID. */
  public static final String GLOBAL_ADMIN_UID = ADSContext.GLOBAL_ADMIN_UID;
  /** These HTML tags cause a line break in formatted text. */
  public static final String[] BREAKING_TAGS = {
          HTML_LINE_BREAK,
          HTML_LIST_ITEM_CLOSE
  };

  /** DN of the schema object. */
  public static final String SCHEMA_DN = "cn=schema";

  /** DN of the replication changes base DN. */
  public static final String REPLICATION_CHANGES_DN = "dc=replicationChanges";

  /** The cli java system property. */
  public static final String CLI_JAVA_PROPERTY = "org.opends.quicksetup.cli";

  /** The default replication port. */
  public static final int DEFAULT_REPLICATION_PORT = 8989;

  /** The maximum chars we show in a line of a dialog. */
  public static final int MAX_CHARS_PER_LINE_IN_DIALOG = 100;

  /** Prefix for log files. */
  public static final String LOG_FILE_PREFIX = "opendj-setup-";

  /** Suffix for log files. */
  public static final String LOG_FILE_SUFFIX = ".log";
}

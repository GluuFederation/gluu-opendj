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
package org.opends.server.backends.jeb;

import org.opends.server.types.Entry;
import org.opends.server.types.DirectoryException;
import com.sleepycat.je.DatabaseException;

import java.io.IOException;

/**
 * The interface that represents a index builder for the import process.
 */
public interface IndexBuilder
{
  /**
   * This method must be called before this object can process any
   * entries. It cleans up any temporary files left over from a
   * previous import.
   */
  void startProcessing();

  /**
   * Indicates that the index thread should process the provided entry.
   * @param oldEntry The existing contents of the entry, or null if this is
   * a new entry.
   * @param newEntry The new contents of the entry.
   * @param entryID The entry ID.
   * @throws com.sleepycat.je.DatabaseException If an error occurs in the JE
   * database.
   * @throws java.io.IOException If an I/O error occurs while writing an
   * intermediate file.
   * @throws DirectoryException If an error occurs while processing the entry.
   */
  void processEntry(Entry oldEntry, Entry newEntry, EntryID entryID)
      throws DatabaseException, IOException, DirectoryException;

  /**
   * Indicates that there will be no more updates.
   * @throws IOException If an I/O error occurs while writing an intermediate
   * file.
   */
  void stopProcessing() throws IOException;
}

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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.util;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.tools.makeldif.TemplateEntry;
import org.opends.server.types.*;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class provides a mechanism for writing entries in LDIF form to a file or
 * an output stream.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class LDIFWriter implements Closeable
{
  // FIXME -- Add support for generating a hash when writing the data.
  // FIXME -- Add support for signing the hash that is generated.

  /** The writer to which the LDIF information will be written. */
  private BufferedWriter writer;

  /** The configuration to use for the export. */
  private LDIFExportConfig exportConfig;

  /** Regular expression used for splitting comments on line-breaks. */
  private static final Pattern SPLIT_NEWLINE = Pattern.compile("\\r?\\n");



  /**
   * Creates a new LDIF writer with the provided configuration.
   *
   * @param  exportConfig  The configuration to use for the export.  It must not
   *                       be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while opening the writer.
   */
  public LDIFWriter(LDIFExportConfig exportConfig)
         throws IOException
  {
    ifNull(exportConfig);
    this.exportConfig = exportConfig;

    writer = exportConfig.getWriter();
  }



  /**
   * Writes the provided comment to the LDIF file, optionally wrapping near the
   * specified column.  Each line will be prefixed by the octothorpe (#)
   * character followed by a space.  If the comment should be wrapped at a
   * specified column, then it will attempt to do so at the first whitespace
   * character at or before that column (so it will try not wrap in the middle
   * of a word).
   * <BR><BR>
   * This comment will be ignored by the
   * Directory Server's LDIF reader, as well as any other compliant LDIF parsing
   * software.
   *
   * @param  comment     The comment to be written.  Any line breaks that it
   *                     contains will be honored, and potentially new line
   *                     breaks may be introduced by the wrapping process.  It
   *                     must not be <CODE>null</CODE>.
   * @param  wrapColumn  The column at which long lines should be wrapped, or
   *                     -1 to indicate that no additional wrapping should be
   *                     added.  This will override the wrap column setting
   *                     specified in the LDIF export configuration.
   *
   * @throws  IOException  If a problem occurs while attempting to write the
   *                       comment to the LDIF file.
   */
  public void writeComment(LocalizableMessage comment, int wrapColumn)
         throws IOException
  {
    ifNull(comment);


    // First, break up the comment into multiple lines to preserve the original
    // spacing that it contained.
    String[] lines = SPLIT_NEWLINE.split(comment);

    // Now iterate through the lines and write them out, prefixing and wrapping
    // them as necessary.
    for (String l : lines)
    {
      if (wrapColumn <= 0)
      {
        writer.write("# ");
        writer.write(l);
        writer.newLine();
      }
      else
      {
        int breakColumn = wrapColumn - 2;

        if (l.length() <= breakColumn)
        {
          writer.write("# ");
          writer.write(l);
          writer.newLine();
        }
        else
        {
          int startPos = 0;
outerLoop:
          while (startPos < l.length())
          {
            if (startPos + breakColumn >= l.length())
            {
              writer.write("# ");
              writer.write(l.substring(startPos));
              writer.newLine();
              startPos = l.length();
            }
            else
            {
              int endPos = startPos + breakColumn;

              int i=endPos - 1;
              while (i > startPos)
              {
                if (l.charAt(i) == ' ')
                {
                  writer.write("# ");
                  writer.write(l.substring(startPos, i));
                  writer.newLine();

                  startPos = i+1;
                  continue outerLoop;
                }

                i--;
              }

              // If we've gotten here, then there are no spaces on the entire
              // line.  If that happens, then we'll have to break in the middle
              // of a word.
              writer.write("# ");
              writer.write(l.substring(startPos, endPos));
              writer.newLine();

              startPos = endPos;
            }
          }
        }
      }
    }
  }

  /**
 * Iterates over each entry contained in the map and writes out the entry in
 * LDIF format. The main benefit of this method is that the entries can be
 * sorted by DN and output in sorted order.
 *
 * @param entries The Map containing the entries keyed by DN.
 *
 * @return <CODE>true</CODE>of all of the entries were
 *                  written out, <CODE>false</CODE>if it was not
 *                  because of the export configuration.
 *
 * @throws IOException  If a problem occurs while writing the entry to LDIF.
 *
 * @throws LDIFException If a problem occurs while trying to determine
 *                         whether to include the entry in the export.
 */
  public boolean writeEntries(Collection<Entry> entries) throws IOException,
      LDIFException
  {
    for (Entry entry : entries)
    {
      if (!writeEntry(entry))
      {
        return false;
      }
    }
    return true;
  }


  /**
   * Writes the provided entry to LDIF.
   *
   * @param  entry  The entry to be written.  It must not be <CODE>null</CODE>.
   *
   * @return  <CODE>true</CODE> if the entry was actually written, or
   *          <CODE>false</CODE> if it was not because of the export
   *          configuration.
   *
   * @throws  IOException  If a problem occurs while writing the entry to LDIF.
   *
   * @throws  LDIFException  If a problem occurs while trying to determine
   *                         whether to include the entry in the export.
   */
  public boolean writeEntry(Entry entry)
         throws IOException, LDIFException
  {
    ifNull(entry);
    return entry.toLDIF(exportConfig);
  }


  /**
   * Writes the provided template entry to LDIF.
   *
   * @param  templateEntry  The template entry to be written.  It must not be
   * <CODE>null</CODE>.
   *
   * @return  <CODE>true</CODE> if the entry was actually written, or
   *          <CODE>false</CODE> if it was not because of the export
   *          configuration.
   *
   * @throws  IOException  If a problem occurs while writing the template entry
   *                       to LDIF.
   *
   * @throws  LDIFException  If a problem occurs while trying to determine
   *                         whether to include the template entry in the
   *                         export.
   */
  public boolean writeTemplateEntry(TemplateEntry templateEntry)
  throws IOException, LDIFException
  {
    ifNull(templateEntry);
    return templateEntry.toLDIF(exportConfig);
  }

  /**
   * Writes a change record entry for the provided change record.
   *
   * @param  changeRecord  The change record entry to be written.
   *
   * @throws  IOException  If a problem occurs while writing the change record.
   */
  public void writeChangeRecord(ChangeRecordEntry changeRecord)
         throws IOException
  {
    ifNull(changeRecord);


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // First, write the DN.
    writeDN("dn", changeRecord.getDN(), writer, wrapLines, wrapColumn);


    // Figure out what type of change it is and act accordingly.
    if (changeRecord instanceof AddChangeRecordEntry)
    {
      StringBuilder changeTypeLine = new StringBuilder("changetype: add");
      writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);

      AddChangeRecordEntry addRecord = (AddChangeRecordEntry) changeRecord;
      for (Attribute a : addRecord.getAttributes())
      {
        for (ByteString v : a)
        {
          final String attrName = a.getNameWithOptions();
          writeAttribute(attrName, v, writer, wrapLines, wrapColumn);
        }
      }
    }
    else if (changeRecord instanceof DeleteChangeRecordEntry)
    {
      StringBuilder changeTypeLine = new StringBuilder("changetype: delete");
      writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);
    }
    else if (changeRecord instanceof ModifyChangeRecordEntry)
    {
      StringBuilder changeTypeLine = new StringBuilder("changetype: modify");
      writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);

      ModifyChangeRecordEntry modifyRecord =
           (ModifyChangeRecordEntry) changeRecord;
      List<RawModification> mods = modifyRecord.getModifications();
      Iterator<RawModification> iterator = mods.iterator();
      while (iterator.hasNext())
      {
        RawModification m = iterator.next();
        RawAttribute a = m.getAttribute();
        String attrName = a.getAttributeType();
        StringBuilder modTypeLine = new StringBuilder();
        modTypeLine.append(m.getModificationType());
        modTypeLine.append(": ");
        modTypeLine.append(attrName);
        writeLDIFLine(modTypeLine, writer, wrapLines, wrapColumn);

        for (ByteString s : a.getValues())
        {
          StringBuilder valueLine = new StringBuilder(attrName);
          String stringValue = s.toString();

          if (needsBase64Encoding(stringValue))
          {
            valueLine.append(":: ");
            valueLine.append(Base64.encode(s));
          }
          else
          {
            valueLine.append(": ");
            valueLine.append(stringValue);
          }

          writeLDIFLine(valueLine, writer, wrapLines, wrapColumn);
        }

        if (iterator.hasNext())
        {
          StringBuilder dashLine = new StringBuilder("-");
          writeLDIFLine(dashLine, writer, wrapLines, wrapColumn);
        }
      }
    }
    else if (changeRecord instanceof ModifyDNChangeRecordEntry)
    {
      StringBuilder changeTypeLine = new StringBuilder("changetype: moddn");
      writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);

      ModifyDNChangeRecordEntry modifyDNRecord =
           (ModifyDNChangeRecordEntry) changeRecord;

      StringBuilder newRDNLine = new StringBuilder("newrdn: ");
      modifyDNRecord.getNewRDN().toString(newRDNLine);
      writeLDIFLine(newRDNLine, writer, wrapLines, wrapColumn);

      StringBuilder deleteOldRDNLine = new StringBuilder("deleteoldrdn: ");
      deleteOldRDNLine.append(modifyDNRecord.deleteOldRDN() ? "1" : "0");
      writeLDIFLine(deleteOldRDNLine, writer, wrapLines, wrapColumn);

      DN newSuperiorDN = modifyDNRecord.getNewSuperiorDN();
      if (newSuperiorDN != null)
      {
        StringBuilder newSuperiorLine = new StringBuilder("newsuperior: ");
        newSuperiorDN.toString(newSuperiorLine);
        writeLDIFLine(newSuperiorLine, writer, wrapLines, wrapColumn);
      }
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }



  /**
   * Writes an add change record for the provided entry.  No filtering will be
   * performed for this entry, nor will any export plugins be invoked.  Further,
   * only the user attributes will be included.
   *
   * @param  entry  The entry to include in the add change record.  It must not
   *                be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while writing the add record.
   */
  public void writeAddChangeRecord(Entry entry)
         throws IOException
  {
    ifNull(entry);


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // First, write the DN.
    writeDN("dn", entry.getName(), writer, wrapLines, wrapColumn);


    // Next, the changetype.
    StringBuilder changeTypeLine = new StringBuilder("changetype: add");
    writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);


    // Now the objectclasses.
    for (String s : entry.getObjectClasses().values())
    {
      StringBuilder ocLine = new StringBuilder();
      ocLine.append("objectClass: ");
      ocLine.append(s);
      writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
    }


    // Finally, the set of user attributes.
    for (AttributeType attrType : entry.getUserAttributes().keySet())
    {
      for (Attribute a : entry.getUserAttribute(attrType))
      {
        StringBuilder attrName = new StringBuilder(a.getName());
        for (String o : a.getOptions())
        {
          attrName.append(";");
          attrName.append(o);
        }

        for (ByteString v : a)
        {
          writeAttribute(attrName, v, writer, wrapLines, wrapColumn);
        }
      }
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }



  /**
   * Writes a delete change record for the provided entry, optionally including
   * a comment with the full entry contents.  No filtering will be performed for
   * this entry, nor will any export plugins be invoked.  Further, only the user
   * attributes will be included.
   *
   * @param  entry         The entry to include in the delete change record.  It
   *                       must not be <CODE>null</CODE>.
   * @param  commentEntry  Indicates whether to include a comment with the
   *                       contents of the entry.
   *
   * @throws  IOException  If a problem occurs while writing the delete record.
   */
  public void writeDeleteChangeRecord(Entry entry, boolean commentEntry)
         throws IOException
  {
    ifNull(entry);

    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // Add the DN and changetype lines.
    writeDN("dn", entry.getName(), writer, wrapLines, wrapColumn);

    StringBuilder changeTypeLine = new StringBuilder("changetype: delete");
    writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);


    // If we should include a comment with the rest of the entry contents, then
    // do so now.
    if (commentEntry)
    {
      // Write the objectclasses.
      for (String s : entry.getObjectClasses().values())
      {
        StringBuilder ocLine = new StringBuilder();
        ocLine.append("# objectClass: ");
        ocLine.append(s);
        writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
      }

      // Write the set of user attributes.
      for (AttributeType attrType : entry.getUserAttributes().keySet())
      {
        for (Attribute a : entry.getUserAttribute(attrType))
        {
          StringBuilder attrName = new StringBuilder();
          attrName.append("# ");
          attrName.append(a.getName());
          for (String o : a.getOptions())
          {
            attrName.append(";");
            attrName.append(o);
          }

          for (ByteString v : a)
          {
            writeAttribute(attrName, v, writer, wrapLines, wrapColumn);
          }
        }
      }
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }



  /**
   * Writes a modify change record with the provided information.  No filtering
   * will be performed, nor will any export plugins be invoked.
   *
   * @param  dn             The DN of the entry being modified.  It must not be
   *                        <CODE>null</CODE>.
   * @param  modifications  The set of modifications to include in the change
   *                        record.  It must not be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while writing the modify record.
   */
  public void writeModifyChangeRecord(DN dn, List<Modification> modifications)
         throws IOException
  {
    ifNull(dn, modifications);

    // If there aren't any modifications, then there's nothing to do.
    if (modifications.isEmpty())
    {
      return;
    }


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // Write the DN and changetype.
    writeDN("dn", dn, writer, wrapLines, wrapColumn);

    StringBuilder changeTypeLine = new StringBuilder("changetype: modify");
    writeLDIFLine(changeTypeLine, writer, wrapLines, wrapColumn);


    // Iterate through the modifications and write them to the LDIF.
    Iterator<Modification> iterator = modifications.iterator();
    while (iterator.hasNext())
    {
      Modification m    = iterator.next();
      Attribute    a    = m.getAttribute();

      StringBuilder nameBuffer = new StringBuilder(a.getName());
      for (String o : a.getOptions())
      {
        nameBuffer.append(";");
        nameBuffer.append(o);
      }
      String  name = nameBuffer.toString();

      StringBuilder modTypeLine = new StringBuilder();
      modTypeLine.append(m.getModificationType());
      modTypeLine.append(": ");
      modTypeLine.append(name);
      writeLDIFLine(modTypeLine, writer, wrapLines, wrapColumn);

      for (ByteString v : a)
      {
        writeAttribute(name, v, writer, wrapLines, wrapColumn);
      }


      // If this is the last modification, then append blank line.  Otherwise
      // write a line with just a dash.
      if (iterator.hasNext())
      {
        writer.write("-");
      }
      writer.newLine();
    }
  }



  /**
   * Writes a modify DN change record with the provided information.  No
   * filtering will be performed, nor will any export plugins be invoked.
   *
   * @param  dn            The DN of the entry before the rename.  It must not
   *                       be <CODE>null</CODE>.
   * @param  newRDN        The new RDN for the entry.  It must not be
   *                       <CODE>null</CODE>.
   * @param  deleteOldRDN  Indicates whether the old RDN value should be removed
   *                       from the entry.
   * @param  newSuperior   The new superior DN for the entry, or
   *                       <CODE>null</CODE> if the entry will stay below the
   *                       same parent.
   *
   * @throws  IOException  If a problem occurs while writing the modify record.
   */
  public void writeModifyDNChangeRecord(DN dn, RDN newRDN, boolean deleteOldRDN,
                                        DN newSuperior)
         throws IOException
  {
    ifNull(dn, newRDN);


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // Write the current DN.
    writeDN("dn", dn, writer, wrapLines, wrapColumn);


    // Write the changetype.  Some older tools may not support the "moddn"
    // changetype, so only use it if a newSuperior element has been provided,
    // but use modrdn elsewhere.
    String changeType = newSuperior == null ? "changetype: modrdn" : "changetype: moddn";
    writeLDIFLine(new StringBuilder(changeType), writer, wrapLines, wrapColumn);


    // Write the newRDN element.
    StringBuilder rdnLine = new StringBuilder("newrdn");
    appendLDIFSeparatorAndValue(rdnLine, ByteString.valueOf(newRDN.toString()));
    writeLDIFLine(rdnLine, writer, wrapLines, wrapColumn);


    // Write the deleteOldRDN element.
    StringBuilder deleteOldRDNLine = new StringBuilder();
    deleteOldRDNLine.append("deleteoldrdn: ");
    deleteOldRDNLine.append(deleteOldRDN ? "1" : "0");
    writeLDIFLine(deleteOldRDNLine, writer, wrapLines, wrapColumn);

    if (newSuperior != null)
    {
      writeDN("newsuperior", newSuperior, writer, wrapLines, wrapColumn);
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();
  }

  private void writeDN(String attrType, DN dn, BufferedWriter writer,
      boolean wrapLines, int wrapColumn) throws IOException
  {
    final StringBuilder newLine = new StringBuilder(attrType);
    appendLDIFSeparatorAndValue(newLine, ByteString.valueOf(dn.toString()));
    writeLDIFLine(newLine, writer, wrapLines, wrapColumn);
  }

  private void writeAttribute(CharSequence attrName, ByteString v,
      BufferedWriter writer, boolean wrapLines, int wrapColumn)
      throws IOException
  {
    StringBuilder newLine = new StringBuilder(attrName);
    appendLDIFSeparatorAndValue(newLine, v);
    writeLDIFLine(newLine, writer, wrapLines, wrapColumn);
  }

  /**
   * Flushes the data written to the output stream or underlying file.
   *
   * @throws  IOException  If a problem occurs while flushing the output.
   */
  public void flush()
         throws IOException
  {
    writer.flush();
  }



  /**
   * Closes the LDIF writer and the underlying output stream or file.
   *
   * @throws  IOException  If a problem occurs while closing the writer.
   */
  @Override
  public void close()
         throws IOException
  {
    writer.flush();
    writer.close();
  }



  /**
   * Appends an LDIF separator and properly-encoded form of the given
   * value to the provided buffer.  If the value is safe to include
   * as-is, then a single colon, a single space, space, and the
   * provided value will be appended.  Otherwise, two colons, a single
   * space, and a base64-encoded form of the value will be appended.
   *
   * @param  buffer      The buffer to which the information should be
   *                     appended.  It must not be <CODE>null</CODE>.
   * @param  valueBytes  The value to append to the buffer.  It must not be
   *                     <CODE>null</CODE>.
   */
  public static void appendLDIFSeparatorAndValue(StringBuilder buffer,
                                                 ByteSequence valueBytes)
  {
    appendLDIFSeparatorAndValue(buffer, valueBytes, false, false);
  }

  /**
   * Appends an LDIF separator and properly-encoded form of the given
   * value to the provided buffer.  If the value is safe to include
   * as-is, then a single colon, a single space, space, and the
   * provided value will be appended.  Otherwise, two colons, a single
   * space, and a base64-encoded form of the value will be appended.
   * @param  buffer      The buffer to which the information should be
   *                     appended.  It must not be <CODE>null</CODE>.
   * @param  valueBytes  The value to append to the buffer.  It must not be
   *                     <CODE>null</CODE>.
   * @param isURL        Whether the provided value is an URL value or not.
   * @param isBase64     Whether the provided value is a base 64 value or not.
   */
  public static void appendLDIFSeparatorAndValue(StringBuilder buffer,
      ByteSequence valueBytes, boolean isURL, boolean isBase64)
  {
    ifNull(buffer, valueBytes);


    // If the value is empty, then just append a single colon (the URL '<' if
    // required) and a single space.
    final boolean valueIsEmpty = valueBytes == null || valueBytes.length() == 0;
    if (isURL)
    {
      buffer.append(":< ");
      if (!valueIsEmpty)
      {
        buffer.append(valueBytes.toString());
      }
    }
    else if (isBase64)
    {
      buffer.append(":: ");
      if (!valueIsEmpty)
      {
        buffer.append(valueBytes.toString());
      }
    }
    else if (needsBase64Encoding(valueBytes))
    {
      buffer.append(":: ");
      buffer.append(Base64.encode(valueBytes));
    }
    else
    {
      buffer.append(": ");
      if (!valueIsEmpty)
      {
        buffer.append(valueBytes.toString());
      }
    }
  }



  /**
   * Writes the provided line to LDIF using the provided information.
   *
   * @param  line        The line of information to write.  It must not be
   *                     <CODE>null</CODE>.
   * @param  writer      The writer to which the data should be written.  It
   *                     must not be <CODE>null</CODE>.
   * @param  wrapLines   Indicates whether to wrap long lines.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   *
   * @throws  IOException  If a problem occurs while writing the information.
   */
  public static void writeLDIFLine(StringBuilder line, BufferedWriter writer,
                                   boolean wrapLines, int wrapColumn)
          throws IOException
  {
    ifNull(line, writer);

    int length = line.length();
    if (wrapLines && length > wrapColumn)
    {
      writer.write(line.substring(0, wrapColumn));
      writer.newLine();

      int pos = wrapColumn;
      while (pos < length)
      {
        int writeLength = Math.min(wrapColumn-1, length-pos);
        writer.write(' ');
        writer.write(line.substring(pos, pos+writeLength));
        writer.newLine();

        pos += wrapColumn-1;
      }
    }
    else
    {
      writer.write(line.toString());
      writer.newLine();
    }
  }
}

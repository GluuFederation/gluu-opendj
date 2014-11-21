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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.protocols.asn1;



import static org.opends.server.util.ServerConstants.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;

import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.ByteSequenceReader;



/**
 * This class contains various static factory methods for creating
 * ASN.1 readers and writers.
 *
 * @see ASN1Reader
 * @see ASN1Writer
 * @see ASN1ByteChannelReader
 */
public final class ASN1
{

  /**
   * Gets an ASN.1 reader whose source is the provided byte array and
   * having an unlimited maximum BER element size.
   *
   * @param array
   *          The byte array to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(byte[] array)
  {
    return getReader(array, 0);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided byte array and
   * having a user defined maximum BER element size.
   *
   * @param array
   *          The byte array to use.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(byte[] array, int maxElementSize)
  {
    return getReader(ByteString.wrap(array), maxElementSize);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided byte sequence
   * and having an unlimited maximum BER element size.
   *
   * @param sequence
   *          The byte sequence to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(ByteSequence sequence)
  {
    return getReader(sequence, 0);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided byte sequence
   * and having a user defined maximum BER element size.
   *
   * @param sequence
   *          The byte sequence to use.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(ByteSequence sequence, int maxElementSize)
  {
    return new ASN1ByteSequenceReader(sequence.asReader(), maxElementSize);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided byte sequence reader
   * and having an unlimited maximum BER element size.
   *
   * @param reader
   *          The byte sequence reader to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(ByteSequenceReader reader)
  {
    return getReader(reader, 0);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided byte sequence reader
   * and having a user defined maximum BER element size.
   *
   * @param reader
   *          The byte sequence reader to use.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(ByteSequenceReader reader,
                                     int maxElementSize)
  {
    return new ASN1ByteSequenceReader(reader, maxElementSize);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided input stream
   * and having an unlimited maximum BER element size.
   *
   * @param stream
   *          The input stream to use.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(InputStream stream)
  {
    return getReader(stream, 0);
  }



  /**
   * Gets an ASN.1 reader whose source is the provided input stream
   * and having a user defined maximum BER element size.
   *
   * @param stream
   *          The input stream to use.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   * @return The new ASN.1 reader.
   */
  public static ASN1Reader getReader(InputStream stream, int maxElementSize)
  {
    return new ASN1InputStreamReader(stream, maxElementSize);
  }



  /**
   * Gets an ASN.1 byte channel reader whose source is the provided
   * readable byte channel, uses 4KB buffer, and having an unlimited
   * maximum BER element size.
   *
   * @param channel
   *          The readable byte channel to use.
   * @return The new ASN.1 byte channel reader.
   */
  public static ASN1ByteChannelReader getReader(ReadableByteChannel channel)
  {
    return getReader(channel, 4096, 0);
  }



  /**
   * Gets an ASN.1 byte channel reader whose source is the provided
   * readable byte channel, having a user defined buffer size, and
   * user defined maximum BER element size.
   *
   * @param channel
   *          The readable byte channel to use.
   * @param bufferSize
   *          The buffer size to use when reading from the channel.
   * @param maxElementSize
   *          The maximum BER element size, or <code>0</code> to
   *          indicate that there is no limit.
   * @return The new ASN.1 byte channel reader.
   */
  public static ASN1ByteChannelReader getReader(ReadableByteChannel channel,
      int bufferSize, int maxElementSize)
  {
    return new ASN1ByteChannelReader(channel, bufferSize, maxElementSize);
  }



  /**
   * Gets an ASN.1 writer whose destination is the provided byte
   * string builder.
   *
   * @param builder
   *          The byte string builder to use.
   * @return The new ASN.1 writer.
   */
  public static ASN1Writer getWriter(ByteStringBuilder builder)
  {
    return getWriter(builder, DEFAULT_MAX_INTERNAL_BUFFER_SIZE);
  }



  /**
   * Gets an ASN.1 writer whose destination is the provided byte string builder.
   *
   * @param builder
   *          The byte string builder to use.
   * @param maxInternalBufferSize
   *          The threshold capacity beyond which internal cached buffers used
   *          for encoding and decoding protocol messages will be trimmed after
   *          use.
   * @return The new ASN.1 writer.
   */
  public static ASN1Writer getWriter(ByteStringBuilder builder,
      int maxInternalBufferSize)
  {
    if (maxInternalBufferSize <= 0)
    {
      throw new IllegalArgumentException();
    }
    ByteSequenceOutputStream outputStream = new ByteSequenceOutputStream(
        builder, maxInternalBufferSize);
    return getWriter(outputStream, maxInternalBufferSize);
  }



  /**
   * Gets an ASN.1 writer whose destination is the provided output
   * stream.
   *
   * @param stream
   *          The output stream to use.
   * @return The new ASN.1 writer.
   */
  public static ASN1Writer getWriter(OutputStream stream)
  {
    return getWriter(stream, DEFAULT_MAX_INTERNAL_BUFFER_SIZE);
  }



  /**
   * Gets an ASN.1 writer whose destination is the provided output
   * stream.
   *
   * @param stream
   *          The output stream to use.
   * @param maxInternalBufferSize
   *          The threshold capacity beyond which internal cached buffers used
   *          for encoding and decoding protocol messages will be trimmed after
   *          use.
   * @return The new ASN.1 writer.
   */
  public static ASN1Writer getWriter(OutputStream stream,
      int maxInternalBufferSize)
  {
    if (maxInternalBufferSize <= 0)
    {
      throw new IllegalArgumentException();
    }
    return new ASN1OutputStreamWriter(stream, maxInternalBufferSize);
  }



  // Prevent instantiation.
  private ASN1()
  {
    // Nothing to do.
  }
}

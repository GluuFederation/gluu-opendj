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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.forgerock.util.Reject;

/**
 * A mutable sequence of bytes backed by a byte array.
 */
public final class ByteStringBuilder implements ByteSequence {

    /**
     * Maximum value that can be stored with a compacted representation.
     */
    public static final long COMPACTED_MAX_VALUE = 0xFFFFFFFFFFFFFFL;

    /** Output stream implementation. */
    private final class OutputStreamImpl extends OutputStream {
        @Override
        public void close() {
            // Do nothing.
        }

        @Override
        public void write(final byte[] bytes) {
            append(bytes);
        }

        @Override
        public void write(final byte[] bytes, final int i, final int i1) {
            append(bytes, i, i1);
        }

        @Override
        public void write(final int i) {
            append((byte) (i & 0xFF));
        }
    }

    /**
     * A sub-sequence of the parent byte string builder. The sub-sequence will
     * be robust against all updates to the byte string builder except for
     * invocations of the method {@code clear()}.
     */
    private final class SubSequence implements ByteSequence {

        /** The length of the sub-sequence. */
        private final int subLength;

        /** The offset of the sub-sequence. */
        private final int subOffset;

        /**
         * Creates a new sub-sequence.
         *
         * @param offset
         *            The offset of the sub-sequence.
         * @param length
         *            The length of the sub-sequence.
         */
        private SubSequence(final int offset, final int length) {
            this.subOffset = offset;
            this.subLength = length;
        }

        /** {@inheritDoc} */
        @Override
        public ByteSequenceReader asReader() {
            return new ByteSequenceReader(this);
        }

        /** {@inheritDoc} */
        @Override
        public byte byteAt(final int index) {
            if (index >= subLength || index < 0) {
                throw new IndexOutOfBoundsException();
            }

            // Protect against reallocation: use builder's buffer.
            return buffer[subOffset + index];
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(final byte[] b, final int offset, final int length) {
            ByteString.checkArrayBounds(b, offset, length);

            // Protect against reallocation: use builder's buffer.
            return ByteString.compareTo(buffer, subOffset, subLength, b, offset, length);
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(final ByteSequence o) {
            if (this == o) {
                return 0;
            }

            // Protect against reallocation: use builder's buffer.
            return -o.compareTo(buffer, subOffset, subLength);
        }

        /** {@inheritDoc} */
        @Override
        public byte[] copyTo(final byte[] b) {
            copyTo(b, 0);
            return b;
        }

        /** {@inheritDoc} */
        @Override
        public byte[] copyTo(final byte[] b, final int offset) {
            if (offset < 0) {
                throw new IndexOutOfBoundsException();
            }

            // Protect against reallocation: use builder's buffer.
            System.arraycopy(buffer, subOffset, b, offset, Math.min(subLength, b.length - offset));
            return b;
        }

        /** {@inheritDoc} */
        @Override
        public ByteBuffer copyTo(final ByteBuffer byteBuffer) {
            byteBuffer.put(buffer, subOffset, subLength);
            byteBuffer.flip();
            return byteBuffer;
        }

        /** {@inheritDoc} */
        @Override
        public ByteStringBuilder copyTo(final ByteStringBuilder builder) {
            // Protect against reallocation: use builder's buffer.
            return builder.append(buffer, subOffset, subLength);
        }

        /** {@inheritDoc} */
        @Override
        public boolean copyTo(CharBuffer charBuffer, CharsetDecoder decoder) {
            return ByteString.copyTo(ByteBuffer.wrap(buffer, subOffset, subLength), charBuffer, decoder);
        }

        /** {@inheritDoc} */
        @Override
        public OutputStream copyTo(final OutputStream stream) throws IOException {
            // Protect against reallocation: use builder's buffer.
            stream.write(buffer, subOffset, subLength);
            return stream;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(final byte[] b, final int offset, final int length) {
            ByteString.checkArrayBounds(b, offset, length);

            // Protect against reallocation: use builder's buffer.
            return ByteString.equals(buffer, subOffset, subLength, b, offset, length);
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            } else if (o instanceof ByteSequence) {
                final ByteSequence other = (ByteSequence) o;

                // Protect against reallocation: use builder's buffer.
                return other.equals(buffer, subOffset, subLength);
            } else {
                return false;
            }
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            // Protect against reallocation: use builder's buffer.
            return ByteString.hashCode(buffer, subOffset, subLength);
        }

        @Override
        public boolean isEmpty() {
            return length == 0;
        }

        /** {@inheritDoc} */
        @Override
        public int length() {
            return subLength;
        }

        /** {@inheritDoc} */
        @Override
        public ByteSequence subSequence(final int start, final int end) {
            if (start < 0 || start > end || end > subLength) {
                throw new IndexOutOfBoundsException();
            }

            return new SubSequence(subOffset + start, end - start);
        }

        /** {@inheritDoc} */
        @Override
        public boolean startsWith(ByteSequence prefix) {
            if (prefix == null || prefix.length() > length) {
                return false;
            }
            return prefix.equals(buffer, 0, prefix.length());
        }

        /** {@inheritDoc} */
        @Override
        public String toBase64String() {
            return Base64.encode(this);
        }

        /** {@inheritDoc} */
        @Override
        public byte[] toByteArray() {
            return copyTo(new byte[subLength]);
        }

        /** {@inheritDoc} */
        @Override
        public ByteString toByteString() {
            // Protect against reallocation: use builder's buffer.
            final byte[] b = new byte[subLength];
            System.arraycopy(buffer, subOffset, b, 0, subLength);
            return ByteString.wrap(b);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            // Protect against reallocation: use builder's buffer.
            return ByteString.toString(buffer, subOffset, subLength);
        }
    }

    // These are package private so that compression and crypto
    // functionality may directly access the fields.

    /** The buffer where data is stored. */
    byte[] buffer;

    /** The number of bytes to expose from the buffer. */
    int length;

    /**
     * The lazily allocated output stream view of this builder. Synchronization
     * is not necessary because the stream is stateless and race conditions can
     * be tolerated.
     */
    private OutputStreamImpl os;

    /**
     * Creates a new byte string builder with an initial capacity of 32 bytes.
     */
    public ByteStringBuilder() {
        // Initially create a 32 byte buffer.
        this(32);
    }

    /**
     * Creates a new byte string builder with the specified initial capacity.
     *
     * @param capacity
     *            The initial capacity.
     * @throws IllegalArgumentException
     *             If the {@code capacity} is negative.
     */
    public ByteStringBuilder(final int capacity) {
        Reject.ifFalse(capacity >= 0, "capacity must be >= 0");
        this.buffer = new byte[capacity];
        this.length = 0;
    }

    /**
     * Creates a new byte string builder with the content of the provided
     * ByteSequence. Its capacity is set to the length of the provided
     * ByteSequence.
     *
     * @param bs
     *            The ByteSequence to copy
     */
    public ByteStringBuilder(final ByteSequence bs) {
        this(bs.length());
        bs.copyTo(this);
    }

    /**
     * Appends the provided byte to this byte string builder.
     *
     * @param b
     *            The byte to be appended to this byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(final byte b) {
        ensureAdditionalCapacity(1);
        buffer[length++] = b;
        return this;
    }

    /**
     * Appends the provided byte array to this byte string builder.
     * <p>
     * An invocation of the form:
     *
     * <pre>
     * src.append(bytes)
     * </pre>
     *
     * Behaves in exactly the same way as the invocation:
     *
     * <pre>
     * src.append(bytes, 0, bytes.length);
     * </pre>
     *
     * @param bytes
     *            The byte array to be appended to this byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(final byte[] bytes) {
        return append(bytes, 0, bytes.length);
    }

    /**
     * Appends the provided byte array to this byte string builder.
     *
     * @param bytes
     *            The byte array to be appended to this byte string builder.
     * @param offset
     *            The offset of the byte array to be used; must be non-negative
     *            and no larger than {@code bytes.length} .
     * @param length
     *            The length of the byte array to be used; must be non-negative
     *            and no larger than {@code bytes.length - offset}.
     * @return This byte string builder.
     * @throws IndexOutOfBoundsException
     *             If {@code offset} is negative or if {@code length} is
     *             negative or if {@code offset + length} is greater than
     *             {@code bytes.length}.
     */
    public ByteStringBuilder append(final byte[] bytes, final int offset, final int length) {
        ByteString.checkArrayBounds(bytes, offset, length);

        if (length != 0) {
            ensureAdditionalCapacity(length);
            System.arraycopy(bytes, offset, buffer, this.length, length);
            this.length += length;
        }

        return this;
    }

    /**
     * Appends the provided {@code ByteBuffer} to this byte string builder.
     *
     * @param buffer
     *            The byte buffer to be appended to this byte string builder.
     * @param length
     *            The number of bytes to be appended from {@code buffer}.
     * @return This byte string builder.
     * @throws IndexOutOfBoundsException
     *             If {@code length} is less than zero or greater than
     *             {@code buffer.remaining()}.
     */
    public ByteStringBuilder append(final ByteBuffer buffer, final int length) {
        if (length < 0 || length > buffer.remaining()) {
            throw new IndexOutOfBoundsException();
        }

        if (length != 0) {
            ensureAdditionalCapacity(length);
            buffer.get(this.buffer, this.length, length);
            this.length += length;
        }

        return this;
    }

    /**
     * Appends the provided {@link ByteSequence} to this byte string builder.
     *
     * @param bytes
     *            The byte sequence to be appended to this byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(final ByteSequence bytes) {
        return bytes.copyTo(this);
    }

    /**
     * Appends the provided {@link ByteSequenceReader} to this byte string
     * builder.
     *
     * @param reader
     *            The byte sequence reader to be appended to this byte string
     *            builder.
     * @param length
     *            The number of bytes to be appended from {@code reader}.
     * @return This byte string builder.
     * @throws IndexOutOfBoundsException
     *             If {@code length} is less than zero or greater than
     *             {@code reader.remaining()}.
     */
    public ByteStringBuilder append(final ByteSequenceReader reader, final int length) {
        if (length < 0 || length > reader.remaining()) {
            throw new IndexOutOfBoundsException();
        }

        if (length != 0) {
            ensureAdditionalCapacity(length);
            reader.get(buffer, this.length, length);
            this.length += length;
        }

        return this;
    }

    /**
     * Appends the UTF-8 encoded bytes of the provided char array to this byte
     * string builder.
     *
     * @param chars
     *            The char array whose UTF-8 encoding is to be appended to this
     *            byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(final char[] chars) {
        if (chars == null) {
            return this;
        }

        // Assume that each char is 1 byte
        final int len = chars.length;
        ensureAdditionalCapacity(len);

        for (int i = 0; i < len; i++) {
            final char c = chars[i];
            final byte b = (byte) (c & 0x0000007F);

            if (c == b) {
                buffer[this.length + i] = b;
            } else {
                // There is a multi-byte char. Defer to JDK.
                final Charset utf8 = Charset.forName("UTF-8");
                final ByteBuffer byteBuffer = utf8.encode(CharBuffer.wrap(chars));
                final int remaining = byteBuffer.remaining();
                ensureAdditionalCapacity(remaining - len);
                byteBuffer.get(buffer, this.length, remaining);
                this.length += remaining;
                return this;
            }
        }

        // The 1 byte char assumption was correct
        this.length += len;
        return this;
    }

    /**
     * Appends the provided {@code DataInput} to this byte string
     * builder.
     *
     * @param stream
     *          The data input stream to be appended to this byte string
     *          builder.
     * @param length
     *          The maximum number of bytes to be appended from {@code
     *          input}.
     * @throws IndexOutOfBoundsException
     *           If {@code length} is less than zero.
     * @throws EOFException
     *           If this stream reaches the end before reading all the bytes.
     * @throws IOException
     *           If an I/O error occurs.
     */
    public void append(DataInput stream, int length) throws EOFException, IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }

        ensureAdditionalCapacity(length);
        stream.readFully(buffer, this.length, length);
        this.length += length;
    }

    /**
     * Appends the provided {@code InputStream} to this byte string builder.
     *
     * @param stream
     *            The input stream to be appended to this byte string builder.
     * @param length
     *            The maximum number of bytes to be appended from {@code buffer}
     *            .
     * @return The number of bytes read from the input stream, or {@code -1} if
     *         the end of the input stream has been reached.
     * @throws IndexOutOfBoundsException
     *             If {@code length} is less than zero.
     * @throws IOException
     *             If an I/O error occurs.
     */
    public int append(final InputStream stream, final int length) throws IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }

        ensureAdditionalCapacity(length);
        final int bytesRead = stream.read(buffer, this.length, length);
        if (bytesRead > 0) {
            this.length += bytesRead;
        }

        return bytesRead;
    }

    /**
     * Appends the big-endian encoded bytes of the provided integer to this byte
     * string builder.
     *
     * @param i
     *            The integer whose big-endian encoding is to be appended to
     *            this byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(int i) {
        ensureAdditionalCapacity(4);
        for (int j = length + 3; j >= length; j--) {
            buffer[j] = (byte) (i & 0xFF);
            i >>>= 8;
        }
        length += 4;
        return this;
    }

    /**
     * Appends the big-endian encoded bytes of the provided long to this byte
     * string builder.
     *
     * @param l
     *            The long whose big-endian encoding is to be appended to this
     *            byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(long l) {
        ensureAdditionalCapacity(8);
        for (int i = length + 7; i >= length; i--) {
            buffer[i] = (byte) (l & 0xFF);
            l >>>= 8;
        }
        length += 8;
        return this;
    }

    /**
     * Appends the compact encoded bytes of the provided unsigned long to this byte
     * string builder. This method allows to encode unsigned long up to 56 bits using
     * fewer bytes (from 1 to 8) than append(long). The encoding has the important
     * property that it preserves ordering, so it can be used for keys.
     *
     * @param value
     *            The long whose compact encoding is to be appended to this
     *            byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder appendCompactUnsigned(long value) {
        Reject.ifFalse(value >= 0, "value must be >= 0");

        final int size = getEncodedSize(value);
        ensureAdditionalCapacity(size);
        switch (size) {
        case 1:
            buffer[length++] = (byte) value;
            break;
        case 2:
            buffer[length++] = (byte) ((value >>> 8) | 0x80L);
            buffer[length++] = l2b(value);
            break;
        case 3:
            buffer[length++] = (byte) ((value >>> 16) | 0xc0L);
            buffer[length++] = l2b(value >>> 8);
            buffer[length++] = l2b(value);
            break;
        case 4:
            buffer[length++] = (byte) ((value >>> 24) | 0xe0L);
            buffer[length++] = l2b(value >>> 16);
            buffer[length++] = l2b(value >>> 8);
            buffer[length++] = l2b(value);
            break;
        case 5:
            buffer[length++] = (byte) ((value >>> 32) | 0xf0L);
            buffer[length++] = l2b(value >>> 24);
            buffer[length++] = l2b(value >>> 16);
            buffer[length++] = l2b(value >>> 8);
            buffer[length++] = l2b(value);
            break;
        case 6:
            buffer[length++] = (byte) ((value >>> 40) | 0xf8L);
            buffer[length++] = l2b(value >>> 32);
            buffer[length++] = l2b(value >>> 24);
            buffer[length++] = l2b(value >>> 16);
            buffer[length++] = l2b(value >>> 8);
            buffer[length++] = l2b(value);
            break;
        case 7:
            buffer[length++] = (byte) ((value >>> 48) | 0xfcL);
            buffer[length++] = l2b(value >>> 40);
            buffer[length++] = l2b(value >>> 32);
            buffer[length++] = l2b(value >>> 24);
            buffer[length++] = l2b(value >>> 16);
            buffer[length++] = l2b(value >>> 8);
            buffer[length++] = l2b(value);
            break;
        default:
            buffer[length++] = (byte) 0xfe;
            buffer[length++] = l2b(value >>> 48);
            buffer[length++] = l2b(value >>> 40);
            buffer[length++] = l2b(value >>> 32);
            buffer[length++] = l2b(value >>> 24);
            buffer[length++] = l2b(value >>> 16);
            buffer[length++] = l2b(value >>> 8);
            buffer[length++] = l2b(value);
            break;
        }
        return this;
    }

    private static int getEncodedSize(long value) {
        if (value < 0x80L) {
            return 1;
        } else if (value < 0x4000L) {
            return 2;
        } else if (value < 0x200000L) {
            return 3;
        } else if (value < 0x10000000L) {
            return 4;
        } else if (value < 0x800000000L) {
            return 5;
        } else if (value < 0x40000000000L) {
            return 6;
        } else if (value < 0x2000000000000L) {
            return 7;
        } else if (value < 0x100000000000000L) {
            return 8;
        } else {
            throw new IllegalArgumentException("value out of range: " + value);
        }
    }

    private static byte l2b(long value) {
        return (byte) (value & 0xffL);
    }

    /**
     * Appends the byte string representation of the provided object to this
     * byte string builder. The object is converted to a byte string as follows:
     * <ul>
     * <li>if the object is an instance of {@code ByteSequence} then this method
     * is equivalent to calling {@link #append(ByteSequence)}
     * <li>if the object is a {@code byte[]} then this method is equivalent to
     * calling {@link #append(byte[])}
     * <li>if the object is a {@code char[]} then this method is equivalent to
     * calling {@link #append(char[])}
     * <li>for all other types of object this method is equivalent to calling
     * {@link #append(String)} with the {@code toString()} representation of the
     * provided object.
     * </ul>
     * <b>Note:</b> this method treats {@code Long} and {@code Integer} objects
     * like any other type of {@code Object}. More specifically, the following
     * invocations are not equivalent:
     * <ul>
     * <li>{@code append(0)} is not equivalent to {@code append((Object) 0)}
     * <li>{@code append(0L)} is not equivalent to {@code append((Object) 0L)}
     * </ul>
     *
     * @param o
     *            The object to be appended to this byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(final Object o) {
        if (o == null) {
            return this;
        } else if (o instanceof ByteSequence) {
            return append((ByteSequence) o);
        } else if (o instanceof byte[]) {
            return append((byte[]) o);
        } else if (o instanceof char[]) {
            return append((char[]) o);
        } else {
            return append(o.toString());
        }
    }

    /**
     * Appends the big-endian encoded bytes of the provided short to this byte
     * string builder.
     *
     * @param i
     *            The short whose big-endian encoding is to be appended to this
     *            byte string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(short i) {
        ensureAdditionalCapacity(2);
        for (int j = length + 1; j >= length; j--) {
            buffer[j] = (byte) (i & 0xFF);
            i >>>= 8;
        }
        length += 2;
        return this;
    }

    /**
     * Appends the UTF-8 encoded bytes of the provided string to this byte
     * string builder.
     *
     * @param s
     *            The string whose UTF-8 encoding is to be appended to this byte
     *            string builder.
     * @return This byte string builder.
     */
    public ByteStringBuilder append(final String s) {
        if (s == null) {
            return this;
        }

        // Assume that each char is 1 byte
        final int len = s.length();
        ensureAdditionalCapacity(len);

        for (int i = 0; i < len; i++) {
            final char c = s.charAt(i);
            final byte b = (byte) (c & 0x0000007F);

            if (c == b) {
                buffer[this.length + i] = b;
            } else {
                // There is a multi-byte char. Defer to JDK
                try {
                    return append(s.getBytes("UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    // TODO: I18N
                    throw new RuntimeException("Unable to encode String '" + s + "' to UTF-8 bytes", e);
                }
            }
        }

        // The 1 byte char assumption was correct
        this.length += len;
        return this;
    }

    /**
     * Appends the ASN.1 BER length encoding representation of the provided
     * integer to this byte string builder.
     *
     * @param length
     *            The value to encode using the BER length encoding rules.
     * @return This byte string builder.
     */
    public ByteStringBuilder appendBERLength(final int length) {
        if ((length & 0x0000007F) == length) {
            ensureAdditionalCapacity(1);

            buffer[this.length++] = (byte) (length & 0xFF);
        } else if ((length & 0x000000FF) == length) {
            ensureAdditionalCapacity(2);

            buffer[this.length++] = (byte) 0x81;
            buffer[this.length++] = (byte) (length & 0xFF);
        } else if ((length & 0x0000FFFF) == length) {
            ensureAdditionalCapacity(3);

            buffer[this.length++] = (byte) 0x82;
            buffer[this.length++] = (byte) (length >> 8 & 0xFF);
            buffer[this.length++] = (byte) (length & 0xFF);
        } else if ((length & 0x00FFFFFF) == length) {
            ensureAdditionalCapacity(4);

            buffer[this.length++] = (byte) 0x83;
            buffer[this.length++] = (byte) (length >> 16 & 0xFF);
            buffer[this.length++] = (byte) (length >> 8 & 0xFF);
            buffer[this.length++] = (byte) (length & 0xFF);
        } else {
            ensureAdditionalCapacity(5);

            buffer[this.length++] = (byte) 0x84;
            buffer[this.length++] = (byte) (length >> 24 & 0xFF);
            buffer[this.length++] = (byte) (length >> 16 & 0xFF);
            buffer[this.length++] = (byte) (length >> 8 & 0xFF);
            buffer[this.length++] = (byte) (length & 0xFF);
        }
        return this;
    }

    /**
     * Returns an {@link OutputStream} whose write operations append data to
     * this byte string builder. The returned output stream will never throw an
     * {@link IOException} and its {@link OutputStream#close() close} method
     * does not do anything.
     *
     * @return An {@link OutputStream} whose write operations append data to
     *         this byte string builder.
     */
    public OutputStream asOutputStream() {
        if (os == null) {
            os = new OutputStreamImpl();
        }
        return os;
    }

    /**
     * Returns a {@link ByteSequenceReader} which can be used to incrementally
     * read and decode data from this byte string builder.
     * <p>
     * <b>NOTE:</b> all concurrent updates to this byte string builder are
     * supported with the exception of {@link #clear()}. Any invocations of
     * {@link #clear()} must be accompanied by a subsequent call to
     * {@code ByteSequenceReader.rewind()}.
     *
     * @return The {@link ByteSequenceReader} which can be used to incrementally
     *         read and decode data from this byte string builder.
     * @see #clear()
     */
    @Override
    public ByteSequenceReader asReader() {
        return new ByteSequenceReader(this);
    }

    /** {@inheritDoc} */
    @Override
    public byte byteAt(final int index) {
        if (index >= length || index < 0) {
            throw new IndexOutOfBoundsException();
        }
        return buffer[index];
    }

    /**
     * Returns the current capacity of this byte string builder. The capacity
     * may increase as more data is appended.
     *
     * @return The current capacity of this byte string builder.
     */
    public int capacity() {
        return buffer.length;
    }

    /**
     * Sets the length of this byte string builder to zero.
     * <p>
     * <b>NOTE:</b> if this method is called, then
     * {@code ByteSequenceReader.rewind()} must also be called on any associated
     * byte sequence readers in order for them to remain valid.
     *
     * @return This byte string builder.
     * @see #asReader()
     */
    public ByteStringBuilder clear() {
        length = 0;
        return this;
    }

    /**
     * Sets the length of this byte string builder to zero, and resets the
     * capacity to the specified size if above provided threshold.
     * <p>
     * <b>NOTE:</b> if this method is called, then
     * {@code ByteSequenceReader.rewind()} must also be called on any associated
     * byte sequence readers in order for them to remain valid.
     *
     * @param thresholdCapacity
     *             The threshold capacity triggering a truncate
     * @param newCapacity
     *            The new capacity.
     * @return This byte string builder.
     * @throws IllegalArgumentException
     *             If the {@code newCapacity} is negative or the {@code newCapacity}
     *             is bigger than the {@code thresholdCapacity}.
     * @see #asReader()
     */
    public ByteStringBuilder clearAndTruncate(int thresholdCapacity, int newCapacity) {
        if (newCapacity > thresholdCapacity) {
            throw new IllegalArgumentException("new capacity '" + newCapacity
                    + "' cannot be bigger than threshold capacity '" + thresholdCapacity + "'");
        }
        if (newCapacity < 0) {
            throw new IllegalArgumentException("new capacity '" + newCapacity + "' cannot be negative.");
        }
        if (buffer.length > thresholdCapacity) {
            // garbage collect excessively large buffers
            buffer = new byte[newCapacity];
        }
        length = 0;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final byte[] bytes, final int offset, final int length) {
        ByteString.checkArrayBounds(bytes, offset, length);
        return ByteString.compareTo(this.buffer, 0, this.length, bytes, offset, length);
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final ByteSequence o) {
        if (this == o) {
            return 0;
        }
        return -o.compareTo(buffer, 0, length);
    }

    /** {@inheritDoc} */
    @Override
    public byte[] copyTo(final byte[] bytes) {
        copyTo(bytes, 0);
        return bytes;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] copyTo(final byte[] bytes, final int offset) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException();
        }
        System.arraycopy(buffer, 0, bytes, offset, Math.min(length, bytes.length - offset));
        return bytes;
    }

    /** {@inheritDoc} */
    @Override
    public ByteBuffer copyTo(final ByteBuffer byteBuffer) {
        byteBuffer.put(buffer, 0, length);
        byteBuffer.flip();
        return byteBuffer;
    }

    /** {@inheritDoc} */
    @Override
    public ByteStringBuilder copyTo(final ByteStringBuilder builder) {
        builder.append(buffer, 0, length);
        return builder;
    }

    /** {@inheritDoc} */
    @Override
    public boolean copyTo(CharBuffer charBuffer, CharsetDecoder decoder) {
        return ByteString.copyTo(ByteBuffer.wrap(buffer, 0, length), charBuffer, decoder);
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream copyTo(final OutputStream stream) throws IOException {
        stream.write(buffer, 0, length);
        return stream;
    }

    /**
     * Copies the entire contents of this byte string to the provided
     * {@code WritableByteChannel}.
     *
     * @param channel
     *            The {@code WritableByteChannel} to copy to.
     * @return The number of bytes written, possibly zero
     * @throws IOException
     *             If some other I/O error occurs
     * @see WritableByteChannel#write(java.nio.ByteBuffer)
     */
    public int copyTo(WritableByteChannel channel) throws IOException {
        return channel.write(ByteBuffer.wrap(buffer, 0, length));
    }

    /**
     * Ensures that the specified number of additional bytes will fit in this
     * byte string builder and resizes it if necessary.
     *
     * @param size
     *            The number of additional bytes.
     * @return This byte string builder.
     */
    public ByteStringBuilder ensureAdditionalCapacity(final int size) {
        final int newCount = this.length + size;
        if (newCount > buffer.length) {
            final byte[] newbuffer = new byte[Math.max(buffer.length << 1, newCount)];
            System.arraycopy(buffer, 0, newbuffer, 0, buffer.length);
            buffer = newbuffer;
        }
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final byte[] bytes, final int offset, final int length) {
        ByteString.checkArrayBounds(bytes, offset, length);
        return ByteString.equals(this.buffer, 0, this.length, bytes, offset, length);
    }

    /**
     * Indicates whether the provided object is equal to this byte string
     * builder. In order for it to be considered equal, the provided object must
     * be a byte sequence containing the same bytes in the same order.
     *
     * @param o
     *            The object for which to make the determination.
     * @return {@code true} if the provided object is a byte sequence whose
     *         content is equal to that of this byte string builder, or
     *         {@code false} if not.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof ByteSequence) {
            final ByteSequence other = (ByteSequence) o;
            return other.equals(buffer, 0, length);
        } else {
            return false;
        }
    }

    /**
     * Returns the byte array that backs this byte string builder. Modifications
     * to this byte string builder's content may cause the returned array's
     * content to be modified, and vice versa.
     * <p>
     * Note that the length of the returned array is only guaranteed to be the
     * same as the length of this byte string builder immediately after a call
     * to {@link #trimToSize()}.
     * <p>
     * In addition, subsequent modifications to this byte string builder may
     * cause the backing byte array to be reallocated thus decoupling the
     * returned byte array from this byte string builder.
     *
     * @return The byte array that backs this byte string builder.
     */
    public byte[] getBackingArray() {
        return buffer;
    }

    /**
     * Returns a hash code for this byte string builder. It will be the sum of
     * all of the bytes contained in the byte string builder.
     * <p>
     * <b>NOTE:</b> subsequent changes to this byte string builder will
     * invalidate the returned hash code.
     *
     * @return A hash code for this byte string builder.
     */
    @Override
    public int hashCode() {
        return ByteString.hashCode(buffer, 0, length);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return length == 0;
    }

    /** {@inheritDoc} */
    @Override
    public int length() {
        return length;
    }

    /**
     * Sets the byte value at the specified index.
     * <p>
     * An index ranges from zero to {@code length() - 1}. The first byte value
     * of the sequence is at index zero, the next at index one, and so on, as
     * for array indexing.
     *
     * @param index
     *            The index of the byte to be set.
     * @param b
     *            The byte to set on this byte string builder.
     * @throws IndexOutOfBoundsException
     *             If the index argument is negative or not less than length().
     */
    public void setByte(final int index, final byte b) {
        if (index >= length || index < 0) {
            throw new IndexOutOfBoundsException();
        }
        buffer[index] = b;
    }

    /**
     * Sets the length of this byte string builder.
     * <p>
     * If the <code>newLength</code> argument is less than the current length,
     * the length is changed to the specified length.
     * <p>
     * If the <code>newLength</code> argument is greater than or equal to the
     * current length, then the capacity is increased and sufficient null bytes
     * are appended so that length becomes the <code>newLength</code> argument.
     * <p>
     * The <code>newLength</code> argument must be greater than or equal to
     * <code>0</code>.
     *
     * @param newLength
     *            The new length.
     * @return This byte string builder.
     * @throws IndexOutOfBoundsException
     *             If the <code>newLength</code> argument is negative.
     */
    public ByteStringBuilder setLength(final int newLength) {
        if (newLength < 0) {
            throw new IndexOutOfBoundsException("Negative newLength: " + newLength);
        }

        if (newLength > length) {
            ensureAdditionalCapacity(newLength - length);

            // Pad with zeros.
            for (int i = length; i < newLength; i++) {
                buffer[i] = 0;
            }
        }
        length = newLength;

        return this;
    }

    /**
     * Returns a new byte sequence that is a subsequence of this byte sequence.
     * <p>
     * The subsequence starts with the byte value at the specified {@code start}
     * index and ends with the byte value at index {@code end - 1}. The length
     * (in bytes) of the returned sequence is {@code end - start}, so if
     * {@code start
     * == end} then an empty sequence is returned.
     * <p>
     * <b>NOTE:</b> the returned sub-sequence will be robust against all updates
     * to the byte string builder except for invocations of the method
     * {@link #clear()}. If a permanent immutable byte sequence is required then
     * callers should invoke {@code toByteString()} on the returned byte
     * sequence.
     *
     * @param start
     *            The start index, inclusive.
     * @param end
     *            The end index, exclusive.
     * @return The newly created byte subsequence.
     */
    @Override
    public ByteSequence subSequence(final int start, final int end) {
        if (start < 0 || start > end || end > length) {
            throw new IndexOutOfBoundsException();
        }

        return new SubSequence(start, end - start);
    }

    /** {@inheritDoc} */
    @Override
    public boolean startsWith(ByteSequence prefix) {
        if (prefix == null || prefix.length() > length) {
            return false;
        }
        return prefix.equals(buffer, 0, prefix.length());
    }

    /** {@inheritDoc} */
    @Override
    public String toBase64String() {
        return Base64.encode(this);
    }

    /** {@inheritDoc} */
    @Override
    public byte[] toByteArray() {
        return copyTo(new byte[length]);
    }

    /**
     * Returns the {@link ByteString} representation of this byte string
     * builder. Subsequent changes to this byte string builder will not modify
     * the returned {@link ByteString}.
     *
     * @return The {@link ByteString} representation of this byte sequence.
     */
    @Override
    public ByteString toByteString() {
        final byte[] b = new byte[length];
        System.arraycopy(buffer, 0, b, 0, length);
        return ByteString.wrap(b);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return ByteString.toString(buffer, 0, length);
    }

    /**
     * Attempts to reduce storage used for this byte string builder. If the
     * buffer is larger than necessary to hold its current sequence of bytes,
     * then it may be resized to become more space efficient.
     *
     * @return This byte string builder.
     */
    public ByteStringBuilder trimToSize() {
        if (buffer.length > length) {
            final byte[] newBuffer = new byte[length];
            System.arraycopy(buffer, 0, newBuffer, 0, length);
            buffer = newBuffer;
        }
        return this;
    }
}

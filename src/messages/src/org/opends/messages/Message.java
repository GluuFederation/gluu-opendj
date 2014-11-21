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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.opends.messages;

import java.util.Locale;
import java.util.Formatter;
import java.util.Formattable;
import java.util.IllegalFormatException;

/**
 * Renders sensitive textural strings.  In most cases message are intended
 * to render textural strings in a locale-sensitive manner although this
 * class defines convenience methods for creating uninternationalized
 * <code>Message</code> objects that render the same text regardless of
 * the requested locale.
 *
 * This class implements <code>CharSequence</code> so that messages can
 * be supplied as arguments to other messages.  This way messages can
 * be composed of fragments of other messages if necessary.
 *
 * @see org.opends.messages.MessageDescriptor
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate=true,
    mayExtend=false,
    mayInvoke=true)
public final class Message implements CharSequence, Formattable,
    Comparable<Message> {

  /** Represents an empty message string. */
  public static final Message EMPTY = Message.raw("");

  // Variable used to workaround a bug in AIX Java 1.6
  // TODO: remove this code once the JDK issue referenced in 3077 is closed.
  private final boolean isAIX = isAIX();

  /**
   * Creates an uninternationalized message that will render itself
   * the same way regardless of the locale requested in
   * <code>toString(Locale)</code>.  The message will have a
   * category of <code>Category.USER_DEFINED</code> and a severity
   * of <code>Severity.INFORMATION</code>
   *
   * Note that the types for <code>args</code> must be consistent with any
   * argument specifiers appearing in <code>formatString</code> according
   * to the rules of java.util.Formatter.  A mismatch in type information
   * will cause this message to render without argument substitution.
   *
   * Before using this method you should be sure that the message you
   * are creating is locale sensitive.  If so you should instead create
   * a formal message.
   *
   * @param formatString of the message or the message itself if not
   *        arguments are necessary
   * @param args any arguments for the format string
   * @return a message object that will render the same in all locales;
   *         null if <code>formatString</code> is null
   */
  static public Message raw(CharSequence formatString, Object... args) {
    return raw(Category.USER_DEFINED, Severity.INFORMATION, formatString, args);
  }

  /**
   * Creates an uninternationalized message that will render itself
   * the same way regardless of the locale requested in
   * <code>toString(Locale)</code>.
   *
   * Note that the types for <code>args</code> must be consistent with any
   * argument specifiers appearing in <code>formatString</code> according
   * to the rules of java.util.Formatter.  A mismatch in type information
   * will cause this message to render without argument substitution.
   *
   * Before using this method you should be sure that the message you
   * are creating is locale sensitive.  If so you should instead create
   * a formal message.
   *
   * @param category of this message
   * @param severity of this message
   * @param formatString of the message or the message itself if not
   *        arguments are necessary
   * @param args any arguments for the format string
   * @return a message object that will render the same in all locales;
   *         null if <code>formatString</code> is null
   */
  static public Message raw(Category category, Severity severity,
                            CharSequence formatString, Object... args) {
    Message message = null;
    if (formatString != null) {
      if (args == null || args.length == 0)
      {
        MessageDescriptor.Raw md = new MessageDescriptor.Raw(
            "%s", category, severity);
        message = md.get(formatString);
      }
      else
      {
        MessageDescriptor.Raw md = new MessageDescriptor.Raw(
            formatString, category, severity);
        message = md.get(args);
      }
    }
    return message;
  }

  /**
   * Creates an uninternationalized message from the string representation
   * of an object.
   *
   * Note that the types for <code>args</code> must be consistent with any
   * argument specifiers appearing in <code>formatString</code> according
   * to the rules of java.util.Formatter.  A mismatch in type information
   * will cause this message to render without argument substitution.
   *
   * @param object from which the message will be created
   * @param args for message
   * @return a message object that will render the same in all locales;
   *         null if <code>object</code> is null
   */
  static public Message fromObject(Object object, Object... args) {
    return (object != null) ? raw(object.toString(), args) : null;
  }

  /**
   * Returns the string representation of the message in the default locale.
   * @param message to stringify
   * @return String representation of of <code>message</code> of null if
   *         <code>message</code> is null
   */
  static public String toString(Message message) {
    return message != null ? message.toString() : null;
  }

  /** Descriptor of this message. */
  private final MessageDescriptor descriptor;

  /** Values used to replace argument specifiers in the format string. */
  private final Object[] args;

  /**
   * Gets the string representation of this message.
   * @return String representation of this message
   */
  @Override
  public String toString() {
    return toString(Locale.getDefault());
  }

  /**
   * Gets the string representation of this message appropriate for
   * <code>locale</code>.
   * @param locale for which the string representation
   *        will be returned
   * @return String representation of this message
   */
  public String toString(Locale locale) {
    String s;
    String fmt = descriptor.getFormatString(locale);
    if (descriptor.requiresFormatter()) {
      try {
        // TODO: remove this code once the JDK issue referenced in 3077 is
        // closed.
        if (isAIX)
        {
          // Java 6 in AIX Formatter does not handle properly Formattable
          // arguments; this code is a workaround for the problem.
          boolean changeType = false;
          for (Object o : args)
          {
            if (o instanceof Formattable)
            {
              changeType = true;
              break;
            }
          }
          if (changeType)
          {
            Object[] newArgs = new Object[args.length];
            for (int i=0; i<args.length; i++)
            {
              if (args[i] instanceof Formattable)
              {
                newArgs[i] = args[i].toString();
              }
              else
              {
                newArgs[i] = args[i];
              }
            }
            s = new Formatter(locale).format(locale, fmt, newArgs).toString();
          }
          else
          {
            s = new Formatter(locale).format(locale, fmt, args).toString();
          }
        }
        else
        {
          s = new Formatter(locale).format(locale, fmt, args).toString();
        }
      } catch (IllegalFormatException e) {
        // This should not happend with any of our internal messages.
        // However, this may happen for raw messages that have a
        // mismatch between argument specifier type and argument type.
        s = fmt;
      }
    } else {
      s = fmt;
    }
    if (s == null) s = "";
    return s;
  }

  /**
   * Gets the descriptor that holds descriptive information
   * about this message.
   * @return MessageDescriptor information
   */
  public MessageDescriptor getDescriptor() {
    return this.descriptor;
  }

  /**
   * Returns the length of this message as rendered using the default
   * locale.
   *
   * @return  the number of <code>char</code>s in this message
   */
  public int length() {
    return length(Locale.getDefault());
  }

  /**
   * Returns the byte representation of this messages in the default
   * locale.
   *
   * @return bytes for this message
   */
  public byte[] getBytes() {
    return toString().getBytes();
  }

  /**
   * Returns the <code>char</code> value at the specified index of
   * this message rendered using the default locale.
   *
   * @param   index   the index of the <code>char</code> value to be returned
   *
   * @return  the specified <code>char</code> value
   *
   * @throws  IndexOutOfBoundsException
   *          if the <tt>index</tt> argument is negative or not less than
   *          <tt>length()</tt>
   */
  public char charAt(int index) throws IndexOutOfBoundsException {
    return charAt(Locale.getDefault(), index);
  }

  /**
   * Returns a new <code>CharSequence</code> that is a subsequence
   * of this message rendered using the default locale.
   * The subsequence starts with the <code>char</code>
   * value at the specified index and ends with the <code>char</code>
   * value at index <tt>end - 1</tt>.  The length (in <code>char</code>s)
   * of the returned sequence is <tt>end - start</tt>, so if
   * <tt>start == end</tt> then an empty sequence is returned.
   *
   * @param   start   the start index, inclusive
   * @param   end     the end index, exclusive
   *
   * @return  the specified subsequence
   *
   * @throws  IndexOutOfBoundsException
   *          if <tt>start</tt> or <tt>end</tt> are negative,
   *          if <tt>end</tt> is greater than <tt>length()</tt>,
   *          or if <tt>start</tt> is greater than <tt>end</tt>
   */
  public CharSequence subSequence(int start, int end)
          throws IndexOutOfBoundsException
  {
    return subSequence(Locale.getDefault(), start, end);
  }

  /**
   * Returns the length of this message as rendered using a specific
   * locale.
   *
   * @param   locale for which the rendering of this message will be
   *          used in determining the length
   * @return  the number of <code>char</code>s in this message
   */
  public int length(Locale locale) {
    return toString(locale).length();
  }

  /**
   * Returns the <code>char</code> value at the specified index of
   * this message rendered using a specific.
   *
   * @param   locale for which the rendering of this message will be
   *          used in determining the character
   * @param   index   the index of the <code>char</code> value to be returned
   *
   * @return  the specified <code>char</code> value
   *
   * @throws  IndexOutOfBoundsException
   *          if the <tt>index</tt> argument is negative or not less than
   *          <tt>length()</tt>
   */
  public char charAt(Locale locale, int index)
          throws IndexOutOfBoundsException
  {
    return toString(locale).charAt(index);
  }

  /**
   * Returns a new <code>CharSequence</code> that is a subsequence
   * of this message rendered using a specific locale.
   * The subsequence starts with the <code>char</code>
   * value at the specified index and ends with the <code>char</code>
   * value at index <tt>end - 1</tt>.  The length (in <code>char</code>s)
   * of the returned sequence is <tt>end - start</tt>, so if
   * <tt>start == end</tt> then an empty sequence is returned.
   *
   * @param   locale for which the rendering of this message will be
   *          used in determining the character
   * @param   start   the start index, inclusive
   * @param   end     the end index, exclusive
   *
   * @return  the specified subsequence
   *
   * @throws  IndexOutOfBoundsException
   *          if <tt>start</tt> or <tt>end</tt> are negative,
   *          if <tt>end</tt> is greater than <tt>length()</tt>,
   *          or if <tt>start</tt> is greater than <tt>end</tt>
   */
  public CharSequence subSequence(Locale locale, int start, int end)
    throws IndexOutOfBoundsException
  {
    return toString(locale).subSequence(start, end);
  }

  /**
   * Formats the object using the provided {@link Formatter formatter}.
   *
   * @param  formatter
   *         The {@link Formatter formatter}.
   *
   * @param  flags
   *         The flags modify the output format.  The value is interpreted as
   *         a bitmask.  Any combination of the following flags may be set:
   *         {@link java.util.FormattableFlags#LEFT_JUSTIFY}, {@link
   *         java.util.FormattableFlags#UPPERCASE}, and {@link
   *         java.util.FormattableFlags#ALTERNATE}.  If no flags are set, the
   *         default formatting of the implementing class will apply.
   *
   * @param  width
   *         The minimum number of characters to be written to the output.
   *         If the length of the converted value is less than the
   *         <tt>width</tt> then the output will be padded by
   *         <tt>'&nbsp;&nbsp;'</tt> until the total number of characters
   *         equals width.  The padding is at the beginning by default.  If
   *         the {@link java.util.FormattableFlags#LEFT_JUSTIFY} flag is set
   *         then the padding will be at the end.  If <tt>width</tt> is
   *         <tt>-1</tt> then there is no minimum.
   *
   * @param  precision
   *         The maximum number of characters to be written to the output.
   *         The precision is applied before the width, thus the output will
   *         be truncated to <tt>precision</tt> characters even if the
   *         <tt>width</tt> is greater than the <tt>precision</tt>.  If
   *         <tt>precision</tt> is <tt>-1</tt> then there is no explicit
   *         limit on the number of characters.
   *
   * @throws  IllegalFormatException
   *          If any of the parameters are invalid.  For specification of all
   *          possible formatting errors, see the <a
   *          href="../util/Formatter.html#detail">Details</a> section of the
   *          formatter class specification.
   */
  public void formatTo(Formatter formatter, int flags,
                       int width, int precision)
    throws IllegalFormatException
  {
    // Ignores flags, width and precission for now.
    // see javadoc for Formattable
    Locale l = formatter.locale();
    formatter.format(l, descriptor.getFormatString(l), args);
  }


  /**
   * Creates a parameterized instance.  See the class header
   * for instructions on how to create messages outside this
   * package.
   * @param descriptor for this message
   * @param args arguments for replacing specifiers in the
   *        message's format string
   */
  Message(MessageDescriptor descriptor, Object... args) {
    this.descriptor = descriptor;
    this.args = args;
  }

  /**
   * Compares this object with the specified object for order.  Returns a
   * negative integer, zero, or a positive integer as this object is less
   * than, equal to, or greater than the specified object.
   *
   * @param   o the object to be compared.
   * @return  a negative integer, zero, or a positive integer as this object
   *          is less than, equal to, or greater than the specified object.
   */
  public int compareTo(Message o) {
    return toString().compareTo(o.toString());
  }

  /**
   * Indicates whether some other message is "equal to" this one.  Messages
   * are considered equal if their string representation in the default
   * locale are equal.
   *
   * @param   o   the reference object with which to compare.
   * @return  <code>true</code> if this object is the same as the obj
   *          argument; <code>false</code> otherwise.
   * @see     #hashCode()
   * @see     java.util.Hashtable
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Message message = (Message) o;

    return toString().equals(message.toString());
  }

  /**
   * Returns a hash code value for the object.
   *
   * @return  a hash code value for this object.
   * @see     java.lang.Object#equals(java.lang.Object)
   * @see     java.util.Hashtable
   */
  @Override
  public int hashCode() {
    int result;
    result = 31 * toString().hashCode();
    return result;
  }


  // TODO: remove this code once the JDK issue referenced in 3077 is closed.
  /**
   * Returns whether we are running on AIX or not.
   * @return <CODE>true</CODE> if we are running on AIX and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isAIX()
  {
    return "aix".equalsIgnoreCase(System.getProperty("os.name"));
  }

}

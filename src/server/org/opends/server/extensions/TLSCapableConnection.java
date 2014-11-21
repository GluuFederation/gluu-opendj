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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.extensions;
import org.opends.messages.MessageBuilder;



/**
 * This interface defines a set of methods that must be implemented by a class
 * (expected to be a client connection) that can dynamically enable and disable
 * the TLS connection security provider.  This will be used by the StartTLS
 * extended operation handler to perform the core function of enabling TLS on an
 * established connection.
 */
public interface TLSCapableConnection
{
  /**
   * Prepares this connection for using TLS and returns whether TLS protection
   * is actually available for the underlying client connection. If there is any
   * reason that TLS protection cannot be enabled on this client connection,
   * then it should be appended to the provided buffer.
   *
   * @param  unavailableReason  The buffer used to hold the reason that TLS is
   *                            not available on the underlying client
   *                            connection.
   *
   * @return  <CODE>true</CODE> if TLS is available on the underlying client
   *          connection, or <CODE>false</CODE> if it is not.
   */
  public boolean prepareTLS(MessageBuilder unavailableReason);
}


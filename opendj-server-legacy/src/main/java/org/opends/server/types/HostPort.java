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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import static org.opends.messages.ReplicationMessages.*;

/**
 * This class defines a data structure that combines an address and port number,
 * as may be used to accept a connection from or initiate a connection to a
 * remote system.
 * <p>
 * Due to the possibility of live network configuration changes, instances of
 * this class are not intended for caching and should be rebuilt on demand.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class HostPort
{

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Constant that represents the local host. */
  private static final String LOCALHOST = "localhost";

  /**
   * The wildcard address allows to instruct a server to
   * "listen to all addresses".
   *
   * @see InetSocketAddress#InetSocketAddress(int) InetSocketAddress javadoc
   */
  public static final String WILDCARD_ADDRESS = "0.0.0.0";



  /**
   * The supplied host for this object.
   * <p>
   * Keeping the supplied host name allows to rebuild the HostPort object in
   * case the network configuration changed on the current machine.
   */
  private final String host;

  /**
   * The normalized host for this object.
   * <p>
   * Normalization consists of:
   * <ul>
   * <li>convert all local addresses to "localhost"</li>
   * <li>convert remote host name / addresses to the equivalent IP address</li>
   * </ul>
   */
  private final String normalizedHost;

  /** The port for this object. */
  private final int port;




  /** Time-stamp acts as memory barrier for networkInterfaces. */
  private static final long CACHED_LOCAL_ADDRESSES_TIMEOUT_MS = 30 * 1000;
  private static volatile long localAddressesTimeStamp;
  private static Set<InetAddress> localAddresses = new HashSet<>();

  /**
   * Returns {@code true} if the provided {@code InetAddress} represents the
   * address of one of the interfaces on the current host machine.
   *
   * @param address
   *          The network address.
   * @return {@code true} if the provided {@code InetAddress} represents the
   *         address of one of the interfaces on the current host machine.
   */
  public static boolean isLocalAddress(InetAddress address)
  {
    return address.isLoopbackAddress() || getLocalAddresses().contains(address);
  }

  /**
   * Returns a Set of all the local addresses as detected by the Java
   * environment from the operating system configuration.
   * <p>
   * The local addresses are temporarily cached to balance the cost of this
   * expensive computation vs. refreshing the data that can be changed while the
   * system is running.
   *
   * @return a Set containing all the local addresses
   */
  private static Set<InetAddress> getLocalAddresses()
  {
    final long currentTimeStamp = System.currentTimeMillis();
    if (localAddressesTimeStamp
        < (currentTimeStamp - CACHED_LOCAL_ADDRESSES_TIMEOUT_MS))
    {
      // Refresh the cache.
      try
      {
        final Enumeration<NetworkInterface> i =
            NetworkInterface.getNetworkInterfaces();
        if (i != null) {
          final Set<InetAddress> newLocalAddresses = new HashSet<>();
          while (i.hasMoreElements())
          {
            NetworkInterface n = i.nextElement();
            Enumeration<InetAddress> j = n.getInetAddresses();
            while (j.hasMoreElements())
            {
              newLocalAddresses.add(j.nextElement());
            }
          }
          localAddresses = newLocalAddresses;
        }
      }
      catch (SocketException e)
      {
        // Ignore and keep the old set.
        logger.traceException(e);
      }
      localAddressesTimeStamp = currentTimeStamp; // Publishes.
    }
    return localAddresses;
  }

  /**
   * Returns a a new HostPort for all addresses, also known as a wildcard
   * address.
   *
   * @param port
   *          The port number for the new {@code HostPort} object.
   * @return a newly constructed HostPort object
   */
  public static HostPort allAddresses(int port)
  {
    return new HostPort(port);
  }

  /**
   * Builds a new instance of {@link HostPort} representing the local machine
   * with the supplied port.
   *
   * @param port
   *          the port to use when building the new {@link HostPort} object
   * @return a new {@link HostPort} instance representing the local machine with
   *         the supplied port.
   */
  public static HostPort localAddress(int port)
  {
    return new HostPort(LOCALHOST, port);
  }

  /**
   * Creates a new {@code HostPort} object with the specified port number but no
   * host.
   *
   * @param port
   *          The port number for this {@code HostPort} object.
   */
  private HostPort(int port)
  {
    this.host = null;
    this.normalizedHost = null;
    this.port = normalizePort(port);
  }



  /**
   * Creates a new {@code HostPort} object with the specified port
   * number but no explicit host.
   *
   * @param  host  The host address or name for this {@code HostPort}
   *               object, or {@code null} if there is none.
   * @param  port  The port number for this {@code HostPort} object.
   */
  public HostPort(String host, int port)
  {
    this.host = removeExtraChars(host);
    this.normalizedHost = normalizeHost(this.host);
    this.port = normalizePort(port);
  }



  /**
   * Creates a new {@code HostPort} object by parsing the supplied
   * "hostName:port" String URL. This method also accepts IPV6 style
   * "[hostAddress]:port" String URLs.
   *
   * @param hostPort
   *          a String representing the URL made of a host and a port.
   * @return a new {@link HostPort} built from the supplied string.
   * @throws NumberFormatException
   *           If the "port" in the supplied string cannot be converted to an
   *           int
   * @throws IllegalArgumentException
   *           if no port could be found in the supplied string, or if the port
   *           is not a valid port number
   */
  public static HostPort valueOf(String hostPort) throws NumberFormatException,
      IllegalArgumentException
  {
    final int sepIndex = hostPort.lastIndexOf(':');
    if ((hostPort.charAt(0) == '['
        && hostPort.charAt(hostPort.length() - 1) == ']')
        || sepIndex == -1)
    {
      throw new IllegalArgumentException(
          "Invalid host/port string: no network port was provided in '"
              + hostPort + "'");
    }
    else if (sepIndex == 0)
    {
      throw new IllegalArgumentException(
          "Invalid host/port string: no host name was provided in '" + hostPort
              + "'");
    }
    else if (hostPort.lastIndexOf(':', sepIndex - 1) != -1
        && (hostPort.charAt(0) != '[' || hostPort.charAt(sepIndex - 1) != ']'))
    {
      throw new IllegalArgumentException(
          "Invalid host/port string: Suspected IPv6 address provided in '"
              + hostPort + "'. The only allowed format for providing IPv6 "
              + "addresses is '[IPv6 address]:port'");
    }
    String host = hostPort.substring(0, sepIndex);
    int port = Integer.parseInt(hostPort.substring(sepIndex + 1));
    return new HostPort(host, port);
  }

  /**
   * Removes extra characters from the host name: surrounding square brackets
   * for IPv6 addresses.
   *
   * @param host
   *          the host name to clean
   * @return the cleaned up host name
   */
  private String removeExtraChars(String host)
  {
    final int startsWith = host.indexOf("[");
    if (startsWith == -1)
    {
      return host;
    }
    return host.substring(1, host.length() - 1);
  }

  /**
   * Returns a normalized String representation of the supplied host.
   *
   * @param host
   *          the host address to normalize
   * @return a normalized String representation of the supplied host.
   * @see #normalizedHost what host normalization covers
   */
  private String normalizeHost(String host)
  {
    if (LOCALHOST.equals(host))
    { // it is already normalized
      return LOCALHOST;
    }

    try
    {
      final InetAddress inetAddress = InetAddress.getByName(host);
      if (isLocalAddress(inetAddress))
      {
        // normalize to localhost for easier identification.
        return LOCALHOST;
      }
      // else normalize to IP address for easier identification.
      // FIXME, this does not fix the multi homing issue where a single machine
      // has several IP addresses
      return inetAddress.getHostAddress();
    }
    catch (UnknownHostException e)
    {
      // We could not resolve this host name, default to the provided host name
      logger.error(ERR_COULD_NOT_SOLVE_HOSTNAME, host);
      return host;
    }
  }

  /**
   * Ensures the supplied port number is valid.
   *
   * @param port
   *          the port number to validate
   * @return the port number if valid
   */
  private int normalizePort(int port)
  {
    if (1 <= port && port <= 65535)
    {
      return port;
    }
    throw new IllegalArgumentException("Invalid network port provided: " + port
        + " is not included in the [1, 65535] range.");
  }

  /**
   * Retrieves the host for this {@code HostPort} object.
   *
   * @return  The host for this {@code HostPort} object, or
   *          {@code null} if none was provided.
   */
  public String getHost()
  {
    return host;
  }



  /**
   * Retrieves the port number for this {@code HostPort} object.
   *
   * @return The valid port number in the [1, 65535] range for this
   *         {@code HostPort} object.
   */
  public int getPort()
  {
    return port;
  }

  /**
   * Whether the current object represents a local address.
   *
   * @return true if this represents a local address, false otherwise.
   */
  public boolean isLocalAddress()
  {
    return LOCALHOST.equals(this.normalizedHost);
  }

  /**
   * Converts the current object to an equivalent {@link InetSocketAddress}
   * object.
   *
   * @return a {@link InetSocketAddress} equivalent of the current object.
   * @throws UnknownHostException
   *           If the current host name cannot be resolved to an
   *           {@link InetAddress}
   */
  public InetSocketAddress toInetSocketAddress() throws UnknownHostException
  {
    return new InetSocketAddress(InetAddress.getByName(getHost()), getPort());
  }

  /**
   * Returns a string representation of this {@code HostPort} object. It will be
   * the host element (or nothing if no host was given) followed by a colon and
   * the port number.
   *
   * @return A string representation of this {@code HostPort} object.
   */
  @Override
  public String toString()
  {
    return toString(host);
  }

  /**
   * Returns a normalized string representation of this {@code HostPort} object.
   *
   * @return A string representation of this {@code HostPort} object.
   * @see #normalizedHost what host normalization covers
   */
  private String toNormalizedString()
  {
    return toString(normalizedHost);
  }

  /**
   * Inner computation for #toString() and {@link #toNormalizedString()}.
   *
   * @param hostName
   *          the hostName to use for this computation
   * @return the String representation fo4r this object
   */
  private String toString(String hostName)
  {
    if (hostName != null)
    {
      if (hostName.contains(":"))
      {
        return "[" + hostName + "]:" + port;
      }
      return hostName + ":" + port;
    }
    return WILDCARD_ADDRESS + ":" + port;
  }

  /**
   * Checks whether the supplied HostPort is an equivalent to the current
   * HostPort.
   *
   * @param other
   *          the HostPort to compare to "this"
   * @return true if the HostPorts are equivalent, false otherwise. False is
   *         also return if calling {@link InetAddress#getAllByName(String)}
   *         throws an UnknownHostException.
   */
  public boolean isEquivalentTo(final HostPort other)
  {
    try
    {
      // Get and compare ports of RS1 and RS2
      if (getPort() != other.getPort())
      {
        return false;
      }

      // Get and compare addresses of RS1 and RS2
      // Normalize local addresses to null for fast comparison.
      final InetAddress[] thisAddresses =
          isLocalAddress() ? null : InetAddress.getAllByName(getHost());
      final InetAddress[] otherAddresses =
          other.isLocalAddress() ? null : InetAddress.getAllByName(other
              .getHost());

      // Now compare addresses, if at least one match, this is the same server.
      if (thisAddresses == null && otherAddresses == null)
      {
        // Both local addresses.
        return true;
      }
      else if (thisAddresses == null || otherAddresses == null)
      {
        // One local address and one non-local.
        return false;
      }

      // Both non-local addresses: check for overlap.
      for (InetAddress thisAddress : thisAddresses)
      {
        for (InetAddress otherAddress : otherAddresses)
        {
          if (thisAddress.equals(otherAddress))
          {
            return true;
          }
        }
      }
      return false;
    }
    catch (UnknownHostException ex)
    {
      // Unknown RS: should not happen
      return false;
    }
  }

  /**
   * Returns {@code true} if the provided Object is a HostPort object with the
   * same host name and port than this HostPort object.
   *
   * @param obj
   *          the reference object with which to compare.
   * @return {@code true} if this object is the same as the obj argument;
   *         {@code false} otherwise.
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (obj == this)
    {
      return true;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }

    HostPort other = (HostPort) obj;
    if (normalizedHost == null)
    {
      if (other.normalizedHost != null)
      {
        return false;
      }
    }
    else if (!normalizedHost.equals(other.normalizedHost))
    {
      return false;
    }

    return port == other.port;
  }

  /**
   * Retrieves a hash code for this HostPort object.
   *
   * @return A hash code for this HostPort object.
   */
  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result
            + ((normalizedHost == null) ? 0 : normalizedHost.hashCode());
    result = prime * result + port;
    return result;
  }

}

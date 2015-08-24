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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.examples;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPListener;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandlerFactory;
import org.forgerock.opendj.ldap.RoundRobinLoadBalancingAlgorithm;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.requests.Requests;

/**
 * An LDAP load balancing proxy which forwards requests to one or more remote
 * Directory Servers. This is implementation is very simple and is only intended
 * as an example:
 * <ul>
 * <li>It does not support SSL connections
 * <li>It does not support StartTLS
 * <li>It does not support Abandon or Cancel requests
 * <li>Very basic authentication and authorization support.
 * </ul>
 * This example takes the following command line parameters:
 *
 * <pre>
 *     {@code <listenAddress> <listenPort> <proxyDN> <proxyPassword> <remoteAddress1> <remotePort1>
 *      [<remoteAddress2> <remotePort2> ...]}
 * </pre>
 */
public final class Proxy {
    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: listen address, listen port,
     *            remote address1, remote port1, remote address2, remote port2,
     *            ...
     */
    public static void main(final String[] args) {
        if (args.length < 6 || args.length % 2 != 0) {
            System.err.println("Usage: listenAddress listenPort "
                    + "proxyDN proxyPassword remoteAddress1 remotePort1 "
                    + "remoteAddress2 remotePort2 ...");
            System.exit(1);
        }

        // Parse command line arguments.
        final String localAddress = args[0];
        final int localPort = Integer.parseInt(args[1]);

        final String proxyDN = args[2];
        final String proxyPassword = args[3];

        // Create load balancer.
        // --- JCite pools ---
        final List<ConnectionFactory> factories = new LinkedList<>();
        final List<ConnectionFactory> bindFactories = new LinkedList<>();
        for (int i = 4; i < args.length; i += 2) {
            final String remoteAddress = args[i];
            final int remotePort = Integer.parseInt(args[i + 1]);

            factories.add(Connections.newCachedConnectionPool(Connections
                    .newAuthenticatedConnectionFactory(Connections
                            .newHeartBeatConnectionFactory(new LDAPConnectionFactory(remoteAddress,
                                    remotePort)), Requests.newSimpleBindRequest(proxyDN,
                            proxyPassword.toCharArray()))));
            bindFactories.add(Connections.newCachedConnectionPool(Connections
                    .newHeartBeatConnectionFactory(new LDAPConnectionFactory(remoteAddress,
                            remotePort))));
        }
        // --- JCite pools ---

        // --- JCite load balancer ---
        final RoundRobinLoadBalancingAlgorithm algorithm =
                new RoundRobinLoadBalancingAlgorithm(factories);
        final RoundRobinLoadBalancingAlgorithm bindAlgorithm =
                new RoundRobinLoadBalancingAlgorithm(bindFactories);
        final ConnectionFactory factory = Connections.newLoadBalancer(algorithm);
        final ConnectionFactory bindFactory = Connections.newLoadBalancer(bindAlgorithm);
        // --- JCite load balancer ---

        // --- JCite backend ---
        /*
         * Create a server connection adapter which will create a new proxy
         * backend for each inbound client connection. This is required because
         * we need to maintain authorization state between client requests.
         */
        final RequestHandlerFactory<LDAPClientContext, RequestContext> proxyFactory =
                new RequestHandlerFactory<LDAPClientContext, RequestContext>() {
                    @Override
                    public ProxyBackend handleAccept(LDAPClientContext clientContext)
                            throws LdapException {
                        return new ProxyBackend(factory, bindFactory);
                    }
                };
        final ServerConnectionFactory<LDAPClientContext, Integer> connectionHandler =
                Connections.newServerConnectionFactory(proxyFactory);
        // --- JCite backend ---

        // --- JCite listener ---
        // Create listener.
        final LDAPListenerOptions options = new LDAPListenerOptions().setBacklog(4096);
        LDAPListener listener = null;
        try {
            listener = new LDAPListener(localAddress, localPort, connectionHandler, options);
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } catch (final IOException e) {
            System.out.println("Error listening on " + localAddress + ":" + localPort);
            e.printStackTrace();
        } finally {
            if (listener != null) {
                listener.close();
            }
        }
        // --- JCite listener ---
    }

    private Proxy() {
        // Not used.
    }
}

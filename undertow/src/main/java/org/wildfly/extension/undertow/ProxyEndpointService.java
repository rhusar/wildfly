/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.ClientMapping;
import org.jboss.as.network.SocketBinding;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.undertow.logging.UndertowLogger;

/**
 * Provides the endpoint, i.e. the scheme, host, and port, via which a given server can be reached by its peers, for
 * publication to the routing registry of the cluster.
 * <p>
 * The advertised address is derived from the {@code client-mappings} of the socket binding of the listener, rather than
 * from its bind address, since the bind address of a member is not generally reachable by its peers under NAT, cloud,
 * container, or multi-homed networking.
 * As is the case for the JGroups transport, a client mapping cannot generally be selected based on the address of the
 * requesting member, and thus the first mapping is used.
 *
 * @author Radoslav Husar
 */
public class ProxyEndpointService implements Service<URI> {

    private final Consumer<URI> endpoint;
    private final Supplier<Server> server;

    private volatile URI uri;

    ProxyEndpointService(Consumer<URI> endpoint, Supplier<Server> server) {
        this.endpoint = endpoint;
        this.server = server;
    }

    @Override
    public void start(StartContext context) throws StartException {
        Server server = this.server.get();
        UndertowListener listener = findListener(server);
        if (listener == null) {
            throw UndertowLogger.ROOT_LOGGER.noProxyEndpointListener(server.getName());
        }

        SocketBinding binding = listener.getSocketBinding();
        List<ClientMapping> mappings = binding.getClientMappings();
        String host;
        int port;
        if (!mappings.isEmpty()) {
            // A client mapping cannot be selected based on the address of the requesting member, so use the first one
            ClientMapping mapping = mappings.get(0);
            host = mapping.getDestinationAddress();
            port = mapping.getDestinationPort();
        } else {
            InetSocketAddress address = binding.getSocketAddress();
            if (address.getAddress().isAnyLocalAddress()) {
                UndertowLogger.ROOT_LOGGER.wildcardProxyEndpointAddress(binding.getName(), server.getName());
            }
            host = address.getAddress().getHostAddress();
            port = address.getPort();
        }

        try {
            this.uri = new URI(listener.getProtocol(), null, host, port, null, null, null);
        } catch (URISyntaxException e) {
            throw new StartException(e);
        }

        UndertowLogger.ROOT_LOGGER.proxyEndpoint(server.getName(), this.uri);
        this.endpoint.accept(this.uri);
    }

    @Override
    public void stop(StopContext context) {
        this.endpoint.accept(null);
        this.uri = null;
    }

    @Override
    public URI getValue() {
        return this.uri;
    }

    /**
     * Returns the listener of the specified server whose address is published to the peers of this member.
     * <p>
     * Only a non-secure HTTP listener is eligible: proxying to a secure listener would additionally require the trust
     * configuration of that listener, which a listener does not currently expose. AJP listeners are never eligible.
     */
    private static UndertowListener findListener(Server server) {
        for (UndertowListener listener : server.getListeners()) {
            // TODO why hard code HTTP?!
            if (HttpListenerService.PROTOCOL.equals(listener.getProtocol()) && !listener.isSecure()) {
                return listener;
            }
        }
        return null;
    }
}

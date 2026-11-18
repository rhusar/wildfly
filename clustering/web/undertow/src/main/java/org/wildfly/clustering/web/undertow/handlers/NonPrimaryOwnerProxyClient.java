/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.proxy.ConnectionPoolErrorHandler;
import io.undertow.server.handlers.proxy.ConnectionPoolManager;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

import org.wildfly.clustering.server.Registration;
import org.wildfly.clustering.server.registry.RegistryListener;
import org.wildfly.clustering.web.service.routing.RouteEndpointRegistry;
import org.wildfly.extension.undertow.ReverseProxyConfiguration;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.ssl.XnioSsl;

/**
 * A proxy client whose targets are the peer members of a group, as published to its routing registry.
 * A single instance is shared by every deployment of a given server that references the same proxy configuration.
 * <p>
 * The connection pools of each peer are maintained by a listener of that registry, so that resolving the target of a
 * request never requires a lookup of the registry itself.
 * The target of a given request is determined by the caller, via {@link #findTarget(String, String)}, and attached to
 * the request via {@link #TARGET}, from which it is read by {@link #findTarget(HttpServerExchange)}.
 * <p>
 * The local member is never a target, i.e. no connection pool to the local member is ever created.
 * A request is only proxied via HTTPS if an SSL context was configured, and a request received via HTTPS is never
 * proxied via an insecure scheme.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyClient implements ProxyClient, RegistryListener<String, Map<String, List<InetSocketAddress>>>, AutoCloseable {

    /**
     * The target to which a request is proxied.
     */
    static final AttachmentKey<ProxyTarget> TARGET = AttachmentKey.create(ProxyTarget.class);

    static final String HTTPS = "https";

    /**
     * The schemes via which a request may be proxied to a peer, without an SSL context.
     */
    static final Set<String> INSECURE_SCHEMES = Set.of("ajp", "http");

    /**
     * The schemes via which a request may be proxied to a peer, given an SSL context.
     */
    static final Set<String> SCHEMES = Set.of("ajp", "http", HTTPS);

    private final RouteEndpointRegistry registry;
    private final String localRoute;
    private final ReverseProxyConfiguration configuration;
    private final XnioSsl ssl;
    private final Set<String> schemes;
    private final UndertowClient client;
    private final AttachmentKey<ExclusiveConnectionHolder> exclusiveConnectionKey = AttachmentKey.create(ExclusiveConnectionHolder.class);
    // Targets per scheme, keyed by route
    private final Map<String, Map<String, Target>> peers = new ConcurrentHashMap<>();

    private volatile Registration registration;

    /**
     * Creates a proxy client to the peers of the specified registry.
     * @param registry the registry of the endpoints of each member, keyed by route
     * @param localRoute the route of the local member, which is never a target
     * @param configuration the configuration of the referenced reverse-proxy handler
     * @param ssl the SSL configuration used to proxy a request via HTTPS, or null, if requests are never proxied via HTTPS
     */
    public NonPrimaryOwnerProxyClient(RouteEndpointRegistry registry, String localRoute, ReverseProxyConfiguration configuration, XnioSsl ssl) {
        this(registry, localRoute, configuration, ssl, UndertowClient.getInstance());
    }

    NonPrimaryOwnerProxyClient(RouteEndpointRegistry registry, String localRoute, ReverseProxyConfiguration configuration, XnioSsl ssl, UndertowClient client) {
        this.registry = registry;
        this.localRoute = localRoute;
        this.configuration = configuration;
        this.ssl = ssl;
        this.schemes = (ssl != null) ? SCHEMES : INSECURE_SCHEMES;
        this.client = client;
    }

    /**
     * Starts listening for changes to the routing registry, and creates the connection pools of its current members.
     */
    public void start() {
        // Register before reading the current entries, so that no subsequent change is missed
        this.registration = this.registry.register(this);
        this.added(this.registry.getEndpoints());
    }

    @Override
    public void close() {
        Registration registration = this.registration;
        if (registration != null) {
            registration.close();
            this.registration = null;
        }
        for (String route : List.copyOf(this.peers.keySet())) {
            close(this.peers.remove(route));
        }
    }

    @Override
    public void added(Map<String, Map<String, List<InetSocketAddress>>> entries) {
        this.updated(entries);
    }

    @Override
    public void updated(Map<String, Map<String, List<InetSocketAddress>>> entries) {
        for (Map.Entry<String, Map<String, List<InetSocketAddress>>> entry : entries.entrySet()) {
            String route = entry.getKey();
            // Never proxy to the local member
            if (route.equals(this.localRoute)) continue;
            Map<String, URI> uris = this.uris(entry.getValue());
            this.peers.compute(route, (key, existing) -> {
                // Retain the connection pools of a peer whose addresses did not change
                if ((existing != null) && uris.equals(targetURIs(existing))) return existing;
                close(existing);
                return !uris.isEmpty() ? this.createTargets(uris) : null;
            });
        }
    }

    @Override
    public void removed(Map<String, Map<String, List<InetSocketAddress>>> entries) {
        for (String route : entries.keySet()) {
            close(this.peers.remove(route));
        }
    }

    /**
     * Returns the target via which a request may be proxied to the peer with the specified route, preferring the
     * specified scheme, or null if that peer is unknown, or cannot currently service a request via any scheme.
     * A request received via HTTPS is only ever proxied via HTTPS.
     * @param route the route of a peer
     * @param scheme the preferred scheme, or null if there is no preference
     * @return a proxy target, or null if the specified peer cannot currently service a request
     */
    public ProxyTarget findTarget(String route, String scheme) {
        Map<String, Target> targets = this.peers.get(route);
        if (targets == null) return null;
        Target preferred = (scheme != null) ? targets.get(scheme) : null;
        if ((preferred != null) && preferred.isUsable()) return preferred;
        // Never downgrade a secure request to an insecure scheme
        if (HTTPS.equals(scheme)) return null;
        for (Target target : targets.values()) {
            if (target.isUsable()) return target;
        }
        return null;
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return exchange.getAttachment(TARGET);
    }

    @Override
    public List<ProxyTarget> getAllTargets() {
        List<ProxyTarget> targets = new ArrayList<>();
        for (Map<String, Target> peer : this.peers.values()) {
            targets.addAll(peer.values());
        }
        return targets;
    }

    @Override
    public void getConnection(ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        ExclusiveConnectionHolder holder = exchange.getConnection().getAttachment(this.exclusiveConnectionKey);
        if ((holder != null) && holder.connection.getConnection().isOpen()) {
            // An exclusive connection was already allocated for the connection of this request, so keep using it
            callback.completed(exchange, holder.connection);
            return;
        }
        if (!(proxyTarget instanceof Target target)) {
            callback.couldNotResolveBackend(exchange);
            return;
        }
        // As for the reverse-proxy handler, always create a new connection for an upgrade request
        if ((holder != null) || exchange.getRequestHeaders().contains(Headers.UPGRADE)) {
            target.pool.connect(proxyTarget, exchange, new ProxyCallback<>() {
                @Override
                public void completed(HttpServerExchange exchange, ProxyConnection result) {
                    if (holder != null) {
                        holder.connection = result;
                    } else {
                        ExclusiveConnectionHolder newHolder = new ExclusiveConnectionHolder();
                        newHolder.connection = result;
                        ServerConnection connection = exchange.getConnection();
                        connection.putAttachment(NonPrimaryOwnerProxyClient.this.exclusiveConnectionKey, newHolder);
                        connection.addCloseListener(new ServerConnection.CloseListener() {
                            @Override
                            public void closed(ServerConnection connection) {
                                ClientConnection clientConnection = newHolder.connection.getConnection();
                                if (clientConnection.isOpen()) {
                                    IoUtils.safeClose(clientConnection);
                                }
                            }
                        });
                    }
                    callback.completed(exchange, result);
                }

                @Override
                public void failed(HttpServerExchange exchange) {
                    callback.failed(exchange);
                }

                @Override
                public void couldNotResolveBackend(HttpServerExchange exchange) {
                    callback.couldNotResolveBackend(exchange);
                }

                @Override
                public void queuedRequestFailed(HttpServerExchange exchange) {
                    callback.queuedRequestFailed(exchange);
                }
            }, timeout, timeUnit, true);
        } else {
            target.pool.connect(proxyTarget, exchange, callback, timeout, timeUnit, false);
        }
    }

    private Map<String, Target> createTargets(Map<String, URI> uris) {
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Map.Entry<String, URI> entry : uris.entrySet()) {
            targets.put(entry.getKey(), new Target(entry.getValue()));
        }
        return Collections.unmodifiableMap(targets);
    }

    /**
     * Returns the URI of the first address of each supported scheme of the specified endpoints.
     */
    private Map<String, URI> uris(Map<String, List<InetSocketAddress>> endpoints) {
        if (endpoints == null) return Map.of();
        Map<String, URI> uris = new LinkedHashMap<>();
        for (Map.Entry<String, List<InetSocketAddress>> entry : endpoints.entrySet()) {
            String scheme = entry.getKey();
            List<InetSocketAddress> addresses = entry.getValue();
            if (this.schemes.contains(scheme) && (addresses != null) && !addresses.isEmpty()) {
                InetSocketAddress address = addresses.get(0);
                uris.put(scheme, URI.create(String.format("%s://%s:%d", scheme, address.getHostString(), address.getPort())));
            }
        }
        return uris;
    }

    private static Map<String, URI> targetURIs(Map<String, Target> targets) {
        Map<String, URI> uris = new LinkedHashMap<>();
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            uris.put(entry.getKey(), entry.getValue().uri);
        }
        return uris;
    }

    private static void close(Map<String, Target> targets) {
        if (targets != null) {
            close(targets.values());
        }
    }

    private static void close(Collection<Target> targets) {
        for (Target target : targets) {
            target.close();
        }
    }

    /**
     * The connection pool to a given address of a peer, configured per the referenced reverse-proxy handler.
     */
    class Target extends ConnectionPoolErrorHandler.SimpleConnectionPoolErrorHandler implements ProxyTarget, ConnectionPoolManager {
        final URI uri;
        final ProxyConnectionPool pool;
        private volatile boolean closed = false;

        Target(URI uri) {
            this.uri = uri;
            XnioSsl ssl = HTTPS.equals(uri.getScheme()) ? NonPrimaryOwnerProxyClient.this.ssl : null;
            this.pool = new ProxyConnectionPool(this, uri, ssl, NonPrimaryOwnerProxyClient.this.client, OptionMap.EMPTY);
        }

        /**
         * Indicates whether this target can currently accept a request, i.e. it is not closed, and its last attempt to
         * connect did not fail. Its connection pool periodically retries a failed target, and clears its error once
         * a connection succeeds.
         * Unlike {@link ProxyConnectionPool#available()}, this may be invoked by any thread, not only an I/O thread.
         */
        boolean isUsable() {
            return !this.closed && this.isAvailable();
        }

        void close() {
            this.closed = true;
            this.pool.close();
        }

        @Override
        public int getProblemServerRetry() {
            return NonPrimaryOwnerProxyClient.this.configuration.getProblemServerRetry();
        }

        @Override
        public int getMaxConnections() {
            return NonPrimaryOwnerProxyClient.this.configuration.getConnectionsPerThread();
        }

        @Override
        public int getMaxCachedConnections() {
            return NonPrimaryOwnerProxyClient.this.configuration.getConnectionsPerThread();
        }

        @Override
        public int getSMaxConnections() {
            return NonPrimaryOwnerProxyClient.this.configuration.getCachedConnectionsPerThread();
        }

        @Override
        public long getTtl() {
            return NonPrimaryOwnerProxyClient.this.configuration.getConnectionIdleTimeout();
        }

        @Override
        public int getMaxQueueSize() {
            return NonPrimaryOwnerProxyClient.this.configuration.getRequestQueueSize();
        }

        @Override
        public String toString() {
            return this.uri.toString();
        }
    }

    private static class ExclusiveConnectionHolder {
        private ProxyConnection connection;
    }
}

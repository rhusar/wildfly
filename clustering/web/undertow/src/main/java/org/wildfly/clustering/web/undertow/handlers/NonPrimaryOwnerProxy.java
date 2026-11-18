/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.util.function.Supplier;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.StatusCodes;

import org.wildfly.clustering.web.service.routing.RouteEndpointRegistry;
import org.wildfly.extension.undertow.ReverseProxyConfiguration;
import org.wildfly.extension.undertow.Server;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.xnio.ssl.XnioSsl;

/**
 * The proxy of a given server, via which a request associated with a session owned by a peer member is proxied to that member.
 * <p>
 * A single instance, i.e. a single proxy client and its connection pools, is shared by every deployment of a server
 * whose sessions have a primary owner within the cluster.
 * Each deployment decorates its own handler chain via {@link NonPrimaryOwnerHandlerDecorator}, which delegates to the
 * shared {@link #getProxyHandler() proxy handler} of this proxy.
 * The proxy client is created on {@link #start()}, i.e. only once a deployment requires it, and closed on {@link #stop()}.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxy implements BlockingLifecycle {

    /**
     * Resolves the proxy of a given server.
     */
    public static final UnaryServiceDescriptor<NonPrimaryOwnerProxy> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.clustering.web.undertow.non-primary-owner-proxy", NonPrimaryOwnerProxy.class);

    /**
     * Responds to a request that cannot be proxied to, nor serviced by, the primary owner of its session.
     */
    static final HttpHandler UNAVAILABLE_HANDLER = new ResponseCodeHandler(StatusCodes.SERVICE_UNAVAILABLE);

    private final RouteEndpointRegistry registry;
    private final ReverseProxyConfiguration configuration;
    private final XnioSsl ssl;
    private final Supplier<Server> server;

    private volatile NonPrimaryOwnerProxyClient client;
    private volatile HttpHandler proxyHandler;

    /**
     * Creates the proxy of a server.
     * @param registry the registry of the endpoints of the members of the routing group of the server
     * @param configuration the configuration of the referenced reverse-proxy handler
     * @param ssl the SSL configuration used to proxy a request via HTTPS, or null, if requests are never proxied via HTTPS
     * @param server the server
     */
    public NonPrimaryOwnerProxy(RouteEndpointRegistry registry, ReverseProxyConfiguration configuration, XnioSsl ssl, Supplier<Server> server) {
        this.registry = registry;
        this.configuration = configuration;
        this.ssl = ssl;
        this.server = server;
    }

    @Override
    public boolean isStarted() {
        return this.client != null;
    }

    @Override
    public void start() {
        NonPrimaryOwnerProxyClient client = new NonPrimaryOwnerProxyClient(this.registry, this.server.get().getRoute(), this.configuration, this.ssl);
        client.start();
        // The proxy handler is shared by every deployment, and thus cannot delegate to the handler chain of any one of them.
        // Since a request is only ever proxied once its target was resolved, the next handler is never expected to be invoked.
        this.proxyHandler = ProxyHandler.builder()
                .setProxyClient(client)
                .setMaxRequestTime(this.configuration.getMaxRequestTime())
                .setMaxConnectionRetries(this.configuration.getMaxRetries())
                .setRewriteHostHeader(this.configuration.isRewriteHostHeader())
                .setReuseXForwarded(this.configuration.isReuseXForwardedHeader())
                .setNext(UNAVAILABLE_HANDLER)
                .build();
        this.client = client;
    }

    @Override
    public void stop() {
        NonPrimaryOwnerProxyClient client = this.client;
        if (client != null) {
            this.client = null;
            this.proxyHandler = null;
            client.close();
        }
    }

    /**
     * Returns the client of this proxy.
     * @return a proxy client
     */
    public NonPrimaryOwnerProxyClient getClient() {
        return this.client;
    }

    /**
     * Returns the handler that proxies a request to the target attached to that request by {@link NonPrimaryOwnerProxyHandler}.
     * @return a proxy handler
     */
    public HttpHandler getProxyHandler() {
        return this.proxyHandler;
    }

    /**
     * Returns the server of this proxy.
     * @return a server
     */
    public Supplier<Server> getServer() {
        return this.server;
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.session;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.function.Function;
import java.util.function.Supplier;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

import org.wildfly.extension.undertow.logging.UndertowLogger;

/**
 * Handler that proxies a request for a session owned by a peer cluster member to that member, rather than servicing it
 * locally, so that requests for a given session are processed by the member that can read it without a remote lookup.
 * <p>
 * Requests that are not associated with a session, whose owner is this member, or whose owner cannot be resolved to a
 * reachable endpoint, are serviced locally, i.e. by the next handler in the chain.
 * <p>
 * A request that was already proxied by a peer member is never proxied again, but is answered with
 * {@link StatusCodes#SERVICE_UNAVAILABLE} if this member does not own its session.
 * Since a client could otherwise suppress proxying of its own requests by sending {@link #PROXIED_BY} itself, that
 * header is only honoured when the request was received directly from an endpoint published by a peer member.
 *
 * @author Radoslav Husar
 */
public class ForeignSessionProxyHandler implements HttpHandler {

    /**
     * Identifies the peer member that proxied a request, and thus that this request must not be proxied again.
     */
    public static final HttpString PROXIED_BY = new HttpString("x-wildfly-proxied-by");

    /**
     * Resolves the identifier of the session associated with a request, or null if the request has no associated session.
     */
    private static final Function<HttpServerExchange, String> SESSION_IDENTIFIER = new Function<>() {
        @Override
        public String apply(HttpServerExchange exchange) {
            ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            SessionConfig config = (context != null) ? context.getCurrentServletContext().getSessionConfig() : null;
            return (config != null) ? config.findSessionId(exchange) : null;
        }
    };

    private final HttpHandler next;
    private final HttpHandler proxyHandler;
    private final HttpHandler unavailableHandler;
    private final ForeignSessionProxyClient proxyClient;
    private final SessionLocalityProvider provider;
    private final Function<HttpServerExchange, String> sessionIdentifier;
    private final Supplier<String> route;

    public ForeignSessionProxyHandler(HttpHandler next, SessionLocalityProvider provider, Supplier<String> route) {
        this(next, new ForeignSessionProxyClient(), provider, SESSION_IDENTIFIER, route);
    }

    private ForeignSessionProxyHandler(HttpHandler next, ForeignSessionProxyClient proxyClient, SessionLocalityProvider provider, Function<HttpServerExchange, String> sessionIdentifier, Supplier<String> route) {
        this(next, ProxyHandler.builder()
                .setProxyClient(proxyClient)
                .setNext(next)
                // Never retry against a different member - the target is determined by session ownership
                .setMaxConnectionRetries(0)
                .setRewriteHostHeader(false)
                .setReuseXForwarded(true)
                .build(), new ResponseCodeHandler(StatusCodes.SERVICE_UNAVAILABLE), proxyClient, provider, sessionIdentifier, route);
    }

    ForeignSessionProxyHandler(HttpHandler next, HttpHandler proxyHandler, HttpHandler unavailableHandler, ForeignSessionProxyClient proxyClient, SessionLocalityProvider provider, Function<HttpServerExchange, String> sessionIdentifier, Supplier<String> route) {
        this.next = next;
        this.proxyHandler = proxyHandler;
        this.unavailableHandler = unavailableHandler;
        this.proxyClient = proxyClient;
        this.provider = provider;
        this.sessionIdentifier = sessionIdentifier;
        this.route = route;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String sessionId = this.sessionIdentifier.apply(exchange);

        // A request without an associated session is serviced locally
        if (sessionId == null) {
            this.next.handleRequest(exchange);
            return;
        }

        // Only honour the marker of a peer member, so that a client cannot suppress proxying of its own requests
        boolean proxied = exchange.getRequestHeaders().contains(PROXIED_BY);
        if (proxied && !this.isPeerOriginated(exchange)) {
            exchange.getRequestHeaders().remove(PROXIED_BY);
            proxied = false;
        }

        URI owner = this.provider.findRemoteOwner(sessionId).orElse(null);

        // The session is owned by this member, or its owner could not be resolved to a reachable endpoint
        if (owner == null) {
            this.next.handleRequest(exchange);
            return;
        }

        if (proxied) {
            // Never proxy a request twice: the topology view of this member and that of the member that proxied it disagree
            UndertowLogger.ROOT_LOGGER.debugf("Refusing to proxy request %s for session %s to %s, since it was already proxied by %s", exchange.getRequestURI(), sessionId, owner, exchange.getRequestHeaders().getFirst(PROXIED_BY));
            this.unavailableHandler.handleRequest(exchange);
            return;
        }

        this.proxyClient.register(owner, this.provider.getPeerEndpoints());
        exchange.putAttachment(ForeignSessionProxyClient.TARGET, owner);
        String route = this.route.get();
        exchange.getRequestHeaders().put(PROXIED_BY, (route != null) ? route : "");

        this.proxyHandler.handleRequest(exchange);
    }

    /**
     * Determines whether the specified exchange was received directly from an endpoint published by a peer member.
     * <p>
     * This uses the peer address of the connection, rather than any header or the source address of the exchange, since
     * the latter can be overridden by a proxy address forwarding handler, and thus by a client.
     * As is the case for the JGroups transport, a client mapping cannot generally be selected based on the source
     * address of a request, and thus the address published by a peer member only matches the address from which its
     * requests originate if its client mappings describe a route used by its peers.
     */
    private boolean isPeerOriginated(HttpServerExchange exchange) {
        InetSocketAddress peerAddress = exchange.getConnection().getPeerAddress(InetSocketAddress.class);
        if ((peerAddress == null) || peerAddress.isUnresolved()) return false;
        InetAddress address = peerAddress.getAddress();

        for (URI endpoint : this.provider.getPeerEndpoints()) {
            try {
                for (InetAddress candidate : InetAddress.getAllByName(endpoint.getHost())) {
                    if (candidate.equals(address)) return true;
                }
            } catch (UnknownHostException e) {
                UndertowLogger.ROOT_LOGGER.debugf(e, "Could not resolve the address of cluster member endpoint %s", endpoint);
            }
        }
        return false;
    }
}

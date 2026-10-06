/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.clustering.web.undertow.logging.UndertowClusteringLogger;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.handlers.ProxyTargetHostSelector;

/**
 * Handler that proxies a request associated with a session owned by a peer cluster member to that member, rather than
 * servicing it locally, so that requests for a given session are processed by the member that can read it without a
 * remote lookup.
 * <p>
 * Requests that are not associated with a session, requests whose session is owned by this member, and requests whose
 * primary owner cannot be resolved to a reachable address are serviced locally, i.e. by the next handler in the chain.
 * <p>
 * A request that a peer member already proxied is never proxied again, but is answered with
 * {@link StatusCodes#SERVICE_UNAVAILABLE} if this member is not the primary owner of its session.
 * A client that sends {@link #PROXIED_BY} itself can therefore only suppress proxying of its own request - it can
 * neither cause a request to be proxied, nor affect the handling of any other session.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyHandler implements HttpHandler {

    /**
     * Identifies the peer member that proxied a request, and thus that this request must not be proxied again.
     */
    public static final HttpString PROXIED_BY = new HttpString("x-wildfly-proxied-by");

    /**
     * Resolves the identifier of the session associated with a request, or null if the request has no associated session.
     */
    static final Function<HttpServerExchange, String> SESSION_IDENTIFIER = new Function<>() {
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
    private final ProxyTargetRegistrar registrar;
    private final SessionLocality locality;
    private final Supplier<Server> server;
    private final Function<HttpServerExchange, String> sessionIdentifier;
    private final Function<HttpServerExchange, String> schemeResolver;

    public NonPrimaryOwnerProxyHandler(HttpHandler next, HttpHandler proxyHandler, HttpHandler unavailableHandler, ProxyTargetRegistrar registrar, SessionLocality locality, Supplier<Server> server) {
        this(next, proxyHandler, unavailableHandler, registrar, locality, server, SESSION_IDENTIFIER, new LocalSchemeResolver(server));
    }

    NonPrimaryOwnerProxyHandler(HttpHandler next, HttpHandler proxyHandler, HttpHandler unavailableHandler, ProxyTargetRegistrar registrar, SessionLocality locality, Supplier<Server> server, Function<HttpServerExchange, String> sessionIdentifier, Function<HttpServerExchange, String> schemeResolver) {
        this.next = next;
        this.proxyHandler = proxyHandler;
        this.unavailableHandler = unavailableHandler;
        this.registrar = registrar;
        this.locality = locality;
        this.server = server;
        this.sessionIdentifier = sessionIdentifier;
        this.schemeResolver = schemeResolver;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String sessionId = this.sessionIdentifier.apply(exchange);

        // A request without an associated session is serviced locally
        if (sessionId == null) {
            this.next.handleRequest(exchange);
            return;
        }

        Map<String, List<InetSocketAddress>> owner = this.locality.findPrimaryOwner(sessionId);

        // This member is the primary owner of this session, or its primary owner could not be resolved
        if (owner.isEmpty()) {
            this.next.handleRequest(exchange);
            return;
        }

        if (exchange.getRequestHeaders().contains(PROXIED_BY)) {
            // Never proxy a request twice: the topology view of this member and that of the member that proxied it disagree
            UndertowClusteringLogger.ROOT_LOGGER.debugf("Refusing to proxy request %s for session %s, already proxied by %s", exchange.getRequestURI(), sessionId, exchange.getRequestHeaders().getFirst(PROXIED_BY));
            this.unavailableHandler.handleRequest(exchange);
            return;
        }

        URI target = this.findTarget(owner, exchange);

        // The primary owner publishes no address reachable via a scheme supported by this request
        if (target == null) {
            this.next.handleRequest(exchange);
            return;
        }

        this.registrar.register(target, this.locality.getPeers());
        exchange.putAttachment(ProxyTargetHostSelector.PROXY_TARGET, target);
        String route = this.server.get().getRoute();
        exchange.getRequestHeaders().put(PROXIED_BY, (route != null) ? route : "");

        this.proxyHandler.handleRequest(exchange);
    }

    /**
     * Returns the endpoint of the primary owner, preferring the scheme on which this request arrived, so that e.g. a
     * request received via AJP is proxied via AJP.
     */
    private URI findTarget(Map<String, List<InetSocketAddress>> endpoints, HttpServerExchange exchange) {
        Collection<String> schemes = new ArrayList<>(endpoints.size() + 1);
        String scheme = this.schemeResolver.apply(exchange);
        if (scheme != null) {
            schemes.add(scheme);
        }
        schemes.addAll(endpoints.keySet());

        for (String candidate : schemes) {
            List<InetSocketAddress> addresses = endpoints.get(candidate);
            if ((addresses != null) && !addresses.isEmpty()) {
                return ProxyTargetRegistrar.uri(candidate, addresses.get(0));
            }
        }
        return null;
    }
}

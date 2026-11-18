/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.proxy.ProxyClient;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.extension.undertow.Server;

/**
 * Unit test for {@link NonPrimaryOwnerProxyHandler}.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyHandlerTestCase {

    private static final String SESSION_ID = "session";
    private static final String OWNER = "node2";

    private final HttpHandler next = mock(HttpHandler.class);
    private final HttpHandler proxyHandler = mock(HttpHandler.class);
    private final HttpHandler unavailableHandler = mock(HttpHandler.class);
    private final NonPrimaryOwnerProxyClient client = mock(NonPrimaryOwnerProxyClient.class);
    private final ProxyClient.ProxyTarget target = mock(ProxyClient.ProxyTarget.class);
    private final SessionLocality locality = mock(SessionLocality.class);
    private final Server server = mock(Server.class);
    private final ServerConnection connection = mock(ServerConnection.class);

    private HttpServerExchange exchange() {
        return new HttpServerExchange(this.connection);
    }

    private NonPrimaryOwnerProxyHandler handler(String sessionId) {
        return this.handler(sessionId, null);
    }

    /**
     * Creates a handler for a request associated with the specified session, arriving on a listener of the specified scheme.
     */
    private NonPrimaryOwnerProxyHandler handler(String sessionId, String scheme) {
        Function<HttpServerExchange, String> identifier = exchange -> sessionId;
        Function<HttpServerExchange, String> schemeResolver = exchange -> scheme;
        return new NonPrimaryOwnerProxyHandler(this.next, this.proxyHandler, this.unavailableHandler, this.client, this.locality, () -> this.server, identifier, schemeResolver);
    }

    @Test
    public void requestWithoutSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        this.handler(null).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.locality, this.client);
    }

    @Test
    public void locallyOwnedSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(null);

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.client);
    }

    @Test
    public void sessionOwnedByPeerIsProxiedToItsPrimaryOwner() throws Exception {
        HttpServerExchange exchange = this.exchange();
        // The context path of the deployment was already resolved by the time its handler chain is invoked
        exchange.setRequestURI("/context/servlet");
        exchange.setResolvedPath("/context");

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(OWNER);
        // The request arrived on an HTTP listener, so the HTTP address of its primary owner is preferred
        when(this.client.findTarget(OWNER, "http")).thenReturn(this.target);
        when(this.server.getRoute()).thenReturn("node1");

        this.handler(SESSION_ID, "http").handleRequest(exchange);

        verify(this.proxyHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.unavailableHandler);

        assertSame(this.target, exchange.getAttachment(NonPrimaryOwnerProxyClient.TARGET));
        // The request is marked, so that its primary owner does not proxy it onward
        assertEquals("node1", exchange.getRequestHeaders().getFirst(NonPrimaryOwnerProxyHandler.PROXIED_BY));
        // The request is proxied to the same URI, i.e. including the context path of the deployment
        assertEquals("", exchange.getResolvedPath());
        assertEquals("/context/servlet", exchange.getRequestURI());
    }

    @Test
    public void unavailableOwnerIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        // The primary owner publishes no address via a supported scheme, or cannot currently service a request
        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(OWNER);
        when(this.client.findTarget(OWNER, "ajp")).thenReturn(null);

        this.handler(SESSION_ID, "ajp").handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler);
        assertNull(exchange.getAttachment(NonPrimaryOwnerProxyClient.TARGET));
        assertNull(exchange.getRequestHeaders().getFirst(NonPrimaryOwnerProxyHandler.PROXIED_BY));
    }

    @Test
    public void alreadyProxiedSessionOwnedByPeerIsRefused() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(NonPrimaryOwnerProxyHandler.PROXIED_BY, "node3");

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(OWNER);

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.unavailableHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.proxyHandler, this.client);
        assertNull(exchange.getAttachment(NonPrimaryOwnerProxyClient.TARGET));
    }

    @Test
    public void alreadyProxiedLocallyOwnedSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(NonPrimaryOwnerProxyHandler.PROXIED_BY, "node3");

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(null);

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.client);
    }
}

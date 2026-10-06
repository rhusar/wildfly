/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.handlers.ProxyTargetHostSelector;

/**
 * Unit test for {@link NonPrimaryOwnerProxyHandler}.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyHandlerTestCase {

    private static final String SESSION_ID = "session";
    private static final Map<String, List<InetSocketAddress>> OWNER = Map.of(
            "ajp", List.of(InetSocketAddress.createUnresolved("192.0.2.2", 8009)),
            "http", List.of(InetSocketAddress.createUnresolved("192.0.2.2", 8080)));

    private final HttpHandler next = mock(HttpHandler.class);
    private final HttpHandler proxyHandler = mock(HttpHandler.class);
    private final HttpHandler unavailableHandler = mock(HttpHandler.class);
    private final LoadBalancingProxyClient proxyClient = mock(LoadBalancingProxyClient.class);
    private final ProxyTargetRegistrar registrar = ProxyTargetRegistrar.of(this.proxyClient);
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
        return new NonPrimaryOwnerProxyHandler(this.next, this.proxyHandler, this.unavailableHandler, this.registrar, this.locality, () -> this.server, identifier, schemeResolver);
    }

    @Test
    public void requestWithoutSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        this.handler(null).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.locality, this.proxyClient);
    }

    @Test
    public void locallyOwnedSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(Map.of());

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.proxyClient);
    }

    @Test
    public void sessionOwnedByPeerIsProxiedToItsPrimaryOwner() throws Exception {
        HttpServerExchange exchange = this.exchange();

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(OWNER);
        when(this.locality.getPeers()).thenReturn(List.of(OWNER));
        when(this.server.getRoute()).thenReturn("node1");

        this.handler(SESSION_ID, "http").handleRequest(exchange);

        verify(this.proxyHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.unavailableHandler);

        // The request arrived on an HTTP listener, so it is proxied to the HTTP address of its primary owner
        URI target = URI.create("http://192.0.2.2:8080");
        assertEquals(target, exchange.getAttachment(ProxyTargetHostSelector.PROXY_TARGET));
        verify(this.proxyClient).addHost(target);
        // The request is marked, so that its primary owner does not proxy it onward
        assertEquals("node1", exchange.getRequestHeaders().getFirst(NonPrimaryOwnerProxyHandler.PROXIED_BY));
    }

    @Test
    public void requestIsProxiedViaTheSchemeOnWhichItArrived() throws Exception {
        HttpServerExchange exchange = this.exchange();

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(OWNER);
        when(this.locality.getPeers()).thenReturn(List.of(OWNER));
        when(this.server.getRoute()).thenReturn("node1");

        this.handler(SESSION_ID, "ajp").handleRequest(exchange);

        verify(this.proxyHandler).handleRequest(exchange);
        assertEquals(URI.create("ajp://192.0.2.2:8009"), exchange.getAttachment(ProxyTargetHostSelector.PROXY_TARGET));
    }

    @Test
    public void ownerWithoutReachableAddressIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        // The primary owner publishes no address, e.g. every one of its listeners binds a wildcard address
        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(Map.of());

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verify(this.proxyClient, never()).addHost(any(URI.class));
    }

    @Test
    public void alreadyProxiedSessionOwnedByPeerIsRefused() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(NonPrimaryOwnerProxyHandler.PROXIED_BY, "node2");

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(OWNER);

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.unavailableHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.proxyHandler, this.proxyClient);
        assertNull(exchange.getAttachment(ProxyTargetHostSelector.PROXY_TARGET));
    }

    @Test
    public void alreadyProxiedLocallyOwnedSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(NonPrimaryOwnerProxyHandler.PROXIED_BY, "node2");

        when(this.locality.findPrimaryOwner(SESSION_ID)).thenReturn(Map.of());

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.proxyClient);
    }
}

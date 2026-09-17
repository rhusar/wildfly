/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.undertow.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;

import org.junit.Test;

/**
 * Unit test for {@link ForeignSessionProxyHandler}.
 *
 * @author Radoslav Husar
 */
public class ForeignSessionProxyHandlerTestCase {

    private static final String SESSION_ID = "session";
    private static final URI OWNER = URI.create("http://127.0.0.2:8080");
    private static final URI PEER = URI.create("http://127.0.0.3:8080");

    private final HttpHandler next = mock(HttpHandler.class);
    private final HttpHandler proxyHandler = mock(HttpHandler.class);
    private final HttpHandler unavailableHandler = mock(HttpHandler.class);
    private final SessionLocalityProvider provider = mock(SessionLocalityProvider.class);
    private final ForeignSessionProxyClient proxyClient = new ForeignSessionProxyClient();
    private final ServerConnection connection = mock(ServerConnection.class);

    private HttpServerExchange exchange() {
        return new HttpServerExchange(this.connection);
    }

    private ForeignSessionProxyHandler handler(String sessionId) {
        Function<HttpServerExchange, String> identifier = exchange -> sessionId;
        Supplier<String> route = () -> "node1";
        return new ForeignSessionProxyHandler(this.next, this.proxyHandler, this.unavailableHandler, this.proxyClient, this.provider, identifier, route);
    }

    @Test
    public void requestWithoutSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        this.handler(null).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler, this.provider);
    }

    @Test
    public void locallyOwnedSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();

        when(this.provider.findRemoteOwner(SESSION_ID)).thenReturn(Optional.empty());

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler);
    }

    @Test
    public void foreignSessionIsProxiedToItsOwner() throws Exception {
        HttpServerExchange exchange = this.exchange();

        when(this.provider.findRemoteOwner(SESSION_ID)).thenReturn(Optional.of(OWNER));
        when(this.provider.getPeerEndpoints()).thenReturn(List.of(OWNER, PEER));

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.proxyHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.unavailableHandler);

        // The target of the exchange determines the host to which the proxy client connects
        assertEquals(OWNER, exchange.getAttachment(ForeignSessionProxyClient.TARGET));
        // The request is marked, so that the owner does not proxy it onward
        assertEquals("node1", exchange.getRequestHeaders().getFirst(ForeignSessionProxyHandler.PROXIED_BY));
    }

    @Test
    public void alreadyProxiedForeignSessionIsRefused() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(ForeignSessionProxyHandler.PROXIED_BY, "node2");

        // The request arrived from an endpoint published by a peer member
        when(this.connection.getPeerAddress(InetSocketAddress.class)).thenReturn(new InetSocketAddress(InetAddress.getByName(PEER.getHost()), 54321));
        when(this.provider.getPeerEndpoints()).thenReturn(List.of(OWNER, PEER));
        when(this.provider.findRemoteOwner(SESSION_ID)).thenReturn(Optional.of(OWNER));

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.unavailableHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.proxyHandler);
        assertNull(exchange.getAttachment(ForeignSessionProxyClient.TARGET));
    }

    @Test
    public void alreadyProxiedLocallyOwnedSessionIsHandledLocally() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(ForeignSessionProxyHandler.PROXIED_BY, "node2");

        when(this.connection.getPeerAddress(InetSocketAddress.class)).thenReturn(new InetSocketAddress(InetAddress.getByName(PEER.getHost()), 54321));
        when(this.provider.getPeerEndpoints()).thenReturn(List.of(OWNER, PEER));
        when(this.provider.findRemoteOwner(SESSION_ID)).thenReturn(Optional.empty());

        this.handler(SESSION_ID).handleRequest(exchange);

        verify(this.next).handleRequest(exchange);
        verifyNoInteractions(this.proxyHandler, this.unavailableHandler);
    }

    @Test
    public void forgedProxyHeaderIsIgnored() throws Exception {
        HttpServerExchange exchange = this.exchange();
        exchange.getRequestHeaders().put(ForeignSessionProxyHandler.PROXIED_BY, "node2");

        // The request arrived from an address that is not an endpoint of any peer member
        when(this.connection.getPeerAddress(InetSocketAddress.class)).thenReturn(new InetSocketAddress(InetAddress.getByName("127.0.0.9"), 54321));
        when(this.provider.getPeerEndpoints()).thenReturn(List.of(OWNER, PEER));
        when(this.provider.findRemoteOwner(SESSION_ID)).thenReturn(Optional.of(OWNER));

        this.handler(SESSION_ID).handleRequest(exchange);

        // A client cannot suppress proxying of its own request
        verify(this.proxyHandler).handleRequest(exchange);
        verifyNoInteractions(this.next, this.unavailableHandler);
        assertEquals("node1", exchange.getRequestHeaders().getFirst(ForeignSessionProxyHandler.PROXIED_BY));
        assertFalse(exchange.getRequestHeaders().get(ForeignSessionProxyHandler.PROXIED_BY).contains("node2"));
    }
}

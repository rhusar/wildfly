/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import io.undertow.server.HttpHandler;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.server.Registration;
import org.wildfly.clustering.web.service.routing.RouteEndpointRegistry;
import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.extension.undertow.ReverseProxyConfiguration;
import org.wildfly.extension.undertow.Server;

/**
 * Unit test for {@link NonPrimaryOwnerProxy}, i.e. the proxy shared by every deployment of a server.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyTestCase {

    private final RouteEndpointRegistry registry = mock(RouteEndpointRegistry.class);
    private final Registration registration = mock(Registration.class);
    private final ReverseProxyConfiguration configuration = mock(ReverseProxyConfiguration.class);
    private final Server server = mock(Server.class);

    private NonPrimaryOwnerProxy proxy() {
        when(this.registry.register(any())).thenReturn(this.registration);
        when(this.registry.getEndpoints()).thenReturn(Map.of("node2", Map.of("http", List.of(InetSocketAddress.createUnresolved("192.0.2.2", 8080)))));
        when(this.configuration.getMaxRequestTime()).thenReturn(-1);
        when(this.server.getRoute()).thenReturn("node1");
        return new NonPrimaryOwnerProxy(this.registry, this.configuration, null, () -> this.server);
    }

    @Test
    public void clientIsCreatedOnStartAndClosedOnStop() {
        NonPrimaryOwnerProxy proxy = this.proxy();

        assertFalse(proxy.isStarted());
        assertNull(proxy.getClient());
        verify(this.registry, times(0)).register(any());

        proxy.start();

        assertTrue(proxy.isStarted());
        assertNotNull(proxy.getClient());
        assertNotNull(proxy.getProxyHandler());
        verify(this.registry).register(proxy.getClient());

        proxy.stop();

        assertFalse(proxy.isStarted());
        assertNull(proxy.getClient());
        verify(this.registration).close();
    }

    @Test
    public void proxyIsSharedByEachDeployment() {
        NonPrimaryOwnerProxy proxy = this.proxy();
        proxy.start();
        try {
            NonPrimaryOwnerProxyClient client = proxy.getClient();
            HttpHandler proxyHandler = proxy.getProxyHandler();

            // Two deployments of the same server, with distinct caches, decorate their own handler chains
            HttpHandler handler1 = new NonPrimaryOwnerHandlerDecorator(proxy, mock(SessionLocality.class)).apply(mock(HttpHandler.class));
            HttpHandler handler2 = new NonPrimaryOwnerHandlerDecorator(proxy, mock(SessionLocality.class)).apply(mock(HttpHandler.class));

            assertInstanceOf(NonPrimaryOwnerProxyHandler.class, handler1);
            assertInstanceOf(NonPrimaryOwnerProxyHandler.class, handler2);
            assertNotSame(handler1, handler2);

            // Decorating a handler chain neither creates a client, nor registers a listener with the routing registry
            assertSame(client, proxy.getClient());
            assertSame(proxyHandler, proxy.getProxyHandler());
            verify(this.registry, times(1)).register(any());
        } finally {
            proxy.stop();
        }
    }
}

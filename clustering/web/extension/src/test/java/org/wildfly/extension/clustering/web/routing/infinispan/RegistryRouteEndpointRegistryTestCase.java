/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.routing.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.server.Registration;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.server.registry.RegistryListener;
import org.wildfly.clustering.web.service.routing.RouteEndpointRegistry;

/**
 * Unit test for {@link InfinispanRoutingProvider.RegistryRouteEndpointRegistry}.
 *
 * @author Radoslav Husar
 */
public class RegistryRouteEndpointRegistryTestCase {

    private static final Map<String, List<InetSocketAddress>> LOCAL_ENDPOINTS = Map.of("http", List.of(InetSocketAddress.createUnresolved("127.0.0.1", 8080)));
    private static final Map<String, List<InetSocketAddress>> REMOTE_ENDPOINTS = Map.of("http", List.of(InetSocketAddress.createUnresolved("127.0.0.2", 8080)), "ajp", List.of(InetSocketAddress.createUnresolved("127.0.0.2", 8009)));

    @SuppressWarnings("unchecked")
    private final CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry = mock(CacheContainerRegistry.class);
    private final RouteEndpointRegistry endpoints = new InfinispanRoutingProvider.RegistryRouteEndpointRegistry(this.registry);

    @Test
    public void endpointsAreThoseOfTheRegistry() {
        Map<String, Map<String, List<InetSocketAddress>>> entries = Map.of("node1", LOCAL_ENDPOINTS, "node2", REMOTE_ENDPOINTS);
        when(this.registry.getEntries()).thenReturn(entries);

        assertEquals(entries, this.endpoints.getEndpoints());
    }

    @Test
    public void listenersAreRegisteredWithTheRegistry() {
        @SuppressWarnings("unchecked")
        RegistryListener<String, Map<String, List<InetSocketAddress>>> listener = mock(RegistryListener.class);
        Registration registration = mock(Registration.class);
        when(this.registry.register(listener)).thenReturn(registration);

        assertSame(registration, this.endpoints.register(listener));
    }
}

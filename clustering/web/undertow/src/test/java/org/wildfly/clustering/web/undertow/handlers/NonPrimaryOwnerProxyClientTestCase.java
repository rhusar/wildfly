/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.web.undertow.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import io.undertow.client.UndertowClient;
import io.undertow.server.handlers.proxy.ProxyClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.clustering.server.Registration;
import org.wildfly.clustering.web.service.routing.RouteEndpointRegistry;
import org.wildfly.extension.undertow.ReverseProxyConfiguration;
import org.xnio.ssl.XnioSsl;

/**
 * Unit test for {@link NonPrimaryOwnerProxyClient}.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyClientTestCase {

    private static final String LOCAL_ROUTE = "node1";
    private static final Map<String, List<InetSocketAddress>> LOCAL_ENDPOINTS = Map.of(
            "http", List.of(InetSocketAddress.createUnresolved("192.0.2.1", 8080)));
    private static final Map<String, List<InetSocketAddress>> PEER_ENDPOINTS = Map.of(
            "ajp", List.of(InetSocketAddress.createUnresolved("192.0.2.2", 8009)),
            "http", List.of(InetSocketAddress.createUnresolved("192.0.2.2", 8080)));

    private final RouteEndpointRegistry registry = mock(RouteEndpointRegistry.class);
    private final Registration registration = mock(Registration.class);
    private final ReverseProxyConfiguration configuration = mock(ReverseProxyConfiguration.class);

    private NonPrimaryOwnerProxyClient client;

    @BeforeEach
    public void init() {
        when(this.registry.register(any())).thenReturn(this.registration);
        when(this.registry.getEndpoints()).thenReturn(Map.of(LOCAL_ROUTE, LOCAL_ENDPOINTS, "node2", PEER_ENDPOINTS));
        when(this.configuration.getConnectionsPerThread()).thenReturn(40);
        when(this.configuration.getCachedConnectionsPerThread()).thenReturn(5);
        when(this.configuration.getRequestQueueSize()).thenReturn(10);
        when(this.configuration.getConnectionIdleTimeout()).thenReturn(60000);
        when(this.configuration.getProblemServerRetry()).thenReturn(30);
        this.client = new NonPrimaryOwnerProxyClient(this.registry, LOCAL_ROUTE, this.configuration, null, UndertowClient.getInstance());
        this.client.start();
    }

    @AfterEach
    public void destroy() {
        this.client.close();
        verify(this.registration).close();
    }

    private static URI uri(ProxyClient.ProxyTarget target) {
        return ((NonPrimaryOwnerProxyClient.Target) target).uri;
    }

    @Test
    public void startRegistersListenerAndSeedsPeers() {
        verify(this.registry).register(this.client);

        // The local member is never a target
        assertNull(this.client.findTarget(LOCAL_ROUTE, "http"));
        assertEquals(2, this.client.getAllTargets().size());
    }

    @Test
    public void preferredSchemeIsSelected() {
        assertEquals(URI.create("ajp://192.0.2.2:8009"), uri(this.client.findTarget("node2", "ajp")));
        assertEquals(URI.create("http://192.0.2.2:8080"), uri(this.client.findTarget("node2", "http")));
    }

    @Test
    public void otherSchemeIsSelectedIfPreferredSchemeIsUnavailable() {
        this.client.updated(Map.of("node2", Map.of("http", List.of(InetSocketAddress.createUnresolved("192.0.2.2", 8080)))));

        assertEquals(URI.create("http://192.0.2.2:8080"), uri(this.client.findTarget("node2", "ajp")));
    }

    @Test
    public void secureRequestIsNeverProxiedViaInsecureScheme() {
        // The peer publishes AJP and HTTP addresses only, so a request received via HTTPS is serviced locally
        assertNull(this.client.findTarget("node2", "https"));
    }

    @Test
    public void httpsEndpointsAreIgnoredWithoutSSLContext() {
        this.client.added(Map.of("node3", Map.of("https", List.of(InetSocketAddress.createUnresolved("192.0.2.3", 8443)))));

        assertNull(this.client.findTarget("node3", "https"));
        assertNull(this.client.findTarget("node3", "http"));
    }

    @Test
    public void httpsEndpointsAreTargetsWithSSLContext() {
        RouteEndpointRegistry registry = mock(RouteEndpointRegistry.class);
        when(registry.register(any())).thenReturn(mock(Registration.class));
        when(registry.getEndpoints()).thenReturn(Map.of());
        NonPrimaryOwnerProxyClient client = new NonPrimaryOwnerProxyClient(registry, LOCAL_ROUTE, this.configuration, mock(XnioSsl.class), UndertowClient.getInstance());
        try {
            client.start();
            client.added(Map.of("node3", Map.of("https", List.of(InetSocketAddress.createUnresolved("192.0.2.3", 8443)))));

            assertEquals(URI.create("https://192.0.2.3:8443"), uri(client.findTarget("node3", "https")));
            // An insecure request may be proxied via HTTPS, if that is the only scheme of the peer
            assertEquals(URI.create("https://192.0.2.3:8443"), uri(client.findTarget("node3", "http")));
        } finally {
            client.close();
        }
    }

    @Test
    public void addedPeerBecomesTarget() {
        assertNull(this.client.findTarget("node3", "http"));

        this.client.added(Map.of("node3", Map.of("http", List.of(InetSocketAddress.createUnresolved("192.0.2.3", 8080)))));

        assertEquals(URI.create("http://192.0.2.3:8080"), uri(this.client.findTarget("node3", "http")));
    }

    @Test
    public void unchangedPeerRetainsItsTargets() {
        ProxyClient.ProxyTarget target = this.client.findTarget("node2", "http");

        this.client.updated(Map.of("node2", PEER_ENDPOINTS));

        assertSame(target, this.client.findTarget("node2", "http"));
    }

    @Test
    public void changedPeerReplacesItsTargets() {
        ProxyClient.ProxyTarget target = this.client.findTarget("node2", "http");

        this.client.updated(Map.of("node2", Map.of("http", List.of(InetSocketAddress.createUnresolved("192.0.2.22", 8080)))));

        ProxyClient.ProxyTarget updated = this.client.findTarget("node2", "http");
        assertNotSame(target, updated);
        assertEquals(URI.create("http://192.0.2.22:8080"), uri(updated));
        // The peer no longer publishes an AJP address, so a request arriving via AJP is proxied via HTTP
        assertSame(updated, this.client.findTarget("node2", "ajp"));
    }

    @Test
    public void removedPeerIsNoLongerTarget() {
        this.client.removed(Map.of("node2", PEER_ENDPOINTS));

        assertNull(this.client.findTarget("node2", "http"));
        assertTrue(this.client.getAllTargets().isEmpty());
    }

    @Test
    public void peerWithoutEndpointsIsNotTarget() {
        this.client.updated(Map.of("node2", Map.of()));

        assertNull(this.client.findTarget("node2", "http"));
    }
}

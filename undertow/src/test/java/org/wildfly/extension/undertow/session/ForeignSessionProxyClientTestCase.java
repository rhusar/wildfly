/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.undertow.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.Headers;

import org.junit.Test;

/**
 * Unit test for {@link ForeignSessionProxyClient}.
 *
 * @author Radoslav Husar
 */
public class ForeignSessionProxyClientTestCase {

    private static final URI TARGET = URI.create("http://127.0.0.2:8080");
    private static final URI OTHER = URI.create("http://127.0.0.3:8080");

    private final ForeignSessionProxyClient client = new ForeignSessionProxyClient();

    @Test
    public void exchangeWithoutTargetIsNotProxied() {
        assertNull(this.client.findTarget(new HttpServerExchange(mock(ServerConnection.class))));
    }

    @Test
    public void unregisteredTargetIsNotProxied() {
        HttpServerExchange exchange = new HttpServerExchange(mock(ServerConnection.class));
        exchange.putAttachment(ForeignSessionProxyClient.TARGET, TARGET);

        // The proxy handler falls through to the local handler chain when no target can be resolved
        assertNull(this.client.findTarget(exchange));
    }

    @Test
    public void registeredTargetIsProxied() {
        HttpServerExchange exchange = new HttpServerExchange(mock(ServerConnection.class));
        exchange.putAttachment(ForeignSessionProxyClient.TARGET, TARGET);

        this.client.register(TARGET, List.of(TARGET, OTHER));

        assertNotNull(this.client.findTarget(exchange));
    }

    @Test
    public void routeOfExchangeIsResolvedFromItsTargetRatherThanFromTheClient() {
        HttpServerExchange exchange = new HttpServerExchange(mock(ServerConnection.class));
        // A route encoded by the client must have no effect on the target of the request
        exchange.getRequestHeaders().add(Headers.COOKIE, "JSESSIONID=session." + OTHER);
        exchange.putAttachment(ForeignSessionProxyClient.TARGET, TARGET);

        assertEquals(TARGET.toString(), this.client.parseRoutes(exchange).next().toString());
    }
}

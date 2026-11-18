/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.undertow.server.HttpServerExchange;

import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.UndertowListener;

/**
 * Resolves the scheme of the listener of a server on which a given request arrived, so that a request received via a
 * given scheme, e.g. AJP, can be proxied via that same scheme.
 * <p>
 * The listener is identified by the local address of the connection of a request, rather than by the scheme of the
 * request itself, since the latter describes the scheme used by the client of an AJP front-end, rather than the
 * scheme via which this server was reached.
 *
 * @author Radoslav Husar
 */
public class LocalSchemeResolver implements Function<HttpServerExchange, String> {

    private final Supplier<Server> server;

    private volatile Map<Integer, String> schemes;

    public LocalSchemeResolver(Supplier<Server> server) {
        this.server = server;
    }

    @Override
    public String apply(HttpServerExchange exchange) {
        InetSocketAddress address = exchange.getConnection().getLocalAddress(InetSocketAddress.class);
        if (address == null) return null;

        Map<Integer, String> schemes = this.schemes;
        if (schemes == null) {
            // Listeners have all registered with their server by the time that server services a request
            schemes = new HashMap<>();
            for (UndertowListener listener : this.server.get().getListeners()) {
                schemes.putIfAbsent(listener.getSocketBinding().getSocketAddress().getPort(), listener.getProtocol());
            }
            this.schemes = schemes;
        }
        return schemes.get(address.getPort());
    }
}

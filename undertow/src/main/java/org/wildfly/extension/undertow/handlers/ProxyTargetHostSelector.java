/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.handlers;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.util.AttachmentKey;

/**
 * A {@link LoadBalancingProxyClient.HostSelector} that honours a deterministic target, when the exchange specifies one
 * via {@link #PROXY_TARGET}, and otherwise selects a host at random.
 * <p>
 * This allows the proxy handler created by a reverse-proxy resource to be directed at a target determined elsewhere,
 * e.g. by the ownership of the session associated with a request, without requiring a separate handler resource.
 *
 * @author Radoslav Husar
 */
public enum ProxyTargetHostSelector implements LoadBalancingProxyClient.HostSelector {
    INSTANCE;

    /**
     * The URI of the host to which the associated exchange should be proxied, overriding host selection.
     * The target must already be registered, via {@link LoadBalancingProxyClient#addHost(URI)}, by whoever attaches it.
     */
    public static final AttachmentKey<URI> PROXY_TARGET = AttachmentKey.create(URI.class);

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = true)
    @Override
    public int selectHost(LoadBalancingProxyClient.Host[] availableHosts) {
        return ThreadLocalRandom.current().nextInt(availableHosts.length);
    }

    @Override
    public int selectHost(LoadBalancingProxyClient.Host[] availableHosts, HttpServerExchange exchange) {
        URI target = exchange.getAttachment(PROXY_TARGET);
        if (target != null) {
            for (int i = 0; i < availableHosts.length; ++i) {
                if (target.equals(availableHosts[i].getUri())) {
                    return i;
                }
            }
        }
        return this.selectHost(availableHosts);
    }
}

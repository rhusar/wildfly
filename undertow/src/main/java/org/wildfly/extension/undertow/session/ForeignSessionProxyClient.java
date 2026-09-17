/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.session;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

/**
 * A {@link io.undertow.server.handlers.proxy.ProxyClient} that proxies a request to a deterministic target, rather than
 * to a target chosen by load balancing logic.
 * <p>
 * This reuses the connection pooling, retry, and exclusivity handling of {@link LoadBalancingProxyClient}, but resolves
 * its target from the {@link #TARGET} attachment of the exchange - set by the {@link ForeignSessionProxyHandler} from
 * the ownership of the session associated with the request - instead of from the route encoded by the client or from a
 * host selector.
 * @author Radoslav Husar
 */
public class ForeignSessionProxyClient extends LoadBalancingProxyClient {

    /**
     * The endpoint of the cluster member to which the associated exchange should be proxied.
     */
    public static final AttachmentKey<URI> TARGET = AttachmentKey.create(URI.class);

    private static final Duration CONNECTION_IDLE_TIMEOUT = Duration.ofMinutes(1);

    private final Set<URI> targets = new CopyOnWriteArraySet<>();

    public ForeignSessionProxyClient() {
        // Requests that trigger a protocol upgrade require an exclusive connection
        super(exchange -> exchange.getRequestHeaders().contains(Headers.UPGRADE));
        // Ensure that connections to a member that is no longer a proxy target, e.g. following an undeployment, do not
        // remain open indefinitely
        this.setTtl((int) CONNECTION_IDLE_TIMEOUT.toMillis());
    }

    /**
     * Registers the specified target, if not already registered, removing any previously registered target that is no
     * longer a member of the group, so that connection pools of departed members do not accumulate.
     * @param target the endpoint of the member to which a request is about to be proxied
     * @param members the endpoints of the current peer members of this member's group
     */
    public synchronized void register(URI target, Collection<URI> members) {
        if (this.targets.contains(target)) return;

        for (URI stale : this.targets) {
            if (!members.contains(stale)) {
                this.removeHost(stale);
                this.targets.remove(stale);
            }
        }

        this.addHost(target, target.toString());
        this.targets.add(target);
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        // Returning null causes the proxy handler to fall through to the local handler chain, e.g. if the target
        // departed the group between the time its ownership was resolved and the time the request was proxied
        URI target = exchange.getAttachment(TARGET);
        return ((target != null) && this.targets.contains(target)) ? super.findTarget(exchange) : null;
    }

    @Override
    protected Iterator<CharSequence> parseRoutes(HttpServerExchange exchange) {
        // Ignore any route encoded by the client - the target of a given exchange is determined by session ownership
        URI target = exchange.getAttachment(TARGET);
        return (target != null) ? List.<CharSequence>of(target.toString()).iterator() : Collections.emptyIterator();
    }

    @Override
    protected Host selectHost(HttpServerExchange exchange) {
        URI target = exchange.getAttachment(TARGET);
        if (target == null) return null;
        // Never fall back to a load balanced host: proxying to a member that does not own the session would only be rejected by that member
        Host host = super.selectHost(exchange);
        return ((host != null) && target.equals(host.getUri())) ? host : null;
    }
}

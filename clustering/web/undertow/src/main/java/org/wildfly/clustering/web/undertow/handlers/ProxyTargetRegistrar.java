/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;

/**
 * Registers the members of a group as targets of a proxy client.
 * <p>
 * A given proxy client is shared by every deployment that references the same handler, and thus so is its registrar,
 * so that a member is registered with that client exactly once.
 *
 * @author Radoslav Husar
 */
public class ProxyTargetRegistrar {

    private static final Map<LoadBalancingProxyClient, ProxyTargetRegistrar> REGISTRARS = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Returns the registrar of the specified proxy client.
     * @param client a proxy client
     * @return the registrar of the specified proxy client
     */
    public static ProxyTargetRegistrar of(LoadBalancingProxyClient client) {
        return REGISTRARS.computeIfAbsent(client, ProxyTargetRegistrar::new);
    }

    /**
     * Returns the URI via which a member reachable at the specified address via the specified scheme is proxied.
     * @param scheme a scheme, e.g. ajp, http, or https
     * @param address the address of a member
     * @return a proxy target URI
     */
    public static URI uri(String scheme, InetSocketAddress address) {
        return URI.create(String.format("%s://%s:%d", scheme, address.getHostString(), address.getPort()));
    }

    private final LoadBalancingProxyClient client;
    private final Set<URI> targets = new CopyOnWriteArraySet<>();

    private ProxyTargetRegistrar(LoadBalancingProxyClient client) {
        this.client = client;
    }

    /**
     * Registers the specified target, if not already registered, pruning any previously registered target that is no
     * longer a member of the group, so that the connection pools of departed members do not accumulate.
     * @param target the target of a request about to be proxied
     * @param members the addresses, per scheme, of each current peer member
     */
    public synchronized void register(URI target, Collection<Map<String, List<InetSocketAddress>>> members) {
        if (this.targets.contains(target)) return;

        Set<URI> current = new HashSet<>();
        for (Map<String, List<InetSocketAddress>> endpoints : members) {
            for (Map.Entry<String, List<InetSocketAddress>> entry : endpoints.entrySet()) {
                for (InetSocketAddress address : entry.getValue()) {
                    current.add(uri(entry.getKey(), address));
                }
            }
        }

        for (URI stale : this.targets) {
            if (!current.contains(stale)) {
                this.client.removeHost(stale);
                this.targets.remove(stale);
            }
        }

        this.client.addHost(target);
        this.targets.add(target);
    }
}

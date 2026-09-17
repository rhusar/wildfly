/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.routing.infinispan;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.infinispan.Cache;
import org.wildfly.clustering.cache.Key;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.server.infinispan.CacheContainerGroupMember;
import org.wildfly.clustering.server.infinispan.affinity.UnaryGroupMemberAffinity;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.session.cache.affinity.SessionAffinityEndpoint;
import org.wildfly.clustering.web.service.routing.SessionLocality;

/**
 * Resolves the primary owner of a session via the consistent hash of the cache of a deployment, and maps it to the
 * endpoint published by that member to the routing registry of its server.
 * @author Radoslav Husar
 */
public class InfinispanSessionLocality implements SessionLocality {

    private final Function<String, CacheContainerGroupMember> affinity;
    private final CacheContainerRegistry<String, SessionAffinityEndpoint> registry;

    public InfinispanSessionLocality(Cache<Key<String>, ?> cache, CacheContainerRegistry<String, SessionAffinityEndpoint> registry) {
        this(new UnaryGroupMemberAffinity<>(cache, registry.getGroup()), registry);
    }

    InfinispanSessionLocality(Function<String, CacheContainerGroupMember> affinity, CacheContainerRegistry<String, SessionAffinityEndpoint> registry) {
        this.affinity = affinity;
        this.registry = registry;
    }

    @Override
    public boolean isEnabled() {
        // This member only proxies requests if it publishes an endpoint of its own, i.e. if its server enables proxying
        return this.getLocalEndpoint() != null;
    }

    @Override
    public URI locate(String sessionId) {
        CacheContainerGroupMember owner = this.affinity.apply(sessionId);
        if ((owner == null) || owner.equals(this.registry.getGroup().getLocalMember())) return null;
        Map.Entry<String, SessionAffinityEndpoint> entry = this.registry.getEntry(owner);
        SessionAffinityEndpoint endpoint = (entry != null) ? entry.getValue() : null;
        return (endpoint != null) ? endpoint.getUri() : null;
    }

    @Override
    public Collection<URI> getPeers() {
        SessionAffinityEndpoint local = this.getLocalEndpoint();
        Collection<SessionAffinityEndpoint> endpoints = this.registry.getEntries().values();
        List<URI> peers = new ArrayList<>(endpoints.size());
        for (SessionAffinityEndpoint endpoint : endpoints) {
            if ((endpoint != null) && !endpoint.equals(local)) {
                peers.add(endpoint.getUri());
            }
        }
        return peers;
    }

    private SessionAffinityEndpoint getLocalEndpoint() {
        Map.Entry<String, SessionAffinityEndpoint> entry = this.registry.getEntry(this.registry.getGroup().getLocalMember());
        return (entry != null) ? entry.getValue() : null;
    }
}

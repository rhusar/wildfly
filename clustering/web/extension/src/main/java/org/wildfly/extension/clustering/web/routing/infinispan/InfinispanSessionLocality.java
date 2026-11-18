/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.routing.infinispan;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.infinispan.Cache;
import org.wildfly.clustering.cache.Key;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.server.infinispan.CacheContainerGroupMember;
import org.wildfly.clustering.server.infinispan.affinity.UnaryGroupMemberAffinity;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.web.service.routing.SessionLocality;

/**
 * Resolves the primary owner of a session via the consistent hash of the cache of a deployment, and maps it to the
 * route via which that member is registered with the routing registry of its server.
 * @author Radoslav Husar
 */
public class InfinispanSessionLocality implements SessionLocality {

    private final Function<String, CacheContainerGroupMember> affinity;
    private final CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry;

    public InfinispanSessionLocality(Cache<Key<String>, ?> cache, CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry) {
        this(new UnaryGroupMemberAffinity<>(cache, registry.getGroup()), registry);
    }

    InfinispanSessionLocality(Function<String, CacheContainerGroupMember> affinity, CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry) {
        this.affinity = affinity;
        this.registry = registry;
    }

    @Override
    public String findPrimaryOwner(String sessionId) {
        CacheContainerGroupMember owner = this.affinity.apply(sessionId);
        if ((owner == null) || owner.equals(this.registry.getGroup().getLocalMember())) return null;
        Map.Entry<String, Map<String, List<InetSocketAddress>>> entry = this.registry.getEntry(owner);
        return (entry != null) ? entry.getKey() : null;
    }
}

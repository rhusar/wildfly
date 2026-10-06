/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.routing.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.marshalling.protostream.util.StringKeyMapEntry;
import org.wildfly.clustering.server.infinispan.CacheContainerGroup;
import org.wildfly.clustering.server.infinispan.CacheContainerGroupMember;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.web.service.routing.SessionLocality;

/**
 * Unit test for {@link InfinispanSessionLocality}.
 *
 * @author Radoslav Husar
 */
public class InfinispanSessionLocalityTestCase {

    private static final Map<String, List<InetSocketAddress>> LOCAL_ENDPOINTS = Map.of("http", List.of(InetSocketAddress.createUnresolved("127.0.0.1", 8080)));
    private static final Map<String, List<InetSocketAddress>> REMOTE_ENDPOINTS = Map.of("http", List.of(InetSocketAddress.createUnresolved("127.0.0.2", 8080)), "ajp", List.of(InetSocketAddress.createUnresolved("127.0.0.2", 8009)));

    private final CacheContainerGroup group = mock(CacheContainerGroup.class);
    private final CacheContainerGroupMember localMember = mock(CacheContainerGroupMember.class);
    private final CacheContainerGroupMember remoteMember = mock(CacheContainerGroupMember.class);
    @SuppressWarnings("unchecked")
    private final CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry = mock(CacheContainerRegistry.class);

    private SessionLocality locality(CacheContainerGroupMember owner) {
        when(this.registry.getGroup()).thenReturn(this.group);
        when(this.group.getLocalMember()).thenReturn(this.localMember);
        Function<String, CacheContainerGroupMember> affinity = Function.of(owner);
        return new InfinispanSessionLocality(affinity, this.registry);
    }

    @Test
    public void locallyOwnedSession() {
        SessionLocality locality = this.locality(this.localMember);

        assertTrue(locality.findPrimaryOwner("session").isEmpty());
    }

    @Test
    public void remotelyOwnedSession() {
        when(this.registry.getEntry(this.remoteMember)).thenReturn(new StringKeyMapEntry<>("node2", REMOTE_ENDPOINTS));

        SessionLocality locality = this.locality(this.remoteMember);

        assertEquals(REMOTE_ENDPOINTS, locality.findPrimaryOwner("session"));
    }

    @Test
    public void ownerWithoutEndpointsIsHandledLocally() {
        // The owner publishes no address reachable by its peers, e.g. all of its listeners bind a wildcard address
        when(this.registry.getEntry(this.remoteMember)).thenReturn(new StringKeyMapEntry<>("node2", Map.of()));

        SessionLocality locality = this.locality(this.remoteMember);

        assertTrue(locality.findPrimaryOwner("session").isEmpty());
    }

    @Test
    public void unknownOwnerIsHandledLocally() {
        when(this.registry.getEntry(this.remoteMember)).thenReturn(null);

        SessionLocality locality = this.locality(this.remoteMember);

        assertTrue(locality.findPrimaryOwner("session").isEmpty());
    }

    @Test
    public void peersExcludeTheLocalMember() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new StringKeyMapEntry<>("node1", LOCAL_ENDPOINTS));
        when(this.registry.getEntries()).thenReturn(Map.of("node1", LOCAL_ENDPOINTS, "node2", REMOTE_ENDPOINTS));

        SessionLocality locality = this.locality(this.localMember);

        assertEquals(List.of(REMOTE_ENDPOINTS), List.copyOf(locality.getPeers()));
    }
}

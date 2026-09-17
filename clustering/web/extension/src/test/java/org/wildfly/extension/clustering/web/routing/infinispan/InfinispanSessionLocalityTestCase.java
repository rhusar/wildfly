/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web.routing.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.wildfly.clustering.function.Function;
import org.wildfly.clustering.server.infinispan.CacheContainerGroup;
import org.wildfly.clustering.server.infinispan.CacheContainerGroupMember;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.session.cache.affinity.SessionAffinityEndpoint;
import org.wildfly.clustering.session.cache.affinity.SessionAffinityRegistryEntry;
import org.wildfly.clustering.web.service.routing.SessionLocality;

/**
 * Unit test for {@link InfinispanSessionLocality}.
 *
 * @author Radoslav Husar
 */
public class InfinispanSessionLocalityTestCase {

    private static final SessionAffinityEndpoint LOCAL_ENDPOINT = new SessionAffinityEndpoint("default-server", URI.create("http://127.0.0.1:8080"));
    private static final SessionAffinityEndpoint REMOTE_ENDPOINT = new SessionAffinityEndpoint("default-server", URI.create("http://127.0.0.2:8080"));

    private final CacheContainerGroup group = mock(CacheContainerGroup.class);
    private final CacheContainerGroupMember localMember = mock(CacheContainerGroupMember.class);
    private final CacheContainerGroupMember remoteMember = mock(CacheContainerGroupMember.class);
    @SuppressWarnings("unchecked")
    private final CacheContainerRegistry<String, SessionAffinityEndpoint> registry = mock(CacheContainerRegistry.class);

    private SessionLocality locality(CacheContainerGroupMember owner) {
        when(this.registry.getGroup()).thenReturn(this.group);
        when(this.group.getLocalMember()).thenReturn(this.localMember);
        Function<String, CacheContainerGroupMember> affinity = Function.of(owner);
        return new InfinispanSessionLocality(affinity, this.registry);
    }

    @Test
    public void locallyOwnedSession() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new SessionAffinityRegistryEntry("node1", LOCAL_ENDPOINT));

        SessionLocality locality = this.locality(this.localMember);

        assertTrue(locality.isEnabled());
        assertNull(locality.locate("session"));
    }

    @Test
    public void remotelyOwnedSession() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new SessionAffinityRegistryEntry("node1", LOCAL_ENDPOINT));
        when(this.registry.getEntry(this.remoteMember)).thenReturn(new SessionAffinityRegistryEntry("node2", REMOTE_ENDPOINT));

        SessionLocality locality = this.locality(this.remoteMember);

        assertEquals(REMOTE_ENDPOINT.getUri(), locality.locate("session"));
    }

    @Test
    public void ownerWithoutEndpointIsHandledLocally() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new SessionAffinityRegistryEntry("node1", LOCAL_ENDPOINT));
        // The owner does not publish an endpoint, e.g. its server does not enable proxying
        when(this.registry.getEntry(this.remoteMember)).thenReturn(new SessionAffinityRegistryEntry("node2"));

        SessionLocality locality = this.locality(this.remoteMember);

        assertNull(locality.locate("session"));
    }

    @Test
    public void unknownOwnerIsHandledLocally() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new SessionAffinityRegistryEntry("node1", LOCAL_ENDPOINT));
        when(this.registry.getEntry(this.remoteMember)).thenReturn(null);

        SessionLocality locality = this.locality(this.remoteMember);

        assertNull(locality.locate("session"));
    }

    @Test
    public void disabledWhenLocalMemberPublishesNoEndpoint() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new SessionAffinityRegistryEntry("node1"));

        SessionLocality locality = this.locality(this.localMember);

        assertFalse(locality.isEnabled());
    }

    @Test
    public void peersExcludeTheLocalMember() {
        when(this.registry.getEntry(this.localMember)).thenReturn(new SessionAffinityRegistryEntry("node1", LOCAL_ENDPOINT));
        when(this.registry.getEntries()).thenReturn(Map.of("node1", LOCAL_ENDPOINT, "node2", REMOTE_ENDPOINT));

        SessionLocality locality = this.locality(this.localMember);

        assertEquals(List.of(REMOTE_ENDPOINT.getUri()), List.copyOf(locality.getPeers()));
    }
}

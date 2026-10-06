/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.service.routing;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolves the primary owner of a session, i.e. the group member from which that session can be read locally, to the
 * addresses via which that member can be reached by its peers.
 * <p>
 * This is distinct from a route locator, which determines the identifier of the server to which the <em>next</em>
 * request for a given session should be directed, and which is encoded into the session identifier or affinity cookie
 * for the benefit of an external load balancer.
 * @author Radoslav Husar
 */
public interface SessionLocality {

    /**
     * Locality for a deployment whose sessions are always owned by this member.
     */
    SessionLocality LOCAL = new SessionLocality() {
        @Override
        public Map<String, List<InetSocketAddress>> findPrimaryOwner(String sessionId) {
            return Map.of();
        }

        @Override
        public Collection<Map<String, List<InetSocketAddress>>> getPeers() {
            return List.of();
        }
    };

    /**
     * Returns the addresses, per scheme, of the primary owner of the session with the specified identifier.
     * Returns an empty map if this member is the primary owner of that session, or if its primary owner could not be
     * resolved to an address reachable by this member.
     * @param sessionId a session identifier
     * @return the addresses of the primary owner of the specified session, per scheme, possibly empty.
     */
    Map<String, List<InetSocketAddress>> findPrimaryOwner(String sessionId);

    /**
     * Returns the addresses, per scheme, of each peer member of this member's group, i.e. excluding this member.
     * @return a collection of addresses per scheme, possibly empty.
     */
    Collection<Map<String, List<InetSocketAddress>>> getPeers();
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.service.routing;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Resolves the group member from which the session with a given identifier can currently be read locally.
 * <p>
 * This is distinct from a route locator, which determines the identifier of the server to which the <em>next</em>
 * request for a given session should be directed, and which is encoded into the session identifier or affinity cookie
 * for the benefit of an external load balancer.
 * @author Radoslav Husar
 */
public interface SessionLocality {

    /**
     * Locality for a deployment whose sessions are always read locally.
     */
    SessionLocality LOCAL = new SessionLocality() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public URI locate(String sessionId) {
            return null;
        }

        @Override
        public Collection<URI> getPeers() {
            return List.of();
        }
    };

    /**
     * Indicates whether the server associated with this deployment publishes an endpoint reachable by its peers, and
     * can therefore resolve the owner of a session to a proxy target.
     * @return true, if remote session owners can be resolved, false otherwise.
     */
    boolean isEnabled();

    /**
     * Returns the endpoint of the group member from which the session with the specified identifier can currently be
     * read locally, or null if that member is this member, or if it could not be resolved to an endpoint.
     * @param sessionId a session identifier
     * @return an endpoint, or null if the specified session should be handled locally.
     */
    URI locate(String sessionId);

    /**
     * Returns the endpoints of the peer members of this member's group, i.e. excluding this member.
     * @return a collection of endpoints, possibly empty.
     */
    Collection<URI> getPeers();
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.service.routing;

/**
 * Resolves the primary owner of a session, i.e. the group member from which that session can be read locally, to the
 * route of that member.
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
        public String findPrimaryOwner(String sessionId) {
            return null;
        }
    };

    /**
     * Returns the route of the primary owner of the session with the specified identifier.
     * Returns null if this member is the primary owner of that session, or if its primary owner could not be resolved.
     * @param sessionId a session identifier
     * @return the route of the primary owner of the specified session, or null if owned locally or unresolvable.
     */
    String findPrimaryOwner(String sessionId);
}

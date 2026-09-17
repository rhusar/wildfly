/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.session;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Determines the cluster member from which the session with a given identifier can currently be read locally.
 * <p>
 * This is distinct from {@link SessionAffinityProvider}, which determines the identifier of the server to which the
 * <em>next</em> request for a given session should be directed, and which is encoded into the session identifier or
 * affinity cookie for the benefit of an external load balancer.
 * A locality provider instead resolves the current read owner of a session to an endpoint reachable by this server,
 * so that a request received for a foreign session can be proxied to the member that owns it.
 * TODO Relocate this to Undertow SPI module
 * @author Radoslav Husar
 */
public interface SessionLocalityProvider {

    /**
     * A provider for deployments whose sessions are always read locally.
     */
    SessionLocalityProvider LOCAL = new SessionLocalityProvider() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public Optional<URI> findRemoteOwner(String sessionId) {
            return Optional.empty();
        }

        @Override
        public Collection<URI> getPeerEndpoints() {
            return List.of();
        }
    };

    /**
     * Indicates whether this deployment participates in server-side proxying of requests for foreign sessions.
     * When false, {@link #findRemoteOwner(String)} always returns {@link Optional#empty()} and no proxying handler need be installed.
     * @return true, if this provider can resolve remote session owners, false otherwise.
     */
    boolean isEnabled();

    /**
     * When present, returns the endpoint of the cluster member from which the session with the specified identifier can
     * currently be read locally, if that member is not this member.
     * When absent, the session is owned by this member, or its owner could not be resolved, and the request should be
     * serviced locally.
     * @param sessionId a session identifier
     * @return the endpoint of the member that currently owns the specified session, if remote.
     */
    Optional<URI> findRemoteOwner(String sessionId);

    /**
     * Returns the endpoints of the peer members of this member's group, i.e. excluding this member.
     * Used both to validate that a request originated from a peer member, and to reconcile the proxy targets of this server.
     * @return a collection of endpoints, possibly empty.
     */
    Collection<URI> getPeerEndpoints();
}

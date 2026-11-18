/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.service.routing;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.wildfly.clustering.server.Registrar;
import org.wildfly.clustering.server.registry.RegistryListener;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Exposes the addresses, per scheme, via which each member of the routing group of a server can be reached by its peers,
 * keyed by the route of each member.
 * <p>
 * Listeners registered via {@link #register(Object)} are notified of changes to these addresses, so that a consumer can
 * maintain its own view of these, rather than resolving them per request.
 * @author Radoslav Husar
 */
public interface RouteEndpointRegistry extends Registrar<RegistryListener<String, Map<String, List<InetSocketAddress>>>> {
    /**
     * Resolves the route endpoint registry of a given server.
     */
    UnaryServiceDescriptor<RouteEndpointRegistry> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.clustering.web.route-endpoint-registry", RouteEndpointRegistry.class);

    /**
     * Returns the addresses, per scheme, via which each member of the routing group can be reached, keyed by route.
     * @return a map of addresses per scheme, keyed by route.
     */
    Map<String, Map<String, List<InetSocketAddress>>> getEndpoints();
}

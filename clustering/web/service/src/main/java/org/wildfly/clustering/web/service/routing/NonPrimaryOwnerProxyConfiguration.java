/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.service.routing;

import java.util.Optional;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * The configuration of a proxy that services a request associated with a session owned by a peer member, by proxying
 * that request to the primary owner of its session, rather than servicing it locally.
 * <p>
 * A single proxy is created per server, and is shared by every deployment of that server whose sessions have a primary owner within the cluster,
 * such that each server maintains a single set of connection pools to its peers.
 * This configuration is agnostic of any specific web container, and thus references the resources that configure the
 * proxy client by name.
 * @author Radoslav Husar
 */
public interface NonPrimaryOwnerProxyConfiguration {
    NullaryServiceDescriptor<NonPrimaryOwnerProxyConfiguration> SERVICE_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.clustering.web.non-primary-owner-proxy", NonPrimaryOwnerProxyConfiguration.class);

    /**
     * Returns the name of the reverse-proxy handler of the web container whose configuration is used to create the
     * connection pools of the proxy client.
     * @return the name of a reverse-proxy handler
     */
    String getReverseProxy();

    /**
     * Returns the name of the SSL context used by the proxy client to proxy a request via HTTPS, if configured.
     * If unspecified, requests are never proxied via HTTPS, and thus a request received via HTTPS is serviced locally.
     * @return the name of an SSL context, if configured.
     */
    Optional<String> getSSLContext();
}

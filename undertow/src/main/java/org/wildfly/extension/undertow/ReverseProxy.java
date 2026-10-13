/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;

import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Exposes the components of a reverse-proxy handler resource, for use by other subsystems.
 * Unlike the generic {@link Handler#SERVICE_DESCRIPTOR handler capability}, the handler provided here is never wrapped,
 * e.g. by a request controller, and its proxy client is exposed via its concrete type.
 *
 * @author Radoslav Husar
 */
public interface ReverseProxy {
    UnaryServiceDescriptor<ReverseProxy> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.undertow.reverse-proxy", ReverseProxy.class);

    /**
     * Returns the handler that proxies a request to a host selected by the proxy client of this reverse proxy.
     * @return a proxy handler
     */
    HttpHandler getHandler();

    /**
     * Returns the proxy client of this reverse proxy, with which the hosts of this reverse proxy are registered.
     * @return a load balancing proxy client
     */
    LoadBalancingProxyClient getProxyClient();
}

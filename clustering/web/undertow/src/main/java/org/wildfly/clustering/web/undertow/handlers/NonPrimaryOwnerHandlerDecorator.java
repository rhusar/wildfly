/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.util.function.UnaryOperator;

import io.undertow.server.HttpHandler;

import org.wildfly.clustering.web.service.routing.SessionLocality;

/**
 * Decorates the handler chain of a deployment such that a request associated with a session owned by a peer member is
 * proxied to that member, via the proxy shared by every deployment of the same server.
 * Unlike the shared proxy, this decorator is specific to a deployment, since the primary owner of a session is determined
 * by the cache of that deployment.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerHandlerDecorator implements UnaryOperator<HttpHandler> {

    private final NonPrimaryOwnerProxy proxy;
    private final SessionLocality locality;

    /**
     * Creates a decorator of the handler chain of a deployment.
     * @param proxy the shared proxy of the server of the deployment
     * @param locality resolves the primary owner of a session of the deployment
     */
    public NonPrimaryOwnerHandlerDecorator(NonPrimaryOwnerProxy proxy, SessionLocality locality) {
        this.proxy = proxy;
        this.locality = locality;
    }

    @Override
    public HttpHandler apply(HttpHandler next) {
        return new NonPrimaryOwnerProxyHandler(next, this.proxy.getProxyHandler(), NonPrimaryOwnerProxy.UNAVAILABLE_HANDLER, this.proxy.getClient(), this.locality, this.proxy.getServer());
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.StatusCodes;

import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.extension.undertow.ReverseProxy;
import org.wildfly.extension.undertow.Server;

/**
 * Creates the decorator of the handler chain of a deployment that services a request associated with a session owned
 * by a peer member, by directing the configured reverse proxy at the primary owner of that session.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerSessionHandlerDecoratorFactory {

    private NonPrimaryOwnerSessionHandlerDecoratorFactory() {
        // Hide
    }

    /**
     * Returns a decorator of the handler chain of a deployment, for the specified reverse proxy.
     * @param reverseProxy the configured reverse proxy
     * @param locality resolves the primary owner of a session
     * @param server the server of the deployment
     * @return a handler chain decorator
     */
    public static UnaryOperator<HttpHandler> createDecorator(ReverseProxy reverseProxy, SessionLocality locality, Supplier<Server> server) {
        HttpHandler unavailableHandler = new ResponseCodeHandler(StatusCodes.SERVICE_UNAVAILABLE);
        ProxyTargetRegistrar registrar = ProxyTargetRegistrar.of(reverseProxy.getProxyClient());
        return next -> new NonPrimaryOwnerProxyHandler(next, reverseProxy.getHandler(), unavailableHandler, registrar, locality, server);
    }
}

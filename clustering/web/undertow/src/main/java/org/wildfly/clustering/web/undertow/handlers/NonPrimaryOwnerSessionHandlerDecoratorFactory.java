/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.handlers;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.StatusCodes;

import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.clustering.web.undertow.logging.UndertowClusteringLogger;
import org.wildfly.extension.undertow.Server;

/**
 * Creates the decorator of the handler chain of a deployment that services a request associated with a session owned
 * by a peer member.
 * <p>
 * The decoration applied to a deployment is determined by the configured handler: a handler that this factory does not
 * know how to drive yields {@link UnaryOperator#identity()}, i.e. every request is serviced locally, as before.
 *
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerSessionHandlerDecoratorFactory {

    private NonPrimaryOwnerSessionHandlerDecoratorFactory() {
        // Hide
    }

    /**
     * Returns a decorator of the handler chain of a deployment, for the specified handler.
     * @param handlerName the name of the configured handler
     * @param handler the configured handler
     * @param locality resolves the primary owner of a session
     * @param server the server of the deployment
     * @return a handler chain decorator
     */
    public static UnaryOperator<HttpHandler> createDecorator(String handlerName, HttpHandler handler, SessionLocality locality, Supplier<Server> server) {
        // Only a proxy handler whose client supports a deterministic target can be directed at the primary owner of a session
        if ((handler instanceof ProxyHandler proxyHandler) && (proxyHandler.getProxyClient() instanceof LoadBalancingProxyClient proxyClient)) {
            HttpHandler unavailableHandler = new ResponseCodeHandler(StatusCodes.SERVICE_UNAVAILABLE);
            ProxyTargetRegistrar registrar = ProxyTargetRegistrar.of(proxyClient);
            return next -> new NonPrimaryOwnerProxyHandler(next, proxyHandler, unavailableHandler, registrar, locality, server);
        }
        UndertowClusteringLogger.ROOT_LOGGER.unsupportedNonPrimaryOwnerSessionHandler(handlerName, handler.getClass().getName());
        return UnaryOperator.identity();
    }
}

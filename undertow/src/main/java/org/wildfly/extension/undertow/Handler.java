/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import java.util.Collection;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
public interface Handler {
    /**
     * Describes the {@link HttpHandler} provided by a handler resource, for use by other subsystems.
     */
    UnaryServiceDescriptor<HttpHandler> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of(Capabilities.CAPABILITY_HANDLER, HttpHandler.class);

    Collection<AttributeDefinition> getAttributes();

    Class<? extends HttpHandler> getHandlerClass();

    HttpHandler createHttpHandler(final Predicate predicate, final ModelNode model, final HttpHandler next);

}

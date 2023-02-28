/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.undertow;

import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.RestartParentResourceAddHandler;
import org.jboss.as.controller.RestartParentResourceRemoveHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;

/**
 * Resource definition for a Cookie configuration.
 *
 * @author Radoslav Husar
 */
abstract class AbstractCookieDefinition extends PersistentResourceDefinition {

    enum Attribute implements org.jboss.as.clustering.controller.Attribute {
        OPTIONAL_NAME(Constants.NAME, ModelType.STRING),
        REQUIRED_NAME(Constants.NAME, ModelType.STRING, true),
        DOMAIN(Constants.DOMAIN, ModelType.STRING),
        COMMENT(Constants.COMMENT, ModelType.STRING), // TODO This is deprecated
        HTTP_ONLY(Constants.HTTP_ONLY, ModelType.BOOLEAN),
        SECURE(Constants.SECURE, ModelType.BOOLEAN),
        MAX_AGE(Constants.MAX_AGE, ModelType.INT),
        ;
        private final AttributeDefinition definition;

        Attribute(String name, ModelType type) {
            this(name, type, false);
        }

        Attribute(String name, ModelType type, boolean required) {
            this.definition = new SimpleAttributeDefinitionBuilder(name, type)
                    .setRestartAllServices()
                    .setRequired(required)
                    .setAllowExpression(true)
                    .build();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }
    }

    Collection<AttributeDefinition> attributes;

    public AbstractCookieDefinition(PathElement path, Collection<AttributeDefinition> attributes) {
        super(path,
                UndertowExtension.getResolver(path.getKeyValuePair()),
                new SessionCookieAdd(attributes),
                new SessionCookieRemove()
        );
        this.attributes = attributes;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return attributes;
    }

    static CookieConfig getConfig(final Attribute nameAttribute, final ExpressionResolver context, final ModelNode model) throws OperationFailedException {
        if (!model.isDefined()) {
            return null;
        }
        ModelNode nameValue = nameAttribute.getDefinition().resolveModelAttribute(context, model);
        ModelNode domainValue = Attribute.DOMAIN.resolveModelAttribute(context, model);
        ModelNode secureValue = Attribute.SECURE.resolveModelAttribute(context, model);
        ModelNode httpOnlyValue = Attribute.HTTP_ONLY.resolveModelAttribute(context, model);
        ModelNode maxAgeValue = Attribute.MAX_AGE.resolveModelAttribute(context, model);

        final String name = nameValue.isDefined() ? nameValue.asString() : null;
        final String domain = domainValue.isDefined() ? domainValue.asString() : null;
        final Boolean secure = secureValue.isDefined() ? secureValue.asBoolean() : null;
        final Boolean httpOnly = httpOnlyValue.isDefined() ? httpOnlyValue.asBoolean() : null;
        final Integer maxAge = maxAgeValue.isDefined() ? maxAgeValue.asInt() : null;

        return new CookieConfig(name, domain, httpOnly, secure, maxAge);
    }

    private static class SessionCookieAdd extends RestartParentResourceAddHandler {

        Collection<AttributeDefinition> attributes;

        protected SessionCookieAdd(Collection<AttributeDefinition> attributes) {
            super(ServletContainerDefinition.PATH_ELEMENT.getKey());

            this.attributes = attributes;
        }

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            for (AttributeDefinition def : this.attributes) {
                def.validateAndSet(operation, model);
            }
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            ServletContainerAdd.installRuntimeServices(context.getCapabilityServiceTarget(), context, parentAddress, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ServletContainerDefinition.SERVLET_CONTAINER_CAPABILITY.getCapabilityServiceName(parentAddress);
        }
    }

    private static class SessionCookieRemove extends RestartParentResourceRemoveHandler {

        protected SessionCookieRemove() {
            super(ServletContainerDefinition.PATH_ELEMENT.getKey());
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            ServletContainerAdd.installRuntimeServices(context.getCapabilityServiceTarget(), context, parentAddress, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ServletContainerDefinition.SERVLET_CONTAINER_CAPABILITY.getCapabilityServiceName(parentAddress);
        }
    }
}

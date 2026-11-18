/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.clustering.function.Supplier;
import org.wildfly.clustering.web.service.routing.RoutingProvider;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers a routing provider resource definition.
 * @author Paul Ferraro
 */
public abstract class RoutingProviderResourceDefinitionRegistrar implements ChildResourceDefinitionRegistrar, ResourceServiceConfigurator, ResourceModelResolver<RoutingProvider>, UnaryOperator<ResourceDescriptor.Builder> {

    static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of(RoutingProvider.SERVICE_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    private final ResourceRegistration registration;
    private final ResourceOperationRuntimeHandler runtimeHandler;

    RoutingProviderResourceDefinitionRegistrar(ResourceRegistration registration) {
        this(registration, ResourceOperationRuntimeHandler::configureService);
    }

    RoutingProviderResourceDefinitionRegistrar(ResourceRegistration registration, Function<ResourceServiceConfigurator, ResourceOperationRuntimeHandler> runtimeHandlerFactory) {
        this.registration = registration;
        this.runtimeHandler = runtimeHandlerFactory.apply(this);
    }

    /**
     * Returns the runtime handler of this resource, which a child resource may use to restart this resource.
     * @return the runtime handler of this resource
     */
    ResourceOperationRuntimeHandler getRuntimeHandler() {
        return this.runtimeHandler;
    }

    @Override
    public ResourceDescriptor.Builder apply(ResourceDescriptor.Builder builder) {
        return builder.addCapability(CAPABILITY)
                .withRuntimeHandler(this.runtimeHandler)
                ;
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDescriptionResolver resolver = DistributableWebSubsystemResourceDefinitionRegistrar.RESOLVER.createChildResolver(this.registration.getPathElement(), RoutingProviderResourceRegistration.WILDCARD.getPathElement());
        ResourceDescriptor descriptor = this.apply(ResourceDescriptor.builder(resolver)).build();
        ManagementResourceRegistration registration = parent.registerSubModel(ResourceDefinition.builder(this.registration, resolver).build());
        ManagementResourceRegistrar.of(descriptor).register(registration);
        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        return CapabilityServiceInstaller.BlockingBuilder.of(CAPABILITY, Supplier.of(this.resolve(context, model))).build();
    }
}

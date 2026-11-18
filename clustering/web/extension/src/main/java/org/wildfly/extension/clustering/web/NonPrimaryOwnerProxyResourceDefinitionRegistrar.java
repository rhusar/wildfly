/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web;

import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.jboss.as.clustering.controller.CommonServiceDescriptor;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.clustering.function.Supplier;
import org.wildfly.clustering.web.service.routing.NonPrimaryOwnerProxyConfiguration;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceAttributeDefinition;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers a resource definition for the configuration of a proxy that services a request associated with a session
 * owned by a peer member, by proxying that request to the primary owner of its session.
 * <p>
 * This resource is a singleton: the proxy itself is created by the web container, once per server, and is shared by every
 * deployment of that server whose sessions have a primary owner within the cluster.
 * Thus, once configured, every such deployment proxies requests to the primary owner of their session.
 * Since the routing provider exposes this proxy configuration, any change to this resource restarts its parent.
 * @author Radoslav Husar
 */
public class NonPrimaryOwnerProxyResourceDefinitionRegistrar implements ChildResourceDefinitionRegistrar, ResourceServiceConfigurator, ResourceModelResolver<NonPrimaryOwnerProxyConfiguration> {

    static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PathElement.pathElement("proxy", "non-primary-owner"), Stability.EXPERIMENTAL);
    private static final PathElement WILDCARD_PATH = PathElement.pathElement(REGISTRATION.getPathElement().getKey());

    static final RuntimeCapability<Void> CAPABILITY = RuntimeCapability.Builder.of(NonPrimaryOwnerProxyConfiguration.SERVICE_DESCRIPTOR).build();

    /**
     * The name of the capability of an Undertow reverse-proxy handler resource.
     * Referenced by name to avoid a dependency of this subsystem on a specific web container.
     */
    private static final String UNDERTOW_REVERSE_PROXY_CAPABILITY_NAME = "org.wildfly.undertow.reverse-proxy.configuration";

    static final SimpleAttributeDefinition REVERSE_PROXY = new SimpleAttributeDefinitionBuilder("reverse-proxy", ModelType.STRING)
            .setRequired(true)
            .setCapabilityReference(UNDERTOW_REVERSE_PROXY_CAPABILITY_NAME, CAPABILITY)
            .build();

    static final CapabilityReferenceAttributeDefinition<SSLContext> CLIENT_SSL_CONTEXT = new CapabilityReferenceAttributeDefinition.Builder<>("client-ssl-context", CapabilityReference.builder(CAPABILITY, CommonServiceDescriptor.SSL_CONTEXT).build())
            .setRequired(false)
            .build();

    private final ResourceOperationRuntimeHandler parentRuntimeHandler;

    NonPrimaryOwnerProxyResourceDefinitionRegistrar(ResourceOperationRuntimeHandler parentRuntimeHandler) {
        this.parentRuntimeHandler = parentRuntimeHandler;
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDescriptionResolver resolver = DistributableWebSubsystemResourceDefinitionRegistrar.RESOLVER.createChildResolver(REGISTRATION.getPathElement(), WILDCARD_PATH);
        ResourceDescriptor descriptor = ResourceDescriptor.builder(resolver)
                .addCapability(CAPABILITY)
                .addAttributes(List.of(REVERSE_PROXY, CLIENT_SSL_CONTEXT))
                .withRuntimeHandler(ResourceOperationRuntimeHandler.combine(ResourceOperationRuntimeHandler.configureService(this), ResourceOperationRuntimeHandler.restartParent(this.parentRuntimeHandler)))
                .build();
        ManagementResourceRegistration registration = parent.registerSubModel(ResourceDefinition.builder(REGISTRATION, resolver).build());
        ManagementResourceRegistrar.of(descriptor).register(registration);
        return registration;
    }

    @Override
    public NonPrimaryOwnerProxyConfiguration resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        String reverseProxy = REVERSE_PROXY.resolveModelAttribute(context, model).asString();
        Optional<String> sslContext = Optional.ofNullable(CLIENT_SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull());
        return new NonPrimaryOwnerProxyConfiguration() {
            @Override
            public String getReverseProxy() {
                return reverseProxy;
            }

            @Override
            public Optional<String> getSSLContext() {
                return sslContext;
            }
        };
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        return CapabilityServiceInstaller.BlockingBuilder.of(CAPABILITY, Supplier.of(this.resolve(context, model))).build();
    }
}

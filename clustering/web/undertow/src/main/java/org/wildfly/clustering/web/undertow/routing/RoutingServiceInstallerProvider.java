/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.routing;

import java.net.URI;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.ServiceController;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.web.service.routing.LegacyRoutingProviderFactory;
import org.wildfly.clustering.web.service.routing.RoutingProvider;
import org.wildfly.clustering.web.undertow.logging.UndertowClusteringLogger;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.ServerServiceInstallerProvider;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * @author Paul Ferraro
 */
@SuppressWarnings("deprecation")
@MetaInfServices(ServerServiceInstallerProvider.class)
public class RoutingServiceInstallerProvider implements ServerServiceInstallerProvider {

    private final LegacyRoutingProviderFactory legacyProviderFactory;

    public RoutingServiceInstallerProvider() {
        Iterator<LegacyRoutingProviderFactory> factories = ServiceLoader.load(LegacyRoutingProviderFactory.class, LegacyRoutingProviderFactory.class.getClassLoader()).iterator();
        if (!factories.hasNext()) {
            throw new ServiceConfigurationError(LegacyRoutingProviderFactory.class.getName());
        }
        this.legacyProviderFactory = factories.next();
    }

    @Override
    public ResourceServiceInstaller getServiceInstaller(String serverName) {
        return new ResourceServiceInstaller() {
            @Override
            public Consumer<OperationContext> install(OperationContext context) {
                ServiceDependency<RoutingProvider> provider = RoutingServiceInstallerProvider.this.getRoutingProvider(context, serverName);
                ServiceDependency<URI> endpoint = RoutingServiceInstallerProvider.this.getProxyEndpoint(context, serverName);
                ServiceInstaller installer = new ServiceInstaller() {
                    @Override
                    public ServiceController<?> install(RequirementServiceTarget target) {
                        ServiceDependency<String> serverId = ServiceDependency.on(Server.SERVICE_DESCRIPTOR, serverName).map(Server::getRoute);
                        for (ServiceInstaller installer : provider.get().getServiceInstallers(serverName, serverId, endpoint)) {
                            installer.install(target);
                        }
                        return null;
                    }
                };
                return ServiceInstaller.Builder.of(installer, context.getCapabilityServiceSupport()).requires(provider).build().install(context);
            }
        };
    }

    /**
     * Returns a dependency on the endpoint via which the specified server can be reached by its peers, or an empty
     * dependency if that server is not configured to proxy requests for foreign sessions.
     */
    private ServiceDependency<URI> getProxyEndpoint(OperationContext context, String serverName) {
        String serverCapabilityName = RuntimeCapability.resolveCapabilityName(Server.SERVICE_DESCRIPTOR, serverName);
        String endpointCapabilityName = RuntimeCapability.resolveCapabilityName(Server.PROXY_ENDPOINT, serverName);
        return context.hasOptionalCapability(endpointCapabilityName, serverCapabilityName, null) ? ServiceDependency.on(Server.PROXY_ENDPOINT, serverName) : ServiceDependency.empty();
    }

    private ServiceDependency<RoutingProvider> getRoutingProvider(OperationContext context, String serverName) {
        if (context.hasOptionalCapability(RoutingProvider.SERVICE_DESCRIPTOR.getName(), RuntimeCapability.resolveCapabilityName(Server.SERVICE_DESCRIPTOR, serverName), null)) {
            return ServiceDependency.on(RoutingProvider.SERVICE_DESCRIPTOR);
        }
        UndertowClusteringLogger.ROOT_LOGGER.legacyRoutingProviderInUse(serverName);
        return ServiceDependency.of(this.legacyProviderFactory.createRoutingProvider());
    }
}

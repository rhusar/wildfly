/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.routing.infinispan;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.jboss.as.controller.ServiceNameFactory;
import org.wildfly.clustering.infinispan.service.CacheConfigurationServiceInstaller;
import org.wildfly.clustering.infinispan.service.CacheServiceInstaller;
import org.wildfly.clustering.marshalling.protostream.util.StringKeyMapEntry;
import org.wildfly.clustering.server.Registration;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.server.registry.RegistryListener;
import org.wildfly.clustering.server.service.BinaryServiceConfiguration;
import org.wildfly.clustering.server.service.ClusteringServiceDescriptor;
import org.wildfly.clustering.server.service.FilteredBinaryServiceInstallerProvider;
import org.wildfly.clustering.web.service.routing.RouteEndpointRegistry;
import org.wildfly.common.iteration.CompositeIterable;
import org.wildfly.extension.clustering.web.routing.LocalRoutingProvider;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Routing provider backed by an Infinispan cache.
 * @author Paul Ferraro
 */
public class InfinispanRoutingProvider extends LocalRoutingProvider {

    private final BinaryServiceConfiguration configuration;
    private final UnaryOperator<ConfigurationBuilder> configurator;

    public InfinispanRoutingProvider(BinaryServiceConfiguration configuration, UnaryOperator<ConfigurationBuilder> configurator) {
        this.configuration = configuration;
        this.configurator = configurator;
    }

    @Override
    public Iterable<ServiceInstaller> getServiceInstallers(String serverName, ServiceDependency<String> route, ServiceDependency<Map<String, List<InetSocketAddress>>> endpoints) {
        BinaryServiceConfiguration serverConfiguration = this.configuration.withChildName(serverName);
        List<ServiceInstaller> installers = new LinkedList<>();

        // In addition to the route of this member, publish the addresses via which its peers can reach it
        installers.add(ServiceInstaller.BlockingBuilder.of(route.combine(endpoints, StringKeyMapEntry::new))
                .provides(serverConfiguration.resolveServiceName(ClusteringServiceDescriptor.REGISTRY_ENTRY))
                .build());

        installers.add(new CacheConfigurationServiceInstaller(serverConfiguration, CacheConfigurationServiceInstaller.fromTemplate(this.configuration).map(this.configurator)));
        installers.add(new CacheServiceInstaller(serverConfiguration));

        new FilteredBinaryServiceInstallerProvider(Set.of(ClusteringServiceDescriptor.REGISTRY, ClusteringServiceDescriptor.REGISTRY_FACTORY)).apply(serverConfiguration).forEach(installers::add);

        // Expose the endpoints of the routing registry of this server, agnostic of its implementation, for use by a proxy client
        ServiceDependency<CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>>> registry = serverConfiguration.getServiceDependency(ClusteringServiceDescriptor.REGISTRY).map(CacheContainerRegistry.class::cast);
        installers.add(ServiceInstaller.BlockingBuilder.of(registry.map(RegistryRouteEndpointRegistry::new))
                .provides(ServiceNameFactory.resolveServiceName(RouteEndpointRegistry.SERVICE_DESCRIPTOR, serverName))
                .build());

        return new CompositeIterable<>(List.of(installers, super.getServiceInstallers(serverName, route, endpoints)));
    }

    /**
     * Exposes the entries of a routing registry, i.e. the endpoints of each member, keyed by route.
     */
    static class RegistryRouteEndpointRegistry implements RouteEndpointRegistry {
        private final CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry;

        RegistryRouteEndpointRegistry(CacheContainerRegistry<String, Map<String, List<InetSocketAddress>>> registry) {
            this.registry = registry;
        }

        @Override
        public Map<String, Map<String, List<InetSocketAddress>>> getEndpoints() {
            return this.registry.getEntries();
        }

        @Override
        public Registration register(RegistryListener<String, Map<String, List<InetSocketAddress>>> listener) {
            return this.registry.register(listener);
        }
    }
}

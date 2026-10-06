/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.routing;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.ClientMapping;
import org.jboss.as.network.SocketBinding;
import org.jboss.msc.service.ServiceController;
import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.web.service.routing.LegacyRoutingProviderFactory;
import org.wildfly.clustering.web.service.routing.RoutingProvider;
import org.wildfly.clustering.web.undertow.logging.UndertowClusteringLogger;
import org.wildfly.extension.undertow.Constants;
import org.wildfly.extension.undertow.Server;
import org.wildfly.extension.undertow.ServerServiceInstallerProvider;
import org.wildfly.extension.undertow.UndertowListener;
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
                ServiceDependency<Map<String, List<InetSocketAddress>>> endpoints = endpoints(context, serverName);
                ServiceInstaller installer = new ServiceInstaller() {
                    @Override
                    public ServiceController<?> install(RequirementServiceTarget target) {
                        ServiceDependency<String> serverId = ServiceDependency.on(Server.SERVICE_DESCRIPTOR, serverName).map(Server::getRoute);
                        for (ServiceInstaller installer : provider.get().getServiceInstallers(serverName, serverId, endpoints)) {
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
     * Returns a dependency on the addresses, per scheme, via which the listeners of the specified server can be reached
     * by its peers, for publication to the routing registry.
     * <p>
     * The advertised address of a listener is resolved from the {@code client-mappings} of its socket binding, since
     * the bind address of a member is not generally reachable by its peers under NAT, cloud, container, or multi-homed
     * networking. As is the case for the JGroups transport, a client mapping cannot be selected based on the address of
     * the requesting member, and thus the first mapping is used.
     */
    private static ServiceDependency<Map<String, List<InetSocketAddress>>> endpoints(OperationContext context, String serverName) {
        // Read the listeners of this server from the model, since a listener only registers with its server once started
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        List<ServiceDependency<UndertowListener>> listeners = new LinkedList<>();
        for (String type : List.of(Constants.AJP_LISTENER, Constants.HTTP_LISTENER, Constants.HTTPS_LISTENER)) {
            for (String listenerName : resource.getChildrenNames(type)) {
                listeners.add(ServiceDependency.on(UndertowListener.SERVICE_DESCRIPTOR, serverName, listenerName));
            }
        }
        return new ServiceDependency<>() {
            @Override
            public void accept(org.jboss.as.controller.RequirementServiceBuilder<?> builder) {
                listeners.forEach(listener -> listener.accept(builder));
            }

            @Override
            public Map<String, List<InetSocketAddress>> get() {
                Map<String, List<InetSocketAddress>> endpoints = new TreeMap<>();
                for (ServiceDependency<UndertowListener> dependency : listeners) {
                    UndertowListener listener = dependency.get();
                    InetSocketAddress address = address(listener, serverName);
                    if (address != null) {
                        endpoints.computeIfAbsent(listener.getProtocol(), scheme -> new ArrayList<>(2)).add(address);
                    }
                }
                return endpoints;
            }
        };
    }

    private static InetSocketAddress address(UndertowListener listener, String serverName) {
        SocketBinding binding = listener.getSocketBinding();
        List<ClientMapping> mappings = binding.getClientMappings();
        if (!mappings.isEmpty()) {
            // A client mapping cannot be selected based on the address of the requesting member, so use the first one
            ClientMapping mapping = mappings.get(0);
            return InetSocketAddress.createUnresolved(mapping.getDestinationAddress(), mapping.getDestinationPort());
        }
        InetSocketAddress bound = binding.getSocketAddress();
        if (bound.getAddress().isAnyLocalAddress()) {
            // A wildcard address tells a peer nothing about how to reach this member
            UndertowClusteringLogger.ROOT_LOGGER.unresolvableListenerAddress(listener.getName(), serverName, binding.getName());
            return null;
        }
        return InetSocketAddress.createUnresolved(bound.getAddress().getHostAddress(), bound.getPort());
    }

    private ServiceDependency<RoutingProvider> getRoutingProvider(OperationContext context, String serverName) {
        if (context.hasOptionalCapability(RoutingProvider.SERVICE_DESCRIPTOR.getName(), RuntimeCapability.resolveCapabilityName(Server.SERVICE_DESCRIPTOR, serverName), null)) {
            return ServiceDependency.on(RoutingProvider.SERVICE_DESCRIPTOR);
        }
        UndertowClusteringLogger.ROOT_LOGGER.legacyRoutingProviderInUse(serverName);
        return ServiceDependency.of(this.legacyProviderFactory.createRoutingProvider());
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.clustering.dmr.ModelNodes;
import org.jboss.as.clustering.jgroups.ChannelFactory;
import org.jboss.as.clustering.jgroups.ProtocolConfiguration;
import org.jboss.as.clustering.jgroups.ProtocolDefaults;
import org.jboss.as.clustering.jgroups.ProtocolStackConfiguration;
import org.jboss.as.clustering.jgroups.RelayConfiguration;
import org.jboss.as.clustering.jgroups.RemoteSiteConfiguration;
import org.jboss.as.clustering.jgroups.TransportConfiguration;
import org.jboss.as.clustering.jgroups.logging.JGroupsLogger;
import org.jboss.as.clustering.naming.BinderServiceBuilder;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jgroups.Channel;

/**
 * @author Paul Ferraro
 * @author Richard Achmatowicz
 * @author Radoslav Husar
 */
public class StackAddHandler extends AbstractAddStepHandler {

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) {

        PathAddress stackAddress = PathAddress.pathAddress(operation.get(OP_ADDR));

        // add a step to initialize an *optional* TRANSPORT parameter
        if (operation.hasDefined(TransportResourceDefinition.PATH.getKey())) {

            PathAddress address = stackAddress.append(TransportResourceDefinition.PATH);
            ModelNode transportOperation = operation.get(ModelKeys.TRANSPORT).clone();
            transportOperation.get(OPERATION_NAME).set(ADD);
            transportOperation.get(OP_ADDR).set(address.toModelNode());

            // execute the operation using the transport handler
            context.addStep(transportOperation, TransportResourceDefinition.ADD_HANDLER, OperationContext.Stage.MODEL, true);
        }

        // add steps to initialize *optional* PROTOCOL parameters

        if (operation.hasDefined(StackResourceDefinition.PROTOCOLS.getName())) {

            List<ModelNode> protocols = operation.get(StackResourceDefinition.PROTOCOLS.getName()).asList();
            // because we use stage IMMEDIATE when creating protocols, unless we reverse the order
            // of the elements in the LIST, they will get applied in reverse order - the last step
            // added gets executed first

            ListIterator<ModelNode> iterator = protocols.listIterator(protocols.size());
            while (iterator.hasPrevious()) {
                // create an ADD operation to add the protocol=* child
                ModelNode protocolOperation = iterator.previous().clone();
                protocolOperation.get(OPERATION_NAME).set(ModelKeys.ADD_PROTOCOL);
                protocolOperation.get(OP_ADDR).set(stackAddress.toModelNode());

                // execute the operation using the transport handler
                context.addStep(protocolOperation, ProtocolResourceDefinition.ADD_HANDLER, OperationContext.Stage.MODEL, true);
            }
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

        // Because we use child resources in a read-only manner to configure the protocol stack, replace the local model with the full model
        installRuntimeServices(context, operation, Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS)));
    }

    static void installRuntimeServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String name = address.getLastElement().getValue();

        // check that we have enough information to create a stack
        protocolStackSanityCheck(name, model);

        // we need to preserve the order of the protocols as maintained by PROTOCOLS
        // pick up the ordered protocols here as a List<Property> where property is <stackName, ModelNode>
        List<Property> orderedProtocols = getOrderedProtocolPropertyList(model);

        // pick up the transport here and its values
        ModelNode transport = model.get(TransportResourceDefinition.PATH.getKeyValuePair());

        String type = ProtocolResourceDefinition.TYPE.resolveModelAttribute(context, transport).asString();
        ModuleIdentifier module = ModelNodes.asModuleIdentifier(ProtocolResourceDefinition.MODULE.resolveModelAttribute(context, transport));
        boolean shared = TransportResourceDefinition.SHARED.resolveModelAttribute(context, transport).asBoolean();

        Transport transportConfig = new Transport(type, module, shared);

        String machine = ModelNodes.asString(TransportResourceDefinition.MACHINE.resolveModelAttribute(context, transport));
        String rack = ModelNodes.asString(TransportResourceDefinition.RACK.resolveModelAttribute(context, transport));
        String site = ModelNodes.asString(TransportResourceDefinition.SITE.resolveModelAttribute(context, transport));

        transportConfig.setTopology(site, rack, machine);

        initProtocolProperties(context, transport, transportConfig);

        Relay relayConfig = null;
        List<Map.Entry<String, Injector<Channel>>> channels = new LinkedList<>();
        if (model.hasDefined(RelayResourceDefinition.PATH.getKey())) {
            ModelNode relay = model.get(RelayResourceDefinition.PATH.getKeyValuePair());
            String siteName = RelayResourceDefinition.SITE.resolveModelAttribute(context, relay).asString();
            relayConfig = new Relay(siteName);
            initProtocolProperties(context, relay, relayConfig);
            if (relay.hasDefined(RemoteSiteResourceDefinition.WILDCARD_PATH.getKey())) {
                List<RemoteSiteConfiguration> remoteSites = relayConfig.getRemoteSites();
                for (Property remoteSiteProperty: relay.get(RemoteSiteResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                    String remoteSiteName = remoteSiteProperty.getName();
                    String channelName = RemoteSiteResourceDefinition.CHANNEL.resolveModelAttribute(context, remoteSiteProperty.getValue()).asString();
                    RemoteSite remoteSite = new RemoteSite(remoteSiteName, channelName);
                    remoteSites.add(remoteSite);
                    channels.add(new AbstractMap.SimpleImmutableEntry<>(channelName, remoteSite.getChannelInjector()));
                }
            }
        }

        // set up the protocol stack Protocol objects
        ProtocolStack stackConfig = new ProtocolStack(name, transportConfig, relayConfig);
        List<Map.Entry<Protocol, String>> protocolSocketBindings = new ArrayList<>(orderedProtocols.size());
        for (Property protocolProperty : orderedProtocols) {
            ModelNode protocol = protocolProperty.getValue();
            type = ProtocolResourceDefinition.TYPE.resolveModelAttribute(context, protocol).asString();
            module = ModelNodes.asModuleIdentifier(ProtocolResourceDefinition.MODULE.resolveModelAttribute(context, protocol));
            Protocol protocolConfig = new Protocol(type, module);
            initProtocolProperties(context, protocol, protocolConfig);
            stackConfig.getProtocols().add(protocolConfig);
            String protocolSocketBinding = ModelNodes.asString(ProtocolResourceDefinition.SOCKET_BINDING.resolveModelAttribute(context, protocol));
            protocolSocketBindings.add(new AbstractMap.SimpleImmutableEntry<>(protocolConfig, protocolSocketBinding));
        }

        String transportSocketBinding = ModelNodes.asString(ProtocolResourceDefinition.SOCKET_BINDING.resolveModelAttribute(context, transport));
        String diagnosticsSocketBinding = ModelNodes.asString(TransportResourceDefinition.DIAGNOSTICS_SOCKET_BINDING.resolveModelAttribute(context, transport));

        // Setup threading as jgroups properties
        // TODO better place to hard-code jgroups props
        applyThreadPoolConfiguration(ModelKeys.DEFAULT, "thread_pool", context, transport, transportConfig);
        applyThreadPoolConfiguration(ModelKeys.INTERNAL, "internal_thread_pool", context, transport, transportConfig);
        applyThreadPoolConfiguration(ModelKeys.OOB, "oob_thread_pool", context, transport, transportConfig);
        applyThreadPoolConfiguration(ModelKeys.TIMER, "timer", context, transport, transportConfig);

        // create the channel factory service builder
        ServiceTarget target = context.getServiceTarget();
        ServiceBuilder<ChannelFactory> builder = target.addService(ChannelFactoryService.getServiceName(name), new ChannelFactoryService(stackConfig))
                .addDependency(ProtocolDefaultsService.SERVICE_NAME, ProtocolDefaults.class, stackConfig.getDefaultsInjector())
                .addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, stackConfig.getEnvironmentInjector())
                .addDependency(Services.JBOSS_SERVICE_MODULE_LOADER, ModuleLoader.class, stackConfig.getModuleLoaderInjector())
        ;
        // add transport dependencies
        addSocketBindingDependency(builder, transportSocketBinding, transportConfig.getSocketBindingInjector());

        for (Map.Entry<Protocol, String> entry: protocolSocketBindings) {
            addSocketBindingDependency(builder, entry.getValue(), entry.getKey().getSocketBindingInjector());
        }

        // add remaining dependencies
        addSocketBindingDependency(builder, diagnosticsSocketBinding, transportConfig.getDiagnosticsSocketBindingInjector());
        for (Map.Entry<String, Injector<Channel>> entry: channels) {
            builder.addDependency(ChannelService.getServiceName(entry.getKey()), Channel.class, entry.getValue());
        }
        builder.setInitialMode(ServiceController.Mode.ON_DEMAND).install();

        new BinderServiceBuilder(target).build(ChannelFactoryService.createChannelFactoryBinding(name), ChannelFactoryService.getServiceName(name), ChannelFactory.class).install();
    }

    private static void applyThreadPoolConfiguration(String threadPool, String propertyPrefix, OperationContext context, ModelNode transport, TransportConfiguration transportConfig) throws OperationFailedException {
        if (transport.get(ThreadPoolDefinition.pathElement(threadPool).getKeyValuePair()).isDefined()) {
            Map<String, String> properties = transportConfig.getProperties();
            ModelNode threadModel = transport.get(ModelKeys.THREAD_POOL, threadPool);

            if (ThreadPoolDefinition.MIN_THREADS.resolveModelAttribute(context, threadModel).isDefined()) {
                properties.put(propertyPrefix + ".min_threads", ThreadPoolDefinition.MIN_THREADS.resolveModelAttribute(context, threadModel).asString());
            }
            if (ThreadPoolDefinition.MAX_THREADS.resolveModelAttribute(context, threadModel).isDefined()) {
                properties.put(propertyPrefix + ".max_threads", ThreadPoolDefinition.MAX_THREADS.resolveModelAttribute(context, threadModel).asString());
            }
            if (ThreadPoolDefinition.QUEUE_MAX_SIZE.resolveModelAttribute(context, threadModel).isDefined()) {
                int queueSize = ThreadPoolDefinition.QUEUE_MAX_SIZE.resolveModelAttribute(context, threadModel).asInt();
                if (queueSize == 0) {
                    // TODO should we be disabling if set to 0?
                    properties.put(propertyPrefix + ".queue_enabled", Boolean.FALSE.toString());
                } else {
                    properties.put(propertyPrefix + ".queue_enabled", Boolean.TRUE.toString());
                    properties.put(propertyPrefix + ".queue_max_size", String.valueOf(queueSize));
                }
            }
            if (ThreadPoolDefinition.KEEPALIVE_TIME.resolveModelAttribute(context, threadModel).isDefined()) {
                long keepAliveTime = ThreadPoolDefinition.KEEPALIVE_TIME.resolveModelAttribute(context, threadModel).asLong();
                TimeUnit unit = Enum.valueOf(TimeUnit.class, ThreadPoolDefinition.KEEPALIVE_TIME_UNIT.resolveModelAttribute(context, threadModel).asString());
                long keepAliveTimeInSeconds = unit.toSeconds(keepAliveTime);
                properties.put(propertyPrefix + ".keep_alive_time", String.valueOf(keepAliveTimeInSeconds));
            }
        }
    }

    static void removeRuntimeServices(OperationContext context, ModelNode operation, ModelNode model) {
        PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
        String name = address.getLastElement().getValue();

        // remove the ChannelFactoryServiceService
        context.removeService(ChannelFactoryService.getServiceName(name));
        context.removeService(ChannelFactoryService.createChannelFactoryBinding(name).getBinderServiceName());
    }

    private static void initProtocolProperties(OperationContext context, ModelNode protocol, Protocol protocolConfig) throws OperationFailedException {

        Map<String, String> properties = protocolConfig.getProperties();
        // properties are a child resource of protocol
        if (protocol.hasDefined(PropertyResourceDefinition.WILDCARD_PATH.getKey())) {
            for (Property property : protocol.get(PropertyResourceDefinition.WILDCARD_PATH.getKey()).asPropertyList()) {
                // the format of the property elements
                //  "property" => {
                //       "relative-to" => {"value" => "fred"},
                //   }
                String propertyName = property.getName();
                // get the value from the ModelNode {"value" => "fred"}
                ModelNode propertyValue = PropertyResourceDefinition.VALUE.resolveModelAttribute(context, property.getValue());

                properties.put(propertyName, propertyValue.asString());
            }
       }
    }

    public static List<Property> getOrderedProtocolPropertyList(ModelNode stack) {
        // check for the empty ordering list
        if  (!stack.hasDefined(StackResourceDefinition.PROTOCOLS.getName())) {
            return null;
        }

        ModelNode result = new ModelNode();
        ModelNode protocols = stack.get(ProtocolResourceDefinition.WILDCARD_PATH.getKey());

        // PROTOCOLS is a list of protocol names only, reflecting the order in which protocols were added to the stack
        for (ModelNode protocol : stack.get(StackResourceDefinition.PROTOCOLS.getName()).asList()) {
            String protocolName = protocol.asString();
            result.add(protocolName, protocols.get(protocolName));
        }

        return result.asPropertyList();
    }

    private static void addSocketBindingDependency(ServiceBuilder<ChannelFactory> builder, String socketBinding, Injector<SocketBinding> injector) {
        if (socketBinding != null) {
            builder.addDependency(SocketBinding.JBOSS_BINDING_NAME.append(socketBinding), SocketBinding.class, injector);
        }
    }

    /**
     * A check that we have the minimal configuration required to create a protocol stack.
     */
    private static void protocolStackSanityCheck(String stackName, ModelNode model) throws OperationFailedException {

         if (!model.get(TransportResourceDefinition.PATH.getKeyValuePair()).isDefined()) {
            throw JGroupsLogger.ROOT_LOGGER.transportNotDefined(stackName);
         }

         List<Property> protocols = getOrderedProtocolPropertyList(model);
         if ( protocols == null || !(protocols.size() > 0)) {
             throw JGroupsLogger.ROOT_LOGGER.protocolListNotDefined(stackName);
         }
    }

    static class ProtocolStack implements ProtocolStackConfiguration {
        private final InjectedValue<ProtocolDefaults> defaults = new InjectedValue<>();
        private final InjectedValue<ServerEnvironment> environment = new InjectedValue<>();
        private final InjectedValue<ModuleLoader> loader = new InjectedValue<>();

        private final String name;
        private final TransportConfiguration transport;
        private final RelayConfiguration relay;
        private final List<ProtocolConfiguration> protocols = new LinkedList<>();

        ProtocolStack(String name, TransportConfiguration transport, RelayConfiguration relay) {
            this.name = name;
            this.transport = transport;
            this.relay = relay;
        }

        Injector<ProtocolDefaults> getDefaultsInjector() {
            return this.defaults;
        }

        Injector<ServerEnvironment> getEnvironmentInjector() {
            return this.environment;
        }

        Injector<ModuleLoader> getModuleLoaderInjector() {
            return this.loader;
        }

        @Override
        public TransportConfiguration getTransport() {
            return this.transport;
        }

        @Override
        public List<ProtocolConfiguration> getProtocols() {
            return this.protocols;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public ProtocolDefaults getDefaults() {
            return this.defaults.getValue();
        }

        @Override
        public ServerEnvironment getEnvironment() {
            return this.environment.getValue();
        }

        @Override
        public RelayConfiguration getRelay() {
            return this.relay;
        }

        @Override
        public ModuleLoader getModuleLoader() {
            return this.loader.getValue();
        }
    }

    static class Transport extends Protocol implements TransportConfiguration {
        private final InjectedValue<SocketBinding> diagnosticsSocketBinding = new InjectedValue<>();
        private final boolean shared;
        private Topology topology;

        Transport(String name, ModuleIdentifier module, boolean shared) {
            super(name, module);
            this.shared = shared;
        }

        Injector<SocketBinding> getDiagnosticsSocketBindingInjector() {
            return this.diagnosticsSocketBinding;
        }

        @Override
        public boolean isShared() {
            return this.shared;
        }

        @Override
        public Topology getTopology() {
            return this.topology;
        }

        public void setTopology(String site, String rack, String machine) {
            if ((site != null) || (rack != null) || (machine != null)) {
                this.topology = new TopologyImpl(site, rack, machine);
            }
        }

        @Override
        public SocketBinding getDiagnosticsSocketBinding() {
            return this.diagnosticsSocketBinding.getOptionalValue();
        }

        private class TopologyImpl implements Topology {
            private final String site;
            private final String rack;
            private final String machine;

            TopologyImpl(String site, String rack, String machine) {
                this.site = site;
                this.rack = rack;
                this.machine = machine;
            }

            @Override
            public String getMachine() {
                return this.machine;
            }

            @Override
            public String getRack() {
                return this.rack;
            }

            @Override
            public String getSite() {
                return this.site;
            }
        }
    }

    static class Relay extends Protocol implements RelayConfiguration {
        private final List<RemoteSiteConfiguration> remoteSites = new LinkedList<>();
        private final String siteName;

        Relay(String siteName) {
            super("relay.RELAY2", ProtocolConfiguration.DEFAULT_MODULE);
            this.siteName = siteName;
        }

        @Override
        public String getSiteName() {
            return this.siteName;
        }

        @Override
        public List<RemoteSiteConfiguration> getRemoteSites() {
            return this.remoteSites;
        }
    }

    static class RemoteSite implements RemoteSiteConfiguration {
        private final InjectedValue<Channel> channel = new InjectedValue<>();
        private final String name;
        private final String clusterName;

        RemoteSite(String name, String clusterName) {
            this.name = name;
            this.clusterName = clusterName;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public Channel getChannel() {
            return this.channel.getValue();
        }

        Injector<Channel> getChannelInjector() {
            return this.channel;
        }

        @Override
        public String getClusterName() {
            return this.clusterName;
        }
    }

    static class Protocol implements ProtocolConfiguration {
        private final String name;
        private final ModuleIdentifier module;
        private final InjectedValue<SocketBinding> socketBinding = new InjectedValue<>();
        private final Map<String, String> properties = new HashMap<>();

        Protocol(String name, ModuleIdentifier module) {
            this.name = name;
            this.module = module;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public ModuleIdentifier getModule() {
            return this.module;
        }

        @Override
        public Map<String, String> getProperties() {
            return this.properties;
        }

        Injector<SocketBinding> getSocketBindingInjector() {
            return this.socketBinding;
        }

        @Override
        public SocketBinding getSocketBinding() {
            return this.socketBinding.getOptionalValue();
        }
    }
}

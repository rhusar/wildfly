/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jgroups.EmptyMessage;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.blocks.RequestCorrelator.Header;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.fork.UnknownForkHandler;
import org.jgroups.protocols.FORK;
import org.jgroups.protocols.TP;
import org.jgroups.stack.Configurator;
import org.jgroups.stack.DiagnosticsHandler;
import org.jgroups.stack.Protocol;
import org.jgroups.util.StackType;
import org.jgroups.util.Util;
import org.wildfly.clustering.jgroups.spi.ChannelFactory;
import org.wildfly.clustering.jgroups.spi.PhysicalAddressCache;
import org.wildfly.clustering.jgroups.spi.ProtocolConfiguration;
import org.wildfly.clustering.jgroups.spi.ChannelFactoryConfiguration;
import org.wildfly.clustering.jgroups.spi.TLSConfiguration;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Factory for creating fork-able channels.
 * @author Paul Ferraro
 */
public class JChannelFactory implements ChannelFactory {

    private final ChannelFactoryConfiguration configuration;

    public JChannelFactory(ChannelFactoryConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ChannelFactoryConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public JChannel createChannel(String id) throws Exception {
        FORK fork = new FORK();
        fork.enableStats(this.configuration.isStatisticsEnabled());
        fork.setUnknownForkHandler(new UnknownForkHandler() {
            private final short id = ClassConfigurator.getProtocolId(RequestCorrelator.class);

            @Override
            public Object handleUnknownForkStack(Message message, String forkStackId) {
                return this.handle(message);
            }

            @Override
            public Object handleUnknownForkChannel(Message message, String forkChannelId) {
                return this.handle(message);
            }

            private Object handle(Message message) {
                Header header = message.getHeader(this.id);
                // If this is a request expecting a response, don't leave the requester hanging - send an identifiable response on which it can filter
                if ((header != null) && (header.type == Header.REQ) && header.rspExpected()) {
                    Message response = new EmptyMessage(message.src()).setFlag(message.getFlags(), false).clearFlag(Message.Flag.RSVP);
                    if (message.getDest() != null) {
                        response.src(message.getDest());
                    }

                    response.putHeader(FORK.ID, message.getHeader(FORK.ID));
                    response.putHeader(this.id, new Header(Header.RSP, header.req_id, header.corrId));

                    fork.getProtocolStack().getChannel().down(response);
                }
                return null;
            }
        });

        Map<String, SocketBinding> bindings = new HashMap<>();
        // Transport always resides at the bottom of the stack
        List<ProtocolConfiguration<? extends Protocol>> transports = Collections.singletonList(this.configuration.getTransport());
        // Add RELAY2 to the top of the stack, if defined
        List<ProtocolConfiguration<? extends Protocol>> relays = this.configuration.getRelay().isPresent() ? Collections.singletonList(this.configuration.getRelay().get()) : Collections.emptyList();
        List<Protocol> protocols = new ArrayList<>(transports.size() + this.configuration.getProtocols().size() + relays.size() + 1);
        for (List<ProtocolConfiguration<? extends Protocol>> protocolConfigs : List.of(transports, this.configuration.getProtocols(), relays)) {
            for (ProtocolConfiguration<? extends Protocol> protocolConfig : protocolConfigs) {
                protocols.add(protocolConfig.createProtocol(this.configuration));
                bindings.putAll(protocolConfig.getSocketBindings());
            }
        }
        // Add implicit FORK to the top of the stack
        protocols.add(fork);

        // Override the SocketFactory of the transport
        TP transport = (TP) protocols.get(0);
        SocketBindingManager manager = this.configuration.getTransport().getSocketBinding().getSocketBindings();
        Optional<TLSConfiguration> tls = this.configuration.getTransport().getTLSConfiguration();

        transport.setSocketFactory(new ManagedSocketFactory(new ManagedSocketFactory.Configuration() {
            @Override
            public Map<String, SocketBinding> getSocketBindings() {
                return Collections.unmodifiableMap(bindings);
            }

            @Override
            public SocketBindingManager getSocketBindingManager() {
                return manager;
            }

            @Override
            public SSLContext getClientSSLContext() {
                return tls.map(TLSConfiguration::getClientSSLContext).orElse(null);
            }

            @Override
            public SSLContext getServerSSLContext() {
                return tls.map(TLSConfiguration::getServerSSLContext).orElse(null);
            }
        }));

        JChannel channel = createChannel(protocols);

        // After JChannel construction, TP.init() has run and all components (e.g. diag_handler) are now non-null.
        // Apply any component-prefixed transport properties (e.g. diag.passcode) that were deferred.
        StackType ipVersion = Util.getIpStackType();
        Map<String, String> componentProperties = this.configuration.getTransport().getComponentProperties();
        if (!componentProperties.isEmpty()) {
            Util.forAllComponents(transport, (comp, prefix) -> {
                try {
                    Map<String, String> compProps = new HashMap<>();
                    String key = prefix + ".";
                    componentProperties.entrySet().stream()
                        .filter(e -> e.getKey().startsWith(key))
                        .forEach(e -> compProps.put(e.getKey().substring(prefix.length() + 1), e.getValue()));
                    if (!compProps.isEmpty()) {
                        Configurator.resolveAndAssignFields(comp, compProps, ipVersion);
                        Configurator.resolveAndInvokePropertyMethods(comp, compProps, ipVersion);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        // Configure diagnostics socket binding on the handler created by TP.init().
        // JChannel's constructor disables the handler; re-enable and set address/port from the binding.
        this.configuration.getTransport().getDiagnosticsSocketBinding().ifPresent(diagnosticsBinding -> {
            DiagnosticsHandler diagHandler = transport.getDiagnosticsHandler();
            diagHandler.setEnabled(true);
            InetSocketAddress address = diagnosticsBinding.getSocketAddress();
            diagHandler.setBindAddress(address.getAddress());
            if (diagnosticsBinding.getMulticastAddress() != null) {
                diagHandler.setMcastAddress(diagnosticsBinding.getMulticastAddress());
                diagHandler.setPort(diagnosticsBinding.getMulticastPort());
            } else {
                diagHandler.setPort(diagnosticsBinding.getPort());
            }
        });

        channel.setName(this.configuration.getMemberName());
        // Populate cache of physical addresses
        channel.addChannelListener(PhysicalAddressCache.INSTANCE);

        return channel;
    }

    // TODO Remove this once DNS_PING is configurable via an explicit DNSResolver
    private static JChannel createChannel(List<Protocol> protocols) throws Exception {
        // DNS_PING current loads its InitialContextFactory via the TCCL
        ClassLoader loader = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(JChannel.class);
            return new JChannel(protocols);
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(loader);
        }
    }

    @Override
    public boolean isUnknownForkResponse(Message response) {
        return !response.hasPayload();
    }
}

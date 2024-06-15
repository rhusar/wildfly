/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import static org.jboss.as.clustering.jgroups.subsystem.SocketTransportResourceDefinition.Attribute.CLIENT_SOCKET_BINDING;
import static org.jboss.as.clustering.jgroups.subsystem.SocketTransportResourceDefinition.Attribute.CLIENT_SSL_CONTEXT;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.jboss.as.clustering.controller.CommonUnaryRequirement;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jgroups.protocols.BasicTCP;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SimpleSupplierDependency;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * @author Paul Ferraro
 */
public class SocketTransportConfigurationServiceConfigurator<TP extends BasicTCP> extends TransportConfigurationServiceConfigurator<TP> {

    private volatile SupplierDependency<SocketBinding> clientBinding;
    private volatile SupplierDependency<SSLContext> sslContext;

    public SocketTransportConfigurationServiceConfigurator(PathAddress address) {
        super(address);
    }

    @Override
    public <B> ServiceBuilder<B> register(ServiceBuilder<B> builder) {
        return super.register(new CompositeDependency(this.clientBinding, this.sslContext).register(builder));
    }

    @Override
    public ServiceConfigurator configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String bindingName = CLIENT_SOCKET_BINDING.resolveModelAttribute(context, model).asStringOrNull();
        this.clientBinding = (bindingName != null) ? new ServiceSupplierDependency<>(CommonUnaryRequirement.SOCKET_BINDING.getServiceName(context, bindingName)) : new SimpleSupplierDependency<>(null);

        String sslContextName = CLIENT_SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
        this.sslContext = (sslContextName != null) ? new ServiceSupplierDependency<>(CommonUnaryRequirement.SSL_CONTEXT.getServiceName(context, sslContextName)) : new SimpleSupplierDependency<>(null);

        return super.configure(context, model);
    }

    @Override
    public Map<String, SocketBinding> getSocketBindings() {
        Map<String, SocketBinding> bindings = super.getSocketBindings();
        SocketBinding clientBinding = this.clientBinding.get();
        for (String serviceName : Set.of("jgroups.tcp.sock", "jgroups.nio.client")) {
            bindings.put(serviceName, clientBinding);
        }
        return bindings;
    }

    @Override
    public SSLContext getSSLContext() {
        return this.sslContext.get();
    }

    @Override
    public void accept(TP protocol) {
        SocketBinding clientBinding = this.clientBinding.get();
        if (clientBinding != null) {
            InetSocketAddress socketAddress = clientBinding.getSocketAddress();
            this.setValue(protocol, "client_bind_addr", socketAddress.getAddress());
            this.setValue(protocol, "client_bind_port", socketAddress.getPort());
        }
        super.accept(protocol);
    }
}

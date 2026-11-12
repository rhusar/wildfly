/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.messaging.activemq.jms;

import java.util.Collection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.Topic;
import javax.naming.NamingException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.client.impl.TopologyMemberImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.ra.ActiveMQRAConnectionFactory;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManagerFactory;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.naming.NamingContext;
import org.jboss.as.naming.NamingStore;
import org.jboss.as.naming.service.NamingService;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.messaging.activemq._private.MessagingLogger;

/**
 * Service responsible for creating and destroying a client
 * {@code jakarta.jms.Topic}.
 *
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class ExternalJMSTopicService implements Service<Topic> {

    static final String JMS_TOPIC_PREFIX = "jms.topic.";

    private final InjectedValue<NamingStore> namingStoreInjector = new InjectedValue<NamingStore>();
    private final InjectedValue<ExternalPooledConnectionFactoryService> pcfInjector = new InjectedValue<ExternalPooledConnectionFactoryService>();

    private Topic topic;
    private final String topicName;
    private final DestinationConfiguration config;
    private ClientSessionFactory sessionFactory;

    private ExternalJMSTopicService(final String name, final boolean enabledAMQ1Prefix) {
        this.topicName = enabledAMQ1Prefix ? JMS_TOPIC_PREFIX + name : name;
        this.config = null;
    }

    private ExternalJMSTopicService(final DestinationConfiguration config, final boolean enabledAMQ1Prefix) {
        this.topicName = enabledAMQ1Prefix ? JMS_TOPIC_PREFIX + config.getName() : config.getName();
        this.config = config;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        NamingStore namingStore = namingStoreInjector.getOptionalValue();
        if (namingStore != null) {
            final Queue managementQueue = config.getManagementQueue();
            final NamingContext storeBaseContext = new NamingContext(namingStore, null);
            try {
                ConnectionFactory cf = (ConnectionFactory) storeBaseContext.lookup(pcfInjector.getValue().getBindInfo().getAbsoluteJndiName());
                if (cf instanceof ActiveMQRAConnectionFactory) {
                    final ActiveMQRAConnectionFactory raCf = (ActiveMQRAConnectionFactory) cf;
                    final ServerLocator locator = raCf.getDefaultFactory().getServerLocator();
                    final ClientProtocolManagerFactory protocolManagerFactory = locator.getProtocolManagerFactory();
                    ClusterTopologyListener listener = new ClusterTopologyListener() {
                        @Override
                        public void nodeUP(TopologyMember member, boolean last) {
                            try {
                                createTopic(member, raCf, protocolManagerFactory, managementQueue);
                            } catch (JMSException | StartException ex) {
                                MessagingLogger.ROOT_LOGGER.errorf(ex, "Creating topic %s on node UP %s failed", topicName, member.getPrimary().toString());
                                throw new RuntimeException(ex);
                            }
                        }

                        @Override
                        public void nodeDown(long eventUID, String nodeID) {
                        }
                    };
                    locator.addClusterTopologyListener(listener);
                    sessionFactory = locator.createSessionFactory();
                    Collection<TopologyMemberImpl> members = locator.getTopology().getMembers();
                    if (members == null || members.isEmpty()) {
                        config.createTopic(cf, managementQueue, topicName);
                    } else {
                        // The listener creates the topic on these members asynchronously, which would race with this service
                        // (and the deployment depending on it) being reported as started.
                        // So create it on the currently known members synchronously, leaving the listener for members joining later.
                        // Creating it more than once is harmless, as a topic which already exists is tolerated.
                        for (TopologyMember member : members) {
                            createTopic(member, raCf, protocolManagerFactory, managementQueue);
                        }
                    }
                } else {
                    config.createTopic(cf, managementQueue, topicName);
                }
            } catch (Exception ex) {
                MessagingLogger.ROOT_LOGGER.errorf(ex, "Error starting the external queue service %s", ex.getMessage());
                throw new StartException(ex);
            } finally {
                try {
                    storeBaseContext.close();
                } catch (NamingException ex) {
                    MessagingLogger.ROOT_LOGGER.tracef(ex, "Error closing the naming context %s", ex.getMessage());
                }
            }
        }
        topic = ActiveMQDestination.createTopic(topicName);
    }

    /**
     * Creates the topic on the primary node of the specified topology member and, on a best effort basis, on its backup node.
     */
    private void createTopic(TopologyMember member, ActiveMQRAConnectionFactory raCf, ClientProtocolManagerFactory protocolManagerFactory, Queue managementQueue) throws JMSException, StartException {
        TransportConfiguration primary = member.getPrimary();
        if (primary != null) {
            MessagingLogger.ROOT_LOGGER.infof("Creating topic %s on node UP %s - %s", topicName, member.getNodeId(), primary.toString());
            createTopic(primary, raCf, protocolManagerFactory, managementQueue);
        }
        TransportConfiguration backup = member.getBackup();
        if (backup != null) {
            MessagingLogger.ROOT_LOGGER.infof("Creating topic %s on backup node UP %s - %s", topicName, member.getNodeId(), backup.toString());
            try {
                createTopic(backup, raCf, protocolManagerFactory, managementQueue);
            } catch (JMSException | StartException ex) {
                MessagingLogger.ROOT_LOGGER.errorf(ex, "Creating topic %s on node UP %s failed", topicName, backup.toString());
            }
        }
    }

    private void createTopic(TransportConfiguration connector, ActiveMQRAConnectionFactory raCf, ClientProtocolManagerFactory protocolManagerFactory, Queue managementQueue) throws JMSException, StartException {
        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(false, connector)) {
            factory.getServerLocator().setProtocolManagerFactory(protocolManagerFactory);
            factory.setUser(raCf.getResourceAdapter().getUserName());
            factory.setPassword(raCf.getResourceAdapter().getPassword());
            config.createTopic(factory, managementQueue, topicName);
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Override
    public Topic getValue() throws IllegalStateException {
        return topic;
    }

    public static ExternalJMSTopicService installService(final String name, final ServiceName serviceName, final ServiceTarget serviceTarget, final boolean enabledAMQ1Prefix) {
        final ExternalJMSTopicService service = new ExternalJMSTopicService(name, enabledAMQ1Prefix);
        final ServiceBuilder<Topic> serviceBuilder = serviceTarget.addService(serviceName, service);
        serviceBuilder.install();
        return service;
    }

    public static ExternalJMSTopicService installRuntimeTopicService(final DestinationConfiguration config, final ServiceTarget serviceTarget, final ServiceName pcf, final boolean enabledAMQ1Prefix) {
        final ExternalJMSTopicService service = new ExternalJMSTopicService(config, enabledAMQ1Prefix);
        final ServiceBuilder<Topic> serviceBuilder = serviceTarget.addService(config.getDestinationServiceName(), service);
        serviceBuilder.addDependency(NamingService.SERVICE_NAME, NamingStore.class, service.namingStoreInjector);
        serviceBuilder.addDependency(pcf, ExternalPooledConnectionFactoryService.class, service.pcfInjector);
        serviceBuilder.requires(ConnectorServices.RESOURCE_ADAPTER_SERVICE_PREFIX.append(config.getResourceAdapter()));
        serviceBuilder.install();
        return service;
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web.session.infinispan;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Configurations;
import org.infinispan.configuration.cache.ExpirationConfiguration;
import org.infinispan.configuration.cache.PersistenceConfiguration;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.configuration.cache.StoreConfiguration;
import org.infinispan.eviction.EvictionStrategy;
import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceController;
import org.wildfly.clustering.cache.Key;
import org.wildfly.clustering.cache.infinispan.embedded.EmbeddedCacheConfiguration;
import org.wildfly.clustering.cache.infinispan.embedded.container.DataContainerConfigurationBuilder;
import org.wildfly.clustering.infinispan.service.CacheConfigurationServiceInstaller;
import org.wildfly.clustering.infinispan.service.CacheServiceInstaller;
import org.wildfly.clustering.infinispan.service.InfinispanServiceDescriptor;
import org.wildfly.clustering.server.infinispan.dispatcher.CacheContainerCommandDispatcherFactory;
import org.wildfly.clustering.server.service.BinaryServiceConfiguration;
import org.wildfly.clustering.server.service.ClusteringServiceDescriptor;
import org.wildfly.clustering.server.infinispan.registry.CacheContainerRegistry;
import org.wildfly.clustering.session.SessionManagerFactory;
import org.wildfly.clustering.session.cache.affinity.SessionAffinityEndpoint;
import org.wildfly.clustering.session.infinispan.embedded.InfinispanSessionManagerFactory;
import org.wildfly.clustering.session.infinispan.embedded.metadata.SessionMetaDataKey;
import org.wildfly.clustering.web.service.deployment.WebDeploymentConfiguration;
import org.wildfly.clustering.web.service.deployment.WebDeploymentServiceDescriptor;
import org.wildfly.clustering.web.service.routing.RouteLocatorProvider;
import org.wildfly.clustering.web.service.routing.SessionLocality;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementConfiguration;
import org.wildfly.clustering.web.service.session.DistributableSessionManagementProvider;
import org.wildfly.clustering.web.service.session.SessionManagerFactoryConfiguration;
import org.wildfly.extension.clustering.web.routing.infinispan.InfinispanSessionLocality;
import org.wildfly.extension.clustering.web.session.AbstractSessionManagementProvider;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.subsystem.service.DeploymentServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

import jakarta.servlet.ServletContext;

/**
 * An Infinispan cache-based {@link DistributableSessionManagementProvider}.
 * @author Paul Ferraro
 */
public class InfinispanSessionManagementProvider extends AbstractSessionManagementProvider {

    public InfinispanSessionManagementProvider(DistributableSessionManagementConfiguration<DeploymentUnit> configuration, BinaryServiceConfiguration cacheConfiguration, RouteLocatorProvider locatorProvider) {
        super(configuration, cacheConfiguration, locatorProvider);
    }

    @Override
    public <C> DeploymentServiceInstaller getSessionManagerFactoryServiceInstaller(SessionManagerFactoryConfiguration<C> configuration) {
        BinaryServiceConfiguration templateCacheConfiguration = this.getCacheConfiguration();
        BinaryServiceConfiguration deploymentCacheConfiguration = templateCacheConfiguration.withChildName(configuration.getDeploymentName());

        UnaryOperator<ConfigurationBuilder> configurator = new UnaryOperator<>() {
            @Override
            public ConfigurationBuilder apply(ConfigurationBuilder builder) {
                // Ensure expiration is not enabled on cache
                ExpirationConfiguration expiration = builder.expiration().create();
                if ((expiration.lifespan() >= 0) || (expiration.maxIdle() >= 0)) {
                    builder.expiration().lifespan(-1).maxIdle(-1);
                }

                OptionalInt size = configuration.getSizeThreshold();
                Optional<Duration> idleThreshold = getSessionManagementConfiguration().getIdleThreshold();

                EvictionStrategy strategy = size.isPresent() || idleThreshold.isPresent() ? EvictionStrategy.REMOVE : EvictionStrategy.NONE;
                builder.memory().storage(StorageType.HEAP).whenFull(strategy);
                if (strategy.isEnabled()) {
                    // When an idle-timeout is configured without a size threshold, the cache's size limit must still be configured due to Infinispan's requirements.
                    // As a workaround we explicitly set maxCount(..) to Integer.MAX_VALUE.
                    // This in effect ensures that eviction is governed solely by idleness rather than hitting of the size constraint.
                    builder.memory().maxCount(size.orElse(Integer.MAX_VALUE));
                    // Only evict creation meta-data entries
                    // We will cascade eviction to the remaining entries for a given session
                    DataContainerConfigurationBuilder container = builder.addModule(DataContainerConfigurationBuilder.class);
                    container.evictable(SessionMetaDataKey.class::isInstance);
                    idleThreshold.ifPresent(container::idleTimeout);
                }

                PersistenceConfiguration persistence = builder.persistence().create();
                // If cache is configured to passivate and purge on startup, but application does not define passivation thresholds, then remove useless stores
                if (!strategy.isEnabled() && persistence.passivation() && persistence.stores().stream().allMatch(StoreConfiguration::purgeOnStartup)) {
                    builder.persistence().passivation(false).clearStores();
                }
                return builder;
            }
        };
        DeploymentServiceInstaller configurationInstaller = new CacheConfigurationServiceInstaller(deploymentCacheConfiguration, CacheConfigurationServiceInstaller.fromTemplate(templateCacheConfiguration).map(configurator));
        DeploymentServiceInstaller cacheInstaller = new CacheServiceInstaller(deploymentCacheConfiguration);

        ServiceDependency<CacheContainerCommandDispatcherFactory> commandDispatcherFactory = deploymentCacheConfiguration.getServiceDependency(ClusteringServiceDescriptor.COMMAND_DISPATCHER_FACTORY).map(CacheContainerCommandDispatcherFactory.class::cast);
        ServiceDependency<Cache<?, ?>> cache = deploymentCacheConfiguration.getServiceDependency(InfinispanServiceDescriptor.CACHE);
        EmbeddedCacheConfiguration cacheConfiguration = new EmbeddedCacheConfiguration() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> Cache<K, V> getCache() {
                return (Cache<K, V>) cache.get();
            }

            @Override
            public boolean isFaultTolerant() {
                return true;
            }
        };
        Supplier<SessionManagerFactory<ServletContext, C>> factory = new Supplier<>() {
            @Override
            public SessionManagerFactory<ServletContext, C> get() {
                return new InfinispanSessionManagerFactory<>(new InfinispanSessionManagerFactory.Configuration<C>() {
                    @Override
                    public EmbeddedCacheConfiguration getCacheConfiguration() {
                        return cacheConfiguration;
                    }

                    @Override
                    public CacheContainerCommandDispatcherFactory getCommandDispatcherFactory() {
                        return commandDispatcherFactory.get();
                    }

                    @Override
                    public org.wildfly.clustering.session.SessionManagerFactoryConfiguration<C> getSessionManagerFactoryConfiguration() {
                        return configuration;
                    }
                });
            }
        };
        DeploymentServiceInstaller installer = ServiceInstaller.BlockingBuilder.of(factory)
                .provides(WebDeploymentServiceDescriptor.SESSION_MANAGER_FACTORY.resolve(configuration.getDeploymentUnit()))
                .requires(List.of(cache, commandDispatcherFactory))
                .withLifecycle(BlockingLifecycle.autoClose())
                .build();

        return DeploymentServiceInstaller.combine(configurationInstaller, cacheInstaller, installer);
    }

    @Override
    public DeploymentServiceInstaller getSessionLocalityServiceInstaller(WebDeploymentConfiguration configuration) {
        DeploymentUnit unit = configuration.getDeploymentUnit();
        BinaryServiceConfiguration templateCacheConfiguration = this.getCacheConfiguration();
        ServiceDependency<Configuration> cacheConfiguration = templateCacheConfiguration.getServiceDependency(InfinispanServiceDescriptor.CACHE_CONFIGURATION);
        ServiceDependency<Cache<Key<String>, ?>> cache = templateCacheConfiguration.withChildName(configuration.getDeploymentName()).getServiceDependency(InfinispanServiceDescriptor.CACHE).map(Cache.class::cast);
        ServiceDependency<CacheContainerRegistry<String, SessionAffinityEndpoint>> registry = templateCacheConfiguration.withChildName(configuration.getServerName()).getServiceDependency(ClusteringServiceDescriptor.REGISTRY).map(CacheContainerRegistry.class::cast);

        ServiceInstaller localInstaller = ServiceInstaller.BlockingBuilder.of(org.wildfly.clustering.function.Supplier.of(SessionLocality.LOCAL))
                .provides(WebDeploymentServiceDescriptor.SESSION_LOCALITY.resolve(unit))
                .build();

        return ServiceInstaller.Builder.of(new ServiceInstaller() {
            @Override
            public ServiceController<?> install(RequirementServiceTarget target) {
                // A session of a local cache is, by definition, always read locally
                if (!Configurations.needSegments(cacheConfiguration.get())) {
                    return localInstaller.install(target);
                }
                return ServiceInstaller.BlockingBuilder.of(cache.combine(registry, InfinispanSessionLocality::new))
                        .provides(WebDeploymentServiceDescriptor.SESSION_LOCALITY.resolve(unit))
                        .build()
                        .install(target);
            }
        }, unit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT)).requires(cacheConfiguration).build();
    }
}

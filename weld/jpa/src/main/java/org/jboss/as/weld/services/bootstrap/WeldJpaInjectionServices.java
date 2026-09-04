/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld.services.bootstrap;

import static org.jboss.as.weld.util.ResourceInjectionUtilities.getResourceAnnotated;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceProperty;
import jakarta.persistence.PersistenceUnit;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jboss.as.jpa.container.PersistenceUnitSearch;
import org.jboss.as.jpa.container.TransactionScopedEntityManager;
import org.jboss.as.jpa.processor.JpaAttachments;
import org.jboss.as.jpa.service.PersistenceUnitServiceImpl;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.weld.logging.WeldLogger;
import org.jboss.as.weld.util.ImmediateResourceReferenceFactory;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.weld.injection.spi.JpaInjectionServices;
import org.jboss.weld.injection.spi.ResourceReference;
import org.jboss.weld.injection.spi.ResourceReferenceFactory;
import org.jboss.weld.injection.spi.helpers.SimpleResourceReference;
import org.jipijapa.plugin.spi.PersistenceUnitMetadata;
import org.wildfly.transaction.client.ContextTransactionManager;

public class WeldJpaInjectionServices implements JpaInjectionServices {

    private DeploymentUnit deploymentUnit;

    public WeldJpaInjectionServices(DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
    }

    @Override
    public ResourceReferenceFactory<EntityManager> registerPersistenceContextInjectionPoint(final InjectionPoint injectionPoint) {
        //TODO: cache this stuff
        final PersistenceContext context = getResourceAnnotated(injectionPoint).getAnnotation(PersistenceContext.class);
        if (context == null) {
            throw WeldLogger.ROOT_LOGGER.annotationNotFound(PersistenceContext.class, injectionPoint.getMember());
        }
        final String scopedPuName = getScopedPUName(deploymentUnit, context.unitName(), injectionPoint.getMember());
        final ServiceName persistenceUnitServiceName = PersistenceUnitServiceImpl.getPUServiceName(scopedPuName);

        //the persistence unit service is not necessarily registered yet, see LazyFactory
        final ServiceController<?> serviceController = deploymentUnit.getServiceRegistry().getService(persistenceUnitServiceName);
        if (serviceController != null) {
            final PersistenceUnitServiceImpl persistenceUnitService = (PersistenceUnitServiceImpl) serviceController.getValue();
            if (persistenceUnitService.getEntityManagerFactory() != null) {
                return new EntityManagerResourceReferenceFactory(scopedPuName, persistenceUnitService.getEntityManagerFactory(), context, deploymentUnit.getAttachment(JpaAttachments.TRANSACTION_SYNCHRONIZATION_REGISTRY), ContextTransactionManager.getInstance());
            }
        }
        final ServiceRegistry serviceRegistry = deploymentUnit.getServiceRegistry();
        //resolve the attachment now, the deployment unit is released by cleanup() before the resource is created
        final TransactionSynchronizationRegistry transactionSynchronizationRegistry = deploymentUnit.getAttachment(JpaAttachments.TRANSACTION_SYNCHRONIZATION_REGISTRY);
        return new LazyFactory<>(serviceRegistry, persistenceUnitServiceName, scopedPuName,
                persistenceUnitService -> TransactionScopedEntityManager.create(
                        scopedPuName,
                        getProperties(context),
                        persistenceUnitService.getEntityManagerFactory(),
                        context.synchronization(),
                        transactionSynchronizationRegistry,
                        ContextTransactionManager.getInstance()));
    }

    @Override
    public ResourceReferenceFactory<EntityManagerFactory> registerPersistenceUnitInjectionPoint(final InjectionPoint injectionPoint) {
        //TODO: cache this stuff
        final PersistenceUnit context = getResourceAnnotated(injectionPoint).getAnnotation(PersistenceUnit.class);
        if (context == null) {
            throw WeldLogger.ROOT_LOGGER.annotationNotFound(PersistenceUnit.class, injectionPoint.getMember());
        }
        final String scopedPuName = getScopedPUName(deploymentUnit, context.unitName(), injectionPoint.getMember());
        final ServiceName persistenceUnitServiceName = PersistenceUnitServiceImpl.getPUServiceName(scopedPuName);

        //the persistence unit service is not necessarily registered yet, see LazyFactory
        final ServiceController<?> serviceController = deploymentUnit.getServiceRegistry().getService(persistenceUnitServiceName);
        if (serviceController != null) {
            final PersistenceUnitServiceImpl persistenceUnitService = (PersistenceUnitServiceImpl) serviceController.getValue();
            if (persistenceUnitService.getEntityManagerFactory() != null) {
                return new ImmediateResourceReferenceFactory<>(persistenceUnitService.getEntityManagerFactory());
            }
        }
        final ServiceRegistry serviceRegistry = deploymentUnit.getServiceRegistry();
        return new LazyFactory<>(serviceRegistry, persistenceUnitServiceName, scopedPuName,
                PersistenceUnitServiceImpl::getEntityManagerFactory);
    }

    @Override
    public void cleanup() {
        deploymentUnit = null;
    }

    private String getScopedPUName(final DeploymentUnit deploymentUnit, final String persistenceUnitName, Member injectionPoint) {
        PersistenceUnitMetadata scopedPu;
        scopedPu = PersistenceUnitSearch.resolvePersistenceUnitSupplier(deploymentUnit, persistenceUnitName);
        if (null == scopedPu) {
            throw WeldLogger.ROOT_LOGGER.couldNotFindPersistenceUnit(persistenceUnitName, deploymentUnit.getName(), injectionPoint);
        }
        return scopedPu.getScopedPersistenceUnitName();
    }

    private static Map getProperties(PersistenceContext context) {
        HashMap map = new HashMap();
        for (PersistenceProperty property : context.properties()) {
            map.put(property.name(), property.value());
        }
        return map;
    }


    private static class EntityManagerResourceReferenceFactory implements ResourceReferenceFactory<EntityManager> {
        private final String scopedPuName;
        private final EntityManagerFactory entityManagerFactory;
        private final PersistenceContext context;
        private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
        private final TransactionManager transactionManager;

        public EntityManagerResourceReferenceFactory(String scopedPuName, EntityManagerFactory entityManagerFactory, PersistenceContext context, TransactionSynchronizationRegistry transactionSynchronizationRegistry, TransactionManager transactionManager) {
            this.scopedPuName = scopedPuName;
            this.entityManagerFactory = entityManagerFactory;
            this.context = context;
            this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
            this.transactionManager = transactionManager;
        }

        @Override
        public ResourceReference<EntityManager> createResource() {
            final TransactionScopedEntityManager result = TransactionScopedEntityManager.create(scopedPuName, getProperties(context), entityManagerFactory, context.synchronization(), transactionSynchronizationRegistry, transactionManager);
            return new SimpleResourceReference<>(result);
        }

    }

    /**
     * Defers resolution of the persistence unit service to {@link #createResource()}, which additionally covers the
     * case where the service is not registered yet: with an {@code initialize-in-order} EAR the top level
     * {@code WeldStartService} can start before the INSTALL phase of a sub-deployment registers its persistence unit
     * services (WFLY-22209).
     */
    private static class LazyFactory<T> implements ResourceReferenceFactory<T> {
        public static final String MSC_SERVICE_THREAD = "MSC service thread";
        public static final String INJECTION_CANNOT_BE_PERFORMED_WITHIN_MSC_SERVICE_THREAD = "injection cannot be performed from JBoss Modular Service Container (MSC) service thread";
        private final ServiceRegistry serviceRegistry;
        private final ServiceName persistenceUnitServiceName;
        private final String scopedPuName;
        private final Function<PersistenceUnitServiceImpl, T> resolver;

        public LazyFactory(ServiceRegistry serviceRegistry, ServiceName persistenceUnitServiceName, String scopedPuName, Function<PersistenceUnitServiceImpl, T> resolver) {
            this.serviceRegistry = serviceRegistry;
            this.persistenceUnitServiceName = persistenceUnitServiceName;
            this.scopedPuName = scopedPuName;
            this.resolver = resolver;
        }

        @Override
        public ResourceReference<T> createResource() {
            final ServiceController<?> serviceController = serviceRegistry.getRequiredService(persistenceUnitServiceName);
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean failed = new AtomicBoolean();
            final AtomicBoolean removed = new AtomicBoolean();

            serviceController.addListener(
                    new LifecycleListener() {

                        @Override
                        public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                            if (event == LifecycleEvent.UP) {
                                latch.countDown();
                                controller.removeListener(this);
                            } else if (event == LifecycleEvent.FAILED) {
                                failed.set(true);
                                latch.countDown();
                            } else if (event == LifecycleEvent.REMOVED) {
                                removed.set(true);
                                latch.countDown();
                            }
                        }
                    }
            );

            try {
                // ensure that injection of a persistence unit doesn't cause the MSC service thread to block
                assert !Thread.currentThread().getName().startsWith(MSC_SERVICE_THREAD) : INJECTION_CANNOT_BE_PERFORMED_WITHIN_MSC_SERVICE_THREAD;
                latch.await();
                if (failed.get()) {
                    throw WeldLogger.ROOT_LOGGER.persistenceUnitFailed(scopedPuName);
                } else if (removed.get()) {
                    throw WeldLogger.ROOT_LOGGER.persistenceUnitRemoved(scopedPuName);
                }
            } catch (InterruptedException e) {
                // Thread was interrupted, which we will preserve in case a higher level operation needs to see it.
                Thread.currentThread().interrupt();
                // rather than just returning the current EntityManagerFactory (might be null or not null),
                // fail with a runtime exception.
                throw new RuntimeException(e);
            }
            final PersistenceUnitServiceImpl persistenceUnitService = (PersistenceUnitServiceImpl) serviceController.getValue();
            return new ResourceReference<>() {
                @Override
                public T getInstance() {
                    return resolver.apply(persistenceUnitService);
                }

                @Override
                public void release() {
                }
            };
        }
    }
}

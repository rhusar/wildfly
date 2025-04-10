/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.subsystems.datasources;

import static org.jboss.as.connector.logging.ConnectorLogger.SUBSYSTEM_DATASOURCES_LOGGER;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_CLASS_NAME;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_DATASOURCE_CLASS_NAME;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_MAJOR_VERSION;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_MINOR_VERSION;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_MODULE_NAME;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_NAME;
import static org.jboss.as.connector.subsystems.datasources.Constants.DRIVER_XA_DATASOURCE_CLASS_NAME;
import static org.jboss.as.connector.subsystems.datasources.Constants.JDBC_DRIVER_ATTRIBUTES;
import static org.jboss.as.connector.subsystems.datasources.Constants.MODULE_SLOT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.sql.Driver;
import java.util.Arrays;
import java.util.Collection;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.connector.services.driver.DriverService;
import org.jboss.as.connector.services.driver.InstalledDriver;
import org.jboss.as.connector.services.driver.registry.DriverRegistry;
import org.jboss.as.connector.util.ConnectorServices;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleNotFoundException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Operation handler responsible for adding a jdbc driver.
 *
 * @author John Bailey
 */
public class JdbcDriverAdd extends AbstractAddStepHandler {
    private static final Collection<AttributeDefinition> ATTRIBUTES = Stream.concat(Arrays.stream(JDBC_DRIVER_ATTRIBUTES), Stream.of(DRIVER_NAME)).collect(Collectors.toList());
    static final JdbcDriverAdd INSTANCE = new JdbcDriverAdd(ATTRIBUTES);

    private JdbcDriverAdd(Collection<AttributeDefinition> attributes) {
        super(attributes);
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final ModelNode address = operation.require(OP_ADDR);
        final String driverName = PathAddress.pathAddress(address).getLastElement().getValue();
        if (operation.get(DRIVER_NAME.getName()).isDefined() && !driverName.equals(operation.get(DRIVER_NAME.getName()).asString())) {
            throw ConnectorLogger.ROOT_LOGGER.driverNameAndResourceNameNotEquals(operation.get(DRIVER_NAME.getName()).asString(), driverName);
        }
        String moduleName = DRIVER_MODULE_NAME.resolveModelAttribute(context, model).asString();
        final Integer majorVersion = model.hasDefined(DRIVER_MAJOR_VERSION.getName()) ? DRIVER_MAJOR_VERSION.resolveModelAttribute(context, model).asInt() : null;
        final Integer minorVersion = model.hasDefined(DRIVER_MINOR_VERSION.getName()) ? DRIVER_MINOR_VERSION.resolveModelAttribute(context, model).asInt() : null;
        final String driverClassName = model.hasDefined(DRIVER_CLASS_NAME.getName()) ? DRIVER_CLASS_NAME.resolveModelAttribute(context, model).asString() : null;
        final String dataSourceClassName = model.hasDefined(DRIVER_DATASOURCE_CLASS_NAME.getName()) ? DRIVER_DATASOURCE_CLASS_NAME.resolveModelAttribute(context, model).asString() : null;
        final String xaDataSourceClassName = model.hasDefined(DRIVER_XA_DATASOURCE_CLASS_NAME.getName()) ? DRIVER_XA_DATASOURCE_CLASS_NAME.resolveModelAttribute(context, model).asString() : null;

        final ServiceTarget target = context.getServiceTarget();

        final String moduleId;
        final Module module;
        String slot = model.hasDefined(MODULE_SLOT.getName()) ? MODULE_SLOT.resolveModelAttribute(context, model).asString() : null;

        try {
            moduleId = ModuleIdentifierUtil.canonicalModuleIdentifier(moduleName, slot);
            module = Module.getCallerModuleLoader().loadModule(moduleId);
        } catch (ModuleNotFoundException e) {
            throw new OperationFailedException(ConnectorLogger.ROOT_LOGGER.missingDependencyInModuleDriver(moduleName, e.getMessage()), e);
        } catch (ModuleLoadException e) {
            throw new OperationFailedException(ConnectorLogger.ROOT_LOGGER.failedToLoadModuleDriver(moduleName), e);
        }

        if (dataSourceClassName != null) {
            Class<? extends DataSource> dsCls;
            try {
                dsCls = module.getClassLoader().loadClass(dataSourceClassName).asSubclass(DataSource.class);
            } catch (ClassNotFoundException  e) {
                throw SUBSYSTEM_DATASOURCES_LOGGER.failedToLoadDataSourceClass(dataSourceClassName, e);
            } catch (ClassCastException e) {
                throw SUBSYSTEM_DATASOURCES_LOGGER.notAValidDataSourceClass(dataSourceClassName, DataSource.class.getName());
            }
            checkDSCls(dsCls, DataSource.class);
        }
        if (xaDataSourceClassName != null) {
            Class<? extends XADataSource> dsCls;
            try {
                dsCls = module.getClassLoader().loadClass(xaDataSourceClassName).asSubclass(XADataSource.class);
            } catch (ClassNotFoundException e) {
                throw SUBSYSTEM_DATASOURCES_LOGGER.failedToLoadDataSourceClass(xaDataSourceClassName, e);
            } catch (ClassCastException e) {
                throw SUBSYSTEM_DATASOURCES_LOGGER.notAValidDataSourceClass(xaDataSourceClassName, XADataSource.class.getName());
            }
            checkDSCls(dsCls, XADataSource.class);
        }
        if (driverClassName == null) {
            final ServiceLoader<Driver> serviceLoader = module.loadService(Driver.class);
            boolean driverLoaded = false;
            if (serviceLoader != null) {
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(module.getClassLoader());
                try {
                for (Driver driver : serviceLoader) {
                    startDriverServices(target, moduleId, driver, driverName, majorVersion, minorVersion, dataSourceClassName, xaDataSourceClassName);
                    driverLoaded = true;
                    //just consider first definition and create service for this. User can use different implementation only
                    // w/ explicit declaration of driver-class attribute
                    break;
                }
                } finally {
                    Thread.currentThread().setContextClassLoader(tccl);
                }
            }
            if (!driverLoaded)
                SUBSYSTEM_DATASOURCES_LOGGER.cannotFindDriverClassName(driverName);
        } else {
            try {
                final Class<? extends Driver> driverClass = module.getClassLoader().loadClass(driverClassName)
                        .asSubclass(Driver.class);
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                Driver driver = null;
                try {
                    Thread.currentThread().setContextClassLoader(module.getClassLoader());
                    final Constructor<? extends Driver> constructor = driverClass.getConstructor();
                    driver = constructor.newInstance();
                } finally {
                    Thread.currentThread().setContextClassLoader(tccl);
                }
                startDriverServices(target, moduleId, driver, driverName, majorVersion, minorVersion, dataSourceClassName, xaDataSourceClassName);
            } catch (Exception e) {
                SUBSYSTEM_DATASOURCES_LOGGER.cannotInstantiateDriverClass(driverClassName, e);
                throw new OperationFailedException(ConnectorLogger.ROOT_LOGGER.cannotInstantiateDriverClass(driverClassName));
            }
        }
    }

    public static void startDriverServices(final ServiceTarget target, final String moduleId, Driver driver, final String driverName, final Integer majorVersion,
                                           final Integer minorVersion, final String dataSourceClassName, final String xaDataSourceClassName) throws IllegalStateException {
        final int majorVer = driver.getMajorVersion();
        final int minorVer = driver.getMinorVersion();
        if ((majorVersion != null && majorVersion != majorVer)
                || (minorVersion != null && minorVersion != minorVer)) {
            throw ConnectorLogger.ROOT_LOGGER.driverVersionMismatch();
        }

        final boolean compliant = driver.jdbcCompliant();
        if (compliant) {
            SUBSYSTEM_DATASOURCES_LOGGER.deployingCompliantJdbcDriver(driver.getClass(), majorVer, minorVer);
        } else {
            SUBSYSTEM_DATASOURCES_LOGGER.deployingNonCompliantJdbcDriver(driver.getClass(), majorVer, minorVer);
        }
        InstalledDriver driverMetadata = new InstalledDriver(driverName, moduleId, driver.getClass().getName(),
                dataSourceClassName, xaDataSourceClassName, majorVer, minorVer, compliant);
        DriverService driverService = new DriverService(driverMetadata, driver);
        final ServiceBuilder<Driver> builder = target.addService(ServiceName.JBOSS.append("jdbc-driver", driverName.replaceAll("\\.", "_")), driverService)
                .addDependency(ConnectorServices.JDBC_DRIVER_REGISTRY_SERVICE, DriverRegistry.class,
                        driverService.getDriverRegistryServiceInjector())
                .setInitialMode(ServiceController.Mode.ACTIVE);
        builder.install();
    }

    static <T> void checkDSCls(Class<? extends T> dsCls, Class<T> t) throws OperationFailedException {
        if (Modifier.isInterface(dsCls.getModifiers()) || Modifier.isAbstract(dsCls.getModifiers())) {
            throw SUBSYSTEM_DATASOURCES_LOGGER.notAValidDataSourceClass(dsCls.getName(), t.getName());
        }
    }


}

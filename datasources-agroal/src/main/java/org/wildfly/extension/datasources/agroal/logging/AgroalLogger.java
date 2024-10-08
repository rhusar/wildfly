/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.datasources.agroal.logging;

import io.agroal.api.AgroalDataSource;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.msc.service.StartException;

import java.lang.invoke.MethodHandles;
import java.sql.SQLException;

import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
@MessageLogger(projectCode = "WFLYAG", length = 4)
public interface AgroalLogger extends BasicLogger {

    AgroalLogger DRIVER_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), AgroalLogger.class, "org.wildfly.extension.datasources.agroal.driver");

    AgroalLogger SERVICE_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), AgroalLogger.class, "org.wildfly.extension.datasources.agroal");

    AgroalLogger POOL_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), AgroalLogger.class, "io.agroal.pool");

    // --- Extension //

    @LogMessage(level = INFO)
    @Message(id = 1, value = "Adding deployment processors for DataSourceDefinition annotation and resource-ref entries")
    void addingDeploymentProcessors();

    // --- Datasource service //

    @LogMessage(level = INFO)
    @Message(id = 101, value = "Started datasource '%s' bound to [%s]")
    void startedDataSource(String datasource, String jndiName);

    @LogMessage(level = INFO)
    @Message(id = 102, value = "Stopped datasource '%s'")
    void stoppedDataSource(String datasource);

    @LogMessage(level = INFO)
    @Message(id = 103, value = "Started xa-datasource '%s' bound to [%s]")
    void startedXADataSource(String datasource, String jndiName);

    @LogMessage(level = INFO)
    @Message(id = 104, value = "Stopped xa-datasource '%s'")
    void stoppedXADataSource(String datasource);

    @Message(id = 105, value = "Exception starting datasource '%s'")
    StartException datasourceStartException(@Cause SQLException cause, String dataSourceName);

    @Message(id = 106, value = "Exception starting xa-datasource '%s'")
    StartException xaDatasourceStartException(@Cause SQLException cause, String dataSourceName);

    @Message(id = 107, value = "Invalid connection provider. Either a java.sql.Driver or javax.sql.DataSource implementation is required. Fix the connection-provider for the driver")
    StartException invalidConnectionProvider();

    @Message(id = 108, value = "An xa-datasource requires a javax.sql.XADataSource as connection provider. Fix the connection-provider for the driver")
    StartException invalidXAConnectionProvider();

    @Message(id = 109, value = "Could not start datasource: transaction manager is missing")
    StartException missingTransactionManager();

    @Message(id = 110, value = "Error obtaining credentials from authentication context for datasource '%s'")
    StartException invalidAuthentication(@Cause Throwable cause, String dataSourceName);

    @Message(id = 111, value = "CredentialSourceSupplier for datasource '%s' is invalid")
    StartException invalidCredentialSourceSupplier(@Cause Throwable cause, String dataSourceName);

    // --- Driver service //

    @LogMessage(level = INFO)
    @Message(id = 201, value = "Performing flush operation, mode %s")
    void flushOperation(AgroalDataSource.FlushMode mode);

    // -- Operations //

    @Message(id = 301, value = "Unknown datasource service of type: %s")
    OperationFailedException unknownDatasourceServiceType(String serviceType);

    @Message(id = 302, value = "Invalid connection in '%s'")
    OperationFailedException invalidConnection(@Cause SQLException cause, String dataSourceName);

    @Message(id = 303, value = "JNDI name have to start with java:/ or java:jboss/")
    OperationFailedException jndiNameInvalidFormat();

    @Message(id = 304, value = "JNDI name shouldn't include '//' or end with '/'")
    OperationFailedException jndiNameShouldValidate();

    // -- Deployment //

    @Message(id = 401, value = "Invalid connection provider. Either a java.sql.Driver or javax.sql.DataSource implementation is required. Fix the connection-provider for the driver")
    DeploymentUnitProcessingException invalidDeploymentConnectionProvider();

    @Message(id = 402, value = "Failed to load connection provider class '%s'")
    DeploymentUnitProcessingException loadClassDeploymentException(@Cause Throwable cause, String className);

    @Message(id = 403, value = "Element <data-source> must provide attribute '%s'")
    DeploymentUnitProcessingException missingAttributeInDatasourceMetadata(String attributeName);

    // --- Driver //

    @LogMessage(level = INFO)
    @Message(id = 501, value = "Loaded class %s for driver '%s'")
    void driverLoaded(String className, String driverName);

    @Message(id = 502, value = "Failed to load driver module '%s'")
    IllegalArgumentException loadModuleException(@Cause Throwable cause, String moduleName);

    @Message(id = 503, value = "Failed to load driver class '%s'")
    IllegalArgumentException loadClassException(@Cause Throwable cause, String className);

    // --- Agroal Pool //

    @LogMessage(level = WARN)
    @Message(id = 601, value = "%s: %s")
    void poolWarning(String datasourceName, String warn);
}

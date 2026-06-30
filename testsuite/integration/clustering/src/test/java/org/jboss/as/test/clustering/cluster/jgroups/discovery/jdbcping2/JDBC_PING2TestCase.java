/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.discovery.jdbcping2;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;

import java.io.File;
import java.util.Collections;

import org.arquillian.testcontainers.api.Testcontainer;
import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.jboss.as.test.clustering.testcontainers.PostgreSQLContainer;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.dmr.ModelNode;

/**
 * Test case verifying JGroups discovery protocol JDBC_PING2 integration into WildFly.
 * The PostgreSQL database runs in a container and is configured via {@link JDBCPING2ServerSetupTask}.
 *
 * @author Radoslav Husar
 */
@TestcontainersRequired
@ServerSetup(JDBC_PING2TestCase.JDBCPING2ServerSetupTask.class)
public class JDBC_PING2TestCase extends CommandDispatcherTestCase {

    @Override
    public void legacy() throws Exception {
    }

    @TestcontainersRequired
    public static class JDBCPING2ServerSetupTask implements ServerSetupTask {

        @Testcontainer
        PostgreSQLContainer container;

        private static final String DATASOURCE_NAME = "jdbc-ping";
        private static final String DRIVER_DEPLOYMENT_NAME = "postgresql-driver.jar";

        private static final PathAddress ADDR_DEPLOYMENT = PathAddress.pathAddress()
                .append(PathElement.pathElement("deployment", DRIVER_DEPLOYMENT_NAME));
        private static final PathAddress ADDR_DATA_SOURCE = PathAddress.pathAddress()
                .append(SUBSYSTEM, "datasources")
                .append("data-source", DATASOURCE_NAME);
        private static final PathAddress ADDR_TCPPING = PathAddress.pathAddress()
                .append(SUBSYSTEM, "jgroups")
                .append("stack", "tcp")
                .append("protocol", "TCPPING");
        private static final PathAddress ADDR_JDBC_PING2 = PathAddress.pathAddress()
                .append(SUBSYSTEM, "jgroups")
                .append("stack", "tcp")
                .append("protocol", "JDBC_PING2");

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            // Get the JDBC URL from the running container
            String jdbcUrl = container.getJdbcUrl();

            // Find the PostgreSQL driver JAR in the classpath
            String driverJarPath = getPostgreSQLDriverPath();

            // Deploy the PostgreSQL driver
            ModelNode deployOp = new ModelNode();
            deployOp.get(OP).set(ADD);
            deployOp.get(OP_ADDR).set(ADDR_DEPLOYMENT.toModelNode());
            ModelNode content = deployOp.get(CONTENT).add();
            content.get(URL).set(new File(driverJarPath).toURI().toURL().toString());
            deployOp.get(ENABLED).set(true);

            Utils.applyUpdates(Collections.singletonList(deployOp), managementClient.getControllerClient());

            // Now configure the datasource and JDBC_PING2
            final ModelNode compositeOp = new ModelNode();
            compositeOp.get(OP).set(COMPOSITE);
            compositeOp.get(OP_ADDR).setEmptyList();
            ModelNode steps = compositeOp.get(STEPS);

            // Create a datasource for JDBC_PING2 using the deployed driver
            ModelNode datasourceAddOp = Util.createAddOperation(ADDR_DATA_SOURCE);
            datasourceAddOp.get("jndi-name").set("java:jboss/datasources/" + DATASOURCE_NAME);
            datasourceAddOp.get("enabled").set(true);
            datasourceAddOp.get("use-java-context").set(true);
            datasourceAddOp.get("connection-url").set(jdbcUrl);
            // Reference the deployed driver by deployment name
            datasourceAddOp.get("driver-name").set(DRIVER_DEPLOYMENT_NAME);
            datasourceAddOp.get("user-name").set(PostgreSQLContainer.USERNAME);
            datasourceAddOp.get("password").set(PostgreSQLContainer.PASSWORD);
            steps.add(datasourceAddOp);

            // Remove existing TCPPING discovery protocol
            ModelNode removeTcppingOp = Util.createRemoveOperation(ADDR_TCPPING);
            steps.add(removeTcppingOp);

            // Add JDBC_PING2 protocol with the datasource
            ModelNode addJdbcPing2Op = Util.createAddOperation(ADDR_JDBC_PING2);
            addJdbcPing2Op.get("add-index").set(1);
            addJdbcPing2Op.get("data-source").set(DATASOURCE_NAME);
            steps.add(addJdbcPing2Op);

            Utils.applyUpdates(Collections.singletonList(compositeOp), managementClient.getControllerClient());
            ServerReload.reloadIfRequired(managementClient);
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            final ModelNode compositeOp = new ModelNode();
            compositeOp.get(OP).set(COMPOSITE);
            compositeOp.get(OP_ADDR).setEmptyList();
            ModelNode steps = compositeOp.get(STEPS);

            // Remove JDBC_PING2 protocol
            ModelNode removeJdbcPing2Op = Util.createRemoveOperation(ADDR_JDBC_PING2);
            steps.add(removeJdbcPing2Op);

            // Restore TCPPING protocol
            ModelNode addTcppingOp = Util.createAddOperation(ADDR_TCPPING);
            addTcppingOp.get("add-index").set(1);
            addTcppingOp.get("socket-bindings").add("node-1");
            addTcppingOp.get("socket-bindings").add("node-2");
            addTcppingOp.get("socket-bindings").add("node-3");
            addTcppingOp.get("socket-bindings").add("node-4");
            steps.add(addTcppingOp);

            // Remove datasource
            ModelNode removeDatasourceOp = Util.createRemoveOperation(ADDR_DATA_SOURCE);
            steps.add(removeDatasourceOp);

            Utils.applyUpdates(Collections.singletonList(compositeOp), managementClient.getControllerClient());

            // Undeploy the driver
            ModelNode undeployOp = new ModelNode();
            undeployOp.get(OP).set(REMOVE);
            undeployOp.get(OP_ADDR).set(ADDR_DEPLOYMENT.toModelNode());
            Utils.applyUpdates(Collections.singletonList(undeployOp), managementClient.getControllerClient());

            ServerReload.reloadIfRequired(managementClient);
        }

        private String getPostgreSQLDriverPath() {
            String classpath = System.getProperty("java.class.path");
            for (String path : classpath.split(File.pathSeparator)) {
                if (path.contains("postgresql") && path.endsWith(".jar")) {
                    return path;
                }
            }
            throw new IllegalStateException("PostgreSQL JDBC driver JAR not found in classpath. " +
                    "Make sure the org.postgresql:postgresql dependency is added to the test dependencies.");
        }
    }
}

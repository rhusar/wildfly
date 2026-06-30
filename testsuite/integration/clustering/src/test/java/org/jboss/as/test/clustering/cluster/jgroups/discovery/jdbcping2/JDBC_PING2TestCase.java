/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.discovery.jdbcping2;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.NODE_1_2;

import java.io.File;
import java.util.Set;

import org.arquillian.testcontainers.api.Testcontainer;
import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.jboss.as.test.clustering.testcontainers.PostgreSQLContainer;
import org.jboss.as.test.shared.ManagementServerSetupTask;
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

    @Testcontainer
    static PostgreSQLContainer container;

    public static class JDBCPING2ServerSetupTask extends ManagementServerSetupTask {

        private static final String DATASOURCE_NAME = "jdbc-ping";
        private static final String DRIVER_DEPLOYMENT_NAME = "postgresql-driver.jar";

        public JDBCPING2ServerSetupTask() {
            super(NODE_1_2, createContainerConfigurationBuilder().build());
        }

        @Override
        public void setup(ManagementClient client, String containerId) throws Exception {
            super.setup(client, containerId);

            // Deploy the PostgreSQL JDBC driver using a URL reference
            ModelNode deployOp = new ModelNode();
            deployOp.get(ClientConstants.OP).set(ClientConstants.ADD);
            deployOp.get(ClientConstants.OP_ADDR).add("deployment", DRIVER_DEPLOYMENT_NAME);
            deployOp.get("content").add().get("url").set(new File(getPostgreSQLDriverPath()).toURI().toURL().toString());
            deployOp.get("enabled").set(true);
            ModelNode result = client.getControllerClient().execute(deployOp);
            if (result.get(ClientConstants.OUTCOME).asString().equals(ClientConstants.FAILED)) {
                throw new RuntimeException(result.get(ClientConstants.FAILURE_DESCRIPTION).toString());
            }

            String jdbcUrl = JDBC_PING2TestCase.container.getJdbcUrl();

            new ManagementServerSetupTask(Set.of(containerId), createContainerConfigurationBuilder()
                    .setupScript(createScriptBuilder()
                            .startBatch()
                            .add("/subsystem=datasources/data-source=%s:add(jndi-name=java:jboss/datasources/%s, enabled=true, use-java-context=true, connection-url=\"%s\", driver-name=%s, user-name=%s, password=%s)",
                                    DATASOURCE_NAME, DATASOURCE_NAME, jdbcUrl, DRIVER_DEPLOYMENT_NAME, PostgreSQLContainer.USERNAME, PostgreSQLContainer.PASSWORD)
                            .add("/subsystem=jgroups/stack=tcp/protocol=TCPPING:remove")
                            .add("/subsystem=jgroups/stack=tcp/protocol=JDBC_PING2:add(add-index=1, data-source=%s)", DATASOURCE_NAME)
                            .endBatch()
                            .build())
                    .build())
                    .setup(client, containerId);
        }

        private static String getPostgreSQLDriverPath() {
            String classpath = System.getProperty("java.class.path");
            for (String path : classpath.split(File.pathSeparator)) {
                if (path.contains("postgresql") && path.endsWith(".jar")) {
                    return path;
                }
            }
            throw new IllegalStateException("PostgreSQL JDBC driver JAR not found in classpath");
        }
    }

    @Override
    public void legacy() throws Exception {
        // Ignore
    }
}

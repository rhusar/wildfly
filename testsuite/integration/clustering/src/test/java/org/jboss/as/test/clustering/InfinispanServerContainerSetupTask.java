/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering;

import org.arquillian.testcontainers.api.Testcontainer;
import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;

/**
 * Server setup task which provides an Infinispan Server container managed by Arquillian Testcontainers.
 * The container is injected and started when this task is enriched, i.e. prior to any subsequent server setup tasks and deployments,
 * and is stopped after the test class.
 * Using a server setup task (rather than a test class field) ensures the container is also started for tests running in-container,
 * whose test instances are not enriched on the client.
 * This task must be listed before any other server setup task that configures the server to connect to the Infinispan Server.
 *
 * @author Radoslav Husar
 */
@TestcontainersRequired
public class InfinispanServerContainerSetupTask implements ServerSetupTask {

    @Testcontainer
    private InfinispanServerContainer container;

    @Override
    public void setup(ManagementClient managementClient, String containerId) {
        // Container lifecycle is managed by Arquillian Testcontainers
    }

    @Override
    public void tearDown(ManagementClient managementClient, String containerId) {
        // Container lifecycle is managed by Arquillian Testcontainers
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.as.test.config.ContainerConfig;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;
import org.wildfly.clustering.container.DefaultContainer;

/**
 * Infinispan Server OCI container configured with the testsuite server profile and application user.
 * The HotRod endpoint is bound to a fixed host port, so that both the WildFly server configuration and in-container clients
 * can use the static {@link org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase#INFINISPAN_SERVER_ADDRESS} and
 * {@link org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase#INFINISPAN_SERVER_PORT}.
 *
 * @author Radoslav Husar
 */
@TestcontainersRequired
public class InfinispanServerContainer extends DefaultContainer {

    private static final String CONFIGURATION_DIRECTORY = "/opt/infinispan/server/conf/";
    private static final String IDENTITIES_BATCH_PATH = CONFIGURATION_DIRECTORY + "identities.batch";
    private static final String IDENTITIES_BATCH = String.format("user create %s -p %s -g testsuite-application-group%n", INFINISPAN_APPLICATION_USER, INFINISPAN_APPLICATION_PASSWORD);

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public InfinispanServerContainer() {
        super(ContainerConfig.INFINISPAN_SERVER.getImage());

        // Infinispan Server does not need to access the host, avoid starting the port forwarding container
        this.setHostAccessible(false);

        this.addExposedPort(INFINISPAN_SERVER_PORT);
        this.setPortBindings(List.of(String.format("%s:%d:%d", INFINISPAN_SERVER_ADDRESS, INFINISPAN_SERVER_PORT, INFINISPAN_SERVER_PORT)));

        // Replace the default server configuration with the testsuite profile
        this.withCopyFileToContainer(MountableFile.forClasspathResource(INFINISPAN_SERVER_PROFILE), CONFIGURATION_DIRECTORY + "infinispan.xml");

        // Create the application user instead of the image's default generated admin user
        this.withCopyToContainer(Transferable.of(IDENTITIES_BATCH), IDENTITIES_BATCH_PATH);
        this.addEnv("IDENTITIES_BATCH", IDENTITIES_BATCH_PATH);

        // ISPN080001: Infinispan Server <version> started in <duration>
        this.setWaitStrategy(Wait.forLogMessage(".*\\QISPN080001\\E.*", 1));
    }

    /**
     * Starts the container unless it was already stopped. Arquillian Testcontainers starts containers on each test enrichment,
     * which includes enrichment of server setup tasks on tear down after the container was stopped at the end of the test class.
     * Restarting the container at that point would leak it, keeping the fixed host port bound for subsequent test classes.
     */
    @Override
    public void start() {
        if (!this.stopped.get()) {
            super.start();
        }
    }

    @Override
    public void stop() {
        this.stopped.set(true);
        super.stop();
    }
}

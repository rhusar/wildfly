/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.single.web.passivation;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.DEPLOYMENT_1;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.setup.SnapshotServerSetupTask;
import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.runner.RunWith;

/**
 * Validates the correctness of session passivation events for a distributed session manager using a local,
 * passivating cache with time-based (idle-threshold) eviction with the default session manager.
 *
 * @author Radoslav Husar
 */
@RunWith(Arquillian.class)
@ServerSetup({
        SnapshotServerSetupTask.class, // n.b. MUST be first!
        LocalIdleThresholdDefaultSessionManagerSessionPassivationTestCase.ServerSetupTask.class
})
public class LocalIdleThresholdDefaultSessionManagerSessionPassivationTestCase extends LocalIdleThresholdSessionPassivationTestCase {

    static class ServerSetupTask extends ManagementServerSetupTask {
        ServerSetupTask() {
            super(createContainerConfigurationBuilder()
                    .setupScript(createScriptBuilder()
                            .startBatch()
                            .add("/subsystem=distributable-web/infinispan-session-management=default:write-attribute(name=max-idle, value=PT1S)")
                            .endBatch()
                            .build())
                    .build());
        }
    }

    private static final String MODULE_NAME = LocalIdleThresholdDefaultSessionManagerSessionPassivationTestCase.class.getSimpleName();

    @Deployment(name = DEPLOYMENT_1, testable = false)
    public static Archive<?> deployment() {
        // n.b. do not add jboss-web.xml no distributable-web.xml because that would OVERRIDE the default session manager configuration
        // that this test is intended to test!
        return getBaseDeployment(MODULE_NAME);
    }
}

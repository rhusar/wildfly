/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation;

import static org.junit.Assert.*;

import java.util.List;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.clustering.ejb.EJBDirectory;
import org.jboss.as.test.clustering.ejb.RemoteEJBDirectory;
import org.jboss.as.test.clustering.single.ejb.timer.passivation.bean.TimerInfo;
import org.jboss.as.test.clustering.single.ejb.timer.passivation.bean.TimerTracker;
import org.jboss.as.test.clustering.single.ejb.timer.passivation.bean.TimerTrackingBean;
import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that EJB timers with serializable info objects work correctly with the
 * distributable timer service configuration.
 *
 * @author Radoslav Husar
 */
@RunWith(Arquillian.class)
@ServerSetup(IdleThresholdTimerPassivationTestCase.ServerSetupTask.class)
public class IdleThresholdTimerPassivationTestCase {

    static class ServerSetupTask extends ManagementServerSetupTask {
        ServerSetupTask() {
            super(createContainerConfigurationBuilder()
                    .setupScript(createScriptBuilder()
                            .startBatch()
                            .add("/subsystem=distributable-ejb/infinispan-timer-management=transient:write-attribute(name=max-idle, value=PT1S)")
                            .add("/subsystem=distributable-ejb/infinispan-timer-management=persistent:write-attribute(name=max-idle, value=PT1S)")
                            .endBatch()
                            .build())
                    .tearDownScript(createScriptBuilder()
                            .startBatch()
                            .add("/subsystem=distributable-ejb/infinispan-timer-management=transient:undefine-attribute(name=max-idle)")
                            .add("/subsystem=distributable-ejb/infinispan-timer-management=persistent:undefine-attribute(name=max-idle)")
                            .endBatch()
                            .build())
                    .build());
        }
    }

    private static final String MODULE_NAME = IdleThresholdTimerPassivationTestCase.class.getSimpleName();
    private static final String APPLICATION_NAME = MODULE_NAME + ".jar";

    @Deployment(testable = false)
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class, APPLICATION_NAME)
                .addPackage(TimerInfo.class.getPackage())
                ;
    }

    private EJBDirectory directory;

    @Before
    public void before() throws Exception {
        this.directory = new RemoteEJBDirectory(MODULE_NAME);
    }

    @After
    public void after() throws Exception {
        this.directory.close();
    }

    @Test
    public void testTimerInfoSerialization(@ArquillianResource ManagementClient managementClient) throws Exception {
        TimerTracker bean = this.directory.lookupSingleton(TimerTrackingBean.class, TimerTracker.class);

        // Step 1: Create a timer with serializable info
        TimerInfo originalInfo = new TimerInfo("test-timer", 42);
        // Create timer that won't expire during the test (30 minutes)
        bean.createTimer(originalInfo, 1800000);

        // Verify timer was created and info is correct
        assertEquals("Should have 1 timer", 1, bean.getTimerCount());
        List<TimerInfo> infos = bean.getTimerInfos();
        assertEquals("Should have 1 timer info", 1, infos.size());
        assertEquals("Timer info should match", originalInfo, infos.get(0));

        // Step 2: Create a second timer with different info
        TimerInfo secondInfo = new TimerInfo("second-timer", 100);
        bean.createTimer(secondInfo, 1800000);

        // Verify both timers exist with correct info
        assertEquals("Should have 2 timers", 2, bean.getTimerCount());
        infos = bean.getTimerInfos();
        assertEquals("Should have 2 timer infos", 2, infos.size());
        assertTrue("Should contain original timer info", infos.contains(originalInfo));
        assertTrue("Should contain second timer info", infos.contains(secondInfo));

        // Cleanup
        bean.cancelAllTimers();
        assertEquals("All timers should be cancelled", 0, bean.getTimerCount());
    }


}

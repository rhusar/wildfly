/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation;

import static org.junit.Assert.*;

import java.time.Duration;
import java.util.Map;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.PassivationEventTracker;
import org.jboss.as.test.clustering.ejb.EJBDirectory;
import org.jboss.as.test.clustering.ejb.RemoteEJBDirectory;
import org.jboss.as.test.clustering.single.ejb.timer.passivation.bean.TimerTracker;
import org.jboss.as.test.clustering.single.ejb.timer.passivation.bean.TimerTrackingBean;
import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that EJB timers are passivated after the configured idle threshold and that
 * serializable TimerInfo objects are correctly preserved through passivation/activation cycles.
 *
 * @author Radoslav Husar
 */
@RunWith(Arquillian.class)
@ServerSetup(IdleThresholdTimerPassivationTestCase.ServerSetupTask.class)
public class IdleThresholdTimerPassivationTestCase {

    // Max idle time configured via ManagementServerSetupTask is PT1S (1 second)
    private static final Duration IDLE_THRESHOLD_DURATION = Duration.ofSeconds(TimeoutUtil.adjust(1));
    // Wait a bit longer than max-idle to ensure passivation has occurred
    private static final Duration IDLE_GRACE_TIME = IDLE_THRESHOLD_DURATION.plusSeconds(TimeoutUtil.adjust(10));

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
                .addPackage(TimerTracker.class.getPackage())
                .addClasses(PassivationEventTracker.class)
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
    public void test() throws Exception {
        TimerTracker bean = this.directory.lookupSingleton(TimerTrackingBean.class, TimerTracker.class);

        // First, clear any existing events on the server; e.g. from previous failed run
        bean.clearTimerEvents();

        // Step 1: Create a timer with serializable info
        // n.b. This cannot be a persistent timer because it would be immediately serialized
        // n.b. TimerInfo is created server-side and never sent to the client
        String timerName = "test-timer";
        bean.createTimer(timerName, false, Duration.ofDays(1));

        // Verify timer was created
        assertEquals("Should have 1 timer", 1, bean.getTimerCount());

        // Step 2: Wait for idle timeout - timer should be passivated
        System.out.println("Waiting " + IDLE_GRACE_TIME.getSeconds() + " seconds for timer passivation...");
        Thread.sleep(IDLE_GRACE_TIME.toMillis());

        // Step 3: Poll for PASSIVATION event from the server (without accessing the timer)
        Map.Entry<Object, PassivationEventTracker.EventType> event = bean.pollTimerEvent();
        //TODO as expected this fails here! !!! [ERROR]   IdleThresholdTimerPassivationTestCase.testTimerPassivationWithSerializableInfo:108 Should have passivation event
        assertNotNull("Should have passivation event", event);
        assertEquals("Event should be for correct timer", timerName, event.getKey());
        assertEquals("Event should be PASSIVATION", PassivationEventTracker.EventType.PASSIVATION, event.getValue());
        System.out.println("✓ Verified: Timer was passivated after idle timeout");

        // Step 4: Access timer to trigger activation
        System.out.println("Accessing timer after idle period - should trigger activation");
        assertEquals("Timer count should be preserved after passivation", 1, bean.getTimerCount());

        // Step 5: Poll for ACTIVATION event from the server
        event = bean.pollTimerEvent();
        assertNotNull("Should have activation event", event);
        assertEquals("Event should be for correct timer", timerName, event.getKey());
        assertEquals("Event should be ACTIVATION", PassivationEventTracker.EventType.ACTIVATION.name(), event.getValue());
        System.out.println("✓ Verified: Timer was activated when accessed");

        // Cleanup
        bean.cancelAllTimers();
        assertEquals("All timers should be cancelled", 0, bean.getTimerCount());
    }


}

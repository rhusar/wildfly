/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.stateful.passivation;

import static org.junit.Assert.*;

import java.time.Duration;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.clustering.ejb.EJBDirectory;
import org.jboss.as.test.clustering.ejb.RemoteEJBDirectory;
import org.jboss.as.test.clustering.single.ejb.stateful.passivation.bean.PassivationTracker;
import org.jboss.as.test.clustering.single.ejb.stateful.passivation.bean.PassivationTrackingBean;
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
 * Tests idle-based (max-idle) passivation for stateful session beans.
 * Validates that beans are passivated after the configured idle timeout and properly activated when accessed again.
 *
 * @author Radoslav Husar
 */
@RunWith(Arquillian.class)
@ServerSetup(IdleThresholdStatefulSessionBeanPassivationTestCase.IdleThresholdServerSetupTask.class)
public class IdleThresholdStatefulSessionBeanPassivationTestCase {

    private static final String MODULE_NAME = IdleThresholdStatefulSessionBeanPassivationTestCase.class.getSimpleName();
    private static final String APPLICATION_NAME = MODULE_NAME + ".jar";

    // Max idle time configured via ManagementServerSetupTask is PT1S (1 second)
    // Wait a bit longer than max-idle to ensure passivation has occurred
    private static final Duration IDLE_WAIT_BUFFER = Duration.ofSeconds(TimeoutUtil.adjust(100));

    private EJBDirectory directory;

    @Deployment(testable = false)
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class, APPLICATION_NAME)
                .addClasses(PassivationTracker.class, PassivationTrackingBean.class)
                ;
    }

    @Before
    public void before() throws Exception {
        this.directory = new RemoteEJBDirectory(MODULE_NAME);
    }

    @After
    public void after() throws Exception {
        this.directory.close();
    }

    @Test
    public void test(@ArquillianResource ManagementClient managementClient) throws Exception {
        PassivationTracker bean = this.directory.lookupStateful(PassivationTrackingBean.class, PassivationTracker.class);
        PassivationTracker bean2 = this.directory.lookupStateful(PassivationTrackingBean.class, PassivationTracker.class);

        // Step 1: Set initial state on the bean
        bean.setValue(1);
        assertEquals("Initial value should be preserved", 1, bean.getValue());
        assertFalse("Bean should not have been passivated yet", bean.wasPassivated());
        assertFalse("Bean should not have been activated yet", bean.wasActivated());

        // Step 2: Wait for idle timeout to elapse
        Thread.sleep(IDLE_WAIT_BUFFER.toMillis());


        // wait for another caffeine thread
        bean2.setValue(1);
//        Thread.sleep(IDLE_WAIT_BUFFER.toMillis());

        // Step 3: Access the bean - this should trigger activation
        // The bean should have been passivated while idle
        assertEquals("Value should be preserved after passivation", 1, bean.getValue());
        assertTrue("Bean should have been passivated due to idle timeout", bean.wasPassivated());
        assertTrue("Bean should have been activated upon access", bean.wasActivated());

        // Step 4: Test a second idle cycle
        bean.resetFlags();
        bean.setValue(2);
        assertEquals("New value should be preserved", 2, bean.getValue());

        // Wait for another idle timeout
        Thread.sleep(IDLE_WAIT_BUFFER.toMillis());

        // Access the bean again - should trigger second activation
        assertEquals("Value should be preserved after second passivation", 2, bean.getValue());
        assertTrue("Bean should have been passivated again", bean.wasPassivated());
        assertTrue("Bean should have been activated again", bean.wasActivated());

        // Cleanup
        bean.remove();
    }

    static class IdleThresholdServerSetupTask extends ManagementServerSetupTask {
        IdleThresholdServerSetupTask() {
            super(createContainerConfigurationBuilder()
                    .setupScript(createScriptBuilder()
                            .startBatch()
                            .add("/subsystem=infinispan/cache-container=ejb/local-cache=passivation/component=expiration:write-attribute(name=interval, value=500)")
                            //.add("/subsystem=infinispan/cache-container=ejb/local-cache=passivation/component=expiration:remove")
                            .add("/subsystem=ejb3:write-attribute(name=default-sfsb-cache, value=distributable)")
                            .add("/subsystem=distributable-ejb/infinispan-bean-management=default:undefine-attribute(name=max-active-beans)")
                            .add("/subsystem=distributable-ejb/infinispan-bean-management=default:write-attribute(name=max-idle, value=PT1S)")
                            .endBatch()
                            .build())
                    .tearDownScript(createScriptBuilder()
                            .startBatch()
                            //.add("/subsystem=distributable-ejb/infinispan-bean-management=default:undefine-attribute(name=max-idle)")
                            //.add("/subsystem=distributable-ejb/infinispan-bean-management=default:write-attribute(name=max-active-beans, value=10000)")
                            .endBatch()
                            .build())
                    .build());
        }
    }
}

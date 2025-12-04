/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation.bean;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Resource;
import jakarta.ejb.Remote;
import jakarta.ejb.Remove;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;

/**
 * A singleton session bean that manages timers with serializable info objects.
 * This bean is used to test that timer info (serializable objects) are properly
 * persisted and retrieved by the distributable timer service.
 *
 * @author Radoslav Husar
 */
@Singleton
@Startup
@Remote(TimerTracker.class)
public class TimerTrackingBean implements TimerTracker {

    @Resource
    private TimerService timerService;

    @Override
    public void createTimer(TimerInfo info, long duration) {
        TimerConfig config = new TimerConfig(info, true); // persistent timer
        this.timerService.createSingleActionTimer(duration, config);
        System.out.println("Created timer with info: " + info + ", duration: " + duration + " ms");
    }

    @Override
    public List<TimerInfo> getTimerInfos() {
        List<TimerInfo> infos = new ArrayList<>();
        for (Timer timer : this.timerService.getTimers()) {
            Object info = timer.getInfo();
            if (info instanceof TimerInfo) {
                infos.add((TimerInfo) info);
            }
        }
        System.out.println("getTimerInfos() = " + infos);
        return infos;
    }

    @Override
    public int getTimerCount() {
        int count = this.timerService.getTimers().size();
        System.out.println("getTimerCount() = " + count);
        return count;
    }

    @Override
    public void cancelAllTimers() {
        for (Timer timer : this.timerService.getTimers()) {
            timer.cancel();
        }
        System.out.println("cancelAllTimers()");
    }

    @Override
    public boolean wasPassivated() {
        // Singleton beans don't passivate
        return false;
    }

    @Override
    public boolean wasActivated() {
        // Singleton beans don't activate
        return false;
    }

    @Override
    public void resetFlags() {
        // No-op for Singleton beans
        System.out.println("resetFlags()");
    }

    @Timeout
    public void timeout(Timer timer) {
        // Timer callback - we don't need to do anything here for passivation testing
        // The test verifies that timer info is preserved, not that timeouts actually fire
        System.out.println("@Timeout for timer with info: " + timer.getInfo());
    }

    @Remove
    @Override
    public void remove() {
        System.out.println("@Remove");
        // Cancel any remaining timers before removal
        this.cancelAllTimers();
    }
}

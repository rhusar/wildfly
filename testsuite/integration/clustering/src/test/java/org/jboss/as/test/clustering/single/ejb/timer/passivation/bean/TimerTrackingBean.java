/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation.bean;

import java.time.Duration;
import java.util.Map.Entry;

import org.jboss.as.test.clustering.PassivationEventTracker;

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
@Startup
@Singleton
@Remote(TimerTracker.class)
public class TimerTrackingBean implements TimerTracker {

    @Resource
    private TimerService timerService;

    @Override
    public void createTimer(String name, boolean persistent, Duration duration) {
        TimerInfo info = new TimerInfo(name);
        TimerConfig config = new TimerConfig(info, persistent);
        this.timerService.createSingleActionTimer(duration.toMillis(), config);

        System.out.printf("Created timer on server with info: %s, persistent? %s, duration %d ms.%n", info, persistent, duration.toMillis());
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
    public void clearTimerEvents() {
        PassivationEventTracker.clearEvents();
        System.out.println("clearTimerEvents()");
    }

    @Override
    public Entry<Object, PassivationEventTracker.EventType> pollTimerEvent() {
        Entry<Object, PassivationEventTracker.EventType> event = PassivationEventTracker.pollEvent();
        if (event == null) {
            System.out.println("pollTimerEvent() = null");
            return null;
        }
        System.out.println("pollTimerEvent() = [" + event.getKey() + ", " + event.getValue() + "]");
        return event;
    }

    @Timeout
    public void timeout(Timer timer) {
        System.out.println("@Timeout fired for timer with info: " + timer.getInfo());
    }

    @Remove
    @Override
    public void remove() {
        System.out.println("Called @Remove");
        // Cancel any remaining timers before removal
        this.cancelAllTimers();
    }
}

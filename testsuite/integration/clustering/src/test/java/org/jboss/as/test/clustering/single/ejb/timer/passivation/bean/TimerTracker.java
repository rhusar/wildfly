/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation.bean;

import java.util.List;

import jakarta.ejb.Remove;

/**
 * Remote interface for a stateful bean that manages timers and tracks passivation events.
 *
 * @author Radoslav Husar
 */
public interface TimerTracker {

    /**
     * Creates a persistent timer with the specified TimerInfo.
     */
    void createTimer(TimerInfo info, long duration);

    /**
     * Gets all TimerInfo objects from active timers.
     */
    List<TimerInfo> getTimerInfos();

    /**
     * Gets the number of active timers.
     */
    int getTimerCount();

    /**
     * Cancels all timers.
     */
    void cancelAllTimers();

    /**
     * Returns whether the bean was passivated since the last reset.
     */
    boolean wasPassivated();

    /**
     * Returns whether the bean was activated since the last reset.
     */
    boolean wasActivated();

    /**
     * Resets the passivation/activation flags.
     */
    void resetFlags();

    /**
     * Removes the bean.
     */
    @Remove
    void remove();
}

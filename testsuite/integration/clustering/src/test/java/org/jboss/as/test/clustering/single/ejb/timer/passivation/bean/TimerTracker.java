/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation.bean;

import java.time.Duration;

import jakarta.ejb.Remove;

/**
 * Remote interface for a singleton bean that manages timers.
 * TimerInfo objects are created and managed entirely on the server side
 * to ensure that serialization tracking only measures timer passivation,
 * not EJB remoting serialization.
 *
 * @author Radoslav Husar
 */
public interface TimerTracker {

    /**
     * Creates a persistent timer with a server-generated TimerInfo.
     * The TimerInfo is created on the server and never sent to the client,
     * ensuring that serialization only occurs during timer passivation.
     *
     * @param name the name for the timer
     * @param persistent whether the timer is persistent
     * @param duration the duration in milliseconds before the timer expires
     */
    void createTimer(String name, boolean persistent, Duration duration);

    /**
     * Gets the number of active timers.
     */
    int getTimerCount();

    /**
     * Cancels all timers.
     */
    void cancelAllTimers();

    /**
     * Clears the static event queue on the server.
     */
    void clearTimerEvents();

    /**
     * Polls a single event from the static event queue on the server.
     * Returns the event key (timer name) and type (PASSIVATION/ACTIVATION).
     *
     * @return array with [0]=timer name, [1]=event type name, or null if no events
     */
    String[] pollTimerEvent();

    /**
     * Removes the bean. Used for test cleanup.
     */
    @Remove
    void remove();
}

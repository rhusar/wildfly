/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.stateful.passivation.bean;

import jakarta.ejb.Remove;

/**
 * Remote interface for a bean that tracks passivation/activation events.
 *
 * @author Radoslav Husar
 */
public interface PassivationTracker {

    /**
     * Sets a value on the bean.
     */
    void setValue(int value);

    /**
     * Gets the value from the bean.
     */
    int getValue();

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

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.passivation.bean;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Remote;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;
import org.jboss.logging.Logger;

/**
 * A stateful session bean that tracks passivation and activation events.
 * Used for testing idle-based passivation with max-idle configuration.
 *
 * @author Radoslav Husar
 */
@Stateful
@Remote(PassivationTracker.class)
public class PassivationTrackingBean implements PassivationTracker {
    private static final Logger log = Logger.getLogger(PassivationTrackingBean.class);

    private int value;
    private volatile boolean beenPassivated = false;
    private volatile boolean beenActivated = false;

    @Override
    public void setValue(int value) {
        this.value = value;
        log.tracef("setValue(%d)", value);
    }

    @Override
    public int getValue() {
        log.tracef("getValue() = %d", this.value);
        return this.value;
    }

    @Override
    public boolean wasPassivated() {
        return this.beenPassivated;
    }

    @Override
    public boolean wasActivated() {
        return this.beenActivated;
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("Called postConstruct()");
    }

    @Override
    public void resetFlags() {
        this.beenPassivated = false;
        this.beenActivated = false;
        System.out.println("Called resetFlags()");
    }

    @PrePassivate
    public void prePassivate() {
        System.out.println("Called @PrePassivate");
        this.beenPassivated = true;
    }

    @PostActivate
    public void postActivate() {
        System.out.println("Called @PostActivate");
        this.beenActivated = true;
    }

    @Remove
    @Override
    public void remove() {
        System.out.println("Called @Remove");
    }
}

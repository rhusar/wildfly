/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.stateful.passivation.bean;

import java.util.UUID;

import org.jboss.as.test.clustering.PassivationEventTracker;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Remote;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;

/**
 * A stateful session bean that tracks passivation and activation events.
 * Used for testing idle-based passivation with max-idle configuration.
 *
 * @author Radoslav Husar
 */
@Stateful
@Remote(PassivationTracker.class)
public class PassivationTrackingBean implements PassivationTracker {

    private String identifier;
    private int value;
    private volatile boolean beenPassivated = false;
    private volatile boolean beenActivated = false;

    @Override
    public void setValue(int value) {
        this.value = value;
        System.out.printf("setValue(%d)%n", value);
    }

    @Override
    public int getValue() {
        System.out.printf("getValue() = %d%n", this.value);
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
        this.identifier = UUID.randomUUID().toString();
        System.out.println("Called postConstruct() - identifier: " + this.identifier);
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public void resetFlags() {
        this.beenPassivated = false;
        this.beenActivated = false;
        System.out.println("Called resetFlags()");
    }

    @Override
    public void clearPassivationEvents() {
        PassivationEventTracker.clearEvents();
    }

    @Override
    public java.util.Map.Entry<Object, PassivationEventTracker.EventType> pollPassivationEvent() {
        return PassivationEventTracker.pollEvent();
    }

    @PrePassivate
    public void prePassivate() {
        System.out.println("Called @PrePassivate - identifier: " + this.identifier);
        this.beenPassivated = true;
        PassivationEventTracker.recordPassivation(this.identifier);
    }

    @PostActivate
    public void postActivate() {
        System.out.println("Called @PostActivate - identifier: " + this.identifier);
        this.beenActivated = true;
        PassivationEventTracker.recordActivation(this.identifier);
    }

    @Remove
    @Override
    public void remove() {
        System.out.println("Called @Remove");
    }
}

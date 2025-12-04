/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.single.ejb.timer.passivation.bean;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Serializable timer info object used to test timer passivation.
 * This object is stored with the timer and should be preserved through passivation/activation cycles.
 *
 * @author Radoslav Husar
 */
public class TimerInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int value;

    public TimerInfo(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public int getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TimerInfo)) return false;
        TimerInfo other = (TimerInfo) obj;
        return this.value == other.value && Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.value);
    }

    @Override
    public String toString() {
        return String.format("TimerInfo{name=%s, value=%d}", this.name, this.value);
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.ejb.bean;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Configuration for bean passivation.
 * @author Paul Ferraro
 * @author Radoslav Husar
 */
public interface BeanPassivationConfiguration {
    /**
     * When present, returns the maximum number of bean instances to retain in memory at a given time.
     * @return when present, the maximum number of bean instances to retain in memory at a given time, or empty if passivation is disabled.
     */
    OptionalInt getMaxActiveBeans();

    /**
     * When present, returns a duration of time after which an idle bean should be evicted from memory.
     * @return an optional duration of time after which an idle bean should be evicted from memory.
     */
    Optional<Duration> getMaxIdle();
}

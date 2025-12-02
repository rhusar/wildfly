/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ejb.timer;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

import org.jboss.modules.Module;
import org.wildfly.clustering.marshalling.ByteBufferMarshaller;

/**
 * Encapsulates the configuration of a {@link TimerManagementProvider}.
 * @author Paul Ferraro
 * @author Radoslav Husar
 */
public interface TimerManagementConfiguration {

    Function<Module, ByteBufferMarshaller> getMarshallerFactory();

    OptionalInt getMaxActiveTimers();

    /**
     * @return The maximum duration an active timer to retain in memory at a time, after which it will be passivated.
     */
    Optional<Duration> getMaxIdle();
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.ejb.bean;

import org.wildfly.clustering.server.eviction.EvictionConfiguration;

/**
 * Configuration for bean passivation.
 * @author Paul Ferraro
 * @author Radoslav Husar
 */
public interface BeanPassivationConfiguration extends EvictionConfiguration {
}

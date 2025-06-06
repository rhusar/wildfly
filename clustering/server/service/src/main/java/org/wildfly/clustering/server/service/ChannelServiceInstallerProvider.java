/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.server.service;

import java.util.function.Function;

import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * SPI for providing services on behalf of a channel.
 * @author Paul Ferraro
 */
public interface ChannelServiceInstallerProvider extends Function<String, Iterable<ServiceInstaller>> {

}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.microprofile.rest.client.tck;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.core.spi.LoadableExtension;

/**
 * @author Radoslav Husar
 */
public class RestClientTCKExtension implements LoadableExtension {

    @Override
    public void register(ExtensionBuilder builder) {
        builder.service(ApplicationArchiveProcessor.class, RestClientApplicationArchiveProcessor.class);
    }

}
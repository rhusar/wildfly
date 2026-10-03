/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.web.remote;

import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.as.test.clustering.cluster.web.AbstractSessionActivationTestCase;

/**
 * @author Paul Ferraro
 * @author Radoslav Husar
 */
@TestcontainersRequired
public abstract class AbstractHotRodSessionActivationTestCase extends AbstractSessionActivationTestCase {

    protected AbstractHotRodSessionActivationTestCase(boolean transactional) {
        super(transactional);
    }

}

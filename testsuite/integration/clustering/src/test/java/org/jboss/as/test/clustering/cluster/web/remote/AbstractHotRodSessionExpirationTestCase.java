/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.web.remote;

import org.arquillian.testcontainers.api.TestcontainersRequired;
import org.infinispan.transaction.TransactionMode;
import org.jboss.as.test.clustering.cluster.web.expiration.SessionExpirationTestCase;

/**
 * @author Paul Ferraro
 */
@TestcontainersRequired
public abstract class AbstractHotRodSessionExpirationTestCase extends SessionExpirationTestCase {

    public AbstractHotRodSessionExpirationTestCase() {
        super(TransactionMode.NON_TRANSACTIONAL);
    }
}

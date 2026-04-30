/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.tls;

import static org.junit.jupiter.api.Assertions.*;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.jboss.as.test.clustering.cluster.dispatcher.bean.ClusterTopology;
import org.jboss.as.test.clustering.cluster.dispatcher.bean.ClusterTopologyRetriever;
import org.jboss.as.test.clustering.cluster.dispatcher.bean.ClusterTopologyRetrieverBean;
import org.jboss.as.test.clustering.cluster.jgroups.TCP_NIO2ServerSetupTask;
import org.jboss.as.test.clustering.ejb.EJBDirectory;
import org.jboss.as.test.clustering.ejb.RemoteEJBDirectory;
import org.junit.jupiter.api.Test;

/**
 * Variant of the {@link CommandDispatcherTestCase} with TLS-secured TCP_NIO2 transport protocol
 * without a shared key. The nodes should reject the connection from each other and form two singleton clusters.
 *
 * @author Radoslav Husar
 */
@ServerSetup({
        TCP_NIO2ServerSetupTask.class,
        TLSServerSetupTask.UntrustedCertSecureJGroupsTransport_TCP_NIO2_NODE_1_2.class,
})
class UntrustedCertTLSTCP_NIO2CommandDispatcherTestCase extends CommandDispatcherTestCase {

    @Override
    @Test
    public void test() throws Exception {
        try (EJBDirectory directory = new RemoteEJBDirectory(MODULE_NAME)) {
            ClusterTopologyRetriever bean = directory.lookupStateless(ClusterTopologyRetrieverBean.class, ClusterTopologyRetriever.class);

            ClusterTopology topology = bean.getClusterTopology();

            assertEquals(1, topology.getNodes().size(), "TLS/SSL-secured cluster nodes formed a cluster while they shouldn't have since they did not have a pre-shared key");
        }
    }

    @Override
    @Test
    public void legacy() throws Exception {
        // This test variant is redundant
    }
}

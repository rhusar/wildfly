/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.tls;

import java.net.URL;
import java.util.Set;

import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.TopologyChangeListenerServlet;
import org.jboss.as.test.clustering.TopologyChangeListenerUtil;
import org.jboss.as.test.clustering.cluster.dispatcher.CommandDispatcherTestCase;
import org.junit.jupiter.api.Test;

/**
 * Variation of the standard {@link CommandDispatcherTestCase} that uses SSL/TLS-secured JGroups communication channel,
 * however, without a shared key. In this case, the nodes should reject the connection from each other and form two
 * singleton clusters.
 * <p>
 * This test prevents against a potential breakage in TLS secured transport wiring in JGroups subsystem.
 * <p>
 * Since the nodes do not form a cluster, the singleton topology of each node is verified over HTTP rather than using the
 * EJB client, whose discovery does not work reliably with split clusters.
 *
 * @author Radoslav Husar
 */
@ServerSetup({
        TLSServerSetupTask.UntrustedCertSecureJGroupsTransport_TCP_NODE_1_2.class,
})
class UntrustedCertTLSCommandDispatcherTestCase extends CommandDispatcherTestCase {

    @Override
    public void test() throws Exception {
        // Overrides the inherited EJB client-based test without @Test so that it is not executed.
    }

    @Override
    public void legacy() throws Exception {
        // Overrides the inherited test without @Test so that it is not executed, as this test variant is redundant.
    }

    @Test
    void singletonClusters(@ArquillianResource(TopologyChangeListenerServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
                           @ArquillianResource(TopologyChangeListenerServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2
    ) throws Exception {
        // These server should never cluster and end up with singleton clusters, i.e. the topology of each node consists of itself only
        // Server log will contain a similar log – which is expected as keys are not preshared:
        // WARN  [org.jgroups.protocols.TCP] (TcpServer.Acceptor[7600]-1,null,null) JGRP000006: 127.0.0.1:7600: failed accepting connection from peer SSLSocket[....]: java.net.SocketException: Socket is closed
        TopologyChangeListenerUtil.establishTopology(baseURL1, "web", DEPLOYMENT_NAME, Set.of(NODE_1));
        TopologyChangeListenerUtil.establishTopology(baseURL2, "web", DEPLOYMENT_NAME, Set.of(NODE_2));
    }
}

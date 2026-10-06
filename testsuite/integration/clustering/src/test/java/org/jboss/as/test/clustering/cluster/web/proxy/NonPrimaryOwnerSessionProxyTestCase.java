/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.web.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.ClusterTestUtil;
import org.jboss.as.test.clustering.TopologyChangeListenerUtil;
import org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase;
import org.jboss.as.test.clustering.cluster.web.DistributableTestCase;
import org.jboss.as.test.clustering.single.web.Mutable;
import org.jboss.as.test.clustering.single.web.SimpleServlet;
import org.jboss.as.test.http.util.TestHttpClientUtils;
import org.jboss.as.test.shared.CLIServerSetupTask;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.test.stabilitylevel.StabilityServerSetupSnapshotRestoreTasks;

/**
 * Smoke test for proxying a request associated with a session owned by a peer cluster member to that member.
 * <p>
 * Each member is configured to service such a request via a reverse-proxy handler directed at the primary owner of the
 * session. Consequently, once a session exists, a request for it is serviced by its primary owner, irrespective of the
 * member that received it.
 *
 * @author Radoslav Husar
 */
@ExtendWith(ArquillianExtension.class)
@ServerSetup({ StabilityServerSetupSnapshotRestoreTasks.Experimental.class, NonPrimaryOwnerSessionProxyTestCase.ServerSetupTask.class })
public class NonPrimaryOwnerSessionProxyTestCase extends AbstractClusteringTestCase {

    private static final String MODULE_NAME = NonPrimaryOwnerSessionProxyTestCase.class.getSimpleName();
    private static final String DEPLOYMENT_NAME = MODULE_NAME + ".war";
    private static final String HANDLER_NAME = "non-primary-owner";

    public NonPrimaryOwnerSessionProxyTestCase() {
        super(NODE_1_2_3);
    }

    @Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(NODE_1)
    public static Archive<?> deployment1() {
        return deployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(NODE_2)
    public static Archive<?> deployment2() {
        return deployment();
    }

    @Deployment(name = DEPLOYMENT_3, managed = false, testable = false)
    @TargetsContainer(NODE_3)
    public static Archive<?> deployment3() {
        return deployment();
    }

    private static Archive<?> deployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME);
        war.addClasses(SimpleServlet.class, Mutable.class);
        ClusterTestUtil.addTopologyListenerDependencies(war);
        war.setWebXML(DistributableTestCase.class.getPackage(), "web.xml");
        return war;
    }

    @Test
    public void sessionOwnedByPeerIsServicedByItsPrimaryOwner(
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2,
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_3) URL baseURL3) throws Exception {

        TopologyChangeListenerUtil.establishTopology(baseURL1, "web", DEPLOYMENT_NAME, NODE_1_2_3);
        // Allow the routing registry to converge before relying on session ownership
        Thread.sleep(GRACE_TIME_TOPOLOGY_CHANGE);

        List<URI> uris = List.of(SimpleServlet.createURI(baseURL1), SimpleServlet.createURI(baseURL2), SimpleServlet.createURI(baseURL3));
        int value = 1;

        try (CloseableHttpClient client = TestHttpClientUtils.promiscuousCookieHttpClient()) {
            String owner;

            // The initial request has no associated session, and is thus serviced locally, creating a session whose
            // primary owner is determined by the consistent hash of its identifier.
            try (CloseableHttpResponse response = client.execute(new HttpGet(uris.get(0)))) {
                assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                assertEquals(value++, Integer.parseInt(response.getFirstHeader(SimpleServlet.VALUE_HEADER).getValue()));
                assertEquals(NODE_1, response.getFirstHeader(SimpleServlet.HEADER_NODE_NAME).getValue());

                Map.Entry<String, String> entry = parseSessionRoute(response);
                assertNotNull(entry, "Expected the response to encode the route of the primary owner of the session");
                owner = entry.getValue();
                assertNotNull(owner);
            }

            Thread.sleep(GRACE_TIME_TO_REPLICATE);

            // Every subsequent request, whichever member receives it, must be serviced by the primary owner of the session.
            for (URI uri : uris) {
                try (CloseableHttpResponse response = client.execute(new HttpGet(uri))) {
                    assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                    assertEquals(value++, Integer.parseInt(response.getFirstHeader(SimpleServlet.VALUE_HEADER).getValue()));
                    assertEquals(owner, response.getFirstHeader(SimpleServlet.HEADER_NODE_NAME).getValue(), String.format("Request to %s should have been serviced by the primary owner of the session", uri));
                }
            }
        }
    }

    public static class ServerSetupTask extends CLIServerSetupTask {
        public ServerSetupTask() {
            this.builder.node(NODE_1_2_3.toArray(new String[0]))
                    .setup("/subsystem=undertow/configuration=handler/reverse-proxy=%s:add(max-retries=0)", HANDLER_NAME)
                    .setup("/subsystem=distributable-web/infinispan-session-management=default:write-attribute(name=non-primary-sessions-handler, value=%s)", HANDLER_NAME)
                    .teardown("/subsystem=distributable-web/infinispan-session-management=default:undefine-attribute(name=non-primary-sessions-handler)")
                    .teardown("/subsystem=undertow/configuration=handler/reverse-proxy=%s:remove()", HANDLER_NAME)
                    .reloadOnSetup(true)
                    .reloadOnTearDown(true)
            ;
        }
    }
}

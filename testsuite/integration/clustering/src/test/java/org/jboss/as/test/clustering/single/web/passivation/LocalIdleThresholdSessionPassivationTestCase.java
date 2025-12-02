/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.single.web.passivation;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.single.web.SimpleServlet;
import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

/**
 * Validates the correctness of session activation/passivation events for a distributed session manager using a local,
 * passivating cache with max-threshold (time-based) eviction.
 *
 * @author Radoslav Husar
 */
@ServerSetup(LocalIdleThresholdSessionPassivationTestCase.IdleThresholdServerSetupTask.class)
public abstract class LocalIdleThresholdSessionPassivationTestCase {

    static class IdleThresholdServerSetupTask extends ManagementServerSetupTask {
        IdleThresholdServerSetupTask() {
            super(createContainerConfigurationBuilder()
                    .setupScript(createScriptBuilder()
                            .startBatch()
                            .add("/subsystem=distributable-web/infinispan-session-management=default:write-attribute(name=max-idle, value=PT1S)")
                            .endBatch()
                            .build())
                    .tearDownScript(createScriptBuilder()
                            .startBatch()
//                            .add("/subsystem=distributable-web/infinispan-session-management=default:undefine-attribute(name=max-idle)")
                            .endBatch()
                            .build())
                    .build());
        }
    }

    // Max idle time configured in jboss-web-idle.xml is PT3S (3 seconds)
    private static final Duration MAX_IDLE_DURATION = Duration.ofSeconds(TimeoutUtil.adjust(3));
    // Wait a bit longer than max-idle to ensure passivation has occurred
    private static final Duration IDLE_WAIT_BUFFER = MAX_IDLE_DURATION.plusSeconds(TimeoutUtil.adjust(5));
    private static final Duration MAX_PASSIVATION_DURATION = Duration.ofSeconds(TimeoutUtil.adjust(10));
    private static final Duration PASSIVATION_CHECK_INTERVAL = Duration.ofMillis(TimeoutUtil.adjust(100));
    private static final Duration COMMIT_DURATION = Duration.ofSeconds(TimeoutUtil.adjust(5));

    static WebArchive getBaseDeployment(String moduleName) {
        WebArchive war = ShrinkWrap.create(WebArchive.class, moduleName + ".war");
        war.addClasses(SessionOperationServlet.class);
        war.setWebXML(SimpleServlet.class.getPackage(), "web.xml");
        return war;
    }

    @Test
    public void test(@ArquillianResource(SessionOperationServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL) throws IOException, URISyntaxException {

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            try {
                String sessionId;

                // Step 1: Create a session and set an attribute
                // This should not trigger any passivation/activation events
                try (CloseableHttpResponse response = client.execute(new HttpPut(SessionOperationServlet.createURI(baseURL, "testAttr", "testValue")))) {
                    assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                    assertTrue(response.containsHeader(SessionOperationServlet.SESSION_ID));
                    sessionId = response.getFirstHeader(SessionOperationServlet.SESSION_ID).getValue();
                }

                Map<String, Queue<SessionOperationServlet.EventType>> events = new HashMap<>();
                events.put(sessionId, new LinkedList<>());

                // Step 2: Wait for max-idle duration to elapse
                // Session should be passivated due to inactivity
                Thread.sleep(IDLE_WAIT_BUFFER.toMillis());

                // Step 3: Poll to verify session was passivated due to idle timeout
                // Use HEAD request to check events without activating the session
                Instant pollStart = Instant.now();
                while (events.get(sessionId).isEmpty() && Duration.between(pollStart, Instant.now()).compareTo(MAX_PASSIVATION_DURATION) < 0) {
                    try (CloseableHttpResponse response = client.execute(new HttpHead(SessionOperationServlet.createURI(baseURL)))) {
                        assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                        collectEvents(response, events);
                    }
                    if (events.get(sessionId).isEmpty()) {
                        Thread.sleep(PASSIVATION_CHECK_INTERVAL.toMillis());
                    }
                }

                assertFalse("Session should have been passivated after max-idle timeout", events.get(sessionId).isEmpty());
                assertEquals("First event should be PASSIVATION", SessionOperationServlet.EventType.PASSIVATION, events.get(sessionId).peek());

                // Step 4: Access the session again - should trigger activation
                try (CloseableHttpResponse response = client.execute(new HttpGet(SessionOperationServlet.createURI(baseURL, "testAttr")))) {
                    assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                    assertTrue(response.containsHeader(SessionOperationServlet.SESSION_ID));
                    assertEquals(sessionId, response.getFirstHeader(SessionOperationServlet.SESSION_ID).getValue());
                    assertTrue(response.containsHeader(SessionOperationServlet.RESULT));
                    assertEquals("testValue", response.getFirstHeader(SessionOperationServlet.RESULT).getValue());
                    collectEvents(response, events);
                }

                // Verify activation occurred
                assertTrue("Session should have been activated", events.get(sessionId).contains(SessionOperationServlet.EventType.ACTIVATION));
                validateEvents(sessionId, events);

                // Step 5: Test a second idle cycle
                // Clear events and wait for another idle period
                events.get(sessionId).clear();
                Thread.sleep(COMMIT_DURATION.toMillis());

                Thread.sleep(IDLE_WAIT_BUFFER.toMillis());

                // Step 6: Verify second passivation occurred
                pollStart = Instant.now();
                while (events.get(sessionId).isEmpty() && Duration.between(pollStart, Instant.now()).compareTo(MAX_PASSIVATION_DURATION) < 0) {
                    try (CloseableHttpResponse response = client.execute(new HttpHead(SessionOperationServlet.createURI(baseURL)))) {
                        assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                        collectEvents(response, events);
                    }
                    if (events.get(sessionId).isEmpty()) {
                        Thread.sleep(PASSIVATION_CHECK_INTERVAL.toMillis());
                    }
                }

                assertFalse("Session should have been passivated again after second idle timeout", events.get(sessionId).isEmpty());
                assertEquals("First event of second cycle should be PASSIVATION", SessionOperationServlet.EventType.PASSIVATION, events.get(sessionId).peek());

                // Step 7: Access the session again - should trigger second activation
                try (CloseableHttpResponse response = client.execute(new HttpGet(SessionOperationServlet.createURI(baseURL, "testAttr")))) {
                    assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                    assertTrue(response.containsHeader(SessionOperationServlet.SESSION_ID));
                    assertEquals(sessionId, response.getFirstHeader(SessionOperationServlet.SESSION_ID).getValue());
                    assertTrue(response.containsHeader(SessionOperationServlet.RESULT));
                    assertEquals("testValue", response.getFirstHeader(SessionOperationServlet.RESULT).getValue());
                    collectEvents(response, events);
                }

                // Verify second activation occurred
                assertTrue("Session should have been activated again", events.get(sessionId).contains(SessionOperationServlet.EventType.ACTIVATION));
                validateEvents(sessionId, events);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try (CloseableHttpResponse response = client.execute(new HttpDelete(SessionOperationServlet.createURI(baseURL)))) {
                    assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                }
            }
        }
    }

    private static void collectEvents(HttpResponse response, Map<String, Queue<SessionOperationServlet.EventType>> events) {
        events.entrySet().forEach((Map.Entry<String, Queue<SessionOperationServlet.EventType>> entry) -> {
            String sessionId = entry.getKey();
            if (response.containsHeader(sessionId)) {
                Stream.of(response.getHeaders(sessionId)).forEach((Header header) -> {
                    entry.getValue().add(SessionOperationServlet.EventType.valueOf(header.getValue()));
                });
            }
        });
    }

    private static void validateEvents(String sessionId, Map<String, Queue<SessionOperationServlet.EventType>> events) {
        Queue<SessionOperationServlet.EventType> types = events.get(sessionId);
        SessionOperationServlet.EventType expected = SessionOperationServlet.EventType.PASSIVATION;

        for (SessionOperationServlet.EventType type : types) {
            assertEquals("Events should alternate between PASSIVATION and ACTIVATION", expected, type);
            // ACTIVATION event must follow PASSIVATION event and vice versa
            expected = SessionOperationServlet.EventType.values()[(expected.ordinal() + 1) % 2];
        }
    }
}

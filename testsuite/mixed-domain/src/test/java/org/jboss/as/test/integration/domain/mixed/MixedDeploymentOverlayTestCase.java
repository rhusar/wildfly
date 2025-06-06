/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.mixed;

import static org.hamcrest.CoreMatchers.containsString;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.hamcrest.CoreMatchers;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class MixedDeploymentOverlayTestCase {

    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    private static final String DEPLOYMENT_NAME = "deployment.war";
    private static final String MAIN_RUNTIME_NAME =  "main-deployment.war";
    private static final String OTHER_RUNTIME_NAME =  "other-deployment.war";
    private static final PathElement DEPLOYMENT_PATH = PathElement.pathElement(DEPLOYMENT, DEPLOYMENT_NAME);
    private static final PathElement DEPLOYMENT_OVERLAY_PATH = PathElement.pathElement(DEPLOYMENT_OVERLAY, "test-overlay");
    private static final PathElement MAIN_SERVER_GROUP = PathElement.pathElement(SERVER_GROUP, "main-server-group");
    private static final PathElement OTHER_SERVER_GROUP = PathElement.pathElement(SERVER_GROUP, "other-server-group");
    private static MixedDomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainClient secondaryClient;
    private Path overlayPath;


    public static void setupDomain() {
        testSupport = MixedDomainTestSuite.getSupport(MixedDeploymentOverlayTestCase.class, false);
        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        secondaryClient = testSupport.getDomainSecondaryLifecycleUtil().getDomainClient();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        primaryClient.close();
        primaryClient = null;
        secondaryClient.close();
        secondaryClient = null;
        MixedDomainTestSuite.afterClass();
    }

    @Before
    public void setUpDeployment() throws Exception {
        // Create our deployment and overlays
        WebArchive webArchive = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME);
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        webArchive.addAsWebResource(tccl.getResource("helloWorld/index.html"), "index.html");
        overlayPath = new File(tccl.getResource("helloWorld/index_fr.html").toURI()).toPath();
        ModelNode result;
        try (InputStream is = webArchive.as(ZipExporter.class).exportAsInputStream()){
            Future<ModelNode> future = primaryClient.executeAsync(addDeployment(is), null);
            result = awaitSimpleOperationExecution(future);
        }
        assertTrue(Operations.isSuccessfulOutcome(result));
        ModelNode contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        assertTrue(contentNode.get(ARCHIVE).asBoolean(true));
    }

    @After
    public void cleanup() throws IOException {
        try {
            cleanDeployment();
        } catch (MgmtOperationException e) {
            // ignored
        }
    }

    @Test
    public void testInstallAndOverlayDeploymentOnDC() throws IOException, MgmtOperationException {
        //Let's deploy it on main-server-group
        executeAsyncForResult(primaryClient, deployOnServerGroup(MAIN_SERVER_GROUP, MAIN_RUNTIME_NAME));
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Hello World");
        if(isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Hello World");
        }
        try {
            performHttpCall(DomainTestSupport.secondaryAddress, 8380, "main-deployment/index.html", "Hello World");
            fail("'test' is available on secondary server-two");
        } catch (IOException good) {
            // good
        }
        executeAsyncForResult(primaryClient, deployOnServerGroup(OTHER_SERVER_GROUP, OTHER_RUNTIME_NAME));
        try {
            performHttpCall(DomainTestSupport.secondaryAddress, 8280, "other-deployment/index.html", "Hello World");
            fail("'test' is available on primary server-one");
        } catch (IOException good) {
            // good
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Hello World");
        if (isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Hello World");
        }
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        //Add some content
        executeAsyncForResult(primaryClient, addOverlayContent(overlayPath));
        //Add overlay on server-groups
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        //No deployment have been redeployed so overlay isn't really active
        if (isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Hello World");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Hello World");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Hello World");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Hello World");
        ModelNode redeployNothingOperation = Operations.createOperation("redeploy-links", PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployNothingOperation.get("deployments").setEmptyList();
        redeployNothingOperation.get("deployments").add(OTHER_RUNTIME_NAME);//Doesn't exist
        redeployNothingOperation.get("deployments").add("inexisting.jar");
        executeAsyncForResult(primaryClient, redeployNothingOperation);
        //Check that nothing happened
        //Only main-server-group deployments have been redeployed so overlay isn't active for other-server-group deployments
        if (isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Hello World");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Hello World");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Hello World");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Hello World");
        executeAsyncForResult(primaryClient, Operations.createOperation("redeploy-links", PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        //Only main-server-group deployments have been redeployed so overlay isn't active for other-server-group deployments
        if (isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Bonjour le monde");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Hello World");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Bonjour le monde");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Hello World");
        executeAsyncForResult(primaryClient, Operations.createOperation(REMOVE, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(REMOVE, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation("redeploy-links", PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        //Only other-server-group deployments have been redeployed because we have removed the overlay from main-server-group
        if(isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Bonjour le monde");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Bonjour le monde");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Bonjour le monde");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Bonjour le monde");
        //Falling call to redeploy-links
        redeployNothingOperation = Operations.createOperation("redeploy-links", PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployNothingOperation.get("deployments").setEmptyList();
        redeployNothingOperation.get("deployments").add(OTHER_RUNTIME_NAME);
        redeployNothingOperation.get("deployments").add("inexisting.jar");
        executeAsyncForResult(primaryClient, redeployNothingOperation);
        //Check that nothing happened
        redeployNothingOperation = Operations.createOperation("redeploy-links", PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployNothingOperation.get("deployments").setEmptyList();
        redeployNothingOperation.get("deployments").add(OTHER_RUNTIME_NAME);
        executeAsyncForFailure(secondaryClient, redeployNothingOperation, getUnknowOperationErrorCode());
        //Removing overlay for other-server-group deployments with affected set to true so those will be redeployed but not the deployments of main-server-group
        ModelNode removeLinkOp = Operations.createOperation(REMOVE, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode());
        removeLinkOp.get("redeploy-affected").set(true);
        executeAsyncForResult(primaryClient, removeLinkOp);
        if(isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Bonjour le monde");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Hello World");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Bonjour le monde");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Hello World");
        //Redeploying main-server-group deployment called main-deployment.war
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        ModelNode redeployOp = Operations.createOperation("redeploy-links", PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployOp.get("deployments").setEmptyList();
        redeployOp.get("deployments").add(MAIN_RUNTIME_NAME);
        executeAsyncForResult(primaryClient, redeployOp);
        if (isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Bonjour le monde");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Hello World");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Bonjour le monde");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Hello World");
        //Redeploying main-server-group deployment called other-deployment.war
        redeployOp = Operations.createOperation("redeploy-links", PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployOp.get("deployments").setEmptyList();
        redeployOp.get("deployments").add(OTHER_RUNTIME_NAME);
        executeAsyncForResult(primaryClient, redeployOp);
        if (isUndertowSupported()) {
            performHttpCall(DomainTestSupport.primaryAddress, 8080, "main-deployment/index.html", "Bonjour le monde");
            performHttpCall(DomainTestSupport.primaryAddress, 8180, "other-deployment/index.html", "Bonjour le monde");
        }
        performHttpCall(DomainTestSupport.secondaryAddress, 8280, "main-deployment/index.html", "Bonjour le monde");
        performHttpCall(DomainTestSupport.secondaryAddress, 8380, "other-deployment/index.html", "Bonjour le monde");

        //Remove all CLI style "deployment-overlay remove --name=overlay-test "
        ModelNode cliRemoveOverlay = Operations.createCompositeOperation();
        cliRemoveOverlay.get(STEPS).add(Operations.createOperation(REMOVE, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        cliRemoveOverlay.get(STEPS).add(Operations.createOperation(REMOVE, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        cliRemoveOverlay.get(STEPS).add(Operations.createOperation(REMOVE, PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        executeAsyncForResult(primaryClient, cliRemoveOverlay);
    }

    private void executeAsyncForResult(DomainClient client, ModelNode op) {
        Future<ModelNode> future = client.executeAsync(op, null);
        ModelNode response = awaitSimpleOperationExecution(future);
        assertTrue(response.toJSONString(true), Operations.isSuccessfulOutcome(response));
    }

    private void executeAsyncForFailure(DomainClient client, ModelNode op, String failureDescription) {
        Future<ModelNode> future = client.executeAsync(op, null);
        ModelNode response = awaitSimpleOperationExecution(future);
        assertFalse(response.toJSONString(true), Operations.isSuccessfulOutcome(response));
        assertThat(Operations.getFailureDescription(response).asString(), containsString(failureDescription));
    }

    private ModelNode addOverlayContent(Path overlay) throws IOException {
        ModelNode op = Operations.createOperation(ADD, PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).append(CONTENT, "index.html").toModelNode());
        try (ByteArrayOutputStream content = new ByteArrayOutputStream()) {
            Files.copy(overlay, content);
            op.get(CONTENT).get(BYTES).set(content.toByteArray());
        }
        return op;
    }

    private void performHttpCall(String host, int port, String path, String expected) throws IOException {
        URL url = new URL("http://" + TestSuiteEnvironment.formatPossibleIpv6Address(host) + ":" + port + "/" + path);
        URLConnection conn = url.openConnection();
        conn.setDoInput(true);
        try (InputStream in = new BufferedInputStream(conn.getInputStream()); StringWriter writer = new StringWriter()) {
            int i = in.read();
            while (i != -1) {
                writer.write((char) i);
                i = in.read();
            }
            String content = writer.toString();
            assertThat(content, CoreMatchers.containsString(expected));
        }
    }
    private ModelNode readDeploymentResource(PathAddress address) {
        ModelNode operation = Operations.createReadResourceOperation(address.toModelNode());
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(INCLUDE_DEFAULTS).set(true);
        Future<ModelNode> future = primaryClient.executeAsync(operation, null);
        ModelNode result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        return Operations.readResult(result);
    }

    private ModelNode awaitSimpleOperationExecution(Future<ModelNode> future) {
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException(e);
        }
    }

    private Operation addDeployment(InputStream attachment) throws MalformedURLException {
        ModelNode operation = Operations.createAddOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        ModelNode content = new ModelNode();
        content.get(INPUT_STREAM_INDEX).set(0);
        operation.get(CONTENT).add(content);
        return Operation.Factory.create(operation, Collections.singletonList(attachment));
    }

    private ModelNode deployOnServerGroup(PathElement group, String runtimeName) throws MalformedURLException {
        ModelNode operation = Operations.createOperation(ADD, PathAddress.pathAddress(group, DEPLOYMENT_PATH).toModelNode());
        operation.get(RUNTIME_NAME).set(runtimeName);
        operation.get(ENABLED).set(true);
        return operation;
    }

    private ModelNode undeployAndRemoveOp() throws MalformedURLException {
        // Pending, before cleaning up, check if the resource exist, otherwise this operation will generate some noise in the logs
        // that can be confusing since you will never know if it is a test error or a normal operation where the resources are not present
        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        ModelNode steps = op.get(STEPS);
        ModelNode sgDep = PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode();
        steps.add(Operations.createOperation(UNDEPLOY, sgDep));
        steps.add(Operations.createRemoveOperation(sgDep));
        sgDep = PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode();
        steps.add(Operations.createOperation(UNDEPLOY, sgDep));
        steps.add(Operations.createRemoveOperation(sgDep));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        return op;
    }

    private void cleanDeployment() throws IOException, MgmtOperationException {
        DomainTestUtils.executeForResult(undeployAndRemoveOp(), primaryClient);
    }

    protected boolean isUndertowSupported() {
        return true;
    }

    protected String getUnknowOperationErrorCode() {
        Version version = this.getClass().getAnnotation(Version.class);
        if (version.value().compare(7, 1) < 0) {
            return "WFLYCTL0031";
        }
        return "WFLYDC0032";
    }
}
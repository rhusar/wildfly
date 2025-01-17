/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.microprofile.rest.client.tck;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

import java.io.FilePermission;
import java.lang.reflect.ReflectPermission;
import java.net.SocketPermission;
import java.util.PropertyPermission;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.container.ManifestContainer;

/**
 * @author Radoslav Husar
 */
public class RestClientApplicationArchiveProcessor implements ApplicationArchiveProcessor {

    @Override
    public void process(Archive<?> applicationArchive, TestClass testClass) {
        if (applicationArchive instanceof ManifestContainer<?>) {
            ManifestContainer<?> manifestContainer = (ManifestContainer<?>) applicationArchive;

            // Run the TCK with security manager
            manifestContainer.addAsManifestResource(createPermissionsXmlAsset(
                    // TODO - once all issues are fixes, make these granular per test case

                    // Permissions required by test instrumentation - Wiremock
                    new FilePermission(System.getProperty("user.dir") + "/target/wildfly/modules/org/wiremock/main/*", "read"),
                    new PropertyPermission("wiremock.server.*", "read"),
                    // Permissions required by test instrumentation - httpclient5
                    new SocketPermission("localhost", "resolve"),
                    // Permissions required by test instrumentation - com.fasterxml.jackson.databind
                    new ReflectPermission("suppressAccessChecks"),
                    new RuntimePermission("accessDeclaredMembers"),
                    // Jetty
                    new PropertyPermission("jetty.git.hash", "write"),

                    // Permissions required by org.eclipse.microprofile.rest.client.tck.timeout.TimeoutTestBase
                    // n.b. even though these are attempted to be read in a privileged block but from a deployment...
                    new PropertyPermission("org.eclipse.microprofile.rest.client.tck.*", "read"),

                    // Permissions required by org.eclipse.microprofile.rest.client.tck.ProxyServerTest
                    new PropertyPermission("org.eclipse.microprofile.rest.client.ssl.*", "read")
            ), "permissions.xml");
        }
    }

}

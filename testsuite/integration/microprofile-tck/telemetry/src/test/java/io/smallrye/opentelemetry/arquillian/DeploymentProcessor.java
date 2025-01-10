/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.smallrye.opentelemetry.arquillian;

import static org.wildfly.testing.tools.deployments.DeploymentDescriptors.createPermissionsXmlAsset;

import java.io.FilePermission;
import java.lang.reflect.ReflectPermission;
import java.net.SocketPermission;
import java.util.PropertyPermission;

import io.smallrye.opentelemetry.ExceptionMapper;
import io.smallrye.opentelemetry.TestConfigSource;
import jakarta.ws.rs.ext.Providers;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.container.ManifestContainer;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author Pavol Loffay
 * @author Radoslav Husar
 */
public class DeploymentProcessor implements ApplicationArchiveProcessor {

    @Override
    public void process(Archive<?> applicationArchive, TestClass testClass) {
        if (applicationArchive instanceof WebArchive) {
            JavaArchive extensionsJar = ShrinkWrap.create(JavaArchive.class, "extension.jar")
                    .addClasses(ExceptionMapper.class, TestConfigSource.class)
                    .addAsServiceProvider(ConfigSource.class, TestConfigSource.class)
                    .addAsServiceProvider(Providers.class, ExceptionMapper.class);

            WebArchive war = (WebArchive) applicationArchive;
            war
                    .addAsLibraries(extensionsJar)
                    .addAsManifestResource(new StringAsset("telemetry.tck.executor=telemetry.tck.executor=java.util.concurrent.ForkJoinPool"),
                            "microprofile-telemetry-tck.properties")
                    .addAsWebInfResource(new StringAsset("<beans bean-discovery-mode=\"all\"/>"), "beans.xml");
        }

        if (applicationArchive instanceof ManifestContainer<?>) {
            ManifestContainer<?> manifestContainer = (ManifestContainer<?>) applicationArchive;

            // Run the TCK with security manager
            manifestContainer.addAsManifestResource(createPermissionsXmlAsset(
                    // Permissions required by test instrumentation - arquillian-core.jar and arquillian-testng.jar
                    new PropertyPermission("*", "read,write"),
                    new ReflectPermission("suppressAccessChecks"),
                    new RuntimePermission("accessDeclaredMembers"),
                    new RuntimePermission("getenv.*"),
                    new RuntimePermission("modifyThread"),

                    // Permissions required by test instrumentation - awaitility.jar
                    new RuntimePermission("modifyThread"),
                    new RuntimePermission("setDefaultUncaughtExceptionHandler"),

                    // Permissions required by org.eclipse.microprofile.telemetry.logs.tck.application.JulTest
                    new FilePermission(System.getProperty("user.dir") + "/target/wildfly/standalone/log/server.log", "read"),

                    // Permissions required by org.eclipse.microprofile.telemetry.metrics.tck.application.BasicHttpClient
                    new SocketPermission("localhost:8080", "connect,resolve")
            ), "permissions.xml");
        }
    }
}

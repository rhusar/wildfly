/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.jgroups.tls;

import static org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase.*;

import java.util.List;
import java.util.Set;

import org.jboss.as.test.shared.ManagementServerSetupTask;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TCP_NIO2;

/**
 * Utility interface containing {@link org.jboss.as.arquillian.api.ServerSetupTask}s for setting up TLS/SSL for JGroups channels.
 *
 * @author Radoslav Husar
 */
public interface TLSServerSetupTasks {

    /**
     * Server setup task that uses Elytron to create physical key and trust store files.
     */
    class PhysicalKeyStoresServerSetupTask extends ManagementServerSetupTask {
        @SuppressWarnings("deprecation")
        public PhysicalKeyStoresServerSetupTask(Set<String> containers) {
            super(createContainerSetConfigurationBuilder()
                    .addContainers(containers, createContainerConfigurationBuilder()
                            .setupScript(createScriptBuilder()
                                    // n.b. we cannot use a batch here since we need to run the 'generate-key-pair' operation on already running store
                                    // WFLYELY00007: The required service 'service org.wildfly.security.key-store.jgroupsKS' is not UP, it is currently 'STARTING'."}}

                                    // Setup and populate shared KS
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:add(path=%sserver.keystore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", containers.size() == 1 ? "../../../" : "")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:generate-key-pair(alias=localhost, algorithm=RSA, key-size=2048, validity=365, credential-reference={clear-text=secret}, distinguished-name=\"CN=localhost\")")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:store")

                                    // Export pem certificate
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:export-certificate(alias=localhost, path=server.keystore.pem, relative-to=jboss.server.config.dir, pem=true)")

                                    // Setup and populate shared TS
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:add(path=%sserver.truststore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", containers.size() == 1 ? "../../../" : "")
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:import-certificate(alias=client, path=server.keystore.pem, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, trust-cacerts=true, validate=false)")
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:store")
                                    .build())
                            .tearDownScript(createScriptBuilder()
                                    // n.b. clearing these stores to avoid the following issue when reusing these setup tasks or rerunning the test sans clean
                                    // WFLYELY01036: Alias 'localhost' already exists in KeyStore [ \"WFLYELY01036: Alias 'localhost' already exists in KeyStore\" ]"

                                    // Remove certificates from the temporary physical file store
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:remove-alias(alias=localhost)")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:store")
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:remove-alias(alias=client)")
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:store")

                                    // Cleanup temporary store model resources
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:remove")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:remove")
                                    .build())
                            .build())
                    .build());
        }
    }

    /**
     * Server setup task that uses Elytron to create a shared physical key and trust store files containing a generated pre-shared key.
     */
    class SharedPhysicalKeyStoresServerSetupTask extends PhysicalKeyStoresServerSetupTask {
        public SharedPhysicalKeyStoresServerSetupTask() {
            super(Set.of(NODE_1));
        }
    }

    /**
     * Server setup task that uses Elytron to create a physical key and trust store for each server.
     */
    class PhysicalKeyStoresServerSetupTask_NODE_1_2 extends PhysicalKeyStoresServerSetupTask {
        public PhysicalKeyStoresServerSetupTask_NODE_1_2() {
            super(NODE_1_2);
        }
    }

    class SecureJGroupsTransportServerSetupTask extends ManagementServerSetupTask {
        @SuppressWarnings("deprecation")
        public SecureJGroupsTransportServerSetupTask(Set<String> nodes, String tp, boolean sharedKS) {
            super(createContainerSetConfigurationBuilder()
                    .addContainers(nodes, createContainerConfigurationBuilder()
                            .setupScript(createScriptBuilder()
                                    .startBatch()
                                    .add("/subsystem=elytron/key-store=jgroupsKS:add(path=%sserver.keystore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", sharedKS ? "../../../" : "")
                                    .add("/subsystem=elytron/key-manager=jgroupsKM:add(key-store=jgroupsKS, credential-reference={clear-text=secret})")
                                    .add("/subsystem=elytron/key-store=jgroupsTS:add(path=%sserver.truststore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", sharedKS ? "../../../" : "")
                                    .add("/subsystem=elytron/trust-manager=jgroupsTM:add(key-store=jgroupsTS)")
                                    .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:add(key-manager=jgroupsKM, trust-manager=jgroupsTM, protocols=[\"TLSv1.2\"])")
                                    .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:add(key-manager=jgroupsKM, trust-manager=jgroupsTM, protocols=[\"TLSv1.2\"], authentication-optional=true, want-client-auth=true, need-client-auth=true)")
                                    .add("/subsystem=jgroups/stack=tcp/transport=%s:write-attribute(name=client-ssl-context, value=jgroupsCSC)", tp)
                                    .add("/subsystem=jgroups/stack=tcp/transport=%s:write-attribute(name=server-ssl-context, value=jgroupsSSC)", tp)
                                    .endBatch()
                                    .build())
                            .tearDownScript(createScriptBuilder()
                                    .startBatch()
                                    .add("/subsystem=jgroups/stack=tcp/transport=%s:undefine-attribute(name=server-ssl-context)", tp)
                                    .add("/subsystem=jgroups/stack=tcp/transport=%s:undefine-attribute(name=client-ssl-context)", tp)
                                    .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:remove")
                                    .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:remove")
                                    .add("/subsystem=elytron/trust-manager=jgroupsTM:remove")
                                    .add("/subsystem=elytron/key-store=jgroupsTS:remove")
                                    .add("/subsystem=elytron/key-manager=jgroupsKM:remove")
                                    .add("/subsystem=elytron/key-store=jgroupsKS:remove")
                                    .endBatch()
                                    .build())
                            .build())
                    .build());
        }
    }

    class UnsharedSecureJGroupsTransportServerSetupTask_NODE_1_2 extends SecureJGroupsTransportServerSetupTask {
        public UnsharedSecureJGroupsTransportServerSetupTask_NODE_1_2() {
            super(NODE_1_2, TCP.class.getSimpleName(), false);
        }
    }

    class SharedStoreSecureJGroupsTransportServerSetupTask_NODE_1_2_3 extends SecureJGroupsTransportServerSetupTask {
        public SharedStoreSecureJGroupsTransportServerSetupTask_NODE_1_2_3() {
            super(NODE_1_2_3, TCP.class.getSimpleName(), true);
        }
    }

    class SharedStoreSecureJGroupsTCP_NIO2TransportServerSetupTask_NODE_1_2 extends SecureJGroupsTransportServerSetupTask {
        public SharedStoreSecureJGroupsTCP_NIO2TransportServerSetupTask_NODE_1_2() {
            super(NODE_1_2, TCP_NIO2.class.getSimpleName(), true);
        }
    }

    /**
     * Server setup task that generates a separate private key for each node and creates a shared truststore
     * containing all nodes' public certificates, enabling proper mutual TLS authentication with per-node identity.
     * All key generation and truststore population is performed on the first node in the list.
     */
    class PerNodePhysicalKeyStoresServerSetupTask extends ManagementServerSetupTask {
        @SuppressWarnings("deprecation")
        public PerNodePhysicalKeyStoresServerSetupTask(List<String> nodes) {
            super(createContainerSetConfigurationBuilder()
                    .addContainer(nodes.get(0), createContainerConfigurationBuilder()
                            .setupScript(perNodeKeySetupScript(nodes))
                            .tearDownScript(perNodeKeyTearDownScript(nodes))
                            .build())
                    .build());
        }

        private static List<List<String>> perNodeKeySetupScript(List<String> nodes) {
            ScriptBuilder builder = createScriptBuilder();
            for (String node : nodes) {
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:add(path=../../../server-%s.keystore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", node, node);
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:generate-key-pair(alias=localhost, algorithm=RSA, key-size=2048, validity=365, credential-reference={clear-text=secret}, distinguished-name=\"CN=localhost\")", node);
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:store", node);
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:export-certificate(alias=localhost, path=server-%s.keystore.pem, relative-to=jboss.server.config.dir, pem=true)", node, node);
            }
            builder.add("/subsystem=elytron/key-store=jgroupsTS-shared:add(path=../../../server.truststore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)");
            for (String node : nodes) {
                builder.add("/subsystem=elytron/key-store=jgroupsTS-shared:import-certificate(alias=%s, path=server-%s.keystore.pem, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, trust-cacerts=true, validate=false)", node, node);
            }
            builder.add("/subsystem=elytron/key-store=jgroupsTS-shared:store");
            return builder.build();
        }

        private static List<List<String>> perNodeKeyTearDownScript(List<String> nodes) {
            ScriptBuilder builder = createScriptBuilder();
            for (String node : nodes) {
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:remove-alias(alias=localhost)", node);
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:store", node);
            }
            for (String node : nodes) {
                builder.add("/subsystem=elytron/key-store=jgroupsTS-shared:remove-alias(alias=%s)", node);
            }
            builder.add("/subsystem=elytron/key-store=jgroupsTS-shared:store");
            builder.add("/subsystem=elytron/key-store=jgroupsTS-shared:remove");
            for (String node : nodes) {
                builder.add("/subsystem=elytron/key-store=jgroupsKS-%s:remove", node);
            }
            return builder.build();
        }
    }

    class PerNodePhysicalKeyStoresServerSetupTask_NODE_1_2_3 extends PerNodePhysicalKeyStoresServerSetupTask {
        public PerNodePhysicalKeyStoresServerSetupTask_NODE_1_2_3() {
            super(List.of(NODE_1, NODE_2, NODE_3));
        }
    }

    /**
     * Server setup task that configures TLS transport with per-node keystores (each containing its own private key)
     * and a shared truststore (containing all nodes' public certificates).
     */
    class PerNodeSecureJGroupsTransportServerSetupTask extends ManagementServerSetupTask {
        @SuppressWarnings("deprecation")
        public PerNodeSecureJGroupsTransportServerSetupTask(List<String> nodes, String tp) {
            super(perNodeTransportConfig(nodes, tp));
        }

        private static ContainerSetConfiguration perNodeTransportConfig(List<String> nodes, String tp) {
            ContainerSetConfigurationBuilder builder = createContainerSetConfigurationBuilder();
            for (String node : nodes) {
                builder.addContainer(node, createContainerConfigurationBuilder()
                        .setupScript(createScriptBuilder()
                                .startBatch()
                                .add("/subsystem=elytron/key-store=jgroupsKS:add(path=../../../server-%s.keystore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)", node)
                                .add("/subsystem=elytron/key-manager=jgroupsKM:add(key-store=jgroupsKS, credential-reference={clear-text=secret})")
                                .add("/subsystem=elytron/key-store=jgroupsTS:add(path=../../../server.truststore.pkcs12, relative-to=jboss.server.config.dir, credential-reference={clear-text=secret}, type=PKCS12)")
                                .add("/subsystem=elytron/trust-manager=jgroupsTM:add(key-store=jgroupsTS)")
                                .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:add(key-manager=jgroupsKM, trust-manager=jgroupsTM, protocols=[\"TLSv1.2\"])")
                                .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:add(key-manager=jgroupsKM, trust-manager=jgroupsTM, protocols=[\"TLSv1.2\"], authentication-optional=true, want-client-auth=true, need-client-auth=true)")
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:write-attribute(name=client-ssl-context, value=jgroupsCSC)", tp)
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:write-attribute(name=server-ssl-context, value=jgroupsSSC)", tp)
                                .endBatch()
                                .build())
                        .tearDownScript(createScriptBuilder()
                                .startBatch()
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:undefine-attribute(name=server-ssl-context)", tp)
                                .add("/subsystem=jgroups/stack=tcp/transport=%s:undefine-attribute(name=client-ssl-context)", tp)
                                .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:remove")
                                .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:remove")
                                .add("/subsystem=elytron/trust-manager=jgroupsTM:remove")
                                .add("/subsystem=elytron/key-store=jgroupsTS:remove")
                                .add("/subsystem=elytron/key-manager=jgroupsKM:remove")
                                .add("/subsystem=elytron/key-store=jgroupsKS:remove")
                                .endBatch()
                                .build())
                        .build());
            }
            return builder.build();
        }
    }

    class PerNodeSecureJGroupsTransportServerSetupTask_NODE_1_2_3 extends PerNodeSecureJGroupsTransportServerSetupTask {
        public PerNodeSecureJGroupsTransportServerSetupTask_NODE_1_2_3() {
            super(List.of(NODE_1, NODE_2, NODE_3), TCP.class.getSimpleName());
        }
    }

}

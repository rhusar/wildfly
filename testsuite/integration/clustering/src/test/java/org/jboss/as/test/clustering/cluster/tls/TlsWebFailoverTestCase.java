/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.tls;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.web.CoarseWebFailoverTestCase;
import org.jboss.as.test.shared.ManagementServerSetupTask;

/**
 * Variation of the standard {@link CoarseWebFailoverTestCase} that uses TLS-secured JGroups communication channel.
 *
 * @author Radoslav Husar
 */
@ServerSetup({TlsWebFailoverTestCase.CreateKeyStoresServerSetupTask.class, TlsWebFailoverTestCase.ServerSetupTask.class})
public class TlsWebFailoverTestCase extends CoarseWebFailoverTestCase {

    public static class CreateKeyStoresServerSetupTask extends ManagementServerSetupTask {
        public CreateKeyStoresServerSetupTask() {
            super(createContainerSetConfigurationBuilder()
                    .addContainer(NODE_1, createContainerConfigurationBuilder()
                            .setupScript(createScriptBuilder()
                                    // n.b. we cannot use a batch here since we need to run the 'generate-key-pair' operation on already running store
                                    // WFLYELY00007: The required service 'service org.wildfly.security.key-store.jgroupsKS' is not UP, it is currently 'STARTING'."}}

                                    // Setup and populate shared KS
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:add(path=../../../server.keystore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:generate-key-pair(alias=localhost,algorithm=RSA,key-size=2048,validity=365,credential-reference={clear-text=secret},distinguished-name=\"CN=localhost\")")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:store")

                                    // Export pem certificate
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:export-certificate(alias=localhost,path=server.keystore.pem,relative-to=jboss.server.config.dir,pem=true)")

                                    // Setup and populate shared TS
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:add(path=../../../server.truststore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:import-certificate(alias=client,path=server.keystore.pem,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},trust-cacerts=true,validate=false)")
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:store")

                                    // Cleanup temporary store resources
                                    .add("/subsystem=elytron/key-store=jgroupsTS-shared:remove")
                                    .add("/subsystem=elytron/key-store=jgroupsKS-shared:remove")
                                    .build())
                            .build())
                    .build());
        }
    }

    public static class ServerSetupTask extends ManagementServerSetupTask {
        public ServerSetupTask() {
            super(createContainerSetConfigurationBuilder()
                    .addContainers(NODE_1_2_3, createContainerConfigurationBuilder()
                            .setupScript(createScriptBuilder()
                                    .startBatch()
                                    .add("/subsystem=elytron/key-store=jgroupsKS:add(path=../../../server.keystore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    .add("/subsystem=elytron/key-manager=jgroupsKM:add(key-store=jgroupsKS,credential-reference={clear-text=secret})")
                                    .add("/subsystem=elytron/key-store=jgroupsTS:add(path=../../../server.truststore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    .add("/subsystem=elytron/trust-manager=jgroupsTM:add(key-store=jgroupsTS)")
                                    .add("/subsystem=elytron/client-ssl-context=jgroupsCSC:add(key-manager=jgroupsKM,protocols=[\"TLSv1.2\"],trust-manager=jgroupsTM)")
                                    .add("/subsystem=elytron/server-ssl-context=jgroupsSSC:add(key-manager=jgroupsKM,protocols=[\"TLSv1.2\"],trust-manager=jgroupsTM,want-client-auth=true,need-client-auth=true)")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:write-attribute(name=client-context,value=jgroupsCSC")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:write-attribute(name=server-context,value=jgroupsSSC")
                                    .endBatch()
                                    .build())
                            .tearDownScript(createScriptBuilder()
                                    .startBatch()
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:undefine-attribute(name=server-context)")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:undefine-attribute(name=client-context)")
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

}

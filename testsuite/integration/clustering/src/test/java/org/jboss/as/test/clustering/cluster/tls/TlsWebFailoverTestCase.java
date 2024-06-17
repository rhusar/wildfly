/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.cluster.tls;

import java.util.Set;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.web.CoarseWebFailoverTestCase;
import org.jboss.as.test.shared.ManagementServerSetupTask;

/**
 * Variation of the 'default' {@link CoarseWebFailoverTestCase} that uses TLS-secured JGroups communication channel.
 *
 * @author Radoslav Husar
 */
@ServerSetup(TlsWebFailoverTestCase.ServerSetupTask.class)
public class TlsWebFailoverTestCase extends CoarseWebFailoverTestCase {

    // Node 1 creates the stores
    // Node 2+3 reference the stores

    public static class ServerSetupTask extends ManagementServerSetupTask {
        public ServerSetupTask() {
            super(createContainerSetConfigurationBuilder()
                    .addContainer(NODE_1, createContainerConfigurationBuilder()
                            .setupScript(createScriptBuilder()
                                    // n.b. cannot use a batch here WFLYELY00007: The required service 'service org.wildfly.security.key-store.twoWayKS' is not UP, it is currently 'STARTING'."}}
//                                    .startBatch()
                                    // Setup KS
                                    .add("/subsystem=elytron/key-store=twoWayKS:add(path=../../../server.keystore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    .add("/subsystem=elytron/key-store=twoWayKS:generate-key-pair(alias=localhost,algorithm=RSA,key-size=2048,validity=365,credential-reference={clear-text=secret},distinguished-name=\"CN=localhost\")")
                                    .add("/subsystem=elytron/key-store=twoWayKS:store()")
                                    // Setup TS
                                    .add("/subsystem=elytron/key-store=twoWayKS:export-certificate(alias=localhost,path=server.keystore.pem,relative-to=jboss.server.config.dir,pem=true)")
                                    .add("/subsystem=elytron/key-manager=twoWayKM:add(key-store=twoWayKS,credential-reference={clear-text=secret})")
                                    .add("/subsystem=elytron/key-store=twoWayTS:add(path=../../../server.truststore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    .add("/subsystem=elytron/key-store=twoWayTS:import-certificate(alias=client,path=server.keystore.pem,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},trust-cacerts=true,validate=false)")
                                    .add("/subsystem=elytron/key-store=twoWayTS:store()")
                                    // Setup TM
                                    .add("/subsystem=elytron/trust-manager=twoWayTM:add(key-store=twoWayTS)")
                                    // Setup SC
                                    .add("/subsystem=elytron/server-ssl-context=server-twoWaySSC:add(key-manager=twoWayKM,protocols=[\"TLSv1.2\"],trust-manager=twoWayTM,want-client-auth=true,need-client-auth=true)\n")
                                    .add("/subsystem=elytron/client-ssl-context=client-twoWaySSC:add(key-manager=twoWayKM,protocols=[\"TLSv1.2\"],trust-manager=twoWayTM\n")
                                    // Setup jgroups
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:write-attribute(name=client-context,value=client-twoWaySSC")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:write-attribute(name=server-context,value=server-twoWaySSC")
//                                    .endBatch()
                                    .build())
                            .tearDownScript(createScriptBuilder()
                                    .startBatch()
                                    // todo - also remove stores
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:undefine-attribute(name=client-context)")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:undefine-attribute(name=server-context)")
                                    .endBatch()
                                    .build())
                            .build())
                    .addContainers(Set.of(NODE_2, NODE_3), createContainerConfigurationBuilder()
                            .setupScript(createScriptBuilder()
                                    .startBatch()
                                    // Setup KS
                                    .add("/subsystem=elytron/key-store=twoWayKS:add(path=../../../server.keystore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    // Setup TS
                                    .add("/subsystem=elytron/key-manager=twoWayKM:add(key-store=twoWayKS,credential-reference={clear-text=secret})")
                                    .add("/subsystem=elytron/key-store=twoWayTS:add(path=../../../server.truststore.pkcs12,relative-to=jboss.server.config.dir,credential-reference={clear-text=secret},type=PKCS12)")
                                    // Setup TM
                                    .add("/subsystem=elytron/trust-manager=twoWayTM:add(key-store=twoWayTS)")
                                    // Setup SC
                                    .add("/subsystem=elytron/server-ssl-context=server-twoWaySSC:add(key-manager=twoWayKM,protocols=[\"TLSv1.2\"],trust-manager=twoWayTM,want-client-auth=true,need-client-auth=true)\n")
                                    .add("/subsystem=elytron/client-ssl-context=client-twoWaySSC:add(key-manager=twoWayKM,protocols=[\"TLSv1.2\"],trust-manager=twoWayTM\n")
                                    // Setup jgroups
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:write-attribute(name=client-context,value=client-twoWaySSC")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:write-attribute(name=server-context,value=server-twoWaySSC")
                                    .endBatch()
                                    .build())
                            .tearDownScript(createScriptBuilder()
                                    .startBatch()
                                    // todo - also remove stores
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:undefine-attribute(name=client-context)")
                                    .add("/subsystem=jgroups/stack=tcp/transport=TCP:undefine-attribute(name=server-context)")
                                    .endBatch()
                                    .build())
                            .build())
                    .build());
        }
    }

}

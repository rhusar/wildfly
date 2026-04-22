/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.clustering.jgroups.subsystem;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;

/**
 * Transformer for socket transport resources.
 *
 * @author Radoslav Husar
 */
public class SocketTransportResourceTransformer implements Consumer<ModelVersion> {

    private final List<ResourceTransformationDescriptionBuilder> builders;

    SocketTransportResourceTransformer(ResourceTransformationDescriptionBuilder parent) {
        this.builders = EnumSet.allOf(SecurableSocketTransportResourceDefinitionRegistrar.Transport.class).stream()
                .map(transport -> parent.addChildResource(transport.getPathElement()))
                .toList();
    }

    @Override
    public void accept(ModelVersion version) {
        if (JGroupsSubsystemModel.VERSION_11_0_0.requiresTransformation(version)) {
            for (ResourceTransformationDescriptionBuilder builder : this.builders) {
                builder.getAttributeBuilder()
                        .addRejectCheck(RejectAttributeChecker.DEFINED, SecurableSocketTransportResourceDefinitionRegistrar.CLIENT_SSL_CONTEXT, SecurableSocketTransportResourceDefinitionRegistrar.SERVER_SSL_CONTEXT)
                        .setDiscard(DiscardAttributeChecker.UNDEFINED, SecurableSocketTransportResourceDefinitionRegistrar.CLIENT_SSL_CONTEXT, SecurableSocketTransportResourceDefinitionRegistrar.SERVER_SSL_CONTEXT)
                        .end();
            }
        }
    }
}

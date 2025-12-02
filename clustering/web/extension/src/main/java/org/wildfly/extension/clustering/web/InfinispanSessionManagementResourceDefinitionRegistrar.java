/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web;

import java.util.List;

import org.jboss.as.clustering.controller.ISO8601DurationAttributeDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.wildfly.clustering.infinispan.service.InfinispanCacheConfigurationAttributeGroup;
import org.wildfly.clustering.server.service.CacheConfigurationAttributeGroup;
import org.wildfly.extension.clustering.web.session.infinispan.InfinispanSessionManagementProvider;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;

/**
 * Registers a resource definition for an Infinispan session management provider.
 * @author Paul Ferraro
 */
public class InfinispanSessionManagementResourceDefinitionRegistrar extends SessionManagementResourceDefinitionRegistrar {

    static final CacheConfigurationAttributeGroup CACHE_ATTRIBUTE_GROUP = new InfinispanCacheConfigurationAttributeGroup(CAPABILITY);

    static final ISO8601DurationAttributeDefinition MAX_IDLE = new ISO8601DurationAttributeDefinition.Builder("max-idle")
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();

    InfinispanSessionManagementResourceDefinitionRegistrar() {
        super(SessionManagementResourceRegistration.INFINISPAN, CACHE_ATTRIBUTE_GROUP, InfinispanSessionManagementProvider::new);
    }

    @Override
    public ResourceDescriptor.Builder apply(ResourceDescriptor.Builder builder) {
        return super.apply(builder)
                .addAttributes(List.of(MAX_IDLE))
                .requireSingletonChildResource(AffinityResourceRegistration.PRIMARY_OWNER)
                ;
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ManagementResourceRegistration registration = super.register(parent, context);

        new PrimaryOwnerAffinityResourceDefinitionRegistrar().register(registration, context);
        new RankedAffinityResourceDefinitionRegistrar().register(registration, context);

        return registration;
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.clustering.jgroups.subsystem;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;

/**
 * @author Radoslav Husar
 * @version Aug 2014
 */
public class ThreadFactoryResourceDefinition extends SimpleResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(ModelKeys.THREAD, ModelKeys.THREAD_FACTORY);

    // TODO move this
    public static ServiceName serviceName(String stack) {
        return ServiceName.JBOSS.append(JGroupsExtension.SUBSYSTEM_NAME).append(ModelKeys.STACK).append(stack).append(ModelKeys.THREAD).append(ModelKeys.THREAD_FACTORY);
    }

    static final SimpleAttributeDefinition GROUP_NAME = new SimpleAttributeDefinitionBuilder(ModelKeys.GROUP_NAME, ModelType.STRING, true)
            .setXmlName(Attribute.GROUP_NAME.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    static final SimpleAttributeDefinition THREAD_NAME_PATTERN = new SimpleAttributeDefinitionBuilder(ModelKeys.THREAD_NAME_PATTERN, ModelType.STRING, true)
            .setXmlName(Attribute.THREAD_NAME_PATTERN.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    static final SimpleAttributeDefinition PRIORITY = new SimpleAttributeDefinitionBuilder(ModelKeys.PRIORITY, ModelType.INT, true)
            .setXmlName(Attribute.PRIORITY.getLocalName())
            .setValidator(new IntRangeValidator(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY, true, true))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{
            GROUP_NAME, THREAD_NAME_PATTERN, PRIORITY
    };

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            registration.registerReadWriteAttribute(attr, null, writeHandler);
        }
    }

    ThreadFactoryResourceDefinition() {
        super(PATH,
                JGroupsExtension.getResourceDescriptionResolver(ModelKeys.THREAD, ModelKeys.THREAD_FACTORY),
                new ThreadFactoryAddHandler(ATTRIBUTES),
                new ReloadRequiredRemoveStepHandler());
    }

    static void buildTransformation(ModelVersion version, ResourceTransformationDescriptionBuilder parent) {
        // TODO
    }
}
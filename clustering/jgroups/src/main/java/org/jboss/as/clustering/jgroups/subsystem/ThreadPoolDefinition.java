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

import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Radoslav Husar
 * @version Aug 2014
 */
public class ThreadPoolDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition MIN_THREADS = new SimpleAttributeDefinitionBuilder(Attribute.MIN_THREADS.getLocalName(), ModelType.INT, true)
            .setXmlName(Attribute.MIN_THREADS.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    public static final SimpleAttributeDefinition MAX_THREADS = new SimpleAttributeDefinitionBuilder(Attribute.MAX_THREADS.getLocalName(), ModelType.INT, true)
            .setXmlName(Attribute.MAX_THREADS.getLocalName())
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, false, true))
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition QUEUE_MAX_SIZE = new SimpleAttributeDefinitionBuilder(Attribute.QUEUE_MAX_SIZE.getLocalName(), ModelType.INT, true)
            .setXmlName(Attribute.QUEUE_MAX_SIZE.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    public static final SimpleAttributeDefinition KEEPALIVE_TIME = new SimpleAttributeDefinitionBuilder(Attribute.KEEPALIVE_TIME.getLocalName(), ModelType.LONG, true)
            .setXmlName(Attribute.KEEPALIVE_TIME.getLocalName())
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition KEEPALIVE_TIME_UNIT = new SimpleAttributeDefinitionBuilder(Attribute.KEEPALIVE_TIME_UNIT.getLocalName(), ModelType.STRING, true)
            .setXmlName(Attribute.KEEPALIVE_TIME_UNIT.getLocalName())
            .setValidator(new EnumValidator<>(TimeUnit.class, false, true))
            .setDefaultValue(new ModelNode("SECONDS"))
            .setAllowExpression(true)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{
            MIN_THREADS, MAX_THREADS, QUEUE_MAX_SIZE, KEEPALIVE_TIME, KEEPALIVE_TIME_UNIT
    };

    static PathElement pathElement(String poolName) {
        return PathElement.pathElement(ModelKeys.THREAD_POOL, poolName);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            registration.registerReadWriteAttribute(attr, null, writeHandler);
        }
    }

    ThreadPoolDefinition(String poolName) {
        super(pathElement(poolName),
                JGroupsExtension.getResourceDescriptionResolver(ModelKeys.TRANSPORT, ModelKeys.THREAD_POOL),
                new AbstractAddStepHandler(ATTRIBUTES),
                ReloadRequiredRemoveStepHandler.INSTANCE
        );
    }

    static void buildTransformation(ModelVersion version, ResourceTransformationDescriptionBuilder parent) {
        // No transformations yet.
    }
}
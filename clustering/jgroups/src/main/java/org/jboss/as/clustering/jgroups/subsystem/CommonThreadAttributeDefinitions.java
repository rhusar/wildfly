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

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.concurrent.TimeUnit;

/**
 * @author Radoslav Husar
 * @version Aug 2014
 */
public interface CommonThreadAttributeDefinitions {

    SimpleAttributeDefinition MAX_THREADS = new SimpleAttributeDefinitionBuilder(ModelKeys.MAX_THREADS, ModelType.INT, false)
            .setXmlName(Attribute.MAX_THREADS.getLocalName())
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, false, true))
            .setAllowExpression(true)
            .build();

    SimpleAttributeDefinition KEEPALIVE_TIME = new SimpleAttributeDefinitionBuilder(ModelKeys.KEEPALIVE_TIME, ModelType.LONG, false)
            .setXmlName(Attribute.KEEPALIVE_TIME.getLocalName())
            .setAllowExpression(true)
            .build();

    SimpleAttributeDefinition KEEPALIVE_TIME_UNIT = new SimpleAttributeDefinitionBuilder(ModelKeys.KEEPALIVE_TIME_UNIT, ModelType.STRING, false)
            .setXmlName(Attribute.KEEPALIVE_TIME_UNIT.getLocalName())
            .setValidator(new EnumValidator<>(TimeUnit.class, false, true))
            .setAllowExpression(true)
            .build();

    SimpleAttributeDefinition CORE_THREADS = new SimpleAttributeDefinitionBuilder(ModelKeys.CORE_THREADS, ModelType.INT, true)
            .setXmlName(Attribute.CORE_THREADS.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    SimpleAttributeDefinition QUEUE_LENGTH = new SimpleAttributeDefinitionBuilder(ModelKeys.QUEUE_LENGTH, ModelType.INT, true)
            .setXmlName(Attribute.QUEUE_LENGTH.getLocalName())
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    SimpleAttributeDefinition ALLOW_CORE_TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelKeys.ALLOW_CORE_TIMEOUT, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(false))
            .build();

}
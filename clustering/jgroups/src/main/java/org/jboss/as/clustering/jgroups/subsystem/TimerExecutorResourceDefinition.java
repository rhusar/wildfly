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
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.msc.service.ServiceName;

import static org.jboss.as.clustering.jgroups.subsystem.CommonThreadAttributeDefinitions.CORE_THREADS;
import static org.jboss.as.clustering.jgroups.subsystem.CommonThreadAttributeDefinitions.QUEUE_LENGTH;

/**
 * @author Radoslav Husar
 * @version Aug 2014
 */
public class TimerExecutorResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH = PathElement.pathElement(ModelKeys.THREAD, ModelKeys.TIMER_EXECUTOR);

    // TODO move this
    public static ServiceName serviceName(String stack) {
        return ServiceName.JBOSS.append(JGroupsExtension.SUBSYSTEM_NAME).append(ModelKeys.STACK).append(stack).append(ModelKeys.THREAD).append(ModelKeys.TIMER_EXECUTOR);
    }

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{
            CORE_THREADS, QUEUE_LENGTH
    };

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        // TODO do not require reload
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            registration.registerReadWriteAttribute(attr, null, writeHandler);
        }
    }

    TimerExecutorResourceDefinition() {
        super(PATH,
                JGroupsExtension.getResourceDescriptionResolver(ModelKeys.THREAD, "executor"),
                new ExecutorAddHandler(ATTRIBUTES),
                new ReloadRequiredRemoveStepHandler()
        );
    }

    static void buildTransformation(ModelVersion version, ResourceTransformationDescriptionBuilder parent) {
        // TODO discard
    }
}
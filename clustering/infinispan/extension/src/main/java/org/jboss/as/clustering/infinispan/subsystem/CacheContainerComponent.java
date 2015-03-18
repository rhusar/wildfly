/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.clustering.infinispan.subsystem;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.msc.service.ServiceName;
import org.wildfly.clustering.infinispan.spi.service.CacheContainerServiceName;
import org.wildfly.clustering.service.GroupServiceNameFactory;

/**
 * @author Paul Ferraro
 */
public enum CacheContainerComponent implements GroupServiceNameFactory {

    ASYNC_OPERATIONS_THREAD_POOL(ThreadPoolResourceDefinition.ASYNC_OPERATIONS.getPathElement()),
    EXPIRATION_THREAD_POOL(ScheduledThreadPoolResourceDefinition.EXPIRATION.getPathElement()),
    LISTENER_THREAD_POOL(ThreadPoolResourceDefinition.LISTENER.getPathElement()),
    SITE("site"),
    STATE_TRANSFER_THREAD_POOL(ThreadPoolResourceDefinition.STATE_TRANSFER.getPathElement()),
    PERSISTENCE_THREAD_POOL(ThreadPoolResourceDefinition.PERSISTENCE.getPathElement()),
    TRANSPORT(JGroupsTransportResourceDefinition.PATH),
    TRANSPORT_THREAD_POOL(ThreadPoolResourceDefinition.TRANSPORT.getPathElement()),;

    private final String[] components;

    CacheContainerComponent(PathElement path) {
        this.components = new String[]{path.getKey(), path.getValue()};
    }

    CacheContainerComponent(String component) {
        this.components = new String[]{component};
    }

    @Override
    public ServiceName getServiceName(String container) {
        return CacheContainerServiceName.CONFIGURATION.getServiceName(container).append(this.components);
    }

    public static CacheContainerComponent forThreadPool(ResourceDefinition resource) {
        // FIXME
        return valueOf(resource.getPathElement().getValue().toUpperCase().replace("-", "_") + "_THREAD_POOL");
    }
}

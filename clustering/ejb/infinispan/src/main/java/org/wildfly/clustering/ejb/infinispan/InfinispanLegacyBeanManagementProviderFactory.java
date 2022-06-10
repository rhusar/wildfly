/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.wildfly.clustering.ejb.infinispan;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.ejb.BeanManagementProvider;
import org.wildfly.clustering.ejb.LegacyBeanManagementConfiguration;
import org.wildfly.clustering.ejb.LegacyBeanManagementProviderFactory;

/**
 * @author Paul Ferraro
 */
@Deprecated
@MetaInfServices(LegacyBeanManagementProviderFactory.class)
public class InfinispanLegacyBeanManagementProviderFactory implements LegacyBeanManagementProviderFactory {

    @Override
    public BeanManagementProvider createBeanManagementProvider(String name, LegacyBeanManagementConfiguration config) {
        return new InfinispanBeanManagementProvider<>(name, new InfinispanBeanManagementConfiguration() {
            @Override
            public String getContainerName() {
                return config.getContainerName();
            }

            @Override
            public String getCacheName() {
                return config.getCacheName();
            }

            @Override
            public Integer getMaxActiveBeans() {
                return config.getMaxActiveBeans();
            }
        });
    }
}

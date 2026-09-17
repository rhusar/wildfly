/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.container;

import org.wildfly.subsystem.service.DeploymentServiceInstaller;

/**
 * Provides service installers for a web deployment.
 * @author Paul Ferraro
 */
public interface WebDeploymentServiceInstallerProvider {

    /**
     * Returns an installer of a service providing a container-specific session manager factory.
     * @param configuration the configuration of the session manager factory
     * @return a number of service configurators
     */
    DeploymentServiceInstaller getSessionManagerFactoryServiceInstaller(SessionManagerFactoryConfiguration configuration);

    /**
     * Returns an installer of a service providing container-specific session affinity logic.
     * @param configuration the configuration of the deployment
     * @return a number of service configurators
     */
    DeploymentServiceInstaller getSessionAffinityProviderServiceInstaller(WebDeploymentConfiguration configuration);

    /**
     * Returns an installer of a service providing container-specific session locality logic, i.e. resolution of the
     * cluster member from which the session associated with a given request can currently be read locally.
     * @param configuration the configuration of the deployment
     * @return a number of service configurators
     */
    DeploymentServiceInstaller getSessionLocalityProviderServiceInstaller(WebDeploymentConfiguration configuration);
}

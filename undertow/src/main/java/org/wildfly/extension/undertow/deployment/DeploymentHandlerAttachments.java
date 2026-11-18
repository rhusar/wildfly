/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.deployment;

import io.undertow.server.session.SessionConfig;
import io.undertow.util.AttachmentKey;

/**
 * Attachments of an exchange exposed to a handler that decorates the handler chain of a deployment.
 * Such a handler wraps the initial handler of a deployment, i.e. it services a request before any servlet request
 * context exists, and before any servlet request listener is invoked.
 * @author Radoslav Husar
 */
public final class DeploymentHandlerAttachments {

    /**
     * The session configuration of the deployment servicing a request, via which a decorating handler may resolve the
     * identifier of the session associated with that request.
     */
    public static final AttachmentKey<SessionConfig> SESSION_CONFIG = AttachmentKey.create(SessionConfig.class);

    private DeploymentHandlerAttachments() {
        // Hide
    }
}

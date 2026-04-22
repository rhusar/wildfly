/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow.security.jacc;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import jakarta.security.jacc.WebResourcePermission;
import org.jboss.logging.Logger;
import org.wildfly.elytron.web.undertow.server.SecurityContextImpl;
import org.wildfly.security.authz.jacc.UncheckedPolicyUtil;

/**
 * Handler wrapper that overrides the authentication requirement when Jakarta Authorization Policy
 * indicates a resource is unchecked (accessible to unauthenticated callers).
 *
 * This wrapper executes AFTER ServletAuthenticationConstraintHandler sets the authentication
 * required flag, allowing Policy to override constraint-based requirements.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PolicyUncheckedOverrideWrapper implements HandlerWrapper {

    private static final Logger log = Logger.getLogger(PolicyUncheckedOverrideWrapper.class);

    private final UncheckedPolicyUtil policyUtil;

    public PolicyUncheckedOverrideWrapper(UncheckedPolicyUtil policyUtil) {
        if (policyUtil == null) {
            throw new IllegalArgumentException("policyUtil must not be null");
        }
        this.policyUtil = policyUtil;
    }

    @Override
    public HttpHandler wrap(HttpHandler next) {
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                SecurityContext securityContext = exchange.getSecurityContext();

                if (securityContext instanceof SecurityContextImpl) {
                    SecurityContextImpl impl = (SecurityContextImpl) securityContext;
                    WebResourcePermission permission = createWebResourcePermission(exchange);

                    if (log.isTraceEnabled()) {
                        log.tracef("Checking Policy for permission: %s", permission);
                    }

                    boolean isUnchecked = policyUtil.isUnchecked(permission);
                    impl.setAuthenticationRequired(!isUnchecked);

                    if (log.isTraceEnabled()) {
                        log.tracef("Policy decision for %s %s: authenticationRequired=%b (isUnchecked=%b)",
                                exchange.getRequestMethod(), exchange.getRequestPath(),
                                !isUnchecked, isUnchecked);
                    }
                } else if (securityContext != null) {
                    log.warnf("Unable to apply Jakarta Authorization Policy - unexpected SecurityContext type: %s",
                            securityContext.getClass().getName());
                }

                next.handleRequest(exchange);
            }
        };
    }

    /**
     * Create a WebResourcePermission for the current request.
     *
     * @param exchange the HTTP exchange
     * @return the permission representing this request
     */
    private WebResourcePermission createWebResourcePermission(HttpServerExchange exchange) {
        String requestPath = exchange.getRequestPath();
        String httpMethod = exchange.getRequestMethod().toString();

        // WebResourcePermission format: WebResourcePermission(String name, String actions)
        // name = request path (servlet mapping)
        // actions = HTTP method (or null for all methods)
        return new WebResourcePermission(requestPath, httpMethod);
    }
}

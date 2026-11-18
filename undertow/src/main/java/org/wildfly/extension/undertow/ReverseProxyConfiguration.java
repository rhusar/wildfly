/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Exposes the configuration of a reverse-proxy handler resource, for reuse by other subsystems that create their own
 * proxy client and proxy handler, e.g. to proxy requests to targets that are not configured as hosts of this resource.
 *
 * @author Radoslav Husar
 */
public interface ReverseProxyConfiguration {
    UnaryServiceDescriptor<ReverseProxyConfiguration> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.undertow.reverse-proxy.configuration", ReverseProxyConfiguration.class);

    /**
     * Returns the maximum number of connections to a given host, per I/O thread.
     * @return a number of connections
     */
    int getConnectionsPerThread();

    /**
     * Returns the number of connections to a given host, per I/O thread, that are retained once idle.
     * @return a number of connections
     */
    int getCachedConnectionsPerThread();

    /**
     * Returns the number of requests that may be queued while awaiting a connection to a given host.
     * @return a queue size
     */
    int getRequestQueueSize();

    /**
     * Returns the duration, in milliseconds, after which an idle connection is closed.
     * @return a number of milliseconds
     */
    int getConnectionIdleTimeout();

    /**
     * Returns the duration, in seconds, after which a host that failed is retried.
     * @return a number of seconds
     */
    int getProblemServerRetry();

    /**
     * Returns the maximum duration, in milliseconds, of a proxied request, or -1 if unlimited.
     * @return a number of milliseconds
     */
    int getMaxRequestTime();

    /**
     * Returns the number of times a proxied request is retried, if the connection to its host fails.
     * @return a number of retries
     */
    int getMaxRetries();

    /**
     * Indicates whether existing X-Forwarded-* headers of a proxied request are reused.
     * @return true, if existing headers are reused, false otherwise
     */
    boolean isReuseXForwardedHeader();

    /**
     * Indicates whether the Host header of a proxied request is rewritten to the address of its host.
     * @return true, if the Host header is rewritten, false otherwise
     */
    boolean isRewriteHostHeader();
}

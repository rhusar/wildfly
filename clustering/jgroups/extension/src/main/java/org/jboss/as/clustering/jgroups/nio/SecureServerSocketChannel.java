/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * A {@link ServerSocketChannel} decorator that wraps a raw server socket channel, producing
 * {@link SecureSocketChannel} instances for each accepted connection. The TLS engine for each
 * accepted channel is configured in server mode using the provided {@link SSLContext}.
 * <p>
 * Non-accept operations are delegated transparently to the underlying raw server channel.
 *
 * @author Radoslav Husar
 */
public class SecureServerSocketChannel extends ServerSocketChannel {

    private final ServerSocketChannel delegate;
    private final SSLContext sslContext;
    private final ExecutorService taskExecutor;

    public SecureServerSocketChannel(ServerSocketChannel delegate, SSLContext sslContext, ExecutorService taskExecutor) {
        super(delegate.provider());

        this.delegate = delegate;
        this.sslContext = sslContext;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Returns the underlying raw (unencrypted) server channel.
     */
    public ServerSocketChannel delegate() {
        return delegate;
    }

    @Override
    public SocketChannel accept() throws IOException {
        SocketChannel rawChannel = delegate.accept();
        if (rawChannel == null) {
            return null;
        }

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);

        return new SecureSocketChannel(rawChannel, engine, taskExecutor);
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        delegate.close();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        delegate.configureBlocking(block);
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        delegate.bind(local, backlog);
        return this;
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return delegate.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return delegate.supportedOptions();
    }

    @Override
    public ServerSocket socket() {
        return delegate.socket();
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

}

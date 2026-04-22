/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLEngine;

/**
 * A {@link SocketChannel} decorator that transparently wraps a raw channel with TLS/SSL encryption
 * and decryption. All cryptographic operations are handled internally via {@link TLSByteChannel},
 * while non-I/O operations are delegated to the underlying raw channel.
 * <p>
 * Read and write operations are mutually exclusive — they share the same lock to serialize
 * concurrent access to the TLS engine, which is not thread-safe.
 * <p>
 * This channel cannot be directly registered with a {@link java.nio.channels.Selector}.
 * Instead, the underlying raw channel should be registered, with this secure channel
 * attached to the resulting {@link java.nio.channels.SelectionKey}:
 * <pre>{@code
 * SelectionKey key = secureChannel.delegate().register(selector, SelectionKey.OP_READ);
 * key.attach(secureChannel);
 * }</pre>
 *
 * @author Radoslav Husar
 */
public class SecureSocketChannel extends SocketChannel {

    private final SocketChannel delegate;
    private final TLSByteChannel tlsChannel;
    private final ReentrantLock lock = new ReentrantLock();

    public SecureSocketChannel(SocketChannel delegate, SSLEngine engine, ExecutorService taskExecutor) {
        super(delegate.provider());
        this.delegate = delegate;
        this.tlsChannel = new TLSByteChannel(delegate, engine, taskExecutor);
    }

    /**
     * Returns the underlying raw (unencrypted) channel, for use with selector registration.
     */
    public SocketChannel delegate() {
        return delegate;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        lock.lock();
        try {
            int initialPosition = dst.position();
            int rawResult = tlsChannel.decrypt(dst);
            if (rawResult < 0) {
                return rawResult;
            }
            return dst.position() - initialPosition;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long totalRead = 0;
        for (int i = offset; i < offset + length; i++) {
            ByteBuffer region = dsts[i];
            if (region.hasRemaining()) {
                int read = this.read(region);
                if (read > 0) {
                    totalRead += read;
                    if (region.hasRemaining()) {
                        break;
                    }
                } else {
                    if (read < 0 && totalRead == 0) {
                        totalRead = -1;
                    }
                    break;
                }
            }
        }
        return totalRead;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        lock.lock();
        try {
            int initialPosition = src.position();
            int rawResult = tlsChannel.encrypt(src);
            if (rawResult < 0) {
                return rawResult;
            }
            return src.position() - initialPosition;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long totalWritten = 0;
        for (int i = offset; i < offset + length; i++) {
            ByteBuffer region = srcs[i];
            if (region.hasRemaining()) {
                int written = this.write(region);
                if (written > 0) {
                    totalWritten += written;
                    if (region.hasRemaining()) {
                        break;
                    }
                } else {
                    if (written < 0 && totalWritten == 0) {
                        totalWritten = -1;
                    }
                    break;
                }
            }
        }
        return totalWritten;
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        try {
            tlsChannel.flushOutbound();
        } catch (Exception ignored) {
        }
        delegate.close();
        tlsChannel.shutdown();
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        delegate.configureBlocking(block);
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return delegate.isConnectionPending();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        return delegate.connect(remote);
    }

    @Override
    public boolean finishConnect() throws IOException {
        return delegate.finishConnect();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return delegate.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        delegate.bind(local);
        return this;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
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
    public SocketChannel shutdownInput() throws IOException {
        delegate.shutdownInput();
        return this;
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        delegate.shutdownOutput();
        return this;
    }

    @Override
    public Socket socket() {
        return delegate.socket();
    }
}

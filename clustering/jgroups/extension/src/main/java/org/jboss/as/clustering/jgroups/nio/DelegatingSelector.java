/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@link Selector} that delegates all operations to a real system selector, but intercepts
 * channel registration to unwrap TLS-wrapped channels before registration.
 * <p>
 * When a {@link SecureSocketChannel} or {@link SecureServerSocketChannel} is registered,
 * the underlying raw channel is registered with the real selector instead. This avoids the
 * JDK-internal {@code sun.nio.ch.SelChImpl} type check in {@code SelectorImpl.register()}.
 *
 * @author Radoslav Husar
 */
class DelegatingSelector extends AbstractSelector {

    private final Selector delegate;

    DelegatingSelector(SelectorProvider provider, Selector delegate) {
        super(provider);
        this.delegate = delegate;
    }

    @Override
    protected SelectionKey register(AbstractSelectableChannel channel, int ops, Object attachment) {
        SelectableChannel raw = unwrap(channel);
        try {
            return raw.register(this.delegate, ops, attachment);
        } catch (ClosedChannelException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SelectableChannel unwrap(AbstractSelectableChannel channel) {
        if (channel instanceof SecureSocketChannel secure) {
            return secure.delegate();
        }
        if (channel instanceof SecureServerSocketChannel secure) {
            return secure.delegate();
        }
        return channel;
    }

    @Override
    public Set<SelectionKey> keys() {
        return this.delegate.keys();
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return this.delegate.selectedKeys();
    }

    @Override
    public int selectNow() throws IOException {
        return this.delegate.selectNow();
    }

    @Override
    public int select(long timeout) throws IOException {
        return this.delegate.select(timeout);
    }

    @Override
    public int select() throws IOException {
        return this.delegate.select();
    }

    @Override
    public int select(Consumer<SelectionKey> action, long timeout) throws IOException {
        return this.delegate.select(action, timeout);
    }

    @Override
    public int select(Consumer<SelectionKey> action) throws IOException {
        return this.delegate.select(action);
    }

    @Override
    public int selectNow(Consumer<SelectionKey> action) throws IOException {
        return this.delegate.selectNow(action);
    }

    @Override
    public Selector wakeup() {
        this.delegate.wakeup();
        return this;
    }

    @Override
    protected void implCloseSelector() throws IOException {
        this.delegate.close();
    }
}

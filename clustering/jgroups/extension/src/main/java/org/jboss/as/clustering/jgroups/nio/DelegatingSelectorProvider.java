/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

/**
 * A {@link SelectorProvider} whose sole purpose is to provide a {@link DelegatingSelector}
 * capable of handling registration of TLS-wrapped channels. Channel creation is not supported
 * as channels are created through the {@link org.jgroups.util.SocketFactory} instead.
 *
 * @author Radoslav Husar
 */
public class DelegatingSelectorProvider extends SelectorProvider {

    private final SelectorProvider delegate = SelectorProvider.provider();

    @Override
    public AbstractSelector openSelector() throws IOException {
        return new DelegatingSelector(this, delegate.openSelector());
    }

    @Override
    public DatagramChannel openDatagramChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pipe openPipe() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketChannel openSocketChannel() {
        throw new UnsupportedOperationException();
    }
}

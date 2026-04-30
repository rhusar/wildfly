/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies {@link SocketChannel} decorator.
 *
 * @author Radoslav Husar
 */
public class SecureSocketChannelTestCase {

    private SocketChannel rawChannel;
    private SecureSocketChannel secureChannel;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        rawChannel = SocketChannel.open();
        executor = Executors.newSingleThreadExecutor();
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        secureChannel = new SecureSocketChannel(rawChannel, engine, executor);
    }

    @After
    public void tearDown() throws Exception {
        secureChannel.close();
        executor.shutdownNow();
    }

    @Test
    public void delegate() {
        assertSame(rawChannel, secureChannel.delegate());
    }

    @Test
    public void isConnected() {
        assertFalse(secureChannel.isConnected());
        assertFalse(rawChannel.isConnected());
    }

    @Test
    public void isConnectionPending() {
        assertFalse(secureChannel.isConnectionPending());
    }

    @Test
    public void bind() throws Exception {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        secureChannel.bind(address);

        assertEquals(rawChannel.getLocalAddress(), secureChannel.getLocalAddress());
    }

    @Test
    public void setAndGetOption() throws Exception {
        secureChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        assertEquals(rawChannel.getOption(StandardSocketOptions.SO_REUSEADDR), secureChannel.getOption(StandardSocketOptions.SO_REUSEADDR));
    }

    @Test
    public void supportedOptions() {
        assertEquals(rawChannel.supportedOptions(), secureChannel.supportedOptions());
    }

    @Test
    public void socket() {
        assertSame(rawChannel.socket(), secureChannel.socket());
    }

    @Test
    public void configureBlocking() throws Exception {
        secureChannel.configureBlocking(false);
        assertFalse(rawChannel.isBlocking());
        assertFalse(secureChannel.isBlocking());
    }

    @Test
    public void closeClosesDelegate() throws Exception {
        secureChannel.close();
        assertFalse(rawChannel.isOpen());
    }
}

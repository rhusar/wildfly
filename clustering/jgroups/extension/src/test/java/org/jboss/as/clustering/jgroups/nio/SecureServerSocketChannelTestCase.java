/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Radoslav Husar
 */
public class SecureServerSocketChannelTestCase {

    private ServerSocketChannel rawChannel;
    private SecureServerSocketChannel secureChannel;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        rawChannel = ServerSocketChannel.open();
        executor = Executors.newSingleThreadExecutor();
        secureChannel = new SecureServerSocketChannel(rawChannel, SSLContext.getDefault(), executor);
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
    public void acceptReturnsSecureSocketChannel() throws Exception {
        secureChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        secureChannel.configureBlocking(false);

        InetSocketAddress serverAddress = (InetSocketAddress) secureChannel.getLocalAddress();

        try (SocketChannel ignored = SocketChannel.open(serverAddress)) {
            SocketChannel accepted = secureChannel.accept();
            assertNotNull(accepted);
            assertTrue(accepted instanceof SecureSocketChannel);
            accepted.close();
        }
    }

    @Test
    public void acceptReturnsNullWhenNoConnection() throws Exception {
        secureChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        secureChannel.configureBlocking(false);

        assertNull(secureChannel.accept());
    }

    @Test
    public void bind() throws Exception {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        secureChannel.bind(address);

        assertNotNull(rawChannel.getLocalAddress());
    }

    @Test
    public void setAndGetOption() throws Exception {
        secureChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        assertTrue(secureChannel.getOption(StandardSocketOptions.SO_REUSEADDR));
        assertTrue(rawChannel.getOption(StandardSocketOptions.SO_REUSEADDR));
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
    public void getLocalAddress() throws Exception {
        secureChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        assertEquals(rawChannel.getLocalAddress(), secureChannel.getLocalAddress());
    }

    @Test
    public void configureBlocking() throws Exception {
        secureChannel.configureBlocking(false);
        assertEquals(rawChannel.isBlocking(), secureChannel.isBlocking());
    }

    @Test
    public void closeClosesDelegate() throws Exception {
        assertTrue(rawChannel.isOpen());
        secureChannel.close();
        assertFalse(rawChannel.isOpen());
    }
}

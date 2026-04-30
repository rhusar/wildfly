/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Radoslav Husar
 */
public class DelegatingSelectorTestCase {

    private Selector selector;

    @Before
    public void setUp() throws Exception {
        DelegatingSelectorProvider provider = new DelegatingSelectorProvider();
        selector = provider.openSelector();
    }

    @After
    public void tearDown() throws Exception {
        selector.close();
    }

    @Test
    public void registerSecureSocketChannel() throws Exception {
        try (SocketChannel rawChannel = SocketChannel.open()) {
            SSLEngine engine = SSLContext.getDefault().createSSLEngine();
            engine.setUseClientMode(true);
            SecureSocketChannel secureChannel = new SecureSocketChannel(rawChannel, engine, Executors.newSingleThreadExecutor());
            secureChannel.configureBlocking(false);

            SelectionKey key = secureChannel.register(selector, SelectionKey.OP_CONNECT);

            assertNotNull(key);
            assertTrue(key.isValid());
            assertFalse(selector.keys().isEmpty());
        }
    }

    @Test
    public void registerSecureServerSocketChannel() throws Exception {
        try (ServerSocketChannel rawChannel = ServerSocketChannel.open()) {
            SecureServerSocketChannel secureChannel = new SecureServerSocketChannel(rawChannel, SSLContext.getDefault(), Executors.newSingleThreadExecutor());
            secureChannel.configureBlocking(false);

            SelectionKey key = secureChannel.register(selector, SelectionKey.OP_ACCEPT);

            assertNotNull(key);
            assertTrue(key.isValid());
            assertFalse(selector.keys().isEmpty());
        }
    }

    @Test
    public void wakeup() {
        selector.wakeup();
        assertTrue(selector.isOpen());
    }

    @Test
    public void close() throws Exception {
        assertTrue(selector.isOpen());
        selector.close();
        assertFalse(selector.isOpen());
    }

    @Test
    public void selectNow() throws Exception {
        int selected = selector.selectNow();
        assertTrue(selected >= 0);
    }
}

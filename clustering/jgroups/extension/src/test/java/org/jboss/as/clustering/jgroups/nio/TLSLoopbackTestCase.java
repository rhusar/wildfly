/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * End-to-end TLS integration test over loopback using {@link SecureSocketChannel},
 * {@link SecureServerSocketChannel}, and {@link TLSByteChannel} with non-blocking
 * selector-driven I/O, mirroring how JGroups TCP_NIO2 uses these channels.
 *
 * @author Radoslav Husar
 */
public class TLSLoopbackTestCase {

    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    private static SSLContext sslContext;
    private static ExecutorService taskExecutor;

    @BeforeClass
    public static void setUpClass() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = TLSLoopbackTestCase.class.getResourceAsStream("/test-keystore.p12")) {
            keyStore.load(is, KEYSTORE_PASSWORD);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        taskExecutor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void tearDownClass() {
        taskExecutor.shutdownNow();
    }

    @Test
    public void bidirectionalDataExchange() throws Exception {
        // 64KB payload exercises large data + multiple TLS records
        byte[] clientPayload = new byte[64 * 1024];
        for (int i = 0; i < clientPayload.length; i++) {
            clientPayload[i] = (byte) (i % 127);
        }
        byte[] serverPayload = "Hello from server".getBytes();

        DelegatingSelectorProvider selectorProvider = new DelegatingSelectorProvider();

        try (ServerSocketChannel rawServerChannel = ServerSocketChannel.open();
             Selector acceptSelector = selectorProvider.openSelector()) {

            SecureServerSocketChannel secureServerChannel = new SecureServerSocketChannel(rawServerChannel, sslContext, taskExecutor);
            secureServerChannel.configureBlocking(false);
            secureServerChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            InetSocketAddress serverAddress = (InetSocketAddress) secureServerChannel.getLocalAddress();
            secureServerChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);

            // Connect client
            SocketChannel rawClientChannel = SocketChannel.open();
            SSLEngine clientEngine = sslContext.createSSLEngine();
            clientEngine.setUseClientMode(true);
            SecureSocketChannel clientChannel = new SecureSocketChannel(rawClientChannel, clientEngine, taskExecutor);
            clientChannel.configureBlocking(false);
            clientChannel.connect(serverAddress);

            Selector clientSelector = selectorProvider.openSelector();
            clientChannel.register(clientSelector, SelectionKey.OP_CONNECT);

            // Finish client connect
            finishConnect(clientChannel, clientSelector);

            // Accept server side
            acceptSelector.select(HANDSHAKE_TIMEOUT_MS);
            Iterator<SelectionKey> acceptKeys = acceptSelector.selectedKeys().iterator();
            assertTrue(acceptKeys.hasNext());
            acceptKeys.next();
            acceptKeys.remove();
            SocketChannel serverChannel = secureServerChannel.accept();
            assertTrue(serverChannel instanceof SecureSocketChannel);
            serverChannel.configureBlocking(false);

            Selector serverSelector = selectorProvider.openSelector();
            ((SecureSocketChannel) serverChannel).delegate().register(serverSelector, SelectionKey.OP_READ);
            clientChannel.delegate().register(clientSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            // Perform TLS handshake + data exchange using non-blocking selector-driven I/O
            ByteBuffer clientWriteBuf = ByteBuffer.wrap(clientPayload);
            boolean clientWriteDone = false;
            boolean serverWriteDone = false;
            // Use a small (16-byte) read buffer on the server side to exercise the scratchpad transfer logic
            ByteBuffer smallServerReadBuf = ByteBuffer.allocate(16);
            ByteBuffer serverAccumulated = ByteBuffer.allocate(clientPayload.length);
            ByteBuffer clientReadBuf = ByteBuffer.allocate(1024);

            long deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;

            while ((!clientWriteDone || !serverWriteDone || clientReadBuf.position() < serverPayload.length || serverAccumulated.hasRemaining())
                    && System.currentTimeMillis() < deadline) {

                // Client I/O
                clientSelector.selectNow();
                clientSelector.selectedKeys().clear();

                if (!clientWriteDone) {
                    clientChannel.write(clientWriteBuf);
                    if (!clientWriteBuf.hasRemaining()) {
                        clientWriteDone = true;
                    }
                }

                if (clientReadBuf.position() < serverPayload.length) {
                    int read = clientChannel.read(clientReadBuf);
                    if (read < 0) break;
                }

                // Server I/O — read into small buffer, accumulate
                serverSelector.selectNow();
                serverSelector.selectedKeys().clear();

                if (serverAccumulated.hasRemaining()) {
                    smallServerReadBuf.clear();
                    int read = serverChannel.read(smallServerReadBuf);
                    if (read < 0) break;
                    if (read > 0) {
                        smallServerReadBuf.flip();
                        serverAccumulated.put(smallServerReadBuf);
                    }
                }

                if (!serverAccumulated.hasRemaining() && !serverWriteDone) {
                    ByteBuffer writeBuf = ByteBuffer.wrap(serverPayload);
                    int written = serverChannel.write(writeBuf);
                    if (written > 0 && !writeBuf.hasRemaining()) {
                        serverWriteDone = true;
                    }
                }

                Thread.yield();
            }

            serverAccumulated.flip();
            byte[] serverReceived = new byte[serverAccumulated.remaining()];
            serverAccumulated.get(serverReceived);
            assertArrayEquals(clientPayload, serverReceived);

            clientReadBuf.flip();
            byte[] clientReceived = new byte[clientReadBuf.remaining()];
            clientReadBuf.get(clientReceived);
            assertArrayEquals(serverPayload, clientReceived);

            serverChannel.close();
            clientChannel.close();
            clientSelector.close();
            serverSelector.close();
            secureServerChannel.close();
        }
    }

    private static void finishConnect(SecureSocketChannel channel, Selector selector) throws Exception {
        long deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS;
        while (!channel.finishConnect() && System.currentTimeMillis() < deadline) {
            selector.select(100);
            selector.selectedKeys().clear();
        }
        assertTrue(channel.isConnected());
    }
}

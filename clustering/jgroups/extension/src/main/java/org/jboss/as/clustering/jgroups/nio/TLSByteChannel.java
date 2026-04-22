/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.clustering.jgroups.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

/**
 * Internal engine that orchestrates all TLS/SSL protocol mechanics: handshake state machines,
 * payload encryption, payload decryption, and delegated computational tasks.
 * <p>
 * This class wraps a raw {@link SocketChannel} and an {@link SSLEngine}, managing four internal
 * byte buffers for network inbound/outbound data and decryption/encryption scratchpads.
 *
 * @author Radoslav Husar
 */
final class TLSByteChannel {

    private final SocketChannel rawChannel;
    private final SSLEngine engine;
    private final ExecutorService taskExecutor;

    private final ByteBuffer networkInboundStore;
    private final ByteBuffer networkOutboundStore;
    private final ByteBuffer decryptionScratchpad;
    private final ByteBuffer encryptionScratchpad;

    TLSByteChannel(SocketChannel rawChannel, SSLEngine engine, ExecutorService taskExecutor) {
        this.rawChannel = rawChannel;
        this.engine = engine;
        this.taskExecutor = taskExecutor;

        SSLSession session = engine.getSession();
        int packetSize = session.getPacketBufferSize();
        int appSize = session.getApplicationBufferSize();

        this.networkInboundStore = ByteBuffer.allocate(packetSize);
        this.networkOutboundStore = ByteBuffer.allocate(packetSize);
        this.networkOutboundStore.flip();

        this.decryptionScratchpad = ByteBuffer.allocate(appSize);
        this.encryptionScratchpad = ByteBuffer.allocate(appSize);
        this.encryptionScratchpad.flip();
    }

    /**
     * Decrypts data from the network and deposits plaintext into the application region.
     *
     * @return total network bytes consumed, or -1 if end-of-stream
     */
    int decrypt(ByteBuffer applicationInputRegion) throws IOException {
        // Drain residual decrypted data from previous cycle
        transferFromScratchpad(applicationInputRegion);

        int decrypted;
        int encrypted;

        do {
            decrypted = performDecryption(decryptionScratchpad);
            encrypted = performEncryption(encryptionScratchpad);
        } while (decrypted > 0 || (encrypted > 0 && networkOutboundStore.hasRemaining() && networkInboundStore.hasRemaining()));

        // Transfer newly decrypted data to the caller's buffer
        transferFromScratchpad(applicationInputRegion);

        return decrypted;
    }

    private void transferFromScratchpad(ByteBuffer destination) {
        if (decryptionScratchpad.position() > 0) {
            decryptionScratchpad.flip();
            int transferable = Math.min(decryptionScratchpad.remaining(), destination.remaining());
            if (transferable > 0) {
                int oldLimit = decryptionScratchpad.limit();
                decryptionScratchpad.limit(decryptionScratchpad.position() + transferable);
                destination.put(decryptionScratchpad);
                decryptionScratchpad.limit(oldLimit);
            }
            decryptionScratchpad.compact();
        }
    }

    /**
     * Encrypts application data and transmits it to the network.
     *
     * @return total network bytes sent, or -1 if the channel is closed
     */
    int encrypt(ByteBuffer applicationOutboundRegion) throws IOException {
        int encrypted = performEncryption(applicationOutboundRegion);
        performDecryption(decryptionScratchpad);
        return encrypted;
    }

    /**
     * Flushes any buffered outbound encrypted data to the network.
     */
    void flushOutbound() throws IOException {
        if (networkOutboundStore.hasRemaining()) {
            transmitToNetwork(networkOutboundStore);
        }
    }

    /**
     * Shuts down the TLS engine, suppressing any faults.
     */
    void shutdown() {
        try {
            engine.closeInbound();
        } catch (Exception ignored) {
        }
        try {
            engine.closeOutbound();
        } catch (Exception ignored) {
        }
    }

    private int performDecryption(ByteBuffer applicationInputRegion) throws IOException {
        int totalReadFromNetwork = 0;

        outer:
        do {
            // Phase 1: Ingest encrypted data from network
            int sessionBytesRead = 0;
            while (networkInboundStore.hasRemaining()) {
                int bytesFromNetwork = rawChannel.read(networkInboundStore);
                if (bytesFromNetwork <= 0) {
                    if (bytesFromNetwork < 0 && sessionBytesRead == 0 && totalReadFromNetwork == 0) {
                        return bytesFromNetwork;
                    }
                    break;
                } else {
                    sessionBytesRead += bytesFromNetwork;
                }
            }

            // Phase 2: Attempt TLS decryption
            networkInboundStore.flip();
            try {
                if (!networkInboundStore.hasRemaining()) {
                    return totalReadFromNetwork;
                }

                totalReadFromNetwork += sessionBytesRead;

                SSLEngineResult result = engine.unwrap(networkInboundStore, applicationInputRegion);

                // Phase 3: Interpret result
                switch (result.getStatus()) {
                    case OK:
                        switch (result.getHandshakeStatus()) {
                            case NEED_UNWRAP:
                                continue;
                            case NEED_WRAP:
                                break outer;
                            case NEED_TASK:
                                executeDelegatedTasks();
                                continue;
                            case NOT_HANDSHAKING:
                            case FINISHED:
                                continue;
                        }
                        break;

                    case BUFFER_OVERFLOW:
                        break outer;

                    case CLOSED:
                        if (totalReadFromNetwork == 0) {
                            return -1;
                        } else {
                            return totalReadFromNetwork;
                        }

                    case BUFFER_UNDERFLOW:
                        continue;
                }
            } finally {
                networkInboundStore.compact();
            }
        } while (applicationInputRegion.hasRemaining());

        return totalReadFromNetwork;
    }

    private int performEncryption(ByteBuffer applicationOutboundRegion) throws IOException {
        int totalWrittenToNetwork = 0;

        // Phase 1: Flush any previously encrypted but unsent data
        if (networkOutboundStore.hasRemaining()) {
            int flushed = transmitToNetwork(networkOutboundStore);
            if (flushed < 0) {
                return flushed;
            }
            totalWrittenToNetwork += flushed;
        }

        // Phase 2: Encrypt and transmit application data
        encryptCycle:
        for (;;) {
            networkOutboundStore.compact();

            SSLEngineResult result = engine.wrap(applicationOutboundRegion, networkOutboundStore);

            networkOutboundStore.flip();

            // Phase 2a: Transmit any newly encrypted data
            if (networkOutboundStore.hasRemaining()) {
                int written = transmitToNetwork(networkOutboundStore);
                if (written < 0) {
                    if (totalWrittenToNetwork == 0) {
                        return written;
                    } else {
                        return totalWrittenToNetwork;
                    }
                } else {
                    totalWrittenToNetwork += written;
                }
            }

            // Phase 2b: Interpret result
            switch (result.getStatus()) {
                case OK:
                    switch (result.getHandshakeStatus()) {
                        case NEED_WRAP:
                            continue;
                        case NEED_UNWRAP:
                            break encryptCycle;
                        case NEED_TASK:
                            executeDelegatedTasks();
                            continue;
                        case NOT_HANDSHAKING:
                        case FINISHED:
                            if (applicationOutboundRegion.hasRemaining()) {
                                continue;
                            } else {
                                break encryptCycle;
                            }
                    }
                    break;

                case BUFFER_OVERFLOW, BUFFER_UNDERFLOW, CLOSED:
                    break encryptCycle;
            }
        }

        return totalWrittenToNetwork;
    }

    private int transmitToNetwork(ByteBuffer sourceRegion) throws IOException {
        int totalWritten = 0;
        while (sourceRegion.hasRemaining()) {
            int written = rawChannel.write(sourceRegion);
            if (written == 0) {
                break;
            } else if (written < 0) {
                if (totalWritten == 0) {
                    return written;
                } else {
                    return totalWritten;
                }
            }
            totalWritten += written;
        }
        return totalWritten;
    }

    private void executeDelegatedTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            taskExecutor.execute(task);
        }
    }
}

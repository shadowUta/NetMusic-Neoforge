package com.github.tartaricacid.netmusic.client.audio;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.config.ServerNetworkConfig;
import com.github.tartaricacid.netmusic.network.NetworkHandler;
import com.github.tartaricacid.netmusic.network.message.RequestLocalMusicChunkMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 通过 Minecraft 网络通道从服务器分块拉取本地音乐文件的 InputStream。
 *
 * <p>联机场景下文件在服务器磁盘上，客户端无法直接 file:// 读取，
 * 因此逐块向服务器请求 {@link RequestLocalMusicChunkMessage}，
 * 服务器返回对应字节块后拼接成流。用于给 javax.sound 解码。</p>
 *
 * <p>分块大小 / 超时 / 并发上限由 netmusic-server-common.toml
 * （{@link ServerNetworkConfig}）控制。</p>
 */
public class ServerMusicInputStream extends InputStream {
    /** 与服务端一致的分块大小上限（字节），用于 byteArray 编解码约束。 */
    public static final int MAX_CHUNK_SIZE = ServerNetworkConfig.CHUNK_SIZE_HARD_CAP;

    /** 等待中的分块请求：key = id + "|" + offset。 */
    private static final Map<String, CompletableFuture<byte[]>> PENDING = new ConcurrentHashMap<>();

    private final String id;
    private final long fileLength;
    private long position;
    private byte[] buffer;
    private int bufferOffset;

    public ServerMusicInputStream(String id, long fileLength) {
        this.id = id;
        this.fileLength = fileLength;
        this.position = 0;
        this.buffer = new byte[0];
        this.bufferOffset = 0;
    }

    /** 客户端收到服务器分块数据时调用（网络线程，通过 enqueueWork 调度）。 */
    public static void onChunk(String id, long offset, byte[] data) {
        String key = key(id, offset);
        CompletableFuture<byte[]> future = PENDING.remove(key);
        if (future != null) {
            future.complete(data);
        } else {
            NetMusic.LOGGER.debug("[netmusic] received unexpected chunk for {} (no pending request)", key);
        }
    }

    private static String key(String id, long offset) {
        return id + "|" + offset;
    }

    private void fetchChunk(long offset) throws IOException {
        int maxRetries = 2;
        int attempts = 0;
        while (attempts <= maxRetries) {
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            PENDING.put(key(id, offset), future);
            int chunkSize = ServerNetworkConfig.chunkSizeBytes();
            NetworkHandler.sendToServer(new RequestLocalMusicChunkMessage(id, offset, chunkSize));
            byte[] data;
            try {
                long timeout = ServerNetworkConfig.CHUNK_TIMEOUT_SECONDS.get();
                data = future.get(timeout, TimeUnit.SECONDS);
                NetMusic.LOGGER.debug("[netmusic] fetched chunk for {} at offset {}, length {}", id, offset, data.length);
                this.buffer = data;
                this.bufferOffset = 0;
                return;
            } catch (Exception e) {
                PENDING.remove(key(id, offset));
                attempts++;
                if (attempts > maxRetries) {
                    throw new IOException("Failed to fetch local music chunk from server at offset " + offset + " after " + maxRetries + " retries", e);
                }
                NetMusic.LOGGER.warn("[netmusic] Chunk fetch timeout for {} at offset {}, retrying {}/{}", id, offset, attempts, maxRetries);
            }
        }
    }

    @Override
    public int read() throws IOException {
        if (bufferOffset >= buffer.length) {
            // Drop reliance on fileLength to detect EOF beforehand, instead fetch chunk and check if it's empty
            fetchChunk(position);
            if (buffer.length == 0) {
                return -1; // EOF
            }
        }
        int value = buffer[bufferOffset] & 0xFF;
        bufferOffset++;
        position++;
        return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        if (bufferOffset >= buffer.length) {
            // Drop reliance on fileLength to detect EOF beforehand, instead fetch chunk and check if it's empty
            fetchChunk(position);
            if (buffer.length == 0) {
                return -1;
            }
        }
        int available = buffer.length - bufferOffset;
        int toCopy = Math.min(available, len);
        System.arraycopy(buffer, bufferOffset, b, off, toCopy);
        bufferOffset += toCopy;
        position += toCopy;
        return toCopy;
    }

    @Override
    public void close() {
        // 清理所有等待中的请求，防止 future 悬挂
        PENDING.entrySet().removeIf(e -> e.getKey().startsWith(id + "|"));
    }
}

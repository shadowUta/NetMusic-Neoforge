package com.github.tartaricacid.netmusic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务器与客户端通讯限制参数配置。
 *
 * <p>本地音乐分块传输、请求超时等网络参数集中在独立的
 * netmusic-server-common.toml 中。服务端与客户端必须使用一致的
 * 分块大小（协议约束），因此双端都从这里读取。</p>
 */
public class ServerNetworkConfig {
    // ── 分块传输 ──
    /** 单次分块请求的最大字节数（KB）。上限受 Minecraft 自定义 payload 的 2MB 限制约束。 */
    public static ModConfigSpec.IntValue CHUNK_SIZE_KB;
    /** 客户端等待服务器返回单个分块的最大秒数，超时视为拉取失败。 */
    public static ModConfigSpec.IntValue CHUNK_TIMEOUT_SECONDS;
    /** 单个客户端可同时等待的分块请求数量上限（并发预取）。 */
    public static ModConfigSpec.IntValue MAX_INFLIGHT_CHUNKS;
    /** 服务器单次响应中允许返回的最大文件条目数，防止超大目录打爆 payload。 */
    public static ModConfigSpec.IntValue MAX_LIST_ENTRIES;

    /** 允许的最大分块大小（字节），超过此值的配置会被钳制。 */
    public static final int CHUNK_SIZE_HARD_CAP = 1024 * 1024; // 1MB

    /** 列表条目硬上限（编解码保护用常量）。
     *
     * <p>注意：网络包 StreamCodec 在 {@code RegisterPayloadHandlersEvent} 时
     * 静态初始化，此时配置尚未加载，绝不能调用 {@code .get()}。因此编解码
     * 上限必须用编译期常量；运行时按配置截断由 handle 里读取。</p> */
    public static final int MAX_LIST_ENTRIES_HARD_CAP = 4096;

    public static ModConfigSpec init() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("network");

        builder.comment("Chunk size in KB for server-client local music file transfer. "
                + "Must stay well below Minecraft's 2MB custom payload limit. "
                + "Both server and client read this value to keep the protocol consistent.");
        CHUNK_SIZE_KB = builder.defineInRange("ChunkSizeKB", 512, 16, 1024);

        builder.comment("Timeout in seconds while the client waits for a single chunk "
                + "response from the server before considering the fetch failed.");
        CHUNK_TIMEOUT_SECONDS = builder.defineInRange("ChunkTimeoutSeconds", 10, 1, 120);

        builder.comment("Maximum number of chunk requests a single client may have in "
                + "flight simultaneously (prefetch concurrency).");
        MAX_INFLIGHT_CHUNKS = builder.defineInRange("MaxInflightChunks", 4, 1, 32);

        builder.comment("Maximum number of file entries returned in one list response. "
                + "Prevents an oversized music folder from exceeding the payload size.");
        MAX_LIST_ENTRIES = builder.defineInRange("MaxListEntries", 512, 1, 4096);

        builder.pop();
        return builder.build();
    }

    /** 当前配置的分块大小（字节），钳制到硬上限内。 */
    public static int chunkSizeBytes() {
        int kb = CHUNK_SIZE_KB.get();
        int bytes = Math.max(1, kb) * 1024;
        return Math.min(bytes, CHUNK_SIZE_HARD_CAP);
    }
}

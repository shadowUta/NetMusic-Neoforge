package com.github.tartaricacid.netmusic.network.message;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.client.audio.ServerMusicInputStream;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务器 → 客户端：服务器本地音乐文件的一个字节分块。
 * 由 {@link ServerMusicInputStream} 的等待方消费。
 */
public record LocalMusicChunkMessage(String id, long offset, byte[] data) implements CustomPacketPayload {
    public static final Type<LocalMusicChunkMessage> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetMusic.MOD_ID, "local_music_chunk"));

    public static final StreamCodec<ByteBuf, LocalMusicChunkMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LocalMusicChunkMessage::id,
            ByteBufCodecs.VAR_LONG, LocalMusicChunkMessage::offset,
            // 上限取硬 cap（1MB），配置的分块大小必须 <= 该值
            ByteBufCodecs.byteArray(ServerMusicInputStream.MAX_CHUNK_SIZE + 1),
            LocalMusicChunkMessage::data,
            LocalMusicChunkMessage::new);

    public static void handle(LocalMusicChunkMessage message, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            ServerMusicInputStream.onChunk(message.id(), message.offset(), message.data());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

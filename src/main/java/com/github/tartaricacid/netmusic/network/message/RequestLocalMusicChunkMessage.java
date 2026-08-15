package com.github.tartaricacid.netmusic.network.message;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.server.ServerLocalMusicManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;

/**
 * 客户端 → 服务器：请求服务器本地音乐文件的 [offset, offset+len) 字节分块。
 */
public record RequestLocalMusicChunkMessage(String id, long offset, int len) implements CustomPacketPayload {
    public static final Type<RequestLocalMusicChunkMessage> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetMusic.MOD_ID, "request_local_music_chunk"));

    public static final StreamCodec<ByteBuf, RequestLocalMusicChunkMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestLocalMusicChunkMessage::id,
            ByteBufCodecs.VAR_LONG, RequestLocalMusicChunkMessage::offset,
            ByteBufCodecs.VAR_INT, RequestLocalMusicChunkMessage::len,
            RequestLocalMusicChunkMessage::new);

    public static void handle(RequestLocalMusicChunkMessage message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                int chunkSize = com.github.tartaricacid.netmusic.server.ServerLocalMusicManager.chunkSize();
                int wanted = Math.min(Math.max(message.len(), 0), chunkSize);
                byte[] data;
                try {
                    data = ServerLocalMusicManager.readChunk(message.id(), Math.max(message.offset(), 0), wanted);
                } catch (IOException e) {
                    NetMusic.LOGGER.error("[netmusic] failed to read local music chunk for id={} offset={}", message.id(), message.offset(), e);
                    data = new byte[0];
                }
                PacketDistributor.sendToPlayer(serverPlayer,
                        new LocalMusicChunkMessage(message.id(), message.offset(), data));
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

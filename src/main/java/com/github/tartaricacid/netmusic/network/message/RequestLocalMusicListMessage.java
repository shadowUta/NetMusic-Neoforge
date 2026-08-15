package com.github.tartaricacid.netmusic.network.message;

import com.github.tartaricacid.netmusic.NetMusic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务器：请求服务端配置的本地音乐文件夹文件列表。
 */
public record RequestLocalMusicListMessage() implements CustomPacketPayload {
    public static final Type<RequestLocalMusicListMessage> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetMusic.MOD_ID, "request_local_music_list"));

    public static final StreamCodec<ByteBuf, RequestLocalMusicListMessage> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
            },
            buf -> new RequestLocalMusicListMessage());

    public static void handle(RequestLocalMusicListMessage message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    java.util.List<com.github.tartaricacid.netmusic.server.ServerLocalMusicManager.LocalMusicFileEntry> entries =
                            com.github.tartaricacid.netmusic.server.ServerLocalMusicManager.scanFolders();
                    int maxEntries = com.github.tartaricacid.netmusic.config.ServerNetworkConfig.MAX_LIST_ENTRIES.get();
                    java.util.List<LocalMusicListMessage.MusicFileEntry> payload = new java.util.ArrayList<>();
                    for (var entry : entries) {
                        if (payload.size() >= maxEntries) {
                            NetMusic.LOGGER.warn("[netmusic] music folder list truncated at {} entries (see MaxListEntries in netmusic-server-common.toml)", maxEntries);
                            break;
                        }
                        payload.add(new LocalMusicListMessage.MusicFileEntry(
                                entry.id(), entry.name(), entry.file().length(), entry.timeSeconds()));
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer, new LocalMusicListMessage(payload));
                }
            });
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

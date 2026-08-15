package com.github.tartaricacid.netmusic.network.message;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.client.gui.LocalFolderPickerScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 服务器 → 客户端：服务端本地音乐文件夹的文件列表。
 */
public record LocalMusicListMessage(List<MusicFileEntry> files) implements CustomPacketPayload {
    public static final Type<LocalMusicListMessage> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetMusic.MOD_ID, "local_music_list"));

    public static final StreamCodec<ByteBuf, LocalMusicListMessage> STREAM_CODEC = StreamCodec.composite(
            // 注意：StreamCodec 静态初始化时配置尚未加载，必须用常量上限（见
            // ServerNetworkConfig.MAX_LIST_ENTRIES_HARD_CAP）。实际列表截断在
            // 服务端 handle 里按配置 MaxListEntries 进行。
            ByteBufCodecs.collection(java.util.ArrayList::new,
                    MusicFileEntry.STREAM_CODEC,
                    com.github.tartaricacid.netmusic.config.ServerNetworkConfig.MAX_LIST_ENTRIES_HARD_CAP),
            LocalMusicListMessage::files,
            LocalMusicListMessage::new);

    public static void handle(LocalMusicListMessage message, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> LocalFolderPickerScreen.onServerList(message.files()));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 单个文件条目：id（服务器端稳定标识）+ 展示名 + 文件长度（字节）+ 时长（秒）。
     */
    public record MusicFileEntry(String id, String name, long fileLength, int timeSeconds) implements CustomPacketPayload {
        public static final Type<MusicFileEntry> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(NetMusic.MOD_ID, "local_music_list_entry"));

        public static final StreamCodec<ByteBuf, MusicFileEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MusicFileEntry::id,
                ByteBufCodecs.STRING_UTF8, MusicFileEntry::name,
                ByteBufCodecs.VAR_LONG, MusicFileEntry::fileLength,
                ByteBufCodecs.VAR_INT, MusicFileEntry::timeSeconds,
                MusicFileEntry::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

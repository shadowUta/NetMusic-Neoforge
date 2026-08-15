package com.github.tartaricacid.netmusic.network;

import com.github.tartaricacid.netmusic.network.message.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    private static final String VERSION = "1.5.1";

    public static void registerPacket(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION).optional();

        registrar.playToClient(MusicToClientMessage.TYPE, MusicToClientMessage.STREAM_CODEC, MusicToClientMessage::handle);
        registrar.playToClient(GetMusicListMessage.TYPE, GetMusicListMessage.STREAM_CODEC, GetMusicListMessage::handle);

        registrar.playToServer(SetMusicIDMessage.TYPE, SetMusicIDMessage.STREAM_CODEC, SetMusicIDMessage::handle);

        // 服务器本地音乐文件夹（联机场景）：列表 + 分块传输
        registrar.playToServer(RequestLocalMusicListMessage.TYPE, RequestLocalMusicListMessage.STREAM_CODEC, RequestLocalMusicListMessage::handle);
        registrar.playToClient(LocalMusicListMessage.TYPE, LocalMusicListMessage.STREAM_CODEC, LocalMusicListMessage::handle);
        registrar.playToServer(RequestLocalMusicChunkMessage.TYPE, RequestLocalMusicChunkMessage.STREAM_CODEC, RequestLocalMusicChunkMessage::handle);
        registrar.playToClient(LocalMusicChunkMessage.TYPE, LocalMusicChunkMessage.STREAM_CODEC, LocalMusicChunkMessage::handle);

        registrar.playToClient(BigMegaphoneStartMessage.TYPE, BigMegaphoneStartMessage.STREAM_CODEC, BigMegaphoneStartMessage::handle);
        registrar.playToClient(BigMegaphoneStopMessage.TYPE, BigMegaphoneStopMessage.STREAM_CODEC, BigMegaphoneStopMessage::handle);
        registrar.playToServer(BigMegaphoneControlMessage.TYPE, BigMegaphoneControlMessage.STREAM_CODEC, BigMegaphoneControlMessage::handle);

        // CompatRegistry.initNetwork(registrar);
    }

    public static void sendToNearby(Level world, BlockPos pos, CustomPacketPayload toSend) {
        if (world instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersNear(serverLevel, null, pos.getX(), pos.getY(), pos.getZ(), 96, toSend);
        }
    }

    public static void sendToServer(CustomPacketPayload message) {
        ClientPacketDistributor.sendToServer(message);
    }

    public static void sendToClientPlayer(CustomPacketPayload message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }
}

package com.github.tartaricacid.netmusic.client.api.implement;

import com.github.tartaricacid.netmusic.client.api.IAudioStreamHandler;
import com.github.tartaricacid.netmusic.client.audio.MusicBufferedInputStream;
import com.github.tartaricacid.netmusic.client.audio.ServerMusicInputStream;
import com.github.tartaricacid.netmusic.util.Mp3Util;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;

/**
 * 处理服务端本地文件协议（netmusic-server://），联机场景下从服务器
 * 分块拉取音频字节流播放。URL 形如 netmusic-server://<id>/<fileLength>。
 */
public class ServerLocalFileHandler implements IAudioStreamHandler {
    private static final String PROTOCOL = "netmusic-server";

    @Override
    public boolean canHandle(URL url) {
        return PROTOCOL.equalsIgnoreCase(url.getProtocol());
    }

    @Override
    public AudioInputStream handle(URL url) throws UnsupportedAudioFileException, IOException {
        // netmusic-server://<id>/<fileLength> —— id 为 URL 安全的 Base64。
        // 自定义协议下 getHost()/getPath() 不可靠，统一从 toString() 解析。
        String raw = url.toString();
        String prefix = PROTOCOL + "://";
        String idAndLength = raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
        int slash = idAndLength.lastIndexOf('/');
        String id = slash > 0 ? idAndLength.substring(0, slash) : idAndLength;
        long fileLength = 0;
        if (slash > 0) {
            try {
                fileLength = Long.parseLong(idAndLength.substring(slash + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        com.github.tartaricacid.netmusic.NetMusic.LOGGER.info("[netmusic] ServerLocalFileHandler parsed id: {}, fileLength: {}", id, fileLength);

        if (Minecraft.getInstance().hasSingleplayerServer()) {
            File localFile = com.github.tartaricacid.netmusic.server.ServerLocalMusicManager.decodeToFile(id);
            if (localFile != null && localFile.exists()) {
                com.github.tartaricacid.netmusic.NetMusic.LOGGER.info("[netmusic] Singleplayer detected, using direct local file stream: {}", localFile);
                BufferedInputStream bufferedInputStream = new MusicBufferedInputStream(new FileInputStream(localFile));
                Mp3Util.skipID3(bufferedInputStream);
                return AudioSystem.getAudioInputStream(bufferedInputStream);
            } else {
                throw new IOException("Local file not found for id in singleplayer mode: " + id);
            }
        }

        java.io.InputStream serverStream = new ServerMusicInputStream(id, fileLength);
        BufferedInputStream bufferedInputStream = new MusicBufferedInputStream(serverStream);
        Mp3Util.skipID3(bufferedInputStream);
        return AudioSystem.getAudioInputStream(bufferedInputStream);
    }

    @Override
    public int getPriority() {
        // 优先于 DirectHttpHandler 等，避免误匹配
        return 100;
    }
}

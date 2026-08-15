package com.github.tartaricacid.netmusic.client.audio;

import com.github.tartaricacid.netmusic.NetMusic;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Function;

public final class MusicPlayManager {
    public static final String ERROR_404 = "http://music.163.com/404";
    public static final String MUSIC_163_URL = "https://music.163.com/";
    private static final String LOCAL_FILE_PROTOCOL = "file";
    private static final String SERVER_FILE_PROTOCOL = "netmusic-server";

    public static void play(String url, String songName, Function<URL, SoundInstance> sound) {
        Optional<String> finalUrl = getFinalUrl(url);

        if (finalUrl.isPresent()) {
            playMusic(finalUrl.get(), songName, sound);
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player != null) {
                minecraft.submitAsync(() -> player.sendSystemMessage(Component.translatable("message.netmusic.music_player.404", url)
                        .withStyle(ChatFormatting.RED)));
            }
            NetMusic.LOGGER.info("Music not found: {}", url);
        }
    }

    private static void playMusic(String url, String songName, Function<URL, SoundInstance> sound) {
        try {
            // netmusic-server:// 是自定义协议，Java 的 URI.toURL() 对未知协议会抛
            // MalformedURLException，用自定义 URLStreamHandler 承载（handler 从 toString 解析）。
            final URL urlFinal;
            if (url.startsWith(SERVER_FILE_PROTOCOL + "://")) {
                urlFinal = new URL(null, url, new java.net.URLStreamHandler() {
                    @Override
                    protected java.net.URLConnection openConnection(java.net.URL u) {
                        return null;
                    }
                });
            } else {
                urlFinal = new URI(url).toURL();
            }
            Minecraft.getInstance().submitAsync(() -> {
                SoundInstance instance = sound.apply(urlFinal);
                Minecraft.getInstance().getSoundManager().play(instance);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("record.nowPlaying", songName));
                }
            });
        } catch (MalformedURLException | URISyntaxException e) {
            NetMusic.LOGGER.error("Malformed URL: {}", url, e);
        }
    }

    public static Optional<String> getFinalUrl(String url) {
        // 自定义协议 netmusic-server:// 无法用 Java URL 解析，直接放行
        // （文件在服务器磁盘上，通过网络通道分块拉取）。
        if (url != null && url.startsWith(SERVER_FILE_PROTOCOL + "://")) {
            return Optional.of(url);
        }
        try {
            URL urlFinal = URI.create(url).toURL();
            // 如果是本地文件
            if (urlFinal.getProtocol().equals(LOCAL_FILE_PROTOCOL)) {
                File file = new File(urlFinal.toURI());
                if (!file.exists()) {
                    NetMusic.LOGGER.info("File not found: {}", url);
                    return Optional.empty();
                }
            }
        } catch (URISyntaxException | MalformedURLException e) {
            NetMusic.LOGGER.error("Malformed URL: {}", url, e);
            return Optional.empty();
        }

        return Optional.of(url);
    }
}

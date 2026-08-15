package com.github.tartaricacid.netmusic.server;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.config.GeneralConfig;
import com.github.tartaricacid.netmusic.config.ServerNetworkConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端本地音乐文件夹管理器。
 *
 * <p>联机场景下，LocalMusicFolders 配置在服务器端，客户端通过
 * 网络消息请求文件列表与分块数据。此类只在服务器侧（物理服务端
 * 或单人游戏主机）调用，读取的是服务端磁盘上的文件。</p>
 */
public final class ServerLocalMusicManager {
    /** 网络分块大小（字节），由 netmusic-server-common.toml 的 ChunkSizeKB 控制。 */
    public static int chunkSize() {
        return ServerNetworkConfig.chunkSizeBytes();
    }

    /** 服务端支持的音频扩展名。 */
    private static final List<String> AUDIO_EXTS = List.of("mp3", "flac", "aac", "m4a", "wav", "ogg");

    /** 文件条目：给客户端的稳定标识 + 展示名 + 服务端绝对路径 + 时长（秒）。 */
    public record LocalMusicFileEntry(String id, String name, File file, int timeSeconds) {
    }

    private ServerLocalMusicManager() {
    }

    /**
     * 扫描服务端配置的所有本地音乐文件夹（含子目录递归）。
     * 每个文件生成一个稳定 id（URL 安全的 Base64 绝对路径），
     * 客户端后续用该 id 请求分块数据。
     */
    public static List<LocalMusicFileEntry> scanFolders() {
        List<LocalMusicFileEntry> result = new ArrayList<>();
        List<? extends String> folders = GeneralConfig.LOCAL_MUSIC_FOLDERS.get();
        if (folders == null || folders.isEmpty()) {
            return result;
        }
        for (String folderPath : folders) {
            File folder = new File(folderPath);
            if (folder.exists() && folder.isDirectory()) {
                scanDirectory(folder, result);
            } else {
                NetMusic.LOGGER.warn("[netmusic] configured local music folder does not exist: {}", folderPath);
            }
        }
        return result;
    }

    private static void scanDirectory(File directory, List<LocalMusicFileEntry> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, result);
            } else if (isAudioFile(file)) {
                String name = stripExtension(file.getName());
                String id = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(file.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                result.add(new LocalMusicFileEntry(id, name, file, readAudioDuration(file)));
            }
        }
    }

    /**
     * 解析音频文件时长（秒）。失败返回 0，客户端会提示用户手动填写。
     */
    private static int readAudioDuration(File file) {
        try {
            javax.sound.sampled.AudioFileFormat format =
                    javax.sound.sampled.AudioSystem.getAudioFileFormat(file);
            long frames = format.getFrameLength();
            float frameRate = format.getFormat().getFrameRate();
            if (frames > 0 && frameRate > 0) {
                int seconds = (int) Math.round(frames / frameRate);
                return Math.max(1, seconds);
            }
        } catch (Exception e) {
            // 格式不支持/解析失败，交给客户端手动填时长
        }
        return 0;
    }

    /**
     * 按 id 读取文件的 [offset, offset+len) 分块。
     *
     * @return 实际读到的字节；文件已到末尾且无数据时返回长度为 0 的数组。
     */
    public static byte[] readChunk(String id, long offset, int len) throws IOException {
        File file = decodeToFile(id);
        if (file == null || !file.isFile()) {
            return new byte[0];
        }
        int wanted = Math.min(len, chunkSize());
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (offset >= fileLength) {
                return new byte[0];
            }
            raf.seek(offset);
            int toRead = (int) Math.min(wanted, fileLength - offset);
            byte[] buffer = new byte[toRead];
            raf.readFully(buffer);
            return buffer;
        }
    }

    /** 获取文件总长度，用于客户端展示/判断（也可用于读到最后一块）。 */
    public static long fileLength(String id) {
        File file = decodeToFile(id);
        if (file == null || !file.isFile()) {
            return -1;
        }
        return file.length();
    }

    /**
     * 把客户端传来的 id 解码回文件路径。仅允许落在配置文件夹内的文件，
     * 防止通过伪造 id 读取服务器任意路径。
     */
    public static File decodeToFile(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        File target;
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(id);
            String path = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            target = new File(path).getCanonicalFile();
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
        List<? extends String> folders = GeneralConfig.LOCAL_MUSIC_FOLDERS.get();
        if (folders == null) {
            return null;
        }
        for (String folderPath : folders) {
            try {
                File root = new File(folderPath).getCanonicalFile();
                String rootPath = root.getPath();
                String targetPath = target.getPath();
                if (targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator)) {
                    return target;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = name.substring(dot + 1);
        return AUDIO_EXTS.contains(ext);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

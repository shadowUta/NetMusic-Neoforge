package com.github.tartaricacid.netmusic.client.config;

import com.github.tartaricacid.netmusic.config.GeneralConfig;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalMusicFolderManager {
    public static class LocalMusicFile {
        public final String name;
        public final String absolutePath;
        public final String url;

        public LocalMusicFile(String name, String absolutePath, String url) {
            this.name = name;
            this.absolutePath = absolutePath;
            this.url = url;
        }
    }

    public static List<LocalMusicFile> scanFolders() {
        List<LocalMusicFile> result = new ArrayList<>();
        List<? extends String> folders = GeneralConfig.LOCAL_MUSIC_FOLDERS.get();
        if (folders == null || folders.isEmpty()) {
            return result;
        }

        for (String folderPath : folders) {
            File folder = new File(folderPath);
            if (folder.exists() && folder.isDirectory()) {
                scanDirectory(folder, result);
            }
        }

        return result;
    }

    private static void scanDirectory(File directory, List<LocalMusicFile> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, result);
            } else {
                String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
                if (ext.equals("mp3") || ext.equals("flac") || ext.equals("aac") || ext.equals("m4a") || ext.equals("wav") || ext.equals("ogg")) {
                    try {
                        String url = file.toURI().toURL().toString();
                        String name = FilenameUtils.getBaseName(file.getName());
                        result.add(new LocalMusicFile(name, file.getAbsolutePath(), url));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
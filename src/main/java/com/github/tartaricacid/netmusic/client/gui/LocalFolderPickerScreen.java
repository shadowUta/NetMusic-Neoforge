package com.github.tartaricacid.netmusic.client.gui;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.client.config.LocalMusicFolderManager;
import com.github.tartaricacid.netmusic.network.NetworkHandler;
import com.github.tartaricacid.netmusic.network.message.LocalMusicListMessage;
import com.github.tartaricacid.netmusic.network.message.RequestLocalMusicListMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 选择本地音乐文件。联机场景下优先从服务器拉取服务端配置的
 * 文件夹列表（文件在服务器磁盘上），单机/无服务器时回退到本地扫描。
 */
public class LocalFolderPickerScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 180;

    /** 统一的展示条目。 */
    public record PickerFile(String name, String url, int timeSeconds) {
    }

    /** 当前打开的屏幕实例，供服务器列表消息回调。 */
    private static LocalFolderPickerScreen ACTIVE_SCREEN;

    private final ComputerMenuScreen parent;
    private List<PickerFile> files = new ArrayList<>();
    private boolean loading = true;
    private int page = 0;
    private int leftPos;
    private int topPos;

    public LocalFolderPickerScreen(ComputerMenuScreen parent) {
        super(Component.translatable("gui.netmusic.computer.folder_picker"));
        this.parent = parent;
        ACTIVE_SCREEN = this;
    }

    /**
     * 客户端收到服务器文件列表时调用（enqueueWork 调度到主线程）。
     */
    public static void onServerList(List<LocalMusicListMessage.MusicFileEntry> entries) {
        Minecraft.getInstance().execute(() -> {
            LocalFolderPickerScreen screen = ACTIVE_SCREEN;
            if (screen == null) {
                return;
            }
            List<PickerFile> list = new ArrayList<>();
            boolean isSingleplayer = Minecraft.getInstance().hasSingleplayerServer();
            for (var entry : entries) {
                String url = null;
                if (isSingleplayer) {
                    try {
                        java.io.File localFile = com.github.tartaricacid.netmusic.server.ServerLocalMusicManager.decodeToFile(entry.id());
                        if (localFile != null && localFile.exists()) {
                            // 单机填原始 Windows 路径（如 C:\Users\...\song.mp3）：比 file:///... 编码 URL
                            // 短得多、直观可读；handleCraftButton 的 URL_FILE_REG 匹配该格式并转 file:// URL。
                            url = localFile.getAbsolutePath();
                        }
                    } catch (Exception e) {
                        NetMusic.LOGGER.warn("[netmusic] Failed to decode local file in singleplayer mode: {}", entry.id(), e);
                    }
                }
                if (url == null) {
                    // netmusic-server://<id>/<fileLength>
                    url = "netmusic-server://" + entry.id() + "/" + entry.fileLength();
                }
                list.add(new PickerFile(entry.name(), url, entry.timeSeconds()));
            }
            screen.setFiles(list);
        });
    }

    private void setFiles(List<PickerFile> list) {
        this.files = list;
        this.loading = false;
        this.page = 0;
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;
        this.clearWidgets();
        this.rebuildButtons();

        // 打开时向服务器请求文件列表（联机场景）。单机/无服务器时
        // 服务器同样会处理（集成服务器在同一进程），回退由空列表承载。
        if (this.loading) {
            NetworkHandler.sendToServer(new RequestLocalMusicListMessage());
        }
    }

    private void rebuildButtons() {
        this.clearWidgets();

        int navY = this.topPos + PANEL_HEIGHT - 24;

        if (this.loading) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.computer.folder_loading"), b -> {
            }).pos(this.leftPos, this.topPos + 60).size(PANEL_WIDTH, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> closeScreen())
                    .pos(this.leftPos + 82, navY).size(76, 20).build());
            return;
        }

        if (this.files.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.computer.folder_empty"), b -> {
            }).pos(this.leftPos, this.topPos + 60).size(PANEL_WIDTH, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> closeScreen())
                    .pos(this.leftPos + 82, navY).size(76, 20).build());
            return;
        }

        int startY = this.topPos + 20;
        int maxIndex = Math.min(this.page * PAGE_SIZE + PAGE_SIZE, this.files.size());
        for (int i = this.page * PAGE_SIZE; i < maxIndex; i++) {
            PickerFile file = this.files.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(file.name()), b -> {
                this.parent.applyPickedFile(file);
                closeScreen();
            }).pos(this.leftPos, startY).size(PANEL_WIDTH, 20).build());
            startY += 22;
        }

        if (this.page > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                this.page--;
                this.rebuildButtons();
            }).pos(this.leftPos, navY).size(76, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> closeScreen())
                .pos(this.leftPos + 82, navY).size(76, 20).build());

        if (maxIndex < this.files.size()) {
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                this.page++;
                this.rebuildButtons();
            }).pos(this.leftPos + 164, navY).size(76, 20).build());
        }
    }

    private void closeScreen() {
        ACTIVE_SCREEN = null;
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.closeScreen();
    }

    @Override
    public void removed() {
        super.removed();
        if (ACTIVE_SCREEN == this) {
            ACTIVE_SCREEN = null;
        }
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // 必须调用 super：Screen.extractRenderState 负责渲染所有 widgets（按钮）。
        // 不调用会导致按钮可点击但不可见。
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, this.topPos + 6, 0xFFFFFFFF);
        if (!this.loading && !this.files.isEmpty()) {
            int maxPage = (this.files.size() - 1) / PAGE_SIZE + 1;
            String pageStr = String.format("%d / %d", this.page + 1, maxPage);
            graphics.centeredText(this.font, pageStr, this.width / 2, this.topPos + PANEL_HEIGHT - 10, 0xFFAAAAAA);
        }
    }

    // 兼容旧入口：单机时也尝试本地扫描（保留逻辑，联机由服务器提供列表）
    @SuppressWarnings("unused")
    private static List<PickerFile> scanLocalFallback() {
        List<PickerFile> list = new ArrayList<>();
        try {
            for (var file : LocalMusicFolderManager.scanFolders()) {
                list.add(new PickerFile(file.name, file.url, 0));
            }
        } catch (Exception e) {
            NetMusic.LOGGER.error("[netmusic] local folder scan failed", e);
        }
        return list;
    }
}
package com.vizor.optirec;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class RecSettingsScreen extends Screen {

    private static String statusMsg = "";
    private static long statusUntil = 0;

    public RecSettingsScreen() {
        super(new LiteralText("Настройки OptiRec"));
    }

    public static void toast(String msg, long ms) {
        statusMsg = msg;
        statusUntil = System.currentTimeMillis() + ms;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40;

        this.addButton(new ButtonWidget(cx - 140, y, 280, 22,
                new LiteralText(Recorder.isRecording()
                        ? "§c■ ОСТАНОВИТЬ ЗАПИСЬ"
                        : "§a● НАЧАТЬ ЗАПИСЬ ЭКРАНА"),
                b -> {
                    toggleRecording();
                    this.init(this.client, this.width, this.height);
                }));
        y += 28;

        this.addButton(new ButtonWidget(cx - 140, y, 280, 20,
                new LiteralText("§eНазначить клавиши (Управление)"),
                b -> MinecraftClient.getInstance().openScreen(
                        new ControlsOptionsScreen(this, MinecraftClient.getInstance().options))));
        y += 26;

        this.addButton(new ButtonWidget(cx - 140, y, 280, 20,
                new LiteralText(String.format("§7Разрешение: %dx%d", getRecWidth(), getRecHeight())),
                b -> {}));
        y += 26;

        this.addButton(new ButtonWidget(cx - 140, y, 280, 20,
                new LiteralText("§7FFmpeg: " + (Recorder.findFfmpegReport() ? "§aнайден" : "§cНЕ найден!")),
                b -> {}));
        y += 30;

        this.addButton(new ButtonWidget(cx - 140, y, 280, 20,
                new LiteralText("Готово"), b -> this.onClose()));
    }

    private void toggleRecording() {
        if (Recorder.isRecording()) {
            Recorder.stop();
            toast("§cЗапись остановлена → .minecraft/recordings/", 4000);
        } else {
            int w = getRecWidth();
            int h = getRecHeight();
            if (Recorder.start(w, h)) {
                toast("§a● Идёт запись... Стоп — клавиша (по умолч. F8)", 3500);
            } else {
                toast("§4Ошибка: " + Recorder.getLastError(), 4500);
            }
        }
    }

    private static int getRecWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getWindow() != null ? mc.getWindow().getFramebufferWidth() : 1280;
    }

    private static int getRecHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getWindow() != null ? mc.getWindow().getFramebufferHeight() : 720;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        net.minecraft.client.gui.DrawableHelper.drawCenteredText(matrices, this.textRenderer,
                "§6§lOptiRec §7— §fзапись полного экрана (GUI+чат)", this.width / 2, 12, 0xFFFFFF);
        drawCenteredText(matrices, "Файл → .minecraft/recordings/rec_....mp4 (FFmpeg, 24 FPS)", 34, 0xAAAAAA);
        drawCenteredText(matrices, "F8 — старт/стоп записи, F9 — это меню", 48, 0xAAAAAA);

        if (System.currentTimeMillis() < statusUntil && !statusMsg.isEmpty()) {
            drawCenteredText(matrices, statusMsg, 62, 0xFFFFFF);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void drawCenteredText(MatrixStack matrices, String text, int y, int color) {
        this.textRenderer.drawWithShadow(matrices, text,
                (this.width - this.textRenderer.getWidth(text)) / 2f, y, color);
    }
}

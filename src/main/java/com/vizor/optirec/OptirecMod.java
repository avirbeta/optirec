package com.vizor.optirec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

public class OptirecMod implements ClientModInitializer {

    private static KeyBinding toggleRec;
    private static KeyBinding openSettings;
    private static int lastRecWidth = -1;
    private static int lastRecHeight = -1;

    @Override
    public void onInitializeClient() {
        toggleRec = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Старт/Стоп записи экрана",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "OptiRec"
        ));
        openSettings = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Открыть меню OptiRec",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "OptiRec"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleRec.wasPressed()) {
                toggleRecording(client);
            }
            while (openSettings.wasPressed()) {
                if (client.currentScreen == null) {
                    client.openScreen(new RecSettingsScreen());
                }
            }
        });

        HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
            if (Recorder.isRecording()) {
                drawIndicator(matrices);
            }
        });
    }

    private static void toggleRecording(MinecraftClient mc) {
        if (Recorder.isRecording()) {
            Recorder.stop();
            RecSettingsScreen.toast("§cЗапись остановлена → .minecraft/recordings/", 4000);
            sendChat(mc, "§c[OptiRec] Запись остановлена → .minecraft/recordings/");
        } else {
            int w = mc.getWindow() != null ? mc.getWindow().getFramebufferWidth() : 1280;
            int h = mc.getWindow() != null ? mc.getWindow().getFramebufferHeight() : 720;
            lastRecWidth = w;
            lastRecHeight = h;
            if (Recorder.start(w, h)) {
                RecSettingsScreen.toast("§a● Идёт запись... (стоп — F8)", 3500);
                sendChat(mc, "§a[OptiRec] ● Идёт запись экрана... (стоп — F8)");
            } else {
                RecSettingsScreen.toast("§4Ошибка: не найден FFmpeg", 4000);
                sendChat(mc, "§4[OptiRec] Ошибка: не найден FFmpeg");
            }
        }
    }

    private static void sendChat(MinecraftClient mc, String text) {
        if (mc.player != null) {
            mc.player.sendMessage(new LiteralText(text), false);
        }
    }

    private static void drawIndicator(net.minecraft.client.util.math.MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long secs = Recorder.getElapsedMs() / 1000;
        String time = String.format("%02d:%02d", secs / 60, secs % 60);
        String text = "§c● REC " + time;
        // красный полупрозрачный фон + текст вправо
        int w = mc.textRenderer.getWidth(text) + 8;
        net.minecraft.client.gui.DrawableHelper.fill(matrices, mc.getWindow().getScaledWidth() - w - 4, 4,
                mc.getWindow().getScaledWidth() - 4, 18, 0x88000000);
        mc.textRenderer.drawWithShadow(matrices, text,
                mc.getWindow().getScaledWidth() - w, 7, 0xFFFFFF);
    }
}

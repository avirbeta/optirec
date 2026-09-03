package com.vizor.optirec;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class CaptureManager {

    private static ByteBuffer pixelBuffer;
    private static ByteBuffer flipBuffer;
    private static int width = -1;
    private static int height = -1;

    private CaptureManager() {
    }

    // Вызывается из GameRendererMixin ПОСЛЕ отрисовки всего кадра
    // (мир + HUD + чат + экраны) — поэтому захватывает полный экран.
    public static void capture() {
        if (!Recorder.isRecording()) return;
        if (!Recorder.shouldCapture()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (w <= 0 || h <= 0) return;

        int size = w * h * 4;
        if (pixelBuffer == null || pixelBuffer.capacity() < size) {
            pixelBuffer = ByteBuffer.allocateDirect(size);
            flipBuffer = ByteBuffer.allocateDirect(size);
        }
        width = w;
        height = h;

        // Приводим к java.nio.Buffer: в Java 8 clear()/rewind()/position()/flip()
        // возвращают Buffer, а на JDK 9+ — ByteBuffer (ковариантный). Явное
        // приведение к Buffer гарантирует вызов метода, существующего в Java 8,
        // иначе на Java 8 будет NoSuchMethodError (краш при записи).
        Buffer p = pixelBuffer;
        p.clear();
        p.position(0);

        // GL_BGRA = 0x80E1, GL_UNSIGNED_BYTE = 0x1401
        GL11.glReadPixels(0, 0, w, h, 0x80E1, 0x1401, pixelBuffer);

        // Вертикальный разворот (glReadPixels читает снизу вверх)
        Buffer f = flipBuffer;
        f.clear();
        f.position(0);
        int rowBytes = w * 4;
        p.rewind();
        for (int row = 0; row < h; row++) {
            p.position(rowBytes * (h - 1 - row));
            p.limit(p.position() + rowBytes);
            flipBuffer.put(pixelBuffer);
        }
        p.limit(pixelBuffer.capacity());
        f.flip();

        Recorder.grabFrame(flipBuffer, w, h);
    }
}

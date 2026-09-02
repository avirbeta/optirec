package com.vizor.optirec;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Recorder {

    private static Process ffmpeg;
    private static OutputStream ffmpegIn;
    private static Thread writerThread;
    private static volatile boolean recording = false;
    private static int recWidth;
    private static int recHeight;
    private static long startTimeMs;
    private static final BlockingQueue<byte[]> QUEUE = new ArrayBlockingQueue<>(48);
    private static volatile long lastFrameNs = 0;
    private static final long FRAME_INTERVAL_NS = 1000000000L / 24; // 24 FPS — легче для CPU

    private Recorder() {
    }

    public static boolean isRecording() {
        return recording;
    }

    public static synchronized boolean start(int width, int height) {
        if (recording) return false;
        if (!findFfmpeg()) return false;

        recWidth = width;
        recHeight = height;

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File recDir = new File(gameDirFile(), "recordings");
        recDir.mkdirs();
        File outFile = new File(recDir, "rec_" + ts + ".mp4");
        String ffmpegPath = findFfmpegPath();

        String[] cmd = {
                ffmpegPath, "-y",
                "-f", "rawvideo",
                "-pix_fmt", "bgr0",
                "-s", width + "x" + height,
                "-r", "24",
                "-i", "-",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-preset", "ultrafast",
                "-crf", "26",
                "-threads", "0",
                outFile.getAbsolutePath()
        };

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            ffmpeg = pb.start();
            ffmpegIn = ffmpeg.getOutputStream();
            startTimeMs = System.currentTimeMillis();

            writerThread = new Thread(() -> {
                try {
                    while (recording || !QUEUE.isEmpty()) {
                        byte[] frame = QUEUE.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (frame == null) continue;
                        ffmpegIn.write(frame);
                    }
                    ffmpegIn.flush();
                    ffmpegIn.close();
                } catch (Exception ignored) {
                }
            }, "OptiRec-writer");
            writerThread.setDaemon(true);
            writerThread.start();

            recording = true;
            return true;
        } catch (Exception e) {
            recording = false;
            return false;
        }
    }

    public static synchronized void stop() {
        if (!recording) return;
        recording = false;

        try {
            if (writerThread != null) {
                writerThread.join(2000);
            }
        } catch (InterruptedException ignored) {
        }

        try {
            if (ffmpegIn != null) {
                ffmpegIn.close();
                ffmpegIn = null;
            }
            if (ffmpeg != null) {
                ffmpeg.destroy();
                ffmpeg.waitFor(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (ffmpeg.isAlive()) ffmpeg.destroyForcibly();
                ffmpeg = null;
            }
        } catch (Exception ignored) {
        }
        QUEUE.clear();
    }

    public static void grabFrame(ByteBuffer buffer, int width, int height) {
        if (!recording) return;
        if (width != recWidth || height != recHeight) return;
        // Кадровая экономия: не грузим CPU каждый кадр — только 24 к/с
        long now = System.nanoTime();
        if (now - lastFrameNs < FRAME_INTERVAL_NS) return;
        if (QUEUE.remainingCapacity() == 0) return; // не копим, если FFmpeg не успевает

        try {
            byte[] bytes = new byte[width * height * 4];
            buffer.duplicate().rewind().get(bytes);
            QUEUE.offer(bytes);
            lastFrameNs = now;
        } catch (Exception ignored) {
        }
    }

    // Вызывается ДО дорогого glReadPixels, чтобы не читать кадр, который всё равно отбросим
    public static boolean shouldCapture() {
        if (!recording) return false;
        return System.nanoTime() - lastFrameNs >= FRAME_INTERVAL_NS && QUEUE.remainingCapacity() > 0;
    }

    public static long getElapsedMs() {
        return recording ? System.currentTimeMillis() - startTimeMs : 0;
    }

    private static File gameDirFile() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    private static boolean findFfmpeg() {
        return findFfmpegPath() != null;
    }

    public static boolean findFfmpegReport() {
        return findFfmpegPath() != null;
    }

    private static String findFfmpegPath() {
        String path = System.getenv("FFMPEG_PATH");
        if (path != null && new File(path).canExecute()) return path;

        String[] candidates = {"ffmpeg", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg"};
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "-version").start();
                if (p.waitFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS) && p.exitValue() == 0) {
                    return c;
                }
                p.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}

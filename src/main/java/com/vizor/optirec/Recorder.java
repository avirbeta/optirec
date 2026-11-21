package com.vizor.optirec;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
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
    private static final BlockingQueue<byte[]> FREE = new ArrayBlockingQueue<>(48);
    private static volatile long lastFrameNs = 0;
    private static final long FRAME_INTERVAL_NS = 1000000000L / 24; // 24 FPS — легче для CPU
    private static volatile String ffmpegPathCache = null;
    private static volatile String lastError = "";

    private Recorder() {
    }

    public static boolean isRecording() {
        return recording;
    }

    public static synchronized boolean start(int width, int height) {
        if (recording) return false;
        if (width <= 0 || height <= 0) {
            lastError = "некорректное разрешение окна";
            return false;
        }

        String ffmpegPath = findFfmpegPath();
        if (ffmpegPath == null) {
            lastError = "FFmpeg не найден (установи ffmpeg или задай FFMPEG_PATH)";
            return false;
        }

        recWidth = width;
        recHeight = height;

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File recDir = new File(gameDirFile(), "recordings");
        if (!recDir.mkdirs() && !recDir.isDirectory()) {
            lastError = "не удалось создать папку recordings";
            return false;
        }
        File outFile = new File(recDir, "rec_" + ts + ".mp4");

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
            QUEUE.clear();
            FREE.clear();
            lastFrameNs = 0;
            ffmpeg = new ProcessBuilder(cmd).start();
            ffmpegIn = ffmpeg.getOutputStream();
            startTimeMs = System.currentTimeMillis();

            // Важно: recording должен быть true ДО старта writer-потока,
            // иначе поток сразу завершится, а кадры будут копиться в очереди.
            recording = true;

            writerThread = new Thread(() -> {
                try {
                    while (true) {
                        byte[] frame = QUEUE.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (frame == null) {
                            if (!recording) break;
                            continue;
                        }
                        ffmpegIn.write(frame);
                        FREE.offer(frame);
                    }
                    ffmpegIn.flush();
                    ffmpegIn.close();
                } catch (Exception ignored) {
                }
            }, "OptiRec-writer");
            writerThread.setDaemon(true);
            writerThread.start();
            lastError = "";
            return true;
        } catch (Exception e) {
            recording = false;
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            cleanupProcess();
            return false;
        }
    }

    public static synchronized void stop() {
        if (!recording) return;
        recording = false;

        try {
            if (writerThread != null) {
                writerThread.join(3000);
            }
        } catch (InterruptedException ignored) {
        }

        try {
            if (ffmpegIn != null) {
                ffmpegIn.close();
                ffmpegIn = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (ffmpeg != null && !ffmpeg.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                ffmpeg.destroy();
                if (!ffmpeg.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    ffmpeg.destroyForcibly();
                }
            }
        } catch (Exception ignored) {
        } finally {
            ffmpeg = null;
            writerThread = null;
        }

        QUEUE.clear();
        FREE.clear();
    }

    private static void cleanupProcess() {
        try {
            if (ffmpegIn != null) {
                ffmpegIn.close();
                ffmpegIn = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (ffmpeg != null) {
                ffmpeg.destroyForcibly();
                ffmpeg = null;
            }
        } catch (Exception ignored) {
        }
        QUEUE.clear();
        FREE.clear();
    }

    public static void grabFrame(ByteBuffer buffer, int width, int height) {
        if (!recording) return;
        if (width != recWidth || height != recHeight) return;
        // Кадровая экономия: не грузим CPU каждый кадр — только 24 к/с
        long now = System.nanoTime();
        if (now - lastFrameNs < FRAME_INTERVAL_NS) return;
        if (QUEUE.remainingCapacity() == 0) return; // не копим, если FFmpeg не успевает

        try {
            int size = width * height * 4;
            byte[] bytes = FREE.poll();
            if (bytes == null || bytes.length != size) {
                bytes = new byte[size];
            }
            ByteBuffer dup = buffer.duplicate();
            Buffer d = dup;
            d.rewind();
            dup.get(bytes);
            if (!QUEUE.offer(bytes)) {
                FREE.offer(bytes);
                return;
            }
            lastFrameNs = now;
        } catch (Exception ignored) {
        }
    }

    // Вызывается ДО дорогого glReadPixels, чтобы не читать кадр, который всё равно отбросим
    public static boolean shouldCapture() {
        if (!recording) return false;
        return System.nanoTime() - lastFrameNs >= FRAME_INTERVAL_NS && QUEUE.remainingCapacity() > 0;
    }

    public static int getFrameWidth() {
        return recWidth;
    }

    public static int getFrameHeight() {
        return recHeight;
    }

    public static long getElapsedMs() {
        return recording ? System.currentTimeMillis() - startTimeMs : 0;
    }

    public static String getLastError() {
        return lastError;
    }

    private static File gameDirFile() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    public static boolean findFfmpegReport() {
        return findFfmpegPath() != null;
    }

    private static String findFfmpegPath() {
        String cached = ffmpegPathCache;
        if (cached != null) return cached;

        String path = System.getenv("FFMPEG_PATH");
        if (path != null && new File(path).canExecute()) {
            ffmpegPathCache = path;
            return path;
        }

        String[] candidates = {"ffmpeg", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg"};
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "-version").start();
                if (p.waitFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS) && p.exitValue() == 0) {
                    ffmpegPathCache = c;
                    return c;
                }
                p.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}

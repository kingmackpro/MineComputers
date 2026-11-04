package net.minecomputers.client;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecomputers.MineComputers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;

/**
 * Plays the picture of a video file on a Screen. A background thread decodes frames with FFmpeg (scaled down so
 * the longest side is at most MAX_SIDE pixels) and hands each one over at the right time; the renderer calls
 * {@link #update()} on the render thread to copy the newest frame into a texture. The sound is NOT played here:
 * the Speaker plays the sound track of the file (see MediaSoundInstance).
 */
final class VideoPlayer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Longest side of the decoded picture. A 4x4 Screen shows this across 4 blocks. */
    private static final int MAX_SIDE = 1024;
    private static int counter;

    private final Path file;
    private final boolean loop;
    /** The moment (System.nanoTime) at which the playback was at position 0: the shared clock, see MediaClient. */
    private final long originNanos;
    private final Thread thread;
    private volatile boolean closed;

    private final Object lock = new Object();
    private int[] frame;
    private int frameW;
    private int frameH;
    private int version;
    private int shownVersion;
    private volatile String error;

    private ResourceLocation location;
    private NativeImage image;
    private DynamicTexture texture;

    VideoPlayer(Path file, boolean loop, long originNanos) {
        this.file = file;
        this.loop = loop;
        this.originNanos = originNanos;
        this.thread = new Thread(this::run, "MineComputers-video");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private static int even(double value) {
        return Math.max(2, ((int) Math.round(value)) & ~1);
    }

    private void run() {
        try {
            int width;
            int height;
            try (FFmpegFrameGrabber probe = new FFmpegFrameGrabber(file.toFile())) {
                probe.start();
                width = probe.getImageWidth();
                height = probe.getImageHeight();
                probe.stop();
            }
            if (width <= 0 || height <= 0) {
                error = "This file has no picture";
                return;
            }
            double scale = Math.min(1.0, MAX_SIDE / (double) Math.max(width, height));
            int targetW = even(width * scale);
            int targetH = even(height * scale);

            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.toFile())) {
                grabber.setImageWidth(targetW);
                grabber.setImageHeight(targetH);
                grabber.setPixelFormat(avutil.AV_PIX_FMT_BGRA);
                grabber.start();
                // The picture at time t of the file is shown when the shared clock says t. origin = the moment the
                // current pass of the file was at 0. Joining late (or after a long download) seeks into the file.
                long lengthMicros = grabber.getLengthInTime();
                long origin = originNanos;
                long elapsedMicros = Math.max(0L, (System.nanoTime() - origin) / 1000L);
                if (lengthMicros > 0) {
                    if (loop) {
                        long cycles = elapsedMicros / lengthMicros;
                        origin += cycles * lengthMicros * 1000L;
                        elapsedMicros -= cycles * lengthMicros;
                    } else if (elapsedMicros >= lengthMicros) {
                        // Already over: show the last picture.
                        elapsedMicros = Math.max(0L, lengthMicros - 200_000L);
                        origin = System.nanoTime() - elapsedMicros * 1000L;
                    }
                }
                if (elapsedMicros > 100_000L) {
                    grabber.setTimestamp(elapsedMicros);
                }
                boolean gotFrame = false;
                long lastStamp = 0;
                long lastPublish = 0;
                while (!closed) {
                    Frame next = grabber.grabImage();
                    if (next == null || next.image == null) {
                        if (loop && gotFrame) {
                            origin += Math.max(lengthMicros, lastStamp + 40_000L) * 1000L;
                            grabber.setTimestamp(0);
                            gotFrame = false;
                            continue;
                        }
                        break; // the last frame stays on the screen
                    }
                    gotFrame = true;
                    lastStamp = next.timestamp;
                    long dueNanos = origin + next.timestamp * 1000L;
                    long wait = dueNanos - System.nanoTime();
                    if (wait > 0) {
                        Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                    } else if (wait < -100_000_000L && System.nanoTime() - lastPublish < 250_000_000L) {
                        continue; // more than 100 ms late: skip this picture to catch up (one is still shown every 250 ms)
                    }
                    publish(next);
                    lastPublish = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            error = String.valueOf(t.getMessage());
            LOGGER.error("MineComputers: video playback failed", t);
        }
    }

    /** Converts a BGRA frame to the ABGR ints NativeImage wants and hands it to the render thread. */
    private void publish(Frame next) {
        int w = next.imageWidth;
        int h = next.imageHeight;
        ByteBuffer bytes = ((ByteBuffer) next.image[0]).duplicate();
        int stride = next.imageStride;
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int base = y * stride;
            for (int x = 0; x < w; x++) {
                int i = base + x * 4;
                int b = bytes.get(i) & 0xFF;
                int g = bytes.get(i + 1) & 0xFF;
                int r = bytes.get(i + 2) & 0xFF;
                pixels[y * w + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }
        synchronized (lock) {
            frame = pixels;
            frameW = w;
            frameH = h;
            version++;
        }
    }

    /** Render thread: copies a new frame into the texture. Returns true if there is a picture to draw. */
    boolean update() {
        int[] data;
        int w;
        int h;
        int v;
        synchronized (lock) {
            data = frame;
            w = frameW;
            h = frameH;
            v = version;
        }
        if (data == null) {
            return false;
        }
        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            releaseTexture();
            location = ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "screen_video/" + (counter++));
            image = new NativeImage(w, h, false);
            texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            shownVersion = 0;
        }
        if (v != shownVersion) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    image.setPixelRGBA(x, y, data[y * w + x]);
                }
            }
            texture.upload();
            shownVersion = v;
        }
        return true;
    }

    ResourceLocation location() {
        return location;
    }

    /** Width divided by height of the picture. */
    float aspect() {
        return image == null ? 1f : (float) image.getWidth() / image.getHeight();
    }

    String error() {
        return error;
    }

    private void releaseTexture() {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            location = null;
            image = null;
            texture = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
        releaseTexture();
    }
}

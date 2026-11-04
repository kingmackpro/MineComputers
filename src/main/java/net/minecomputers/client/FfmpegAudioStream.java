package net.minecomputers.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

import javax.sound.sampled.AudioFormat;

import net.minecraft.client.sounds.AudioStream;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.lwjgl.BufferUtils;

/**
 * Decodes the audio of any file FFmpeg understands (WAV, OGG, FLAC, M4A, Opus, and the sound track of MP4, WebM
 * and MKV) into mono 16-bit 44.1 kHz PCM for Minecraft's sound engine. With loop = true it starts over at the end.
 */
final class FfmpegAudioStream implements AudioStream {
    private static final int SAMPLE_RATE = 44100;

    private final FFmpegFrameGrabber grabber;
    private final boolean loop;
    private final AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private byte[] pending = new byte[0];
    private int pendingPos;
    private boolean finished;
    private boolean gotSamplesThisPass;

    /**
     * startMs = where the shared clock is: the stream starts that far into the file (modulo its length when looping;
     * past the end of a non-looping file it is silent).
     */
    FfmpegAudioStream(Path file, boolean loop, long startMs) throws IOException {
        this.loop = loop;
        this.grabber = new FFmpegFrameGrabber(file.toFile());
        grabber.setAudioChannels(1);
        grabber.setSampleRate(SAMPLE_RATE);
        grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
        grabber.start();
        long lengthMs = grabber.getLengthInTime() / 1000L;
        long seekMs = 0;
        if (lengthMs > 0 && startMs > 0) {
            if (loop) {
                seekMs = startMs % lengthMs;
            } else if (startMs >= lengthMs) {
                finished = true;
            } else {
                seekMs = startMs;
            }
        }
        if (seekMs > 0) {
            grabber.setTimestamp(seekMs * 1000L);
        }
    }

    /** Decodes the next block of samples into `pending`. Returns false when the audio has ended. */
    private boolean next() throws IOException {
        while (!finished) {
            Frame frame = grabber.grabSamples();
            if (frame == null) {
                if (loop && gotSamplesThisPass) {
                    grabber.setTimestamp(0);
                    gotSamplesThisPass = false;
                    continue;
                }
                finished = true;
                return false;
            }
            if (frame.samples == null || frame.samples.length == 0 || !(frame.samples[0] instanceof ShortBuffer samples)) {
                continue;
            }
            ShortBuffer view = samples.duplicate();
            byte[] out = new byte[view.remaining() * 2];
            int i = 0;
            while (view.hasRemaining()) {
                short value = view.get();
                out[i++] = (byte) (value & 0xFF);
                out[i++] = (byte) ((value >> 8) & 0xFF);
            }
            gotSamplesThisPass = true;
            pending = out;
            pendingPos = 0;
            return true;
        }
        return false;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer out = BufferUtils.createByteBuffer(size);
        while (out.position() < size) {
            if (pendingPos >= pending.length) {
                if (!next()) {
                    break;
                }
                continue;
            }
            int count = Math.min(size - out.position(), pending.length - pendingPos);
            out.put(pending, pendingPos, count);
            pendingPos += count;
        }
        out.flip();
        return out;
    }

    @Override
    public void close() {
        finished = true;
        try {
            grabber.close();
        } catch (Exception ignored) {
            // Nothing more to do.
        }
    }
}

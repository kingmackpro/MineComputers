package net.minecomputers.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/**
 * Decodes an MP3 (JLayer, pure Java) frame by frame into the mono 16-bit PCM that Minecraft's sound engine
 * plays. Mono, because OpenAL only makes mono sources quieter with distance; stereo files are mixed down.
 * With loop = true it starts over when the file ends.
 */
final class Mp3Stream implements AudioStream {
    private final byte[] data;
    private final boolean loop;
    private final AudioFormat format;
    private Bitstream bitstream;
    private Decoder decoder;
    private byte[] pending = new byte[0];
    private int pendingPos;
    private boolean finished;
    private int framesThisPass;

    Mp3Stream(byte[] data, boolean loop) throws IOException {
        this.data = data;
        this.loop = loop;
        open();
        if (!decodeFrame()) {
            throw new IOException("This MP3 has no audio frames");
        }
        this.format = new AudioFormat(decoder.getOutputFrequency(), 16, 1, true, false);
    }

    private void open() {
        bitstream = new Bitstream(new ByteArrayInputStream(data));
        decoder = new Decoder();
        framesThisPass = 0;
    }

    /** Decodes the next frame into `pending`. Returns false when the audio has ended. */
    private boolean decodeFrame() throws IOException {
        if (finished) {
            return false;
        }
        try {
            while (true) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    bitstream.close();
                    if (loop && framesThisPass > 0) {
                        open();
                        continue;
                    }
                    finished = true;
                    return false;
                }
                SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                int channels = Math.max(1, decoder.getOutputChannels());
                short[] buffer = samples.getBuffer();
                int frames = samples.getBufferLength() / channels;
                byte[] out = new byte[frames * 2];
                for (int i = 0; i < frames; i++) {
                    int sum = 0;
                    for (int c = 0; c < channels; c++) {
                        sum += buffer[i * channels + c];
                    }
                    short mono = (short) (sum / channels);
                    out[i * 2] = (byte) (mono & 0xFF);
                    out[i * 2 + 1] = (byte) ((mono >> 8) & 0xFF);
                }
                bitstream.closeFrame();
                framesThisPass++;
                pending = out;
                pendingPos = 0;
                return true;
            }
        } catch (JavaLayerException | RuntimeException e) {
            finished = true;
            throw new IOException("MP3 decoding failed: " + e.getMessage(), e);
        }
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
                if (!decodeFrame()) {
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
            bitstream.close();
        } catch (JavaLayerException ignored) {
            // Nothing more to do.
        }
    }
}

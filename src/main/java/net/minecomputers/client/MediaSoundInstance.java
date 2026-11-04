package net.minecomputers.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import net.minecomputers.MineComputers;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A sound at a Speaker whose audio is our own decoded stream instead of an .ogg from a resource pack. It goes
 * through Minecraft's normal sound engine, so the volume sliders, the distance fade and pausing all work.
 * The "minecomputers:media" entry in sounds.json only exists so the engine accepts the sound; its file is never read.
 * MP3 is decoded with JLayer, everything else (and the sound of videos) with FFmpeg.
 */
final class MediaSoundInstance extends AbstractSoundInstance {
    static final ResourceLocation EVENT = ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "media");

    private final Path file;
    private final boolean loopAudio;
    /** The moment (System.nanoTime) at which the playback was at position 0: the shared clock, see MediaClient. */
    private final long originNanos;

    MediaSoundInstance(BlockPos pos, Path file, float volume, boolean loopAudio, long originNanos) {
        super(EVENT, SoundSource.RECORDS, RandomSource.create());
        this.file = file;
        this.loopAudio = loopAudio;
        this.originNanos = originNanos;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.volume = volume;
        this.looping = false; // looping is done inside the stream
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        try {
            // The sound engine asks for the stream a moment after the sound was queued: start where the shared
            // clock is NOW, not where it was when we queued.
            long startMs = Math.max(0L, (System.nanoTime() - originNanos) / 1_000_000L);
            if (looksLikeMp3(file)) {
                return CompletableFuture.completedFuture(new Mp3Stream(Files.readAllBytes(file), loopAudio, startMs));
            }
            return CompletableFuture.completedFuture(new FfmpegAudioStream(file, loopAudio, startMs));
        } catch (IOException | RuntimeException | LinkageError e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** An MP3 starts with an ID3 tag or with a frame sync (0xFF 0xE0 or higher). */
    private static boolean looksLikeMp3(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(3);
            if (head.length < 3) {
                return false;
            }
            boolean id3 = head[0] == 'I' && head[1] == 'D' && head[2] == '3';
            boolean sync = (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;
            return id3 || sync;
        }
    }
}

package net.minecomputers.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.mojang.logging.LogUtils;
import net.minecomputers.network.MediaData;
import net.minecomputers.network.MediaPlay;
import net.minecomputers.network.MediaRequest;
import net.minecomputers.network.MediaRules;
import net.minecomputers.network.MediaStop;
import net.minecomputers.network.VideoPlay;
import net.minecomputers.network.VideoStop;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Client side of Speaker and Screen media: keeps downloaded files in a cache folder (named by their SHA-256),
 * asks the server for files it does not have, plays sound as MediaSoundInstances and video as VideoPlayers.
 * Everything here runs on the client main thread, except reading and writing files (the IO pool).
 */
final class MediaClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{64}");

    /** What to start once the file is here: sound at a Speaker, or video at a Screen. */
    private record Request(boolean video, BlockPos pos, float volume, boolean loop) {
    }

    private static final class Download {
        final byte[] data;
        int next;
        int filled;

        Download(int total) {
            this.data = new byte[total];
        }
    }

    private static final Map<BlockPos, SoundInstance> PLAYING = new HashMap<>();
    private static final Map<BlockPos, VideoPlayer> VIDEOS = new HashMap<>();
    private static final Map<String, List<Request>> WAITING = new HashMap<>();
    private static final Map<String, Download> DOWNLOADS = new HashMap<>();

    /** The video playing on the Screen whose origin block is here, or null. */
    static VideoPlayer video(BlockPos origin) {
        return VIDEOS.get(origin);
    }

    static void onPlay(MediaPlay play) {
        if (!ID.matcher(play.id()).matches()) {
            return;
        }
        stopSound(play.pos());
        float volume = Math.max(0f, Math.min(2f, play.volume()));
        want(play.id(), new Request(false, play.pos().immutable(), volume, play.loop()));
    }

    static void onVideoPlay(VideoPlay play) {
        if (!ID.matcher(play.id()).matches()) {
            return;
        }
        stopVideo(play.pos());
        want(play.id(), new Request(true, play.pos().immutable(), 0f, play.loop()));
    }

    private static void want(String id, Request request) {
        Path file = cacheFile(id);
        if (Files.isRegularFile(file)) {
            start(request, file);
            return;
        }
        WAITING.computeIfAbsent(id, key -> new ArrayList<>()).add(request);
        if (!DOWNLOADS.containsKey(id)) {
            DOWNLOADS.put(id, new Download(0));
            PacketDistributor.sendToServer(new MediaRequest(id));
        }
    }

    static void onStop(MediaStop stop) {
        stopSound(stop.pos());
        forget(stop.pos(), false);
    }

    static void onVideoStop(VideoStop stop) {
        stopVideo(stop.pos());
        forget(stop.pos(), true);
    }

    private static void forget(BlockPos pos, boolean video) {
        for (List<Request> list : WAITING.values()) {
            list.removeIf(request -> request.pos().equals(pos) && request.video() == video);
        }
    }

    static void onData(MediaData data) {
        String id = data.id();
        if (!ID.matcher(id).matches() || !DOWNLOADS.containsKey(id)) {
            return; // we did not ask for this
        }
        if (data.total() <= 0 || data.total() > MediaRules.MAX_VIDEO_BYTES) {
            DOWNLOADS.remove(id);
            WAITING.remove(id);
            return;
        }
        Download download = DOWNLOADS.get(id);
        if (data.index() == 0) {
            download = new Download(data.total());
            DOWNLOADS.put(id, download);
        }
        byte[] chunk = data.data();
        if (data.total() != download.data.length || data.index() != download.next
                || download.filled + chunk.length > download.data.length) {
            DOWNLOADS.remove(id);
            WAITING.remove(id);
            return;
        }
        System.arraycopy(chunk, 0, download.data, download.filled, chunk.length);
        download.filled += chunk.length;
        download.next++;
        if (download.filled == download.data.length) {
            DOWNLOADS.remove(id);
            finish(id, download.data);
        }
    }

    /** Checks the hash, writes the cache file, then starts everything that was waiting for this file. */
    private static void finish(String id, byte[] bytes) {
        Util.ioPool().execute(() -> {
            Path file = cacheFile(id);
            boolean good = false;
            try {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (hash.equals(id)) {
                    Files.createDirectories(file.getParent());
                    Files.write(file, bytes);
                    good = true;
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                LOGGER.warn("MineComputers: could not cache media file {}", id, e);
            }
            boolean ok = good;
            Minecraft.getInstance().execute(() -> {
                List<Request> waiting = WAITING.remove(id);
                if (!ok || waiting == null) {
                    return;
                }
                for (Request request : waiting) {
                    start(request, file);
                }
            });
        });
    }

    private static void start(Request request, Path file) {
        if (request.video()) {
            stopVideo(request.pos());
            VIDEOS.put(request.pos(), new VideoPlayer(file, request.loop()));
        } else {
            stopSound(request.pos());
            MediaSoundInstance instance = new MediaSoundInstance(request.pos(), file, request.volume(), request.loop());
            PLAYING.put(request.pos(), instance);
            Minecraft.getInstance().getSoundManager().play(instance);
        }
    }

    private static void stopSound(BlockPos pos) {
        SoundInstance old = PLAYING.remove(pos);
        if (old != null) {
            Minecraft.getInstance().getSoundManager().stop(old);
        }
    }

    private static void stopVideo(BlockPos pos) {
        VideoPlayer old = VIDEOS.remove(pos);
        if (old != null) {
            old.close();
        }
    }

    private static Path cacheFile(String id) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("minecomputers-cache").resolve(id + ".bin");
    }

    private MediaClient() {
    }
}

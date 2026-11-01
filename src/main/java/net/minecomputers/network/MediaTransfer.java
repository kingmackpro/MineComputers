package net.minecomputers.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * Server side of playing stored media for players: tells the players near a Speaker to play a file, and sends
 * the file to a client that does not have it yet.
 *
 * Safety: a client may only request files the server announced to it with a MediaPlay (so it cannot ask for
 * arbitrary files of the world), one download of the same file at a time, ids must be 64 hex characters.
 * The client checks the SHA-256 of what it received against the id.
 */
public final class MediaTransfer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{64}");
    /** Players this close to a Speaker get the play request (and download the file if they lack it). */
    private static final double HEAR_RADIUS = 48.0;
    private static final double STOP_RADIUS = 128.0;
    private static final int MAX_ANNOUNCED_PER_PLAYER = 256;

    private static final Map<UUID, Set<String>> ANNOUNCED = new HashMap<>();
    private static final Set<String> IN_FLIGHT = new HashSet<>();

    public static void play(ServerLevel level, BlockPos pos, String id, float volume, boolean loop) {
        MediaPlay packet = new MediaPlay(pos, id, volume, loop);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= HEAR_RADIUS * HEAR_RADIUS) {
                Set<String> announced = ANNOUNCED.computeIfAbsent(player.getUUID(), key -> new HashSet<>());
                if (announced.size() >= MAX_ANNOUNCED_PER_PLAYER) {
                    announced.clear();
                }
                announced.add(id);
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /** Shows a stored video on the Screen whose origin block is at pos, for the players near it. */
    public static void playVideo(ServerLevel level, BlockPos pos, String id, boolean loop) {
        VideoPlay packet = new VideoPlay(pos, id, loop);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= HEAR_RADIUS * HEAR_RADIUS) {
                Set<String> announced = ANNOUNCED.computeIfAbsent(player.getUUID(), key -> new HashSet<>());
                if (announced.size() >= MAX_ANNOUNCED_PER_PLAYER) {
                    announced.clear();
                }
                announced.add(id);
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public static void stopVideo(ServerLevel level, BlockPos pos) {
        VideoStop packet = new VideoStop(pos);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= STOP_RADIUS * STOP_RADIUS) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public static void stop(ServerLevel level, BlockPos pos) {
        MediaStop packet = new MediaStop(pos);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= STOP_RADIUS * STOP_RADIUS) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /** A client asks for a file it was told to play but does not have. */
    public static void handleRequest(MediaRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        String id = request.id();
        UUID uuid = player.getUUID();
        Set<String> announced = ANNOUNCED.get(uuid);
        String flight = uuid + ":" + id;
        if (!ID.matcher(id).matches() || announced == null || !announced.contains(id) || !IN_FLIGHT.add(flight)) {
            return;
        }
        MinecraftServer server = player.server;
        Path file = MediaStore.directory(server).resolve(id + ".bin");
        Util.ioPool().execute(() -> {
            byte[] data = null;
            try {
                data = Files.readAllBytes(file);
            } catch (IOException e) {
                LOGGER.warn("MineComputers: could not read media file {}", id, e);
            }
            byte[] bytes = data;
            server.execute(() -> {
                IN_FLIGHT.remove(flight);
                if (bytes == null || bytes.length == 0 || player.hasDisconnected()) {
                    return;
                }
                int chunks = (bytes.length + MediaRules.CHUNK_BYTES - 1) / MediaRules.CHUNK_BYTES;
                for (int i = 0; i < chunks; i++) {
                    int from = i * MediaRules.CHUNK_BYTES;
                    int to = Math.min(bytes.length, from + MediaRules.CHUNK_BYTES);
                    PacketDistributor.sendToPlayer(player, new MediaData(id, bytes.length, i, Arrays.copyOfRange(bytes, from, to)));
                }
            });
        });
    }

    public static void onLogout(UUID player) {
        ANNOUNCED.remove(player);
        IN_FLIGHT.removeIf(entry -> entry.startsWith(player + ":"));
    }

    private MediaTransfer() {
    }
}

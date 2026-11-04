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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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
    /** A player further than this from a playing Speaker or Screen is told to stop it (and gets it again when he comes back). */
    private static final double LEAVE_RADIUS = 64.0;
    private static final double STOP_RADIUS = 128.0;
    private static final int MAX_ANNOUNCED_PER_PLAYER = 256;

    /** A Speaker (sound) or a Screen (video) that is playing something. */
    private record Key(ResourceKey<Level> dimension, BlockPos pos, boolean video) {
    }

    /** What it plays and when it started, in server game ticks: the shared clock. */
    private record Active(String id, float volume, boolean loop, long startTick) {
    }

    private static final Map<Key, Active> ACTIVE = new HashMap<>();
    /** Per player: which active playbacks he has been sent (so a player who comes into range later gets them once). */
    private static final Map<UUID, Set<Key>> TOLD = new HashMap<>();
    private static final Map<UUID, Set<String>> ANNOUNCED = new HashMap<>();
    private static final Set<String> IN_FLIGHT = new HashSet<>();

    public static void play(ServerLevel level, BlockPos pos, String id, float volume, boolean loop) {
        begin(level, new Key(level.dimension(), pos.immutable(), false), new Active(id, volume, loop, level.getGameTime()));
    }

    /** Shows a stored video on the Screen whose origin block is at pos, for the players near it. */
    public static void playVideo(ServerLevel level, BlockPos pos, String id, boolean loop) {
        begin(level, new Key(level.dimension(), pos.immutable(), true), new Active(id, 0f, loop, level.getGameTime()));
    }

    /** Registers the playback (start time = now) and sends it to everyone in range. A restart replaces the old one. */
    private static void begin(ServerLevel level, Key key, Active active) {
        ACTIVE.put(key, active);
        for (Set<Key> told : TOLD.values()) {
            told.remove(key);
        }
        sync(level);
    }

    /**
     * Once a second (and when something starts): every player who came within HEAR_RADIUS of a playing Speaker or
     * Screen and has not been told yet gets it, with the position the playback has reached by now, so he joins in
     * at the right moment. A player who went further than LEAVE_RADIUS gets a stop (frees his decoder) and is told
     * again when he comes back.
     */
    public static void sync(ServerLevel level) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Map.Entry<Key, Active> entry : ACTIVE.entrySet()) {
            Key key = entry.getKey();
            if (!key.dimension().equals(level.dimension())) {
                continue;
            }
            Active active = entry.getValue();
            Vec3 center = Vec3.atCenterOf(key.pos());
            for (ServerPlayer player : level.players()) {
                Set<Key> told = TOLD.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
                double distance = player.distanceToSqr(center);
                boolean has = told.contains(key);
                if (!has && distance <= HEAR_RADIUS * HEAR_RADIUS) {
                    told.add(key);
                    Set<String> announced = ANNOUNCED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
                    if (announced.size() >= MAX_ANNOUNCED_PER_PLAYER) {
                        announced.clear();
                    }
                    announced.add(active.id());
                    long positionMs = Math.max(0L, (level.getGameTime() - active.startTick()) * 50L);
                    if (key.video()) {
                        PacketDistributor.sendToPlayer(player, new VideoPlay(key.pos(), active.id(), active.loop(), positionMs));
                    } else {
                        PacketDistributor.sendToPlayer(player,
                                new MediaPlay(key.pos(), active.id(), active.volume(), active.loop(), positionMs));
                    }
                } else if (has && distance > LEAVE_RADIUS * LEAVE_RADIUS) {
                    told.remove(key);
                    if (key.video()) {
                        PacketDistributor.sendToPlayer(player, new VideoStop(key.pos()));
                    } else {
                        PacketDistributor.sendToPlayer(player, new MediaStop(key.pos()));
                    }
                }
            }
        }
    }

    private static void forget(Key key) {
        ACTIVE.remove(key);
        for (Set<Key> told : TOLD.values()) {
            told.remove(key);
        }
    }

    public static void stopVideo(ServerLevel level, BlockPos pos) {
        forget(new Key(level.dimension(), pos.immutable(), true));
        VideoStop packet = new VideoStop(pos);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= STOP_RADIUS * STOP_RADIUS) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public static void stop(ServerLevel level, BlockPos pos) {
        forget(new Key(level.dimension(), pos.immutable(), false));
        MediaStop packet = new MediaStop(pos);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= STOP_RADIUS * STOP_RADIUS) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /** Runs every game tick of every level; the resend check itself runs once a second. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
            sync(level);
        }
    }

    /** The world is closing: forget everything (in single player the next world must not inherit it). */
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        TOLD.clear();
        ANNOUNCED.clear();
        IN_FLIGHT.clear();
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
        TOLD.remove(player);
        IN_FLIGHT.removeIf(entry -> entry.startsWith(player + ":"));
    }

    private MediaTransfer() {
    }
}

package net.minecomputers.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecomputers.block.entity.BrainBlockEntity;
import net.minecomputers.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The hacking mini game, server side (SPEC section 8). The server owns everything: it picks the zone, runs the needle
 * clock (server ticks) and judges the key press with the time the press ARRIVES, so a client can not cheat; the client
 * only draws. A small tolerance covers network delay.
 * Rules: needs a Cipher in the inventory; one hacker per system at a time; rounds = 3 per started 200 non-blank lines
 * (all files), min 3, max 30; needle speed 1 (one turn in 2 s) + 0.1 per round, max 3; zone 12% to 30% of the circle.
 * A miss breaks one Cipher and keeps the progress for 24 hours (real time, per hacker and system, reset when the owner
 * locks the system again). Closing the screen or walking away stops the hack without breaking the Cipher. While a hack
 * runs, the system's script is paused (the owner notices). Success: the hacker gets access (kept Cipher).
 * TODO: the numbers are constants here, SPEC wants them in the config.
 */
public final class HackManager {
    private static final int MIN_ROUNDS = 3;
    private static final int MAX_ROUNDS = 30;
    private static final double LINES_PER_STEP = 200.0;
    private static final double BASE_PERIOD_MS = 2000.0;
    private static final double SPEED_STEP = 0.1;
    private static final double SPEED_MAX = 3.0;
    private static final double ZONE_MIN = 0.12;
    private static final double ZONE_MAX = 0.30;
    /** Time between the round info and the needle starting, so the player can see it. */
    private static final int START_DELAY_TICKS = 30;
    /** After a miss the same player has to wait before he can try on the same system again. */
    private static final int COOLDOWN_TICKS = 100;
    /** Network delay allowance, in turns of the circle, on both sides of the zone. */
    private static final double TOLERANCE = 0.03;
    private static final long PROGRESS_MS = 24L * 60L * 60L * 1000L;

    private static final class Session {
        final UUID player;
        final GlobalPos brain;
        final BlockPos laptop;
        final int rounds;
        int round;
        int periodMs;
        double zoneStart;
        double zoneSize;
        long startTick;
        long deadlineTick;

        Session(UUID player, GlobalPos brain, BlockPos laptop, int rounds, int round) {
            this.player = player;
            this.brain = brain;
            this.laptop = laptop;
            this.rounds = rounds;
            this.round = round;
        }
    }

    private record Progress(int round, long lockStamp, long expiresMs) {
    }

    private static final Map<GlobalPos, Session> BY_BRAIN = new HashMap<>();
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
    private static final Map<String, Progress> PROGRESS = new HashMap<>();
    private static final Map<String, Long> COOLDOWN = new HashMap<>();

    /** True while somebody hacks this Brain: its script is paused. */
    public static boolean isHacked(GlobalPos brain) {
        return BY_BRAIN.containsKey(brain);
    }

    private static String key(UUID player, GlobalPos brain) {
        return player + "|" + brain.dimension().location() + "|" + brain.pos().asLong();
    }

    /** Rounds for this system: 3 per started 200 non-blank lines of all its files, 3 to 30. */
    public static int roundsFor(BrainBlockEntity brain) {
        int lines = 0;
        for (String content : brain.files().values()) {
            for (String line : content.split("\n")) {
                if (!line.isBlank()) {
                    lines++;
                }
            }
        }
        int rounds = 3 * (int) Math.ceil(lines / LINES_PER_STEP);
        return Math.max(MIN_ROUNDS, Math.min(MAX_ROUNDS, rounds));
    }

    private static int savedRound(UUID player, GlobalPos brainKey, BrainBlockEntity brain, int rounds) {
        String k = key(player, brainKey);
        Progress progress = PROGRESS.get(k);
        if (progress == null) {
            return 0;
        }
        if (progress.expiresMs() < System.currentTimeMillis() || progress.lockStamp() != brain.lockStamp()) {
            PROGRESS.remove(k);
            return 0;
        }
        return Math.max(0, Math.min(progress.round(), rounds - 1));
    }

    private static void send(ServerPlayer player, BlockPos laptop, int phase, int round, int rounds,
                             Session s, int delayMs, String text) {
        PacketDistributor.sendToPlayer(player, new HackState(laptop, phase, round, rounds,
                s == null ? 0f : (float) s.zoneStart, s == null ? 0f : (float) s.zoneSize,
                s == null ? 0 : s.periodMs, delayMs, text));
    }

    /** A locked-out player opened the laptop: tell his client to show the hack screen. */
    public static void offer(ServerPlayer player, Level level, BlockPos laptop, BrainBlockEntity brain) {
        GlobalPos key = GlobalPos.of(level.dimension(), brain.getBlockPos());
        int rounds = roundsFor(brain);
        send(player, laptop, HackState.OFFER, savedRound(player.getUUID(), key, brain, rounds), rounds, null, 0,
                "This system is locked by a Cipher.");
    }

    public static void handle(HackAction action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Level level = player.level();
        BlockPos pos = action.laptop();
        if (action.action() == HackAction.ABORT) {
            end(player.getUUID());
            return;
        }
        if (!ServerHandlers.laptopUsable(player, level, pos)) {
            end(player.getUUID());
            return;
        }
        BrainBlockEntity brain = NetworkScan.findBrain(level, pos);
        if (brain == null) {
            return;
        }
        switch (action.action()) {
            case HackAction.BEGIN -> begin(player, level, pos, brain);
            case HackAction.PRESS -> press(player, level);
            default -> {
            }
        }
    }

    private static void begin(ServerPlayer player, Level level, BlockPos laptop, BrainBlockEntity brain) {
        UUID id = player.getUUID();
        int rounds = roundsFor(brain);
        GlobalPos key = GlobalPos.of(level.dimension(), brain.getBlockPos());
        if (!brain.isLocked() || CipherLock.canAccess(player, brain)) {
            return; // nothing to hack
        }
        if (BY_PLAYER.containsKey(id)) {
            return;
        }
        int saved = savedRound(id, key, brain, rounds);
        if (BY_BRAIN.containsKey(key)) {
            send(player, laptop, HackState.FAIL, saved, rounds, null, 0, "Someone else is hacking this system right now.");
            return;
        }
        Long until = COOLDOWN.get(key(id, key));
        if (until != null && level.getGameTime() < until) {
            send(player, laptop, HackState.FAIL, saved, rounds, null, 0, "Wait a moment before you try again.");
            return;
        }
        if (findCipher(player).isEmpty()) {
            send(player, laptop, HackState.FAIL, saved, rounds, null, 0, "You need a Cipher to hack a system.");
            return;
        }
        Session session = new Session(id, key, laptop, rounds, saved);
        BY_PLAYER.put(id, session);
        BY_BRAIN.put(key, session);
        startRound(player, level, session);
        if (brain.owner() != null) {
            ServerPlayer owner = player.server.getPlayerList().getPlayer(brain.owner());
            if (owner != null) {
                BlockPos p = brain.getBlockPos();
                owner.displayClientMessage(Component.literal("Someone is hacking your system at "
                        + p.getX() + ", " + p.getY() + ", " + p.getZ() + "!"), false);
            }
        }
    }

    private static void startRound(ServerPlayer player, Level level, Session s) {
        double speed = Math.min(SPEED_MAX, 1.0 + SPEED_STEP * s.round);
        s.periodMs = (int) Math.round(BASE_PERIOD_MS / speed);
        RandomSource random = level.random;
        s.zoneSize = ZONE_MIN + random.nextDouble() * (ZONE_MAX - ZONE_MIN);
        s.zoneStart = random.nextDouble();
        s.startTick = level.getGameTime() + START_DELAY_TICKS;
        // two full turns after the start and the player must have pressed, else it counts as a miss
        s.deadlineTick = s.startTick + (long) Math.ceil(2.0 * s.periodMs / 50.0) + 6;
        send(player, s.laptop, HackState.ROUND, s.round, s.rounds, s, START_DELAY_TICKS * 50, "");
    }

    private static void press(ServerPlayer player, Level level) {
        Session s = BY_PLAYER.get(player.getUUID());
        if (s == null) {
            return;
        }
        long now = level.getGameTime();
        if (now < s.startTick) {
            return; // pressed before the needle started: ignored
        }
        double elapsedMs = (now - s.startTick) * 50.0;
        double angle = (elapsedMs % s.periodMs) / s.periodMs;
        double d = ((angle - s.zoneStart) % 1.0 + 1.0) % 1.0; // how far past the zone start the needle is
        boolean hit = d <= s.zoneSize + TOLERANCE || d >= 1.0 - TOLERANCE;
        if (hit) {
            advance(player, s);
        } else {
            miss(player, s, "Missed!");
        }
    }

    private static void advance(ServerPlayer player, Session s) {
        ServerLevel level = player.server.getLevel(s.brain.dimension());
        if (level == null || !(level.getBlockEntity(s.brain.pos()) instanceof BrainBlockEntity brain)) {
            end(s.player);
            return;
        }
        s.round++;
        if (s.round >= s.rounds) {
            brain.grantAccess(s.player);
            PROGRESS.remove(key(s.player, s.brain));
            end(s.player);
            send(player, s.laptop, HackState.DONE, s.rounds, s.rounds, null, 0, "Access granted.");
            // the editor opens right away (the hacker keeps his Cipher)
            PacketDistributor.sendToPlayer(player, NetworkScan.snapshot(player.level(), s.laptop, true));
            return;
        }
        PROGRESS.put(key(s.player, s.brain), new Progress(s.round, brain.lockStamp(), System.currentTimeMillis() + PROGRESS_MS));
        startRound(player, player.level(), s);
    }

    /** A miss: one Cipher breaks, the progress is kept, the player waits a few seconds. */
    private static void miss(ServerPlayer player, Session s, String reason) {
        ServerLevel level = player.server.getLevel(s.brain.dimension());
        if (level != null && level.getBlockEntity(s.brain.pos()) instanceof BrainBlockEntity brain) {
            PROGRESS.put(key(s.player, s.brain), new Progress(s.round, brain.lockStamp(), System.currentTimeMillis() + PROGRESS_MS));
        }
        ItemStack cipher = findCipher(player);
        boolean broke = false;
        if (!cipher.isEmpty() && !player.getAbilities().instabuild) {
            cipher.shrink(1);
            broke = true;
        }
        COOLDOWN.put(key(s.player, s.brain), player.level().getGameTime() + COOLDOWN_TICKS);
        end(s.player);
        send(player, s.laptop, HackState.FAIL, s.round, s.rounds, null, 0, reason
                + (broke ? " Your Cipher broke." : "") + " Part " + s.round + " of " + s.rounds + " is saved for 24 hours.");
    }

    private static ItemStack findCipher(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (stack.is(ModItems.CIPHER.get())) {
                return stack;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(ModItems.CIPHER.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void end(UUID player) {
        Session s = BY_PLAYER.remove(player);
        if (s != null) {
            BY_BRAIN.remove(s.brain);
        }
    }

    public static void onLogout(UUID player) {
        end(player);
    }

    /** Every tick: a needle that was not stopped in time is a miss; a hacker who left or walked away just stops. */
    public static void onTick(ServerTickEvent.Post event) {
        if (BY_PLAYER.isEmpty()) {
            return;
        }
        for (Session s : new ArrayList<>(BY_PLAYER.values())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(s.player);
            if (player == null) {
                end(s.player);
                continue;
            }
            Level level = player.level();
            if (!(level.isLoaded(s.brain.pos()) && level.dimension().equals(s.brain.dimension())
                    && level.getBlockEntity(s.brain.pos()) instanceof BrainBlockEntity)) {
                end(s.player);
                continue;
            }
            long now = level.getGameTime();
            if (now % 20 == 0 && !ServerHandlers.laptopUsable(player, level, s.laptop)) {
                end(s.player);
                send(player, s.laptop, HackState.FAIL, s.round, s.rounds, null, 0, "Hack stopped: you moved away.");
                continue;
            }
            if (now > s.deadlineTick) {
                miss(player, s, "Too slow!");
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        BY_BRAIN.clear();
        BY_PLAYER.clear();
        PROGRESS.clear();
        COOLDOWN.clear();
    }

    private HackManager() {
    }
}

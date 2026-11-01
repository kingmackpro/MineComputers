package net.minecomputers.script;

import net.minecomputers.block.entity.SpeakerBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * What scripts can do with a Speaker: get("speaker:1").play("block.note_block.harp", 1, 1) plays a sound
 * (name, volume 0 to 2 default 1, pitch 0.5 to 2 default 1) and returns true if it played (false: no FE or
 * too many sounds this second). .isPowered(). Runs on the server thread.
 */
final class SpeakerApi {
    static Object call(GlobalPos device, String op, Object a, Object b, Object c) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(device.dimension());
        if (level == null || !level.isLoaded(device.pos())) {
            throw new IllegalStateException("The speaker is not loaded");
        }
        if (!(level.getBlockEntity(device.pos()) instanceof SpeakerBlockEntity speaker)) {
            throw new IllegalStateException("This speaker is gone: run the script again");
        }
        switch (op) {
            case "play" -> {
                if (a == null) {
                    throw new IllegalArgumentException("A sound name is required");
                }
                return speaker.play(a.toString(), number(b, 1.0), number(c, 1.0));
            }
            case "file" -> {
                return speaker.playMedia(String.valueOf(a), number(b, 1.0), number(c, 0.0) != 0);
            }
            case "stopfile" -> speaker.stopMedia();
            case "powered" -> {
                return speaker.isPowered();
            }
            default -> throw new IllegalArgumentException("Unknown speaker operation: " + op);
        }
        return null;
    }

    /** The script stopped: silence the file this speaker is playing. */
    static void releaseAll(GlobalPos device) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(device.dimension());
        if (level != null && level.isLoaded(device.pos())
                && level.getBlockEntity(device.pos()) instanceof SpeakerBlockEntity speaker) {
            speaker.stopMedia();
        }
    }

    private static double number(Object o, double fallback) {
        if (o == null) {
            return fallback;
        }
        if (o instanceof Number n && !Double.isNaN(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("A number is required");
    }

    private SpeakerApi() {
    }
}

package net.minecomputers.script;

import net.minecomputers.block.entity.RedstoneLinkBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * What scripts can do with a Redstone Link (8 links, numbered 0 to 7, wireless Create-style transmitters):
 * get("redstone:1").setFrequency(0, "red_wool", "blue_wool") pairs link 0 with Create Redstone Links that use
 * those two items, .setSignal(0, 15) sends strength 0 to 15, .getSignal(0), .getFrequency(0) -> [first, second],
 * .clearLink(0), .isPowered(). Item names are like "minecraft:red_wool" ("minecraft:" may be left out); an empty
 * or missing item means none. Runs on the server thread.
 */
final class RedstoneApi {
    static Object call(GlobalPos device, String op, Object a, Object b, Object c) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(device.dimension());
        if (level == null || !level.isLoaded(device.pos())) {
            throw new IllegalStateException("The redstone link is not loaded");
        }
        if (!(level.getBlockEntity(device.pos()) instanceof RedstoneLinkBlockEntity link)) {
            throw new IllegalStateException("This redstone link is gone: run the script again");
        }
        switch (op) {
            case "freq" -> link.setFrequency(index(a), RedstoneLinkBlockEntity.parseItem(text(b)), RedstoneLinkBlockEntity.parseItem(text(c)));
            case "signal" -> link.setSignal(index(a), (int) Math.round(number(b)));
            case "getsignal" -> {
                return link.signal(index(a));
            }
            case "getfreq" -> {
                return link.frequencyName(index(a), number(b) != 0);
            }
            case "clear" -> {
                int i = index(a);
                link.setSignal(i, 0);
                link.setFrequency(i, Items.AIR, Items.AIR);
            }
            case "powered" -> {
                return link.isPowered();
            }
            default -> throw new IllegalArgumentException("Unknown redstone link operation: " + op);
        }
        return null;
    }

    /** Sets every link of a Redstone Link to 0 (the script stopped). Does nothing if the block is not loaded. */
    static void releaseAll(GlobalPos device) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(device.dimension());
        if (level == null || !level.isLoaded(device.pos())) {
            return;
        }
        if (level.getBlockEntity(device.pos()) instanceof RedstoneLinkBlockEntity link) {
            for (int i = 0; i < RedstoneLinkBlockEntity.LINKS; i++) {
                link.setSignal(i, 0);
            }
        }
    }

    private static int index(Object o) {
        double value = number(o);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException("A link number is a whole number, 0 to " + (RedstoneLinkBlockEntity.LINKS - 1));
        }
        return (int) value;
    }

    private static double number(Object o) {
        if (o instanceof Number n && !Double.isNaN(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("A number is required");
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    private RedstoneApi() {
    }
}

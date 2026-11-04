package net.minecomputers.network;

import net.minecomputers.block.entity.BrainBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The Cipher lock (SPEC section 8), server side. A system (its Brain) that is locked can only be used by its owner,
 * by players who hacked it, and by ops (permission level 2). Everybody else gets nothing from the laptop.
 * A locked-out player who opens a laptop gets the hacking mini game instead (see HackManager).
 */
public final class CipherLock {
    /** True if this player may see and change the system of this Brain. */
    public static boolean canAccess(ServerPlayer player, BrainBlockEntity brain) {
        if (!brain.isLocked()) {
            return true;
        }
        if (net.minecomputers.MineComputersConfig.hackOwnSystems()) {
            return brain.hasAccess(player.getUUID()); // testing: even the owner has to hack
        }
        return player.getUUID().equals(brain.owner())
                || brain.hasAccess(player.getUUID())
                || player.hasPermissions(2);
    }

    /**
     * What the laptop at this position may show this player, or null if the system is locked against him (he gets
     * an action bar message instead). Use this instead of NetworkScan.snapshot for anything sent to a player.
     */
    public static LaptopSnapshot snapshotFor(ServerPlayer player, Level level, BlockPos laptop, boolean open) {
        BrainBlockEntity brain = NetworkScan.findBrain(level, laptop);
        if (brain != null && !canAccess(player, brain)) {
            if (open) {
                HackManager.offer(player, level, laptop, brain); // opens the hack screen
            }
            return null;
        }
        return NetworkScan.snapshot(level, laptop, open);
    }

    private CipherLock() {
    }
}

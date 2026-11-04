package net.minecomputers.network;

import net.minecomputers.registry.ModBlocks;
import net.minecomputers.registry.ModDataComponents;
import net.minecomputers.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rules for editing a system from far away (SPEC sections 2 and 4). A Laptop item has two upgrade slots (its own
 * menu, see menu/LaptopUpgradeMenu): slot 0 takes a Remote Access upgrade, slot 1 a Link Transmitter that was linked
 * to a Remote Gateway. The items are stored on the laptop stack (vanilla container component). A laptop with the
 * upgrade AND a linked transmitter can be used from up to RANGE blocks from that gateway, in the same dimension,
 * without placing it. The player must keep holding that laptop. The server checks all of this on every request.
 * Common code (no client classes): the menu screen reads the same helpers.
 */
public final class RemoteAccess {
    /** Blocks between the player and the Remote Gateway, without boosters. */
    public static final double RANGE = 50.0;
    /** Extra blocks for every Range Booster in the laptop. */
    public static final double BOOST = 50.0;

    public static final int SLOTS = 4;
    public static final int SLOT_UPGRADE = 0;
    public static final int SLOT_TRANSMITTER = 1;
    public static final int SLOT_BOOSTER_A = 2;
    public static final int SLOT_BOOSTER_B = 3;

    /** Which item a slot of the laptop's upgrade menu accepts (add new upgrade slots here). */
    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_UPGRADE -> stack.is(ModItems.REMOTE_ACCESS.get());
            case SLOT_TRANSMITTER -> stack.is(ModItems.LINK_TRANSMITTER.get());
            case SLOT_BOOSTER_A, SLOT_BOOSTER_B -> stack.is(ModItems.RANGE_BOOSTER.get());
            default -> false;
        };
    }

    /** Range in blocks with this many Range Boosters installed. */
    public static double rangeWith(int boosters) {
        return RANGE + BOOST * boosters;
    }

    /** Range of this laptop item: base range plus its Range Boosters. */
    public static double range(ItemStack laptop) {
        int boosters = 0;
        for (int slot = SLOT_BOOSTER_A; slot <= SLOT_BOOSTER_B; slot++) {
            if (slotItem(laptop, slot).is(ModItems.RANGE_BOOSTER.get())) {
                boosters++;
            }
        }
        return rangeWith(boosters);
    }

    /** What is in one of the laptop's two upgrade slots (empty stack if nothing). */
    public static ItemStack slotItem(ItemStack laptop, int slot) {
        ItemContainerContents contents = laptop.get(DataComponents.CONTAINER);
        if (contents == null) {
            return ItemStack.EMPTY;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        contents.copyInto(list);
        return list.get(slot);
    }

    /** True if something is installed in the laptop (it can not be placed then: the items would be lost). */
    public static boolean hasAnyUpgrade(ItemStack laptop) {
        return !slotItem(laptop, SLOT_UPGRADE).isEmpty() || !slotItem(laptop, SLOT_TRANSMITTER).isEmpty();
    }

    /** True if this laptop item has a Remote Access upgrade installed. */
    public static boolean hasUpgrade(ItemStack laptop) {
        return slotItem(laptop, SLOT_UPGRADE).is(ModItems.REMOTE_ACCESS.get());
    }

    /** The Remote Gateway stored on a Link Transmitter item, or null. */
    public static GlobalPos transmitterLink(ItemStack transmitter) {
        return transmitter.get(ModDataComponents.LINKED_GATEWAY.get());
    }

    /** The Remote Gateway this laptop is linked to (needs the upgrade and a linked Link Transmitter in its slots), or null. */
    public static GlobalPos linkOf(ItemStack laptop) {
        if (!hasUpgrade(laptop)) {
            return null;
        }
        ItemStack transmitter = slotItem(laptop, SLOT_TRANSMITTER);
        return transmitter.is(ModItems.LINK_TRANSMITTER.get()) ? transmitterLink(transmitter) : null;
    }

    /** Why this player can not use this gateway right now (a short message for the player), or null if he can. */
    public static String problem(ServerPlayer player, GlobalPos gateway, double range) {
        if (!player.level().dimension().equals(gateway.dimension())) {
            return "The linked Remote Gateway is in another dimension.";
        }
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(gateway.pos())) {
            return "The linked Remote Gateway is not loaded: you are too far away.";
        }
        if (!level.getBlockState(gateway.pos()).is(ModBlocks.REMOTE_GATEWAY.get())) {
            return "The linked Remote Gateway is gone.";
        }
        if (player.distanceToSqr(Vec3.atCenterOf(gateway.pos())) > range * range) {
            return "Out of range: stay within " + (int) range + " blocks of the Remote Gateway.";
        }
        return null;
    }

    /**
     * True if the player is holding (in either hand) a laptop linked to the gateway at pos, and is within range of
     * it. This is what every remote laptop request is checked with.
     */
    public static boolean canUse(ServerPlayer player, Level level, BlockPos pos) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.LAPTOP.get()) && here.equals(linkOf(stack))) {
                return problem(player, here, range(stack)) == null;
            }
        }
        return false;
    }

    /** A message for the player when a remote request was refused because of range or because he put the laptop away. */
    public static String refusal(ServerPlayer player, Level level, BlockPos pos) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.LAPTOP.get()) && here.equals(linkOf(stack))) {
                String problem = problem(player, here, range(stack));
                return problem == null ? "" : problem;
            }
        }
        return "Hold the linked laptop to keep editing remotely.";
    }

    private RemoteAccess() {
    }
}

package net.minecomputers.menu;

import net.minecomputers.network.CipherLock;
import net.minecomputers.network.LaptopSnapshot;
import net.minecomputers.network.RemoteAccess;
import net.minecomputers.registry.ModItems;
import net.minecomputers.registry.ModMenus;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The upgrade menu of a Laptop ITEM (right-click it, not placed). Two slots: a Remote Access upgrade and a Link
 * Transmitter. The items live on the laptop stack itself (vanilla container component, see RemoteAccess) and are
 * saved on the server every time a slot changes. The held laptop can not be moved while the menu is open.
 * Button 0 ("Open editor") opens the editor of the linked system, if the laptop is ready and in range.
 * The server decides everything: the client only shows what the slots contain.
 */
public class LaptopUpgradeMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 190;
    public static final int SLOT_Y = 36;
    /** X of each upgrade slot, same order as the slot numbers in RemoteAccess. */
    public static final int[] SLOT_X = {12, 52, 104, 144};
    /** Short label over each slot. */
    public static final String[] SLOT_LABEL = {"Access", "Link", "Boost", "Boost"};
    public static final int INV_Y = 108;

    private final Inventory playerInventory;
    private final InteractionHand hand;
    private final Container upgrades;
    private boolean loading = true;

    /** Server side (and shared by the client constructor). */
    public LaptopUpgradeMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(ModMenus.LAPTOP_UPGRADES.get(), containerId);
        this.playerInventory = playerInventory;
        this.hand = hand;
        this.upgrades = new SimpleContainer(RemoteAccess.SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!loading) {
                    save();
                }
            }
        };

        // what is installed now (only meaningful on the server; the client gets the slots from the server)
        ItemStack laptop = playerInventory.player.getItemInHand(hand);
        for (int i = 0; i < RemoteAccess.SLOTS; i++) {
            upgrades.setItem(i, RemoteAccess.slotItem(laptop, i).copy());
        }
        loading = false;

        for (int i = 0; i < RemoteAccess.SLOTS; i++) {
            final int slotNumber = i;
            addSlot(new Slot(upgrades, i, SLOT_X[i], SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RemoteAccess.accepts(slotNumber, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        // the slot that holds the laptop (main hand = selected hotbar slot) is locked
        int locked = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addPlayerSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18, locked);
            }
        }
        for (int col = 0; col < 9; col++) {
            addPlayerSlot(playerInventory, col, 8 + col * 18, INV_Y + 58, locked);
        }
    }

    /** Client side: the server writes which hand holds the laptop. */
    public LaptopUpgradeMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    private void addPlayerSlot(Inventory inventory, int index, int x, int y, int lockedIndex) {
        if (index == lockedIndex) {
            addSlot(new Slot(inventory, index, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        } else {
            addSlot(new Slot(inventory, index, x, y));
        }
    }

    /** Writes the two slots to the laptop stack in the player's hand (server only). */
    private void save() {
        if (playerInventory.player.level().isClientSide) {
            return;
        }
        ItemStack laptop = playerInventory.player.getItemInHand(hand);
        if (!laptop.is(ModItems.LAPTOP.get())) {
            return;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(RemoteAccess.SLOTS, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < RemoteAccess.SLOTS; i++) {
            list.set(i, upgrades.getItem(i).copy());
            any |= !upgrades.getItem(i).isEmpty();
        }
        if (any) {
            laptop.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
        } else {
            laptop.remove(DataComponents.CONTAINER);
        }
    }

    /** True if a Remote Access upgrade and a linked Link Transmitter are in the slots. */
    public boolean remoteReady() {
        return link() != null;
    }

    public boolean hasUpgrade() {
        return upgrades.getItem(RemoteAccess.SLOT_UPGRADE).is(ModItems.REMOTE_ACCESS.get());
    }

    public boolean hasTransmitter() {
        return upgrades.getItem(RemoteAccess.SLOT_TRANSMITTER).is(ModItems.LINK_TRANSMITTER.get());
    }

    /** Range in blocks with the Range Boosters that are in the slots now. */
    public double range() {
        int boosters = 0;
        for (int slot = RemoteAccess.SLOT_BOOSTER_A; slot <= RemoteAccess.SLOT_BOOSTER_B; slot++) {
            if (upgrades.getItem(slot).is(ModItems.RANGE_BOOSTER.get())) {
                boosters++;
            }
        }
        return RemoteAccess.rangeWith(boosters);
    }

    /** The gateway the laptop is linked to (upgrade + linked transmitter in the slots), or null. */
    public GlobalPos link() {
        if (!hasUpgrade() || !hasTransmitter()) {
            return null;
        }
        return RemoteAccess.transmitterLink(upgrades.getItem(RemoteAccess.SLOT_TRANSMITTER));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        GlobalPos link = link();
        if (link == null) {
            sp.displayClientMessage(Component.literal("Put a Remote Access upgrade and a linked Link Transmitter in the laptop."), true);
            return true;
        }
        String problem = RemoteAccess.problem(sp, link, range());
        if (problem != null) {
            sp.displayClientMessage(Component.literal(problem), true);
            return true;
        }
        sp.closeContainer();
        // the gateway acts like a placed laptop: the snapshot is the system that touches it
        LaptopSnapshot snapshot = CipherLock.snapshotFor(sp, sp.serverLevel(), link.pos(), true);
        if (snapshot != null) {
            PacketDistributor.sendToPlayer(sp, snapshot);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < RemoteAccess.SLOTS) {
            if (!moveItemStackTo(stack, RemoteAccess.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, RemoteAccess.SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.LAPTOP.get());
    }
}

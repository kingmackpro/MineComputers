package net.minecomputers.menu;

import net.minecomputers.block.entity.RedstoneLinkBlockEntity;
import net.minecomputers.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Redstone Link GUI, like Create's link: 8 links, each with two "ghost" frequency slots.
 * Click a ghost slot with an item on the cursor to set it (the item is not used up); click with an empty
 * cursor, or shift-click, to clear it. Shift-click an item in your inventory to put it in the first free ghost slot.
 *
 * The server decides: every change is written to the block entity on the server (the client only predicts the
 * same click). While the menu is open the slots follow the block, so a script changing a frequency shows up.
 * Data slots 0..7 = the signal strength of each link, 8 = powered (1/0).
 *
 * This class is common code (no client classes), the screen is client/RedstoneLinkScreen.
 */
public class RedstoneLinkMenu extends AbstractContainerMenu {
    public static final int LINKS = RedstoneLinkBlockEntity.LINKS;
    public static final int GHOST_SLOTS = LINKS * 2;
    /** Data slot index of the powered flag. */
    public static final int DATA_POWERED = LINKS;

    // Layout (relative to the GUI's top left), shared with the screen.
    public static final int WIDTH = 176;
    public static final int HEIGHT = 212;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 22;
    public static final int COLUMN_STEP = 84;
    public static final int ROW_STEP = 24;
    public static final int ROWS = 4;
    public static final int INV_Y = 134;

    /** Server side: the block. Null on the client. */
    private final RedstoneLinkBlockEntity block;
    private final BlockPos pos;
    private final Container ghost = new SimpleContainer(GHOST_SLOTS);
    private final ContainerData data;

    /** Client constructor (called by the menu type with the data the server wrote). */
    public RedstoneLinkMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new SimpleContainerData(LINKS + 1));
    }

    /** Server constructor. */
    public RedstoneLinkMenu(int id, Inventory inventory, RedstoneLinkBlockEntity block) {
        this(id, inventory, block, block.getBlockPos(), new ContainerData() {
            @Override
            public int get(int index) {
                if (index < LINKS) {
                    return block.signal(index);
                }
                return block.isPowered() ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return LINKS + 1;
            }
        });
    }

    private RedstoneLinkMenu(int id, Inventory inventory, RedstoneLinkBlockEntity block, BlockPos pos, ContainerData data) {
        super(ModMenus.REDSTONE_LINK.get(), id);
        this.block = block;
        this.pos = pos;
        this.data = data;

        for (int link = 0; link < LINKS; link++) {
            int x = GRID_X + (link / ROWS) * COLUMN_STEP + 10;
            int y = GRID_Y + (link % ROWS) * ROW_STEP;
            addSlot(new GhostSlot(ghost, link * 2, x, y));
            addSlot(new GhostSlot(ghost, link * 2 + 1, x + 20, y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
        if (block != null) {
            readFromBlock();
        }
    }

    /** A slot that only shows a frequency item. All changes go through clicked(). */
    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    // ------------------------------------------------------------ for the screen

    public int signal(int link) {
        return data.get(link);
    }

    public boolean isPowered() {
        return data.get(DATA_POWERED) != 0;
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                ItemStack carried = getCarried();
                ghost.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            } else if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.THROW) {
                ghost.setItem(slotId, ItemStack.EMPTY);
            } else {
                return; // swap, clone, double click: nothing to do on a ghost slot
            }
            writeToBlock(slotId / 2);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= GHOST_SLOTS && index < slots.size()) {
            ItemStack stack = slots.get(index).getItem();
            if (!stack.isEmpty()) {
                for (int i = 0; i < GHOST_SLOTS; i++) {
                    if (ghost.getItem(i).isEmpty()) {
                        ghost.setItem(i, stack.copyWithCount(1));
                        writeToBlock(i / 2);
                        break;
                    }
                }
            }
        }
        return ItemStack.EMPTY; // the real item always stays where it is
    }

    // ------------------------------------------------------ server: block sync

    /** Server only: copies the two slots of a link into the block. */
    private void writeToBlock(int link) {
        if (block == null || block.isRemoved()) {
            return;
        }
        Item first = ghost.getItem(link * 2).getItem();
        Item second = ghost.getItem(link * 2 + 1).getItem();
        block.setFrequency(link, first, second);
    }

    /** Server only: copies the block's frequencies into the slots (when a script changed them). */
    private void readFromBlock() {
        for (int link = 0; link < LINKS; link++) {
            for (int s = 0; s < 2; s++) {
                int index = link * 2 + s;
                Item want = block.frequencyItem(link, s == 1);
                if (ghost.getItem(index).getItem() != want) {
                    ghost.setItem(index, want == Items.AIR ? ItemStack.EMPTY : new ItemStack(want));
                }
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (block != null && !block.isRemoved()) {
            readFromBlock();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (block == null) {
            return true; // client: the server closes the menu
        }
        return !block.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}

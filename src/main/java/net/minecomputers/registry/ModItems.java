package net.minecomputers.registry;

import net.minecomputers.MineComputers;
import net.minecomputers.item.CipherItem;
import net.minecomputers.item.LaptopItem;
import net.minecomputers.item.LinkTransmitterItem;
import net.minecomputers.item.RemoteAccessItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All items: a block item for each block, plus the three standalone items.
 * Crafting recipes come later (see docs/SPEC.md).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MineComputers.MOD_ID);

    // Block items.
    // The laptop is a custom item: it can get the Remote Access upgrade and be linked to a Remote Gateway.
    public static final DeferredItem<LaptopItem> LAPTOP = ITEMS.registerItem("laptop",
            props -> new LaptopItem(ModBlocks.LAPTOP.get(), props), new Item.Properties().stacksTo(1));
    public static final DeferredItem<BlockItem> BRAIN = ITEMS.registerSimpleBlockItem(ModBlocks.BRAIN);
    public static final DeferredItem<BlockItem> DATA_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_CABLE);
    public static final DeferredItem<BlockItem> POWER_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_CABLE);
    public static final DeferredItem<BlockItem> POWER_DATA_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_DATA_CABLE);
    public static final DeferredItem<BlockItem> SERVO = ITEMS.registerSimpleBlockItem(ModBlocks.SERVO);
    public static final DeferredItem<BlockItem> SCREEN = ITEMS.registerSimpleBlockItem(ModBlocks.SCREEN);
    public static final DeferredItem<BlockItem> SPEAKER = ITEMS.registerSimpleBlockItem(ModBlocks.SPEAKER);
    public static final DeferredItem<BlockItem> REDSTONE_LINK = ITEMS.registerSimpleBlockItem(ModBlocks.REDSTONE_LINK);
    public static final DeferredItem<BlockItem> REMOTE_GATEWAY = ITEMS.registerSimpleBlockItem(ModBlocks.REMOTE_GATEWAY);

    public static final DeferredItem<BlockItem> POWER_SOURCE = ITEMS.registerSimpleBlockItem(ModBlocks.POWER_SOURCE);

    // Standalone items.
    public static final DeferredItem<RemoteAccessItem> REMOTE_ACCESS = ITEMS.registerItem("remote_access",
            RemoteAccessItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<LinkTransmitterItem> LINK_TRANSMITTER = ITEMS.registerItem("link_transmitter",
            LinkTransmitterItem::new, new Item.Properties().stacksTo(1));
    /** Laptop upgrade: +50 blocks of remote range per booster (two slots in the laptop menu). */
    public static final DeferredItem<Item> RANGE_BOOSTER = ITEMS.registerSimpleItem("range_booster", new Item.Properties().stacksTo(1));
    public static final DeferredItem<CipherItem> CIPHER = ITEMS.registerItem("cipher",
            CipherItem::new, new Item.Properties().stacksTo(16));

    private ModItems() {
    }
}

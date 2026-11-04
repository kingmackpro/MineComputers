package net.minecomputers.block.entity;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecomputers.power.PowerNet;
import net.minecomputers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Redstone Link: 8 links in one block. Each link is a Create-style wireless TRANSMITTER: it has a frequency
 * (two items, exactly like Create's own Redstone Link) and a signal strength 0 to 15 that the script sets.
 * Every Create Redstone Link (or anything else on Create's link network) that listens on the same two items
 * receives that strength.
 *
 * Integration: each configured link is registered with Create's link network (Create.REDSTONE_LINK_NETWORK_HANDLER)
 * as an IRedstoneLinkable. This block never listens (it only sends), so it does not read signals.
 *
 * Power: FE only (PowerNet). Every second: 1 FE while at least one link is configured, plus 1 FE for each link
 * that is sending a signal above 0. No FE: all links send 0 until power is back (checked once a second).
 */
public class RedstoneLinkBlockEntity extends BlockEntity {
    public static final int LINKS = 8;
    public static final int MAX_STRENGTH = 15;
    /** The power check runs once a second. */
    private static final int POWER_INTERVAL_TICKS = 20;

    private final Link[] links = new Link[LINKS];
    private boolean powered;
    private int tickCount;

    public RedstoneLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_LINK.get(), pos, state);
        for (int i = 0; i < LINKS; i++) {
            links[i] = new Link();
        }
    }

    /** One of the 8 links. Transmit only. */
    private final class Link implements IRedstoneLinkable {
        Item first = Items.AIR;
        Item second = Items.AIR;
        int strength;
        boolean joined;
        Couple<Frequency> key = makeKey();

        boolean configured() {
            return first != Items.AIR || second != Items.AIR;
        }

        Couple<Frequency> makeKey() {
            return Couple.create(Frequency.of(new ItemStack(first)), Frequency.of(new ItemStack(second)));
        }

        @Override
        public int getTransmittedStrength() {
            return powered && !isRemoved() ? strength : 0;
        }

        @Override
        public void setReceivedStrength(int power) {
            // This block only sends.
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == RedstoneLinkBlockEntity.this;
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }

    // ------------------------------------------------------------ script API

    private Link link(int index) {
        if (index < 0 || index >= LINKS) {
            throw new IllegalArgumentException("A link number is 0 to " + (LINKS - 1));
        }
        return links[index];
    }

    /** Sets the frequency (two items, either may be air = empty) of a link. Keeps its signal. */
    public void setFrequency(int index, Item first, Item second) {
        Link link = link(index);
        leave(link);
        link.first = first;
        link.second = second;
        link.key = link.makeKey();
        join(link);
        setChanged();
    }

    /** Sets the strength (0 to 15) a link sends. */
    public void setSignal(int index, int strength) {
        Link link = link(index);
        int clamped = Math.max(0, Math.min(MAX_STRENGTH, strength));
        if (clamped != link.strength) {
            link.strength = clamped;
            refresh(link);
            setChanged();
        }
    }

    public int signal(int index) {
        return link(index).strength;
    }

    /** The registry name of one of the two frequency items of a link ("" when empty). */
    public String frequencyName(int index, boolean second) {
        Item item = second ? link(index).second : link(index).first;
        return item == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** One of the two frequency items of a link (Items.AIR when empty). Used by the GUI menu. */
    public Item frequencyItem(int index, boolean second) {
        return second ? link(index).second : link(index).first;
    }

    public boolean isPowered() {
        return powered;
    }

    /** Parses an item name like "minecraft:red_wool" or "red_wool"; empty means no item. */
    public static Item parseItem(String name) {
        if (name == null || name.isBlank()) {
            return Items.AIR;
        }
        String full = name.contains(":") ? name : "minecraft:" + name;
        ResourceLocation id = ResourceLocation.tryParse(full);
        if (id == null) {
            throw new IllegalArgumentException("Not a valid item name: " + name);
        }
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + name));
    }

    // --------------------------------------------------- Create link network

    private void join(Link link) {
        if (level == null || level.isClientSide || link.joined || !link.configured()) {
            return;
        }
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
        link.joined = true;
    }

    private void leave(Link link) {
        if (level == null || level.isClientSide || !link.joined) {
            return;
        }
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
        link.joined = false;
    }

    private void refresh(Link link) {
        if (level != null && !level.isClientSide && link.joined) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, link);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        for (Link link : links) {
            join(link);
        }
    }

    @Override
    public void onChunkUnloaded() {
        for (Link link : links) {
            leave(link);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        for (Link link : links) {
            leave(link);
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------ power

    /** Called every server tick by the block. */
    public void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        tickCount++;
        if (tickCount != 1 && tickCount % POWER_INTERVAL_TICKS != 0) {
            return;
        }
        int cost = 0;
        boolean any = false;
        for (Link link : links) {
            if (link.configured()) {
                any = true;
                if (link.strength > 0) {
                    cost++;
                }
            }
        }
        if (any) {
            cost++;
        }
        boolean now = cost == 0 || PowerNet.pull(level, worldPosition, cost);
        if (now != powered) {
            powered = now;
            for (Link link : links) {
                refresh(link);
            }
        }
    }

    // --------------------------------------------------------------- save

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Link link : links) {
            CompoundTag entry = new CompoundTag();
            entry.putString("first", link.first == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(link.first).toString());
            entry.putString("second", link.second == Items.AIR ? "" : BuiltInRegistries.ITEM.getKey(link.second).toString());
            entry.putInt("strength", link.strength);
            list.add(entry);
        }
        tag.put("links", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < LINKS && i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Link link = links[i];
            try {
                link.first = parseItem(entry.getString("first"));
                link.second = parseItem(entry.getString("second"));
            } catch (IllegalArgumentException e) {
                link.first = Items.AIR; // an item that no longer exists (a removed mod)
                link.second = Items.AIR;
            }
            link.strength = Math.max(0, Math.min(MAX_STRENGTH, entry.getInt("strength")));
            link.key = link.makeKey();
        }
    }
}

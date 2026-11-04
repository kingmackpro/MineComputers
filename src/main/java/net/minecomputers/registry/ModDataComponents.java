package net.minecomputers.registry;

import net.minecomputers.MineComputers;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components stored on items. (The upgrades inside a Laptop use the vanilla container component, see
 * network/RemoteAccess.)
 */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MineComputers.MOD_ID);

    /** On a Link Transmitter: the Remote Gateway it was linked to (dimension + position). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINKED_GATEWAY =
            COMPONENTS.register("linked_gateway", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }
}

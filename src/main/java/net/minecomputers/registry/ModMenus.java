package net.minecomputers.registry;

import net.minecomputers.MineComputers;
import net.minecomputers.menu.LaptopUpgradeMenu;
import net.minecomputers.menu.RedstoneLinkMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Container menus (GUIs that have slots). */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MineComputers.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RedstoneLinkMenu>> REDSTONE_LINK =
            MENUS.register("redstone_link", () -> IMenuTypeExtension.create(RedstoneLinkMenu::new));

    /** Upgrade slots of a Laptop item (Remote Access + Link Transmitter). */
    public static final DeferredHolder<MenuType<?>, MenuType<LaptopUpgradeMenu>> LAPTOP_UPGRADES =
            MENUS.register("laptop_upgrades", () -> IMenuTypeExtension.create(LaptopUpgradeMenu::new));

    private ModMenus() {
    }
}

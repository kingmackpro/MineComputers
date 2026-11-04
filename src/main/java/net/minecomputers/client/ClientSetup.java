package net.minecomputers.client;

import net.minecomputers.MineComputers;
import net.minecomputers.registry.ModBlockEntities;
import net.minecomputers.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registration, kept out of the common MineComputers constructor so none of this loads on a
 * dedicated server: NeoForge only fires this on the physical client.
 */
@EventBusSubscriber(modid = MineComputers.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SCREEN.get(), ScreenBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.REDSTONE_LINK.get(), RedstoneLinkScreen::new);
        event.register(ModMenus.LAPTOP_UPGRADES.get(), LaptopUpgradeScreen::new);
    }

    private ClientSetup() {
    }
}

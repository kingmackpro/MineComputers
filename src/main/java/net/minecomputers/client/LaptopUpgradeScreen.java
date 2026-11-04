package net.minecomputers.client;

import net.minecomputers.menu.LaptopUpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The upgrade menu of a Laptop item: two slots (Remote Access, Link Transmitter), a status line and an
 * "Open editor" button. Plain colored rectangles in the laptop's dark/teal style (no texture file yet).
 * Client only: registered in ClientSetup.
 */
public class LaptopUpgradeScreen extends AbstractContainerScreen<LaptopUpgradeMenu> {
    private static final int PANEL = 0xFF161B20;
    private static final int PANEL_LIGHT = 0xFF2A323A;
    private static final int PANEL_DARK = 0xFF0B0E11;
    private static final int SLOT_BG = 0xFF222A31;
    private static final int TEAL = 0xFF2BD1C4;
    private static final int TEXT = 0xFFD7DEE4;
    private static final int TEXT_DIM = 0xFF7C8791;

    private Button openButton;

    public LaptopUpgradeScreen(LaptopUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = LaptopUpgradeMenu.WIDTH;
        this.imageHeight = LaptopUpgradeMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        openButton = Button.builder(Component.literal("Open editor"), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 8, topPos + 80, imageWidth - 16, 18).build();
        addRenderableWidget(openButton);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        openButton.active = menu.remoteReady();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;
        g.fill(x, y, x + w, y + h, PANEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
        g.fill(x + 6, y + 17, x + w - 6, y + 18, TEAL);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, PANEL_DARK);
            g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        for (int i = 0; i < LaptopUpgradeMenu.SLOT_X.length; i++) {
            centered(g, LaptopUpgradeMenu.SLOT_LABEL[i], LaptopUpgradeMenu.SLOT_X[i] + 8, 24, TEXT_DIM);
        }

        String status;
        int color = TEXT_DIM;
        GlobalPos link = menu.link();
        if (!menu.hasUpgrade()) {
            status = "Insert a Remote Access upgrade";
        } else if (!menu.hasTransmitter()) {
            status = "Insert a Link Transmitter";
        } else if (link == null) {
            status = "Not linked: use it on a Remote Gateway";
        } else {
            status = "Linked to gateway " + link.pos().getX() + ", " + link.pos().getY() + ", " + link.pos().getZ();
            color = TEAL;
        }
        centered(g, status, LaptopUpgradeMenu.WIDTH / 2, 58, color);
        centered(g, "Range: " + (int) menu.range() + " blocks", LaptopUpgradeMenu.WIDTH / 2, 66, TEXT_DIM);
        g.drawString(font, playerInventoryTitle, 8, LaptopUpgradeMenu.INV_Y - 10, TEXT_DIM, false);
    }

    private void centered(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }
}

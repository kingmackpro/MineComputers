package net.minecomputers.client;

import net.minecomputers.menu.RedstoneLinkMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Redstone Link GUI: 8 links in two columns, each with two frequency slots and its current signal strength.
 * Drawn with plain colored rectangles in the laptop's dark/teal style (no texture file yet).
 * Client only: registered in ClientSetup.
 */
public class RedstoneLinkScreen extends AbstractContainerScreen<RedstoneLinkMenu> {
    private static final int PANEL = 0xFF161B20;
    private static final int PANEL_LIGHT = 0xFF2A323A;
    private static final int PANEL_DARK = 0xFF0B0E11;
    private static final int SLOT_BG = 0xFF222A31;
    private static final int TEAL = 0xFF2BD1C4;
    private static final int TEXT = 0xFFD7DEE4;
    private static final int TEXT_DIM = 0xFF7C8791;
    private static final int RED = 0xFFE5534B;

    public RedstoneLinkScreen(RedstoneLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = RedstoneLinkMenu.WIDTH;
        this.imageHeight = RedstoneLinkMenu.HEIGHT;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;
        // bevelled panel
        g.fill(x, y, x + w, y + h, PANEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_LIGHT);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
        // header line in teal
        g.fill(x + 6, y + 17, x + w - 6, y + 18, TEAL);
        // every slot gets a recessed box
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
        boolean powered = menu.isPowered();
        if (!powered) {
            Component noPower = Component.literal("No FE");
            g.drawString(font, noPower, RedstoneLinkMenu.WIDTH - 8 - font.width(noPower), 6, RED, false);
        }
        for (int link = 0; link < RedstoneLinkMenu.LINKS; link++) {
            int cellX = RedstoneLinkMenu.GRID_X + (link / RedstoneLinkMenu.ROWS) * RedstoneLinkMenu.COLUMN_STEP;
            int cellY = RedstoneLinkMenu.GRID_Y + (link % RedstoneLinkMenu.ROWS) * RedstoneLinkMenu.ROW_STEP;
            g.drawString(font, Integer.toString(link + 1), cellX, cellY + 4, TEXT_DIM, false);
            int strength = menu.signal(link);
            String text = Integer.toString(strength);
            g.drawString(font, text, cellX + 10 + 20 + 18 + 4, cellY + 4, strength > 0 ? TEAL : TEXT_DIM, false);
        }
        g.drawString(font, playerInventoryTitle, 8, RedstoneLinkMenu.INV_Y - 10, TEXT_DIM, false);
    }
}

package net.minecomputers.item;

import java.util.List;

import net.minecomputers.menu.LaptopUpgradeMenu;
import net.minecomputers.network.RemoteAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Laptop item. Right-click (in the air or on a block) opens its upgrade menu: two slots for a Remote Access
 * upgrade and a Link Transmitter, and an "Open editor" button that edits the linked system from up to 50 blocks
 * away (see LaptopUpgradeMenu and RemoteAccess).
 * Sneak + right-click on a block places the laptop as before, but only if nothing is installed in it: a placed
 * laptop would lose its upgrades, so take them out first (TODO: make the placed laptop remember its upgrades).
 */
public class LaptopItem extends BlockItem {
    public LaptopItem(net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        openMenu(level, player, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            openMenu(context.getLevel(), player, context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (RemoteAccess.hasAnyUpgrade(context.getItemInHand())) {
            if (!context.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("Take the upgrades out first"), true);
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    private static void openMenu(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        serverPlayer.openMenu(
                new SimpleMenuProvider((id, inventory, p) -> new LaptopUpgradeMenu(id, inventory, hand),
                        Component.literal("Laptop upgrades")),
                buf -> buf.writeBoolean(mainHand));
    }

    private static String describe(GlobalPos pos) {
        return pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Right-click: upgrades. Sneak + right-click: place").withStyle(ChatFormatting.DARK_GRAY));
        if (!RemoteAccess.hasUpgrade(stack)) {
            return;
        }
        tooltip.add(Component.literal("Remote Access installed").withStyle(ChatFormatting.AQUA));
        GlobalPos link = RemoteAccess.linkOf(stack);
        if (link == null) {
            tooltip.add(Component.literal("Not linked: add a linked Link Transmitter").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Linked to the gateway at " + describe(link)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Works within " + (int) RemoteAccess.range(stack) + " blocks of it")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

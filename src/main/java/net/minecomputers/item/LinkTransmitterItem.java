package net.minecomputers.item;

import java.util.List;

import net.minecomputers.network.RemoteAccess;
import net.minecomputers.registry.ModBlocks;
import net.minecomputers.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Link Transmitter. Right-click a Remote Gateway with it to link it to that gateway (the position is stored on
 * the item; right-click another gateway to link it again). Then put it into the Link Transmitter slot of a
 * laptop's upgrade menu (the laptop also needs the Remote Access upgrade). It stays in that slot, so one system per
 * laptop. See LaptopUpgradeMenu and RemoteAccess.
 */
public class LinkTransmitterItem extends Item {
    public LinkTransmitterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.REMOTE_GATEWAY.get())) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            BlockPos pos = context.getClickedPos();
            context.getItemInHand().set(ModDataComponents.LINKED_GATEWAY.get(),
                    GlobalPos.of(context.getLevel().dimension(), pos.immutable()));
            player.displayClientMessage(Component.literal("Link Transmitter linked to the Remote Gateway at "
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "."), true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GlobalPos link = RemoteAccess.transmitterLink(stack);
        if (link == null) {
            tooltip.add(Component.literal("Not linked: right-click a Remote Gateway").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Linked to the gateway at " + link.pos().getX() + ", " + link.pos().getY()
                    + ", " + link.pos().getZ()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Put it in a laptop's upgrade menu (right-click the laptop)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

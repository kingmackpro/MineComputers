package net.minecomputers.item;

import java.util.List;

import net.minecomputers.block.entity.BrainBlockEntity;
import net.minecomputers.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Cipher. Right-click a Brain with it: the system is locked to you (the Cipher is used up). Only you can use it
 * from then on; other players have to hack it (see CipherLock). Locking it again with a new Cipher throws out
 * everybody who hacked it. Sneak + right-click your own locked Brain: unlocks it (the Cipher is not used up).
 * A Brain locked by somebody else can not be locked by you: hack it first.
 */
public class CipherItem extends Item {
    public CipherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.BRAIN.get())) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BrainBlockEntity brain)) {
            return InteractionResult.PASS;
        }
        boolean mine = player.getUUID().equals(brain.owner());
        if (brain.isLocked() && !mine) {
            say(player, "This system is locked by someone else. Hack it with a laptop first.");
            return InteractionResult.FAIL;
        }
        if (mine && player.isShiftKeyDown()) {
            brain.unlock();
            say(player, "System unlocked.");
            return InteractionResult.CONSUME;
        }
        brain.lock(player.getUUID());
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        say(player, "System locked. Only you can use it now.");
        return InteractionResult.CONSUME;
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Right-click a Brain: lock the system to you").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak + right-click your own locked Brain: unlock").withStyle(ChatFormatting.DARK_GRAY));
    }
}

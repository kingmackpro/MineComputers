package net.minecomputers.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Remote Access laptop upgrade. It does nothing on its own: right-click a Laptop and put it into the
 * Remote Access slot of the upgrade menu. See LaptopItem and LaptopUpgradeMenu.
 */
public class RemoteAccessItem extends Item {
    public RemoteAccessItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Install it in a Laptop's upgrade menu (right-click the laptop)")
                .withStyle(ChatFormatting.GRAY));
    }
}

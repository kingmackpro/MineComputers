package net.minecomputers.block;

import net.minecomputers.block.entity.RedstoneLinkBlockEntity;
import net.minecomputers.menu.RedstoneLinkMenu;
import net.minecomputers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Redstone Link: 8 wireless Create-style transmitters, set from a script or from the GUI (right-click,
 * two ghost item slots per link like Create's link). Uses FE. See RedstoneLinkBlockEntity and RedstoneLinkMenu.
 */
public class RedstoneLinkBlock extends Block implements EntityBlock {
    public RedstoneLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneLinkBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RedstoneLinkBlockEntity link) {
            // TODO (Cipher milestone): check owner / Cipher here before opening.
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RedstoneLinkMenu(id, inventory, link), getName()), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.REDSTONE_LINK.get()) {
            return null;
        }
        return (lvl, pos, st, entity) -> ((RedstoneLinkBlockEntity) entity).serverTick();
    }
}

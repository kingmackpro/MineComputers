package net.minecomputers.block;

import net.minecomputers.block.entity.RedstoneLinkBlockEntity;
import net.minecomputers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The Redstone Link: 8 wireless Create-style transmitters, set from a script. Uses FE. See RedstoneLinkBlockEntity. */
public class RedstoneLinkBlock extends Block implements EntityBlock {
    public RedstoneLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneLinkBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.REDSTONE_LINK.get()) {
            return null;
        }
        return (lvl, pos, st, entity) -> ((RedstoneLinkBlockEntity) entity).serverTick();
    }
}

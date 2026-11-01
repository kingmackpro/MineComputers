package net.minecomputers.block;

import net.minecomputers.block.entity.SpeakerBlockEntity;
import net.minecomputers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The Speaker: plays sounds from a script. Uses 5 FE per second. See SpeakerBlockEntity. */
public class SpeakerBlock extends Block implements EntityBlock {
    public SpeakerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SPEAKER.get()) {
            return null;
        }
        return (lvl, pos, st, entity) -> ((SpeakerBlockEntity) entity).serverTick();
    }
}

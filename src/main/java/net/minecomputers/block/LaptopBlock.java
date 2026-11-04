package net.minecomputers.block;

import net.minecomputers.network.CipherLock;
import net.minecomputers.network.LaptopSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The placed laptop. Right-click: the server checks what the laptop is connected to
 * and sends the player a snapshot, which opens the editor GUI on the client.
 */
public class LaptopBlock extends Block {
    public LaptopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            LaptopSnapshot snapshot = CipherLock.snapshotFor(serverPlayer, level, pos, true);
            if (snapshot != null) {
                PacketDistributor.sendToPlayer(serverPlayer, snapshot);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

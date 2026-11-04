package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: BEGIN a hack, PRESS the key in the mini game, or ABORT (the screen was closed). */
public record HackAction(BlockPos laptop, int action) implements CustomPacketPayload {
    public static final int BEGIN = 0;
    public static final int PRESS = 1;
    public static final int ABORT = 2;

    public static final Type<HackAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "hack_action"));

    public static final StreamCodec<FriendlyByteBuf, HackAction> STREAM_CODEC =
            StreamCodec.ofMember(HackAction::write, HackAction::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(laptop);
        buf.writeVarInt(action);
    }

    private static HackAction read(FriendlyByteBuf buf) {
        return new HackAction(buf.readBlockPos(), buf.readVarInt());
    }

    @Override
    public Type<HackAction> type() {
        return TYPE;
    }
}

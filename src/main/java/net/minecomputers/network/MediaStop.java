package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: stop what the Speaker at "pos" is playing. */
public record MediaStop(BlockPos pos) implements CustomPacketPayload {
    public static final Type<MediaStop> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "media_stop"));

    public static final StreamCodec<FriendlyByteBuf, MediaStop> STREAM_CODEC =
            StreamCodec.ofMember(MediaStop::write, MediaStop::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    private static MediaStop read(FriendlyByteBuf buf) {
        return new MediaStop(buf.readBlockPos());
    }

    @Override
    public Type<MediaStop> type() {
        return TYPE;
    }
}

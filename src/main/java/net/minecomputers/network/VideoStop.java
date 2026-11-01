package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: stop the video on the Screen whose origin block is at "pos". */
public record VideoStop(BlockPos pos) implements CustomPacketPayload {
    public static final Type<VideoStop> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "video_stop"));

    public static final StreamCodec<FriendlyByteBuf, VideoStop> STREAM_CODEC =
            StreamCodec.ofMember(VideoStop::write, VideoStop::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    private static VideoStop read(FriendlyByteBuf buf) {
        return new VideoStop(buf.readBlockPos());
    }

    @Override
    public Type<VideoStop> type() {
        return TYPE;
    }
}

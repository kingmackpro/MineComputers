package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: show the stored video file "id" on the Screen whose origin block is at "pos". The sound comes from a
 * Speaker. positionMs = how far into the playback the server clock already is (the shared clock, see MediaTransfer).
 */
public record VideoPlay(BlockPos pos, String id, boolean loop, long positionMs) implements CustomPacketPayload {
    public static final Type<VideoPlay> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "video_play"));

    public static final StreamCodec<FriendlyByteBuf, VideoPlay> STREAM_CODEC =
            StreamCodec.ofMember(VideoPlay::write, VideoPlay::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(id, 64);
        buf.writeBoolean(loop);
        buf.writeVarLong(positionMs);
    }

    private static VideoPlay read(FriendlyByteBuf buf) {
        return new VideoPlay(buf.readBlockPos(), buf.readUtf(64), buf.readBoolean(), buf.readVarLong());
    }

    @Override
    public Type<VideoPlay> type() {
        return TYPE;
    }
}

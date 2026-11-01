package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: start playing the stored audio file "id" (SHA-256 in the media store) at the Speaker at "pos". */
public record MediaPlay(BlockPos pos, String id, float volume, boolean loop) implements CustomPacketPayload {
    public static final Type<MediaPlay> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "media_play"));

    public static final StreamCodec<FriendlyByteBuf, MediaPlay> STREAM_CODEC =
            StreamCodec.ofMember(MediaPlay::write, MediaPlay::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(id, 64);
        buf.writeFloat(volume);
        buf.writeBoolean(loop);
    }

    private static MediaPlay read(FriendlyByteBuf buf) {
        return new MediaPlay(buf.readBlockPos(), buf.readUtf(64), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<MediaPlay> type() {
        return TYPE;
    }
}

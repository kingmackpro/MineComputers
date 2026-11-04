package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: start playing the stored audio file "id" (SHA-256 in the media store) at the Speaker at "pos".
 * positionMs = how far into the playback the server clock already is (0 when it just started, more for a player
 * who comes into range later): the shared clock, see MediaTransfer.
 */
public record MediaPlay(BlockPos pos, String id, float volume, boolean loop, long positionMs) implements CustomPacketPayload {
    public static final Type<MediaPlay> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "media_play"));

    public static final StreamCodec<FriendlyByteBuf, MediaPlay> STREAM_CODEC =
            StreamCodec.ofMember(MediaPlay::write, MediaPlay::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(id, 64);
        buf.writeFloat(volume);
        buf.writeBoolean(loop);
        buf.writeVarLong(positionMs);
    }

    private static MediaPlay read(FriendlyByteBuf buf) {
        return new MediaPlay(buf.readBlockPos(), buf.readUtf(64), buf.readFloat(), buf.readBoolean(), buf.readVarLong());
    }

    @Override
    public Type<MediaPlay> type() {
        return TYPE;
    }
}

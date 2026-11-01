package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: one chunk (0, 1, 2, ... in order) of the media file "id", which is "total" bytes long. */
public record MediaData(String id, int total, int index, byte[] data) implements CustomPacketPayload {
    public static final Type<MediaData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "media_data"));

    public static final StreamCodec<FriendlyByteBuf, MediaData> STREAM_CODEC =
            StreamCodec.ofMember(MediaData::write, MediaData::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
        buf.writeVarInt(total);
        buf.writeVarInt(index);
        buf.writeByteArray(data);
    }

    private static MediaData read(FriendlyByteBuf buf) {
        return new MediaData(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MediaRules.CHUNK_BYTES));
    }

    @Override
    public Type<MediaData> type() {
        return TYPE;
    }
}

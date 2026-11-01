package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: "I do not have this media file yet, send it." The server only answers for files it announced to this player. */
public record MediaRequest(String id) implements CustomPacketPayload {
    public static final Type<MediaRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "media_request"));

    public static final StreamCodec<FriendlyByteBuf, MediaRequest> STREAM_CODEC =
            StreamCodec.ofMember(MediaRequest::write, MediaRequest::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
    }

    private static MediaRequest read(FriendlyByteBuf buf) {
        return new MediaRequest(buf.readUtf(64));
    }

    @Override
    public Type<MediaRequest> type() {
        return TYPE;
    }
}

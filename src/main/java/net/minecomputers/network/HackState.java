package net.minecomputers.network;

import net.minecomputers.MineComputers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: what the hacking mini game shows (SPEC section 8). The server owns the clock and the zone; the
 * client only draws them.
 * <ul>
 * <li>OFFER: the system is locked, the player may start a hack ({@code round} = saved progress, {@code rounds} = total).</li>
 * <li>ROUND: a round starts in {@code delayMs}; the needle makes one turn per {@code periodMs}, starting at the top
 * and going clockwise; the zone starts at {@code zoneStart} (0..1 of a turn) and is {@code zoneSize} long.</li>
 * <li>FAIL: a miss or a refused start, with a message in {@code text}.</li>
 * <li>DONE: the hack worked; the editor opens next.</li>
 * </ul>
 */
public record HackState(BlockPos laptop, int phase, int round, int rounds, float zoneStart, float zoneSize,
                        int periodMs, int delayMs, String text) implements CustomPacketPayload {
    public static final int OFFER = 0;
    public static final int ROUND = 1;
    public static final int FAIL = 2;
    public static final int DONE = 3;

    public static final Type<HackState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MOD_ID, "hack_state"));

    public static final StreamCodec<FriendlyByteBuf, HackState> STREAM_CODEC =
            StreamCodec.ofMember(HackState::write, HackState::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(laptop);
        buf.writeVarInt(phase);
        buf.writeVarInt(round);
        buf.writeVarInt(rounds);
        buf.writeFloat(zoneStart);
        buf.writeFloat(zoneSize);
        buf.writeVarInt(periodMs);
        buf.writeVarInt(delayMs);
        buf.writeUtf(text, 256);
    }

    private static HackState read(FriendlyByteBuf buf) {
        return new HackState(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
    }

    @Override
    public Type<HackState> type() {
        return TYPE;
    }
}

package net.minecomputers.block.entity;

import net.minecomputers.network.MediaTransfer;
import net.minecomputers.power.PowerNet;
import net.minecomputers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Speaker. First slice: plays any sound that exists in the game (vanilla or other mods, like a note block
 * or a music disc: normal in-world sound that gets quieter with distance). Dragged-in files, URLs and Screen
 * sync come later with the media milestone.
 *
 * Power: 5 FE per second, always (paid once a second). No FE: it is silent until power is back.
 * Rate limit: at most MAX_PLAYS_PER_SECOND sounds per second, so a script cannot flood the server.
 */
public class SpeakerBlockEntity extends BlockEntity {
    public static final int FE_PER_SECOND = 5;
    public static final int MAX_PLAYS_PER_SECOND = 10;
    private static final int INTERVAL_TICKS = 20;

    private boolean powered;
    private int tickCount;
    private int playsThisSecond;
    /** The media file this speaker is playing for the players (null = none). Not saved. */
    private String mediaId;
    private int lastMediaTick = -100;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    public boolean isPowered() {
        return powered;
    }

    /** Plays a sound at the speaker. Volume 0 to 2, pitch 0.5 to 2. Returns false if it did not play (no FE, rate limit). */
    public boolean play(String name, double volume, double pitch) {
        if (level == null || level.isClientSide) {
            return false;
        }
        String full = name.contains(":") ? name : "minecraft:" + name;
        ResourceLocation id = ResourceLocation.tryParse(full);
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) {
            throw new IllegalArgumentException("Unknown sound: " + name);
        }
        if (!powered || playsThisSecond >= MAX_PLAYS_PER_SECOND) {
            return false;
        }
        playsThisSecond++;
        float v = (float) Math.max(0.0, Math.min(2.0, volume));
        float p = (float) Math.max(0.5, Math.min(2.0, pitch));
        level.playSound(null, worldPosition, sound, SoundSource.RECORDS, v, p);
        return true;
    }

    /**
     * Plays a stored audio file (its media store id) for the players near the speaker. Returns false if it did not
     * start (no FE, or started again less than a second after the last time).
     */
    public boolean playMedia(String id, double volume, boolean loop) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!powered || tickCount - lastMediaTick < INTERVAL_TICKS) {
            return false;
        }
        lastMediaTick = tickCount;
        mediaId = id;
        MediaTransfer.play(serverLevel, worldPosition, id, (float) Math.max(0.0, Math.min(2.0, volume)), loop);
        return true;
    }

    public void stopMedia() {
        if (mediaId != null && level instanceof ServerLevel serverLevel) {
            MediaTransfer.stop(serverLevel, worldPosition);
        }
        mediaId = null;
    }

    @Override
    public void setRemoved() {
        stopMedia();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        stopMedia();
        super.onChunkUnloaded();
    }

    /** Called every server tick by the block. */
    public void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        tickCount++;
        if (tickCount != 1 && tickCount % INTERVAL_TICKS != 0) {
            return;
        }
        playsThisSecond = 0;
        powered = PowerNet.pull(level, worldPosition, FE_PER_SECOND);
        if (!powered) {
            stopMedia(); // no FE: the music stops
        }
    }
}

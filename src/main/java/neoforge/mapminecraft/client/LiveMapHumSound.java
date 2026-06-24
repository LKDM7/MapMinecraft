package neoforge.mapminecraft.client;

import neoforge.mapminecraft.block.LiveMapBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A soft looping hum tied to one projector. It stops itself (and runs {@code onStop}) when the
 * projector is gone, has no data, or the player walks out of range — so the renderer can restart it
 * when the player returns.
 */
public class LiveMapHumSound extends AbstractTickableSoundInstance {

    private static final double RANGE = 24.0;

    private final BlockPos pos;
    private final Runnable onStop;

    public LiveMapHumSound(BlockPos pos, Runnable onStop) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, RandomSource.create());
        this.pos = pos;
        this.onStop = onStop;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.volume = 0.22F;
        this.pitch = 1.5F;
        this.looping = true;
        this.delay = 0;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean active = mc.level != null
                && mc.level.getBlockEntity(pos) instanceof LiveMapBlockEntity be
                && !be.getData().isEmpty()
                && mc.player != null
                && mc.player.blockPosition().distSqr(pos) < RANGE * RANGE;
        if (!active) {
            stop();
            onStop.run();
        }
    }
}

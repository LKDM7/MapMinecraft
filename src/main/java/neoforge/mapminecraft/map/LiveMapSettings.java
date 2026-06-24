package neoforge.mapminecraft.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Per-projector settings, editable in-game through the projector's GUI. Stored on the block entity,
 * saved to NBT and synced to clients (server scans with {@link #radius}/{@link #interval} and drives
 * the redstone radar with {@link #redstone}/{@link #detectMode}; the renderer uses the rest).
 */
public record LiveMapSettings(int radius, int interval, float scale, float hover,
                              boolean bob, boolean rotate, float rotationSpeed,
                              boolean redstone, int detectMode,
                              boolean useTarget, int targetX, int targetZ,
                              boolean showHologram) {

    public static final int MIN_RADIUS = 8, MAX_RADIUS = 128;
    // Floor the scan period at 60 ticks (3 s): scanning more often than this is the dominant
    // server-tick cost (a full volumetric re-read) and the main source of TPS lag.
    public static final int MIN_INTERVAL = 60, MAX_INTERVAL = 1200;
    public static final float MIN_SCALE = 0.25F, MAX_SCALE = 8.0F;
    public static final float MIN_HOVER = 0.0F, MAX_HOVER = 4.0F;
    public static final float MIN_ROT = 0.0F, MAX_ROT = 180.0F;

    /** Redstone radar detection modes. */
    public static final int DETECT_ALL = 0, DETECT_PLAYERS = 1, DETECT_HOSTILES = 2,
            DETECT_ANIMALS = 3, DETECT_ITEMS = 4, DETECT_COUNT = 5;

    public static final LiveMapSettings DEFAULT =
            new LiveMapSettings(32, 160, 2.0F, 0.55F, true, false, 12.0F, false, DETECT_ALL, false, 0, 0, true);

    public static final Codec<LiveMapSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("radius").forGetter(LiveMapSettings::radius),
            Codec.INT.fieldOf("interval").forGetter(LiveMapSettings::interval),
            Codec.FLOAT.fieldOf("scale").forGetter(LiveMapSettings::scale),
            Codec.FLOAT.fieldOf("hover").forGetter(LiveMapSettings::hover),
            Codec.BOOL.fieldOf("bob").forGetter(LiveMapSettings::bob),
            Codec.BOOL.fieldOf("rotate").forGetter(LiveMapSettings::rotate),
            Codec.FLOAT.fieldOf("rotationSpeed").forGetter(LiveMapSettings::rotationSpeed),
            Codec.BOOL.fieldOf("redstone").forGetter(LiveMapSettings::redstone),
            Codec.INT.fieldOf("detectMode").forGetter(LiveMapSettings::detectMode),
            Codec.BOOL.optionalFieldOf("useTarget", false).forGetter(LiveMapSettings::useTarget),
            Codec.INT.optionalFieldOf("targetX", 0).forGetter(LiveMapSettings::targetX),
            Codec.INT.optionalFieldOf("targetZ", 0).forGetter(LiveMapSettings::targetZ),
            Codec.BOOL.optionalFieldOf("showHologram", true).forGetter(LiveMapSettings::showHologram)
    ).apply(instance, LiveMapSettings::new));

    public static final StreamCodec<ByteBuf, LiveMapSettings> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                VarInt.write(buf, s.radius);
                VarInt.write(buf, s.interval);
                buf.writeFloat(s.scale);
                buf.writeFloat(s.hover);
                buf.writeBoolean(s.bob);
                buf.writeBoolean(s.rotate);
                buf.writeFloat(s.rotationSpeed);
                buf.writeBoolean(s.redstone);
                VarInt.write(buf, s.detectMode);
                buf.writeBoolean(s.useTarget);
                buf.writeInt(s.targetX);
                buf.writeInt(s.targetZ);
                buf.writeBoolean(s.showHologram);
            },
            buf -> new LiveMapSettings(VarInt.read(buf), VarInt.read(buf), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readBoolean(), VarInt.read(buf),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readBoolean()).sanitized()
    );

    /** Clamp every value into its valid range — never trust raw values off the network. */
    public LiveMapSettings sanitized() {
        return new LiveMapSettings(
                Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS),
                Mth.clamp(interval, MIN_INTERVAL, MAX_INTERVAL),
                Mth.clamp(scale, MIN_SCALE, MAX_SCALE),
                Mth.clamp(hover, MIN_HOVER, MAX_HOVER),
                bob,
                rotate,
                Mth.clamp(rotationSpeed, MIN_ROT, MAX_ROT),
                redstone,
                Mth.clamp(detectMode, 0, DETECT_COUNT - 1),
                useTarget, targetX, targetZ, showHologram
        );
    }

    public LiveMapSettings withRadius(int v) { return new LiveMapSettings(v, interval, scale, hover, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withInterval(int v) { return new LiveMapSettings(radius, v, scale, hover, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withScale(float v) { return new LiveMapSettings(radius, interval, v, hover, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withHover(float v) { return new LiveMapSettings(radius, interval, scale, v, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withBob(boolean v) { return new LiveMapSettings(radius, interval, scale, hover, v, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withRotate(boolean v) { return new LiveMapSettings(radius, interval, scale, hover, bob, v, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withRotationSpeed(float v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, v, redstone, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withRedstone(boolean v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, rotationSpeed, v, detectMode, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withDetectMode(int v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, rotationSpeed, redstone, v, useTarget, targetX, targetZ, showHologram); }
    public LiveMapSettings withUseTarget(boolean v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, rotationSpeed, redstone, detectMode, v, targetX, targetZ, showHologram); }
    public LiveMapSettings withTargetX(int v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, v, targetZ, showHologram); }
    public LiveMapSettings withTargetZ(int v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, v, showHologram); }
    public LiveMapSettings withShowHologram(boolean v) { return new LiveMapSettings(radius, interval, scale, hover, bob, rotate, rotationSpeed, redstone, detectMode, useTarget, targetX, targetZ, v); }
}

package neoforge.mapminecraft.net;

import io.netty.buffer.ByteBuf;
import neoforge.mapminecraft.Mapminecraft;
import neoforge.mapminecraft.block.LiveMapBlockEntity;
import neoforge.mapminecraft.map.LiveMapSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent client → server when a player changes a projector's settings in its GUI. The server validates
 * the values, applies them to the block entity and re-syncs to everyone.
 */
public record UpdateSettingsPayload(BlockPos pos, LiveMapSettings settings) implements CustomPacketPayload {

    public static final Type<UpdateSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Mapminecraft.MODID, "update_settings"));

    public static final StreamCodec<ByteBuf, UpdateSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateSettingsPayload::pos,
            LiveMapSettings.STREAM_CODEC, UpdateSettingsPayload::settings,
            UpdateSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-side handler: apply the settings if the player is close enough to the projector. */
    public static void handle(UpdateSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.blockPosition().distSqr(payload.pos()) > 12 * 12) {
                return; // anti-cheat: must be near the block
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof LiveMapBlockEntity be) {
                be.applySettings(payload.settings().sanitized());
            }
        });
    }
}

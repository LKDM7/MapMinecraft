package neoforge.mapminecraft.net;

import io.netty.buffer.ByteBuf;
import neoforge.mapminecraft.Mapminecraft;
import neoforge.mapminecraft.block.LiveMapBlockEntity;
import neoforge.mapminecraft.map.LiveMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client with the scanned voxel snapshot for one projector. Carried as compact binary
 * (VarInt + RLE via {@link LiveMapData#STREAM_CODEC}) rather than NBT, so it bypasses the 2 MB NBT
 * accounter limit that the chunk packet imposes on {@code getUpdateTag} — the voxel band is far too
 * big to ride along inside the chunk packet.
 */
public record LiveMapDataPayload(BlockPos pos, LiveMapData data) implements CustomPacketPayload {

    public static final Type<LiveMapDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Mapminecraft.MODID, "live_map_data"));

    public static final StreamCodec<ByteBuf, LiveMapDataPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LiveMapDataPayload::pos,
            LiveMapData.STREAM_CODEC, LiveMapDataPayload::data,
            LiveMapDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client-side: hand the fresh snapshot to the projector block entity so it re-renders. */
    public static void handle(LiveMapDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.pos()) instanceof LiveMapBlockEntity be) {
                be.applyData(payload.data());
            }
        });
    }
}

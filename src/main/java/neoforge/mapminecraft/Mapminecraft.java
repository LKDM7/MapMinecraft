package neoforge.mapminecraft;

import com.mojang.logging.LogUtils;
import neoforge.mapminecraft.block.LiveMapBlock;
import neoforge.mapminecraft.block.LiveMapBlockEntity;
import neoforge.mapminecraft.client.LiveMapBlockEntityRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import neoforge.mapminecraft.net.UpdateSettingsPayload;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import neoforge.mapminecraft.net.LiveMapDataPayload;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Mapminecraft.MODID)
public class Mapminecraft {
    public static final String MODID = "mapminecraft";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    // The live map projector block: place it and it projects a floating 3D hologram of the terrain.
    public static final DeferredBlock<LiveMapBlock> LIVE_MAP_BLOCK = BLOCKS.registerBlock("live_map",
            LiveMapBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F).lightLevel(state -> 7).noOcclusion());
    public static final DeferredItem<BlockItem> LIVE_MAP_ITEM = ITEMS.registerSimpleBlockItem("live_map", LIVE_MAP_BLOCK);

    // The block entity that scans the terrain around the projector and syncs it to clients.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiveMapBlockEntity>> LIVE_MAP_BE =
            BLOCK_ENTITIES.register("live_map", () -> BlockEntityType.Builder.of(LiveMapBlockEntity::new, LIVE_MAP_BLOCK.get()).build(null));

    // The mod's creative tab.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("mapminecraft",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mapminecraft"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> LIVE_MAP_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(LIVE_MAP_ITEM.get()))
                    .build());

    public Mapminecraft(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        // Networking: the projector GUI sends setting changes to the server.
        modEventBus.addListener(this::registerPayloads);

        LOGGER.info("MapMinecraft loaded");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.STREAM_CODEC, UpdateSettingsPayload::handle)
                .playToClient(LiveMapDataPayload.TYPE, LiveMapDataPayload.STREAM_CODEC, LiveMapDataPayload::handle);
    }

    // Server-side game events (NeoForge bus).
    @EventBusSubscriber(modid = MODID)
    public static class ServerEvents {
        /**
         * When a chunk is sent to a player, push the current voxel snapshot for any projector it
         * contains, so the hologram appears immediately instead of waiting for the next periodic scan.
         */
        @SubscribeEvent
        public static void onChunkSent(ChunkWatchEvent.Sent event) {
            LevelChunk chunk = event.getChunk();
            for (var entry : chunk.getBlockEntities().entrySet()) {
                if (entry.getValue() instanceof LiveMapBlockEntity be && !be.getData().isEmpty()) {
                    PacketDistributor.sendToPlayer(event.getPlayer(),
                            new LiveMapDataPayload(entry.getKey(), be.getData()));
                }
            }
        }
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            // Render the floating 3D hologram above the live map projector block.
            event.registerBlockEntityRenderer(LIVE_MAP_BE.get(), LiveMapBlockEntityRenderer::new);
        }
    }
}

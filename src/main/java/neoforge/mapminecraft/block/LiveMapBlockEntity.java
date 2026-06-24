package neoforge.mapminecraft.block;

import neoforge.mapminecraft.Mapminecraft;
import neoforge.mapminecraft.map.LiveMapData;
import neoforge.mapminecraft.map.LiveMapSettings;
import neoforge.mapminecraft.net.LiveMapDataPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

/**
 * The block entity behind a live map projector. Server-side it periodically scans the terrain around
 * itself into a compact {@link LiveMapData} (surface height + block id per column) and syncs it to
 * nearby clients, which render the floating 3D hologram (see {@code LiveMapBlockEntityRenderer}).
 *
 * <p>Each projector carries its own {@link LiveMapSettings} (scan radius/interval + hologram look),
 * edited in-game through the projector's GUI.
 */
public class LiveMapBlockEntity extends BlockEntity {

    private static final String SETTINGS_KEY = "Settings";

    private LiveMapData data = LiveMapData.EMPTY;
    private LiveMapSettings settings = LiveMapSettings.DEFAULT;
    private int signal; // current redstone radar output (0-15), transient/recomputed
    private long ticks;
    private final int phase;

    // Scratch buffers reused across scans. The volumetric voxel array is ~67 MB at radius 128, and
    // re-allocating it on every scan (most of which find the terrain unchanged) was the dominant GC
    // pressure source. We scan into these, compare against the live snapshot, and only allocate a new
    // LiveMapData when something actually changed.
    private int[] scratchHeights = new int[0];
    private int[] scratchVoxels = new int[0];

    public LiveMapBlockEntity(BlockPos pos, BlockState state) {
        super(Mapminecraft.LIVE_MAP_BE.get(), pos, state);
        // Spread scans across projectors so they don't all fire on the same tick.
        this.phase = Math.floorMod((int) pos.asLong(), 160);
    }

    public LiveMapData getData() {
        return data;
    }

    public LiveMapSettings getSettings() {
        return settings;
    }

    /** World XZ the map is centred on (custom target when set, else the projector); Y is the projector's. */
    public BlockPos getScanCenter() {
        return settings.useTarget()
                ? new BlockPos(settings.targetX(), worldPosition.getY(), settings.targetZ())
                : worldPosition;
    }

    /** Current redstone radar output (read by the block's getSignal). */
    public int getSignal() {
        return signal;
    }

    /** Server: validate-and-apply new settings, immediately re-scan and sync to clients. */
    public void applySettings(LiveMapSettings newSettings) {
        this.settings = newSettings;
        if (level instanceof ServerLevel server) {
            // Skip (and clear) the terrain scan entirely while the hologram is switched off.
            this.data = settings.showHologram() ? scan(server) : LiveMapData.EMPTY;
            updateRedstone(server);
            setChanged();
            // Settings ride the (now tiny) block-entity update tag; the voxel data goes out-of-band.
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            syncData(server);
        }
    }

    /** Stream the current voxel snapshot to every client tracking this projector's chunk. */
    private void syncData(ServerLevel server) {
        PacketDistributor.sendToPlayersTrackingChunk(server, new ChunkPos(worldPosition),
                new LiveMapDataPayload(worldPosition, data));
    }

    /** Client: receive a fresh voxel snapshot (replaces the BE-sync path, which no longer carries data). */
    public void applyData(LiveMapData newData) {
        this.data = newData;
    }

    /** Count nearby entities matching the detect mode and emit a proportional redstone signal. */
    private void updateRedstone(ServerLevel server) {
        int newSignal = 0;
        if (settings.redstone()) {
            int radius = settings.radius();
            AABB box = new AABB(worldPosition).inflate(radius, radius, radius);
            newSignal = Math.min(15, server.getEntities((Entity) null, box, this::matches).size());
        }
        if (newSignal != signal) {
            signal = newSignal;
            setChanged();
            server.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private boolean matches(Entity e) {
        return switch (settings.detectMode()) {
            case LiveMapSettings.DETECT_PLAYERS -> e instanceof Player;
            case LiveMapSettings.DETECT_HOSTILES -> e instanceof Enemy;
            case LiveMapSettings.DETECT_ANIMALS -> e instanceof Animal;
            case LiveMapSettings.DETECT_ITEMS -> e instanceof ItemEntity;
            default -> e instanceof LivingEntity;
        };
    }

    /** Client: local preview while dragging sliders, before the change is sent to the server. */
    public void previewSettings(LiveMapSettings newSettings) {
        this.settings = newSettings;
    }

    /** Driven by the block's server ticker. */
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ticks++;

        // Radar detection runs more often than the terrain scan so redstone reacts quickly.
        if (ticks % 10 == 0) {
            updateRedstone(server);
        }

        // Hologram switched off: don't scan at all (saves the volumetric read), just the radar above.
        if (!settings.showHologram()) {
            return;
        }

        // Settle into the periodic rhythm, but scan quickly right after placement so it shows fast.
        // Never scan more often than MIN_INTERVAL, even on the post-placement "show it fast" path.
        boolean due = (ticks + phase) % settings.interval() == 0
                || (data.isEmpty() && ticks % LiveMapSettings.MIN_INTERVAL == 0);
        if (!due) {
            return;
        }

        LiveMapData scanned = scan(server);
        if (scanned == data) {
            return; // terrain unchanged → scan() handed back the same snapshot, nothing to re-sync
        }
        data = scanned;
        setChanged();
        // Data-only change: no block update needed, just stream the new snapshot to trackers.
        syncData(server);
    }

    // Vertical band captured per column, anchored to the surface: blocks from surfaceY+FLOOR up to
    // surfaceY+FLOOR+BAND-1. FLOOR is negative so a few solid blocks below the surface give the
    // diorama thickness; the band reaches 256 blocks up to capture tall mountains and builds.
    private static final int BAND = 256;
    private static final int FLOOR = -8;

    private LiveMapData scan(ServerLevel level) {
        int radius = settings.radius();
        int size = radius * 2;
        int cells = size * size;
        int volume = cells * BAND;
        // Reuse the scratch buffers; only (re)allocate when the radius changed. The voxel buffer must
        // be cleared each scan because we only write non-air voxels (and skip whole air sections), so
        // stale ids would otherwise linger. heights is fully overwritten per column below.
        if (scratchHeights.length != cells) {
            scratchHeights = new int[cells];
        }
        if (scratchVoxels.length != volume) {
            scratchVoxels = new int[volume];
        } else {
            Arrays.fill(scratchVoxels, 0);
        }
        int[] heights = scratchHeights;
        int[] voxels = scratchVoxels;
        // Scan around the custom target when set, otherwise around the projector itself.
        int centerX = settings.useTarget() ? settings.targetX() : worldPosition.getX();
        int centerZ = settings.useTarget() ? settings.targetZ() : worldPosition.getZ();
        int originX = centerX - radius;
        int originZ = centerZ - radius;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // Read straight from the chunk sections instead of level.getBlockState() per voxel: that
        // re-resolves chunk + section on every call, which is murderous with BAND=256. Here we fetch
        // each loaded chunk once (getChunkNow never force-loads) and skip whole 16-block sections that
        // are pure air in one step — the bulk of a tall band above the surface. Result is identical.
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int worldX = originX + dx;
                int worldZ = originZ + dz;
                int index = dz * size + dx;

                // MOTION_BLOCKING gives the surface to anchor the band on; skips tall grass/flowers.
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
                heights[index] = surfaceY;

                LevelChunk chunk = level.getChunkSource().getChunkNow(worldX >> 4, worldZ >> 4);
                if (chunk == null) {
                    continue; // not loaded → leave the whole column as air, never force-load
                }

                int base = index * BAND;
                int anchor = surfaceY + FLOOR; // world Y of voxel k=0
                int localX = worldX & 15;
                int localZ = worldZ & 15;
                int yMin = Math.max(minY, anchor);
                int yMax = Math.min(maxY - 1, anchor + BAND - 1);

                int worldY = yMin;
                while (worldY <= yMax) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(worldY));
                    int sectionTop = (worldY & ~15) + 15;        // last world Y in this section
                    int runEnd = Math.min(yMax, sectionTop);
                    if (section.hasOnlyAir()) {
                        worldY = runEnd + 1;                     // whole section is air → skip it
                        continue;
                    }
                    for (; worldY <= runEnd; worldY++) {
                        BlockState state = section.getBlockState(localX, worldY & 15, localZ);
                        if (!state.isAir()) {
                            voxels[base + (worldY - anchor)] = Block.getId(state);
                        }
                    }
                }
            }
        }

        // Change detection: if the freshly scanned content matches the live snapshot, hand back the
        // SAME object so callers skip the re-sync AND we avoid cloning ~67 MB for nothing. The
        // comparison cost is the same as the old scanned.equals(data); what we save is the allocation.
        if (!data.isEmpty()
                && data.width() == size && data.height() == size
                && data.band() == BAND && data.floor() == FLOOR
                && Arrays.equals(data.heights(), heights)
                && Arrays.equals(data.voxels(), voxels)) {
            return data;
        }
        // Terrain changed: hand off independent copies — the snapshot is shared with the renderer and
        // streamed to clients, so it must not alias the reusable scratch buffers.
        return new LiveMapData(size, size, BAND, FLOOR, heights.clone(), voxels.clone());
    }

    // NOTE: the voxel snapshot is intentionally NOT persisted or written to the update tag. It is
    // transient render data, fully recomputed by scan() on the server tick, and far too large to ride
    // inside the chunk packet (2 MB NBT limit). It is streamed to clients via LiveMapDataPayload.
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        LiveMapSettings.CODEC.encodeStart(NbtOps.INSTANCE, settings).result()
                .ifPresent(encoded -> tag.put(SETTINGS_KEY, encoded));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(SETTINGS_KEY)) {
            settings = LiveMapSettings.CODEC.parse(NbtOps.INSTANCE, tag.get(SETTINGS_KEY)).result()
                    .orElse(LiveMapSettings.DEFAULT).sanitized();
        } else {
            settings = LiveMapSettings.DEFAULT;
        }
        data = LiveMapData.EMPTY; // rebuilt by the next server scan, then streamed to clients
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

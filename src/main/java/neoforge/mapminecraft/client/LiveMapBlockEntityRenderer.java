package neoforge.mapminecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import neoforge.mapminecraft.block.LiveMapBlockEntity;
import neoforge.mapminecraft.map.LiveMapData;
import neoforge.mapminecraft.map.LiveMapSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a floating, glowing 3D voxel hologram of the scanned terrain above a live map projector
 * block. Voxels are textured with the real block textures (block atlas) and biome tint, lit with
 * directional shading and ambient occlusion. The map plane is horizontal (XZ) and the relief rises
 * along +Y — the natural top-down viewing orientation.
 */
public class LiveMapBlockEntityRenderer implements BlockEntityRenderer<LiveMapBlockEntity> {

    // Block atlas, no culling, entity format (position, colour, uv, overlay, lightmap, normal).
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    // Translucent pass for water, drawn after the opaque mesh so the lake bed shows through it.
    // No-cull variant (like the opaque pass) so face winding can't make the water disappear.
    private static final RenderType WATER_RENDER_TYPE = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
    private static final RandomSource RANDOM = RandomSource.create();
    private static TextureAtlasSprite solidSprite; // lazily resolved from the block atlas
    private static TextureAtlasSprite waterSprite;  // lazily resolved animated water texture
    private static final ResourceLocation WATER_STILL =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
    private static final int WATER_ALPHA = 0xCC; // ~80% opacity so water reads blue over the dark base
    private static final float WATER_LIGHTEN = 0.55F; // brighten the tint to offset the dark water texture

    /** Map footprint in local units before scale (1.0 = the whole map). One voxel is MAP_SPAN/size. */
    private static final float MAP_SPAN = 1.0F;
    private static final float BASE = 0.02F;

    private static final float SHADE_TOP = 1.0F;
    private static final float SHADE_NS = 0.8F;
    private static final float SHADE_EW = 0.6F;

    // Solid base plate under the terrain so the diorama sits on a clean pedestal (6 quads).
    private static final int BASE_QUADS = 6;
    private static final float BASE_THICK = 0.06F;
    private static final float BASE_OVERHANG = 0.035F;
    private static final int BASE_COLOR = 0xFF3A3F46; // projector metal
    private static final ResourceLocation BASE_SPRITE =
            ResourceLocation.fromNamespaceAndPath("mapminecraft", "block/live_map_side");
    // Opaque white sprite for flat-coloured voxels (water, leaves) that don't texture well as cubes.
    private static final ResourceLocation HOLO_SOLID =
            ResourceLocation.fromNamespaceAndPath("mapminecraft", "block/holo_solid");

    // The round, glowing projector lens drawn on top of the (cuboid) block body. The body itself is a
    // normal block model; only the lens is round, so we render it here as a low cylinder, full-bright.
    private static final int LENS_SIDES = 24;
    private static final float LENS_RADIUS = 4.5F / 16F; // matches the bezel ring in the item model
    private static final float LENS_Y_TOP = 8.0F / 16F;
    private static final float LENS_Y_BOTTOM = 6.0F / 16F;
    private static final float LENS_SPIN_DEG_PER_TICK = 1.5F; // ~30°/s scanner spin
    private static final ResourceLocation LENS_SPRITE_LOC =
            ResourceLocation.fromNamespaceAndPath("mapminecraft", "block/live_map_lens");
    private static TextureAtlasSprite lensSprite; // lazily resolved from the block atlas

    // Materialize animation + ambient hum, tracked per projector position (client-side).
    private static final float REVEAL_TICKS = 9.0F;
    private static final Map<Long, Long> REVEAL = new HashMap<>();
    private static final Set<Long> ACTIVE_HUMS = new HashSet<>();

    // Cache the uploaded GPU mesh per (snapshot set, master position). Evicted entries free their VertexBuffer.
    private static final int CACHE_MAX = 32;
    private static final Map<CompositeKey, GpuMesh> CACHE =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CompositeKey, GpuMesh> eldest) {
                    if (size() > CACHE_MAX) {
                        eldest.getValue().close();
                        return true;
                    }
                    return false;
                }
            };

    public LiveMapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Keep rendering the floating hologram even when the projector block itself is off-screen. */
    @Override
    public boolean shouldRenderOffScreen(LiveMapBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(LiveMapBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // The physical round lens always shows, independent of whether terrain has been scanned yet.
        renderLens(be, partialTick, poseStack, buffer);

        // Hologram switched off in the GUI: keep the lens, skip the floating map entirely.
        if (!be.getSettings().showHologram()) {
            return;
        }

        if (be.getLevel() == null) {
            return;
        }

        // Effective scan centre (custom target or the projector) so biome tint is sampled at the
        // mapped location, not the block. The hologram still renders above the projector itself.
        BlockPos center = be.getScanCenter();
        // A standalone projector renders just its own scan; a master also folds in every linked
        // projector's scan, stitched together in world coordinates into one larger diorama.
        List<Grid> grids = gatherGrids(be, center);
        if (grids.isEmpty()) {
            return; // nothing scanned yet (here or on any linked projector)
        }
        CompositeKey key = new CompositeKey(grids, center.asLong());
        GpuMesh mesh = CACHE.get(key);
        if (mesh == null) {
            mesh = buildGpu(bake(grids, be.getLevel(), center));
            CACHE.put(key, mesh);
        }
        if (mesh.buffer == null && mesh.water == null) {
            return; // nothing to draw
        }

        LiveMapSettings settings = be.getSettings();
        long gameTime = be.getLevel().getGameTime();

        // First time we render this projector: trigger the materialize animation, sound and hum.
        long posKey = center.asLong();
        Long start = REVEAL.get(posKey);
        if (start == null) {
            REVEAL.put(posKey, gameTime);
            start = gameTime;
            onReveal(be, center);
        }
        float p = Mth.clamp(((float) (gameTime - start) + partialTick) / REVEAL_TICKS, 0F, 1F);
        float reveal = easeOutBack(p); // 0 → pops slightly past 1 → settles at 1

        float bob = settings.bob()
                ? Mth.sin(((float) (gameTime % 24000L) + partialTick) * 0.05F) * 0.05F
                : 0F;
        float scale = settings.scale() * reveal;

        poseStack.pushPose();
        poseStack.translate(0.5F, settings.hover() + bob, 0.5F); // centre above the block
        poseStack.scale(scale, scale, scale);
        if (settings.rotate()) {
            double degPerSec = settings.rotationSpeed();
            float angle = (float) (((double) gameTime * degPerSec / 20.0 + partialTick * degPerSec / 20.0) % 360.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        }
        // The BER pose is camera-POSITION-relative but lacks the camera ROTATION, which lives in
        // RenderSystem's model-view. Combine them, else the hologram won't track the world (it follows
        // the camera). Final model-view = cameraView × blockPose.
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelView.mul(poseStack.last().pose());
        poseStack.popPose();

        draw(mesh.buffer, RENDER_TYPE, modelView);   // opaque terrain first
        draw(mesh.water, WATER_RENDER_TYPE, modelView); // then translucent water over it
    }

    /** Draw one cached GPU buffer through the given render type at the given model-view matrix. */
    private static void draw(VertexBuffer buffer, RenderType renderType, Matrix4f modelView) {
        if (buffer == null) {
            return;
        }
        renderType.setupRenderState();
        buffer.bind();
        buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    /** Play the materialize sound and start the ambient hum the first time a projector appears. */
    private static void onReveal(LiveMapBlockEntity be, BlockPos pos) {
        Level level = be.getLevel();
        if (level != null) {
            level.playLocalSound(pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.7F, false);
        }
        long key = pos.asLong();
        if (ACTIVE_HUMS.add(key)) {
            Minecraft.getInstance().getSoundManager().play(new LiveMapHumSound(pos, () -> ACTIVE_HUMS.remove(key)));
        }
    }

    /** Ease-out "back" — overshoots slightly past 1 then settles, giving a satisfying pop. */
    private static float easeOutBack(float p) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float t = p - 1.0F;
        return 1.0F + c3 * t * t * t + c1 * t * t;
    }

    /** Upload the opaque + water meshes to GPU vertex buffers once; per-frame we only re-issue the draw. */
    private static GpuMesh buildGpu(BakedScene scene) {
        return new GpuMesh(upload(scene.opaque()), upload(scene.water()));
    }

    /** Upload a single baked mesh to a GPU vertex buffer (null when empty). */
    private static VertexBuffer upload(BakedMesh mesh) {
        if (mesh.quadCount() == 0) {
            return null;
        }
        float[] pos = mesh.pos();
        float[] nor = mesh.nor();
        float[] uv = mesh.uv();
        int[] col = mesh.col();
        int light = LightTexture.FULL_BRIGHT; // hologram glow, independent of ambient light

        try (ByteBufferBuilder bytes = new ByteBufferBuilder(mesh.quadCount() * 4 * DefaultVertexFormat.NEW_ENTITY.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            for (int q = 0; q < mesh.quadCount(); q++) {
                int nb = q * 3;
                float nx = nor[nb], ny = nor[nb + 1], nz = nor[nb + 2];
                int base = q * 12;
                int cbase = q * 4;
                int ubase = q * 8;
                for (int v = 0; v < 4; v++) {
                    int p = base + v * 3;
                    builder.addVertex(pos[p], pos[p + 1], pos[p + 2])
                            .setColor(col[cbase + v])
                            .setUv(uv[ubase + v * 2], uv[ubase + v * 2 + 1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(nx, ny, nz);
                }
            }
            MeshData meshData = builder.build();
            if (meshData == null) {
                return null;
            }
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(meshData); // consumes meshData
            VertexBuffer.unbind();
            return vbo;
        }
    }

    // ----- baking -------------------------------------------------------------------------------

    /**
     * Collect the grids that feed this projector's hologram: its own scan first, then every linked
     * projector's scan (skipping any that haven't been scanned/synced yet). Each grid carries its own
     * world scan centre so the bake can stitch them in world space.
     */
    private static List<Grid> gatherGrids(LiveMapBlockEntity be, BlockPos center) {
        List<Grid> grids = new ArrayList<>();
        LiveMapData own = be.getData();
        if (!own.isEmpty()) {
            grids.add(new Grid(own, center.getX(), center.getZ()));
        }
        Level level = be.getLevel();
        if (level != null) {
            for (BlockPos link : be.getLinks()) {
                if (level.getBlockEntity(link) instanceof LiveMapBlockEntity slave) {
                    LiveMapData d = slave.getData();
                    if (!d.isEmpty()) {
                        BlockPos sc = slave.getScanCenter();
                        grids.add(new Grid(d, sc.getX(), sc.getZ()));
                    }
                }
            }
        }
        return grids;
    }

    private static BakedScene bake(List<Grid> grids, BlockAndTintGetter level, BlockPos master) {
        // All grids share one world cell scale (set by the master's own resolution) so they stitch
        // seamlessly regardless of each projector's individual scan radius.
        float cell = MAP_SPAN / grids.get(0).data().width();
        boolean single = grids.size() == 1;
        int masterX = master.getX();
        int masterZ = master.getZ();

        // Lowest filled world Y across every grid anchors the whole diorama at a common base level.
        int globalMinY = Integer.MAX_VALUE;
        int opaqueQuads = single ? BASE_QUADS : 0;
        int waterQuads = 0;
        for (Grid g : grids) {
            LiveMapData d = g.data();
            globalMinY = Math.min(globalMinY, gridMinY(d));
            opaqueQuads += countFaces(d.width(), d.band(), d.floor(), d.heights(), d.voxels(), false);
            waterQuads += countFaces(d.width(), d.band(), d.floor(), d.heights(), d.voxels(), true);
        }
        if (globalMinY == Integer.MAX_VALUE) {
            globalMinY = 0;
        }

        BakedMesh opaque = allocMesh(opaqueQuads);
        BakedMesh water = allocMesh(waterQuads);
        Cursor co = opaque.cursor();
        Cursor cw = water.cursor();
        for (Grid g : grids) {
            LiveMapData d = g.data();
            fillVoxels(co, cw, d.width(), d.band(), d.floor(), cell, globalMinY, d.heights(), d.voxels(),
                    level, g.centerX(), g.centerZ(), masterX, masterZ);
        }
        // The pedestal only makes sense under a single map; a composite floats as a stitched diorama.
        if (single) {
            emitBase(co);
        }
        return new BakedScene(opaque, water);
    }

    /** Lowest filled world Y in one grid, or {@link Integer#MAX_VALUE} if it is entirely air. */
    private static int gridMinY(LiveMapData data) {
        int size = data.width();
        int band = data.band();
        int floor = data.floor();
        int[] heights = data.heights();
        int[] voxels = data.voxels();
        int minY = Integer.MAX_VALUE;
        for (int i = 0; i < size * size; i++) {
            int base = i * band;
            for (int k = 0; k < band; k++) {
                if (voxels[base + k] != 0) {
                    int wy = heights[i] + floor + k;
                    if (wy < minY) minY = wy;
                    break;
                }
            }
        }
        return minY;
    }

    private static BakedMesh allocMesh(int quads) {
        return new BakedMesh(new float[quads * 12], new float[quads * 3],
                new float[quads * 8], new int[quads * 4], quads);
    }

    // Per block-state-id classification, cached so the hot mesh loops don't re-resolve the block
    // state + fluid state ~12x per voxel (each neighbour occlusion test) across millions of voxels per
    // rebuild — that lookup was the dominant bake cost at large radius. The block registry is fixed
    // after load, so the mapping is stable for the whole session; the cache is render-thread only.
    private static final byte CLASS_UNKNOWN = -1;
    private static final byte CLASS_AIR = 0;
    private static final byte CLASS_OPAQUE = 1;
    private static final byte CLASS_WATER = 2;
    private static final byte CLASS_LEAF = 3;
    private static byte[] classCache;

    private static byte classOf(int id) {
        byte[] c = classCache;
        if (c == null || id >= c.length) {
            int len = Math.max(id + 1, Block.BLOCK_STATE_REGISTRY.size());
            byte[] grown = new byte[len];
            java.util.Arrays.fill(grown, CLASS_UNKNOWN);
            if (c != null) {
                System.arraycopy(c, 0, grown, 0, c.length);
            }
            classCache = c = grown;
        }
        byte v = c[id];
        if (v == CLASS_UNKNOWN) {
            v = computeClass(id);
            c[id] = v;
        }
        return v;
    }

    private static byte computeClass(int id) {
        if (id == 0) {
            return CLASS_AIR;
        }
        BlockState state = Block.stateById(id);
        if (state.isAir()) {
            return CLASS_AIR;
        }
        var fluid = state.getFluidState();
        if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
            return CLASS_WATER;
        }
        if (state.getBlock() instanceof LeavesBlock) {
            return CLASS_LEAF;
        }
        return CLASS_OPAQUE;
    }

    private static boolean isLeaf(int blockId) {
        return classOf(blockId) == CLASS_LEAF;
    }

    private static boolean isWaterId(int blockId) {
        return classOf(blockId) == CLASS_WATER;
    }

    /** Block id of the voxel at footprint cell (dx,dz) and world Y {@code wy}; 0 (air) if out of range. */
    private static int voxelAt(int size, int band, int floor, int[] heights, int[] voxels, int dx, int dz, int wy) {
        if (dx < 0 || dz < 0 || dx >= size || dz >= size) {
            return 0; // outside the footprint → air, so edge faces show
        }
        int i = dz * size + dx;
        int k = wy - (heights[i] + floor);
        if (k < 0 || k >= band) {
            return 0;
        }
        return voxels[i * band + k];
    }

    /** A neighbour occludes water faces if it is any non-air voxel. */
    private static boolean solidAt(int size, int band, int floor, int[] heights, int[] voxels,
                                   int dx, int dz, int wy) {
        return voxelAt(size, band, floor, heights, voxels, dx, dz, wy) != 0;
    }

    /** A neighbour occludes opaque faces only if it is itself opaque — water never hides terrain. */
    private static boolean opaqueAt(int size, int band, int floor, int[] heights, int[] voxels,
                                    int dx, int dz, int wy) {
        byte cl = classOf(voxelAt(size, band, floor, heights, voxels, dx, dz, wy));
        return cl == CLASS_OPAQUE || cl == CLASS_LEAF;
    }

    /**
     * Exposed-face count for one pass (must match {@link #fillVoxels}). {@code wantWater} selects the
     * water voxels (single pass, occluded by any solid neighbour) vs the opaque ones (leaves count
     * double, occluded only by opaque neighbours so faces under water still draw).
     */
    private static int countFaces(int size, int band, int floor, int[] heights, int[] voxels, boolean wantWater) {
        int quads = 0;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int i = dz * size + dx;
                int base = i * band;
                for (int k = 0; k < band; k++) {
                    int id = voxels[base + k];
                    if (id == 0 || isWaterId(id) != wantWater) {
                        continue;
                    }
                    int wy = heights[i] + floor + k;
                    int faces = 0;
                    if (!occ(wantWater, size, band, floor, heights, voxels, dx, dz, wy + 1)) faces++;
                    if (!occ(wantWater, size, band, floor, heights, voxels, dx, dz, wy - 1)) faces++;
                    if (!occ(wantWater, size, band, floor, heights, voxels, dx - 1, dz, wy)) faces++;
                    if (!occ(wantWater, size, band, floor, heights, voxels, dx + 1, dz, wy)) faces++;
                    if (!occ(wantWater, size, band, floor, heights, voxels, dx, dz - 1, wy)) faces++;
                    if (!occ(wantWater, size, band, floor, heights, voxels, dx, dz + 1, wy)) faces++;
                    quads += faces * (!wantWater && isLeaf(id) ? 2 : 1);
                }
            }
        }
        return quads;
    }

    /** Occlusion test for a voxel of the given pass: water culls vs any solid, opaque vs opaque only. */
    private static boolean occ(boolean water, int size, int band, int floor, int[] heights, int[] voxels,
                               int dx, int dz, int wy) {
        return water
                ? solidAt(size, band, floor, heights, voxels, dx, dz, wy)
                : opaqueAt(size, band, floor, heights, voxels, dx, dz, wy);
    }

    /**
     * Emit a cube per non-air voxel, drawing only exposed faces. Opaque voxels go to {@code co};
     * water voxels go to the translucent cursor {@code cw} with the animated water sprite.
     */
    private static void fillVoxels(Cursor co, Cursor cw, int size, int band, int floor, float cell, int minY,
                                   int[] heights, int[] voxels, BlockAndTintGetter level,
                                   int centerX, int centerZ, int masterCenterX, int masterCenterZ) {
        Map<Integer, BlockTex> texCache = new HashMap<>();
        TextureAtlasSprite solid = solidSprite();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        int originX = centerX - size / 2;
        int originZ = centerZ - size / 2;
        float eps = cell * 0.04F;

        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int i = dz * size + dx;
                int base = i * band;
                for (int k = 0; k < band; k++) {
                    int id = voxels[base + k];
                    if (id == 0) {
                        continue;
                    }
                    int wy = heights[i] + floor + k;
                    BlockState state = Block.stateById(id);
                    worldPos.set(originX + dx, wy, originZ + dz);

                    // Per-block category: water → translucent cursor + animated water sprite; leaves →
                    // solid backing + real leaf overlay; everything else → real sprite resolved per face.
                    boolean water = isWaterId(id);
                    boolean leaf = isLeaf(id);
                    Cursor vc = water ? cw : co;
                    TextureAtlasSprite faceSolid = water ? waterSprite() : solid;
                    int waterColor = water
                            ? (WATER_ALPHA << 24) | lighten(BiomeColors.getAverageWaterColor(level, worldPos), WATER_LIGHTEN) : 0;
                    int foliage = leaf ? tint(state, 0, level, worldPos) : 0;
                    TextureAtlasSprite leafSprite = leaf
                            ? texCache.computeIfAbsent(id * 6 + Direction.UP.get3DDataValue(),
                                    x -> resolveFace(state, Direction.UP)).sprite() : null;

                    float x0 = (originX + dx - masterCenterX) * cell;
                    float x1 = x0 + cell;
                    float z0 = (originZ + dz - masterCenterZ) * cell;
                    float z1 = z0 + cell;
                    float yb = BASE + (wy - minY) * cell;
                    float yt = yb + cell;

                    if (!occ(water, size, band, floor, heights, voxels, dx, dz, wy + 1)) {
                        emitVoxelFace(vc, state, level, worldPos, texCache, id, water, waterColor, leaf, leafSprite, foliage, faceSolid, eps,
                                Direction.UP, SHADE_TOP, x0, yt, z0, x1, yt, z0, x1, yt, z1, x0, yt, z1, 0F, 1F, 0F);
                    }
                    if (!occ(water, size, band, floor, heights, voxels, dx, dz, wy - 1)) {
                        emitVoxelFace(vc, state, level, worldPos, texCache, id, water, waterColor, leaf, leafSprite, foliage, faceSolid, eps,
                                Direction.DOWN, 0.45F, x0, yb, z1, x1, yb, z1, x1, yb, z0, x0, yb, z0, 0F, -1F, 0F);
                    }
                    if (!occ(water, size, band, floor, heights, voxels, dx - 1, dz, wy)) {
                        emitVoxelFace(vc, state, level, worldPos, texCache, id, water, waterColor, leaf, leafSprite, foliage, faceSolid, eps,
                                Direction.WEST, SHADE_EW, x0, yb, z0, x0, yb, z1, x0, yt, z1, x0, yt, z0, -1F, 0F, 0F);
                    }
                    if (!occ(water, size, band, floor, heights, voxels, dx + 1, dz, wy)) {
                        emitVoxelFace(vc, state, level, worldPos, texCache, id, water, waterColor, leaf, leafSprite, foliage, faceSolid, eps,
                                Direction.EAST, SHADE_EW, x1, yb, z1, x1, yb, z0, x1, yt, z0, x1, yt, z1, 1F, 0F, 0F);
                    }
                    if (!occ(water, size, band, floor, heights, voxels, dx, dz - 1, wy)) {
                        emitVoxelFace(vc, state, level, worldPos, texCache, id, water, waterColor, leaf, leafSprite, foliage, faceSolid, eps,
                                Direction.NORTH, SHADE_NS, x1, yb, z0, x0, yb, z0, x0, yt, z0, x1, yt, z0, 0F, 0F, -1F);
                    }
                    if (!occ(water, size, band, floor, heights, voxels, dx, dz + 1, wy)) {
                        emitVoxelFace(vc, state, level, worldPos, texCache, id, water, waterColor, leaf, leafSprite, foliage, faceSolid, eps,
                                Direction.SOUTH, SHADE_NS, x0, yb, z1, x1, yb, z1, x1, yt, z1, x0, yt, z1, 0F, 0F, 1F);
                    }
                }
            }
        }
    }

    /** A solid pedestal under the relief, slightly overhanging, textured with the projector metal. */
    private static void emitBase(Cursor c) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(BASE_SPRITE);
        float h = 0.5F + BASE_OVERHANG;
        float x0 = -h, x1 = h, z0 = -h, z1 = h;
        float yt = 0F, yb = -BASE_THICK;
        int top = mul(BASE_COLOR, SHADE_TOP);
        int bottom = mul(BASE_COLOR, 0.45F);
        int ew = mul(BASE_COLOR, SHADE_EW);
        int ns = mul(BASE_COLOR, SHADE_NS);
        c.quad(x0, yt, z0, x1, yt, z0, x1, yt, z1, x0, yt, z1, 0F, 1F, 0F, sprite, top, top, top, top);          // top
        c.quad(x0, yb, z1, x1, yb, z1, x1, yb, z0, x0, yb, z0, 0F, -1F, 0F, sprite, bottom, bottom, bottom, bottom); // bottom
        c.quad(x0, yb, z0, x0, yb, z1, x0, yt, z1, x0, yt, z0, -1F, 0F, 0F, sprite, ew, ew, ew, ew);              // west
        c.quad(x1, yb, z1, x1, yb, z0, x1, yt, z0, x1, yt, z1, 1F, 0F, 0F, sprite, ew, ew, ew, ew);               // east
        c.quad(x1, yb, z0, x0, yb, z0, x0, yt, z0, x1, yt, z0, 0F, 0F, -1F, sprite, ns, ns, ns, ns);              // north
        c.quad(x0, yb, z1, x1, yb, z1, x1, yt, z1, x0, yt, z1, 0F, 0F, 1F, sprite, ns, ns, ns, ns);               // south
    }

    /**
     * Emit one voxel face. For leaves, draws the solid foliage backing first, then the real
     * leaf sprite pushed out by {@code eps} along the normal — texture detail, no cutout holes,
     * and a slightly puffed canopy silhouette.
     */
    private static void faceQuad(Cursor c, boolean leaf, TextureAtlasSprite solid, TextureAtlasSprite leafSprite,
                                 float eps,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3,
                                 float nx, float ny, float nz, int c0, int c1, int c2, int c3) {
        c.quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, nx, ny, nz, solid, c0, c1, c2, c3);
        if (leaf && leafSprite != null) {
            c.quad(x0 + nx * eps, y0 + ny * eps, z0 + nz * eps,
                    x1 + nx * eps, y1 + ny * eps, z1 + nz * eps,
                    x2 + nx * eps, y2 + ny * eps, z2 + nz * eps,
                    x3 + nx * eps, y3 + ny * eps, z3 + nz * eps,
                    nx, ny, nz, leafSprite, c0, c1, c2, c3);
        }
    }

    /** Resolve a single voxel face's sprite + colour and emit it (water/leaves use the solid sprite). */
    private static void emitVoxelFace(Cursor c, BlockState state, BlockAndTintGetter level, BlockPos pos,
                                      Map<Integer, BlockTex> faceCache, int id, boolean water, int waterColor,
                                      boolean leaf, TextureAtlasSprite leafSprite, int foliage,
                                      TextureAtlasSprite solid, float eps, Direction dir, float shade,
                                      float x0, float y0, float z0, float x1, float y1, float z1,
                                      float x2, float y2, float z2, float x3, float y3, float z3,
                                      float nx, float ny, float nz) {
        TextureAtlasSprite sprite;
        int color;
        if (water) {
            sprite = solid;
            color = mul(waterColor, shade);
        } else if (leaf) {
            sprite = solid; // solid backing; faceQuad overlays the real leaf sprite
            color = mul(foliage, shade);
        } else {
            BlockTex ft = faceCache.computeIfAbsent(id * 6 + dir.get3DDataValue(), key -> resolveFace(state, dir));
            sprite = ft.sprite();
            color = mul(tint(state, ft.tintIndex(), level, pos), shade);
        }
        faceQuad(c, leaf, sprite, leafSprite, eps,
                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, nx, ny, nz, color, color, color, color);
    }

    private static TextureAtlasSprite solidSprite() {
        if (solidSprite == null) {
            solidSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(HOLO_SOLID);
        }
        return solidSprite;
    }

    private static TextureAtlasSprite lensSprite() {
        if (lensSprite == null) {
            lensSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(LENS_SPRITE_LOC);
        }
        return lensSprite;
    }

    /**
     * Draw the round glowing projector lens as a low cylinder (top cap + side wall) sitting on the
     * block's casing. Rendered full-bright through the standard buffer source so it reads as a lit lens.
     * No-cull render type means winding doesn't matter.
     */
    private void renderLens(LiveMapBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        TextureAtlasSprite sprite = lensSprite();
        VertexConsumer vc = buffer.getBuffer(RENDER_TYPE);

        // Spin the lens about its vertical axis so the scope grid sweeps like a scanner.
        Level level = be.getLevel();
        float spin = level != null
                ? (((float) (level.getGameTime() % 360000L) + partialTick) * LENS_SPIN_DEG_PER_TICK) % 360F
                : 0F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.translate(-0.5F, 0F, -0.5F);

        PoseStack.Pose pose = poseStack.last();
        int light = LightTexture.FULL_BRIGHT;
        int color = 0xFFFFFFFF;

        final float cx = 0.5F, cz = 0.5F, r = LENS_RADIUS;
        final float yt = LENS_Y_TOP, yb = LENS_Y_BOTTOM;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float uMid = (u0 + u1) * 0.5F, vMid = (v0 + v1) * 0.5F;
        float uHalf = (u1 - u0) * 0.5F, vHalf = (v1 - v0) * 0.5F;

        float[] cos = new float[LENS_SIDES + 1];
        float[] sin = new float[LENS_SIDES + 1];
        for (int i = 0; i <= LENS_SIDES; i++) {
            float a = (float) (i * 2.0 * Math.PI / LENS_SIDES);
            cos[i] = Mth.cos(a);
            sin[i] = Mth.sin(a);
        }

        // Top cap: a triangle fan emitted as degenerate quads (centre, p_i, p_{i+1}, p_{i+1}).
        for (int i = 0; i < LENS_SIDES; i++) {
            float x1 = cx + cos[i] * r,     z1 = cz + sin[i] * r;
            float x2 = cx + cos[i + 1] * r, z2 = cz + sin[i + 1] * r;
            float cu1 = uMid + cos[i] * uHalf,     cv1 = vMid + sin[i] * vHalf;
            float cu2 = uMid + cos[i + 1] * uHalf, cv2 = vMid + sin[i + 1] * vHalf;
            vertex(vc, pose, cx, yt, cz, color, uMid, vMid, light, 0F, 1F, 0F);
            vertex(vc, pose, x1, yt, z1, color, cu1, cv1, light, 0F, 1F, 0F);
            vertex(vc, pose, x2, yt, z2, color, cu2, cv2, light, 0F, 1F, 0F);
            vertex(vc, pose, x2, yt, z2, color, cu2, cv2, light, 0F, 1F, 0F);
        }

        // Side wall: one quad per segment, normal pointing radially outward.
        for (int i = 0; i < LENS_SIDES; i++) {
            float x1 = cx + cos[i] * r,     z1 = cz + sin[i] * r;
            float x2 = cx + cos[i + 1] * r, z2 = cz + sin[i + 1] * r;
            float su1 = u0 + (u1 - u0) * (i / (float) LENS_SIDES);
            float su2 = u0 + (u1 - u0) * ((i + 1) / (float) LENS_SIDES);
            float nx = (cos[i] + cos[i + 1]) * 0.5F, nz = (sin[i] + sin[i + 1]) * 0.5F;
            vertex(vc, pose, x1, yt, z1, color, su1, v0, light, nx, 0F, nz);
            vertex(vc, pose, x2, yt, z2, color, su2, v0, light, nx, 0F, nz);
            vertex(vc, pose, x2, yb, z2, color, su2, v1, light, nx, 0F, nz);
            vertex(vc, pose, x1, yb, z1, color, su1, v1, light, nx, 0F, nz);
        }

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               int color, float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(pose.pose(), x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static TextureAtlasSprite waterSprite() {
        if (waterSprite == null) {
            waterSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WATER_STILL);
        }
        return waterSprite;
    }

    /** Sprite + tint index for one face of a block's baked model (falls back to the particle icon). */
    private static BlockTex resolveFace(BlockState state, Direction dir) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RANDOM.setSeed(42L);
        List<BakedQuad> quads = model.getQuads(state, dir, RANDOM);
        if (!quads.isEmpty()) {
            BakedQuad quad = quads.get(0);
            return new BlockTex(quad.getSprite(), quad.getTintIndex());
        }
        RANDOM.setSeed(42L);
        List<BakedQuad> any = model.getQuads(state, null, RANDOM);
        if (!any.isEmpty()) {
            BakedQuad quad = any.get(0);
            return new BlockTex(quad.getSprite(), quad.getTintIndex());
        }
        return new BlockTex(model.getParticleIcon(), -1);
    }

    private static int tint(BlockState state, int tintIndex, BlockAndTintGetter level, BlockPos pos) {
        if (tintIndex < 0 || level == null) {
            return 0xFFFFFFFF;
        }
        int rgb = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, tintIndex);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Lerp an RGB colour toward white by {@code t} (0 = unchanged, 1 = white). Returns RGB only. */
    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int mul(int argb, float factor) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b; // keep input alpha (water is translucent)
    }

    /** One scan feeding a (possibly composite) hologram: the voxel snapshot plus its world scan centre. */
    private record Grid(LiveMapData data, int centerX, int centerZ) {
    }

    // Keyed by the IDENTITY of each data snapshot, not its contents: the server only ever hands us a
    // new LiveMapData object when the terrain actually changed (it skips re-syncing equal scans), so
    // reference equality is enough. Hashing the full voxel arrays here would run every frame the
    // hologram is on screen (CACHE.get is per-frame) and is the dominant cost of looking at the map.
    private static final class CompositeKey {
        private final LiveMapData[] datas;
        private final long center;
        private final int hash;

        CompositeKey(List<Grid> grids, long center) {
            this.datas = new LiveMapData[grids.size()];
            int h = Long.hashCode(center);
            for (int i = 0; i < datas.length; i++) {
                LiveMapData d = grids.get(i).data();
                datas[i] = d;
                h = h * 31 + System.identityHashCode(d);
            }
            this.center = center;
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CompositeKey other) || other.center != center
                    || other.datas.length != datas.length) {
                return false;
            }
            for (int i = 0; i < datas.length; i++) {
                if (datas[i] != other.datas[i]) { // reference identity: cheap and sufficient
                    return false;
                }
            }
            return true;
        }
    }

    private record BlockTex(TextureAtlasSprite sprite, int tintIndex) {
    }

    private record BakedMesh(float[] pos, float[] nor, float[] uv, int[] col, int quadCount) {
        Cursor cursor() {
            return new Cursor(pos, nor, uv, col);
        }
    }

    /** A baked diorama split into its opaque mesh and its translucent water mesh. */
    private record BakedScene(BakedMesh opaque, BakedMesh water) {
    }

    /** Holds the uploaded GPU buffers (null when a mesh is empty); closed on cache eviction. */
    private static final class GpuMesh {
        private final VertexBuffer buffer; // opaque
        private final VertexBuffer water;  // translucent

        GpuMesh(VertexBuffer buffer, VertexBuffer water) {
            this.buffer = buffer;
            this.water = water;
        }

        void close() {
            if (buffer != null) {
                buffer.close();
            }
            if (water != null) {
                water.close();
            }
        }
    }

    private static final class Cursor {
        private final float[] pos;
        private final float[] nor;
        private final float[] uv;
        private final int[] col;
        private int q = 0;

        Cursor(float[] pos, float[] nor, float[] uv, int[] col) {
            this.pos = pos;
            this.nor = nor;
            this.uv = uv;
            this.col = col;
        }

        void quad(float ax, float ay, float az, float bx, float by, float bz,
                  float cx, float cy, float cz, float dx, float dy, float dz,
                  float nx, float ny, float nz, TextureAtlasSprite sprite,
                  int colA, int colB, int colC, int colD) {
            int p = q * 12;
            pos[p] = ax; pos[p + 1] = ay; pos[p + 2] = az;
            pos[p + 3] = bx; pos[p + 4] = by; pos[p + 5] = bz;
            pos[p + 6] = cx; pos[p + 7] = cy; pos[p + 8] = cz;
            pos[p + 9] = dx; pos[p + 10] = dy; pos[p + 11] = dz;
            int nn = q * 3;
            nor[nn] = nx; nor[nn + 1] = ny; nor[nn + 2] = nz;
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
            int u = q * 8;
            uv[u] = u0; uv[u + 1] = v0;     // A
            uv[u + 2] = u1; uv[u + 3] = v0; // B
            uv[u + 4] = u1; uv[u + 5] = v1; // C
            uv[u + 6] = u0; uv[u + 7] = v1; // D
            int cc = q * 4;
            col[cc] = colA; col[cc + 1] = colB; col[cc + 2] = colC; col[cc + 3] = colD;
            q++;
        }
    }
}

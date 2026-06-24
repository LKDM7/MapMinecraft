package neoforge.mapminecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The live map projector block. Place it like any block; its {@link LiveMapBlockEntity} scans the
 * surrounding terrain and a renderer projects a floating 3D hologram above it.
 */
public class LiveMapBlock extends Block implements EntityBlock {

    public static final MapCodec<LiveMapBlock> CODEC = simpleCodec(LiveMapBlock::new);

    // Bottom-half slab shape (0..8 in Y).
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

    public LiveMapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends LiveMapBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            // Client-only screen opening, isolated in a client class so the server never loads it.
            neoforge.mapminecraft.client.LiveMapClient.openSettings(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof LiveMapBlockEntity be ? be.getSignal() : 0;
    }

    // Comparator output = the radar entity count (0-15).
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LiveMapBlockEntity be ? be.getSignal() : 0;
    }

    private static final DustParticleOptions AMBER_DUST =
            new DustParticleOptions(new org.joml.Vector3f(1.0F, 0.72F, 0.20F), 0.8F);

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Faint amber particles rising from the projector lens while it is active.
        if (level.getBlockEntity(pos) instanceof LiveMapBlockEntity be && !be.getData().isEmpty() && random.nextInt(3) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
            double y = pos.getY() + 0.55 + random.nextDouble() * 0.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
            level.addParticle(AMBER_DUST, x, y, z, 0.0, 0.03, 0.0);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiveMapBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the server scans; only our block entity attaches here, so the cast is safe.
        return level.isClientSide ? null : (lvl, pos, st, be) -> ((LiveMapBlockEntity) be).serverTick();
    }
}

package neoforge.mapminecraft.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Compact 3D voxel snapshot of the volume scanned by a live map.
 *
 * <p>The map captures a square {@code width x height} footprint. For every column we capture a
 * vertical band of {@link #band} blocks, anchored to that column's surface: voxel {@code k} sits at
 * world Y {@code heights[column] + floor + k}. The band runs from a few blocks below the surface
 * (for terrain thickness) to well above it (for trees and overhangs). Each voxel holds the
 * block-state id (from {@link net.minecraft.world.level.block.Block#getId}), {@code 0} = air.
 *
 * <p>{@link #voxels} is laid out as {@code [columnIndex * band + k]} where
 * {@code columnIndex = z * width + x}. The array is run-length encoded on the wire, which is tiny in
 * practice since the band is mostly air above the surface and a solid run below it.
 */
public record LiveMapData(int width, int height, int band, int floor, int[] heights, int[] voxels) {

    /** An empty, un-scanned map. */
    public static final LiveMapData EMPTY = new LiveMapData(0, 0, 0, 0, new int[0], new int[0]);

    public static final Codec<LiveMapData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("width").forGetter(LiveMapData::width),
            Codec.INT.fieldOf("height").forGetter(LiveMapData::height),
            Codec.INT.fieldOf("band").forGetter(LiveMapData::band),
            Codec.INT.fieldOf("floor").forGetter(LiveMapData::floor),
            Codec.INT_STREAM.fieldOf("heights").xmap(IntStream::toArray, IntStream::of).forGetter(LiveMapData::heights),
            Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of)
                    .xmap(LiveMapData::rleDecode, LiveMapData::rleEncode)
                    .fieldOf("voxels_rle").forGetter(LiveMapData::voxels)
    ).apply(instance, LiveMapData::new));

    public static final StreamCodec<ByteBuf, LiveMapData> STREAM_CODEC = StreamCodec.of(
            LiveMapData::encode,
            LiveMapData::decode
    );

    /** Run-length encode the voxel array as a flat [count, value, count, value, ...] int array. */
    private static int[] rleEncode(int[] voxels) {
        if (voxels.length == 0) {
            return new int[0];
        }
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        int run = 1;
        int prev = voxels[0];
        for (int i = 1; i < voxels.length; i++) {
            if (voxels[i] == prev) {
                run++;
            } else {
                out.add(run);
                out.add(prev);
                run = 1;
                prev = voxels[i];
            }
        }
        out.add(run);
        out.add(prev);
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    private static int[] rleDecode(int[] rle) {
        int total = 0;
        for (int i = 0; i < rle.length; i += 2) {
            total += rle[i];
        }
        int[] voxels = new int[total];
        int p = 0;
        for (int i = 0; i + 1 < rle.length; i += 2) {
            int count = rle[i];
            int value = rle[i + 1];
            for (int j = 0; j < count; j++) {
                voxels[p++] = value;
            }
        }
        return voxels;
    }

    private static void encode(ByteBuf buffer, LiveMapData data) {
        VarInt.write(buffer, data.width);
        VarInt.write(buffer, data.height);
        VarInt.write(buffer, data.band);
        VarInt.write(buffer, zigzag(data.floor));
        for (int value : data.heights) {
            VarInt.write(buffer, zigzag(value));
        }
        int[] rle = rleEncode(data.voxels);
        VarInt.write(buffer, rle.length);
        for (int value : rle) {
            VarInt.write(buffer, value);
        }
    }

    private static LiveMapData decode(ByteBuf buffer) {
        int width = VarInt.read(buffer);
        int height = VarInt.read(buffer);
        int band = VarInt.read(buffer);
        int floor = unzigzag(VarInt.read(buffer));
        int cells = width * height;
        int[] heights = new int[cells];
        for (int i = 0; i < cells; i++) {
            heights[i] = unzigzag(VarInt.read(buffer));
        }
        int rleLen = VarInt.read(buffer);
        int[] rle = new int[rleLen];
        for (int i = 0; i < rleLen; i++) {
            rle[i] = VarInt.read(buffer);
        }
        return new LiveMapData(width, height, band, floor, heights, rleDecode(rle));
    }

    private static int zigzag(int v) {
        return (v << 1) ^ (v >> 31);
    }

    private static int unzigzag(int v) {
        return (v >>> 1) ^ -(v & 1);
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0 || band <= 0;
    }

    /** Block id at footprint cell (x,z), band level k; 0 (air) for out-of-range. */
    public int voxel(int x, int z, int k) {
        if (x < 0 || z < 0 || x >= width || z >= height || k < 0 || k >= band) {
            return 0;
        }
        return voxels[(z * width + x) * band + k];
    }

    // records compare arrays by reference; override so identical snapshots are treated as equal,
    // which lets the client skip rebuilding its cached mesh when nothing actually changed.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LiveMapData other)) return false;
        return width == other.width
                && height == other.height
                && band == other.band
                && floor == other.floor
                && Arrays.equals(heights, other.heights)
                && Arrays.equals(voxels, other.voxels);
    }

    @Override
    public int hashCode() {
        int result = 31 * width + height;
        result = 31 * result + band;
        result = 31 * result + floor;
        result = 31 * result + Arrays.hashCode(heights);
        result = 31 * result + Arrays.hashCode(voxels);
        return result;
    }

    @Override
    public String toString() {
        return "LiveMapData[" + width + "x" + height + "x" + band + "]";
    }
}

package neoforge.mapminecraft.client;

import neoforge.mapminecraft.block.LiveMapBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points, kept out of common code so the dedicated server never loads them. */
public final class LiveMapClient {

    private LiveMapClient() {
    }

    /** Open the settings GUI for the projector at {@code pos}, if its block entity is present. */
    public static void openSettings(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof LiveMapBlockEntity be) {
            mc.setScreen(new LiveMapConfigScreen(be));
        }
    }
}

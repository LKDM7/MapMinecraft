package neoforge.mapminecraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import neoforge.mapminecraft.map.LiveMapSettings;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side, per-player preset slots for projector settings. Each slot stores a full
 * {@link LiveMapSettings} the player can re-apply to any projector, so they don't have to redo the
 * sliders every time. Saved as a small JSON file in the config directory; entirely client-side, so
 * presets travel with the player across worlds and servers rather than living on a block.
 *
 * <p>The location-specific scan-center fields ({@code useTarget}/{@code targetX}/{@code targetZ})
 * are intentionally not carried by a preset: applying a saved center to a projector somewhere else
 * makes no sense, so {@link #applyTo} keeps the current projector's center.
 */
public final class LiveMapPresets {

    public static final int SLOT_COUNT = 3;

    private static final Logger LOG = LoggerFactory.getLogger("mapminecraft");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mapminecraft-presets.json";

    /** null = empty slot. Indexed 0..SLOT_COUNT-1. */
    private static final LiveMapSettings[] SLOTS = new LiveMapSettings[SLOT_COUNT];
    private static boolean loaded;

    private LiveMapPresets() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            for (int i = 0; i < SLOT_COUNT; i++) {
                JsonElement el = root.get(String.valueOf(i + 1));
                if (el != null && el.isJsonObject()) {
                    SLOTS[i] = LiveMapSettings.CODEC.parse(JsonOps.INSTANCE, el)
                            .result().map(LiveMapSettings::sanitized).orElse(null);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to read projector presets from {}", path, e);
        }
    }

    private static void persist() {
        Path path = file();
        JsonObject root = new JsonObject();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (SLOTS[i] != null) {
                String key = String.valueOf(i + 1);
                LiveMapSettings.CODEC.encodeStart(JsonOps.INSTANCE, SLOTS[i])
                        .result().ifPresent(el -> root.add(key, el));
            }
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Failed to write projector presets to {}", path, e);
        }
    }

    /** True if slot {@code index} (0-based) holds saved settings. */
    public static boolean isFilled(int index) {
        ensureLoaded();
        return index >= 0 && index < SLOT_COUNT && SLOTS[index] != null;
    }

    /** Store {@code settings} into slot {@code index} (0-based) and persist to disk. */
    public static void save(int index, LiveMapSettings settings) {
        ensureLoaded();
        if (index < 0 || index >= SLOT_COUNT) {
            return;
        }
        SLOTS[index] = settings.sanitized();
        persist();
    }

    /**
     * Return {@code current} with the saved slot's settings applied, keeping the projector's own
     * scan-center fields. Returns {@code current} unchanged if the slot is empty.
     */
    public static LiveMapSettings applyTo(int index, LiveMapSettings current) {
        ensureLoaded();
        if (index < 0 || index >= SLOT_COUNT || SLOTS[index] == null) {
            return current;
        }
        LiveMapSettings p = SLOTS[index];
        return new LiveMapSettings(p.radius(), p.interval(), p.scale(), p.hover(), p.bob(), p.rotate(),
                p.rotationSpeed(), p.redstone(), p.detectMode(),
                current.useTarget(), current.targetX(), current.targetZ(),
                p.showHologram()).sanitized();
    }
}

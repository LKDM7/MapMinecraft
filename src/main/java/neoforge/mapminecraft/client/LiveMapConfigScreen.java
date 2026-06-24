package neoforge.mapminecraft.client;

import neoforge.mapminecraft.block.LiveMapBlockEntity;
import neoforge.mapminecraft.map.LiveMapSettings;
import neoforge.mapminecraft.net.UpdateSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.DoubleConsumer;

/**
 * In-game settings panel for a live map projector. Sliders/toggles update a working copy of the
 * settings and preview it live on the block entity; on close the result is sent to the server.
 *
 * <p>The panel sits on the left so the hologram stays visible on the right while tweaking.
 */
public class LiveMapConfigScreen extends Screen {

    private static final int PANEL_X = 14;
    private static final int PANEL_W = 210;
    private static final int ROW_H = 20;
    private static final int GAP = 24;

    private final LiveMapBlockEntity be;
    private LiveMapSettings working;
    private int panelTop;
    private int panelBottom;
    private int coordLabelY;
    private int xBoxX;
    private int zBoxX;

    public LiveMapConfigScreen(LiveMapBlockEntity be) {
        super(Component.translatable("screen.mapminecraft.live_map"));
        this.be = be;
        this.working = be.getSettings();
    }

    @Override
    protected void init() {
        int x = PANEL_X;
        int y = Math.max(40, height / 2 - 100);
        panelTop = y - 24;

        Button show = Button.builder(showLabel(), b -> {
            set(working.withShowHologram(!working.showHologram()));
            b.setMessage(showLabel());
        }).bounds(x, y, PANEL_W, ROW_H).build();
        addRenderableWidget(show);
        y += GAP;

        addRenderableWidget(slider(x, y, "screen.mapminecraft.radius", LiveMapSettings.MIN_RADIUS, LiveMapSettings.MAX_RADIUS,
                working.radius(), true, v -> set(working.withRadius((int) v))));
        y += GAP;
        addRenderableWidget(slider(x, y, "screen.mapminecraft.interval", LiveMapSettings.MIN_INTERVAL, LiveMapSettings.MAX_INTERVAL,
                working.interval(), true, v -> set(working.withInterval((int) v))));
        y += GAP;
        addRenderableWidget(slider(x, y, "screen.mapminecraft.scale", LiveMapSettings.MIN_SCALE, LiveMapSettings.MAX_SCALE,
                working.scale(), false, v -> set(working.withScale((float) v))));
        y += GAP;
        addRenderableWidget(slider(x, y, "screen.mapminecraft.hover", LiveMapSettings.MIN_HOVER, LiveMapSettings.MAX_HOVER,
                working.hover(), false, v -> set(working.withHover((float) v))));
        y += GAP;
        addRenderableWidget(slider(x, y, "screen.mapminecraft.rotspeed", LiveMapSettings.MIN_ROT, LiveMapSettings.MAX_ROT,
                working.rotationSpeed(), false, v -> set(working.withRotationSpeed((float) v))));
        y += GAP;

        Button bob = Button.builder(bobLabel(), b -> {
            set(working.withBob(!working.bob()));
            b.setMessage(bobLabel());
        }).bounds(x, y, 103, ROW_H).build();
        Button rot = Button.builder(rotLabel(), b -> {
            set(working.withRotate(!working.rotate()));
            b.setMessage(rotLabel());
        }).bounds(x + 107, y, 103, ROW_H).build();
        addRenderableWidget(bob);
        addRenderableWidget(rot);
        y += GAP;

        Button redstone = Button.builder(redstoneLabel(), b -> {
            set(working.withRedstone(!working.redstone()));
            b.setMessage(redstoneLabel());
        }).bounds(x, y, 103, ROW_H).build();
        Button detect = Button.builder(detectLabel(), b -> {
            set(working.withDetectMode((working.detectMode() + 1) % LiveMapSettings.DETECT_COUNT));
            b.setMessage(detectLabel());
        }).bounds(x + 107, y, 103, ROW_H).build();
        addRenderableWidget(redstone);
        addRenderableWidget(detect);
        y += GAP;

        // Recenter-on-coordinates: a toggle plus X/Z entry boxes.
        Button target = Button.builder(targetLabel(), b -> {
            set(working.withUseTarget(!working.useTarget()));
            b.setMessage(targetLabel());
        }).bounds(x, y, PANEL_W, ROW_H).build();
        addRenderableWidget(target);
        y += GAP;

        coordLabelY = y;
        y += 10; // room for the X / Z captions drawn in render()
        xBoxX = x;
        zBoxX = x + 107;
        EditBox xBox = new EditBox(font, xBoxX, y, 103, ROW_H, Component.literal("X"));
        xBox.setMaxLength(8);
        xBox.setFilter(s -> s.matches("-?\\d*"));
        xBox.setValue(String.valueOf(working.targetX()));
        xBox.setResponder(s -> set(working.withTargetX(parseCoord(s, working.targetX()))));
        EditBox zBox = new EditBox(font, zBoxX, y, 103, ROW_H, Component.literal("Z"));
        zBox.setMaxLength(8);
        zBox.setFilter(s -> s.matches("-?\\d*"));
        zBox.setValue(String.valueOf(working.targetZ()));
        zBox.setResponder(s -> set(working.withTargetZ(parseCoord(s, working.targetZ()))));
        addRenderableWidget(xBox);
        addRenderableWidget(zBox);
        y += GAP + 4;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y, PANEL_W, ROW_H).build());
        panelBottom = y + ROW_H + 6;
    }

    private SettingSlider slider(int x, int y, String key, float min, float max, float current, boolean integer, DoubleConsumer onChange) {
        return new SettingSlider(x, y, PANEL_W, ROW_H, Component.translatable(key), min, max, current, integer, onChange);
    }

    private void set(LiveMapSettings s) {
        working = s.sanitized();
        be.previewSettings(working); // live preview on the actual block entity
    }

    private Component bobLabel() {
        return Component.translatable("screen.mapminecraft.bob").append(": ").append(onOff(working.bob()));
    }

    private Component rotLabel() {
        return Component.translatable("screen.mapminecraft.rotate").append(": ").append(onOff(working.rotate()));
    }

    private Component redstoneLabel() {
        return Component.translatable("screen.mapminecraft.redstone").append(": ").append(onOff(working.redstone()));
    }

    private Component targetLabel() {
        return Component.translatable("screen.mapminecraft.target").append(": ").append(onOff(working.useTarget()));
    }

    private Component showLabel() {
        return Component.translatable("screen.mapminecraft.show").append(": ").append(onOff(working.showHologram()));
    }

    private static int parseCoord(String s, int fallback) {
        if (s.isEmpty() || s.equals("-")) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Component detectLabel() {
        String key = switch (working.detectMode()) {
            case LiveMapSettings.DETECT_PLAYERS -> "screen.mapminecraft.detect.players";
            case LiveMapSettings.DETECT_HOSTILES -> "screen.mapminecraft.detect.hostiles";
            case LiveMapSettings.DETECT_ANIMALS -> "screen.mapminecraft.detect.animals";
            case LiveMapSettings.DETECT_ITEMS -> "screen.mapminecraft.detect.items";
            default -> "screen.mapminecraft.detect.all";
        };
        return Component.translatable("screen.mapminecraft.detect").append(": ").append(Component.translatable(key));
    }

    private static Component onOff(boolean on) {
        return CommonComponents.optionStatus(on);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new UpdateSettingsPayload(be.getBlockPos(), working));
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim only the panel area, leaving the rest of the world (and the hologram) clearly visible.
        graphics.fill(PANEL_X - 6, panelTop, PANEL_X + PANEL_W + 6, panelBottom, 0xC0101418);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, PANEL_X, panelTop + 8, 0xFFFFFF, true);
        // Captions for the target coordinate boxes.
        graphics.drawString(font, "X", xBoxX, coordLabelY, 0xA0A8B4, false);
        graphics.drawString(font, "Z", zBoxX, coordLabelY, 0xA0A8B4, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // keep the world (and live hologram preview) animating behind the panel
    }

    /** A slider that maps its 0..1 position onto a numeric range and reports the real value. */
    private static final class SettingSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final Component label;
        private final boolean integer;
        private final DoubleConsumer onChange;

        SettingSlider(int x, int y, int w, int h, Component label, float min, float max, float current,
                      boolean integer, DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(), (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.integer = integer;
            this.onChange = onChange;
            updateMessage();
        }

        private double real() {
            double v = min + value * (max - min);
            return integer ? Math.round(v) : v;
        }

        @Override
        protected void updateMessage() {
            double v = real();
            String shown = integer ? String.valueOf((int) v) : String.format("%.2f", v);
            setMessage(label.copy().append(": " + shown));
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }
}

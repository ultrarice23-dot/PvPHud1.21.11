package dev.pvphud.gui;

import dev.pvphud.PvpHudClient;
import dev.pvphud.config.HudElementType;
import dev.pvphud.config.PvpConfig;
import dev.pvphud.hud.HudRenderer;
import dev.pvphud.tracker.CombatTracker;
import dev.pvphud.util.Colors;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;

/**
 * Opened with \ . Tabs with scrolling option lists + a drag-and-drop HUD editor mode.
 * The real HUD keeps rendering behind this screen (demo data if no fight), so every change previews live.
 */
public class SettingsScreen extends Screen {
    private enum Tab {
        GENERAL("General"), THEME("Theme"), LAYOUT("Layout"), STATS("Stats"), POPUPS("Popups"), COORDS("Coords"), SOUND("Sound"), COMBAT("Combat");
        final String label;
        Tab(String l) { label = l; }
    }

    private record Row(String label, ClickableWidget widget, IntSupplier swatch) {}

    private static final int ROW_H = 22, WW = 150;
    private static Tab lastTab = Tab.GENERAL;

    private final List<Row> rows = new ArrayList<>();
    private Tab tab = lastTab;
    private double scroll;
    private boolean editMode;
    private int px, py, pw, ph, listTop, listBottom;
    private HudElementType dragging;
    private double dragDX;

    public SettingsScreen() { super(Text.literal("PvP HUD Settings")); }

    public boolean isEditing() { return editMode; }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) { /* keep HUD crisp */ }
    @Override public void removed() { PvpConfig.save(); }

    // ------------------------------------------------------------------ init
    @Override
    protected void init() {
        clearChildren();
        rows.clear();
        pw = Math.min(width - 16, 430);
        ph = Math.min(height - 16, 280);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        if (editMode) {
            int by = height - 26;
            addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> { editMode = false; init(); })
                    .dimensions(width / 2 - 105, by, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Reset layout"), b -> { PvpConfig.get().resetLayout(); })
                    .dimensions(width / 2 + 5, by, 100, 20).build());
            return;
        }

        listTop = py + 34;
        listBottom = py + ph - 30;

        Tab[] ts = Tab.values();
        int tw = (pw - 16) / ts.length;
        for (int i = 0; i < ts.length; i++) {
            Tab t = ts[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(t.label), x -> { tab = t; lastTab = t; scroll = 0; init(); })
                    .dimensions(px + 8 + i * tw, py + 8, tw - 2, 20).build();
            b.active = t != tab;
            addDrawableChild(b);
        }

        int by = py + ph - 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Edit HUD Layout"), b -> { editMode = true; init(); })
                .dimensions(px + 8, by, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset All"), b -> { PvpConfig.reset(); init(); })
                .dimensions(px + 124, by, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(px + pw - 78, by, 70, 20).build());

        buildRows();
        for (Row r : rows) if (r.widget != null) addSelectableChild(r.widget);
        layoutRows();
    }

    // ------------------------------------------------------------------ rows
    private void buildRows() {
        PvpConfig c = PvpConfig.get();
        switch (tab) {
            case GENERAL -> {
                header("Tracker");
                toggle("HUD enabled", () -> c.enabled, v -> c.enabled = v);
                toggle("Auto-start on first attack", () -> c.autoStart, v -> c.autoStart = v);
                toggle("Show HUD while paused  ( ] )", () -> c.showWhenPaused, v -> c.showWhenPaused = v);
                intSlider("Auto-hide after idle (s, 0=off)", 0, 120, () -> c.autoHideSeconds, v -> c.autoHideSeconds = v);
                header("Target locking");
                toggle("Lock players only", () -> c.lockPlayersOnly, v -> c.lockPlayersOnly = v);
                toggle("Lock crosshair target on  [", () -> c.lockOnRefresh, v -> c.lockOnRefresh = v);
                header("Editor");
                toggle("Snap to screen center", () -> c.snapToCenter, v -> c.snapToCenter = v);
                header("Keys: \\ settings   [ start/reset   ] pause   ' resume");
            }
            case THEME -> {
                header("Preset");
                cycle("Theme", PvpConfig.Theme.values(), () -> c.theme, v -> c.theme = v);
                header("Font");
                cycle("Font", PvpConfig.FontChoice.values(), () -> c.font, v -> c.font = v);
                text("Custom font id", () -> c.customFontId, v -> c.customFontId = v);
                toggle("Bold text", () -> c.bold, v -> c.bold = v);
                toggle("Text shadow", () -> c.textShadow, v -> c.textShadow = v);
                header("Colors (hex RRGGBB). Panel colors apply to CUSTOM theme");
                color("Background", () -> c.bgColor, v -> c.bgColor = v);
                color("Text", () -> c.textColor, v -> c.textColor = v);
                color("Border", () -> c.borderColor, v -> c.borderColor = v);
                color("Accent", () -> c.accentColor, v -> c.accentColor = v);
                color("You", () -> c.selfColor, v -> c.selfColor = v);
                color("Opponent", () -> c.enemyColor, v -> c.enemyColor = v);
                color("Absorption", () -> c.absorbColor, v -> c.absorbColor = v);
                header("Opacity & shape");
                pctSlider("Background opacity", () -> c.bgOpacity, v -> c.bgOpacity = v);
                pctSlider("Text opacity", () -> c.textOpacity, v -> c.textOpacity = v);
                pctSlider("Border opacity", () -> c.borderOpacity, v -> c.borderOpacity = v);
                toggle("Border", () -> c.showBorder, v -> c.showBorder = v);
                intSlider("Corner radius", 0, 8, () -> c.cornerRadius, v -> c.cornerRadius = v);
                intSlider("Padding", 2, 12, () -> c.padding, v -> c.padding = v);
                slider("Global scale", 0.5, 2.5, 0.05, () -> c.globalScale, v -> c.globalScale = (float) v, v -> String.format(Locale.ROOT, "Global scale: %.2fx", v));
            }
            case LAYOUT -> {
                header("Elements (drag them in the editor)");
                for (HudElementType t : HudElementType.values()) {
                    PvpConfig.ElementCfg e = c.element(t);
                    String n = pretty(t);
                    toggle(n + " visible", () -> e.visible, v -> e.visible = v);
                    slider(n + " scale", 0.4, 3.0, 0.05, () -> e.scale, v -> e.scale = (float) v, v -> String.format(Locale.ROOT, "%.2fx", v));
                }
                header("Player cards");
                intSlider("Card width", 110, 220, () -> c.cardWidth, v -> c.cardWidth = v);
                toggle("Show head", () -> c.showHead, v -> c.showHead = v);
                toggle("Show name", () -> c.showName, v -> c.showName = v);
                toggle("Show HP numbers", () -> c.showHpNumber, v -> c.showHpNumber = v);
                cycle("Health display", PvpConfig.HeartStyle.values(), () -> c.heartStyle, v -> c.heartStyle = v);
                toggle("Absorption hearts row", () -> c.showAbsorptionRow, v -> c.showAbsorptionRow = v);
                toggle("Armor durability", () -> c.showArmor, v -> c.showArmor = v);
                header("Balance bar");
                intSlider("Bar width", 100, 320, () -> c.balanceWidth, v -> c.balanceWidth = v);
                intSlider("Bar height", 4, 20, () -> c.balanceBarHeight, v -> c.balanceBarHeight = v);
                slider("Tilt (px)", 0, 20, 1, () -> c.balanceTilt, v -> c.balanceTilt = (float) v, v -> "Tilt: " + (int) v);
                pctSlider("Animation smoothing", () -> c.balanceSmoothing, v -> c.balanceSmoothing = v);
                toggle("Show percentages", () -> c.balanceShowPercent, v -> c.balanceShowPercent = v);
                toggle("Center mark", () -> c.balanceCenterMark, v -> c.balanceCenterMark = v);
                toggle("Include absorption", () -> c.balanceIncludeAbsorption, v -> c.balanceIncludeAbsorption = v);
            }
            case STATS -> {
                header("Lines");
                intSlider("Panel width", 120, 260, () -> c.statsWidth, v -> c.statsWidth = v);
                toggle("Title", () -> c.statsShowTitle, v -> c.statsShowTitle = v);
                toggle("Time elapsed", () -> c.showTime, v -> c.showTime = v);
                toggle("Combo / best combo", () -> c.showCombo, v -> c.showCombo = v);
                toggle("Accuracy", () -> c.showAccuracy, v -> c.showAccuracy = v);
                toggle("Hits (you : opponent)", () -> c.showHits, v -> c.showHits = v);
                toggle("Hit differential", () -> c.showHitDiff, v -> c.showHitDiff = v);
                toggle("Damage dealt / taken", () -> c.showDamage, v -> c.showDamage = v);
                toggle("Armor durability dealt / taken", () -> c.showArmorDamage, v -> c.showArmorDamage = v);
                toggle("Unbreaking saved estimate", () -> c.showUnbreaking, v -> c.showUnbreaking = v);
                toggle("Color-code hit diff", () -> c.colorDiff, v -> c.colorDiff = v);
                header("Numbers");
                cycle("Damage unit", PvpConfig.DamageUnit.values(), () -> c.damageUnit, v -> c.damageUnit = v);
                intSlider("Decimals", 0, 2, () -> c.damageDecimals, v -> c.damageDecimals = v);
            }
            case COORDS -> {
                header("Coordinates of the last player you hit");
                toggle("Show even if tracker is off", () -> c.coordsAlways, v -> c.coordsAlways = v);
                toggle("Show distance", () -> c.coordsShowDistance, v -> c.coordsShowDistance = v);
                toggle("Show direction", () -> c.coordsShowDirection, v -> c.coordsShowDirection = v);
                toggle("Show dimension", () -> c.coordsShowDimension, v -> c.coordsShowDimension = v);
                toggle("Chat message on new player hit", () -> c.coordsChat, v -> c.coordsChat = v);
                intSlider("Panel width", 110, 240, () -> c.coordsWidth, v -> c.coordsWidth = v);
                header("Move / hide the panel in: Layout tab or HUD editor");
            }
            case POPUPS -> {
                header("Floating damage text");
                toggle("Popups enabled", () -> c.popupsEnabled, v -> c.popupsEnabled = v);
                toggle("Show damage dealt", () -> c.popupShowDealt, v -> c.popupShowDealt = v);
                toggle("Show damage taken", () -> c.popupShowTaken, v -> c.popupShowTaken = v);
                slider("Size", 0.5, 4.0, 0.1, () -> c.popupSize, v -> c.popupSize = (float) v, v -> String.format(Locale.ROOT, "Size: %.1fx", v));
                intSlider("Duration (ms)", 300, 3000, () -> c.popupDurationMs, v -> c.popupDurationMs = v);
                intSlider("Float height (px)", 0, 100, () -> c.popupRise, v -> c.popupRise = v);
                intSlider("Horizontal spread", 0, 80, () -> c.popupSpread, v -> c.popupSpread = v);
                cycle("Anchor", PvpConfig.PopupAnchor.values(), () -> c.popupAnchor, v -> c.popupAnchor = v);
                color("Dealt color", () -> c.dealtColor, v -> c.dealtColor = v);
                color("Taken color", () -> c.takenColor, v -> c.takenColor = v);
                action("Preview", "Spawn test popups", () -> CombatTracker.I.testPopups());
            }
            case SOUND -> {
                header("Master");
                toggle("Sounds enabled", () -> c.soundsEnabled, v -> c.soundsEnabled = v);
                pctSlider("Master volume", () -> c.masterVolume, v -> c.masterVolume = v);
                header("Hit landed");
                toggle("Enabled", () -> c.hitSoundEnabled, v -> c.hitSoundEnabled = v);
                cycle("Sound", PvpConfig.SoundChoice.values(), () -> c.hitSound, v -> c.hitSound = v);
                pctSlider("Volume", () -> c.hitVolume, v -> c.hitVolume = v);
                slider("Base pitch", 0.5, 2.0, 0.05, () -> c.hitPitch, v -> c.hitPitch = (float) v, v -> String.format(Locale.ROOT, "Base pitch: %.2f", v));
                slider("Pitch per combo", 0, 0.2, 0.01, () -> c.comboPitchStep, v -> c.comboPitchStep = (float) v, v -> String.format(Locale.ROOT, "Pitch / combo: +%.2f", v));
                slider("Max pitch", 1.0, 2.0, 0.05, () -> c.maxPitch, v -> c.maxPitch = (float) v, v -> String.format(Locale.ROOT, "Max pitch: %.2f", v));
                action("Test", "Play hit", () -> CombatTracker.I.playHit());
                header("Damage taken");
                toggle("Enabled ", () -> c.takenSoundEnabled, v -> c.takenSoundEnabled = v);
                cycle("Sound ", PvpConfig.SoundChoice.values(), () -> c.takenSound, v -> c.takenSound = v);
                pctSlider("Volume ", () -> c.takenVolume, v -> c.takenVolume = v);
                slider("Pitch", 0.5, 2.0, 0.05, () -> c.takenPitch, v -> c.takenPitch = (float) v, v -> String.format(Locale.ROOT, "Pitch: %.2f", v));
                action("Test ", "Play taken", () -> CombatTracker.I.playTaken());
            }
            case COMBAT -> {
                header("Accuracy / anti-spam");
                slider("Min attack charge", 0.0, 1.0, 0.05, () -> c.minCooldown, v -> c.minCooldown = (float) v, v -> String.format(Locale.ROOT, "Min charge: %d%%", Math.round(v * 100)));
                intSlider("Hit confirm window (ticks)", 2, 20, () -> c.hitWindowTicks, v -> c.hitWindowTicks = v);
                header("Fight rules");
                intSlider("Fight range (blocks)", 4, 64, () -> c.fightRange, v -> c.fightRange = v);
                toggle("Count all damage (even far away)", () -> c.countAllDamage, v -> c.countAllDamage = v);
                slider("Min damage counted", 0, 2, 0.05, () -> c.minDamage, v -> c.minDamage = (float) v, v -> String.format(Locale.ROOT, "Min damage: %.2f", v));
                toggle("Combo resets when hit", () -> c.comboResetsOnHit, v -> c.comboResetsOnHit = v);
                toggle("Combo resets on miss", () -> c.comboResetsOnMiss, v -> c.comboResetsOnMiss = v);
            }
        }
    }

    private void add(String label, ClickableWidget w, IntSupplier swatch) { rows.add(new Row(label, w, swatch)); }
    private void header(String s) { rows.add(new Row(s, null, null)); }

    private void toggle(String label, BooleanSupplier g, Consumer<Boolean> s) {
        add(label, ButtonWidget.builder(onOff(g.getAsBoolean()), b -> { s.accept(!g.getAsBoolean()); b.setMessage(onOff(g.getAsBoolean())); })
                .dimensions(0, 0, WW, 18).build(), null);
    }

    private <E extends Enum<E>> void cycle(String label, E[] values, Supplier<E> g, Consumer<E> s) {
        add(label, ButtonWidget.builder(Text.literal(pretty(g.get())), b -> {
            E next = values[(g.get().ordinal() + 1) % values.length];
            s.accept(next);
            b.setMessage(Text.literal(pretty(next)));
        }).dimensions(0, 0, WW, 18).build(), null);
    }

    private void slider(String label, double min, double max, double step, DoubleSupplier g, DoubleConsumer s, DoubleFunction<String> fmt) {
        add(label, new ConfigSlider(WW, min, max, step, g.getAsDouble(), s, fmt), null);
    }
    private void intSlider(String label, int min, int max, IntSupplier g, IntConsumer s) {
        slider(label, min, max, 1, g::getAsInt, v -> s.accept((int) Math.round(v)), v -> String.valueOf((int) Math.round(v)));
    }
    private void pctSlider(String label, DoubleSupplier g, DoubleConsumer s) {
        slider(label, 0, 1, 0.01, g, s, v -> Math.round(v * 100) + "%");
    }

    private void color(String label, Supplier<String> g, Consumer<String> s) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.empty());
        f.setMaxLength(9);
        f.setText(g.get());
        f.setChangedListener(str -> {
            boolean ok = Colors.valid(str);
            f.setEditableColor(ok ? 0xE0E0E0 : 0xFF5555);
            if (ok) s.accept(str.trim());
        });
        add(label, f, () -> Colors.parse(g.get(), 0xFFFFFFFF));
    }

    private void text(String label, Supplier<String> g, Consumer<String> s) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, WW, 18, Text.empty());
        f.setMaxLength(64);
        f.setText(g.get());
        f.setChangedListener(s);
        add(label, f, null);
    }

    private void action(String label, String button, Runnable r) {
        add(label, ButtonWidget.builder(Text.literal(button), b -> r.run()).dimensions(0, 0, WW, 18).build(), null);
    }

    private static Text onOff(boolean v) { return Text.literal(v ? "ON" : "OFF").formatted(v ? Formatting.GREEN : Formatting.RED); }

    private static String pretty(Enum<?> e) {
        String[] parts = e.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) { if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); }
        return sb.toString();
    }

    private void layoutRows() {
        int y = listTop + 4 - (int) scroll;
        for (Row r : rows) {
            if (r.widget != null) {
                r.widget.setX(px + pw - 14 - r.widget.getWidth());
                r.widget.setY(y + 2);
                r.widget.visible = y >= listTop && y + ROW_H <= listBottom;
            }
            y += ROW_H;
        }
    }

    private int maxScroll() { return Math.max(0, rows.size() * ROW_H + 8 - (listBottom - listTop)); }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (editMode) { renderEditor(ctx, mx, my, delta); return; }

        ctx.fill(px, py, px + pw, py + ph, 0xE6101018);
        outline(ctx, px, py, pw, ph, 0xFF3A3A55);
        ctx.fill(px + 4, listTop - 2, px + pw - 4, listBottom, 0x55000000);

        int y = listTop + 4 - (int) scroll;
        for (Row r : rows) {
            boolean vis = y >= listTop && y + ROW_H <= listBottom;
            if (vis) {
                if (r.widget == null) {
                    ctx.drawText(textRenderer, r.label, px + 12, y + 7, 0xFF55AAFF, false);
                } else {
                    ctx.drawText(textRenderer, r.label, px + 12, y + 7, 0xFFDDDDDD, false);
                    if (r.swatch != null) {
                        int sx = r.widget.getX() - 20;
                        ctx.fill(sx - 1, y + 3, sx + 15, y + 19, 0xFFFFFFFF);
                        ctx.fill(sx, y + 4, sx + 14, y + 18, 0xFF000000 | r.swatch.getAsInt());
                    }
                    r.widget.render(ctx, mx, my, delta);
                }
            }
            y += ROW_H;
        }
        int ms = maxScroll();
        if (ms > 0) {
            int trackH = listBottom - listTop;
            int barH = Math.max(16, trackH * trackH / (trackH + ms));
            int barY = listTop + (int) ((trackH - barH) * (scroll / ms));
            ctx.fill(px + pw - 7, barY, px + pw - 4, barY + barH, 0xAA8888AA);
        }
        super.render(ctx, mx, my, delta); // tabs + bottom buttons
    }

    private void renderEditor(DrawContext ctx, int mx, int my, float delta) {
        PvpConfig c = PvpConfig.get();
        for (HudElementType t : HudElementType.values()) {
            HudRenderer.Rect r = HudRenderer.bounds(t);
            boolean vis = c.element(t).visible;
            int col = dragging == t ? 0xFFFFFF55 : vis ? 0xFF55AAFF : 0x88AAAAAA;
            outline(ctx, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, col);
            ctx.drawText(textRenderer, pretty(t) + (vis ? "" : " (hidden)"), r.x(), Math.max(0, r.y() - 10), col, true);
        }
        if (dragging != null && c.snapToCenter && Math.abs(c.element(dragging).x - 0.5f) < 0.0001f)
            ctx.fill(width / 2, 0, width / 2 + 1, height, 0x8855AAFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Drag = move   |   Scroll = resize   |   Right-click = show/hide"), width / 2, height - 40, 0xFFFFFFFF);
        super.render(ctx, mx, my, delta);
    }

    // ------------------------------------------------------------------ input
    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private HudElementType hit(double mx, double my) {
        HudElementType[] v = HudElementType.values();
        for (int i = v.length - 1; i >= 0; i--) if (HudRenderer.bounds(v[i]).contains(mx, my)) return v[i];
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (editMode) {
            if (super.mouseClicked(click, doubled)) return true;
            HudElementType t = hit(mx, my);
            if (t == null) return false;
            if (button == 0) {
                HudRenderer.Rect r = HudRenderer.bounds(t);
                dragging = t;
                dragDX = mx - (r.x() + r.w() / 2.0);
                dragDY = my - (r.y() + r.h() / 2.0);
            } else if (button == 1) {
                PvpConfig.ElementCfg e = PvpConfig.get().element(t);
                e.visible = !e.visible;
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }
    private double dragDY;

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (editMode && dragging != null && click.button() == 0) {
            PvpConfig c = PvpConfig.get();
            PvpConfig.ElementCfg e = c.element(dragging);
            double cx = click.x() - dragDX, cy = click.y() - dragDY;
            e.x = (float) Math.max(0.0, Math.min(1.0, cx / width));
            e.y = (float) Math.max(0.0, Math.min(1.0, cy / height));
            if (c.snapToCenter && Math.abs(cx - width / 2.0) < 4) e.x = 0.5f;
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (editMode) {
            HudElementType t = hit(mx, my);
            if (t != null) {
                PvpConfig.ElementCfg e = PvpConfig.get().element(t);
                e.scale = Math.max(0.4f, Math.min(3f, e.scale + (float) Math.signum(vAmount) * 0.05f));
                return true;
            }
            return false;
        }
        if (mx >= px && mx <= px + pw && my >= listTop && my <= listBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vAmount * ROW_H * 0.8));
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        boolean typing = getFocused() instanceof TextFieldWidget f && f.isFocused();
        if (editMode && input.key() == 256) { editMode = false; init(); return true; }
        if (!typing && input.key() == KeyBindingHelper.getBoundKeyOf(PvpHudClient.OPEN).getCode()) { close(); return true; }
        return super.keyPressed(input);
    }
}

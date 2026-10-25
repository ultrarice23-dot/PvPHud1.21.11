package dev.pvphud.hud;

import dev.pvphud.config.HudElementType;
import dev.pvphud.config.PvpConfig;
import dev.pvphud.gui.SettingsScreen;
import dev.pvphud.tracker.CombatTracker;
import dev.pvphud.tracker.HudData;
import dev.pvphud.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gl.RenderPipelines;
import org.joml.Matrix3x2fStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudRenderer {
    private HudRenderer() {}

    private static final Identifier HEART_CONTAINER = Identifier.of("minecraft", "hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.of("minecraft", "hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.of("minecraft", "hud/heart/half");
    private static final Identifier ABS_FULL = Identifier.of("minecraft", "hud/heart/absorbing_full");
    private static final Identifier ABS_HALF = Identifier.of("minecraft", "hud/heart/absorbing_half");

    public record Rect(int x, int y, int w, int h, float scale, int baseW, int baseH) {
        public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Line(String label, String value, int color) {}

    // palette (ARGB)
    private static int cBg, cText, cDim, cBorder, cSelf, cEnemy, cAbsorb, cAccent;
    private static boolean shadow;

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }
    private static PvpConfig cfg() { return PvpConfig.get(); }

    // ======================================================================
    public static void render(DrawContext ctx) {
        PvpConfig c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.player == null || mc.world == null) return;
        boolean preview = mc.currentScreen instanceof SettingsScreen;
        boolean showAll = preview && ((SettingsScreen) mc.currentScreen).isEditing();
        CombatTracker t = CombatTracker.I;
        if (!preview && mc.options.hudHidden) return;
        boolean trackerOn = preview || (t.isVisible() && !(c.autoHideSeconds > 0 && t.idleSeconds() > c.autoHideSeconds));
        boolean demo = preview && (t.state == CombatTracker.State.OFF || !t.hasTarget());
        HudData d = demo ? HudData.demo() : t.snapshot();
        if (preview && !d.hasLastHit) d.fillDemoCoords();
        palette(c);
        for (HudElementType type : HudElementType.values()) {
            if (!showAll && !c.element(type).visible) continue;
            if (type == HudElementType.COORDS) {
                if (!showAll && !preview && (!d.hasLastHit || !(trackerOn || c.coordsAlways))) continue;
            } else if (!trackerOn) continue;
            drawElement(ctx, type, d);
        }
        drawPopups(ctx, c);
    }

    private static void palette(PvpConfig c) {
        int bg, text, border;
        switch (c.theme) {
            case LIGHT -> { bg = 0xF2F2F7; text = 0x16161D; border = 0xB5B5C8; }
            case CLEAR -> { bg = 0x000000; text = 0xFFFFFF; border = 0xFFFFFF; }
            case CUSTOM -> {
                bg = Colors.rgb(Colors.parse(c.bgColor, 0xFF0E0E16));
                text = Colors.rgb(Colors.parse(c.textColor, 0xFFFFFFFF));
                border = Colors.rgb(Colors.parse(c.borderColor, 0xFF3A3A55));
            }
            default -> { bg = 0x0E0E16; text = 0xFFFFFF; border = 0x3A3A55; }
        }
        cBg = Colors.alpha(bg, c.theme == PvpConfig.Theme.CLEAR ? 0f : c.bgOpacity);
        cText = Colors.textAlpha(text, c.textOpacity);
        cDim = Colors.textAlpha(text, c.textOpacity * 0.65f);
        cBorder = Colors.alpha(border, c.borderOpacity);
        cSelf = Colors.parse(c.selfColor, 0xFF55FF77);
        cEnemy = Colors.parse(c.enemyColor, 0xFFFF5566);
        cAbsorb = Colors.parse(c.absorbColor, 0xFFFFD633);
        cAccent = Colors.parse(c.accentColor, 0xFF55AAFF);
        shadow = c.textShadow || c.theme == PvpConfig.Theme.CLEAR;
    }

    // ======================================================================
    // geometry
    public static float scaleOf(HudElementType t) { return cfg().globalScale * cfg().element(t).scale; }

    public static int[] size(HudElementType type) {
        PvpConfig c = cfg();
        int p = c.padding;
        return switch (type) {
            case BALANCE -> new int[]{c.balanceWidth, 2 * p + (c.balanceShowPercent ? 10 : 0) + c.balanceBarHeight};
            case TARGET, SELF -> new int[]{c.cardWidth, cardHeight(c)};
            case STATS -> new int[]{c.statsWidth, 2 * p + (c.statsShowTitle ? 11 : 0) + Math.max(1, statsLines(HudData.EMPTY).size()) * 10 - 1};
            case COORDS -> new int[]{c.coordsWidth, 2 * p + coordsLineCount(c) * 10 - 1};
        };
    }

    public static Rect bounds(HudElementType type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int[] sz = size(type);
        float s = scaleOf(type);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float ww = sz[0] * s, hh = sz[1] * s;
        PvpConfig.ElementCfg e = cfg().element(type);
        int left = Math.round(e.x * sw - ww / 2f), top = Math.round(e.y * sh - hh / 2f);
        left = Math.max(0, Math.min(left, Math.max(0, sw - Math.round(ww))));
        top = Math.max(0, Math.min(top, Math.max(0, sh - Math.round(hh))));
        return new Rect(left, top, Math.round(ww), Math.round(hh), s, sz[0], sz[1]);
    }

    private static void drawElement(DrawContext ctx, HudElementType type, HudData d) {
        Rect r = bounds(type);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) r.x(), (float) r.y());
        m.scale(r.scale(), r.scale());
        panel(ctx, r.baseW(), r.baseH());
        switch (type) {
            case BALANCE -> drawBalance(ctx, d, r.baseW());
            case TARGET -> drawCard(ctx, d.opp, r.baseW(), cEnemy, d.hasTarget);
            case SELF -> drawCard(ctx, d.me, r.baseW(), cSelf, true);
            case STATS -> drawStats(ctx, d, r.baseW());
            case COORDS -> drawCoords(ctx, d, r.baseW());
        }
        m.popMatrix();
    }

    // ======================================================================
    // panel background (rounded corners + border)
    private static int inset(int row, int r) {
        if (row >= r) return 0;
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private static void panel(DrawContext ctx, int w, int h) {
        PvpConfig c = cfg();
        int r = Math.min(c.cornerRadius, Math.min(w, h) / 2);
        if (((cBg >>> 24) & 255) > 0) {
            if (r == 0) ctx.fill(0, 0, w, h, cBg);
            else for (int y = 0; y < h; y++) {
                int iy = inset(Math.min(y, h - 1 - y), r);
                ctx.fill(iy, y, w - iy, y + 1, cBg);
            }
        }
        if (c.showBorder && ((cBorder >>> 24) & 255) > 0) {
            for (int y = 0; y < h; y++) {
                int my = Math.min(y, h - 1 - y);
                int iy = inset(my, r);
                if (my == 0) { ctx.fill(iy, y, w - iy, y + 1, cBorder); continue; }
                int end = (my < r) ? Math.max(iy + 1, inset(my - 1, r)) : 1;
                ctx.fill(iy, y, end, y + 1, cBorder);
                ctx.fill(w - end, y, w - iy, y + 1, cBorder);
            }
        }
    }

    // ======================================================================
    // text helpers (font / bold / shadow)
    private static Identifier fontId(PvpConfig c) {
        return switch (c.font) {
            case UNIFORM -> Identifier.of("minecraft", "uniform");
            case ALT -> Identifier.of("minecraft", "alt");
            case ILLAGER -> Identifier.of("minecraft", "illageralt");
            case CUSTOM -> Identifier.tryParse(c.customFontId);
            default -> null;
        };
    }

    private static Text styled(String s) {
        PvpConfig c = cfg();
        Style st = Style.EMPTY;
        Identifier f = fontId(c);
        if (f != null) st = st.withFont(new StyleSpriteSource.Font(f));
        if (c.bold) st = st.withBold(true);
        return Text.literal(s).setStyle(st);
    }

    private static void txt(DrawContext ctx, String s, int x, int y, int color) { ctx.drawText(tr(), styled(s), x, y, color, shadow); }
    private static void txtR(DrawContext ctx, String s, int rightX, int y, int color) {
        Text t = styled(s);
        ctx.drawText(tr(), t, rightX - tr().getWidth(t), y, color, shadow);
    }
    private static void txtC(DrawContext ctx, String s, int cx, int y, int color) {
        Text t = styled(s);
        ctx.drawText(tr(), t, cx - tr().getWidth(t) / 2, y, color, shadow);
    }
    private static String trunc(String s, int maxW) {
        if (tr().getWidth(styled(s)) <= maxW) return s;
        while (s.length() > 1 && tr().getWidth(styled(s + "..")) > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    // ======================================================================
    // cards (you / opponent)
    private static int cardHeight(PvpConfig c) {
        int h = 2 * c.padding;
        if (c.showHead || c.showName) h += 20;
        if (c.heartStyle == PvpConfig.HeartStyle.NUMBERS) h += 11;
        else h += 10 + (c.showAbsorptionRow ? 10 : 0) + 1;
        if (c.showArmor) h += 16;
        return h;
    }

    private static String hpText(HudData.Card k) {
        String s = String.format(Locale.ROOT, "%.1f/%.0f", k.hp, k.max);
        if (k.abs > 0.01f) s += String.format(Locale.ROOT, " +%.1f", k.abs);
        return s;
    }

    private static void drawCard(DrawContext ctx, HudData.Card k, int w, int nameColor, boolean valid) {
        PvpConfig c = cfg();
        int p = c.padding, y = p;
        if (c.showHead || c.showName) {
            int x = p;
            if (c.showHead) { drawHead(ctx, k, x, y); x += 20; }
            if (c.showName) txt(ctx, trunc(k.name, w - x - p), x, y + (c.showHpNumber ? 0 : 4), nameColor);
            if (c.showHpNumber && c.heartStyle != PvpConfig.HeartStyle.NUMBERS) {
                int col = k.abs > 0.01f ? cAbsorb : cDim;
                txt(ctx, hpText(k), x, y + 9, col);
            }
            y += 20;
        }
        if (c.heartStyle == PvpConfig.HeartStyle.NUMBERS) {
            txt(ctx, hpText(k), p, y, k.abs > 0.01f ? cAbsorb : cText);
            y += 11;
        } else {
            int containers = Math.max(1, Math.min(10, (int) Math.ceil(k.max / 2f)));
            drawHearts(ctx, p, y, containers, k.hp, false);
            if (c.heartStyle == PvpConfig.HeartStyle.BOTH) {
                txtR(ctx, String.format(Locale.ROOT, "%.1f", k.hp), w - p, y + 1, cText);
            }
            y += 10;
            if (c.showAbsorptionRow) {
                int ac = Math.min(10, (int) Math.ceil(k.abs / 2f));
                if (ac > 0) drawHearts(ctx, p, y, ac, k.abs, true);
                y += 10;
            }
            y += 1;
        }
        if (c.showArmor) {
            int x = p, sumMax = 0, sumLeft = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack s = k.armor[i];
                if (s == null || s.isEmpty()) {
                    ctx.fill(x, y, x + 12, y + 12, 0x22FFFFFF);
                } else {
                    Matrix3x2fStack m = ctx.getMatrices();
                    m.pushMatrix(); m.translate((float) x, (float) y); m.scale(0.75f, 0.75f);
                    ctx.drawItem(s, 0, 0);
                    m.popMatrix();
                    if (s.isDamageable()) {
                        float ratio = 1f - (float) s.getDamage() / s.getMaxDamage();
                        ctx.fill(x, y + 13, x + 12, y + 14, 0xAA000000);
                        int bar = 0xFF000000 | java.awt.Color.HSBtoRGB(Math.max(0f, ratio) / 3f, 0.9f, 1f);
                        ctx.fill(x, y + 13, x + Math.round(12 * ratio), y + 14, bar);
                        sumMax += s.getMaxDamage(); sumLeft += s.getMaxDamage() - s.getDamage();
                    }
                }
                x += 16;
            }
            String pct = sumMax == 0 ? "no armor" : Math.round(100f * sumLeft / sumMax) + "%";
            txt(ctx, pct, x + 2, y + 2, cDim);
            y += 16;
        }
    }

    private static void drawHead(DrawContext ctx, HudData.Card k, int x, int y) {
        if (k.entry != null) PlayerSkinDrawer.draw(ctx, k.entry.getSkinTextures(), x, y, 16);
        else {
            ctx.fill(x, y, x + 16, y + 16, 0xFF555566);
            ctx.drawText(tr(), "?", x + 5, y + 4, 0xFFFFFFFF, true);
        }
    }

    /** value = raw health points; 20 points = 10 hearts. Uses ceil like vanilla so 16.5 shows a half heart. */
    private static void drawHearts(DrawContext ctx, int x, int y, int containers, float value, boolean absorb) {
        int units = (int) Math.ceil(value);
        for (int i = 0; i < containers; i++) {
            int hx = x + i * 8;
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, y, 9, 9);
            if (units >= (i + 1) * 2) ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, absorb ? ABS_FULL : HEART_FULL, hx, y, 9, 9);
            else if (units >= i * 2 + 1) ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, absorb ? ABS_HALF : HEART_HALF, hx, y, 9, 9);
        }
    }

    // ======================================================================
    // balance bar
    private static void drawBalance(DrawContext ctx, HudData d, int w) {
        PvpConfig c = cfg();
        int p = c.padding, bx = p, bw = w - 2 * p, bh = c.balanceBarHeight;
        int by = p + (c.balanceShowPercent ? 10 : 0);
        if (c.balanceShowPercent) {
            int me = Math.round(100f * HudData.pct(d.me, c.balanceIncludeAbsorption));
            int op = Math.round(100f * HudData.pct(d.opp, c.balanceIncludeAbsorption));
            txt(ctx, me + "%", bx, p, cSelf);
            txtR(ctx, op + "%", w - p, p, cEnemy);
            int diff = me - op;
            if (diff != 0) txtC(ctx, (diff > 0 ? "+" : "") + diff + "%", w / 2, p, diff > 0 ? cSelf : cEnemy);
        }
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0x88000000);
        float ratio = Math.max(0f, Math.min(1f, d.ratio));
        for (int row = 0; row < bh; row++) {
            float off = c.balanceTilt * ((row + 0.5f) / bh - 0.5f);
            int div = bx + Math.round(bw * ratio + off);
            div = Math.max(bx, Math.min(bx + bw, div));
            // subtle vertical shading
            float shade = 1f + 0.15f * (0.5f - (row + 0.5f) / bh);
            ctx.fill(bx, by + row, div, by + row + 1, shade(cSelf, shade));
            ctx.fill(div, by + row, bx + bw, by + row + 1, shade(cEnemy, shade));
        }
        if (c.balanceCenterMark) ctx.fill(bx + bw / 2, by - 1, bx + bw / 2 + 1, by + bh + 1, 0x99FFFFFF);
    }

    private static int shade(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((argb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ======================================================================
    // stats panel
    private static String fmtTime(long ticks) {
        long s = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, (ticks % 20) / 2);
    }

    private static List<Line> statsLines(HudData d) {
        PvpConfig c = cfg();
        List<Line> L = new ArrayList<>();
        if (c.showTime) L.add(new Line("Time", fmtTime(d.ticks), cText));
        if (c.showCombo) L.add(new Line("Combo", "x" + d.combo + "  (best " + d.best + ")", cAccent));
        if (c.showAccuracy) {
            String v = d.swings == 0 ? "--" : Math.round(100f * d.hits / d.swings) + "%  (" + d.hits + "/" + d.swings + ")";
            L.add(new Line("Accuracy", v, cText));
        }
        if (c.showHits) L.add(new Line("Hits (you:opp)", d.hits + " : " + d.hitsTaken, cText));
        if (c.showHitDiff) {
            int diff = d.hits - d.hitsTaken;
            int col = !c.colorDiff ? cText : diff > 0 ? cSelf : diff < 0 ? cEnemy : cDim;
            L.add(new Line("Hit diff", (diff > 0 ? "+" : "") + diff, col));
        }
        if (c.showDamage) {
            L.add(new Line("Damage dealt", c.fmtDamage(d.dealt) + c.unitSuffix(), cSelf));
            L.add(new Line("Damage taken", c.fmtDamage(d.taken) + c.unitSuffix(), cEnemy));
        }
        if (c.showArmorDamage) {
            String a = d.armorDealt + (c.showUnbreaking ? " (~" + Math.round(d.armorDealtSaved) + " saved)" : "");
            String b = d.armorTaken + (c.showUnbreaking ? " (~" + Math.round(d.armorTakenSaved) + " saved)" : "");
            L.add(new Line("Armor dmg dealt", a, cSelf));
            L.add(new Line("Armor dmg taken", b, cEnemy));
        }
        return L;
    }

    private static void drawStats(DrawContext ctx, HudData d, int w) {
        PvpConfig c = cfg();
        int p = c.padding, y = p;
        if (c.statsShowTitle) {
            txt(ctx, d.paused ? "COMBAT STATS (paused)" : "COMBAT STATS", p, y, cAccent);
            ctx.fill(p, y + 9, w - p, y + 10, cAccent);
            y += 11;
        }
        for (Line l : statsLines(d)) {
            txt(ctx, l.label(), p, y, cDim);
            txtR(ctx, l.value(), w - p, y, l.color());
            y += 10;
        }
    }

    // ======================================================================
    // last-hit coordinates panel
    private static int coordsLineCount(PvpConfig c) {
        return 3 + ((c.coordsShowDistance || c.coordsShowDirection) ? 1 : 0) + (c.coordsShowDimension ? 1 : 0);
    }

    private static String directionText(float rel) {
        String[] t = {"^ ahead", "^> front-right", "> right", "v> back-right", "v behind", "<v back-left", "< left", "<^ front-left"};
        int sector = Math.round(rel / 45f);
        return t[((sector % 8) + 8) % 8];
    }

    private static void drawCoords(DrawContext ctx, HudData d, int w) {
        PvpConfig c = cfg();
        int p = c.padding, y = p;
        txt(ctx, "LAST HIT", p, y, cAccent);
        if (!d.lastHitLive) txtR(ctx, "last seen", w - p, y, cDim);
        y += 10;
        txt(ctx, trunc(d.lastHitName, w - 2 * p), p, y, cEnemy);
        y += 10;
        txt(ctx, "XYZ", p, y, cDim);
        txtR(ctx, String.format(Locale.ROOT, "%.0f  %.0f  %.0f", d.lx, d.ly, d.lz), w - p, y, cText);
        y += 10;
        if (c.coordsShowDistance || c.coordsShowDirection) {
            String left = c.coordsShowDistance ? String.format(Locale.ROOT, "%.1f m", d.lastHitDist) : "";
            txt(ctx, left, p, y, cText);
            if (c.coordsShowDirection) txtR(ctx, directionText(d.lastHitBearing), w - p, y, cAccent);
            y += 10;
        }
        if (c.coordsShowDimension) txt(ctx, d.lastHitDim, p, y, cDim);
    }

    // ======================================================================
    // floating damage popups (screen-space)
    private static void drawPopups(DrawContext ctx, PvpConfig c) {
        if (!c.popupsEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        long now = Util.getMeasuringTimeMs();
        Matrix3x2fStack m = ctx.getMatrices();
        for (CombatTracker.Popup p : CombatTracker.I.popups) {
            float age = (now - p.born) / (float) Math.max(100, c.popupDurationMs);
            if (age < 0 || age >= 1f) continue;
            float cx, cy;
            if (c.popupAnchor == PvpConfig.PopupAnchor.TARGET_CARD) {
                Rect r = bounds(p.dealt ? HudElementType.TARGET : HudElementType.SELF);
                cx = r.x() + r.w() / 2f + p.ox * 0.3f; cy = r.y() - 6;
            } else { cx = sw / 2f + p.ox; cy = sh / 2f - 14; }
            float ease = 1f - (1f - age) * (1f - age);
            cy -= c.popupRise * ease;
            float alpha = age < 0.65f ? 1f : 1f - (age - 0.65f) / 0.35f;
            int a = (int) (alpha * 255);
            if (a < 6) continue;
            float pop = 1f + 0.5f * Math.max(0f, 1f - age * 8f);
            float s = c.popupSize * pop;
            Text t = styled(p.text);
            int tw = tr().getWidth(t);
            m.pushMatrix();
            m.translate(cx, cy);
            m.scale(s, s);
            ctx.drawText(tr(), t, -tw / 2, -4, (a << 24) | (p.rgb & 0xFFFFFF), true);
            m.popMatrix();
        }
    }
}

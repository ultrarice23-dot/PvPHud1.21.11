package dev.pvphud.util;

import java.util.regex.Pattern;

public final class Colors {
    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    private Colors() {}

    public static boolean valid(String s) { return s != null && HEX.matcher(s.trim()).matches(); }

    /** Accepts RRGGBB or AARRGGBB, optional '#'. Returns ARGB. */
    public static int parse(String s, int fallback) {
        if (!valid(s)) return fallback;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        long v = Long.parseLong(h, 16);
        if (h.length() == 6) v |= 0xFF000000L;
        return (int) v;
    }

    public static int rgb(int argb) { return argb & 0xFFFFFF; }

    public static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** Text with alpha < 4 is drawn opaque by vanilla, so keep a floor. */
    public static int textAlpha(int rgb, float a) {
        int al = Math.max(10, Math.min(255, Math.round(a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}

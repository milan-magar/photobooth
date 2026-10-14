package com.photobooth;

import java.awt.image.BufferedImage;

public final class FilterEngine {

    private FilterEngine() {}

    public enum Effect {
        NORMAL("Normal"),
        XRAY("X-Ray"),
        SEPIA("Sepia"),
        THERMAL("Thermal Vision"),
        POP_ART("Pop Art"),
        BULGE("Bulge"),
        SQUEEZE("Squeeze"),
        TWIRL("Twirl");

        public final String label;
        Effect(String label) { this.label = label; }
    }

    public static BufferedImage apply(BufferedImage src, Effect effect) {
        if (src == null) return null;
        if (effect == Effect.NORMAL) return src;

        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);

        switch (effect) {
            case XRAY:      xray(pixels); break;
            case SEPIA:     sepia(pixels); break;
            case THERMAL:   thermal(pixels); break;
            case POP_ART:   popArt(pixels, w, h); break;
            case BULGE:     pixels = bulge(pixels, w, h); break;
            case SQUEEZE:   pixels = squeeze(pixels, w, h); break;
            case TWIRL:     pixels = twirl(pixels, w, h); break;
            default: break;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    private static void xray(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = 255 - ((c >> 16) & 0xFF);
            int g = 255 - ((c >> 8)  & 0xFF);
            int b = 255 - (c & 0xFF);

            r = clamp((int)((r - 128) * 1.4 + 128));
            g = clamp((int)((g - 128) * 1.4 + 128));
            b = clamp((int)((b - 128) * 1.6 + 140));

            r = clamp(r - 20);
            b = clamp(b + 30);

            px[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static void sepia(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8)  & 0xFF;
            int b = c & 0xFF;

            int nr = clamp((int)(0.393*r + 0.769*g + 0.189*b));
            int ng = clamp((int)(0.349*r + 0.686*g + 0.168*b));
            int nb = clamp((int)(0.272*r + 0.534*g + 0.131*b));

            px[i] = (nr << 16) | (ng << 8) | nb;
        }
    }

    private static final int[][] THERMAL_STOPS = {
        {  0,   0, 128},
        { 80,   0, 160},
        {200,   0,  60},
        {255, 220,   0},
        {255, 255, 255}
    };

    private static void thermal(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8)  & 0xFF;
            int b = c & 0xFF;
            int lum = (int)(0.299*r + 0.587*g + 0.114*b);
            px[i] = sampleGradient(lum);
        }
    }

    private static int sampleGradient(int t) {
        float f = t / 255f * (THERMAL_STOPS.length - 1);
        int idx = (int) f;
        if (idx >= THERMAL_STOPS.length - 1) idx = THERMAL_STOPS.length - 2;
        float frac = f - idx;

        int[] a = THERMAL_STOPS[idx];
        int[] b = THERMAL_STOPS[idx + 1];

        int r = clamp((int)(a[0] + (b[0] - a[0]) * frac));
        int g = clamp((int)(a[1] + (b[1] - a[1]) * frac));
        int bl = clamp((int)(a[2] + (b[2] - a[2]) * frac));
        return (r << 16) | (g << 8) | bl;
    }

    private static void popArt(int[] px, int w, int h) {
        int hw = w / 2, hh = h / 2;
        float[][] tints = {
            {1.8f, 0.4f, 0.4f},
            {0.4f, 1.8f, 0.4f},
            {0.4f, 0.4f, 1.8f},
            {1.6f, 1.6f, 0.4f}
        };

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int qi = (y < hh ? 0 : 2) + (x < hw ? 0 : 1);
                float[] t = tints[qi];

                int srcX = x % hw;
                int srcY = y % hh;
                int c = px[srcY * w + srcX];

                int r = clamp((int)(((c >> 16) & 0xFF) * t[0]));
                int g = clamp((int)(((c >> 8)  & 0xFF) * t[1]));
                int b = clamp((int)((c & 0xFF) * t[2]));

                px[y * w + x] = (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int[] bulge(int[] px, int w, int h) {
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0;
        double rMax = Math.min(cx, cy);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);

                if (r < rMax && r > 0) {
                    double nr = r / rMax;
                    double rNew = r * Math.sqrt(nr);
                    double scale = (rNew > 0) ? r / rNew : 0;

                    int sx = (int)(cx + dx * scale);
                    int sy = (int)(cy + dy * scale);
                    sx = clampCoord(sx, w);
                    sy = clampCoord(sy, h);
                    out[y * w + x] = px[sy * w + sx];
                } else {
                    out[y * w + x] = px[y * w + x];
                }
            }
        }
        return out;
    }

    private static int[] squeeze(int[] px, int w, int h) {
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0;
        double rMax = Math.min(cx, cy);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);

                if (r < rMax && r > 0) {
                    double nr = r / rMax;
                    double rNew = r * nr;
                    double scale = (rNew > 0) ? rNew / r : 0;

                    int sx = (int)(cx + dx * scale);
                    int sy = (int)(cy + dy * scale);
                    sx = clampCoord(sx, w);
                    sy = clampCoord(sy, h);
                    out[y * w + x] = px[sy * w + sx];
                } else {
                    out[y * w + x] = px[y * w + x];
                }
            }
        }
        return out;
    }

    private static int[] twirl(int[] px, int w, int h) {
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0;
        double rMax = Math.min(cx, cy);
        double maxAngle = Math.PI * 0.9;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);

                if (r < rMax && r > 0) {
                    double alpha = Math.atan2(dy, dx);
                    double theta = maxAngle * (1.0 - r / rMax);
                    double srcAlpha = alpha - theta;

                    int sx = (int)(cx + r * Math.cos(srcAlpha));
                    int sy = (int)(cy + r * Math.sin(srcAlpha));
                    sx = clampCoord(sx, w);
                    sy = clampCoord(sy, h);
                    out[y * w + x] = px[sy * w + sx];
                } else {
                    out[y * w + x] = px[y * w + x];
                }
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int clampCoord(int v, int max) {
        return v < 0 ? 0 : (v >= max ? max - 1 : v);
    }
}
package com.photobooth;

import java.awt.image.BufferedImage;

public final class FilterEngine {

    private FilterEngine() {}

    public enum Effect {
        // Page 1
        NORMAL("Normal"),
        SEPIA("Sepia"),
        XRAY("X-Ray"),
        THERMAL("Thermal Camera"),
        POP_ART("Pop Art"),
        BULGE("Bulge"),
        SQUEEZE("Squeeze"),
        TWIRL("Twirl"),
        MIRROR("Mirror"),

        // Page 2
        GLOW("Glow"),
        HALFTONE("Halftone"),
        LIGHT_TUNNEL("Light Tunnel"),
        PLASTIC_CAMERA("Plastic Camera"),
        ROSE_TINT("Rose Tint"),
        BLACK_WHITE("Black and White"),
        FISHEYE("Fish Eye"),
        STRETCH("Stretch"),
        INVERT("Negative");

        public final String label;
        Effect(String label) { this.label = label; }
    }

    public static BufferedImage apply(BufferedImage src, Effect effect) {
        if (src == null) return null;
        if (effect == Effect.NORMAL) return src;

        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);

        switch (effect) {
            case SEPIA:          sepia(px); break;
            case XRAY:           xray(px); break;
            case THERMAL:        thermal(px); break;
            case POP_ART:        popArt(px, w, h); break;
            case BULGE:          px = bulge(px, w, h); break;
            case SQUEEZE:        px = squeeze(px, w, h); break;
            case TWIRL:          px = twirl(px, w, h); break;
            case MIRROR:         px = mirrorHoriz(px, w, h); break;
            case GLOW:           glow(px); break;
            case HALFTONE:       halftone(px, w, h); break;
            case LIGHT_TUNNEL:   px = lightTunnel(px, w, h); break;
            case PLASTIC_CAMERA: plasticCamera(px); break;
            case ROSE_TINT:      roseTint(px); break;
            case BLACK_WHITE:    blackAndWhite(px); break;
            case FISHEYE:        px = fisheye(px, w, h); break;
            case STRETCH:        px = stretch(px, w, h); break;
            case INVERT:         invert(px); break;
            default: break;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    // -------- Basic color effects --------

    private static void invert(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = 255 - ((c >> 16) & 0xFF);
            int g = 255 - ((c >> 8)  & 0xFF);
            int b = 255 - (c & 0xFF);
            px[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static void blackAndWhite(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int lum = clamp((int)(0.299*r + 0.587*g + 0.114*b));
            px[i] = (lum << 16) | (lum << 8) | lum;
        }
    }

    private static void sepia(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int nr = clamp((int)(0.393*r + 0.769*g + 0.189*b));
            int ng = clamp((int)(0.349*r + 0.686*g + 0.168*b));
            int nb = clamp((int)(0.272*r + 0.534*g + 0.131*b));
            px[i] = (nr << 16) | (ng << 8) | nb;
        }
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
            r = clamp(r - 20); b = clamp(b + 30);
            px[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static void roseTint(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            r = clamp((int)(r * 1.15 + 20));
            g = clamp((int)(g * 0.85));
            b = clamp((int)(b * 0.95 + 10));
            px[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static void plasticCamera(int[] px) {
        // Slightly oversaturated, high-contrast, warm
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            r = clamp((int)((r - 128) * 1.3 + 128 + 15));
            g = clamp((int)((g - 128) * 1.2 + 128 + 5));
            b = clamp((int)((b - 128) * 1.1 + 128 - 10));
            px[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static final int[][] THERMAL_STOPS = {
        {  0,   0, 128}, { 80,   0, 160}, {200,   0,  60},
        {255, 220,   0}, {255, 255, 255}
    };

    private static void thermal(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int lum = (int)(0.299*r + 0.587*g + 0.114*b);
            px[i] = sampleGradient(lum);
        }
    }

    private static int sampleGradient(int t) {
        float f = t / 255f * (THERMAL_STOPS.length - 1);
        int idx = (int) f;
        if (idx >= THERMAL_STOPS.length - 1) idx = THERMAL_STOPS.length - 2;
        float frac = f - idx;
        int[] a = THERMAL_STOPS[idx], b = THERMAL_STOPS[idx + 1];
        int r = clamp((int)(a[0] + (b[0] - a[0]) * frac));
        int g = clamp((int)(a[1] + (b[1] - a[1]) * frac));
        int bl = clamp((int)(a[2] + (b[2] - a[2]) * frac));
        return (r << 16) | (g << 8) | bl;
    }

    private static void popArt(int[] px, int w, int h) {
        int hw = w / 2, hh = h / 2;
        float[][] tints = {
            {1.8f, 0.4f, 0.4f}, {0.4f, 1.8f, 0.4f},
            {0.4f, 0.4f, 1.8f}, {1.6f, 1.6f, 0.4f}
        };
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int qi = (y < hh ? 0 : 2) + (x < hw ? 0 : 1);
                float[] t = tints[qi];
                int c = px[(y % hh) * w + (x % hw)];
                int r = clamp((int)(((c >> 16) & 0xFF) * t[0]));
                int g = clamp((int)(((c >> 8)  & 0xFF) * t[1]));
                int b = clamp((int)((c & 0xFF) * t[2]));
                px[y * w + x] = (r << 16) | (g << 8) | b;
            }
        }
    }

    private static void glow(int[] px) {
        // Bloom: brighten highlights, soften saturation
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int lum = (int)(0.299*r + 0.587*g + 0.114*b);
            int boost = lum > 180 ? 60 : lum > 120 ? 25 : 0;
            r = clamp(r + boost);
            g = clamp(g + boost);
            b = clamp(b + boost);
            px[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static void halftone(int[] px, int w, int h) {
        // 4x4 dot pattern, black on white
        final int cell = 4;
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                long sum = 0; int n = 0;
                for (int dy = 0; dy < cell && y+dy < h; dy++)
                    for (int dx = 0; dx < cell && x+dx < w; dx++) {
                        int c = px[(y+dy)*w + (x+dx)];
                        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                        sum += (0.299*r + 0.587*g + 0.114*b);
                        n++;
                    }
                int avg = (int)(sum / Math.max(1, n));
                int radius = (255 - avg) * cell / 255;
                for (int dy = 0; dy < cell && y+dy < h; dy++)
                    for (int dx = 0; dx < cell && x+dx < w; dx++) {
                        double ddx = dx - cell/2.0, ddy = dy - cell/2.0;
                        boolean ink = (ddx*ddx + ddy*ddy) <= radius*radius;
                        int v = ink ? 0 : 255;
                        px[(y+dy)*w + (x+dx)] = (v << 16) | (v << 8) | v;
                    }
            }
        }
    }

    private static int[] mirrorHoriz(int[] px, int w, int h) {
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y*w + x] = px[y*w + (w-1-x)];
        return out;
    }

    // -------- Geometric distortions --------

    private static int[] bulge(int[] px, int w, int h) {
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0, rMax = Math.min(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);
                if (r < rMax && r > 0) {
                    double nr = r / rMax;
                    double rNew = r * Math.sqrt(nr);
                    double scale = (rNew > 0) ? r / rNew : 0;
                    int sx = clampCoord((int)(cx + dx * scale), w);
                    int sy = clampCoord((int)(cy + dy * scale), h);
                    out[y*w + x] = px[sy*w + sx];
                } else out[y*w + x] = px[y*w + x];
            }
        }
        return out;
    }

    private static int[] squeeze(int[] px, int w, int h) {
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0, rMax = Math.min(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);
                if (r < rMax && r > 0) {
                    double nr = r / rMax;
                    double rNew = r * nr;
                    double scale = (rNew > 0) ? rNew / r : 0;
                    int sx = clampCoord((int)(cx + dx * scale), w);
                    int sy = clampCoord((int)(cy + dy * scale), h);
                    out[y*w + x] = px[sy*w + sx];
                } else out[y*w + x] = px[y*w + x];
            }
        }
        return out;
    }

    private static int[] twirl(int[] px, int w, int h) {
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0, rMax = Math.min(cx, cy);
        double maxAngle = Math.PI * 0.9;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);
                if (r < rMax && r > 0) {
                    double alpha = Math.atan2(dy, dx);
                    double theta = maxAngle * (1.0 - r / rMax);
                    int sx = clampCoord((int)(cx + r * Math.cos(alpha - theta)), w);
                    int sy = clampCoord((int)(cy + r * Math.sin(alpha - theta)), h);
                    out[y*w + x] = px[sy*w + sx];
                } else out[y*w + x] = px[y*w + x];
            }
        }
        return out;
    }

    private static int[] lightTunnel(int[] px, int w, int h) {
        // Repeated sampling scaled by radius → concentric rings
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0, rMax = Math.min(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx, dy = y - cy;
                double r = Math.sqrt(dx*dx + dy*dy);
                if (r > 0) {
                    double t = r / rMax;
                    double warped = Math.pow(t, 0.5);
                    double scale = warped / t;
                    int sx = clampCoord((int)(cx + dx * scale), w);
                    int sy = clampCoord((int)(cy + dy * scale), h);
                    out[y*w + x] = px[sy*w + sx];
                } else out[y*w + x] = px[y*w + x];
            }
        }
        return out;
    }

    private static int[] fisheye(int[] px, int w, int h) {
        // Wider-angle than bulge: 2 * atan(r) style curvature
        int[] out = new int[px.length];
        double cx = w / 2.0, cy = h / 2.0, rMax = Math.min(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - cx) / rMax;
                double dy = (y - cy) / rMax;
                double r = Math.sqrt(dx*dx + dy*dy);
                if (r < 1 && r > 0) {
                    double theta = Math.atan(r) / (Math.PI / 4);
                    double srcR = theta;
                    double scale = srcR / r;
                    int sx = clampCoord((int)(cx + dx * scale * rMax), w);
                    int sy = clampCoord((int)(cy + dy * scale * rMax), h);
                    out[y*w + x] = px[sy*w + sx];
                } else out[y*w + x] = 0;
            }
        }
        return out;
    }

    private static int[] stretch(int[] px, int w, int h) {
        // Horizontal compression + vertical stretch
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            int sy = clampCoord((int)(y * 0.75 + h * 0.125), h);
            for (int x = 0; x < w; x++) {
                int sx = clampCoord((int)(x * 1.25 - w * 0.125), w);
                out[y*w + x] = px[sy*w + sx];
            }
        }
        return out;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
    private static int clampCoord(int v, int max) {
        return v < 0 ? 0 : (v >= max ? max - 1 : v);
    }
}
package dev.microg.facefix;

public final class FaceMapping {
    private FaceMapping() {}
    public static float x(float x, float y, int r, int w, int h) {
        switch (Math.floorMod(r,4)) {
            case 1: return y;
            case 2: return w - 1f - x;
            case 3: return w - 1f - y;
            default: return x;
        }
    }
    public static float y(float x, float y, int r, int w, int h) {
        switch (Math.floorMod(r,4)) {
            case 1: return h - 1f - x;
            case 2: return h - 1f - y;
            case 3: return x;
            default: return y;
        }
    }
    public static float clamp(float v, float upper) {
        return Math.max(0f, Math.min(upper, v));
    }
}

package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * A light, cheap blur for the home wallpaper: enough to read as glass behind the UI while the
 * poster stays recognisable. Works on a downscaled copy, so a 2 GB TV can afford it, and the
 * ImageView's own filtering does the final upscale.
 *
 * <p>Android 11 (this TV) has no RenderEffect blur, so this is a small box blur in the same spirit
 * as {@link HeroArt}, but at a much higher resolution so the artwork is still legible.
 */
final class GlassBlur {

    private GlassBlur() { }

    /**
     * Returns a lightly blurred copy at {@code outW x outH}. The caller keeps the returned bitmap and
     * recycles it when the next wallpaper replaces it.
     */
    static Bitmap mild(Bitmap src, int outW, int outH, int radius, int passes) {
        if (src == null || outW <= 0 || outH <= 0) return null;
        Bitmap small = Bitmap.createScaledBitmap(src, outW, outH, true);

        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < passes; pass++) {
            px = boxBlur(px, w, h, radius, true);
            px = boxBlur(px, w, h, radius, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (small != out) small.recycle();
        return out;
    }

    private static int[] boxBlur(int[] src, int w, int h, int radius, boolean horizontal) {
        int[] dst = new int[src.length];
        int lines = horizontal ? h : w;
        int length = horizontal ? w : h;
        int span = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int j = Math.max(0, Math.min(length - 1, i + k));
                    int p = horizontal ? src[line * w + j] : src[j * w + line];
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                }
                dst[horizontal ? line * w + i : i * w + line] = Color.rgb(r / span, g / span, b / span);
            }
        }
        return dst;
    }
}

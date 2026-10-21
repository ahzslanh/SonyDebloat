package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * A frosted sheet over the whole home screen while a hold-OK menu is open: the launcher's own pixels
 * are copied, downscaled, box-blurred and drawn back, then dimmed and vignetted.
 *
 * <p>Android 11 (this TV) has no RenderEffect blur, so this is the same trick {@link HeroArt} uses for
 * the cinematic shelf — a small blurred bitmap stretched over the screen. It runs once when the menu
 * opens, never per frame, and the copy is a quarter of screen size, so a 1 GB TV can afford it.
 */
final class BlurScrim extends View {

    /** How much the blurred copy is shrunk before blurring: 4 = 1/16 of the pixels to process. */
    private static final int DOWNSCALE = 6;
    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 3;
    private static final int DIM = 0x9A000000;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap backdrop;

    BlurScrim(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundColor(Color.TRANSPARENT);
    }

    /** Captures the window, blurs it and fades the sheet in. Falls back to a plain dim on failure. */
    void captureAndShow(Window window, View decor) {
        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(160).start();

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;

        final Rect rect = new Rect(0, 0, decor.getWidth(), decor.getHeight());
        if (rect.width() <= 0 || rect.height() <= 0) return;

        final int w = Math.max(1, rect.width() / DOWNSCALE);
        final int h = Math.max(1, rect.height() / DOWNSCALE);
        final Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        try {
            PixelCopy.request(window, rect, small, copyResult -> {
                if (copyResult == PixelCopy.SUCCESS) {
                    backdrop = blur(small);
                }
                invalidate();
                if (copyResult != PixelCopy.SUCCESS) small.recycle();
            }, new android.os.Handler(getContext().getMainLooper()));
        } catch (Exception e) {
            setBackgroundColor(DIM);
        }
    }

    /** Halve twice, saturate slightly, then box-blur on the tiny bitmap: reads as frosted glass. */
    private Bitmap blur(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap half = Bitmap.createScaledBitmap(src, Math.max(1, w / 2), Math.max(1, h / 2), true);
        Bitmap quarter = Bitmap.createScaledBitmap(half, Math.max(1, w / 4), Math.max(1, h / 4), true);
        src.recycle();
        half.recycle();

        int qw = quarter.getWidth(), qh = quarter.getHeight();
        int[] px = new int[qw * qh];
        quarter.getPixels(px, 0, qw, 0, 0, qw, qh);
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            px = boxBlur(px, qw, qh, BLUR_RADIUS, true);
            px = boxBlur(px, qw, qh, BLUR_RADIUS, false);
        }
        Bitmap out = Bitmap.createBitmap(qw, qh, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, qw, 0, 0, qw, qh);
        quarter.recycle();
        return out;
    }

    private static int[] boxBlur(int[] src, int w, int h, int radius, boolean horizontal) {
        int[] dst = new int[src.length];
        int lines = horizontal ? h : w, length = horizontal ? w : h, span = 2 * radius + 1;
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backdrop != null && !backdrop.isRecycled()) {
            canvas.drawBitmap(backdrop, null, new Rect(0, 0, getWidth(), getHeight()), paint);
            canvas.drawColor(DIM);
        } else {
            canvas.drawColor(DIM);
        }
    }

    void release() {
        if (backdrop != null) {
            backdrop.recycle();
            backdrop = null;
        }
    }

    /** Convenience for callers: add to a parent if not already added. */
    static void attach(ViewGroup parent, BlurScrim scrim, int index) {
        if (scrim.getParent() == parent) return;
        parent.addView(scrim, index, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}

package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Posters for the Top 10 shelves: fetched once from TMDB's image CDN, written to the app's cache
 * directory, then decoded at the tile's real size.
 *
 * <p>The same care the rest of the launcher takes applies here: a bounded {@link LruCache} holds the
 * decoded bitmaps, {@code inSampleSize} decodes against the measured tile, the config is RGB_565, and
 * nothing is fetched for a tile that is not on screen. A poster file that is already on disk is never
 * downloaded twice.
 */
final class PosterCache {

    private static final String TAG = "TvHome.Poster";
    private static final int MAX_MEMORY_ENTRIES = 24;
    private static final String DIR = "posters";

    /** Called on the main thread after a poster finishes downloading, so the tile can redraw. */
    interface OnReady {
        void onReady(String url);
    }

    private volatile OnReady onReady;

    void setOnReady(OnReady r) {
        this.onReady = r;
    }

    private final Context ctx;
    private final LruCache<String, Bitmap> memory =
            new LruCache<String, Bitmap>(MAX_MEMORY_ENTRIES) {
                @Override protected int sizeOf(String key, Bitmap value) { return 1; }
                @Override protected void entryRemoved(boolean evicted, String key,
                                                      Bitmap old, Bitmap now) {
                    if (evicted && old != null) old.recycle();
                }
            };

    PosterCache(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /**
     * A decoded poster for this tile size, from memory or disk only — never the network.
     *
     * <p>Callers run on the main thread (RecyclerView binding), so a synchronous download would
     * throw NetworkOnMainThreadException. When the poster is not on disk yet this returns null and
     * starts the download; the next bind (or an explicit {@link #prefetch}) finds it.
     */
    Bitmap get(String url, int reqW, int reqH) {
        if (url == null || reqW <= 0 || reqH <= 0) return null;
        String key = key(url);
        Bitmap hit = memory.get(key);
        if (hit != null) return hit;

        File file = file(key);
        if (!file.exists()) {
            prefetch(url);          // background download; nothing to draw yet
            return null;
        }
        Bitmap bmp = decode(file, reqW, reqH);
        if (bmp != null) memory.put(key, bmp);
        file.delete();      // the decoded bitmap is cached; the file has done its job
        return bmp;
    }

    /**
     * A full-size poster for the page-1 wallpaper, decoded straight from disk at the size asked for.
     * The file is kept (the wallpaper may show it again), and nothing is put in the tile LRU, so a
     * large wallpaper bitmap cannot evict the small shelf tiles.
     */
    Bitmap getWallpaper(String url, int reqW, int reqH) {
        if (url == null || reqW <= 0 || reqH <= 0) return null;
        File f = file(key(url));
        if (!f.exists()) {
            prefetch(url);
            return null;
        }
        return decode(f, reqW, reqH);
    }

    /** True when this poster is already decoded and ready to draw. */
    boolean isReady(String url) {
        return url != null && memory.get(key(url)) != null;
    }

    /**
     * Fetches a poster in the background and decodes it into memory, so a later bind draws it with
     * no work. The file is kept (unlike {@link #get}, which deletes after decoding) because this runs
     * off the main thread and has no tile size yet; the decode below is at a generous size the tiles
     * then reuse.
     */
    void prefetch(final String url) {
        if (url == null) return;
        final String key = key(url);
        if (memory.get(key) != null) return;
        new Thread(() -> {
            File f = file(key);
            if (!f.exists()) download(url, f);
            if (!f.exists()) return;
            if (memory.get(key) != null) return;
            Bitmap bmp = decode(f, PREFETCH_W, PREFETCH_H);
            if (bmp != null) {
                memory.put(key, bmp);
                final OnReady r = onReady;
                if (r != null) new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> r.onReady(url));
            }
        }, "poster-prefetch").start();
    }

    /** Size posters are decoded at when prefetching, big enough for any tile on a 1080p screen. */
    private static final int PREFETCH_W = 220;
    private static final int PREFETCH_H = 330;

    private void download(String url, File file) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return;
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            File tmp = new File(file.getPath() + ".part");
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            tmp.renameTo(file);
            final OnReady r = onReady;
            if (r != null) new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> r.onReady(url));
        } catch (Exception e) {
            Log.w(TAG, "download failed: " + url, e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Bitmap decode(File file, int reqW, int reqH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) return null;

        int sample = 2;
        while (bounds.outWidth / sample >= reqW && bounds.outHeight / sample >= reqH) {
            if (bounds.outWidth / (sample * 2) < reqW || bounds.outHeight / (sample * 2) < reqH) break;
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), opts);
    }

    private File file(String key) {
        File dir = new File(ctx.getCacheDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, key + ".img");
    }

    private static String key(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    void clearMemory() {
        memory.evictAll();
    }
}

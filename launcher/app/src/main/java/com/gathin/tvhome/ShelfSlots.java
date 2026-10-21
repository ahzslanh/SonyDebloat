package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Which two shelves the home screen shows, and in what order.
 *
 * <p>The owner swaps a shelf with the long-press menu on any poster; the choice is written to
 * {@code files/shelf_slots.json} so it survives restarts. An unreadable file falls back to the
 * default pair rather than hiding the shelves.
 */
final class ShelfSlots {

    private static final String TAG = "TvHome.ShelfSlots";
    private static final String FILE = "shelf_slots.json";
    /** How many shelves fit on one screen. */
    static final int SLOTS = 2;

    /** Netflix movies, then Netflix series. */
    private static final String[] DEFAULT = {"netflix_movies", "netflix_series"};

    private ShelfSlots() { }

    /** The source ids to show, always {@link #SLOTS} long. */
    static String[] load(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return DEFAULT.clone();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            JSONArray arr = new JSONObject(new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8))
                    .optJSONArray("slots");
            if (arr == null) return DEFAULT.clone();
            String[] out = new String[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                out[i] = i < arr.length() ? arr.optString(i, DEFAULT[i]) : DEFAULT[i];
            }
            return out;
        } catch (Exception e) {
            Log.w(TAG, "slots read failed", e);
            return DEFAULT.clone();
        }
    }

    /** Replaces one slot and saves. Returns the new full slot list. */
    static String[] set(Context ctx, int slot, String sourceId) {
        String[] slots = load(ctx);
        if (slot >= 0 && slot < SLOTS) slots[slot] = sourceId;
        save(ctx, slots);
        return slots;
    }

    static boolean save(Context ctx, String[] slots) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < SLOTS; i++) arr.put(slots[i]);
            JSONObject root = new JSONObject().put("slots", arr);
            try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), FILE))) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "slots write failed", e);
            return false;
        }
    }
}

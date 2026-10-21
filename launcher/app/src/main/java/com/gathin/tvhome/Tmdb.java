package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Top 10 movies / series, fetched from TMDB.
 *
 * <p>TMDB is the same source Kodi and Stremio shelves use. It needs a free API key: put it in
 * gradle.properties as {@code tmdb.apiKey=...} (or the TV's file {@code tmdb_key.txt}), and the
 * launcher fetches the trending list once per launch and caches the poster files on disk.
 *
 * <p>Nothing here blocks the UI: {@link #load} runs on a worker thread and hands the result back on
 * the main thread. When there is no key, or the network is down, the shelf stays empty — the home
 * screen is untouched.
 */
final class Tmdb {

    private static final String TAG = "TvHome.Tmdb";
    private static final String BASE = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/w342";
    /** How many posters a shelf shows: the top ten, as asked. */
    static final int COUNT = 10;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Item {
        final int rank;
        final int tmdbId;
        final String title;
        /** Full URL of the 2:3 poster, or null when TMDB has none (rare). */
        final String posterUrl;
        /** Release year, shown under the title. */
        final String year;

        Item(int rank, int tmdbId, String title, String posterUrl, String year) {
            this.rank = rank;
            this.tmdbId = tmdbId;
            this.title = title;
            this.posterUrl = posterUrl;
            this.year = year;
        }
    }

    interface Callback {
        void onLoaded(List<Item> movies, List<Item> series);
    }

    private Tmdb() {
    }

    /** Fetches both shelves off the main thread. */
    static void load(final String apiKey, final Callback cb) {
        if (apiKey == null || apiKey.isEmpty()) return;
        new Thread(() -> {
            List<Item> movies = fetch(apiKey, "movie");
            List<Item> series = fetch(apiKey, "tv");
            MAIN.post(() -> cb.onLoaded(movies, series));
        }, "tmdb-load").start();
    }

    /** Trending for the week, trimmed to {@link #COUNT}. */
    private static List<Item> fetch(String apiKey, String kind) {
        List<Item> out = new ArrayList<>();
        try {
            String body = get(BASE + "/trending/" + kind + "/week?api_key=" + apiKey);
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length() && out.size() < COUNT; i++) {
                JSONObject o = results.getJSONObject(i);
                String poster = o.optString("poster_path", null);
                if (poster == null || poster.isEmpty() || "null".equals(poster)) continue;
                String title = o.optString("title", o.optString("name", ""));
                if (title.isEmpty()) continue;
                String date = o.optString("release_date", o.optString("first_air_date", ""));
                String year = date.length() >= 4 ? date.substring(0, 4) : "";
                out.add(new Item(out.size() + 1, o.optInt("id"), title, IMG + poster, year));
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "fetch " + kind + " failed", e);
        }
        return out;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new RuntimeException("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}

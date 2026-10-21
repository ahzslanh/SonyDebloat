package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The three shelves, and the cache that keeps them off the network.
 *
 * <ul>
 *   <li><b>Netflix Movies</b> — Netflix's own Malaysia Top 10 movies, read from Tudum.</li>
 *   <li><b>Netflix Series</b> — Netflix's own Malaysia Top 10 shows, read from Tudum.</li>
 *   <li><b>TMDB Trending</b> — TMDB's weekly trending list.</li>
 * </ul>
 *
 * <p>Netflix publishes no API, but the Top 10 pages are public HTML; the titles are read from them
 * and each title's poster is looked up on TMDB, which is where the artwork comes from. Nothing needs
 * a Netflix login.
 *
 * <p><b>Caching.</b> Everything is written to {@code files/shelves.json} and reused until it is
 * stale ({@link #TTL_MS}, six hours). The launcher therefore draws the shelves instantly from disk
 * on every start, and only refetches when the cache is old or missing. A failed fetch keeps the
 * cached copy rather than emptying the shelves.
 */
final class Shelves {

    private static final String TAG = "TvHome.Shelves";
    private static final String FILE = "shelves.json";
    private static final String TUDUM = "https://www.netflix.com/tudum/top10/malaysia";
    private static final String TMDB_BASE = "https://api.themoviedb.org/3";
    private static final String TMDB_IMG = "https://image.tmdb.org/t/p/w342";

    /** How long a cached list is trusted: six hours, so it refreshes a few times a day. */
    static final long TTL_MS = 6 * 60 * 60 * 1000L;
    static final int COUNT = 10;

    /** One poster: a title and where its artwork lives. */
    static final class Item {
        final int rank;
        final String title;
        final String posterUrl;
        final String year;

        Item(int rank, String title, String posterUrl, String year) {
            this.rank = rank;
            this.title = title;
            this.posterUrl = posterUrl;
            this.year = year;
        }
    }

    /** A named rail of posters. */
    static final class Shelf {
        final String title;
        final List<Item> items;
        Shelf(String title, List<Item> items) { this.title = title; this.items = items; }
    }

    interface Callback {
        /** Always called on the main thread; cached=true when nothing was fetched. */
        void onShelves(List<Shelf> shelves, boolean cached);
    }

    private Shelves() { }

    /**
     * Returns the shelves. When the cache is fresh it is handed back at once; otherwise the cached
     * copy is shown first (so the home screen is never empty) and a refresh follows.
     */
    static void get(final Context ctx, final String tmdbKey, final Callback cb) {
        final File file = new File(ctx.getFilesDir(), FILE);
        final String cachedText = read(file);
        List<Shelf> cached = decode(cachedText);

        if (!cached.isEmpty() && !isStale(cachedText)) {
            cb.onShelves(cached, true);
            return;
        }
        if (!cached.isEmpty()) cb.onShelves(cached, true);   // show stale content while refreshing

        new Thread(() -> {
            List<Shelf> fresh = fetch(tmdbKey);
            if (fresh.isEmpty()) return;                     // keep the cached copy on failure
            write(file, encode(fresh));
            cb.onShelves(fresh, false);
        }, "shelves-fetch").start();
    }

    /** Forces a refetch, ignoring the TTL (used by the Settings action). */
    static void refresh(final Context ctx, final String tmdbKey, final Callback cb) {
        new Thread(() -> {
            List<Shelf> fresh = fetch(tmdbKey);
            if (fresh.isEmpty()) return;
            write(new File(ctx.getFilesDir(), FILE), encode(fresh));
            cb.onShelves(fresh, false);
        }, "shelves-refresh").start();
    }

    private static boolean isStale(String text) {
        if (text == null) return true;
        try {
            long at = new JSONObject(text).optLong("fetchedAt", 0);
            return System.currentTimeMillis() - at > TTL_MS;
        } catch (Exception e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ fetching

    private static List<Shelf> fetch(String tmdbKey) {
        List<Shelf> out = new ArrayList<>();
        List<Item> movies = netflixTudum(shelvesUrl(TUDUM), "movie", tmdbKey);
        if (!movies.isEmpty()) out.add(new Shelf("Top 10 Movies on Netflix", movies));
        List<Item> shows = netflixTudum(shelvesUrl(TUDUM) + "/tv", "tv", tmdbKey);
        if (!shows.isEmpty()) out.add(new Shelf("Top 10 Series on Netflix", shows));
        List<Item> trending = tmdbTrending(tmdbKey, "movie");
        if (!trending.isEmpty()) out.add(new Shelf("Trending on TMDB", trending));
        return out;
    }

    private static String shelvesUrl(String base) {
        return base;
    }

    /**
     * Netflix's Top 10 for Malaysia: the titles are read from the public Tudum page, then each one is
     * matched on TMDB for a poster. Titles TMDB cannot find are dropped rather than shown artless.
     */
    private static List<Item> netflixTudum(String url, String tmdbKind, String tmdbKey) {
        List<Item> out = new ArrayList<>();
        try {
            String html = get(url);
            List<String> titles = titles(html);
            for (String t : titles) {
                if (out.size() >= COUNT) break;
                String[] hit = tmdbLookup(tmdbKey, tmdbKind, t);
                if (hit == null) continue;
                out.add(new Item(out.size() + 1, t, hit[0], hit[1]));
            }
        } catch (Exception e) {
            Log.w(TAG, "tudum failed: " + url, e);
        }
        return out;
    }

    private static final Pattern TITLE = Pattern.compile("\"title\":\"([^\"]{2,70})\"");
    /** Rows that are headings or UI copy, not a title. */
    private static final Pattern NOT_A_TITLE = Pattern.compile(
            "^(Top 10|Limited Series|Season \\d|Volume \\d|Part \\d).*", Pattern.CASE_INSENSITIVE);

    private static List<String> titles(String html) {
        List<String> out = new ArrayList<>();
        Matcher m = TITLE.matcher(html);
        while (m.find() && out.size() < 40) {
            String t = m.group(1).trim();
            t = t.replaceAll("\\s*:\\s*(Season|Limited Series|Volume|Part)\\b.*$", "").trim();
            if (t.isEmpty() || NOT_A_TITLE.matcher(t).matches()) continue;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    private static List<Item> tmdbTrending(String key, String kind) {
        List<Item> out = new ArrayList<>();
        try {
            String body = get(TMDB_BASE + "/trending/" + kind + "/week?api_key=" + key);
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length() && out.size() < COUNT; i++) {
                JSONObject o = results.getJSONObject(i);
                String poster = o.optString("poster_path", "");
                String title = o.optString("title", o.optString("name", ""));
                if (poster.isEmpty() || title.isEmpty()) continue;
                String date = o.optString("release_date", o.optString("first_air_date", ""));
                out.add(new Item(out.size() + 1, title, TMDB_IMG + poster,
                        date.length() >= 4 ? date.substring(0, 4) : ""));
            }
        } catch (Exception e) {
            Log.w(TAG, "tmdb trending failed", e);
        }
        return out;
    }

    /** TMDB search for one title: {posterUrl, year}, or null when nothing matches. */
    private static String[] tmdbLookup(String key, String kind, String title) {
        if (key == null || key.isEmpty()) return null;
        try {
            String body = get(TMDB_BASE + "/search/" + kind + "?api_key=" + key
                    + "&query=" + android.net.Uri.encode(title));
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject o = results.getJSONObject(0);
            String poster = o.optString("poster_path", "");
            if (poster.isEmpty()) return null;
            String date = o.optString("release_date", o.optString("first_air_date", ""));
            return new String[]{TMDB_IMG + poster, date.length() >= 4 ? date.substring(0, 4) : ""};
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(9000);
        c.setReadTimeout(9000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");
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

    // ------------------------------------------------------------------ cache file

    private static String encode(List<Shelf> shelves) {
        try {
            JSONObject root = new JSONObject();
            root.put("fetchedAt", System.currentTimeMillis());
            root.put("version", 1);
            JSONArray arr = new JSONArray();
            for (Shelf s : shelves) {
                JSONObject so = new JSONObject();
                so.put("title", s.title);
                JSONArray items = new JSONArray();
                for (Item i : s.items) {
                    JSONObject io = new JSONObject();
                    io.put("rank", i.rank);
                    io.put("title", i.title);
                    io.put("poster", i.posterUrl);
                    io.put("year", i.year);
                    items.put(io);
                }
                so.put("items", items);
                arr.put(so);
            }
            root.put("shelves", arr);
            return root.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Shelf> decode(String text) {
        List<Shelf> out = new ArrayList<>();
        if (text == null) return out;
        try {
            JSONArray arr = new JSONObject(text).optJSONArray("shelves");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject so = arr.getJSONObject(i);
                JSONArray items = so.optJSONArray("items");
                List<Item> list = new ArrayList<>();
                if (items != null) {
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject io = items.getJSONObject(j);
                        list.add(new Item(io.optInt("rank", j + 1), io.optString("title"),
                                io.optString("poster"), io.optString("year")));
                    }
                }
                if (!list.isEmpty()) out.add(new Shelf(so.optString("title"), list));
            }
        } catch (Exception e) {
            Log.w(TAG, "cache decode failed", e);
        }
        return out;
    }

    private static String read(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(File f, String text) {
        if (text == null) return;
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "cache write failed", e);
        }
    }
}

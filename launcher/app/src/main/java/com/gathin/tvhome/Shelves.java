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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every shelf the launcher can show, plus the cache that keeps them off the network.
 *
 * <p>A "source" is one rail of ten posters. The home screen shows two of them at a time, and the
 * owner can swap either slot for any other source with the long-press menu on a poster.
 *
 * <ul>
 *   <li><b>Netflix Movies</b> — Netflix's own Malaysia Top 10 movies, read from Tudum.</li>
 *   <li><b>Netflix Series</b> — Netflix's own Malaysia Top 10 shows, read from Tudum.</li>
 *   <li><b>TMDB Movies</b> — TMDB's weekly trending movies.</li>
 *   <li><b>TMDB Series</b> — TMDB's weekly trending shows.</li>
 * </ul>
 *
 * <p>Netflix publishes no API, but the Top 10 pages are public HTML; the titles are read from them
 * and each title's poster is looked up on TMDB, which is where the artwork comes from. Nothing needs
 * a Netflix login.
 *
 * <p><b>Caching.</b> Every source is written to {@code files/shelves.json} and reused until it is
 * stale ({@link #TTL_MS}, six hours). The launcher draws the shelves instantly from disk on every
 * start, and only refetches when the cache is old or missing. A failed fetch keeps the cached copy.
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

    /** One rail of posters, with a stable id so a slot can point at it and the owner can swap it. */
    static final class Source {
        final String id;
        final String title;
        final List<Item> items;
        Source(String id, String title, List<Item> items) {
            this.id = id; this.title = title; this.items = items;
        }
    }

    /** The ids the launcher knows how to build, in the order the swap menu lists them. */
    static final String[] ALL_IDS = {
            "netflix_movies", "netflix_series", "tmdb_movies", "tmdb_series"
    };

    /** Title for a source id, for the swap menu (so the menu works before a fetch completes). */
    static String titleOf(String id) {
        switch (id) {
            case "netflix_movies": return "Top 10 Movies on Netflix";
            case "netflix_series": return "Top 10 Series on Netflix";
            case "tmdb_movies":    return "Trending Movies on TMDB";
            case "tmdb_series":    return "Trending Series on TMDB";
            default:               return id;
        }
    }

    interface Callback {
        /** Always called on the main thread; cached=true when nothing was fetched. */
        void onCatalog(Map<String, Source> catalog, boolean cached);
    }

    private Shelves() { }

    /**
     * Returns every source, keyed by id. When the cache is fresh it is handed back at once; otherwise
     * the cached copy is shown first (so the home screen is never empty) and a refresh follows.
     */
    static void get(final Context ctx, final String tmdbKey, final Callback cb) {
        final File file = new File(ctx.getFilesDir(), FILE);
        final String cachedText = read(file);
        Map<String, Source> cached = decode(cachedText);

        if (!cached.isEmpty() && !isStale(cachedText)) {
            cb.onCatalog(cached, true);
            return;
        }
        if (!cached.isEmpty()) cb.onCatalog(cached, true);   // show stale content while refreshing

        new Thread(() -> {
            Map<String, Source> fresh = fetch(tmdbKey);
            if (fresh.isEmpty()) return;                     // keep the cached copy on failure
            write(file, encode(fresh));
            cb.onCatalog(fresh, false);
        }, "shelves-fetch").start();
    }

    /** Forces a refetch, ignoring the TTL (used by the Settings action). */
    static void refresh(final Context ctx, final String tmdbKey, final Callback cb) {
        new Thread(() -> {
            Map<String, Source> fresh = fetch(tmdbKey);
            if (fresh.isEmpty()) return;
            write(new File(ctx.getFilesDir(), FILE), encode(fresh));
            cb.onCatalog(fresh, false);
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

    private static Map<String, Source> fetch(String tmdbKey) {
        Map<String, Source> out = new LinkedHashMap<>();
        List<Item> movies = netflixTudum(TUDUM, "movie", tmdbKey);
        if (!movies.isEmpty()) out.put("netflix_movies", new Source("netflix_movies", titleOf("netflix_movies"), movies));
        List<Item> shows = netflixTudum(TUDUM + "/tv", "tv", tmdbKey);
        if (!shows.isEmpty()) out.put("netflix_series", new Source("netflix_series", titleOf("netflix_series"), shows));
        List<Item> tmdbMovies = tmdbTrending(tmdbKey, "movie");
        if (!tmdbMovies.isEmpty()) out.put("tmdb_movies", new Source("tmdb_movies", titleOf("tmdb_movies"), tmdbMovies));
        List<Item> tmdbSeries = tmdbTrending(tmdbKey, "tv");
        if (!tmdbSeries.isEmpty()) out.put("tmdb_series", new Source("tmdb_series", titleOf("tmdb_series"), tmdbSeries));
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

    private static String encode(Map<String, Source> catalog) {
        try {
            JSONObject root = new JSONObject();
            root.put("fetchedAt", System.currentTimeMillis());
            root.put("version", 2);
            JSONArray arr = new JSONArray();
            for (Source src : catalog.values()) {
                JSONObject so = new JSONObject();
                so.put("id", src.id);
                so.put("title", src.title);
                JSONArray items = new JSONArray();
                for (Item i : src.items) {
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
            root.put("sources", arr);
            return root.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Source> decode(String text) {
        Map<String, Source> out = new LinkedHashMap<>();
        if (text == null) return out;
        try {
            JSONObject root = new JSONObject(text);
            JSONArray arr = root.optJSONArray("sources");
            if (arr == null) {
                // Old single-list format: give what it had back under the Netflix ids so a stale
                // cache from a previous build still shows something until the first refresh.
                arr = root.optJSONArray("shelves");
                if (arr == null) return out;
            }
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
                if (list.isEmpty()) continue;
                String id = so.optString("id", "");
                String title = so.optString("title", "");
                // Old format had no id: infer from the title.
                if (id.isEmpty()) {
                    if (title.contains("Movies on Netflix")) id = "netflix_movies";
                    else if (title.contains("Series on Netflix")) id = "netflix_series";
                    else if (title.contains("TMDB")) id = "tmdb_movies";
                }
                if (id.isEmpty()) continue;
                out.put(id, new Source(id, title.isEmpty() ? titleOf(id) : title, list));
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

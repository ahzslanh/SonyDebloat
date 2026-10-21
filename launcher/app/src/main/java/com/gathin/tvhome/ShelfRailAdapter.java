package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The poster rail inside one Top 10 shelf.
 *
 * <p>Vertical 2:3 posters, ranked 1..10, sliding horizontally as the D-pad moves. Each poster is
 * decoded at its measured size from {@link PosterCache}; neighbours are prefetched so moving right
 * does not wait on the network.
 */
final class ShelfRailAdapter extends RecyclerView.Adapter<ShelfRailAdapter.PosterVh> {

    /** Poster width in dp; the height follows the 2:3 ratio. */
    private static final int POSTER_W_DP = 84;
    private static final float FOCUS_SCALE = 1.08f;
    /** Name line under a poster, and the rail's vertical padding. */
    private static final int NAME_DP = 20;
    private static final int PAD_DP = 8;

    /** Height a rail needs for its posters: 2:3 poster + name + padding. */
    static int railHeightDp() {
        return Math.round(POSTER_W_DP * 3f / 2f) + NAME_DP + PAD_DP;
    }

    interface Listener {
        /** A poster was activated: open this title. */
        void onPosterClicked(Shelves.Item item);

        /** A poster gained focus: the backdrop follows it. */
        void onPosterFocused(Shelves.Item item);
    }

    private final List<Shelves.Item> items;
    private final PosterCache cache;
    private final Listener listener;

    ShelfRailAdapter(List<Shelves.Item> items, PosterCache cache, Listener listener) {
        this.items = items;
        this.cache = cache;
        this.listener = listener;
    }

    final class PosterVh extends RecyclerView.ViewHolder {
        final View card;
        final ImageView poster;
        final TextView rank;
        final TextView name;

        PosterVh(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            poster = v.findViewById(R.id.banner);
            rank = v.findViewById(R.id.rank);
            name = v.findViewById(R.id.name);
        }
    }

    @NonNull
    @Override
    public PosterVh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_poster, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int w = Math.round(POSTER_W_DP * density);
        int h = Math.round(w * 3f / 2f);                 // 2:3 poster

        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        lp.width = w;
        lp.height = h;
        card.setLayoutParams(lp);

        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nlp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nlp.width = w;
        nlp.topMargin = h + Math.round(6 * density);
        name.setLayoutParams(nlp);

        return new PosterVh(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PosterVh h, int position) {
        final Shelves.Item item = items.get(position);

        h.name.setText(item.year.isEmpty() ? item.title : item.title + " (" + item.year + ")");
        h.rank.setText("#" + item.rank);

        // Focus: zoom the tile, reveal the name, and drive the backdrop.
        h.card.setOnFocusChangeListener((v, focused) -> {
            if (!listenerFocusAnimations()) return;
            v.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                    .scaleY(focused ? FOCUS_SCALE : 1f).setDuration(140).start();
            h.name.setAlpha(focused ? 1f : 0f);
            if (focused) listener.onPosterFocused(item);
        });
        h.card.setOnClickListener(v -> listener.onPosterClicked(item));

        // Poster: measured-size decode, cache-first, skip if this holder was recycled onto another item.
        final ImageView target = h.poster;
        final String url = item.posterUrl;
        target.setTag(url);
        target.setImageDrawable(null);
        if (cache == null || url == null) return;

        Runnable load = () -> {
            if (!url.equals(target.getTag())) return;
            int w = target.getWidth(), hh = target.getHeight();
            if (w <= 0 || hh <= 0) return;
            Bitmap bmp = cache.get(url, w, hh);
            if (bmp != null && url.equals(target.getTag())) target.setImageBitmap(bmp);
        };
        if (target.getWidth() > 0) load.run();
        else target.post(load);

        // Prefetch a couple ahead so pressing RIGHT never waits on the network.
        for (int ahead = 1; ahead <= 2 && position + ahead < items.size(); ahead++) {
            cache.prefetch(items.get(position + ahead).posterUrl);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Motion preference, supplied by the caller; kept simple here (always on). */
    private boolean listenerFocusAnimations() {
        return true;
    }
}

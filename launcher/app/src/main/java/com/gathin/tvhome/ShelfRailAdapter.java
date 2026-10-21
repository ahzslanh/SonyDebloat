package com.gathin.tvhome;

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
 * The poster rail inside one shelf slot.
 *
 * <p>Vertical 2:3 posters, ranked 1..10. Right/Left walk the rail and stop at #10 / #1 — reaching an
 * end never spills into another shelf. Up/Down are reported to the listener, which owns the page
 * flip and the move between shelves.
 */
final class ShelfRailAdapter extends RecyclerView.Adapter<ShelfRailAdapter.PosterVh> {

    /** Poster width in dp; height follows the 2:3 ratio. Two shelves fit the screen at this size. */
    private static final int POSTER_W_DP = 88;
    private static final float FOCUS_SCALE = 1.06f;
    private static final int NAME_DP = 18;
    private static final int PAD_DP = 6;

    /** Height one rail needs: 2:3 poster + name line + breathing room. */
    static int railHeightDp() {
        return Math.round(POSTER_W_DP * 3f / 2f) + NAME_DP + PAD_DP;
    }

    interface Listener {
        void onPosterClicked(Shelves.Item item);
        void onPosterFocused(Shelves.Item item);
        /** Up or Down on a poster: the page flip / shelf move is the launcher's decision. */
        void onPosterVertical(int railIndex, int keyCode);
        /** Hold OK on a poster: offer to replace this shelf. */
        void onPosterLongPress(int railIndex, Shelves.Item item);
    }

    private final int railIndex;
    private final List<Shelves.Item> items;
    private final PosterCache cache;
    private final Listener listener;

    ShelfRailAdapter(int railIndex, List<Shelves.Item> items, PosterCache cache, Listener listener) {
        this.railIndex = railIndex;
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
        int h = Math.round(w * 3f / 2f);

        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        lp.width = w;
        lp.height = h;
        card.setLayoutParams(lp);

        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nlp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nlp.width = w;
        nlp.topMargin = h + Math.round(4 * density);
        name.setLayoutParams(nlp);

        return new PosterVh(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PosterVh h, int position) {
        final Shelves.Item item = items.get(position);

        h.name.setText(item.year.isEmpty() ? item.title : item.title + " (" + item.year + ")");
        h.rank.setText("#" + item.rank);

        h.card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? FOCUS_SCALE : 1f)
                    .scaleY(focused ? FOCUS_SCALE : 1f).setDuration(140).start();
            h.name.setAlpha(focused ? 1f : 0f);
            if (focused) listener.onPosterFocused(item);
        });
        h.card.setOnClickListener(v -> listener.onPosterClicked(item));
        h.card.setOnLongClickListener(v -> {
            listener.onPosterLongPress(railIndex, item);
            return true;
        });

        // Keys: Up/Down leave the rail; Left/Right stop at the ends so #10 never jumps shelves.
        h.card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                listener.onPosterVertical(railIndex, keyCode);
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && position == 0) return true;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    && position == items.size() - 1) return true;
            return false;
        });

        // Poster: measured-size decode, cache-first, guarded against a recycled holder.
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

        for (int ahead = 1; ahead <= 2 && position + ahead < items.size(); ahead++) {
            cache.prefetch(items.get(position + ahead).posterUrl);
        }
    }

    /** A poster finished downloading: rebind the visible tile showing it. */
    void posterReady(String url, RecyclerView rail) {
        if (url == null) return;
        for (int i = 0; i < rail.getChildCount(); i++) {
            View child = rail.getChildAt(i);
            int pos = rail.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION || pos >= items.size()) continue;
            if (url.equals(items.get(pos).posterUrl)) notifyItemChanged(pos);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}

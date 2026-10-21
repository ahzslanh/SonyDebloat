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
    private static final int NAME_DP = 22;

    /** Height one rail needs: the 2:3 poster plus the one-line title under it. */
    static int railHeightDp() {
        return Math.round(POSTER_W_DP * 3f / 2f) + NAME_DP;
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
    /** The RecyclerView this adapter drives, so a focused poster can be slid fully into view. */
    private RecyclerView rail;
    private final PosterCache cache;
    private final Listener listener;

    ShelfRailAdapter(int railIndex, List<Shelves.Item> items, PosterCache cache, Listener listener) {
        this.railIndex = railIndex;
        this.items = items;
        this.cache = cache;
        this.listener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        super.onAttachedToRecyclerView(rv);
        rail = rv;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView rv) {
        super.onDetachedFromRecyclerView(rv);
        if (rail == rv) rail = null;
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
        nlp.topMargin = h + Math.round(2 * density);
        name.setLayoutParams(nlp);

        // The rail gives every item an exact height, so the title under a poster can never be clipped.
        ViewGroup.LayoutParams rootLp = v.getLayoutParams();
        if (rootLp != null) {
            rootLp.height = Math.round(railHeightDp() * density);
            v.setLayoutParams(rootLp);
        }
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
            if (focused) {
                listener.onPosterFocused(item);
                revealFully(h);
            }
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

    /**
     * Slides the rail so this poster is fully on screen, with a margin at either end. Without this,
     * the D-pad can move focus onto #10 while it is still half past the edge.
     */
    private void revealFully(final PosterVh h) {
        final RecyclerView rv = rail;
        if (rv == null) return;
        final View item = h.itemView;
        final int margin = Math.round(90 * rv.getResources().getDisplayMetrics().density);
        rv.post(() -> {
            if (rv.getAdapter() != this) return;
            int left = item.getLeft() - rv.getPaddingLeft();
            int right = item.getRight() - (rv.getWidth() - rv.getPaddingRight());
            if (left < margin) {
                rv.smoothScrollBy(left - margin, 0);
            } else if (right > -margin) {
                rv.smoothScrollBy(right + margin, 0);
            }
        });
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

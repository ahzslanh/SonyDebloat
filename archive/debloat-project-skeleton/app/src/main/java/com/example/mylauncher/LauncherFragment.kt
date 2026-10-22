package com.example.mylauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.LruCache
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

/**
 * "Apple Glass" style home screen.
 *
 *  * fixed 5-column grid – the whole catalogue fits on one screen, no scrolling
 *  * every poster is decoded once and kept in a bounded LRU cache
 *  * posters are down-sampled to their real on-screen size (no giant bitmaps)
 *  * only on-screen cells are ever handed to the adapter by the GridView itself
 *  * full D-pad support – the remote moves focus around the grid and SELECT opens
 */
class LauncherFragment : Fragment() {

    private lateinit var grid: GridView
    private lateinit var adapter: PosterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.launcher_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        grid = view.findViewById(R.id.poster_grid)
        grid.numColumns = COLUMNS
        adapter = PosterAdapter()
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, position, _ ->
            val item = LauncherData.items[position]
            Toast.makeText(
                context,
                "Opening ${item.title} (${item.provider})",
                Toast.LENGTH_SHORT
            ).show()
        }

        grid.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    v?.animate()?.scaleX(1.06f)?.scaleY(1.06f)?.setDuration(120)?.start()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        requestInitialFocus()
    }

    fun requestInitialFocus() {
        grid.post {
            if (grid.adapter != null && grid.adapter.count > 0) {
                grid.requestFocus()
                grid.setSelection(0)
            }
        }
    }

    fun onHomeBackPressed() {
        // Home screen: BACK does nothing (never returns to the stock launcher).
    }

    // ------------------------------------------------------------------
    // Adapter
    // ------------------------------------------------------------------
    private inner class PosterAdapter : BaseAdapter() {

        private val cache = PosterCache(MAX_CACHED_POSTERS)

        override fun getCount(): Int = LauncherData.items.size
        override fun getItem(position: Int): Any = LauncherData.items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.grid_item, parent, false)

            val item = LauncherData.items[position]
            val thumb = view.findViewById<ImageView>(R.id.thumb)
            val title = view.findViewById<TextView>(R.id.title)

            thumb.tag = item.id
            title.text = item.title
            view.background = parent.context.getDrawable(R.drawable.glass_focus)

            // Decode only once the view has a real measured size, otherwise the
            // bitmap would be scaled against 0 and we'd waste a decode.
            if (thumb.width > 0 && thumb.height > 0) {
                thumb.setImageBitmap(cache.get(item.id, thumb.width, thumb.height))
            } else {
                thumb.setImageDrawable(null)
                thumb.post {
                    if (thumb.tag == item.id && thumb.width > 0) {
                        thumb.setImageBitmap(cache.get(item.id, thumb.width, thumb.height))
                    }
                }
            }

            return view
        }
    }

    // ------------------------------------------------------------------
    // Bounded LRU poster cache with size-aware decoding
    // ------------------------------------------------------------------
    private inner class PosterCache(private val maxEntries: Int) : LruCache<String, Bitmap>(maxEntries) {

        fun get(id: String, reqW: Int, reqH: Int): Bitmap? {
            val key = "poster_$id"
            get(key)?.let { return it }
            val bmp = decodeScaled(id, reqW, reqH) ?: return null
            put(key, bmp)
            return bmp
        }

        override fun sizeOf(key: String, value: Bitmap): Int = 1

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) oldValue.recycle()
        }
    }

    private fun decodeScaled(id: String, reqW: Int, reqH: Int): Bitmap? {
        val resId = LauncherData.items.firstOrNull { it.id == id }?.drawableRes ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resId, opts)

        var sample = 1
        while (opts.outWidth / (sample * 2) >= reqW && opts.outHeight / (sample * 2) >= reqH) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeResource(resources, resId, decodeOpts)
    }

    companion object {
        private const val COLUMNS = 5
        private const val MAX_CACHED_POSTERS = 12
    }
}

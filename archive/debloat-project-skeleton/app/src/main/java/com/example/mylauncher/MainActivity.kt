package com.example.mylauncher

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Home entry point.
 *
 * Registered as the HOME activity (plus LEANBACK_LAUNCHER so it also shows up
 * in the app row). It hosts [LauncherFragment] and keeps the screen awake.
 */
class MainActivity : FragmentActivity() {

    private var fragment: LauncherFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val f = LauncherFragment()
        fragment = f
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, f)
            .commitNow()
    }

    /**
     * The remote's BACK key must never drop us back to the stock Google TV
     * launcher while we are the home screen.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            fragment?.onHomeBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fragment?.requestInitialFocus()
    }
}

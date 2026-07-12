package com.bytedance.tiktok.base

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * create by libo
 * create on 2020-05-19
 * description activity基类 — ImmersionBar replaced with standard APIs
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onCreate -> init()")
            init()
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.init() completed")
        } catch (e: Exception) {
            Log.e("JellyTok_CRASH", "${this::class.simpleName}.init() CRASH: ${e.message}", e)
        }
    }

    protected abstract fun init()

    protected fun setSystemBarColor(color: Int) {
        window.statusBarColor = color
    }

    protected fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.statusBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    protected fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    protected fun setExitAnimation(animId: Int) {
        overridePendingTransition(0, animId)
    }

    protected fun setFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.statusBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

package com.bytedance.tiktok.base

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.viewbinding.ViewBinding

/**
 * 支持View Binding Activity
 */
abstract class BaseBindingActivity<VB : ViewBinding>(
    val block: (LayoutInflater) -> VB
) : BaseActivity() {
    private var _binding: VB? = null
    protected val binding: VB
        get() = requireNotNull(_binding) { "The property of binding has been destroyed." }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onCreate binding inflate started")
            _binding = block(layoutInflater)
            setContentView(binding.root)
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onCreate binding inflated, calling super")
        } catch (e: Exception) {
            Log.e("JellyTok_CRASH", "${this::class.simpleName}.onCreate binding CRASH: ${e.message}", e)
            // Set a dummy view to prevent white screen
            setContentView(View(this))
        }
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}

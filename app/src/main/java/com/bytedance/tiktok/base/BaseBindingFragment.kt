package com.bytedance.tiktok.base

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseBindingFragment<VB : ViewBinding>(
    val block: (LayoutInflater) -> VB
) : Fragment() {
    private var _binding: VB? = null
    protected val binding: VB
        get() = requireNotNull(_binding) { "The property of binding has been destroyed." }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onCreateView() started")
            _binding = block(inflater)
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onCreateView() binding created")
            binding.root
        } catch (e: Exception) {
            Log.e("JellyTok_CRASH", "${this::class.simpleName}.onCreateView() CRASH: ${e.message}", e)
            // Return a dummy view to prevent crash
            View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onViewCreated() started")
            super.onViewCreated(view, savedInstanceState)
            Log.d("JellyTok_CRASH", "${this::class.simpleName}.onViewCreated() completed")
        } catch (e: Exception) {
            Log.e("JellyTok_CRASH", "${this::class.simpleName}.onViewCreated() CRASH: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

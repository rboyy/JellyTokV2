package com.bytedance.tiktok.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * ViewPager that disables horizontal swipe navigation.
 * Tab switching is handled programmatically via bottom menu clicks.
 */
class NoSwipeViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    override fun onTouchEvent(ev: MotionEvent?): Boolean = false
}

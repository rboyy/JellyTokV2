package com.bytedance.tiktok.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bytedance.tiktok.R
import com.bytedance.tiktok.utils.AnimUtils
import java.util.*

/**
 * create by libo
 * create on 2020-05-20
 * description 点赞动画view
 */
class LikeView : RelativeLayout {
    private var gestureDetector: GestureDetector? = null

    /** 图片大小  */
    private val likeViewSize = 330
    private val angles = intArrayOf(-30, 0, 30)

    /** 单击是否有点赞效果  */
    private val canSingleTabShow = false
    private var onPlayPauseListener: OnPlayPauseListener? = null
    private var onLikeListener: OnLikeListener? = null
    private var onSpeedChangeListener: OnSpeedChangeListener? = null

    // Long press for 2x speed
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPressing = false
    private val longPressRunnable = Runnable {
        isLongPressing = true
        onSpeedChangeListener?.onSpeedChange(2f)
    }

    // Horizontal swipe for seek
    private var isSeeking = false
    private var downX = 0f
    private var downY = 0f
    private var seekAnchorPositionMs = 0L
    private var seekProgressBar: com.bytedance.tiktok.view.VideoProgressBar? = null
    private var onSeekListener: OnSeekListener? = null
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    fun setSeekProgressBar(progressBar: com.bytedance.tiktok.view.VideoProgressBar?) {
        this.seekProgressBar = progressBar
    }

    fun setOnSeekListener(listener: OnSeekListener?) {
        this.onSeekListener = listener
    }

    private fun init() {
        gestureDetector = GestureDetector(object : SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                longPressHandler.postDelayed(longPressRunnable, 500)
                return true
            }

            override fun onShowPress(e: MotionEvent) {
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                isLongPressing = false
                onSpeedChangeListener?.onSpeedChange(1f)
                onPlayPauseListener?.onPlayOrPause()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                isLongPressing = false
                longPressHandler.removeCallbacks(longPressRunnable)
                onSpeedChangeListener?.onSpeedChange(1f)
                addLikeView(e)
                onLikeListener?.onLikeListener()
                return true
            }
        })
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isSeeking = false
                    longPressHandler.postDelayed(longPressRunnable, 500)
                    gestureDetector!!.onTouchEvent(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSeeking) {
                        // Continue seeking — lower sensitivity: full screen swipe = 67% of duration
                        val dx = event.x - downX
                        val seekSensitivity = width * 1.5f
                        val progressDelta = dx / seekSensitivity
                        val player = onSeekListener?.getPlayer()
                        if (player != null) {
                            val duration = player.duration.coerceAtLeast(1)
                            val newPositionMs = (seekAnchorPositionMs + progressDelta * duration)
                                .toLong().coerceIn(0, duration)
                            val newProgress = newPositionMs.toFloat() / duration
                            seekProgressBar?.setProgress(newProgress, duration)
                        }
                        true
                    } else {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        val absDx = Math.abs(dx)
                        val absDy = Math.abs(dy)
                        // Enter seek only when: horizontal threshold met AND movement is clearly horizontal
                        if (absDx > touchSlop * 3 && absDx > absDy * 2 && seekProgressBar != null) {
                            isSeeking = true
                            // Prevent parent (ViewPager2) from intercepting during seek
                            parent?.requestDisallowInterceptTouchEvent(true)
                            longPressHandler.removeCallbacks(longPressRunnable)
                            if (isLongPressing) {
                                isLongPressing = false
                                onSpeedChangeListener?.onSpeedChange(1f)
                            }
                            val player = onSeekListener?.getPlayer()
                            seekAnchorPositionMs = player?.currentPosition ?: 0L
                            onSeekListener?.onSeekStart()
                            true
                        } else if (absDy > touchSlop * 2 && absDy > absDx) {
                            // Primarily vertical movement — let parent handle vertical scroll
                            false
                        } else {
                            gestureDetector!!.onTouchEvent(event)
                            true
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (isSeeking) {
                        isSeeking = false
                        seekProgressBar?.finishSeek()
                        val player = onSeekListener?.getPlayer()
                        if (player != null) {
                            val duration = player.duration.coerceAtLeast(1)
                            val dx = event.x - downX
                            val seekSensitivity = width * 1.5f
                            val progressDelta = dx / seekSensitivity
                            val seekPositionMs = (seekAnchorPositionMs + progressDelta * duration)
                                .toLong().coerceIn(0, duration)
                            onSeekListener?.onSeekTo(seekPositionMs)
                        }
                        true
                    } else {
                        if (isLongPressing) {
                            isLongPressing = false
                            onSpeedChangeListener?.onSpeedChange(1f)
                        }
                        gestureDetector!!.onTouchEvent(event)
                        true
                    }
                }
                else -> {
                    gestureDetector!!.onTouchEvent(event)
                    true
                }
            }
        }
    }

    private fun addLikeView(e: MotionEvent) {
        val imageView = ImageView(context)
        imageView.setImageResource(R.mipmap.ic_like)
        addView(imageView)
        val layoutParams = LayoutParams(likeViewSize, likeViewSize)
        layoutParams.leftMargin = e.x.toInt() - likeViewSize / 2
        layoutParams.topMargin = e.y.toInt() - likeViewSize
        imageView.layoutParams = layoutParams
        playAnim(imageView)
    }

    private fun playAnim(view: View) {
        val animationSet = AnimationSet(true)
        val degrees = angles[Random().nextInt(3)]
        animationSet.addAnimation(AnimUtils.rotateAnim(0, 0, degrees.toFloat()))
        animationSet.addAnimation(AnimUtils.scaleAnim(100, 2f, 1f, 0))
        animationSet.addAnimation(AnimUtils.alphaAnim(0f, 1f, 100, 0))
        animationSet.addAnimation(AnimUtils.scaleAnim(500, 1f, 1.8f, 300))
        animationSet.addAnimation(AnimUtils.alphaAnim(1f, 0f, 500, 300))
        animationSet.addAnimation(AnimUtils.translationAnim(500, 0f, 0f, 0f, -400f, 300))
        animationSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                Handler().post { removeView(view) }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        view.startAnimation(animationSet)
    }

    interface OnPlayPauseListener {
        fun onPlayOrPause()
    }

    /**
     * 设置单机播放暂停事件
     * @param onPlayPauseListener
     */
    fun setOnPlayPauseListener(onPlayPauseListener: OnPlayPauseListener?) {
        this.onPlayPauseListener = onPlayPauseListener
    }

    interface OnLikeListener {
        fun onLikeListener()
    }

    /**
     * 设置双击点赞事件
     * @param onLikeListener
     */
    fun setOnLikeListener(onLikeListener: OnLikeListener?) {
        this.onLikeListener = onLikeListener
    }

    interface OnSpeedChangeListener {
        fun onSpeedChange(speed: Float)
    }

    fun setOnSpeedChangeListener(listener: OnSpeedChangeListener?) {
        this.onSpeedChangeListener = listener
    }

    interface OnSeekListener {
        fun getPlayer(): com.google.android.exoplayer2.SimpleExoPlayer?
        fun onSeekStart()
        fun onSeekTo(positionMs: Long)
    }
}
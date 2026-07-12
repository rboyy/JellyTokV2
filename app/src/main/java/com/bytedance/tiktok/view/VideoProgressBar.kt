package com.bytedance.tiktok.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom thin progress bar for video playback with drag-to-seek.
 * Positioned above the action buttons in the video feed.
 */
class VideoProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9F00.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9F00.toInt()
        style = Paint.Style.FILL
    }

    private var duration: Long = 0
    private var position: Long = 0
    private var isDragging = false
    private var listener: OnSeekListener? = null

    private val barHeight = 6f
    private val knobRadius = 12f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (knobRadius * 2 + 8).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val left = knobRadius + 4
        val right = width - knobRadius - 4
        val barWidth = right - left

        // Background track
        canvas.drawLine(left, cy, right.toFloat(), cy, bgPaint)

        // Foreground progress
        val progress = if (duration > 0) position.toFloat() / duration else 0f
        val progressX = left + barWidth * progress.coerceIn(0f, 1f)
        canvas.drawLine(left, cy, progressX, cy, fgPaint)

        // Knob (only when visible or dragging)
        if (duration > 0 || isDragging) {
            canvas.drawCircle(progressX, cy, knobRadius, knobPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (duration <= 0) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                listener?.onSeekStart()
                seekTo(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    seekTo(event.x)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val left = knobRadius + 4
                    val right = width - knobRadius - 4
                    val ratio = ((event.x - left) / (right - left)).coerceIn(0f, 1f)
                    listener?.onSeekTo((ratio * duration).toLong())
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun seekTo(x: Float) {
        val left = knobRadius + 4
        val right = width - knobRadius - 4
        val ratio = ((x - left) / (right - left)).coerceIn(0f, 1f)
        position = (ratio * duration).toLong()
        invalidate()
    }

    fun updateProgress(position: Long, duration: Long) {
        this.duration = duration
        if (!isDragging) {
            this.position = position
            invalidate()
        }
    }

    /**
     * 程序化设置进度（用于外部滑动手势控制）
     */
    fun setProgress(progress: Float, duration: Long) {
        this.duration = duration
        this.position = (progress.coerceIn(0f, 1f) * duration).toLong()
        isDragging = true
        invalidate()
    }

    fun finishSeek() {
        isDragging = false
    }

    fun reset() {
        duration = 0
        position = 0
        isDragging = false
        invalidate()
    }

    fun setOnSeekListener(listener: OnSeekListener?) {
        this.listener = listener
    }

    interface OnSeekListener {
        fun onSeekStart()
        fun onSeekTo(positionMs: Long)
    }
}

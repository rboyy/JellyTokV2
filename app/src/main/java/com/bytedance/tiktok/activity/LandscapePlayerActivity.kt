package com.bytedance.tiktok.activity

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bytedance.tiktok.R
import com.bytedance.tiktok.base.BaseActivity
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.view.VideoProgressBar
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

/**
 * Landscape fullscreen player for landscape-oriented videos.
 * Supports vertical swipe to navigate between videos in the list.
 * Long press for 2x speed, double tap to fast-forward 10s.
 * Returns the current video index and playback position to the calling activity.
 */
class LandscapePlayerActivity : BaseActivity() {

    companion object {
        const val EXTRA_VIDEO_LIST = "video_list"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_START_POSITION_MS = "start_position_ms"

        const val RESULT_CURRENT_INDEX = "current_index"
        const val RESULT_POSITION_MS = "position_ms"
        const val RESULT_ITEM_ID = "item_id"

        private const val FAST_FORWARD_MS = 10_000L
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: VideoProgressBar
    private lateinit var touchOverlay: View
    private lateinit var ivBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvSpeed: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false

    // Video list navigation
    private var videoList: ArrayList<VideoBean> = ArrayList()
    private var currentIndex = 0

    // Swipe state
    private var isSwipeSeeking = false
    private var isSwipeNavigating = false
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeAnchorMs = 0L

    // Long press 2x speed
    private var isLongPressing = false

    private val SWIPE_NAVIGATE_THRESHOLD = 100
    private val SWIPE_SEEK_THRESHOLD = 30

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isSeeking && !isSwipeNavigating && ::player.isInitialized && player.isPlaying) {
                val pos = player.currentPosition
                val dur = player.duration.coerceAtLeast(0)
                progressBar.updateProgress(pos, dur)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun init() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setFullScreen()
        keepScreenOn()
        setContentView(R.layout.activity_landscape_player)

        playerView = findViewById(R.id.playerView)
        progressBar = findViewById(R.id.progressBar)
        touchOverlay = findViewById(R.id.touchOverlay)
        ivBack = findViewById(R.id.ivBack)
        tvTitle = findViewById(R.id.tvTitle)
        tvHint = findViewById(R.id.tvHint)
        tvSpeed = findViewById(R.id.tvSpeed)

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        videoList = intent.getSerializableExtra(EXTRA_VIDEO_LIST) as? ArrayList<VideoBean> ?: ArrayList()
        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val startPos = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)

        if (videoList.isEmpty()) {
            finish()
            return
        }

        ivBack.setOnClickListener { finishWithResult() }

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

        loadVideo(currentIndex, startPos)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    progressBar.updateProgress(player.currentPosition, player.duration.coerceAtLeast(0))
                }
                if (state == Player.STATE_ENDED) {
                    if (currentIndex < videoList.size - 1) {
                        navigateToVideo(currentIndex + 1, 0)
                    }
                }
            }
        })

        progressBar.setOnSeekListener(object : VideoProgressBar.OnSeekListener {
            override fun onSeekStart() { isSeeking = true }
            override fun onSeekTo(positionMs: Long) {
                player.seekTo(positionMs)
                isSeeking = false
            }
        })

        setupTouchOverlay()
        handler.post(progressUpdater)
    }

    private fun setupTouchOverlay() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap → fast forward 10s
                val newPos = (player.currentPosition + FAST_FORWARD_MS)
                    .coerceAtMost(player.duration.coerceAtLeast(0))
                player.seekTo(newPos)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // Long press → 2x speed
                isLongPressing = true
                player.setPlaybackSpeed(2f)
                tvSpeed.visibility = View.VISIBLE
            }

            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Single tap → toggle play/pause
                if (isLongPressing) {
                    isLongPressing = false
                    player.setPlaybackSpeed(1f)
                    tvSpeed.visibility = View.GONE
                }
                if (player.isPlaying) player.pause() else player.play()
                return true
            }
        })

        touchOverlay.setOnTouchListener { _, event ->
            // Check for finger release — end long press speed
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (isLongPressing) {
                    isLongPressing = false
                    player.setPlaybackSpeed(1f)
                    tvSpeed.visibility = View.GONE
                }
            }

            // Let GestureDetector handle taps, double taps, long press
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }

            // Handle swipes (seek / navigate)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.x
                    swipeStartY = event.y
                    isSwipeSeeking = false
                    isSwipeNavigating = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - swipeStartX
                    val dy = event.y - swipeStartY

                    if (isSwipeNavigating) {
                        showNavigationHint(dy)
                    } else if (isSwipeSeeking) {
                        val sensitivity = touchOverlay.width / 2f
                        val delta = dx / sensitivity
                        val dur = player.duration.coerceAtLeast(1)
                        val newPos = (swipeAnchorMs + delta * dur).toLong().coerceIn(0, dur)
                        progressBar.setProgress(newPos.toFloat() / dur, dur)
                    } else if (!isLongPressing) {
                        val absDx = Math.abs(dx)
                        val absDy = Math.abs(dy)
                        if (absDy > SWIPE_NAVIGATE_THRESHOLD && absDy > absDx) {
                            isSwipeNavigating = true
                            showNavigationHint(dy)
                        } else if (absDx > SWIPE_SEEK_THRESHOLD && absDx > absDy) {
                            isSwipeSeeking = true
                            swipeAnchorMs = player.currentPosition
                            swipeStartX = event.x
                            isSeeking = true
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwipeNavigating) {
                        val dy = event.y - swipeStartY
                        if (dy > SWIPE_NAVIGATE_THRESHOLD && currentIndex > 0) {
                            navigateToVideo(currentIndex - 1, 0)
                        } else if (dy < -SWIPE_NAVIGATE_THRESHOLD && currentIndex < videoList.size - 1) {
                            navigateToVideo(currentIndex + 1, 0)
                        }
                        isSwipeNavigating = false
                        hideNavigationHint()
                    } else if (isSwipeSeeking) {
                        isSwipeSeeking = false
                        progressBar.finishSeek()
                        val dx = event.x - swipeStartX
                        val sensitivity = touchOverlay.width / 2f
                        val delta = dx / sensitivity
                        val dur = player.duration.coerceAtLeast(1)
                        val seekTo = (swipeAnchorMs + delta * dur).toLong().coerceIn(0, dur)
                        player.seekTo(seekTo)
                        isSeeking = false
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun loadVideo(index: Int, startPosMs: Long) {
        if (index < 0 || index >= videoList.size) return
        currentIndex = index
        val video = videoList[index]

        tvTitle.text = video.content ?: ""
        progressBar.reset()
        // Reset speed indicator
        isLongPressing = false
        tvSpeed.visibility = View.GONE

        player.stop()
        val mediaItem = MediaItem.fromUri(video.videoRes)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        player.setPlaybackSpeed(1f)
        if (startPosMs > 0) player.seekTo(startPosMs)
    }

    private fun navigateToVideo(index: Int, startPosMs: Long) {
        if (index < 0 || index >= videoList.size) return
        loadVideo(index, startPosMs)
    }

    private fun showNavigationHint(dy: Float) {
        if (dy > SWIPE_NAVIGATE_THRESHOLD && currentIndex > 0) {
            val prevTitle = videoList[currentIndex - 1].content ?: ""
            tvHint.text = "\u2191 $prevTitle"
            tvHint.visibility = View.VISIBLE
        } else if (dy < -SWIPE_NAVIGATE_THRESHOLD && currentIndex < videoList.size - 1) {
            val nextTitle = videoList[currentIndex + 1].content ?: ""
            tvHint.text = "\u2193 $nextTitle"
            tvHint.visibility = View.VISIBLE
        } else {
            tvHint.visibility = View.GONE
        }
    }

    private fun hideNavigationHint() {
        tvHint.visibility = View.GONE
    }

    private fun finishWithResult() {
        val resultIntent = android.content.Intent()
        resultIntent.putExtra(RESULT_CURRENT_INDEX, currentIndex)
        resultIntent.putExtra(RESULT_POSITION_MS, player.currentPosition)
        resultIntent.putExtra(RESULT_ITEM_ID, videoList[currentIndex].jellyfinItemId)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        finishWithResult()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressUpdater)
        if (::player.isInitialized) {
            player.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            player.release()
        }
    }
}

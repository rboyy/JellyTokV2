package com.bytedance.tiktok.player

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bytedance.tiktok.activity.MainActivity
import com.bytedance.tiktok.databinding.ViewPlayviewBinding
import com.bytedance.tiktok.fragment.MainFragment
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.source.BaseMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

/**
 * create by libo
 * create on 2018/12/20
 * description 播放器VideoPlayer
 */
class VideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs), Iplayer , DefaultLifecycleObserver {

    private val trackSelector: TrackSelector = DefaultTrackSelector(context)
    private val mPlayer : SimpleExoPlayer by lazy {
            SimpleExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build()
    }

    // 自定义 DefaultLoadControl 参数 — optimized for seamless local playback
    val MIN_BUFFER_MS = 1_000
    val MAX_BUFFER_MS = 3_000
    val PLAYBACK_BUFFER_MS = 100
    val REBUFFER_MS = 150
    val loadControl = DefaultLoadControl.Builder()
        .setPrioritizeTimeOverSizeThresholds(true)
        .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, PLAYBACK_BUFFER_MS, REBUFFER_MS)
        .build()

    private var binding: ViewPlayviewBinding = ViewPlayviewBinding.inflate(LayoutInflater.from(context), this, true)
    companion object {
        const val MAX_CACHE_BYTE: Long = 1024*1024*200  //200MB

        /**
         * 全局共享缓存 — 必须在companion object中避免多实例冲突
         */
        @Volatile
        private var sharedCache: SimpleCache? = null

        fun getCache(context: android.content.Context): SimpleCache {
            return sharedCache ?: synchronized(this) {
                sharedCache ?: run {
                    val cacheFile = context.cacheDir.resolve("tiktok_cache_file")
                    SimpleCache(cacheFile, LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTE), StandaloneDatabaseProvider(context)).also {
                        sharedCache = it
                    }
                }
            }
        }
    }

    init {
        initPlayer()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        //返回时，推荐页面可见，则继续播放视频
        if (MainActivity.curMainPage == 0 && MainFragment.Companion.curPage == 1) {
            // Safety: if player is in IDLE state (e.g. system killed it), re-prepare before playing
            if (mPlayer.playbackState == Player.STATE_IDLE && mPlayer.currentMediaItem == null) {
                // Player was released or reset — let the fragment re-initiate playback
                return
            }
            play()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)

        pause()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        // Do NOT call stop() here — it resets ExoPlayer to IDLE state,
        // causing a black screen when returning from background because
        // onResume() only calls play() without re-preparing.
        // pause() in onPause() is sufficient.
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        release()
    }

    private fun initPlayer() {
        binding.playerview.player = mPlayer
        binding.playerview.useController = false
        mPlayer.playWhenReady = true
        mPlayer.repeatMode = Player.REPEAT_MODE_ALL
    }

    /**
     * 横屏视频旋转 — 只旋转视频画面，app保持竖屏
     * 设置 PlayerView 为横屏尺寸 (screenHeight × screenWidth)，
     * 使用 RESIZE_MODE_FIT 保持视频原始比例不拉伸，
     * 旋转90°后填满屏幕宽度，上下留黑边（横屏视频在竖屏上的正常表现）。
     */
    fun setLandscapeRotation(landscape: Boolean) {
        if (landscape) {
            val dm = resources.displayMetrics
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            // Set PlayerView to landscape dimensions
            binding.playerview.layoutParams.apply {
                width = sh
                height = sw
            }
            // FIT: maintains video aspect ratio (no stretching)
            binding.playerview.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
            // Rotate 90°: (sh, sw) → visually (sw, sh) = full screen
            binding.playerview.rotation = 90f
            // Translate to center the rotated view in the FrameLayout
            binding.playerview.translationX = (sw - sh) / 2f
            binding.playerview.translationY = (sh - sw) / 2f
        } else {
            // Reset to normal portrait mode
            binding.playerview.layoutParams.apply {
                width = FrameLayout.LayoutParams.MATCH_PARENT
                height = FrameLayout.LayoutParams.MATCH_PARENT
            }
            binding.playerview.rotation = 0f
            binding.playerview.translationX = 0f
            binding.playerview.translationY = 0f
            binding.playerview.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        }
    }

    /**
     * 快速切换播放 — setMediaSource handles transition without stop() for smoother switching
     */
    fun playVideoDirect(url: String, positionMs: Long = 0) {
        if (TextUtils.isEmpty(url)) return
        android.util.Log.d("JELLYFIN_RESUME", "playVideoDirect url=${url.take(60)}... pos=${positionMs}ms")
        val mediaItem = MediaItem.fromUri(url)
        val dataSourceFactory = CacheDataSource.Factory().setCache(getCache(context)).setUpstreamDataSourceFactory(
            DefaultDataSource.Factory(context))
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        // Do NOT call stop() — it resets to IDLE and causes re-buffering delay.
        // setMediaSource transitions smoothly from READY→BUFFERING→READY.
        mPlayer.setMediaSource(mediaSource)
        if (positionMs > 0) {
            mPlayer.seekTo(positionMs)
            android.util.Log.d("JELLYFIN_RESUME", "seekTo $positionMs")
        }
        mPlayer.prepare()
        mPlayer.play()
    }

    /**
     * 使用本地缓存播放
     */
    fun playVideo(mediaSource: BaseMediaSource) {
        mPlayer.setMediaSource(mediaSource)
        mPlayer.prepare()
        mPlayer.play()
    }




    /**
     * 根据url生成缓存，播放本地缓存
     */
    override fun playVideo(url: String) {
        if (TextUtils.isEmpty(url)) {
            return
        }
        val mediaItem = MediaItem.fromUri(url)
        val dataSourceFactory = CacheDataSource.Factory().setCache(getCache(context)).setUpstreamDataSourceFactory(
            DefaultDataSource.Factory(context))
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        mPlayer.setMediaSource(mediaSource)
        mPlayer.prepare()
        mPlayer.play()
    }

    override fun getplayer(): SimpleExoPlayer {
        return mPlayer
    }

    override fun pause() {
        mPlayer.pause()
    }

    override fun play() {
        mPlayer.play()
    }

    override fun stop() {
        mPlayer.stop()
    }

    override fun release() {
        mPlayer?.let {
            it.release()
        }
    }


    override fun isPlaying(): Boolean = mPlayer.isPlaying
}

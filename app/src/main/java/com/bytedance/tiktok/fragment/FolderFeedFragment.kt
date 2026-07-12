package com.bytedance.tiktok.fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout.LayoutParams
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.bytedance.tiktok.R
import com.bytedance.tiktok.adapter.VideoAdapter
import com.bytedance.tiktok.base.BaseBindingFragment
import com.bytedance.tiktok.bean.PauseVideoEvent
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.databinding.FragmentRecommendBinding
import com.bytedance.tiktok.jellyfin.JellyfinManager
import com.bytedance.tiktok.player.VideoPlayer
import com.bytedance.tiktok.utils.RxBus
import com.bytedance.tiktok.view.ControllerView
import com.bytedance.tiktok.view.LikeView
import com.bytedance.tiktok.view.VideoProgressBar
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.launch
import rx.Subscription
import rx.functions.Action1

class FolderFeedFragment : BaseBindingFragment<FragmentRecommendBinding>({ FragmentRecommendBinding.inflate(it) }) {
    private var adapter: VideoAdapter? = null
    private var curPlayPos = -1
    private lateinit var videoView: VideoPlayer
    private var ivCurCover: ImageView? = null
    private var subscribe: Subscription? = null
    private var curProgressBar: VideoProgressBar? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var isSeeking = false
    private var currentItemId: String = ""

    companion object {
        private const val ARG_FOLDER_ID = "folder_id"
        private const val ARG_FOLDER_NAME = "folder_name"
        fun newInstance(folderId: String, folderName: String) = FolderFeedFragment().apply {
            arguments = Bundle().apply { putString(ARG_FOLDER_ID, folderId); putString(ARG_FOLDER_NAME, folderName) }
        }
    }

    private val folderId: String get() = arguments?.getString(ARG_FOLDER_ID) ?: ""
    private val folderName: String get() = arguments?.getString(ARG_FOLDER_NAME) ?: ""

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isSeeking && ::videoView.isInitialized) {
                val player = videoView.getplayer(); val curBar = curProgressBar
                if (player != null && curBar != null) { val dur = player.duration.coerceAtLeast(0); if (dur > 0) curBar.updateProgress(player.currentPosition, dur) }
            }
            progressHandler.postDelayed(this, 500)
        }
    }
    private val progressReportRunnable = object : Runnable {
        override fun run() {
            if (::videoView.isInitialized && currentItemId.isNotEmpty() && !isSeeking) {
                val player = videoView.getplayer()
                if (player != null) {
                    val pos = player.currentPosition; val paused = !player.isPlaying
                    adapter?.getDatas()?.getOrNull(curPlayPos)?.playbackPositionTicks = pos * 10_000L
                    JellyfinManager.saveLocalPosition(currentItemId, pos)
                    viewLifecycleOwner.lifecycleScope.launch { JellyfinManager.reportProgress(currentItemId, pos, paused) }
                }
            }
            progressHandler.postDelayed(this, 10_000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initRecyclerView(); initVideoPlayer(); setViewPagerLayoutManager(); setRefreshEvent(); observeEvent()
            loadFolderVideos(); progressHandler.post(progressUpdater); progressHandler.post(progressReportRunnable)
        } catch (e: Exception) { android.util.Log.e("JellyTok_CRASH", "FolderFeedFragment CRASH: ${e.message}", e) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressHandler.removeCallbacks(progressUpdater); progressHandler.removeCallbacks(progressReportRunnable)
        if (currentItemId.isNotEmpty() && ::videoView.isInitialized) {
            val pos = videoView.getplayer()?.currentPosition ?: 0L
            JellyfinManager.saveLocalPosition(currentItemId, pos)
            viewLifecycleOwner.lifecycleScope.launch { JellyfinManager.reportStopped(currentItemId, pos) }
        }
    }

    private fun initRecyclerView() {
        val rv = binding.recyclerView.getChildAt(0) as? RecyclerView ?: return
        rv.clipChildren = false
        binding.recyclerView.clipChildren = false
        adapter = VideoAdapter(requireContext(), rv); binding.recyclerView.adapter = adapter
    }

    private fun loadFolderVideos() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = JellyfinManager.getVideosRecursive(folderId, limit = 500)
                val allVideos = mutableListOf<VideoBean>()
                for (item in resp.items) {
                    if ((item.userData?.playCount ?: 0) == 0) {
                        val serverTicks = item.userData?.playbackPositionTicks ?: 0L
                        val localTicks = JellyfinManager.getLocalPosition(item.id) * 10_000L
                        val vb = VideoBean().apply {
                            jellyfinItemId = item.id; jellyfinImageUrl = JellyfinManager.getImageUrl(item.id)
                            videoRes = JellyfinManager.getStreamUrl(item.id); content = item.name
                            playbackPositionTicks = maxOf(serverTicks, localTicks)
                            isLiked = item.userData?.isFavorite == true
                            isLandscape = (item.width ?: 0) > (item.height ?: 0) && (item.height ?: 0) > 0
                            userBean = VideoBean.UserBean().apply { nickName = folderName; head = R.mipmap.head1 }
                        }
                        allVideos.add(vb)
                    }
                }
                allVideos.shuffle(); adapter?.setList(allVideos)
                if (allVideos.isNotEmpty()) binding.recyclerView.post { playCurVideo(0) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun initVideoPlayer() {
        videoView = VideoPlayer(requireActivity())
        videoView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        lifecycle.addObserver(videoView)
    }

    private fun observeEvent() {
        subscribe = RxBus.getDefault().toObservable(PauseVideoEvent::class.java)
            .subscribe(Action1 { event: PauseVideoEvent -> if (event.isPlayOrPause) videoView.play() else videoView.pause() } as Action1<PauseVideoEvent>)
    }
    override fun onDestroy() { super.onDestroy(); subscribe?.unsubscribe() }

    private fun setViewPagerLayoutManager() {
        with(binding.recyclerView) {
            orientation = ViewPager2.ORIENTATION_VERTICAL; offscreenPageLimit = 1
            registerOnPageChangeCallback(pageChangeCallback)
        }
    }
    private val pageChangeCallback = object : OnPageChangeCallback() { override fun onPageSelected(position: Int) { playCurVideo(position) } }

    private fun setRefreshEvent() {
        binding.refreshLayout.setColorSchemeResources(R.color.color_link)
        binding.refreshLayout.setOnRefreshListener {
            object : CountDownTimer(1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() { binding.refreshLayout.isRefreshing = false; loadFolderVideos() }
            }.start()
        }
    }

    private fun playCurVideo(position: Int) {
        if (position == curPlayPos || adapter?.getDatas()?.isEmpty() == true) return
        val itemView = adapter!!.getRootViewAt(position) ?: return
        val rootView = itemView.findViewById<ViewGroup>(R.id.rl_container)
        val likeView: LikeView = rootView.findViewById(R.id.likeview)
        val controllerView: ControllerView = rootView.findViewById(R.id.controller)
        val ivPlay = rootView.findViewById<ImageView>(R.id.iv_play)
        val ivCover = rootView.findViewById<ImageView>(R.id.iv_cover)
        val progressBar: VideoProgressBar = rootView.findViewById(R.id.progress_bar)
        val tvSpeed: TextView = rootView.findViewById(R.id.tv_speed)
        val ivRotate: ImageView = rootView.findViewById(R.id.iv_rotate)

        curProgressBar = progressBar; progressBar.reset(); progressBar.bringToFront()
        tvSpeed.bringToFront(); ivRotate.bringToFront(); ivPlay.bringToFront()

        likeView.setOnPlayPauseListener(object : LikeView.OnPlayPauseListener {
            override fun onPlayOrPause() {
                if (videoView.isPlaying()) { videoView.pause(); ivPlay.visibility = View.VISIBLE }
                else { videoView.play(); ivPlay.visibility = View.GONE }
            }
        })
        likeView.setOnSpeedChangeListener(object : LikeView.OnSpeedChangeListener {
            override fun onSpeedChange(speed: Float) {
                videoView.getplayer()?.setPlaybackSpeed(speed)
                tvSpeed.visibility = if (speed > 1f) View.VISIBLE else View.GONE
            }
        })
        progressBar.setOnSeekListener(object : VideoProgressBar.OnSeekListener {
            override fun onSeekStart() { isSeeking = true; videoView.pause() }
            override fun onSeekTo(positionMs: Long) { videoView.getplayer()?.seekTo(positionMs); videoView.play(); isSeeking = false }
        })
        likeView.setSeekProgressBar(progressBar)
        likeView.setOnSeekListener(object : LikeView.OnSeekListener {
            override fun getPlayer() = videoView.getplayer()
            override fun onSeekStart() { isSeeking = true }
            override fun onSeekTo(positionMs: Long) { videoView.getplayer()?.seekTo(positionMs); isSeeking = false }
        })
        likeView.setOnLikeListener(object : LikeView.OnLikeListener {
            override fun onLikeListener() {
                val datas = adapter?.getDatas() ?: return
                if (position < datas.size) {
                    val item = datas[position]; val wasLiked = item.isLiked
                    controllerView.like()
                    viewLifecycleOwner.lifecycleScope.launch {
                        try { JellyfinManager.toggleFavorite(item.jellyfinItemId, wasLiked) } catch (_: Exception) {}
                    }
                }
            }
        })
        controllerView.setListener(object : com.bytedance.tiktok.utils.OnVideoControllerListener {
            override fun onHeadClick() {}
            override fun onLikeClick() {
                val datas = adapter?.getDatas() ?: return
                if (position < datas.size) {
                    val item = datas[position]; val wasLiked = item.isLiked
                    viewLifecycleOwner.lifecycleScope.launch {
                        try { JellyfinManager.toggleFavorite(item.jellyfinItemId, wasLiked); item.isLiked = !wasLiked; controllerView.updateLikeUI() } catch (_: Exception) {}
                    }
                }
            }
            override fun onCommentClick() {}
            override fun onShareClick() {}
        })

        curPlayPos = position
        ivRotate.visibility = View.GONE; ivRotate.setOnClickListener(null)

        autoPlayVideo(curPlayPos, ivCover); dettachParentView(rootView)

        // 只旋转视频画面，app保持竖屏
        val videoData = adapter?.getDatas()?.getOrNull(position)
        if (videoData?.isLandscape == true) {
            videoView.setLandscapeRotation(true)
        } else {
            videoView.setLandscapeRotation(false)
            if (videoData != null && videoData.jellyfinItemId.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val dims = JellyfinManager.getVideoDimensions(videoData.jellyfinItemId)
                    if (dims != null && dims.first > dims.second) {
                        videoData.isLandscape = true
                        if (position == curPlayPos) videoView.setLandscapeRotation(true)
                    }
                }
            }
        }
    }

    private fun dettachParentView(rootView: ViewGroup) {
        videoView.parent?.let { (it as ViewGroup).removeView(videoView) }
        rootView.clipChildren = false
        rootView.clipToPadding = false
        rootView.addView(videoView, 0)
    }

    private fun autoPlayVideo(position: Int, ivCover: ImageView) {
        val datas = adapter!!.getDatas()
        if (position < 0 || position >= datas.size) return
        val url = datas[position].videoRes; if (url.isEmpty()) return
        val resumeMs = datas[position].playbackPositionTicks / 10_000L
        val prevItemId = currentItemId
        val prevPos = if (prevItemId.isNotEmpty()) videoView.getplayer()?.currentPosition ?: 0L else 0L
        videoView.playVideoDirect(url, resumeMs)
        val itemId = datas[position].jellyfinItemId
        if (prevItemId.isNotEmpty() && prevItemId != itemId && curPlayPos >= 0 && curPlayPos < datas.size) {
            datas[curPlayPos].playbackPositionTicks = prevPos * 10_000L; JellyfinManager.saveLocalPosition(prevItemId, prevPos)
        }
        currentItemId = itemId
        viewLifecycleOwner.lifecycleScope.launch {
            if (prevItemId.isNotEmpty() && prevItemId != itemId) JellyfinManager.reportStopped(prevItemId, prevPos)
            JellyfinManager.reportStart(itemId)
        }
        videoView.getplayer()?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    JellyfinManager.clearLocalPosition(currentItemId)
                    val nextPos = position + 1
                    if (nextPos < datas.size) binding.recyclerView.setCurrentItem(nextPos, true)
                }
            }
            override fun onRenderedFirstFrame() { ivCover.visibility = View.GONE; ivCurCover = ivCover }
        })
    }
}

package com.bytedance.tiktok.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.RelativeLayout
import com.bytedance.tiktok.R
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.databinding.ViewControllerBinding
import com.bytedance.tiktok.utils.AutoLinkHerfManager
import com.bytedance.tiktok.utils.NumUtils
import com.bytedance.tiktok.utils.OnVideoControllerListener

/**
 * create by libo
 * create on 2020-05-20
 * description
 */
class ControllerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs), View.OnClickListener {
    private var listener: OnVideoControllerListener? = null
    private var videoData: VideoBean? = null
    private var binding: ViewControllerBinding = ViewControllerBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        init()
    }

    private fun init() {
        binding.ivHead!!.setOnClickListener(this)
        binding.ivComment!!.setOnClickListener(this)
        binding.ivShare!!.setOnClickListener(this)
        binding.rlLike!!.setOnClickListener(this)
        binding.ivFocus!!.setOnClickListener(this)
        setRotateAnim()
    }

    fun setVideoData(videoData: VideoBean) {
        this.videoData = videoData
        binding.ivHead!!.setImageResource(videoData.userBean!!.head)
        binding.tvNickname!!.text = ""
        AutoLinkHerfManager.setContent(videoData.content, binding.autoLinkTextView)
        binding.tvMarquee!!.text = ""
        binding.ivHeadAnim!!.setImageResource(videoData.userBean!!.head)
        binding.tvLikecount!!.text = NumUtils.numberFilter(videoData.likeCount)
        binding.tvCommentcount!!.text = NumUtils.numberFilter(videoData.commentCount)
        binding.tvSharecount!!.text = NumUtils.numberFilter(videoData.shareCount)
        binding.animationView!!.setAnimation("like.json")

        //点赞状态
        if (videoData.isLiked) {
            binding.ivLike!!.setTextColor(resources.getColor(R.color.color_FF0041))
        } else {
            binding.ivLike!!.setTextColor(resources.getColor(R.color.white))
        }

        //关注状态
        if (videoData.isFocused) {
            binding.ivFocus!!.visibility = GONE
        } else {
            binding.ivFocus!!.visibility = VISIBLE
        }
    }

    fun setListener(listener: OnVideoControllerListener?) {
        this.listener = listener
    }

    override fun onClick(v: View) {
        if (listener == null) {
            return
        }
        when (v.id) {
            R.id.ivHead -> listener!!.onHeadClick()
            R.id.rlLike -> {
                listener!!.onLikeClick()
                // Only auto-toggle when no external listener manages state
                // When listener is set, it calls updateLikeUI() after its own state change
            }
            R.id.ivComment -> listener!!.onCommentClick()
            R.id.ivShare -> listener!!.onShareClick()
            R.id.ivFocus -> if (!videoData!!.isFocused) {
                videoData!!.isLiked = true
                binding.ivFocus!!.visibility = GONE
            }
        }
    }

    /**
     * 更新点赞UI状态（不改变isLiked值）
     */
    fun updateLikeUI() {
        if (videoData!!.isLiked) {
            binding.animationView!!.visibility = VISIBLE
            binding.animationView!!.playAnimation()
            binding.ivLike!!.setTextColor(resources.getColor(R.color.color_FF0041))
        } else {
            binding.animationView!!.visibility = INVISIBLE
            binding.ivLike!!.setTextColor(resources.getColor(R.color.white))
        }
    }

    /**
     * 点赞动作 (原有逻辑，含状态翻转)
     */
    fun like() {
        videoData!!.isLiked = !videoData!!.isLiked
        updateLikeUI()
    }

    /**
     * 循环旋转动画
     */
    private fun setRotateAnim() {
        val rotateAnimation = RotateAnimation(0f, 359f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        rotateAnimation.repeatCount = Animation.INFINITE
        rotateAnimation.duration = 8000
        rotateAnimation.interpolator = LinearInterpolator()
        binding.rlRecord!!.startAnimation(rotateAnimation)
    }
}
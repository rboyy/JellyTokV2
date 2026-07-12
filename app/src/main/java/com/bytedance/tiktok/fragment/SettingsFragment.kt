package com.bytedance.tiktok.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bytedance.tiktok.R
import com.bytedance.tiktok.activity.MainActivity
import com.bytedance.tiktok.base.BaseBindingFragment
import com.bytedance.tiktok.bean.CurUserBean
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.databinding.FragmentSettingsBinding
import com.bytedance.tiktok.jellyfin.JellyfinManager
import com.bytedance.tiktok.utils.RxBus
import kotlinx.coroutines.launch

class SettingsFragment : BaseBindingFragment<FragmentSettingsBinding>({ FragmentSettingsBinding.inflate(it) }) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            setupUI()
        } catch (e: Exception) {
            android.util.Log.e("JellyTok_CRASH", "SettingsFragment.onViewCreated CRASH: ${e.message}", e)
        }
    }

    private fun setupUI() {
        // Show current server info
        updateCurrentInfo()

        // Pre-fill with active server credentials
        val active = JellyfinManager.getActiveServer()
        if (active != null) {
            binding.etServer.setText(active.url)
            binding.etUsername.setText(active.username)
            binding.etPassword.setText(active.password)
        }

        binding.btnLogin.setOnClickListener {
            val url = binding.etServer.text.toString().trim()
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString()

            if (url.isEmpty() || user.isEmpty()) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "请填写服务器地址和用户名"
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            binding.progress.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    JellyfinManager.login(url, user, pass)
                    Toast.makeText(requireContext(), "连接成功", Toast.LENGTH_SHORT).show()
                    updateCurrentInfo()

                    // Post updated user info
                    val userBean = VideoBean.UserBean()
                    userBean.nickName = "Jellyfin"
                    userBean.head = R.mipmap.head1
                    userBean.sign = "JellyTok"
                    RxBus.getDefault().post(CurUserBean(userBean))

                    // Navigate back to recommend page
                    val activity = requireActivity() as? MainActivity
                    activity?.let {
                        // Switch to recommend tab via MainPageChangeEvent
                        RxBus.getDefault().post(com.bytedance.tiktok.bean.MainPageChangeEvent(0))
                    }
                } catch (e: Exception) {
                    binding.btnLogin.isEnabled = true
                    binding.progress.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "连接失败: ${e.message}"
                }
            }
        }
    }

    private fun updateCurrentInfo() {
        val active = JellyfinManager.getActiveServer()
        if (active != null && active.accessToken.isNotEmpty()) {
            binding.tvCurrentInfo.text = "当前连接: ${active.name}\n用户: ${active.username}\n服务器: ${active.url}"
        } else {
            binding.tvCurrentInfo.text = "未连接任何服务器"
        }
    }
}

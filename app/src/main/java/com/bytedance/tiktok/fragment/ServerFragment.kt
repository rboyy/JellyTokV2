package com.bytedance.tiktok.fragment

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bytedance.tiktok.R
import com.bytedance.tiktok.activity.LoginActivity
import com.bytedance.tiktok.base.BaseBindingFragment
import com.bytedance.tiktok.bean.CurUserBean
import com.bytedance.tiktok.bean.MainPageChangeEvent
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.databinding.FragmentServerBinding
import com.bytedance.tiktok.jellyfin.JellyfinManager
import com.bytedance.tiktok.utils.RxBus
import kotlinx.coroutines.launch

/**
 * Server management: server list + connection form combined in one page.
 */
class ServerFragment : BaseBindingFragment<FragmentServerBinding>({ FragmentServerBinding.inflate(it) }) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            setupConnectionForm()
            refreshServerList()
        } catch (e: Exception) {
            android.util.Log.e("JellyTok_CRASH", "ServerFragment.onViewCreated CRASH: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshServerList()
    }

    // ── Server List ──

    private fun refreshServerList() {
        val container = binding.serverList
        container.removeAllViews()

        val servers = JellyfinManager.getServers()
        val activeIndex = JellyfinManager.getActiveServerIndex()

        if (servers.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "暂无保存的服务器\n请在下方添加"
                setTextColor(Color.parseColor("#888888"))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            container.addView(emptyView)
            return
        }

        servers.forEachIndexed { index, server ->
            val isActive = index == activeIndex
            val cardView = createServerCard(server, isActive, index)
            container.addView(cardView)

            if (index < servers.size - 1) {
                val spacer = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 12
                    )
                }
                container.addView(spacer)
            }
        }

        // Show current connection info
        val active = JellyfinManager.getActiveServer()
        if (active != null && active.accessToken.isNotEmpty()) {
            binding.tvCurrentInfo.text = "当前连接: ${active.name}\n用户: ${active.username}\n服务器: ${active.url}"
        } else {
            binding.tvCurrentInfo.text = "未连接任何服务器"
        }

        // Pre-fill form with active server credentials
        if (active != null) {
            binding.etServer.setText(active.url)
            binding.etUsername.setText(active.username)
            binding.etPassword.setText(active.password)
        }
    }

    private fun createServerCard(
        server: com.bytedance.tiktok.jellyfin.ServerConfig,
        isActive: Boolean,
        index: Int
    ): View {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp4 = (4 * resources.displayMetrics.density).toInt()

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp12, dp16, dp12)
            val bg = GradientDrawable().apply {
                cornerRadius = 12 * resources.displayMetrics.density
                if (isActive) {
                    setColor(Color.parseColor("#2A2D3A"))
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#A8C7FA"))
                } else {
                    setColor(Color.parseColor("#1A1D24"))
                    setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#333640"))
                }
            }
            background = bg
        }

        val nameRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameTv = TextView(requireContext()).apply {
            text = if (server.name.isNotEmpty()) server.name else server.url
            setTextColor(Color.WHITE)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameRow.addView(nameTv)

        if (isActive) {
            val badge = TextView(requireContext()).apply {
                text = "当前"
                setTextColor(Color.parseColor("#0B2F6B"))
                textSize = 11f
                val badgeBg = GradientDrawable().apply {
                    cornerRadius = 4 * resources.displayMetrics.density
                    setColor(Color.parseColor("#A8C7FA"))
                }
                background = badgeBg
                setPadding(dp8, dp4, dp8, dp4)
            }
            nameRow.addView(badge)
        }
        card.addView(nameRow)

        val urlTv = TextView(requireContext()).apply {
            text = server.url
            setTextColor(Color.parseColor("#8E9099"))
            textSize = 12f
            setPadding(0, dp4, 0, 0)
        }
        card.addView(urlTv)

        val userTv = TextView(requireContext()).apply {
            text = "用户: ${server.username}"
            setTextColor(Color.parseColor("#666870"))
            textSize = 12f
            setPadding(0, dp4, 0, 0)
        }
        card.addView(userTv)

        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp8, 0, 0)
        }

        if (!isActive && server.accessToken.isNotEmpty()) {
            val switchBtn = android.widget.Button(requireContext()).apply {
                text = "切换"
                setTextColor(Color.WHITE)
                textSize = 13f
                val btnBg = GradientDrawable().apply {
                    cornerRadius = 6 * resources.displayMetrics.density
                    setColor(Color.parseColor("#6DD58C"))
                }
                background = btnBg
                setPadding(dp12, dp4, dp12, dp4)
                minHeight = 0
                minimumHeight = 0
                setOnClickListener { switchToServer(index) }
            }
            actionRow.addView(switchBtn)
            actionRow.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp8, 0)
            })
        }

        if (server.accessToken.isEmpty()) {
            val reconnectBtn = android.widget.Button(requireContext()).apply {
                text = "重新连接"
                setTextColor(Color.WHITE)
                textSize = 13f
                val btnBg = GradientDrawable().apply {
                    cornerRadius = 6 * resources.displayMetrics.density
                    setColor(Color.parseColor("#FFDDA4"))
                }
                background = btnBg
                setPadding(dp12, dp4, dp12, dp4)
                minHeight = 0
                minimumHeight = 0
                setOnClickListener {
                    binding.etServer.setText(server.url)
                    binding.etUsername.setText(server.username)
                    binding.etPassword.setText(server.password)
                    binding.etServer.requestFocus()
                }
            }
            actionRow.addView(reconnectBtn)
            actionRow.addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp8, 0)
            })
        }

        val deleteBtn = android.widget.Button(requireContext()).apply {
            text = "删除"
            setTextColor(Color.WHITE)
            textSize = 13f
            val btnBg = GradientDrawable().apply {
                cornerRadius = 6 * resources.displayMetrics.density
                setColor(Color.parseColor("#555555"))
            }
            background = btnBg
            setPadding(dp12, dp4, dp12, dp4)
            minHeight = 0
            minimumHeight = 0
            setOnClickListener { deleteServer(index) }
        }
        actionRow.addView(deleteBtn)

        card.addView(actionRow)
        return card
    }

    private fun switchToServer(index: Int) {
        Toast.makeText(requireContext(), "正在切换服务器...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val success = JellyfinManager.switchServer(index)
            if (success) {
                Toast.makeText(requireContext(), "切换成功", Toast.LENGTH_SHORT).show()
                refreshServerList()
                val userBean = VideoBean.UserBean()
                userBean.nickName = "Jellyfin"
                userBean.head = R.mipmap.head1
                userBean.sign = "JellyTok"
                RxBus.getDefault().post(CurUserBean(userBean))
                RxBus.getDefault().post(MainPageChangeEvent(MainFragment.PAGE_RECOMMEND))
            } else {
                Toast.makeText(requireContext(), "切换失败，请检查服务器设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteServer(index: Int) {
        val isActive = index == JellyfinManager.getActiveServerIndex()
        JellyfinManager.removeServer(index)
        if (isActive && JellyfinManager.getServers().isEmpty()) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        } else if (isActive) {
            viewLifecycleOwner.lifecycleScope.launch {
                JellyfinManager.switchServer(0)
                refreshServerList()
            }
        } else {
            refreshServerList()
        }
        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
    }

    // ── Connection Form ──

    private fun setupConnectionForm() {
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
                    refreshServerList()

                    val userBean = VideoBean.UserBean()
                    userBean.nickName = "Jellyfin"
                    userBean.head = R.mipmap.head1
                    userBean.sign = "JellyTok"
                    RxBus.getDefault().post(CurUserBean(userBean))

                    RxBus.getDefault().post(MainPageChangeEvent(MainFragment.PAGE_RECOMMEND))
                } catch (e: Exception) {
                    binding.btnLogin.isEnabled = true
                    binding.progress.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "连接失败: ${e.message}"
                }
            }
        }
    }
}

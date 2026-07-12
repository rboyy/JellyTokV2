package com.bytedance.tiktok.fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bytedance.tiktok.R
import com.bytedance.tiktok.base.BaseBindingFragment
import com.bytedance.tiktok.databinding.FragmentFoldersBinding
import com.bytedance.tiktok.jellyfin.JellyfinItem
import com.bytedance.tiktok.jellyfin.JellyfinManager
import kotlinx.coroutines.launch

class FoldersFragment : BaseBindingFragment<FragmentFoldersBinding>({ FragmentFoldersBinding.inflate(it) }) {

    // Navigation stack: each entry is (folderId, folderName)
    private val navStack = mutableListOf<Pair<String, String>>()
    private var adapter: BrowserAdapter? = null

    private var backCallback: OnBackPressedCallback? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initRecyclerView()
            initBackButton()
            setRefreshEvent()
            loadRoot()
        } catch (e: Exception) {
            android.util.Log.e("JellyTok_CRASH", "FoldersFragment.onViewCreated CRASH: ${e.message}", e)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = BrowserAdapter()
        binding.recyclerView.adapter = adapter
    }

    private fun initBackButton() {
        binding.ivBack.bringToFront()
        binding.tvBreadcrumb.bringToFront()
        binding.ivBack.setOnClickListener { goBack() }

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (binding.feedContainer.visibility == View.VISIBLE) {
                    hideFeed()
                } else if (navStack.isNotEmpty()) {
                    goBack()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)
    }

    @Suppress("DEPRECATION")
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        backCallback?.isEnabled = isVisibleToUser && (navStack.isNotEmpty() || binding.feedContainer.visibility == View.VISIBLE)
    }

    private fun setRefreshEvent() {
        binding.refreshLayout.setColorSchemeResources(R.color.color_link)
        binding.refreshLayout.setOnRefreshListener {
            if (navStack.isEmpty()) loadRoot()
            else loadChildren(navStack.last().first)
            object : CountDownTimer(1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() { binding.refreshLayout.isRefreshing = false }
            }.start()
        }
    }

    // ── Navigation ──

    private fun loadRoot() {
        navStack.clear()
        updateHeader()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = JellyfinManager.getViews()
                val folders = response.items.filter { it.isFolder }
                adapter?.setItems(folders.map { BrowserItem(it, isFolder = true) })
                binding.tvEmpty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
                if (folders.isEmpty()) binding.tvEmpty.text = "暂无媒体库"
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "加载失败: ${e.message}"
            }
        }
    }

    private fun navigateIntoFolder(folderId: String, folderName: String) {
        navStack.add(Pair(folderId, folderName))
        updateHeader()
        loadChildren(folderId)
    }

    private fun loadChildren(folderId: String) {
        binding.tvEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = JellyfinManager.getChildren(folderId)
                val items = response.items.map { item ->
                    BrowserItem(item, isFolder = item.isFolder)
                }
                adapter?.setItems(items)
                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                if (items.isEmpty()) binding.tvEmpty.text = "此文件夹为空"
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "加载失败: ${e.message}"
            }
        }
    }

    private fun goBack() {
        if (navStack.isNotEmpty()) {
            navStack.removeAt(navStack.lastIndex)
            updateHeader()
            if (navStack.isEmpty()) {
                loadRoot()
            } else {
                loadChildren(navStack.last().first)
            }
        }
    }

    private fun updateHeader() {
        if (navStack.isEmpty()) {
            binding.tvTitle.text = "媒体库"
            binding.ivBack.visibility = View.GONE
            binding.tvBreadcrumb.visibility = View.GONE
        } else {
            binding.tvTitle.text = navStack.last().second
            binding.ivBack.visibility = View.VISIBLE
            if (navStack.size > 1) {
                val path = navStack.dropLast(1).joinToString(" > ") { it.second }
                binding.tvBreadcrumb.text = path
                binding.tvBreadcrumb.visibility = View.VISIBLE
            } else {
                binding.tvBreadcrumb.text = "媒体库"
                binding.tvBreadcrumb.visibility = View.VISIBLE
            }
        }
        backCallback?.isEnabled = userVisibleHint && (navStack.isNotEmpty() || binding.feedContainer.visibility == View.VISIBLE)
    }

    // ── Video Feed Overlay ──

    private fun openVideoFeed(folderId: String, folderName: String) {
        val feedFragment = FolderFeedFragment.newInstance(folderId, folderName)
        binding.feedContainer.visibility = View.VISIBLE
        backCallback?.isEnabled = true
        childFragmentManager.beginTransaction()
            .replace(R.id.feed_container, feedFragment)
            .commit()
    }

    private fun hideFeed() {
        binding.feedContainer.visibility = View.GONE
        backCallback?.isEnabled = userVisibleHint && navStack.isNotEmpty()
        val frag = childFragmentManager.findFragmentById(R.id.feed_container)
        if (frag != null) {
            childFragmentManager.beginTransaction().remove(frag).commit()
        }
    }

    // ── Adapter ──

    data class BrowserItem(val item: JellyfinItem, val isFolder: Boolean)

    inner class BrowserAdapter : RecyclerView.Adapter<BrowserAdapter.VH>() {
        private val items = mutableListOf<BrowserItem>()

        fun setItems(list: List<BrowserItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val bi = items[position]
            val item = bi.item
            holder.name.text = item.name

            val imageUrl = if (item.imageTags?.containsKey("Primary") == true) {
                JellyfinManager.getImageUrl(item.id, 600)
            } else {
                null
            }

            if (imageUrl != null) {
                Glide.with(holder.itemView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.color.color_bg_theme)
                    .into(holder.thumb)
            } else {
                holder.thumb.setImageResource(
                    if (bi.isFolder) android.R.drawable.ic_menu_gallery
                    else android.R.drawable.ic_menu_slideshow
                )
            }

            // Add folder indicator
            if (bi.isFolder) {
                holder.name.text = "📁 ${item.name}"
            }

            holder.itemView.setOnClickListener {
                if (bi.isFolder) {
                    navigateIntoFolder(item.id, item.name)
                } else {
                    // Video clicked — open feed for current folder
                    val currentFolderId = if (navStack.isNotEmpty()) navStack.last().first
                    else item.id
                    val currentFolderName = if (navStack.isNotEmpty()) navStack.last().second
                    else "视频"
                    openVideoFeed(currentFolderId, currentFolderName)
                }
            }
        }

        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.iv_thumb)
            val name: TextView = view.findViewById(R.id.tv_name)
        }
    }
}

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
import com.bytedance.tiktok.databinding.FragmentFavoritesBinding
import com.bytedance.tiktok.jellyfin.JellyfinItem
import com.bytedance.tiktok.jellyfin.JellyfinManager
import kotlinx.coroutines.launch

class FavoritesFragment : BaseBindingFragment<FragmentFavoritesBinding>({ FragmentFavoritesBinding.inflate(it) }) {

    private var adapter: FavoritesAdapter? = null
    private var favoriteItems = mutableListOf<JellyfinItem>()
    private var backCallback: OnBackPressedCallback? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initRecyclerView()
            initBackCallback()
            setRefreshEvent()
            loadFavorites()
        } catch (e: Exception) {
            android.util.Log.e("JellyTok_CRASH", "FavoritesFragment.onViewCreated CRASH: ${e.message}", e)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = FavoritesAdapter()
        binding.recyclerView.adapter = adapter
    }

    private fun initBackCallback() {
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (binding.feedContainer.visibility == View.VISIBLE) {
                    hideFeed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)
    }

    @Suppress("DEPRECATION")
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        backCallback?.isEnabled = isVisibleToUser && binding.feedContainer.visibility == View.VISIBLE
    }

    private fun setRefreshEvent() {
        binding.refreshLayout.setColorSchemeResources(R.color.color_link)
        binding.refreshLayout.setOnRefreshListener {
            loadFavorites()
            object : CountDownTimer(1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() { binding.refreshLayout.isRefreshing = false }
            }.start()
        }
    }

    private fun loadFavorites() {
        binding.tvEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = JellyfinManager.getFavorites()
                favoriteItems.clear()
                favoriteItems.addAll(response.items)
                adapter?.setItems(favoriteItems.toList())
                binding.tvCount.text = "${favoriteItems.size} 部影片"
                if (favoriteItems.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "暂无收藏"
                }
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "加载失败: ${e.message}"
            }
        }
    }

    private fun openVideoFeed(startIndex: Int) {
        val feedFragment = FavoritesFeedFragment.newInstance(startIndex)
        binding.feedContainer.visibility = View.VISIBLE
        backCallback?.isEnabled = true
        childFragmentManager.beginTransaction()
            .replace(R.id.feed_container, feedFragment)
            .commit()
    }

    private fun hideFeed() {
        binding.feedContainer.visibility = View.GONE
        backCallback?.isEnabled = false
        val frag = childFragmentManager.findFragmentById(R.id.feed_container)
        if (frag != null) {
            childFragmentManager.beginTransaction().remove(frag).commit()
        }
    }

    // ── Grid Adapter ──

    inner class FavoritesAdapter : RecyclerView.Adapter<FavoritesAdapter.VH>() {
        private val items = mutableListOf<JellyfinItem>()

        fun setItems(list: List<JellyfinItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name

            val imageUrl = if (item.imageTags?.isNotEmpty() == true) {
                JellyfinManager.getImageUrl(item.id, 400)
            } else null

            if (imageUrl != null) {
                Glide.with(holder.itemView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.color.color_bg_theme)
                    .into(holder.thumb)
            } else {
                holder.thumb.setImageResource(android.R.drawable.ic_menu_slideshow)
            }

            holder.itemView.setOnClickListener {
                openVideoFeed(position)
            }
        }

        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.iv_thumb)
            val name: TextView = view.findViewById(R.id.tv_name)
        }
    }
}

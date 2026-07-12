package com.bytedance.tiktok.fragment

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bytedance.tiktok.R
import com.bytedance.tiktok.activity.LandscapePlayerActivity
import com.bytedance.tiktok.base.BaseBindingFragment
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.databinding.FragmentSearchBinding
import com.bytedance.tiktok.jellyfin.JellyfinItem
import com.bytedance.tiktok.jellyfin.JellyfinManager
import kotlinx.coroutines.launch

class SearchFragment : BaseBindingFragment<FragmentSearchBinding>({ FragmentSearchBinding.inflate(it) }) {

    private var adapter: SearchResultAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initUI()
        } catch (e: Exception) {
            android.util.Log.e("JellyTok_CRASH", "SearchFragment CRASH: ${e.message}", e)
        }
    }

    private fun initUI() {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = SearchResultAdapter()
        binding.recyclerView.adapter = adapter

        binding.ivBack.setOnClickListener {
            hideKeyboard()
            parentFragmentManager.popBackStack()
        }

        binding.tvSearch.setOnClickListener { doSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        // Show/hide clear button based on text
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.ivClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        binding.ivClear.setOnClickListener {
            binding.etSearch.text.clear()
        }

        // Auto focus and show keyboard
        binding.etSearch.requestFocus()
        binding.etSearch.postDelayed({
            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun doSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        binding.statusContainer.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.tvStatus.text = "搜索中..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = JellyfinManager.searchVideos(query)
                adapter?.setItems(response.items)
                if (response.items.isEmpty()) {
                    binding.statusContainer.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.tvStatus.text = "未找到相关影片"
                } else {
                    binding.statusContainer.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.statusContainer.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.tvStatus.text = "搜索失败: ${e.message}"
            }
        }
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    inner class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.VH>() {
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
                // Build a VideoBean list from search results for landscape player
                val videoList = ArrayList(items.map { searchItem ->
                    VideoBean().apply {
                        jellyfinItemId = searchItem.id
                        videoRes = JellyfinManager.getStreamUrl(searchItem.id)
                        content = searchItem.name
                        jellyfinImageUrl = JellyfinManager.getImageUrl(searchItem.id)
                    }
                })
                val intent = Intent(requireContext(), LandscapePlayerActivity::class.java)
                intent.putExtra(LandscapePlayerActivity.EXTRA_VIDEO_LIST, videoList)
                intent.putExtra(LandscapePlayerActivity.EXTRA_START_INDEX, position)
                intent.putExtra(LandscapePlayerActivity.EXTRA_START_POSITION_MS, 0L)
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.iv_thumb)
            val name: TextView = view.findViewById(R.id.tv_name)
        }
    }
}

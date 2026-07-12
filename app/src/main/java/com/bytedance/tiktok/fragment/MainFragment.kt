package com.bytedance.tiktok.fragment

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.bytedance.tiktok.R
import com.bytedance.tiktok.base.BaseBindingFragment
import com.bytedance.tiktok.base.CommPagerAdapter
import com.bytedance.tiktok.bean.MainPageChangeEvent
import com.bytedance.tiktok.bean.PauseVideoEvent
import com.bytedance.tiktok.databinding.FragmentMainBinding
import com.bytedance.tiktok.utils.RxBus
import rx.Subscription
import rx.functions.Action1
import java.util.*

class MainFragment : BaseBindingFragment<FragmentMainBinding>({ FragmentMainBinding.inflate(it) }) {
    private var recommendFragment: RecommendFragment? = null
    private var favoritesFragment: FavoritesFragment? = null
    private var foldersFragment: FoldersFragment? = null
    private var serverFragment: ServerFragment? = null

    private val fragments = ArrayList<Fragment>()
    private var pagerAdapter: CommPagerAdapter? = null
    private var pageChangeSubscription: Subscription? = null
    private var isFromMenuClick = false

    companion object {
        const val PAGE_CURRENT_LOCATION = 0
        const val PAGE_RECOMMEND = 1
        const val PAGE_FAVORITES = 2
        const val PAGE_FOLDERS = 3
        const val PAGE_SERVER = 4
        var curPage = PAGE_RECOMMEND
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            applyWindowInsets()
            setFragments()
            setMainMenu()
            listenPageChangeEvents()
            initSearchButton()
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, searchBackCallback)
        } catch (e: Exception) {
            android.util.Log.e("JellyTok_CRASH", "MainFragment.onViewCreated CRASH: ${e.message}", e)
        }
    }

    private fun applyWindowInsets() {
        val statusBarHeight = getStatusBarHeight()
        if (statusBarHeight > 0) {
            // Use topMargin so the top_bar background doesn't cover the status bar area
            // (this eliminates the visible black border)
            val params = binding.topBar.layoutParams as android.widget.RelativeLayout.LayoutParams
            params.topMargin = statusBarHeight
            binding.topBar.layoutParams = params
        }

        // Bottom nav: navigation bar padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = navBar)
            insets
        }
        ViewCompat.requestApplyInsets(binding.bottomNav)
    }

    private fun getStatusBarHeight(): Int {
        // Method 1: Window visible display frame (most reliable, accounts for cutout)
        val rect = android.graphics.Rect()
        requireActivity().window.decorView.getWindowVisibleDisplayFrame(rect)
        if (rect.top > 0) return rect.top

        // Method 2: System resource lookup
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) return resources.getDimensionPixelSize(resId)

        // Method 3: Default fallback ~36dp
        return (36 * resources.displayMetrics.density).toInt()
    }

    private fun setFragments() {
        val currentLocationFragment = CurrentLocationFragment()
        recommendFragment = RecommendFragment()
        favoritesFragment = FavoritesFragment()
        foldersFragment = FoldersFragment()
        serverFragment = ServerFragment()

        fragments.add(currentLocationFragment)
        fragments.add(recommendFragment!!)
        fragments.add(favoritesFragment!!)
        fragments.add(foldersFragment!!)
        fragments.add(serverFragment!!)

        val titles = arrayOf("视频", "推荐", "收藏", "媒体库", "服务器")
        pagerAdapter = CommPagerAdapter(childFragmentManager, fragments, titles)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 4

        // Top tab bar only for video pages
        binding.tabTitle.addTab(binding.tabTitle.newTab().setText("视频"))
        binding.tabTitle.addTab(binding.tabTitle.newTab().setText("推荐"))
        binding.tabTitle.setupWithViewPager(binding.viewPager)
        binding.tabTitle.getTabAt(1)?.select()
        binding.tabTitle.setupWithViewPager(null)

        binding.viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                curPage = position
                updateTopBarForPage(position)

                if (position == PAGE_RECOMMEND) {
                    RxBus.getDefault().post(PauseVideoEvent(true))
                } else {
                    RxBus.getDefault().post(PauseVideoEvent(false))
                }

                if (!isFromMenuClick) {
                    val menuIndex = pageToMenuIndex(position)
                    if (menuIndex >= 0) {
                        binding.tabMainMenu.getTabAt(menuIndex)?.select()
                    }
                }
                isFromMenuClick = false
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        binding.viewPager.currentItem = PAGE_RECOMMEND
    }

    private fun updateTopBarForPage(position: Int) {
        binding.tabTitle.visibility = when (position) {
            PAGE_CURRENT_LOCATION, PAGE_RECOMMEND -> View.VISIBLE
            else -> View.GONE
        }
    }

    private fun setMainMenu() {
        with(binding.tabMainMenu) {
            addTab(newTab().setText("首页"))
            addTab(newTab().setText("收藏"))
            addTab(newTab().setText("媒体库"))
            addTab(newTab().setText("服务器"))

            getTabAt(0)?.select()

            addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    isFromMenuClick = true
                    val page = menuToPageIndex(tab.position)
                    if (page >= 0 && page != binding.viewPager.currentItem) {
                        binding.viewPager.setCurrentItem(page, false)
                    }
                }

                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    onTabSelected(tab)
                }
            })
        }
    }

    private fun listenPageChangeEvents() {
        pageChangeSubscription = RxBus.getDefault()
            .toObservable(MainPageChangeEvent::class.java)
            .subscribe(Action1 { event: MainPageChangeEvent ->
                val page = event.page
                if (page in 0 until fragments.size) {
                    isFromMenuClick = true
                    binding.viewPager.setCurrentItem(page, false)
                    val menuIndex = pageToMenuIndex(page)
                    if (menuIndex >= 0) {
                        binding.tabMainMenu.getTabAt(menuIndex)?.select()
                    }
                }
            } as Action1<MainPageChangeEvent>)
    }

    private fun menuToPageIndex(menuPos: Int): Int {
        return when (menuPos) {
            0 -> PAGE_RECOMMEND
            1 -> PAGE_FAVORITES
            2 -> PAGE_FOLDERS
            3 -> PAGE_SERVER
            else -> -1
        }
    }

    private fun pageToMenuIndex(page: Int): Int {
        return when (page) {
            PAGE_CURRENT_LOCATION, PAGE_RECOMMEND -> 0
            PAGE_FAVORITES -> 1
            PAGE_FOLDERS -> 2
            PAGE_SERVER -> 3
            else -> -1
        }
    }

    private fun initSearchButton() {
        binding.ivSearch.setOnClickListener {
            binding.searchContainer.visibility = View.VISIBLE
            searchBackCallback.isEnabled = true
            childFragmentManager.beginTransaction()
                .replace(R.id.search_container, SearchFragment())
                .addToBackStack("search")
                .commit()
        }

        childFragmentManager.addOnBackStackChangedListener {
            if (childFragmentManager.backStackEntryCount == 0) {
                binding.searchContainer.visibility = View.GONE
                searchBackCallback.isEnabled = false
            }
        }
    }

    private val searchBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.searchContainer.visibility == View.VISIBLE) {
                childFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pageChangeSubscription?.unsubscribe()
    }
}

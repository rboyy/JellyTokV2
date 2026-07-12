package com.bytedance.tiktok.activity

import android.util.Log
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.bytedance.tiktok.base.BaseBindingActivity
import com.bytedance.tiktok.base.CommPagerAdapter
import com.bytedance.tiktok.bean.MainPageChangeEvent
import com.bytedance.tiktok.bean.PauseVideoEvent
import com.bytedance.tiktok.databinding.ActivityMainBinding
import com.bytedance.tiktok.fragment.MainFragment
import com.bytedance.tiktok.fragment.PersonalHomeFragment
import com.bytedance.tiktok.utils.RxBus
import rx.functions.Action1
import java.util.*

class MainActivity : BaseBindingActivity<ActivityMainBinding>({ActivityMainBinding.inflate(it)}) {
    private var pagerAdapter: CommPagerAdapter? = null
    private val fragments = ArrayList<Fragment>()
    private val mainFragment = MainFragment()
    private val personalHomeFragment = PersonalHomeFragment()

    override fun init() {
        try {
            // Edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d("JellyTok_CRASH", "MainActivity.init() started")
            fragments.add(mainFragment)
            fragments.add(personalHomeFragment)
            pagerAdapter = CommPagerAdapter(supportFragmentManager, fragments, arrayOf("", ""))
            binding.viewPager!!.adapter = pagerAdapter
            Log.d("JellyTok_CRASH", "MainActivity.init() adapter set")

            //点击头像切换页面 — only handle outer ViewPager pages (0=main, 1=profile)
            RxBus.getDefault().toObservable(MainPageChangeEvent::class.java)
                    .subscribe(Action1 { event: MainPageChangeEvent ->
                        if (binding.viewPager != null && event.page < fragments.size) {
                            binding.viewPager!!.currentItem = event.page
                        }
                    } as Action1<MainPageChangeEvent>)
            binding.viewPager.addOnPageChangeListener(object : OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
                override fun onPageSelected(position: Int) {
                    curMainPage = position
                    if (position == 0) {
                        RxBus.getDefault().post(PauseVideoEvent(true))
                    } else if (position == 1) {
                        RxBus.getDefault().post(PauseVideoEvent(false))
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })
            Log.d("JellyTok_CRASH", "MainActivity.init() completed")
        } catch (e: Exception) {
            Log.e("JellyTok_CRASH", "MainActivity.init() CRASH: ${e.message}", e)
        }
    }

    override fun onBackPressed() {
        if (binding.viewPager.currentItem == 1) {
            binding.viewPager.currentItem = 0
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        var curMainPage = 0
    }
}
package com.goodwy.voicerecorderfree.activities

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.voicerecorderfree.BuildConfig
import com.goodwy.voicerecorderfree.R
import com.goodwy.voicerecorderfree.adapters.ViewPagerAdapter
import com.goodwy.voicerecorderfree.databinding.ActivityMainBinding
import com.goodwy.voicerecorderfree.extensions.config
import com.goodwy.voicerecorderfree.extensions.deleteExpiredTrashedRecordings
import com.goodwy.voicerecorderfree.extensions.ensureStoragePermission
import com.goodwy.voicerecorderfree.extensions.launchAbout
import com.goodwy.voicerecorderfree.helpers.STOP_AMPLITUDE_UPDATE
import com.goodwy.voicerecorderfree.models.Events
import com.goodwy.voicerecorderfree.services.RecorderService
import me.grantland.widget.AutofitHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {

    private var isSearchOpen = false
    private var mSearchMenuItem: MenuItem? = null
    private var searchQuery = ""
    private var bus: EventBus? = null
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val START_RECORDING_INTENT_ACTION = "START_RECORDING_ACTION";
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
//        refreshMenuItems()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.mainCoordinator,
            nestedView = binding.mainHolder,
            useTransparentNavigation = false,
            useTopSearchMenu = config.bottomNavigationBar
        )

//        val marginTop = if (config.bottomNavigationBar) actionBarSize + pixels(R.dimen.top_toolbar_search_height).toInt() else actionBarSize
//        binding.mainHolder.updateLayoutParams<ViewGroup.MarginLayoutParams> {
//            setMargins(0, marginTop, 0, 0)
//        }
        binding.mainMenu.updateTitle(getString(R.string.app_launcher_name_g))
        binding.mainMenu.searchBeVisibleIf(config.bottomNavigationBar)
        config.tabsChanged = false

        if (savedInstanceState == null) {
            deleteExpiredTrashedRecordings()
        }

        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                tryInitVoiceRecorder()
            } else {
                toast(com.goodwy.commons.R.string.no_audio_permissions)
                finish()
            }

            bus = EventBus.getDefault()
            bus!!.register(this)
            if (config.recordAfterLaunch && !RecorderService.isRunning) {
                Intent(this@MainActivity, RecorderService::class.java).apply {
                    try {
                        startService(this)
                    } catch (ignored: Exception) { }
                }
            }

            if (intent.action == START_RECORDING_INTENT_ACTION) {
                binding.viewPager.currentItem = 0
                Intent(this@MainActivity, RecorderService::class.java).apply {
                    try {
                        startService(this)
                    } catch (ignored: Exception) { }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        refreshMenuItems()

        if (config.tabsChanged) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        updateMenuColors()
        if (getPagerAdapter()?.showRecycleBin != config.useRecycleBin) {
            setupViewPager()
        }
        setupTabColors()
        getPagerAdapter()?.onResume()

        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.viewPager.setPageTransformer(true, animation)
        binding.viewPager.setPagingEnabled(!config.useSwipeToAction)
    }

    override fun onPause() {
        super.onPause()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
        getPagerAdapter()?.onDestroy()

        Intent(this@MainActivity, RecorderService::class.java).apply {
            action = STOP_AMPLITUDE_UPDATE
            try {
                startService(this)
            } catch (ignored: Exception) { }
        }
        config.tabsChanged = false
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else if (isThirdPartyIntent()) {
            setResult(Activity.RESULT_CANCELED, null)
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.getToolbar().inflateMenu(R.menu.menu)
        binding.mainMenu.toggleHideOnScroll(false)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchOpenListener = {
            if (binding.viewPager.currentItem == 0) {
                binding.viewPager.currentItem = 1
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            getPagerAdapter()?.searchTextChanged(text)
            binding.mainMenu.clearSearch()
        }

        binding.mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        //Top Search
        setupSearch(binding.mainMenu.getToolbar().menu)
    }

    private fun setupSearch(menu: Menu) {
        menu.findItem(R.id.search).isVisible = !config.bottomNavigationBar
        binding.mainMenu.clearSearch()
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        (mSearchMenuItem!!.actionView as SearchView).apply {
            val textColor = getProperTextColor()
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text).apply {
                setTextColor(textColor)
                setHintTextColor(textColor)
            }
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn).apply {
                setImageResource(com.goodwy.commons.R.drawable.ic_clear_round)
                setColorFilter(textColor)
            }
            findViewById<View>(androidx.appcompat.R.id.search_plate)?.apply { // search underline
                background.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
            }
            setIconifiedByDefault(false)
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
                setColorFilter(textColor)
            }

            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(com.goodwy.commons.R.string.search_recordings)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        searchQuery = newText
                        getPagerAdapter()?.searchTextChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                //getCurrentFragment()?.onSearchOpened()
                binding.viewPager.currentItem = 1
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                //getCurrentFragment()?.onSearchClosed()
                isSearchOpen = false
                return true
            }
        })
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        binding.mainMenu.updateColors()
    }

    private fun tryInitVoiceRecorder() {
        if (isRPlus()) {
            ensureStoragePermission { granted ->
                if (granted) {
                    setupViewPager()
                } else {
                    toast(com.goodwy.commons.R.string.no_storage_permissions)
                    finish()
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    setupViewPager()
                } else {
                    toast(com.goodwy.commons.R.string.no_storage_permissions)
                    finish()
                }
            }
        }
    }

    private fun setupViewPager() {
        var tabDrawables = arrayOf(
            com.goodwy.commons.R.drawable.ic_microphone_vector,
            R.drawable.ic_headset_scaled
        )
        var tabLabels = arrayOf(
            com.goodwy.strings.R.string.recorder_g,
            com.goodwy.strings.R.string.player_g
        )
        if (config.useRecycleBin) {
            tabDrawables += com.goodwy.commons.R.drawable.ic_delete_outline
            tabLabels += com.goodwy.commons.R.string.recycle_bin
        }

        // bottom tab bar
        if (config.bottomNavigationBar) {
            binding.mainTabsHolder.removeAllTabs()

            tabDrawables.forEachIndexed { i, drawableId ->
                binding.mainTabsHolder.newTab()
                    .setCustomView(com.goodwy.commons.R.layout.bottom_tablayout_item).apply {
                        customView?.findViewById<ImageView>(com.goodwy.commons.R.id.tab_item_icon)
                            ?.setImageDrawable(
                                AppCompatResources.getDrawable(
                                    this@MainActivity,
                                    drawableId
                                )
                            )

                        customView?.findViewById<TextView>(com.goodwy.commons.R.id.tab_item_label)
                            ?.apply {
                                setText(tabLabels[i])
                                beGoneIf(config.useIconTabs)
                            }

                        AutofitHelper.create(
                            customView?.findViewById(com.goodwy.commons.R.id.tab_item_label)
                        )

                        binding.mainTabsHolder.addTab(this)
                    }
            }

            binding.mainTabsHolder.onTabSelectionChanged(
                tabUnselectedAction = {
                    updateBottomTabItemColors(
                        it.customView,
                        false,
                        getDeselectedTabDrawableIds()[it.position]
                    )
                    if (it.position == 1) {
                        if (config.bottomNavigationBar) binding.mainMenu.closeSearch()
                        else mSearchMenuItem?.collapseActionView()
                    }
                },
                tabSelectedAction = {
                    binding.viewPager.currentItem = it.position
                    updateBottomTabItemColors(
                        it.customView,
                        true,
                        getSelectedTabDrawableIds()[it.position]
                    )
                }
            )
        } else {
            // top tab bar
            binding.mainTopTabsHolder.removeAllTabs()
            tabLabels.forEachIndexed { i, drawableId ->
                val tab =
                    if (config.useIconTabs) binding.mainTopTabsHolder.newTab()
                        .setIcon(getTabIcon(i))
                    else binding.mainTopTabsHolder.newTab().setText(getTabLabel(i))
                tab.contentDescription = getTabLabel(i)
                binding.mainTopTabsHolder.addTab(tab, i)
                binding.mainTopTabsHolder.setTabTextColors(getProperTextColor(),
                    getProperPrimaryColor())
            }

            binding.mainTopTabsHolder.onTabSelectionChanged(
                tabUnselectedAction = {
                    it.icon?.applyColorFilter(getProperTextColor())
                    it.icon?.alpha = 220 // max 255
                },
                tabSelectedAction = {
                    binding.viewPager.currentItem = it.position
                    it.icon?.applyColorFilter(getProperPrimaryColor())
                    it.icon?.alpha = 220 // max 255
                }
            )
        }

        binding.viewPager.adapter = ViewPagerAdapter(this, config.useRecycleBin)
        binding.viewPager.onPageChangeListener {
            if (config.bottomNavigationBar) binding.mainTabsHolder.getTabAt(it)?.select()
            else binding.mainTopTabsHolder.getTabAt(it)?.select()
            (binding.viewPager.adapter as ViewPagerAdapter).finishActMode()
        }

        if (isThirdPartyIntent()) {
            binding.viewPager.currentItem = 0
        } else {
            binding.viewPager.currentItem = config.lastUsedViewPagerPage
            if (config.bottomNavigationBar) binding.mainTabsHolder.getTabAt(config.lastUsedViewPagerPage)?.select()
            else binding.mainTopTabsHolder.getTabAt(config.lastUsedViewPagerPage)?.select()
        }
    }

    private fun setupTabColors() {
        val bottomBarColor = getBottomNavigationBackgroundColor()
        updateNavigationBarColor(bottomBarColor)

        if (config.bottomNavigationBar) {
            // bottom tab bar
            val activeView =
                binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
            updateBottomTabItemColors(
                activeView,
                true,
                getSelectedTabDrawableIds()[binding.viewPager.currentItem]
            )
            for (i in 0 until binding.mainTabsHolder.tabCount) {
                if (i != binding.viewPager.currentItem) {
                    val inactiveView = binding.mainTabsHolder.getTabAt(i)?.customView
                    updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[i])
                }
            }

            binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.select()
            binding.mainTabsHolder.setBackgroundColor(bottomBarColor)

            binding.mainTabsHolder.beVisible()
            binding.mainTopTabsContainer.beGone()
        } else {
            // top tab bar
            if (config.tabsChanged) {
                if (config.useIconTabs) {
                    binding.mainTopTabsHolder.getTabAt(0)?.text = null
                    binding.mainTopTabsHolder.getTabAt(1)?.text = null
                } else {
                    binding.mainTopTabsHolder.getTabAt(0)?.icon = null
                    binding.mainTopTabsHolder.getTabAt(1)?.icon = null
                }
            }
            binding.mainTopTabsHolder.apply {
                val properTextColor = getProperTextColor()
                val properPrimaryColor = getProperPrimaryColor()
                setSelectedTabIndicatorColor(getProperBackgroundColor())
                getTabAt(binding.viewPager.currentItem)?.icon?.applyColorFilter(properPrimaryColor)
                getTabAt(binding.viewPager.currentItem)?.icon?.alpha = 220
                setTabTextColors(properTextColor, properPrimaryColor)
                for (i in 0 until binding.mainTopTabsHolder.tabCount) {
                    if (i != binding.viewPager.currentItem) {
                        getTabAt(i)?.icon?.applyColorFilter(properTextColor)
                    }
                }
            }

            binding.mainTabsHolder.beGone()
            binding.mainTopTabsContainer.beVisible()
        }
    }

    private fun getPagerAdapter() = (binding.viewPager.adapter as? ViewPagerAdapter)

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun isThirdPartyIntent() = intent?.action == MediaStore.Audio.Media.RECORD_SOUND_ACTION

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun recordingSaved(event: Events.RecordingSaved) {
        if (isThirdPartyIntent()) {
            Intent().apply {
                data = event.uri!!
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    private fun getSelectedTabDrawableIds(): ArrayList<Int> {
        val icons = arrayListOf(com.goodwy.commons.R.drawable.ic_microphone_vector, R.drawable.ic_headset_scaled)

        if (config.useRecycleBin) {
            icons.add(com.goodwy.commons.R.drawable.ic_delete_outline)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val icons = arrayListOf(R.drawable.ic_microphone_scaled, R.drawable.ic_headset_vector)

        if (config.useRecycleBin) {
            icons.add(R.drawable.ic_delete_scaled)
        }

        return icons
    }
}

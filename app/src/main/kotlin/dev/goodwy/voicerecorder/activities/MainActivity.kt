package dev.goodwy.voicerecorder.activities

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.view.Menu
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import dev.goodwy.voicerecorder.BuildConfig
import dev.goodwy.voicerecorder.R
import dev.goodwy.voicerecorder.adapters.ViewPagerAdapter
import dev.goodwy.voicerecorder.databinding.ActivityMainBinding
import dev.goodwy.voicerecorder.extensions.config
import dev.goodwy.voicerecorder.extensions.deleteExpiredTrashedRecordings
import dev.goodwy.voicerecorder.extensions.ensureStoragePermission
import dev.goodwy.voicerecorder.extensions.launchAbout
import dev.goodwy.voicerecorder.helpers.STOP_AMPLITUDE_UPDATE
import dev.goodwy.voicerecorder.helpers.whatsNewList
import dev.goodwy.voicerecorder.models.Events
import dev.goodwy.voicerecorder.services.RecorderService
import me.grantland.widget.AutofitHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Objects

class MainActivity : SimpleActivity() {

    private var bus: EventBus? = null
    private var isSpeechToTextAvailable = false

    override var isSearchBarEnabled = true

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val START_RECORDING_INTENT_ACTION = "START_RECORDING_ACTION";
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
//        refreshMenuItems()

        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.mainTabsHolder)
        )

        binding.mainMenu.apply {
            updateTitle(getAppLauncherName())
            searchBeVisibleIf(config.showSearchBar)
        }
        config.needRestart = false

        if (savedInstanceState == null) {
            deleteExpiredTrashedRecordings()
        }

        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                tryInitVoiceRecorder()
                checkWhatsNewDialog()
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
                        config.isRunning = true
                    } catch (_: Exception) { }
                }
                intent.action = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        refreshMenuItems()

        if (config.needRestart) {
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
        config.needRestart = false
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else if (isThirdPartyIntent()) {
            setResult(RESULT_CANCELED, null)
            false
        } else {
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.mainMenu.setText(speechToText)
                }
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu)
//        binding.mainMenu.toggleHideOnScroll(false)
        if (baseConfig.useSpeechToText) {
            isSpeechToTextAvailable = isSpeechToTextAvailable()
            binding.mainMenu.showSpeechToText = isSpeechToTextAvailable
        }
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSpeechToTextClickListener = {
            speechToText()
        }

        binding.mainMenu.onSearchOpenListener = {
            if (binding.viewPager.currentItem == 0) {
                binding.viewPager.currentItem = 1
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            getPagerAdapter()?.searchTextChanged(text)
            binding.mainMenu.clearSearch()
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        //Top Search
        setupSearch(binding.mainMenu.requireToolbar().menu)
    }

    private fun setupSearch(menu: Menu) {
        binding.mainMenu.clearSearch()
    }

    private fun updateMenuColors() {
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
            R.drawable.ic_playlist_play_scaled
        )
        var tabLabels = arrayOf(
            com.goodwy.strings.R.string.recorder_g,
            com.goodwy.strings.R.string.player_g
        )
        if (config.useRecycleBin) {
            tabDrawables += com.goodwy.commons.R.drawable.ic_delete_outline
            tabLabels += com.goodwy.commons.R.string.recycle_bin
        }


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

        binding.viewPager.adapter = ViewPagerAdapter(this, config.useRecycleBin)
        binding.viewPager.onPageChangeListener {
            binding.mainTabsHolder.getTabAt(it)?.select()
            (binding.viewPager.adapter as ViewPagerAdapter).finishActMode()
        }

        if (isThirdPartyIntent()) {
            binding.viewPager.currentItem = 0
        } else {
            binding.viewPager.currentItem = config.lastUsedViewPagerPage
            binding.mainTabsHolder.getTabAt(config.lastUsedViewPagerPage)?.select()
        }
    }

    private fun setupTabColors() {
        val bottomBarColor = getSurfaceColor()

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
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    private fun getSelectedTabDrawableIds(): ArrayList<Int> {
        val icons = arrayListOf(R.drawable.ic_microphone_scaled, R.drawable.ic_playlist_play_scaled)

        if (config.useRecycleBin) {
            icons.add(R.drawable.ic_playlist_remove_scaled)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val icons = arrayListOf(R.drawable.ic_microphone, R.drawable.ic_playlist_play_vector)

        if (config.useRecycleBin) {
            icons.add(R.drawable.ic_playlist_remove_vector)
        }

        return icons
    }

    private fun checkWhatsNewDialog() {
        whatsNewList().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}

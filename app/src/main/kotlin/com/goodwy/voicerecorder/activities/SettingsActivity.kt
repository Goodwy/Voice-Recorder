package com.goodwy.voicerecorder.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.behaviorule.arturdumchev.library.pixels
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.helpers.rustore.RuStoreHelper
import com.goodwy.commons.helpers.rustore.model.StartPurchasesEvent
import com.goodwy.commons.models.RadioItem
import com.goodwy.voicerecorder.BuildConfig
import com.goodwy.voicerecorder.R
import com.goodwy.voicerecorder.databinding.ActivitySettingsBinding
import com.goodwy.voicerecorder.dialogs.MoveRecordingsDialog
import com.goodwy.voicerecorder.extensions.config
import com.goodwy.voicerecorder.extensions.deleteTrashedRecordings
import com.goodwy.voicerecorder.extensions.getAllRecordings
import com.goodwy.voicerecorder.extensions.hasRecordings
import com.goodwy.voicerecorder.extensions.launchAbout
import com.goodwy.voicerecorder.extensions.launchFolderPicker
import com.goodwy.voicerecorder.extensions.launchPurchase
import com.goodwy.voicerecorder.helpers.*
import com.goodwy.voicerecorder.models.Events
import com.google.android.material.snackbar.Snackbar
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import kotlinx.coroutines.launch
import kotlin.collections.ArrayList
import org.greenrobot.eventbus.EventBus
import ru.rustore.sdk.core.feature.model.FeatureAvailabilityResult
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private var recycleBinContentSize = 0
    private lateinit var binding: ActivitySettingsBinding

    private val purchaseHelper = PurchaseHelper(this)
    private var ruStoreHelper: RuStoreHelper? = null
    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3
    private var ruStoreIsConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.settingsCoordinator,
            nestedView = binding.settingsHolder,
            useTransparentNavigation = false,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)
        // TODO TRANSPARENT Navigation Bar
        if (config.transparentNavigationBar) {
            setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
                binding.settingsCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                updateNavigationBarColor(getProperBackgroundColor())
            }
        }

        if (isPlayStoreInstalled()) {
            //PlayStore
            purchaseHelper.initBillingClient()
            val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
            val subList: ArrayList<String> = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
            purchaseHelper.retrieveDonation(iapList, subList)

            purchaseHelper.isIapPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isPro = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isPro = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }

            purchaseHelper.isSupPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isProSubs = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isProSubs = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }
        }
        if (isRuStoreInstalled()) {
            //RuStore
            ruStoreHelper = RuStoreHelper()
            ruStoreHelper!!.checkPurchasesAvailability(this@SettingsActivity)

            lifecycleScope.launch {
                ruStoreHelper!!.eventStart
                    .flowWithLifecycle(lifecycle)
                    .collect { event ->
                        handleEventStart(event)
                    }
            }

            lifecycleScope.launch {
                ruStoreHelper!!.statePurchased
                    .flowWithLifecycle(lifecycle)
                    .collect { state ->
                        //update of purchased
                        if (!state.isLoading && ruStoreIsConnected) {
                            baseConfig.isProRuStore = state.purchases.firstOrNull() != null
                            updatePro()
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()

        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupRoundIcon()
        setupOverflowIcon()

        setupUseEnglish()
        setupLanguage()
        setupChangeDateTimeFormat()
        setupHideNotification()
        setupSaveRecordingsFolder()
        setupExtension()
        setupBitrate()
        setupAudioSource()
        setupRecordAfterLaunch()
        setupKeepScreenOn()

        setupDefaultTab()
        //setupBottomNavigationBar()
        setupNavigationBarStyle()
        setupUseIconTabs()
        setupScreenSlideAnimation()

        setupUseSwipeToAction()
        setupSwipeVibration()
        setupSwipeRipple()
        setupSwipeRightAction()
        setupSwipeLeftAction()
        setupDeleteConfirmation()

        setupShowDividers()

        setupUseRecycleBin()
        setupEmptyRecycleBin()

        setupTipJar()
        setupAbout()

        setupColors()
        updateTextColors(binding.settingsHolder)
    }

    private fun updatePro(isPro: Boolean = checkPro()) {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro)
            settingsTipJarHolder.beVisibleIf(isPro)

            val stringId =
                if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action
                else com.goodwy.strings.R.string.swipe_left_action
            settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, isPro)

            arrayOf(
                settingsSwipeLeftActionHolder
            ).forEach {
                it.alpha = if (isPro) 1f else 0.4f
            }
        }
    }

    private fun setupPurchaseThankYou() {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(checkPro())
            settingsPurchaseThankYouHolder.setOnClickListener {
                launchPurchase()
            }
            moreButton.setOnClickListener {
                launchPurchase()
            }
            val appDrawable = resources.getColoredDrawableWithColor(this@SettingsActivity, com.goodwy.commons.R.drawable.ic_plus_support, getProperPrimaryColor())
            purchaseLogo.setImageDrawable(appDrawable)
            val drawable = resources.getColoredDrawableWithColor(this@SettingsActivity, com.goodwy.commons.R.drawable.button_gray_bg, getProperPrimaryColor())
            moreButton.background = drawable
            moreButton.setTextColor(getProperBackgroundColor())
            moreButton.setPadding(2, 2, 2, 2)
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsCustomizeColorsHolder.setOnClickListener {
            startCustomizationActivity(
                showAccentColor = false,
                isCollection = resources.getBoolean(R.bool.is_pro_app),
                productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
                productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
                subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                playStoreInstalled = isPlayStoreInstalled(),
                ruStoreInstalled = isRuStoreInstalled(),
                showAppIconColor = true
            )
        }
    }

    private fun setupCustomizeWidgetColors() {
        binding.settingsWidgetColorCustomizationHolder.setOnClickListener {
            Intent(this, WidgetRecordDisplayConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() {
        binding.apply {
            settingsUseEnglishHolder.beVisibleIf(
                (config.wasUseEnglishToggled || Locale.getDefault().language != "en")
                        && !isTiramisuPlus()
            )
            settingsUseEnglish.isChecked = config.useEnglish
            settingsUseEnglishHolder.setOnClickListener {
                settingsUseEnglish.toggle()
                config.useEnglish = settingsUseEnglish.isChecked
                exitProcess(0)
            }
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        if (isTiramisuPlus()) {
            settingsLanguageHolder.beVisible()
            settingsLanguageHolder.setOnClickListener {
                launchChangeAppLanguageIntent()
            }
        } else {
            settingsLanguageHolder.beGone()
        }
    }

    private fun setupChangeDateTimeFormat() {
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupHideNotification() {
        binding.settingsHideNotification.isChecked = config.hideNotification
        binding.settingsHideNotificationHolder.setOnClickListener {
            binding.settingsHideNotification.toggle()
            config.hideNotification = binding.settingsHideNotification.isChecked
        }
    }

    private fun setupSaveRecordingsFolder() {
        binding.settingsSaveRecordings.text = humanizePath(config.saveRecordingsFolder)
        binding.settingsSaveRecordingsHolder.setOnClickListener {
            val currentFolder = config.saveRecordingsFolder
            launchFolderPicker(currentFolder) { newFolder ->
                if (!newFolder.isNullOrEmpty()) {
                    ensureBackgroundThread {
                        val hasRecordings = hasRecordings()
                        runOnUiThread {
                            if (newFolder != currentFolder && hasRecordings) {
                                MoveRecordingsDialog(
                                    activity = this,
                                    previousFolder = currentFolder,
                                    newFolder = newFolder
                                ) {
                                    config.saveRecordingsFolder = newFolder
                                    binding.settingsSaveRecordings.text =
                                        humanizePath(config.saveRecordingsFolder)
                                }
                            } else {
                                config.saveRecordingsFolder = newFolder
                                binding.settingsSaveRecordings.text =
                                    humanizePath(config.saveRecordingsFolder)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupExtension() {
        binding.settingsExtension.text = config.getExtensionText()
        binding.settingsExtensionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(EXTENSION_M4A, getString(R.string.m4a)),
                RadioItem(EXTENSION_MP3, getString(R.string.mp3_experimental))
            )

            if (isQPlus()) {
                items.add(RadioItem(EXTENSION_OGG, getString(R.string.ogg_opus)))
            }

            RadioGroupDialog(this@SettingsActivity, items, config.extension, com.goodwy.commons.R.string.extension) {
                config.extension = it as Int
                binding.settingsExtension.text = config.getExtensionText()
            }
        }
    }


    private fun setupBitrate() {
        binding.settingsBitrate.text = getBitrateText(config.bitrate)
        binding.settingsBitrateHolder.setOnClickListener {
            val items = BITRATES.map { RadioItem(it, getBitrateText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.bitrate, R.string.bitrate) {
                config.bitrate = it as Int
                binding.settingsBitrate.text = getBitrateText(config.bitrate)
            }
        }
    }

    private fun getBitrateText(value: Int): String {
        return getString(R.string.bitrate_value).format(value / 1000)
    }

    private fun setupRecordAfterLaunch() {
        binding.settingsRecordAfterLaunch.isChecked = config.recordAfterLaunch
        binding.settingsRecordAfterLaunchHolder.setOnClickListener {
            binding.settingsRecordAfterLaunch.toggle()
            config.recordAfterLaunch = binding.settingsRecordAfterLaunch.isChecked
        }
    }

    private fun setupKeepScreenOn() {
        binding.settingsKeepScreenOn.isChecked = config.keepScreenOn
        binding.settingsKeepScreenOnHolder.setOnClickListener {
            binding.settingsKeepScreenOn.toggle()
            config.keepScreenOn = binding.settingsKeepScreenOn.isChecked
        }
    }

    private fun setupUseRecycleBin() {
        updateRecycleBinButtons()
        binding.settingsUseRecycleBin.isChecked = config.useRecycleBin
        binding.settingsUseRecycleBinHolder.setOnClickListener {
            binding.settingsUseRecycleBin.toggle()
            config.tabsChanged = true
            config.useRecycleBin = binding.settingsUseRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun updateRecycleBinButtons() {
        binding.settingsEmptyRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() {
        ensureBackgroundThread {
            try {
                recycleBinContentSize = getAllRecordings(trashed = true).sumByInt { it.size }
            } catch (ignored: Exception) {
            }

            runOnUiThread {
                binding.settingsEmptyRecycleBinSize.text = recycleBinContentSize.formatSize()
            }
        }

        binding.settingsEmptyRecycleBinHolder.setOnClickListener {
            if (recycleBinContentSize == 0) {
                toast(com.goodwy.commons.R.string.recycle_bin_empty)
            } else {
                ConfirmationDialog(
                    activity = this,
                    message = "",
                    messageId = com.goodwy.commons.R.string.empty_recycle_bin_confirmation,
                    positive = com.goodwy.commons.R.string.yes,
                    negative = com.goodwy.commons.R.string.no
                ) {
                    ensureBackgroundThread {
                        deleteTrashedRecordings()
                        runOnUiThread {
                            recycleBinContentSize = 0
                            binding.settingsEmptyRecycleBinSize.text = 0.formatSize()
                            EventBus.getDefault().post(Events.RecordingTrashUpdated())
                        }
                    }
                }
            }
        }
    }

    private fun setupAudioSource() {
        binding.settingsAudioSource.text = config.getAudioSourceText(config.audioSource)
        binding.settingsAudioSourceHolder.setOnClickListener {
            val items = getAudioSources()
                .map {
                    RadioItem(
                        id = it,
                        title = config.getAudioSourceText(it)
                    )
                } as ArrayList

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.audioSource,
                titleId = R.string.audio_source
            ) {
                config.audioSource = it as Int
                binding.settingsAudioSource.text = config.getAudioSourceText(config.audioSource)
            }
        }
    }

    private fun getAudioSources(): ArrayList<Int> {
        val availableSources = arrayListOf(
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.UNPROCESSED
        )

        if (isQPlus()) {
            availableSources.add(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
        }

        return availableSources
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(VIEVPAGE_LAST, getString(com.goodwy.commons.R.string.last_used_tab)),
                RadioItem(VIEVPAGE_RECORDER, getString(com.goodwy.strings.R.string.recorder_g), icon = com.goodwy.commons.R.drawable.ic_microphone_vector),
                RadioItem(VIEVPAGE_PLAYER, getString(com.goodwy.strings.R.string.player_g), icon = R.drawable.ic_headset_vector)
            )

            RadioGroupIconDialog(this@SettingsActivity, items, config.viewPage) {
                config.viewPage = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun setupNavigationBarStyle() {
        binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
        binding.settingsNavigationBarStyleHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(com.goodwy.strings.R.string.top), icon = com.goodwy.commons.R.drawable.ic_tab_top),
                RadioItem(1, getString(com.goodwy.strings.R.string.bottom), icon = com.goodwy.commons.R.drawable.ic_tab_bottom),
            )

            val checkedItemId = if (config.bottomNavigationBar) 1 else 0
            RadioGroupIconDialog(this@SettingsActivity, items, checkedItemId, com.goodwy.strings.R.string.tab_navigation) {
                config.bottomNavigationBar = it == 1
                config.tabsChanged = true
                binding.settingsNavigationBarStyle.text = getNavigationBarStyleText()
            }
        }
    }

    private fun setupUseIconTabs() {
        binding.settingsUseIconTabs.isChecked = config.useIconTabs
        binding.settingsUseIconTabsHolder.setOnClickListener {
            binding.settingsUseIconTabs.toggle()
            config.useIconTabs = binding.settingsUseIconTabs.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupScreenSlideAnimation() {
        binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
        binding.settingsScreenSlideAnimationHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(com.goodwy.commons.R.string.no), icon = com.goodwy.commons.R.drawable.ic_view_array),
                RadioItem(1, getString(com.goodwy.strings.R.string.screen_slide_animation_zoomout), icon = com.goodwy.commons.R.drawable.ic_view_carousel),
                RadioItem(2, getString(com.goodwy.strings.R.string.screen_slide_animation_depth), icon = com.goodwy.commons.R.drawable.ic_playing_cards),
            )

            RadioGroupIconDialog(this@SettingsActivity, items, config.screenSlideAnimation, com.goodwy.strings.R.string.screen_slide_animation) {
                config.screenSlideAnimation = it as Int
                config.tabsChanged = true
                binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
            }
        }
    }

    private fun setupUseSwipeToAction() {
        updateSwipeToActionVisible()
        binding.apply {
            settingsUseSwipeToAction.isChecked = config.useSwipeToAction
            settingsUseSwipeToActionHolder.setOnClickListener {
                settingsUseSwipeToAction.toggle()
                config.useSwipeToAction = settingsUseSwipeToAction.isChecked
                config.tabsChanged = true
                updateSwipeToActionVisible()
            }
        }
    }

    private fun updateSwipeToActionVisible() {
        binding.apply {
            settingsSwipeVibrationHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRippleHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRightActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeLeftActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSkipDeleteConfirmationHolder.beVisibleIf(config.useSwipeToAction &&(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE))
        }
    }

    private fun setupSwipeVibration() {
        binding.apply {
            settingsSwipeVibration.isChecked = config.swipeVibration
            settingsSwipeVibrationHolder.setOnClickListener {
                settingsSwipeVibration.toggle()
                config.swipeVibration = settingsSwipeVibration.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupSwipeRipple() {
        binding.apply {
            settingsSwipeRipple.isChecked = config.swipeRipple
            settingsSwipeRippleHolder.setOnClickListener {
                settingsSwipeRipple.toggle()
                config.swipeRipple = settingsSwipeRipple.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupSwipeRightAction() = binding.apply {
        if (isRTLLayout) settingsSwipeRightActionLabel.text = getString(com.goodwy.strings.R.string.swipe_left_action)
        settingsSwipeRightAction.text = getSwipeActionText(false)
        settingsSwipeRightActionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                RadioItem(SWIPE_ACTION_SHARE, getString(com.goodwy.commons.R.string.share), icon = com.goodwy.commons.R.drawable.ic_ios_share),
                RadioItem(SWIPE_ACTION_OPEN, getString(com.goodwy.commons.R.string.open_with), icon = R.drawable.ic_open_with),
                RadioItem(SWIPE_ACTION_EDIT, getString(com.goodwy.commons.R.string.rename), icon = R.drawable.ic_file_rename),
            )

            val title =
                if (isRTLLayout) com.goodwy.strings.R.string.swipe_left_action else com.goodwy.strings.R.string.swipe_right_action
            RadioGroupIconDialog(this@SettingsActivity, items, config.swipeRightAction, title) {
                config.swipeRightAction = it as Int
                config.tabsChanged = true
                settingsSwipeRightAction.text = getSwipeActionText(false)
                settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            }
        }
    }

    private fun setupSwipeLeftAction() = binding.apply {
        val pro = checkPro()
        settingsSwipeLeftActionHolder.alpha = if (pro) 1f else 0.4f
        val stringId = if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action else com.goodwy.strings.R.string.swipe_left_action
        settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, pro)
        settingsSwipeLeftAction.text = getSwipeActionText(true)
        settingsSwipeLeftActionHolder.setOnClickListener {
            if (pro) {
                val items = arrayListOf(
                    RadioItem(
                        SWIPE_ACTION_DELETE,
                        getString(com.goodwy.commons.R.string.delete),
                        icon = com.goodwy.commons.R.drawable.ic_delete_outline
                    ),
                    RadioItem(
                        SWIPE_ACTION_SHARE,
                        getString(com.goodwy.commons.R.string.share),
                        icon = com.goodwy.commons.R.drawable.ic_ios_share
                    ),
                    RadioItem(
                        SWIPE_ACTION_OPEN,
                        getString(com.goodwy.commons.R.string.open_with),
                        icon = R.drawable.ic_open_with
                    ),
                    RadioItem(
                        SWIPE_ACTION_EDIT,
                        getString(com.goodwy.commons.R.string.rename),
                        icon = R.drawable.ic_file_rename
                    ),
                )

                val title =
                    if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action else com.goodwy.strings.R.string.swipe_left_action
                RadioGroupIconDialog(this@SettingsActivity, items, config.swipeLeftAction, title) {
                    config.swipeLeftAction = it as Int
                    config.tabsChanged = true
                    settingsSwipeLeftAction.text = getSwipeActionText(true)
                    settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
                }
            } else {
                RxAnimation.from(settingsSwipeLeftActionHolder)
                    .shake(shakeTranslation = 2f)
                    .subscribe()

                showSnackbar(binding.root)
            }
        }
    }

    private fun getSwipeActionText(left: Boolean) = getString(
        when (if (left) config.swipeLeftAction else config.swipeRightAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.string.delete
            SWIPE_ACTION_SHARE -> com.goodwy.commons.R.string.share
            SWIPE_ACTION_EDIT -> com.goodwy.commons.R.string.rename
            else -> com.goodwy.commons.R.string.open_with
        }
    )

    private fun setupDeleteConfirmation() {
        binding.apply {
            //settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            settingsSkipDeleteConfirmation.isChecked = config.skipDeleteConfirmation
            settingsSkipDeleteConfirmationHolder.setOnClickListener {
                settingsSkipDeleteConfirmation.toggle()
                config.skipDeleteConfirmation = settingsSkipDeleteConfirmation.isChecked
            }
        }
    }

    private fun setupRoundIcon() {
        binding.settingsRoundPlayIcon.isChecked = config.roundIcon
        binding.settingsRoundPlayIconHolder.setOnClickListener {
            binding.settingsRoundPlayIcon.toggle()
            config.roundIcon = binding.settingsRoundPlayIcon.isChecked
        }
    }

    private fun setupOverflowIcon() = binding.apply {
        settingsOverflowIcon.applyColorFilter(getProperTextColor())
        settingsOverflowIcon.setImageResource(getOverflowIcon(config.overflowIcon))
        settingsOverflowIconHolder.setOnClickListener {
            val items = arrayListOf(
                com.goodwy.commons.R.drawable.ic_more_horiz,
                com.goodwy.commons.R.drawable.ic_three_dots_vector,
                com.goodwy.commons.R.drawable.ic_more_horiz_round
            )

            IconListDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.overflowIcon + 1,
                defaultItemId = OVERFLOW_ICON_HORIZONTAL + 1,
                titleId = com.goodwy.strings.R.string.overflow_icon,
                size = pixels(com.goodwy.commons.R.dimen.normal_icon_size).toInt(),
                color = getProperTextColor()
            ) { wasPositivePressed, newValue ->
                if (wasPositivePressed) {
                    if (config.overflowIcon != newValue - 1) {
                        config.overflowIcon = newValue - 1
                        settingsOverflowIcon.setImageResource(getOverflowIcon(config.overflowIcon))
                    }
                }
            }
        }
    }

    private fun setupShowDividers() = binding.apply {
        settingsShowDividers.isChecked = config.useDividers
        settingsShowDividersHolder.setOnClickListener {
            settingsShowDividers.toggle()
            config.useDividers = settingsShowDividers.isChecked
        }
    }

    private fun setupTipJar() = binding.apply {
        settingsTipJarHolder.apply {
            beVisibleIf(checkPro())
            background.applyColorFilter(getBottomNavigationBackgroundColor().lightenColor(4))
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupAbout() = binding.apply {
        settingsAboutVersion.text = "Version: " + BuildConfig.VERSION_NAME
        settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
    }

    private fun setupColors() {
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()

        arrayOf(
            binding.settingsAppearanceLabel,
            binding.settingsGeneralLabel,
            binding.settingsTabsLabel,
            binding.settingsSwipeGesturesLabel,
            binding.settingsListViewLabel,
            binding.settingsRecycleBinLabel,
            binding.settingsOtherLabel
        ).forEach {
            it.setTextColor(primaryColor)
        }

        arrayOf(
            binding.settingsColorCustomizationHolder,
            binding.settingsGeneralHolder,
            binding.settingsTabsHolder,
            binding.settingsSwipeGesturesHolder,
            binding.settingsListViewHolder,
            binding.settingsRecycleBinHolder,
            binding.settingsOtherHolder
        ).forEach {
            it.setCardBackgroundColor(getBottomNavigationBackgroundColor())
        }

        arrayOf(
            binding.settingsCustomizeColorsChevron,
            binding.settingsWidgetColorCustomizationChevron,
            binding.settingsChangeDateTimeFormatChevron,
            binding.settingsTipJarChevron,
            binding.settingsAboutChevron
        ).forEach {
            it.applyColorFilter(textColor)
        }
    }

    private fun getDefaultTabText() = getString(
        when (config.viewPage) {
            VIEVPAGE_RECORDER -> com.goodwy.strings.R.string.recorder_g
            VIEVPAGE_PLAYER -> com.goodwy.strings.R.string.player_g
            else -> com.goodwy.commons.R.string.last_used_tab
        }
    )

    private fun updateProducts() {
        val productList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3, subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
        ruStoreHelper!!.getProducts(productList)
    }

    private fun handleEventStart(event: StartPurchasesEvent) {
        when (event) {
            is StartPurchasesEvent.PurchasesAvailability -> {
                when (event.availability) {
                    is FeatureAvailabilityResult.Available -> {
                        //Process purchases available
                        updateProducts()
                        ruStoreIsConnected = true
                    }

                    is FeatureAvailabilityResult.Unavailable -> {
                        //toast(event.availability.cause.message ?: "Process purchases unavailable", Toast.LENGTH_LONG)
                    }

                    else -> {}
                }
            }

            is StartPurchasesEvent.Error -> {
                //toast(event.throwable.message ?: "Process unknown error", Toast.LENGTH_LONG)
            }
        }
    }

    private fun checkPro() = resources.getBoolean(R.bool.is_pro_app) || isPro()

    private fun showSnackbar(view: View) {
        view.performHapticFeedback()

        val snackbar = Snackbar.make(view, com.goodwy.strings.R.string.support_project_to_unlock, Snackbar.LENGTH_SHORT)
            .setAction(com.goodwy.commons.R.string.support) {
                launchPurchase()
            }

        val bgDrawable = ResourcesCompat.getDrawable(view.resources, com.goodwy.commons.R.drawable.button_background_16dp, null)
        snackbar.view.background = bgDrawable
        val properBackgroundColor = getProperBackgroundColor()
        val backgroundColor = if (properBackgroundColor == Color.BLACK) getBottomNavigationBackgroundColor().lightenColor(6) else getBottomNavigationBackgroundColor().darkenColor(6)
        snackbar.setBackgroundTint(backgroundColor)
        snackbar.setTextColor(getProperTextColor())
        snackbar.setActionTextColor(getProperPrimaryColor())
        snackbar.show()
    }
}

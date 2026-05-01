package dev.goodwy.voicerecorder.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import android.view.Menu
import com.behaviorule.arturdumchev.library.pixels
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import dev.goodwy.voicerecorder.BuildConfig
import dev.goodwy.voicerecorder.R
import dev.goodwy.voicerecorder.databinding.ActivitySettingsBinding
import dev.goodwy.voicerecorder.dialogs.FilenamePatternDialog
import dev.goodwy.voicerecorder.dialogs.MoveRecordingsDialog
import dev.goodwy.voicerecorder.extensions.config
import dev.goodwy.voicerecorder.extensions.deleteTrashedRecordings
import dev.goodwy.voicerecorder.extensions.getAllRecordings
import dev.goodwy.voicerecorder.extensions.hasRecordings
import dev.goodwy.voicerecorder.extensions.launchAbout
import dev.goodwy.voicerecorder.extensions.launchFolderPicker
import dev.goodwy.voicerecorder.extensions.launchPurchase
import dev.goodwy.voicerecorder.helpers.BITRATES
import dev.goodwy.voicerecorder.helpers.DEFAULT_BITRATE
import dev.goodwy.voicerecorder.helpers.DEFAULT_SAMPLING_RATE
import dev.goodwy.voicerecorder.helpers.EXTENSION_M4A
import dev.goodwy.voicerecorder.helpers.EXTENSION_MP3
import dev.goodwy.voicerecorder.helpers.EXTENSION_OGG
import dev.goodwy.voicerecorder.helpers.SAMPLING_RATES
import dev.goodwy.voicerecorder.helpers.SAMPLING_RATE_BITRATE_LIMITS
import dev.goodwy.voicerecorder.models.Events
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import dev.goodwy.voicerecorder.extensions.showSnackbar
import dev.goodwy.voicerecorder.helpers.SWIPE_ACTION_DELETE
import dev.goodwy.voicerecorder.helpers.SWIPE_ACTION_EDIT
import dev.goodwy.voicerecorder.helpers.SWIPE_ACTION_OPEN
import dev.goodwy.voicerecorder.helpers.SWIPE_ACTION_SHARE
import dev.goodwy.voicerecorder.helpers.VIEVPAGE_LAST
import dev.goodwy.voicerecorder.helpers.VIEVPAGE_PLAYER
import dev.goodwy.voicerecorder.helpers.VIEVPAGE_RECORDER
import dev.goodwy.voicerecorder.helpers.whatsNewList
import kotlin.collections.ArrayList
import org.greenrobot.eventbus.EventBus
import java.util.Locale
import kotlin.math.abs
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private var recycleBinContentSize = 0
    private lateinit var binding: ActivitySettingsBinding

    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    override fun onCreate(savedInstanceState: Bundle?) {
        useOverflowIcon = false
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupOptionsMenu()

        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)

        val iapList: java.util.ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
        val subList: java.util.ArrayList<String> =
            arrayListOf(
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        val ruStoreList: java.util.ArrayList<String> =
            arrayListOf(
                productIdX1, productIdX2, productIdX3,
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        PurchaseHelper().checkPurchase(
            this@SettingsActivity,
            iapList = iapList,
            subList = subList,
            ruStoreList = ruStoreList
        ) { updatePro ->
            if (updatePro) updatePro()
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()

        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupRoundIcon()
        setupOverflowIcon()

        setupUseEnglish()
        setupLanguage()
        setupChangeDateTimeFormat()

        setupSaveRecordingsFolder()
        setupFilenamePattern()
        setupExtension()
        setupBitrate()
        setupSamplingRate()
        setupMicrophoneMode()
        setupRecordAfterLaunch()
        setupKeepScreenOn()

        setupDefaultTab()
        setupUseIconTabs()
        setupScreenSlideAnimation()

        setupUseSwipeToAction()
        setupSwipeWidth()
        setupSwipeVibration()
        setupSwipeRipple()
        setupSwipeRightAction()
        setupSwipeLeftAction()
        setupDeleteConfirmation()

        setupShowSearchBar()

        setupShowDividers()

        setupUseRecycleBin()
        setupEmptyRecycleBin()

        setupTipJar()
        setupAbout()

        setupColors()
        updateTextColors(binding.settingsHolder)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.whats_new -> {
                    WhatsNewDialog(this@SettingsActivity, whatsNewList())
                    true
                }
                else -> false
            }
        }
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

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(checkPro())
        settingsPurchaseThankYouHolder.onClick = { launchPurchase() }
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

    private fun setupFilenamePattern() {
        binding.settingsFilenamePattern.text = config.filenamePattern
        binding.settingsFilenamePatternHolder.setOnClickListener {
            FilenamePatternDialog(this) { newPattern ->
                binding.settingsFilenamePattern.text = newPattern
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
                adjustBitrate()
                adjustSamplingRate()
            }
        }
    }

    private fun setupBitrate() {
        binding.settingsBitrate.text = getBitrateText(config.bitrate)
        binding.settingsBitrateHolder.setOnClickListener {
            val items = BITRATES[config.extension]!!
                .map { RadioItem(it, getBitrateText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.bitrate, R.string.bitrate) {
                config.bitrate = it as Int
                binding.settingsBitrate.text = getBitrateText(config.bitrate)
                adjustSamplingRate()
            }
        }
    }

    private fun getBitrateText(value: Int): String {
        return getString(R.string.bitrate_value).format(value / 1000)
    }

    private fun adjustBitrate() {
        val availableBitrates = BITRATES[config.extension]!!
        if (!availableBitrates.contains(config.bitrate)) {
            val currentBitrate = config.bitrate
            val closestBitrate = availableBitrates.minByOrNull { abs(it - currentBitrate) }
                ?: DEFAULT_BITRATE

            config.bitrate = closestBitrate
            binding.settingsBitrate.text = getBitrateText(config.bitrate)
        }
    }

    private fun setupSamplingRate() {
        binding.settingsSamplingRate.text = getSamplingRateText(config.samplingRate)
        binding.settingsSamplingRateHolder.setOnClickListener {
            val items = getSamplingRatesArray()
                .map { RadioItem(it, getSamplingRateText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.samplingRate, R.string.sample_rate) {
                config.samplingRate = it as Int
                binding.settingsSamplingRate.text = getSamplingRateText(config.samplingRate)
            }
        }
    }

    private fun getSamplingRateText(value: Int): String {
        return getString(R.string.sampling_rate_value).format(value)
    }

    private fun getSamplingRatesArray(): ArrayList<Int> {
        val baseRates = SAMPLING_RATES[config.extension]!!
        val limits = SAMPLING_RATE_BITRATE_LIMITS[config.extension]!!
        val filteredRates = baseRates.filter {
            config.bitrate in limits[it]!![0]..limits[it]!![1]
        } as ArrayList
        return filteredRates
    }

    private fun adjustSamplingRate() {
        val availableSamplingRates = getSamplingRatesArray()
        if (!availableSamplingRates.contains(config.samplingRate)) {
            if (availableSamplingRates.contains(DEFAULT_SAMPLING_RATE)) {
                config.samplingRate = DEFAULT_SAMPLING_RATE
            } else {
                config.samplingRate = availableSamplingRates.last()
            }
            binding.settingsSamplingRate.text = getSamplingRateText(config.samplingRate)
        }
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
            config.needRestart = true
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
            } catch (_: Exception) {
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

    private fun setupMicrophoneMode() {
        binding.settingsMicrophoneMode.text = config.getMicrophoneModeText(config.microphoneMode)
        binding.settingsMicrophoneModeHolder.setOnClickListener {
            if (config.wasMicModeWarningShown) {
                showMicrophoneModeDialog()
            } else {
                ConfirmationDialog(
                    activity = this,
                    dialogTitle = getString(R.string.microphone_mode),
                    message = getString(R.string.change_microphone_mode_confirmation),
                    negative = 0,
                    positive = com.goodwy.commons.R.string.ok
                ) {
                    config.wasMicModeWarningShown = true
                    showMicrophoneModeDialog()
                }
            }
        }
    }

    private fun showMicrophoneModeDialog() {
        val items = getMediaRecorderAudioSources()
            .map { microphoneMode ->
                RadioItem(
                    id = microphoneMode,
                    title = config.getMicrophoneModeText(microphoneMode)
                )
            } as ArrayList

        RadioGroupDialog(
            activity = this@SettingsActivity,
            items = items,
            checkedItemId = config.microphoneMode,
            titleId = R.string.microphone_mode
        ) {
            config.microphoneMode = it as Int
            binding.settingsMicrophoneMode.text = config.getMicrophoneModeText(config.microphoneMode)
        }
    }

    private fun getMediaRecorderAudioSources(): List<Int> {
        return buildList {
            add(MediaRecorder.AudioSource.DEFAULT)
            add(MediaRecorder.AudioSource.CAMCORDER)
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)

            if (isQPlus()) {
                add(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
            }

            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.UNPROCESSED)
        }
    }

    private fun setupDefaultTab() {
        binding.settingsDefaultTab.text = getDefaultTabText()
        binding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(VIEVPAGE_LAST, getString(com.goodwy.commons.R.string.last_used_tab)),
                RadioItem(VIEVPAGE_RECORDER, getString(com.goodwy.strings.R.string.recorder_g), icon = com.goodwy.commons.R.drawable.ic_microphone_vector),
                RadioItem(VIEVPAGE_PLAYER, getString(com.goodwy.strings.R.string.player_g), icon = R.drawable.ic_playlist_play_vector)
            )

            RadioGroupIconDialog(this@SettingsActivity, items, config.viewPage) {
                config.viewPage = it as Int
                binding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun setupUseIconTabs() {
        binding.settingsUseIconTabs.isChecked = config.useIconTabs
        binding.settingsUseIconTabsHolder.setOnClickListener {
            binding.settingsUseIconTabs.toggle()
            config.useIconTabs = binding.settingsUseIconTabs.isChecked
            config.needRestart = true
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
                config.needRestart = true
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
                config.needRestart = true
                updateSwipeToActionVisible()
            }
        }
    }

    private fun setupSwipeWidth() = binding.apply {
        settingsSwipeWidthHolder.beVisibleIf(config.useSwipeToAction)
        settingsSwipeWidth.text = getSwipeWidthText(config.swipeToActionWidth)
        settingsSwipeWidthHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(2, "1/2"),
                RadioItem(3, "1/3"),
                RadioItem(4, "1/4"),
                RadioItem(5, "1/5"),
            )

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.swipeToActionWidth,
                com.goodwy.strings.R.string.swipe_width,
                defaultItemId = 2
            ) {
                config.swipeToActionWidth = it as Int
                config.needRestart = true
                settingsSwipeWidth.text = getSwipeWidthText(config.swipeToActionWidth)
            }
        }
    }

    private fun getSwipeWidthText(swipeWidth: Int): String {
        return when (swipeWidth) {
            3 -> "1/3"
            4 -> "1/4"
            5 -> "1/5"
            else -> "1/2"
        }
    }

    private fun updateSwipeToActionVisible() {
        binding.apply {
            settingsSwipeWidthHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeVibrationHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRippleHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRightActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeLeftActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSkipDeleteConfirmationHolder.beVisibleIf(
                config.useSwipeToAction &&
                        (config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            )
        }
    }

    private fun setupSwipeVibration() {
        binding.apply {
            settingsSwipeVibration.isChecked = config.swipeVibration
            settingsSwipeVibrationHolder.setOnClickListener {
                settingsSwipeVibration.toggle()
                config.swipeVibration = settingsSwipeVibration.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupSwipeRipple() {
        binding.apply {
            settingsSwipeRipple.isChecked = config.swipeRipple
            settingsSwipeRippleHolder.setOnClickListener {
                settingsSwipeRipple.toggle()
                config.swipeRipple = settingsSwipeRipple.isChecked
                config.needRestart = true
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
                config.needRestart = true
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
                    config.needRestart = true
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

    private fun setupShowSearchBar() = binding.apply {
        settingsShowSearchBar.isChecked = config.showSearchBar
        settingsShowSearchBarHolder.setOnClickListener {
            settingsShowSearchBar.toggle()
            config.showSearchBar = settingsShowSearchBar.isChecked
            config.needRestart = true
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
            background.applyColorFilter(getColoredMaterialStatusBarColor())
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupAbout() = binding.apply {
        val flavorName = BuildConfig.FLAVOR
        val storeDisplayName = when (flavorName) {
            "gplay" -> "Google Play"
            "foss" -> "FOSS"
            "rustore" -> "RuStore"
            else -> ""
        }
        val versionName = BuildConfig.VERSION_NAME
        val fullVersionText = "Version: $versionName ($storeDisplayName)"

        settingsAboutVersion.text = fullVersionText
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
            binding.settingsRecordingLabel,
            binding.settingsAudioSectionLabel,
            binding.settingsTabsLabel,
            binding.settingsSwipeGesturesLabel,
            binding.settingsTopAppBarLabel,
            binding.settingsListViewLabel,
            binding.settingsWidgetsLabel,
            binding.settingsRecycleBinLabel,
            binding.settingsOtherLabel
        ).forEach {
            it.setTextColor(primaryColor)
        }

        arrayOf(
            binding.settingsColorCustomizationHolder,
            binding.settingsGeneralHolder,
            binding.settingsRecordingHolder,
            binding.settingsAudioSectionHolder,
            binding.settingsTabsHolder,
            binding.settingsSwipeGesturesHolder,
            binding.settingsTopAppBarHolder,
            binding.settingsListViewHolder,
            binding.settingsWidgetsHolder,
            binding.settingsRecycleBinHolder,
            binding.settingsOtherHolder
        ).forEach {
            it.setCardBackgroundColor(getSurfaceColor())
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

    private fun checkPro() = resources.getBoolean(R.bool.is_pro_app) || isPro()
}

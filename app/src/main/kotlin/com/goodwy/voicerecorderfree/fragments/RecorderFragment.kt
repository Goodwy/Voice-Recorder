package com.goodwy.voicerecorderfree.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.compose.extensions.getActivity
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.extensions.*
import com.goodwy.voicerecorderfree.R
import com.goodwy.voicerecorderfree.databinding.FragmentRecorderBinding
import com.goodwy.voicerecorderfree.extensions.config
import com.goodwy.voicerecorderfree.extensions.ensureStoragePermission
import com.goodwy.voicerecorderfree.extensions.setKeepScreenAwake
import com.goodwy.voicerecorderfree.helpers.*
import com.goodwy.voicerecorderfree.models.Events
import com.goodwy.voicerecorderfree.services.RecorderService
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Timer
import java.util.TimerTask

class RecorderFragment(
    context: Context,
    attributeSet: AttributeSet
) : MyViewPagerFragment(context, attributeSet) {

    private var status = RECORDING_STOPPED
    private var pauseBlinkTimer = Timer()
    private var bus: EventBus? = null
    private lateinit var binding: FragmentRecorderBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecorderBinding.bind(this)
    }

    override fun onResume() {
        setupColors()
        if (!RecorderService.isRunning) {
            status = RECORDING_STOPPED
        }

        refreshView()
    }

    override fun onDestroy() {
        bus?.unregister(this)
        pauseBlinkTimer.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupColors()
        binding.recorderVisualizer.recreate()
        bus = EventBus.getDefault()
        bus!!.register(this)

        updateRecordingDuration(0)
        binding.toggleRecordingButton.setOnClickListener {
            val activity = context as? BaseSimpleActivity
            activity?.ensureStoragePermission {
                if (it) {
                    activity.handleNotificationPermission { granted ->
                        if (granted) {
//                            cycleRecordingState()
                            toggleRecording()
                        } else {
                            PermissionRequiredDialog(
                                activity = context as BaseSimpleActivity,
                                textId = com.goodwy.commons.R.string.allow_notifications_voice_recorder,
                                positiveActionCallback = {
                                    (context as BaseSimpleActivity).openNotificationSettings()
                                }
                            )
                        }
                    }
                } else {
                    activity.toast(com.goodwy.commons.R.string.no_storage_permissions)
                }
            }
        }

//        binding.cancelRecordingButton.setDebouncedClickListener { cancelRecording() }
//        binding.saveRecordingButton.setDebouncedClickListener { saveRecording() }

        binding.togglePauseButton.setOnClickListener {
            RxAnimation.from(binding.recordingDuration)
                .shake(shakeTranslation = 2f)
                .subscribe()

            Intent(context, RecorderService::class.java).apply {
                action = TOGGLE_PAUSE
                context.startService(this)
            }
        }

        Intent(context, RecorderService::class.java).apply {
            action = GET_RECORDER_INFO
            try {
                context.startService(this)
            } catch (e: Exception) {
            }
        }
    }

    private fun setupColors() {
        val properTextColor = context.getProperTextColor()
        val properPrimaryColor = context.getProperPrimaryColor()
        binding.toggleRecordingButton.apply {
//            setImageResource(R.drawable.ic_record_animated)
            background.applyColorFilter(properTextColor) // Ring color
        }

        binding.togglePauseButton.apply {
            setImageDrawable(resources.getColoredDrawableWithColor(R.drawable.ic_pause_vector, properTextColor))
        }

        binding.recorderVisualizer.chunkColor = properPrimaryColor
        binding.recordingDuration.setTextColor(properTextColor)

        binding.recordingControlsWrapper.setBackgroundColor(context.getBottomNavigationBackgroundColor())
    }

    private fun updateRecordingDuration(duration: Int) {
        binding.recordingDuration.text = duration.getFormattedDuration()
    }

    private fun toggleRecording() {
        status = if (status == RECORDING_RUNNING || status == RECORDING_PAUSED) {
            RECORDING_STOPPED
        } else {
            RECORDING_RUNNING
        }

        if (status == RECORDING_RUNNING) {
            binding.toggleRecordingButton.post {
                binding.toggleRecordingButton.isSelected = true
            }
            startRecording()
        } else {
            binding.togglePauseButton.beGone()
            binding.recordingDuration.beGone()
            binding.toggleRecordingButton.isSelected = false
            saveRecording()
        }
    }

    private fun cycleRecordingState() {
        when (status) {
            RECORDING_PAUSED,
            RECORDING_RUNNING -> {
                Intent(context, RecorderService::class.java).apply {
                    action = TOGGLE_PAUSE
                    context.startService(this)
                }
            }

            else -> {
                startRecording()
            }
        }

        status = if (status == RECORDING_RUNNING) RECORDING_PAUSED else RECORDING_RUNNING
    }

    private fun startRecording() {
        Intent(context, RecorderService::class.java).apply {
            context.startService(this)
        }
        binding.recorderVisualizer.recreate()
    }

    private fun cancelRecording() {
        status = RECORDING_STOPPED
        Intent(context, RecorderService::class.java).apply {
            action = CANCEL_RECORDING
            context.startService(this)
        }
        refreshView()
    }

    private fun saveRecording() {
        status = RECORDING_STOPPED
        Intent(context, RecorderService::class.java).apply {
            context.stopService(this)
        }
        refreshView()
    }

    private fun getPauseBlinkTask() = object : TimerTask() {
        override fun run() {
            if (status == RECORDING_PAUSED) {
                // update just the alpha so that it will always be clickable
                Handler(Looper.getMainLooper()).post {
                    binding.togglePauseButton.alpha =
                        if (binding.togglePauseButton.alpha == 0f) 1f else 0f
                }
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun refreshView() {
        binding.togglePauseButton.beVisibleIf(status != RECORDING_STOPPED)
        binding.recordingDuration.beVisibleIf(status != RECORDING_STOPPED)
        pauseBlinkTimer.cancel()

        when (status) {
            RECORDING_PAUSED -> {
                pauseBlinkTimer = Timer()
                pauseBlinkTimer.scheduleAtFixedRate(getPauseBlinkTask(), 500, 500)
                binding.toggleRecordingButton.post {
                    binding.toggleRecordingButton.isSelected = true
                }
            }

            RECORDING_RUNNING -> {
                binding.togglePauseButton.alpha = 1f
                if (context.config.keepScreenOn) {
                    context.getActivity().setKeepScreenAwake(true)
                }
                binding.toggleRecordingButton.post {
                    binding.toggleRecordingButton.isSelected = true
                }
            }

            else -> {
                binding.recorderVisualizer.recreate()
                binding.recordingDuration.text = null
                binding.toggleRecordingButton.isSelected = false
            }
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotDurationEvent(event: Events.RecordingDuration) {
        updateRecordingDuration(event.duration)
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotStatusEvent(event: Events.RecordingStatus) {
        status = event.status
        refreshView()
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotAmplitudeEvent(event: Events.RecordingAmplitude) {
        val amplitude = event.amplitude
        if (status == RECORDING_RUNNING) {
            binding.recorderVisualizer.update(amplitude)
        }
    }
}

package com.goodwy.voicerecorder.fragments

import android.content.Context
import android.util.AttributeSet
import com.goodwy.commons.extensions.areSystemAnimationsEnabled
import com.goodwy.commons.extensions.beVisibleIf
import com.goodwy.commons.extensions.getProperBackgroundColor
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.commons.extensions.updateTextColors
import com.goodwy.voicerecorder.activities.SimpleActivity
import com.goodwy.voicerecorder.adapters.TrashAdapter
import com.goodwy.voicerecorder.databinding.FragmentTrashBinding
import com.goodwy.voicerecorder.extensions.config
import com.goodwy.voicerecorder.interfaces.RefreshRecordingsListener
import com.goodwy.voicerecorder.models.Events
import com.goodwy.voicerecorder.models.Recording
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TrashFragment(
    context: Context,
    attributeSet: AttributeSet
) : MyViewPagerFragment(context, attributeSet), RefreshRecordingsListener {

    private var itemsIgnoringSearch = ArrayList<Recording>()
    private var lastSearchQuery = ""
    private var bus: EventBus? = null
    private var prevSavePath = ""
    private lateinit var binding: FragmentTrashBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentTrashBinding.bind(this)
    }

    override fun onResume() {
        setupColors()
        if ((prevSavePath.isNotEmpty() && context!!.config.saveRecordingsFolder != prevSavePath) || context!!.config.updateRecycleBin) {
            context!!.config.updateRecycleBin = false
            loadRecordings(trashed = true)
        } else {
            getRecordingsAdapter()?.apply {
                updateTextColor(context.getProperTextColor())
                updateBackgroundColor(context.getProperBackgroundColor())
            }
        }

        storePrevPath()
    }

    override fun onDestroy() {
        bus?.unregister(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        bus = EventBus.getDefault()
        bus!!.register(this)
        setupColors()
        loadRecordings(trashed = true)
        storePrevPath()
    }

    override fun refreshRecordings() = loadRecordings(trashed = true)

    override fun playRecording(recording: Recording, playOnPrepared: Boolean) {}

    override fun onLoadingStart() {
        if (itemsIgnoringSearch.isEmpty()) {
            binding.loadingIndicator.show()
        } else {
            binding.loadingIndicator.hide()
        }
    }

    override fun onLoadingEnd(recordings: ArrayList<Recording>) {
        binding.loadingIndicator.hide()
        binding.trashPlaceholder.beVisibleIf(recordings.isEmpty())
        itemsIgnoringSearch = recordings
        setupAdapter(itemsIgnoringSearch)
    }

    private fun setupAdapter(recordings: ArrayList<Recording>) {
        binding.trashFastscroller.beVisibleIf(recordings.isNotEmpty())
        binding.trashPlaceholder.beVisibleIf(recordings.isEmpty())
        if (recordings.isEmpty()) {
            val stringId = if (lastSearchQuery.isEmpty()) {
                com.goodwy.commons.R.string.recycle_bin_empty
            } else {
                com.goodwy.commons.R.string.no_items_found
            }

            binding.trashPlaceholder.text = context.getString(stringId)
        }

        val adapter = getRecordingsAdapter()
        if (adapter == null) {
            TrashAdapter(context as SimpleActivity, recordings, this, binding.trashList).apply {
                binding.trashList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                binding.trashList.scheduleLayoutAnimation()
            }
        } else {
            adapter.updateItems(recordings)
        }
    }

    fun onSearchTextChanged(text: String) {
        lastSearchQuery = text
        val filtered = itemsIgnoringSearch.filter { it.title.contains(text, true) }
            .toMutableList() as ArrayList<Recording>
        setupAdapter(filtered)
    }

    private fun getRecordingsAdapter() = binding.trashList.adapter as? TrashAdapter

    private fun storePrevPath() {
        prevSavePath = context!!.config.saveRecordingsFolder
    }

    private fun setupColors() {
        val properPrimaryColor = context.getProperPrimaryColor()
        binding.trashFastscroller.updateColors(properPrimaryColor)
        context.updateTextColors(binding.trashHolder)
    }

    fun finishActMode() = getRecordingsAdapter()?.finishActMode()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun recordingMovedToRecycleBin(@Suppress("UNUSED_PARAMETER") event: Events.RecordingTrashUpdated) {
        refreshRecordings()
    }
}

package com.goodwy.voicerecorder.adapters

import android.annotation.SuppressLint
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.voicerecorder.BuildConfig
import com.goodwy.voicerecorder.R
import com.goodwy.voicerecorder.activities.SimpleActivity
import com.goodwy.voicerecorder.databinding.ItemRecordingBinding
import com.goodwy.voicerecorder.databinding.ItemRecordingSwipeBinding
import com.goodwy.voicerecorder.dialogs.DeleteConfirmationDialog
import com.goodwy.voicerecorder.dialogs.RenameRecordingDialog
import com.goodwy.voicerecorder.extensions.config
import com.goodwy.voicerecorder.extensions.deleteRecordings
import com.goodwy.voicerecorder.extensions.trashRecordings
import com.goodwy.voicerecorder.helpers.*
import com.goodwy.voicerecorder.interfaces.RefreshRecordingsListener
import com.goodwy.voicerecorder.models.Events
import com.goodwy.voicerecorder.models.Recording
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import org.greenrobot.eventbus.EventBus
import kotlin.math.min

class RecordingsAdapter(
    activity: SimpleActivity,
    var recordings: ArrayList<Recording>,
    private val refreshListener: RefreshRecordingsListener,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    var currRecordingId = 0

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recordings

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
            findItem(R.id.cab_open_with).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_rename -> renameRecording()
            R.id.cab_share -> shareRecordings()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
            R.id.cab_open_with -> openRecordingWith()
        }
    }

    override fun getSelectableItemCount() = recordings.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int): Int? {
        return recordings.getOrNull(position)?.id
    }

    override fun getItemKeyPosition(key: Int): Int {
        return recordings.indexOfFirst { it.id == key }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            if (activity.config.useSwipeToAction) ItemRecordingSwipeBinding.inflate(layoutInflater, parent, false).root
            else ItemRecordingBinding.inflate(layoutInflater, parent, false).root
        return createViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.bindView(
            any = recording,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, recording)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recordings.size

    private fun getItemWithKey(key: Int): Recording? = recordings.firstOrNull { it.id == key }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: ArrayList<Recording>) {
        if (newItems.hashCode() != recordings.hashCode()) {
            recordings = newItems
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun renameRecording() {
        val recording = getItemWithKey(selectedKeys.first()) ?: return
        RenameRecordingDialog(activity, recording) {
            finishActMode()
            refreshListener.refreshRecordings()
        }
    }

    private fun openRecordingWith() {
        val recording = getItemWithKey(selectedKeys.first()) ?: return
        val path = recording.path
        activity.openPathIntent(path, false, BuildConfig.APPLICATION_ID, "audio/*")
    }

    private fun shareRecordings() {
        val selectedItems = getSelectedItems()
        val paths = selectedItems.map { it.path }
        activity.sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_recordings, itemsCnt, itemsCnt)
        }

        val baseString = if (activity.config.useRecycleBin) {
            com.goodwy.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            R.string.delete_recordings_confirmation
        }
        val question = String.format(resources.getString(baseString), items)

        DeleteConfirmationDialog(
            activity = activity,
            message = question,
            showSkipRecycleBinOption = activity.config.useRecycleBin
        ) { skipRecycleBin ->
            ensureBackgroundThread {
                val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin
                if (toRecycleBin) {
                    trashRecordings()
                } else {
                    deleteRecordings()
                }
            }
        }
    }

    private fun deleteRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val oldRecordingIndex = recordings.indexOfFirst { it.id == currRecordingId }
        val recordingsToRemove = recordings
            .filter { selectedKeys.contains(it.id) } as ArrayList<Recording>

        val positions = getSelectedItemPositions()

        activity.deleteRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
            }
        }
    }

    private fun trashRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val oldRecordingIndex = recordings.indexOfFirst { it.id == currRecordingId }
        val recordingsToRemove =
            recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
        val positions = getSelectedItemPositions()

        activity.trashRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
                EventBus.getDefault().post(Events.RecordingTrashUpdated())
            }
        }
    }

    private fun doDeleteAnimation(
        oldRecordingIndex: Int,
        recordingsToRemove: ArrayList<Recording>,
        positions: ArrayList<Int>
    ) {
        recordings.removeAll(recordingsToRemove.toSet())
        activity.runOnUiThread {
            if (recordings.isEmpty()) {
                refreshListener.refreshRecordings()
                finishActMode()
            } else {
                positions.sortDescending()
                removeSelectedItems(positions)
                if (recordingsToRemove.map { it.id }.contains(currRecordingId)) {
                    val newRecordingIndex = min(oldRecordingIndex, recordings.size - 1)
                    val newRecording = recordings[newRecordingIndex]
                    refreshListener.playRecording(newRecording, false)
                }
            }
        }
    }

    fun updateCurrentRecording(newId: Int) {
        val oldId = currRecordingId
        currRecordingId = newId
        notifyItemChanged(recordings.indexOfFirst { it.id == oldId })
        notifyItemChanged(recordings.indexOfFirst { it.id == newId })
    }

    private fun getSelectedItems(): ArrayList<Recording> {
        return recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
    }

    private fun setupView(view: View, recording: Recording) {
        if (activity.config.useSwipeToAction) {
            ItemRecordingSwipeBinding.bind(view).apply {
                recordingFrame.setupViewBackground(activity)
                itemHolder.setBackgroundColor(backgroundColor)
                recordingSwipe.isSelected = selectedKeys.contains(recording.id)

                arrayListOf(
                    recordingTitle,
                    recordingDate,
                    recordingDuration,
                    recordingSize
                ).forEach {
                    it.setTextColor(textColor)
                }

                if (recording.id == currRecordingId) {
                    recordingTitle.setTextColor(root.context.getProperPrimaryColor())
                }

                recordingTitle.text = recording.title
                recordingDate.text = recording.timestamp.formatDate(root.context)
                recordingDuration.text = recording.duration.getFormattedDuration()
                recordingSize.text = recording.size.formatSize()

                overflowMenuIcon.drawable.apply {
                    mutate()
                    setTint(activity.getProperTextColor())
                }

                overflowMenuIcon.setOnClickListener {
                    showPopupMenu(overflowMenuAnchor, recording)
                }

                divider.setBackgroundColor(textColor)
                divider.beInvisibleIf(!root.context.config.useDividers || recordings.last() == recording)

                //swipe
                val isRTL = activity.isRTLLayout
                val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
                swipeLeftIcon.setImageResource(swipeActionImageResource(swipeLeftAction))
                swipeLeftIcon.setColorFilter(properPrimaryColor.getContrastColor())
                swipeLeftIconHolder.setBackgroundColor(swipeActionColor(swipeLeftAction))

                val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
                swipeRightIcon.setImageResource(swipeActionImageResource(swipeRightAction))
                swipeRightIcon.setColorFilter(properPrimaryColor.getContrastColor())
                swipeRightIconHolder.setBackgroundColor(swipeActionColor(swipeRightAction))

                if (activity.config.swipeRipple) {
                    recordingSwipe.setRippleColor(SwipeDirection.Left, swipeActionColor(swipeLeftAction))
                    recordingSwipe.setRippleColor(SwipeDirection.Right, swipeActionColor(swipeRightAction))
                }

                recordingSwipe.useHapticFeedback = activity.config.swipeVibration
                recordingSwipe.swipeGestureListener = object : SwipeGestureListener {
                    override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                        finishActMode()
                        val swipeLeftOrRightAction =
                            if (activity.isRTLLayout) activity.config.swipeRightAction else activity.config.swipeLeftAction
                        swipeAction(swipeLeftOrRightAction, recording)
                        slideLeftReturn(swipeLeftIcon, swipeLeftIconHolder)
                        return true
                    }

                    override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                        finishActMode()
                        val swipeRightOrLeftAction =
                            if (activity.isRTLLayout) activity.config.swipeLeftAction else activity.config.swipeRightAction
                        swipeAction(swipeRightOrLeftAction, recording)
                        slideRightReturn(swipeRightIcon, swipeRightIconHolder)
                        return true
                    }

                    override fun onSwipedActivated(swipedRight: Boolean) {
                        if (swipedRight) slideRight(swipeRightIcon, swipeRightIconHolder)
                        else slideLeft(swipeLeftIcon)
                    }

                    override fun onSwipedDeactivated(swipedRight: Boolean) {
                        if (swipedRight) slideRightReturn(swipeRightIcon, swipeRightIconHolder)
                        else slideLeftReturn(swipeLeftIcon, swipeLeftIconHolder)
                    }
                }
            }
        } else {
            ItemRecordingBinding.bind(view).apply {
                root.setupViewBackground(activity)
                recordingFrame.isSelected = selectedKeys.contains(recording.id)

                arrayListOf(
                    recordingTitle,
                    recordingDate,
                    recordingDuration,
                    recordingSize
                ).forEach {
                    it.setTextColor(textColor)
                }

                if (recording.id == currRecordingId) {
                    recordingTitle.setTextColor(root.context.getProperPrimaryColor())
                }

                recordingTitle.text = recording.title
                recordingDate.text = recording.timestamp.formatDate(root.context)
                recordingDuration.text = recording.duration.getFormattedDuration()
                recordingSize.text = recording.size.formatSize()

                overflowMenuIcon.drawable.apply {
                    mutate()
                    setTint(activity.getProperTextColor())
                }

                overflowMenuIcon.setOnClickListener {
                    showPopupMenu(overflowMenuAnchor, recording)
                }

                divider.setBackgroundColor(textColor)
                divider.beInvisibleIf(!root.context.config.useDividers || recordings.last() == recording)
            }
        }
    }

    private fun slideRight(view: View, parent: View) {
        view.animate()
            .x(parent.right - activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin) - view.width)
    }

    private fun slideLeft(view: View) {
        view.animate()
            .x(activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin))
    }

    private fun slideRightReturn(view: View, parent: View) {
        view.animate()
            .x(parent.left + activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin))
    }

    private fun slideLeftReturn(view: View, parent: View) {
        view.animate()
            .x(parent.width - activity.resources.getDimension(com.goodwy.commons.R.dimen.big_margin) - view.width)
    }

    override fun onChange(position: Int) = recordings.getOrNull(position)?.title ?: ""

    @SuppressLint("NotifyDataSetChanged")
    private fun showPopupMenu(view: View, recording: Recording) {
        if (selectedKeys.isNotEmpty()) {
            selectedKeys.clear()
            notifyDataSetChanged()
        }

        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            menu.findItem(R.id.cab_select_all).isVisible = false
            setOnMenuItemClickListener { item ->
                val recordingId = recording.id
                when (item.itemId) {
                    R.id.cab_rename -> {
                        executeItemMenuOperation(recordingId) {
                            renameRecording()
                        }
                    }

                    R.id.cab_share -> {
                        executeItemMenuOperation(recordingId) {
                            shareRecordings()
                        }
                    }

                    R.id.cab_open_with -> {
                        executeItemMenuOperation(recordingId) {
                            openRecordingWith()
                        }
                    }

                    R.id.cab_delete -> {
                        executeItemMenuOperation(recordingId, removeAfterCallback = false) {
                            askConfirmDelete()
                        }
                    }
                }

                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(
        callId: Int,
        removeAfterCallback: Boolean = true,
        callback: () -> Unit
    ) {
        selectedKeys.add(callId)
        callback()
        if (removeAfterCallback) {
            selectedKeys.remove(callId)
        }
    }

    private fun swipeActionImageResource(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.drawable.ic_delete_outline
            SWIPE_ACTION_SHARE -> com.goodwy.commons.R.drawable.ic_ios_share
            SWIPE_ACTION_OPEN -> R.drawable.ic_open_with
            else -> R.drawable.ic_file_rename
        }
    }

    private fun swipeActionColor(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(com.goodwy.commons.R.color.red_missed, activity.theme)
            SWIPE_ACTION_SHARE -> resources.getColor(R.color.color_primary, activity.theme)
            SWIPE_ACTION_OPEN -> resources.getColor(R.color.green_call, activity.theme)
            else -> resources.getColor(R.color.swipe_purple, activity.theme)
        }
    }

    private fun swipeAction(swipeAction: Int, recording: Recording) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(recording)
            SWIPE_ACTION_SHARE -> swipedShare(recording)
            SWIPE_ACTION_OPEN -> swipedOpenRecordingWith(recording)
            else -> swipedRenameRecording(recording)
        }
    }

    private fun swipedRenameRecording(recording: Recording) {
        RenameRecordingDialog(activity, recording) {
            refreshListener.refreshRecordings()
        }
    }

    private fun swipedOpenRecordingWith(recording: Recording) {
        val path = recording.path
        activity.openPathIntent(path, false, BuildConfig.APPLICATION_ID, "audio/*")
    }

    private fun swipedShare(recording: Recording) {
        val path = recording.path
        activity.sharePathIntent(path, BuildConfig.APPLICATION_ID)
    }

    private fun swipedDelete(recording: Recording) {
        finishActMode()
        if (activity.config.skipDeleteConfirmation) {
            if (activity.config.useRecycleBin) {
                moveMediaStoreRecordingsToRecycleBinSwipe(recording)
            } else {
                deleteMediaStoreRecordingsSwipe(recording)
            }
        } else askConfirmDeleteSwipe(recording)
    }

    private fun askConfirmDeleteSwipe(recording: Recording) {
        val items = "\"${recording.title}\""

        val baseString = if (activity.config.useRecycleBin) {
            com.goodwy.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            R.string.delete_recordings_confirmation
        }
        val question = String.format(resources.getString(baseString), items)

        DeleteConfirmationDialog(activity, question, activity.config.useRecycleBin) { skipRecycleBin ->
            ensureBackgroundThread {
                val toRecycleBin = !skipRecycleBin && activity.config.useRecycleBin
                if (toRecycleBin) {
                    moveMediaStoreRecordingsToRecycleBinSwipe(recording)
                } else {
                    deleteMediaStoreRecordingsSwipe(recording)
                }
            }
        }
    }

    private fun moveMediaStoreRecordingsToRecycleBinSwipe(recording: Recording) {
        val oldRecordingIndex = recordings.indexOfFirst { it.id == currRecordingId }
        val recordingsToRemove = arrayListOf(recording)
        val position = getItemKeyPosition(recording.id)
        val positions = arrayListOf(position)

        activity.trashRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
                EventBus.getDefault().post(Events.RecordingTrashUpdated())
            }
        }
    }

    private fun deleteMediaStoreRecordingsSwipe(recording: Recording) {
        val oldRecordingIndex = recordings.indexOfFirst { it.id == currRecordingId }
        val recordingsToRemove = arrayListOf(recording)
        val position = getItemKeyPosition(recording.id)
        val positions = arrayListOf(position)

        activity.deleteRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
            }
        }
    }
}

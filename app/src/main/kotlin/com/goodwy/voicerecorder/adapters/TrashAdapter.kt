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
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.voicerecorder.R
import com.goodwy.voicerecorder.activities.SimpleActivity
import com.goodwy.voicerecorder.databinding.ItemRecordingBinding
import com.goodwy.voicerecorder.databinding.ItemRecordingSwipeBinding
import com.goodwy.voicerecorder.extensions.config
import com.goodwy.voicerecorder.extensions.deleteRecordings
import com.goodwy.voicerecorder.extensions.restoreRecordings
import com.goodwy.voicerecorder.helpers.*
import com.goodwy.voicerecorder.interfaces.RefreshRecordingsListener
import com.goodwy.voicerecorder.models.Events
import com.goodwy.voicerecorder.models.Recording
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import org.greenrobot.eventbus.EventBus

class TrashAdapter(
    activity: SimpleActivity,
    var recordings: ArrayList<Recording>,
    private val refreshListener: RefreshRecordingsListener,
    recyclerView: MyRecyclerView
) :
    MyRecyclerViewAdapter(activity, recyclerView, {}), RecyclerViewFastScroller.OnPopupTextUpdate {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_trash

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_restore -> restoreRecordings()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = recordings.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recordings.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recordings.indexOfFirst { it.id == key }

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

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: ArrayList<Recording>) {
        if (newItems.hashCode() != recordings.hashCode()) {
            recordings = newItems
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun restoreRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val recordingsToRestore = recordings
            .filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
        val positions = getSelectedItemPositions()

        activity.restoreRecordings(recordingsToRestore) { success ->
            if (success) {
                doDeleteAnimation(recordingsToRestore, positions)
                EventBus.getDefault().post(Events.RecordingTrashUpdated())
            }
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_recordings, itemsCnt, itemsCnt)
        }

        val baseString = R.string.delete_recordings_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteMediaStoreRecordings()
            }
        }
    }

    private fun deleteMediaStoreRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val recordingsToRemove = recordings
            .filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
        val positions = getSelectedItemPositions()

        activity.deleteRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(recordingsToRemove, positions)
            }
        }
    }

    private fun doDeleteAnimation(
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
            }
        }
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
                val swipeLeftAction = if (isRTL) SWIPE_ACTION_RESTORE else SWIPE_ACTION_DELETE
                swipeLeftIcon.setImageResource(swipeActionImageResource(swipeLeftAction))
                swipeLeftIcon.setColorFilter(properPrimaryColor.getContrastColor())
                swipeLeftIconHolder.setBackgroundColor(swipeActionColor(swipeLeftAction))

                val swipeRightAction = if (isRTL) SWIPE_ACTION_DELETE else SWIPE_ACTION_RESTORE
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
            menu.findItem(R.id.cab_restore).title =
                resources.getString(com.goodwy.commons.R.string.restore_this_file)
            setOnMenuItemClickListener { item ->
                val recordingId = recording.id
                when (item.itemId) {
                    R.id.cab_restore -> {
                        executeItemMenuOperation(recordingId, removeAfterCallback = false) {
                            restoreRecordings()
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
        @Suppress("SameParameterValue") removeAfterCallback: Boolean = false,
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
            else -> R.drawable.ic_restore_from_trash
        }
    }

    private fun swipeActionColor(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(com.goodwy.commons.R.color.red_missed, activity.theme)
            else -> resources.getColor(R.color.green_call, activity.theme)
        }
    }

    private fun swipeAction(swipeAction: Int, recording: Recording) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(recording)
            else -> swipedRestoreRecordings(recording)
        }
    }

    private fun swipedRestoreRecordings(recording: Recording) {
        val recordingsToRestore = arrayListOf(recording)
        val position = getItemKeyPosition(recording.id)
        val positions = arrayListOf(position)

        activity.restoreRecordings(recordingsToRestore) { success ->
            if (success) {
                doDeleteAnimation(recordingsToRestore, positions)
                EventBus.getDefault().post(Events.RecordingTrashUpdated())
            }
        }
    }

    private fun swipedDelete(recording: Recording) {
        finishActMode()
        if (activity.config.skipDeleteConfirmation) {
            deleteMediaStoreRecordingsSwipe(recording)
        } else askConfirmDeleteSwipe(recording)
    }

    private fun askConfirmDeleteSwipe(recording: Recording) {
        val items = "\"${recording.title}\""

        val baseString = R.string.delete_recordings_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteMediaStoreRecordingsSwipe(recording)
            }
        }
    }

    private fun deleteMediaStoreRecordingsSwipe(recording: Recording) {
        val recordingsToRemove = arrayListOf(recording)
        val position = getItemKeyPosition(recording.id)
        val positions = arrayListOf(position)

        activity.deleteRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(recordingsToRemove, positions)
            }
        }
    }
}

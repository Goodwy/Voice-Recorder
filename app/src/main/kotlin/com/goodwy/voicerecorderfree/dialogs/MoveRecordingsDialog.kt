package com.goodwy.voicerecorderfree.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.helpers.MEDIUM_ALPHA
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.voicerecorderfree.R
import com.goodwy.voicerecorderfree.databinding.DialogMoveRecordingsBinding
import com.goodwy.voicerecorderfree.extensions.getAllRecordings
import com.goodwy.voicerecorderfree.extensions.moveRecordings

class MoveRecordingsDialog(
    private val activity: BaseSimpleActivity,
    private val previousFolder: String,
    private val newFolder: String,
    private val callback: () -> Unit
) {
    private lateinit var dialog: AlertDialog
    private val binding = DialogMoveRecordingsBinding.inflate(activity.layoutInflater).apply {
        message.setText(R.string.move_recordings_to_new_folder_desc)
        progressIndicator.setIndicatorColor(activity.getProperPrimaryColor())
    }

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.yes, null)
            .setNegativeButton(com.goodwy.commons.R.string.no, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = R.string.move_recordings
                ) {
                    dialog = it
                    dialog.setOnDismissListener { callback() }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        callback()
                        dialog.dismiss()
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        binding.progressIndicator.show()
                        with(dialog) {
                            setCancelable(false)
                            setCanceledOnTouchOutside(false)
                            arrayOf(
                                binding.message,
                                getButton(AlertDialog.BUTTON_POSITIVE),
                                getButton(AlertDialog.BUTTON_NEGATIVE)
                            ).forEach { button ->
                                button.isEnabled = false
                                button.alpha = MEDIUM_ALPHA
                            }

                            moveAllRecordings()
                        }
                    }
                }
            }
    }

    private fun moveAllRecordings() {
        ensureBackgroundThread {
            activity.moveRecordings(
                recordingsToMove = activity.getAllRecordings(),
                sourceParent = previousFolder,
                destinationParent = newFolder
            ) {
                activity.runOnUiThread {
                    callback()
                    dialog.dismiss()
                }
            }
        }
    }
}

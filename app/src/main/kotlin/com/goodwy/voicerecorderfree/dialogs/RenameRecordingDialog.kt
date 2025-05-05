package com.goodwy.voicerecorderfree.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.helpers.isRPlus
import com.goodwy.voicerecorderfree.databinding.DialogRenameRecordingBinding
import com.goodwy.voicerecorderfree.extensions.config
import com.goodwy.voicerecorderfree.models.Events
import com.goodwy.voicerecorderfree.models.Recording
import org.greenrobot.eventbus.EventBus
import java.io.File

class RenameRecordingDialog(
    val activity: BaseSimpleActivity,
    val recording: Recording,
    val callback: () -> Unit
) {
    init {
        val binding = DialogRenameRecordingBinding.inflate(activity.layoutInflater).apply {
            renameRecordingTitle.setText(recording.title.substringBeforeLast('.'))
        }
        val view = binding.root

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = view,
                    dialog = this,
                    titleId = com.goodwy.commons.R.string.rename
                ) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameRecordingTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameRecordingTitle.value
                        if (newTitle.isEmpty()) {
                            activity.toast(com.goodwy.commons.R.string.empty_name)
                            return@setOnClickListener
                        }

                        if (!newTitle.isAValidFilename()) {
                            activity.toast(com.goodwy.commons.R.string.invalid_name)
                            return@setOnClickListener
                        }

                        ensureBackgroundThread {
                            if (isRPlus()) {
                                renameRecording(recording, newTitle)
                            } else {
                                renameRecordingLegacy(recording, newTitle)
                            }

                            activity.runOnUiThread {
                                callback()
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun renameRecording(recording: Recording, newTitle: String) {
        val oldExtension = recording.title.getFilenameExtension()
        val newDisplayName = "${newTitle.removeSuffix(".$oldExtension")}.$oldExtension"

        try {
            val path = "${activity.config.saveRecordingsFolder}/${recording.title}"
            val newPath = "${path.getParentPath()}/$newDisplayName"
            activity.handleSAFDialogSdk30(path) {
                val success = activity.renameDocumentSdk30(path, newPath)
                if (success) {
                    EventBus.getDefault().post(Events.RecordingCompleted())
                }
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun renameRecordingLegacy(recording: Recording, newTitle: String) {
        val oldExtension = recording.title.getFilenameExtension()
        val oldPath = recording.path
        val newFilename = "${newTitle.removeSuffix(".$oldExtension")}.$oldExtension"
        val newPath = File(oldPath.getParentPath(), newFilename).absolutePath
        activity.renameFile(oldPath, newPath, false)
    }
}

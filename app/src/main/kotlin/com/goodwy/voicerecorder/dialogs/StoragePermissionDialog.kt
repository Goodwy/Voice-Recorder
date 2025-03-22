package com.goodwy.voicerecorder.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.databinding.DialogMessageBinding
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.voicerecorder.R

class StoragePermissionDialog(
    private val activity: BaseSimpleActivity,
    private val callback: (result: Boolean) -> Unit
) {
    private var dialog: AlertDialog? = null

    init {
        val view = DialogMessageBinding.inflate(activity.layoutInflater, null, false)
        view.message.text = activity.getString(R.string.confirm_recording_folder)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { _, _ ->
                callback(true)
            }
            .apply {
                activity.setupDialogStuff(
                    view = view.root,
                    dialog = this,
                    cancelOnTouchOutside = false,
                ) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}

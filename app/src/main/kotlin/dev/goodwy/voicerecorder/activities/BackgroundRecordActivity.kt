package dev.goodwy.voicerecorder.activities

import android.content.Intent
import android.os.Bundle
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.extensions.openNotificationSettings
import dev.goodwy.voicerecorder.extensions.config
import dev.goodwy.voicerecorder.helpers.MyTileService
import dev.goodwy.voicerecorder.services.RecorderService

class BackgroundRecordActivity : SimpleActivity() {
    companion object {
        const val RECORD_INTENT_ACTION = "RECORD_ACTION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDimAmount(0f)
        handleIntent()
    }

    private fun handleIntent() {
        if (intent.action == RECORD_INTENT_ACTION) {
            val shouldStart = intent.getBooleanExtra("should_start", true)

            handleNotificationPermission { granted ->
                if (granted) {
                    if (shouldStart) {
                        startRecording()
                    } else {
                        stopRecording()
                    }
                } else {
                    PermissionRequiredDialog(
                        activity = this,
                        textId = com.goodwy.commons.R.string.allow_notifications_voice_recorder,
                        positiveActionCallback = { openNotificationSettings() }
                    )
                }
            }
        } else {
            moveTaskToBack(true)
            finish()
        }
    }

    private fun startRecording() {
        val intent = Intent(this, RecorderService::class.java)
        try {
            startForegroundService(intent)
            config.isRunning = true
            updateTile()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        moveTaskToBack(true)
        finish()
    }

    private fun stopRecording() {
        val intent = Intent(this, RecorderService::class.java)
        intent.action = "CANCEL_RECORDING"
        try {
            stopService(intent)
            config.isRunning = false
            updateTile()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        moveTaskToBack(true)
        finish()
    }

    private fun updateTile() {
        try {
            val intent = Intent(this, MyTileService::class.java)
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

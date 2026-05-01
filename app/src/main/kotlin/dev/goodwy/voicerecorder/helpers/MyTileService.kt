package dev.goodwy.voicerecorder.helpers

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.goodwy.commons.helpers.isQPlus
import com.goodwy.commons.helpers.isUpsideDownCakePlus
import dev.goodwy.voicerecorder.R
import dev.goodwy.voicerecorder.activities.BackgroundRecordActivity
import dev.goodwy.voicerecorder.extensions.config

class MyTileService : TileService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        updateTileState()
        return START_NOT_STICKY
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, BackgroundRecordActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.action = BackgroundRecordActivity.RECORD_INTENT_ACTION
        intent.putExtra("should_start", !config.isRunning)


        if (isUpsideDownCakePlus()) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            try {
                startActivityAndCollapse(pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                @SuppressLint("StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val isRunning = config.isRunning
        qsTile?.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (isQPlus()) {
            qsTile?.subtitle = if (isRunning) {
                getString(R.string.recording)
            } else {
                getString(com.goodwy.strings.R.string.off)
            }
        }
        qsTile?.updateTile()
    }
}
package com.goodwy.voicerecorderfree.helpers

import android.annotation.SuppressLint
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.goodwy.voicerecorderfree.activities.BackgroundRecordActivity
import com.goodwy.voicerecorderfree.services.RecorderService

class MyTileService : TileService() {

    override fun onClick() {
     //  MyCameraImpl.newInstance(this).toggleFlashlight()
        super.onClick()
        if (isLocked) {
            unlockAndRun { this.toggle() }
        } else {
            toggle()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun toggle() {
        val intent = Intent(this, BackgroundRecordActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.action = BackgroundRecordActivity.RECORD_INTENT_ACTION
        startActivityAndCollapse(intent)
    }

    override fun onTileRemoved() {
     //   if (MyCameraImpl.isFlashlightOn)
     //       MyCameraImpl.newInstance(this).toggleFlashlight()
    }

    override fun onStartListening() {
        updateTile()
    }

    override fun onTileAdded() {
        updateTile()
    }

    private fun updateTile() {
        qsTile?.state = if (RecorderService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}

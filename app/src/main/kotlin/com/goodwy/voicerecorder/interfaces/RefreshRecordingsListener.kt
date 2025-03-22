package com.goodwy.voicerecorder.interfaces

import com.goodwy.voicerecorder.models.Recording

interface RefreshRecordingsListener {
    fun refreshRecordings()

    fun playRecording(recording: Recording, playOnPrepared: Boolean)
}

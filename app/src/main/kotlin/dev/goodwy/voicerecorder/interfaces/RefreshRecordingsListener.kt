package dev.goodwy.voicerecorder.interfaces

import dev.goodwy.voicerecorder.models.Recording

interface RefreshRecordingsListener {
    fun refreshRecordings()

    fun playRecording(recording: Recording, playOnPrepared: Boolean)
}

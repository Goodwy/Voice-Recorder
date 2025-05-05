package com.goodwy.voicerecorderfree.interfaces

import com.goodwy.voicerecorderfree.models.Recording

interface RefreshRecordingsListener {
    fun refreshRecordings()

    fun playRecording(recording: Recording, playOnPrepared: Boolean)
}

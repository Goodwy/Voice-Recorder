package com.goodwy.voicerecorder.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import com.goodwy.voicerecorder.extensions.config
import com.goodwy.voicerecorder.helpers.SAMPLE_RATE

class MediaRecorderWrapper(val context: Context) : Recorder {

    @Suppress("DEPRECATION")
    private var recorder = MediaRecorder().apply {
        setAudioSource(context.config.audioSource)
        setOutputFormat(context.config.getOutputFormat())
        setAudioEncoder(context.config.getAudioEncoder())
        setAudioEncodingBitRate(context.config.bitrate)
        setAudioSamplingRate(SAMPLE_RATE)
    }

    override fun setOutputFile(path: String) {
        recorder.setOutputFile(path)
    }

    override fun setOutputFile(parcelFileDescriptor: ParcelFileDescriptor) {
        val pFD = ParcelFileDescriptor.dup(parcelFileDescriptor.fileDescriptor)
        recorder.setOutputFile(pFD.fileDescriptor)
    }

    override fun prepare() {
        recorder.prepare()
    }

    override fun start() {
        recorder.start()
    }

    override fun stop() {
        recorder.stop()
    }

    @SuppressLint("NewApi")
    override fun pause() {
        recorder.pause()
    }

    @SuppressLint("NewApi")
    override fun resume() {
        recorder.resume()
    }

    override fun release() {
        recorder.release()
    }

    override fun getMaxAmplitude(): Int {
        return recorder.maxAmplitude
    }
}

package dev.goodwy.voicerecorder.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.helpers.isRPlus
import dev.goodwy.voicerecorder.BuildConfig
import dev.goodwy.voicerecorder.R
import dev.goodwy.voicerecorder.activities.SplashActivity
import dev.goodwy.voicerecorder.extensions.config
import dev.goodwy.voicerecorder.extensions.getFormattedFilename
import dev.goodwy.voicerecorder.extensions.updateWidgets
import dev.goodwy.voicerecorder.helpers.*
import dev.goodwy.voicerecorder.models.Events
import dev.goodwy.voicerecorder.recorder.MediaRecorderWrapper
import dev.goodwy.voicerecorder.recorder.Mp3Recorder
import dev.goodwy.voicerecorder.recorder.Recorder
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.Timer
import java.util.TimerTask

class RecorderService : Service() {
    companion object {
        var isRunning = false
            set(value) {
                field = value
                // Update the config when changes are made
                updateConfigState(value)
            }

        private fun updateConfigState(isRunning: Boolean) {
            // We need some context, so we call it from the method
        }

        private const val AMPLITUDE_UPDATE_MS = 75L
    }

    private var recordingPath = ""
    private var resultUri: Uri? = null

    private var duration = 0
    private var status = RECORDING_STOPPED
    private var durationTimer = Timer()
    private var amplitudeTimer = Timer()
    private var recorder: Recorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent.action) {
            GET_RECORDER_INFO -> broadcastRecorderInfo()
            STOP_AMPLITUDE_UPDATE -> amplitudeTimer.cancel()
            TOGGLE_PAUSE -> togglePause()
            CANCEL_RECORDING -> cancelRecording()
            else -> startRecording()
        }

        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        // Synchronize the initial state
        if (config.isRunning) {
            RecorderService.isRunning = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        isRunning = false
        updateWidgets(false)
    }

    // mp4 output format with aac encoding should produce good enough m4a files according to https://stackoverflow.com/a/33054794/1967672
    @SuppressLint("DiscouragedApi")
    private fun startRecording() {
        isRunning = true
        updateWidgets(true)
        if (status == RECORDING_RUNNING) {
            return
        }

        val defaultFolder = File(config.saveRecordingsFolder)
        if (!defaultFolder.exists()) {
            defaultFolder.mkdir()
        }

        val recordingFolder = defaultFolder.absolutePath
        recordingPath = "$recordingFolder/${getFormattedFilename()}.${config.getExtension()}"
        resultUri = null

        try {
            recorder = if (recordMp3()) {
                Mp3Recorder(this)
            } else {
                MediaRecorderWrapper(this)
            }

            if (isRPlus()) {
                val fileUri = createDocumentUriUsingFirstParentTreeUri(recordingPath)
                createSAFFileSdk30(recordingPath)
                resultUri = fileUri
                contentResolver.openFileDescriptor(fileUri, "w")!!
                    .use { recorder?.setOutputFile(it) }
            } else if (isPathOnSD(recordingPath)) {
                var document = getDocumentFile(recordingPath.getParentPath())
                document = document?.createFile("", recordingPath.getFilenameFromPath())
                check(document != null) { "Failed to create document on SD Card" }
                resultUri = document.uri
                contentResolver.openFileDescriptor(document.uri, "w")!!
                    .use { recorder?.setOutputFile(it) }
            } else {
                recorder?.setOutputFile(recordingPath)
                resultUri = FileProvider.getUriForFile(
                    this, "${BuildConfig.APPLICATION_ID}.provider", File(recordingPath)
                )
            }

            recorder?.prepare()
            recorder?.start()
            duration = 0
            status = RECORDING_RUNNING
            broadcastRecorderInfo()
            startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())

            durationTimer = Timer()
            durationTimer.scheduleAtFixedRate(getDurationUpdateTask(), 1000, 1000)

            startAmplitudeUpdates()
            updateRecordingState(true)
        } catch (e: Exception) {
            showErrorToast(e)
            stopRecording()
        }
    }

    private fun stopRecording() {
        durationTimer.cancel()
        amplitudeTimer.cancel()
        status = RECORDING_STOPPED

        recorder?.apply {
            try {
                stop()
                release()
            } catch (
                @Suppress(
                    "TooGenericExceptionCaught",
                    "SwallowedException"
                ) _: RuntimeException
            ) {
                toast(R.string.recording_too_short)
            } catch (e: Exception) {
                showErrorToast(e)
                e.printStackTrace()
            }

            ensureBackgroundThread {
                scanRecording()
                EventBus.getDefault().post(Events.RecordingCompleted())
            }
        }
        recorder = null
        updateRecordingState(false)
    }

    private fun cancelRecording() {
        durationTimer.cancel()
        amplitudeTimer.cancel()
        status = RECORDING_STOPPED

        recorder?.apply {
            try {
                stop()
                release()
            } catch (_: Exception) {
            }
        }

        recorder = null
        if (isRPlus()) {
            val recordingUri = createDocumentUriUsingFirstParentTreeUri(recordingPath)
            DocumentsContract.deleteDocument(contentResolver, recordingUri)
        } else {
            File(recordingPath).delete()
        }

        EventBus.getDefault().post(Events.RecordingCompleted())
        stopSelf()
        updateRecordingState(false)
    }

    private fun broadcastRecorderInfo() {
        broadcastDuration()
        broadcastStatus()
        startAmplitudeUpdates()
    }

    @SuppressLint("DiscouragedApi")
    private fun startAmplitudeUpdates() {
        amplitudeTimer.cancel()
        amplitudeTimer = Timer()
        amplitudeTimer.scheduleAtFixedRate(getAmplitudeUpdateTask(), 0, AMPLITUDE_UPDATE_MS)
    }

    @SuppressLint("NewApi")
    private fun togglePause() {
        try {
            if (status == RECORDING_RUNNING) {
                recorder?.pause()
                status = RECORDING_PAUSED
            } else if (status == RECORDING_PAUSED) {
                recorder?.resume()
                status = RECORDING_RUNNING
            }
            broadcastStatus()
            startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun scanRecording() {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(recordingPath),
            arrayOf(recordingPath.getMimeType())
        ) { _, uri ->
            if (uri == null) {
                toast(com.goodwy.commons.R.string.unknown_error_occurred)
                return@scanFile
            }

            recordingSavedSuccessfully(resultUri ?: uri)
        }
    }

    private fun recordingSavedSuccessfully(savedUri: Uri) {
        toast(R.string.recording_saved_successfully)
        EventBus.getDefault().post(Events.RecordingSaved(savedUri))
    }

    private fun getDurationUpdateTask() = object : TimerTask() {
        override fun run() {
            if (status == RECORDING_RUNNING) {
                duration++
                broadcastDuration()
            }
        }
    }

    private fun getAmplitudeUpdateTask() = object : TimerTask() {
        override fun run() {
            if (recorder != null) {
                try {
                    EventBus.getDefault()
                        .post(Events.RecordingAmplitude(recorder!!.getMaxAmplitude()))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun showNotification(): Notification {
        val channelId = "simple_recorder"
        val title = getString(R.string.app_name_g)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }

        val icon = R.drawable.ic_recorder
        val visibility = NotificationCompat.VISIBILITY_PUBLIC
        var text = getString(R.string.recording)
        if (status == RECORDING_PAUSED) {
            text += " (${getString(R.string.paused)})"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(getOpenAppIntent())
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setVisibility(visibility)
            .setSound(null)
            .setOngoing(true)
            .setAutoCancel(true)

        return builder.build()
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
        return PendingIntent.getActivity(
            this,
            RECORDER_RUNNING_NOTIF_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastDuration() {
        EventBus.getDefault().post(Events.RecordingDuration(duration))
    }

    private fun broadcastStatus() {
        EventBus.getDefault().post(Events.RecordingStatus(status))
    }

    private fun recordMp3(): Boolean {
        return config.extension == EXTENSION_MP3
    }

    private fun updateRecordingState(isRunning: Boolean) {
        config.isRunning = isRunning
        RecorderService.isRunning = isRunning
        updateTile()
    }

    private fun updateTile() {
        try {
            val intent = Intent(this, MyTileService::class.java)
            startService(intent)
        } catch (_: Exception) {
        }
    }
}

package com.goodwy.voicerecorder.helpers

const val REPOSITORY_NAME = "Voice-Recorder"

const val RECORDER_RUNNING_NOTIF_ID = 10000

private const val PATH = "com.goodwy.voicerecorder.action."
const val GET_RECORDER_INFO = PATH + "GET_RECORDER_INFO"
const val STOP_AMPLITUDE_UPDATE = PATH + "STOP_AMPLITUDE_UPDATE"
const val TOGGLE_PAUSE = PATH + "TOGGLE_PAUSE"
const val CANCEL_RECORDING = PATH + "CANCEL_RECORDING"

const val EXTENSION_M4A = 0
const val EXTENSION_MP3 = 1
const val EXTENSION_OGG = 2

val BITRATES = arrayListOf(32000, 64000, 96000, 128000, 160000, 192000, 256000, 320000)
const val DEFAULT_BITRATE = 192000
const val SAMPLE_RATE = 48000

const val VIEVPAGE_LAST = 0
const val VIEVPAGE_RECORDER = 1
const val VIEVPAGE_PLAYER = 2

const val IS_RECORDING = "is_recording"
const val TOGGLE_WIDGET_UI = "toggle_widget_ui"

// shared preferences
const val HIDE_NOTIFICATION = "hide_notification"
const val SAVE_RECORDINGS = "save_recordings"
const val EXTENSION = "extension"
const val AUDIO_SOURCE = "audio_source"
const val BITRATE = "bitrate"
const val RECORD_AFTER_LAUNCH = "record_after_launch"
const val USE_RECYCLE_BIN = "use_recycle_bin"
const val LAST_RECYCLE_BIN_CHECK = "last_recycle_bin_check"
const val KEEP_SCREEN_ON = "keep_screen_on"

const val DEFAULT_RECORDINGS_FOLDER = "Recordings"

//Goodwy
const val UPDATE_RECYCLE_BIN = "update_recycle_bin"
const val SHOW_WIDGET_NAME = "show_widget_name"

const val VIEVPAGE = "view_page"
const val ROUND_ICON = "round_icon"

const val RECORDING_RUNNING = 0
const val RECORDING_STOPPED = 1
const val RECORDING_PAUSED = 2

// swiped left action
const val SWIPE_RIGHT_ACTION = "swipe_right_action"
const val SWIPE_LEFT_ACTION = "swipe_left_action"
const val SWIPE_ACTION_DELETE = 2
const val SWIPE_ACTION_EDIT = 7
const val SWIPE_ACTION_SHARE = 8
const val SWIPE_ACTION_OPEN = 9
const val SWIPE_ACTION_RESTORE = 10
const val SWIPE_VIBRATION = "swipe_vibration"
const val SWIPE_RIPPLE = "swipe_ripple"

package com.goodwy.voicerecorderfree.extensions

fun String?.isAudioMimeType(): Boolean {
    return this?.startsWith("audio") == true
}

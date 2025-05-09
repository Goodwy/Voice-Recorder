package com.goodwy.voicerecorderfree.models

data class Recording(
    val id: Int,
    val title: String,
    val path: String,
    val timestamp: Long,
    val duration: Int,
    val size: Int
)

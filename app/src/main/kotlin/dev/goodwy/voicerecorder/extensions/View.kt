package dev.goodwy.voicerecorder.extensions

import android.graphics.Rect
import android.view.View


fun View.setWidth(size: Int) {
    val lp = layoutParams
    lp.width = size
    layoutParams = lp
}

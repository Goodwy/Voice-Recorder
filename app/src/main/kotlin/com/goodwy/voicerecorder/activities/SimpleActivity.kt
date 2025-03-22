package com.goodwy.voicerecorder.activities

import android.graphics.drawable.Drawable
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getColoredDrawableWithColor
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.voicerecorder.R
import com.goodwy.voicerecorder.helpers.REPOSITORY_NAME

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_one,
        R.mipmap.ic_launcher_two,
        R.mipmap.ic_launcher_three,
        R.mipmap.ic_launcher_four,
        R.mipmap.ic_launcher_five,
        R.mipmap.ic_launcher_six,
        R.mipmap.ic_launcher_seven,
        R.mipmap.ic_launcher_eight,
        R.mipmap.ic_launcher_nine,
        R.mipmap.ic_launcher_ten,
        R.mipmap.ic_launcher_eleven
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name_g)

    override fun getRepositoryName() = REPOSITORY_NAME

    protected fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_microphone_scaled
            1 -> R.drawable.ic_headset_vector
            else -> R.drawable.ic_delete_scaled
        }
        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    protected fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> com.goodwy.strings.R.string.recorder_g
            1 -> com.goodwy.strings.R.string.player_g
            else -> com.goodwy.commons.R.string.recycle_bin
        }
        return resources.getString(stringId)
    }
}

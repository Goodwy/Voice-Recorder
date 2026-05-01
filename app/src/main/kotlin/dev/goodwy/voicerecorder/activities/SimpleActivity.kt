package dev.goodwy.voicerecorder.activities

import android.graphics.drawable.Drawable
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getColoredDrawableWithColor
import com.goodwy.commons.extensions.getProperTextColor
import dev.goodwy.voicerecorder.R
import dev.goodwy.voicerecorder.helpers.REPOSITORY_NAME

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
}

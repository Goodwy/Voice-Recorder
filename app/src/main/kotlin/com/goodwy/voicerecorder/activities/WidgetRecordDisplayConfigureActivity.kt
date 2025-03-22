package com.goodwy.voicerecorder.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.SeekBar
import com.goodwy.commons.dialogs.ColorPickerDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.IS_CUSTOMIZING_COLORS
import com.goodwy.voicerecorder.R
import com.goodwy.voicerecorder.databinding.WidgetRecordDisplayConfigBinding
import com.goodwy.voicerecorder.extensions.config
import com.goodwy.voicerecorder.helpers.MyWidgetRecordDisplayProvider

class WidgetRecordDisplayConfigureActivity : SimpleActivity() {
    private var mWidgetAlpha = 0f
    private var mWidgetId = 0
    private var mWidgetColor = 0
    private var mWidgetColorWithoutTransparency = 0
    private var mLabelColor = 0
    private lateinit var binding: WidgetRecordDisplayConfigBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        binding = WidgetRecordDisplayConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initVariables()

        updateTextColors(binding.configHolder)
        binding.configHolder.background.applyColorFilter(getProperBackgroundColor())

        val isCustomizingColors = intent.extras?.getBoolean(IS_CUSTOMIZING_COLORS) ?: false
        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isCustomizingColors) {
            finish()
        }

        binding.configSave.setOnClickListener { saveConfig() }
        binding.configWidgetColorHolder.setOnClickListener { pickBackgroundColor() }

        val primaryColor = getProperPrimaryColor()
        binding.configWidgetSeekbar.setColors(getProperTextColor(), primaryColor, primaryColor)

        binding.configWidgetName.isChecked = config.showWidgetName
        handleWidgetNameDisplay()
        binding.configWidgetNameTextColorHolder.setOnClickListener { pickLabelColor() }
        binding.configWidgetNameHolder.setOnClickListener {
            binding.configWidgetName.toggle()
            handleWidgetNameDisplay()
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.setBackgroundColor(0)
    }

    private fun initVariables() {
        mWidgetColor = config.widgetBgColor
//        @Suppress("DEPRECATION")
//        if (mWidgetColor == resources.getColor(R.color.default_widget_bg_color) && isDynamicTheme()) {
//            mWidgetColor = resources.getColor(com.goodwy.commons.R.color.you_primary_color, theme)
//        }
        mLabelColor = config.widgetLabelColor

        mWidgetAlpha = Color.alpha(mWidgetColor) / 255.toFloat()

        mWidgetColorWithoutTransparency = Color.rgb(
            Color.red(mWidgetColor),
            Color.green(mWidgetColor),
            Color.blue(mWidgetColor)
        )

        binding.configWidgetSeekbar.setOnSeekBarChangeListener(seekbarChangeListener)
        binding.configWidgetSeekbar.progress = (mWidgetAlpha * 100).toInt()
        updateColors()
    }

    private fun saveConfig() {
        config.widgetBgColor = mWidgetColor
        config.widgetLabelColor = mLabelColor
        config.showWidgetName = binding.configWidgetName.isChecked
        requestWidgetUpdate()

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mWidgetColorWithoutTransparency,
            addDefaultColorButton = true,
            colorDefault = resources.getColor(R.color.default_widget_bg_color)
        ) { wasPositivePressed, color, wasDefaultPressed ->
            if (wasPositivePressed || wasDefaultPressed) {
                mWidgetColorWithoutTransparency = color
                updateColors()
            }
        }
    }

    private fun pickLabelColor() {
        ColorPickerDialog(this, mLabelColor,
            addDefaultColorButton = true,
            colorDefault = resources.getColor(com.goodwy.commons.R.color.default_widget_label_color)
        ) { wasPositivePressed, color, wasDefaultPressed ->
            if (wasPositivePressed || wasDefaultPressed) {
                mLabelColor = color
                updateColors()
                handleWidgetNameDisplay()
            }
        }
    }

    private fun requestWidgetUpdate() {
        Intent(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            null,
            this,
            MyWidgetRecordDisplayProvider::class.java
        ).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateColors() {
        binding.apply {
            mWidgetColor = mWidgetColorWithoutTransparency.adjustAlpha(mWidgetAlpha)
            configWidgetColor.setFillWithStroke(mWidgetColor, mWidgetColor)
            configImage.background.mutate().applyColorFilter(mWidgetColor)
            configWidgetNameTextColor.setFillWithStroke(mLabelColor, mLabelColor)
            widgetName.setTextColor(mLabelColor)

            val getProperPrimaryColor = getProperPrimaryColor()
            configSave.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor)
            configSave.setTextColor(getProperPrimaryColor.getContrastColor())
        }
    }

    private val seekbarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            mWidgetAlpha = progress.toFloat() / 100.toFloat()
            updateColors()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    private fun handleWidgetNameDisplay() {
        val showName = binding.configWidgetName.isChecked
        binding.widgetName.beVisibleIf(showName)
        binding.configWidgetNameTextColorHolder.beVisibleIf(showName)
    }
}

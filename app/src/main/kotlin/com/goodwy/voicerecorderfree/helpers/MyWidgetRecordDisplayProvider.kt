package com.goodwy.voicerecorderfree.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import com.goodwy.commons.extensions.getColoredDrawableWithColor
import com.goodwy.commons.extensions.setVisibleIf
import com.goodwy.voicerecorderfree.R
import com.goodwy.voicerecorderfree.activities.BackgroundRecordActivity
import com.goodwy.voicerecorderfree.extensions.config
import com.goodwy.voicerecorderfree.extensions.drawableToBitmap

class MyWidgetRecordDisplayProvider : AppWidgetProvider() {
    companion object {
        private const val OPEN_APP_INTENT_ID = 1
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        changeWidgetIcon(appWidgetManager, context, context.config.widgetBgColor, false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TOGGLE_WIDGET_UI && intent.extras?.containsKey(IS_RECORDING) == true) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
//            val color = if (intent.extras!!.getBoolean(IS_RECORDING)) {
//                context.config.widgetBgColor
//            } else {
//                Color.WHITE
//            }
            val color = context.config.widgetBgColor
            val isRecord = intent.extras!!.getBoolean(IS_RECORDING)

            changeWidgetIcon(appWidgetManager, context, color, isRecord)
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun changeWidgetIcon(appWidgetManager: AppWidgetManager, context: Context, color: Int, isRecord: Boolean) {
        val alpha = Color.alpha(context.config.widgetBgColor)
        val bmp = getColoredIcon(context, color, alpha, isRecord)

        appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
            RemoteViews(context.packageName, R.layout.widget_record_display).apply {
                setupAppOpenIntent(context, this, isRecord)
                setImageViewBitmap(R.id.record_display_btn, bmp)
                setTextColor(R.id.widget_name, context.config.widgetLabelColor)
                setVisibleIf(R.id.widget_name, context.config.showWidgetName)
                appWidgetManager.updateAppWidget(it, this)
            }
        }
    }

    private fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, MyWidgetRecordDisplayProvider::class.java)
    }

    private fun setupAppOpenIntent(context: Context, views: RemoteViews, isRecord: Boolean) {
        Intent(context, BackgroundRecordActivity::class.java).apply {
            action = BackgroundRecordActivity.RECORD_INTENT_ACTION
            val pendingIntent = PendingIntent.getActivity(
                context,
                OPEN_APP_INTENT_ID,
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.record_display_btn, pendingIntent)
            val padding = if (isRecord) context.resources.getDimensionPixelOffset(R.dimen.padding_record_icon) else 0
            views.setViewPadding(R.id.record_display_btn, padding, padding, padding, padding)
        }
    }

    private fun getColoredIcon(context: Context, color: Int, alpha: Int, isRecord: Boolean): Bitmap {
        val drawable = if (isRecord) {
            context.resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.squircle_bg, color, alpha)
        } else {
            context.resources.getColoredDrawableWithColor(R.drawable.oval_record, color, alpha)
        }
        return context.drawableToBitmap(drawable)
    }
}

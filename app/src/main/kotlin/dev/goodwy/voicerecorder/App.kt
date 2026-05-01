package dev.goodwy.voicerecorder

import com.goodwy.commons.RightApp
import com.goodwy.commons.helpers.PurchaseHelper

class App : RightApp() {

    override fun onCreate() {
        super.onCreate()
        PurchaseHelper().initPurchaseIfNeed(this, "2063553708")
    }
}

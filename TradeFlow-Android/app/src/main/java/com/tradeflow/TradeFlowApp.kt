package com.tradeflow

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Application class. Boots the Chaquopy CPython runtime exactly once for the
 * lifetime of the process so that subsequent JNI calls into Python modules can
 * be made from the dedicated single-thread executor without re-initialization.
 */
class TradeFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}

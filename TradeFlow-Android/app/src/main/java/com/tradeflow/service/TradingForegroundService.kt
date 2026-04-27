package com.tradeflow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tradeflow.MainActivity
import com.tradeflow.R
import com.tradeflow.agent.PythonBridge
import com.tradeflow.agent.ThoughtStream
import com.tradeflow.security.SecureCredentialStore

/**
 * The OS Shield. Anchors the Python sentry/brain/executor loop to a
 * high-priority foreground service that:
 *  - Declares foregroundServiceType=DATA_SYNC (required Android 14+).
 *  - Holds a PARTIAL_WAKE_LOCK so the CPU never parks while the screen is off.
 *  - Posts a sticky notification on a high-importance channel so aggressive
 *    OEM memory managers (ColorOS, OxygenOS, MIUI) don't reap the process.
 *  - Returns START_STICKY so an unexpected kill is rescheduled.
 */
class TradingForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForegroundCompat(buildNotification("Monitoring Volatility..."))

        val store = SecureCredentialStore(this)
        if (!store.hasAllKeys()) {
            ThoughtStream.onError(
                "Missing OKX (key/secret/passphrase) or DeepSeek key — open Settings to configure."
            )
            updateNotification("Idle — credentials missing")
            return START_STICKY
        }

        val dbPath = getDatabasePath("position_journal.sqlite").absolutePath
        PythonBridge.start(
            callback = ThoughtStream,
            okxApiKey = store.okxKey(),
            okxApiSecret = store.okxSecret(),
            okxApiPassphrase = store.okxPassphrase(),
            deepseekApiKey = store.deepseekKey(),
            dbPath = dbPath
        )

        ThoughtStream.state.observeForever { newState ->
            updateNotification("Sentry: $newState")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        try {
            PythonBridge.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping python bridge", t)
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startInForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TradeFlow:SentryWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        Log.i(TAG, "PARTIAL_WAKE_LOCK acquired")
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "WakeLock release failed", t)
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TradeFlow Sentry",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent volatility monitoring for the TradeFlow agent."
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TradeFlow")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "TradingFgService"
        private const val CHANNEL_ID = "tradeflow_sentry_channel"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            val intent = Intent(context, TradingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TradingForegroundService::class.java))
        }
    }
}

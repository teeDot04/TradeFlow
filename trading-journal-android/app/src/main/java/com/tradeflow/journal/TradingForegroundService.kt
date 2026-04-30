package com.tradeflow.journal

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tradeflow.journal.data.ThoughtManager

class TradingForegroundService : Service() {
    private val CHANNEL_ID = "trading_service_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification("TradeFlow Sovereign Agent Active")
        startForeground(1, notification)
        
        AgentCore.startAgent(this)
        AgentCore.heartbeatListener = {
            updateNotification("Heartbeat: ${System.currentTimeMillis() / 1000}")
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TradeFlow")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Trading Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        AgentCore.stopAgent()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

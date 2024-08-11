package com.qedron.gateway


import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

class GatewayService : Service(), GatewayServer.Handler {

    companion object {
        const val PREFERENCE_KEY = "qedron_gateway_api_key"
        const val DEFAULT_PORT = 8082
        private const val NOTIFICATION_ID = 8722227
        const val GATEWAY_CHANNEL_ID = "qedron_notifications_gateway"
    }

    private lateinit var gatewayServer: GatewayServer

    private fun createNotification(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GATEWAY_CHANNEL_ID,
                "qedron gateway channel",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        return NotificationCompat.Builder(context, GATEWAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle("SMS API service is running")
            .setContentText("Tap to open the app")
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onCreate() {
        val key = PreferenceManager.getDefaultSharedPreferences(this).getString(PREFERENCE_KEY, null)
        gatewayServer = GatewayServer(DEFAULT_PORT, key, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(this))
        }
        gatewayServer.start()
        return START_STICKY
    }

    override fun onDestroy() {
        gatewayServer.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSendMessage(phone: String, message: String, saveMessage: Boolean): String? {
        GatewayServiceUtil.sendMessage(
            this,
            GatewayServiceUtil.getSmsManager(this),
            phone,
            message
        )
        return null
    }

}
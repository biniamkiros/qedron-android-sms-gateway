package com.qedron.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.qedron.gateway.BroadcastViewModel.Companion.ABORTED
import com.qedron.gateway.BroadcastViewModel.Companion.FAILED
import com.qedron.gateway.BroadcastViewModel.Companion.CLEARED
import com.qedron.gateway.BroadcastViewModel.Companion.COMPLETED
import com.qedron.gateway.BroadcastViewModel.Companion.INITIATED
import com.qedron.gateway.BroadcastViewModel.Companion.KILLED
import com.qedron.gateway.BroadcastViewModel.Companion.ONGOING
import com.qedron.gateway.BroadcastViewModel.Companion.STARTED


class BroadcastService() : LifecycleService() {
    private val appViewModelStore: ViewModelStore
        get() = (application as App).viewModelStore

    private val viewModel: BroadcastViewModel by lazy {
        ViewModelProvider(
            appViewModelStore,
            ViewModelProvider.AndroidViewModelFactory(application)
        )[BroadcastViewModel::class.java]
    }
    companion object {
        private const val BROADCAST_NOTIFICATION_ID = 45327
        private const val BROADCAST_CHANNEL_ID = "qedron_notifications_broadcast"
        private const val BROADCAST_CHANNEL_NAME = "qedron broadcast channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(BROADCAST_NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(BROADCAST_NOTIFICATION_ID, createNotification())
        }

        viewModel.progress.observe(this) { progress ->
            updateNotification("sending message to $progress")
        }
        viewModel.status.observe(this) { status ->
            when(status){
                ONGOING -> updateNotification("Starting broadcast...")
                INITIATED,
                STARTED,
                KILLED,
                ABORTED,
                FAILED,
                CLEARED,
                COMPLETED -> stopService()
                else -> stopService()
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        getNotificationManger()
        return getNotification("starting broadcasting...").build()
    }

    private fun getNotificationManger(): NotificationManager {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BROADCAST_CHANNEL_ID,
                BROADCAST_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        return notificationManager

    }

    private fun updateNotification(progress: String) {
        getNotificationManger().notify(BROADCAST_NOTIFICATION_ID, getNotification(progress).build())
    }

    private fun getNotification(text:String): NotificationCompat.Builder {

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("key", "value") // Add any extras you need
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val clearIntent = Intent(this, AbortNotificationReceiver::class.java).apply {
            putExtra("action_msg", "some message for toast")
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags or PendingIntent.FLAG_UPDATE_CURRENT)
        val clearPendingIntent = PendingIntent.getBroadcast(this, 0, clearIntent, flags)

        return NotificationCompat.Builder(this, BROADCAST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_broadcast_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setSound(null)
            .addAction(
                R.drawable.ic_clear_all_24, "abort",
                clearPendingIntent
            )
    }

    private fun stopService(){
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        //viewModel.abortBroadcast()
        super.onDestroy()
    }

}

package com.qedron.gateway

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class GatewayMessagingService : FirebaseMessagingService() {
    companion object {
        private const val NOTIFICATION_ID = 7545600
        const val STATUS_CHANNEL_ID = "qedron_status_notifications_gateway"
    }
    private val handler = Handler(Looper.getMainLooper())
    // private val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val phone = remoteMessage.data["phone"]
        val message = remoteMessage.data["message"]
        if (phone != null && message != null) {
            try {
                val sms = this.getSystemService(SmsManager::class.java)//SmsManager.getDefault()
                val parts: ArrayList<String> = sms.divideMessage(message)
                sms.sendMultipartTextMessage(
                    phone,
                    null,
                    parts,
                    null,
                    null
                )
                notify(phone, true)
//                Toast.makeText(this, "Sending sms..", Toast.LENGTH_LONG).show()
//                Toast.makeText(this, "Sending sms..", Toast.LENGTH_LONG).show()
//                SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            } catch (e: Exception) {
                notify(phone, false)
                handler.post {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun notify(phone:String, success:Boolean){
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        val sharedPreference =  getSharedPreferences("SMS_COUNT",Context.MODE_PRIVATE)
        val count = sharedPreference.getInt(today,0) + 1
        val editor = sharedPreference.edit()
        editor.putInt(today,count)
        editor.apply()

        val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "qedron status channel",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
           notificationManager.createNotificationChannel(channel)
        }

        val timeoutMs = 1000L * 60 * 60 * 6

        val intent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        val text = if (success) "Sent messages to $phone" else "Failed to send message to $phone"
        val title = "Today's sent messages: $count"
        val builder = NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle(title)
            .setContentText(text)
            .setTimeoutAfter(timeoutMs)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

}
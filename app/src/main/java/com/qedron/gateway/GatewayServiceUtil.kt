package com.qedron.gateway

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Suppress("DEPRECATION")
object GatewayServiceUtil {
    private const val NOTIFICATION_ID = 7545600
    const val STATUS_CHANNEL_ID = "qedron_status_notifications_gateway"
    const val STATUS_CHANNEL_NAME = "qedron status channel"
    const val TODAY = "today"
    const val MESSAGE = "message"
    const val LAST = "last"
    const val COUNT = "count"
    const val SENT = "sent"
    const val DELIVERED = "delivered"
    const val FAILED = "failed"
    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (GatewayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun notifyStat(context: Context){
       val stat = getStat(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                STATUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            notificationManager.createNotificationChannel(channel)
        }

        val timeoutMs = 1000L * 60 * 60 * 6

        val intent = Intent(context, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val title = "Today's messages: ${if(stat.last.isNullOrEmpty()) "no messages" else stat.count}${if(stat.count > 0) ", sent: ${stat.sent}, delivered: ${stat.delivered}" else ""} ${if(stat.failed > 0) ", failed:${stat.failed}" else ""}"
        val detail = "Last event: ${if(stat.last.isNullOrEmpty()) "no event" else stat.last} \n\nLast message: ${if(stat.message.isNullOrEmpty()) "no message" else stat.message}"
        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle(title)
//            .setContentText(detail)
            .setTimeoutAfter(timeoutMs)
            .setShowWhen(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun getStat(context: Context): Stat {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        val sharedPreference =  context.getSharedPreferences("SMS_COUNT",Context.MODE_PRIVATE)
        val date = sharedPreference.getString(TODAY,"")
        val last = sharedPreference.getString(LAST,"")
        val message = sharedPreference.getString(MESSAGE,"")
        return if(!date.equals(today)) {
            setStat(context, today = today, last="", count = 0, sent = 0, delivered = 0, failed = 0)
            Stat(today,last,"",0,0,0,0)
        } else {
            val count = sharedPreference.getInt(COUNT, 0)
            val sent = sharedPreference.getInt(SENT, 0)
            val delivered = sharedPreference.getInt(DELIVERED, 0)
            val failed = sharedPreference.getInt(FAILED, 0)
            Stat(today, last, message, count, sent, delivered, failed)
        }
    }

    fun setStat(context: Context,
                today:String?=null,
                last:String?=null,
                message:String?=null,
                count:Int=0,
                sent:Int=-0,
                delivered:Int=0,
                failed:Int=0,
    ){
        val sharedPreference =  context.getSharedPreferences("SMS_COUNT",Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        if(!today.isNullOrEmpty()) editor.putString(TODAY,today)
        if(!last.isNullOrEmpty()) editor.putString(LAST,last)
        if(!message.isNullOrEmpty()) editor.putString(MESSAGE,message)
        if(count > 0) editor.putInt(COUNT,count)
        if(sent > 0) editor.putInt(SENT,sent)
        if(delivered > 0) editor.putInt(DELIVERED,delivered)
        if(failed > 0) editor.putInt(FAILED,failed)
        editor.apply()
    }

    fun increment(context: Context, name:String){
        val stat = getStat(context)
        when(name){
            COUNT-> setStat(context, count=stat.count + 1)
            SENT-> setStat(context, sent=stat.sent + 1)
            DELIVERED-> setStat(context, delivered=stat.delivered + 1)
            FAILED-> setStat(context, failed=stat.failed + 1)
        }
    }
}
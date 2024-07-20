package com.qedron.gateway

import android.app.ActivityManager
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Suppress("DEPRECATION")
object GatewayServiceUtil {
    private const val NOTIFICATION_ID = 7545600
    private val handler = Handler(Looper.getMainLooper())

    private const val STATUS_CHANNEL_ID = "qedron_status_notifications_gateway"
    private const val STATUS_CHANNEL_NAME = "qedron status channel"

    private const val SMS_COUNT = "SMS_COUNT"
    private const val TODAY = "today"
    private const val MESSAGE = "message"
    private const val LAST = "last"
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

    fun sendMessage(context: Context, phone:String, message: String): Boolean {
        try {
            val sentPendingIntents = ArrayList<PendingIntent>()
            val deliveredPendingIntents = ArrayList<PendingIntent>()

            val sentPI = PendingIntent.getBroadcast(context, 0,
                Intent(context, SmsSentReceiver::class.java), PendingIntent.FLAG_MUTABLE)
            val deliveredPI = PendingIntent.getBroadcast(context, 0,
                Intent(context, SmsDeliveredReceiver::class.java),  PendingIntent.FLAG_MUTABLE)

            val sms = context.getSystemService(SmsManager::class.java)//SmsManager.getDefault()
            val parts: ArrayList<String> = sms.divideMessage(message)
            parts.forEach { _ ->
                sentPendingIntents.add(sentPI)
                deliveredPendingIntents.add(deliveredPI)
            }

            sms.sendMultipartTextMessage(
                phone,
                null,
                parts,
                sentPendingIntents,
                deliveredPendingIntents
            )
            setStat(context,message=message)
            increment(context, COUNT)
            return true
        } catch (e: Exception) {
            setStat(context,last="${phone}: ${e.message}")
            notifyStat(context)
            handler.post {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            return false
        }
    }

    fun notifyStat(context: Context) {
        val stat = getStat(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0

        val intent = Intent(context, MainActivity::class.java)
        val clearIntent = Intent(context, ResetNotificationReceiver::class.java).apply {
            putExtra("action_msg", "some message for toast")
        }

        val clearPendingIntent = PendingIntent.getBroadcast(context, 0, clearIntent, flags)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val title =
            "Today's messages: ${if (stat.last.isNullOrEmpty()) "No messages" else stat.count}${if (stat.count > 0) ", sent: ${stat.sent}, delivered: ${stat.delivered}" else ""} ${if (stat.failed > 0) ", failed:${stat.failed}" else ""}"
        val detail =
            "${if (!stat.last.isNullOrEmpty()) "Last error: ${stat.last}" else ""} ${if (stat.message.isNullOrEmpty()) "no messages sent" else "\n\nLast message: ${ stat.message}"}"

        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle(title)
            .setTimeoutAfter(timeoutMs)
            .setShowWhen(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_clear_all_24, "clear",
                clearPendingIntent)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun close(context:Context){
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                STATUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun clear(context:Context){
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        setStat(context, today = today, last="", message="", count = 0, sent = 0, delivered = 0, failed = 0)
    }

    private fun getStat(context: Context): Stat {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        val sharedPreference =  context.getSharedPreferences(SMS_COUNT,Context.MODE_PRIVATE)
        val date = sharedPreference.getString(TODAY,"")
        val last = sharedPreference.getString(LAST,"")
        val message = sharedPreference.getString(MESSAGE,"")
        return if(!date.equals(today)) {
            clear(context)
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
                count:Int=-1,
                sent:Int=-1,
                delivered:Int=-1,
                failed:Int=-1,
    ){
        val sharedPreference =  context.getSharedPreferences(SMS_COUNT,Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        if(today !== null) editor.putString(TODAY,today)
        if(last !== null) editor.putString(LAST,last)
        if(message !== null) editor.putString(MESSAGE,message)
        if(count > -1) editor.putInt(COUNT,count)
        if(sent > -1) editor.putInt(SENT,sent)
        if(delivered > -1) editor.putInt(DELIVERED,delivered)
        if(failed > -1) editor.putInt(FAILED,failed)
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

//    suspend fun savePhoneNumberList(context: Context, phones:Array<String>){
//        // Initialize the database
//        val contactDatabase = ContactDatabase.getDatabase(context)
//        // Insert a new note
//        val newNote = Contact(name = "", phoneNumber = "913201724")
//        contactDatabase.contactDao().insert(newNote)
//    }
//
//    fun appendPhoneNumberList(context: Context, phones:Array<String>){
//        val savedPhones = getPhoneNumberList(context)
////        savedPhones = phones
//        savePhoneNumberList(context, savedPhones)
//    }
//
//    fun getPhoneNumberList(context: Context): Array<String> {
//        val sharedPreference =  context.getSharedPreferences(PHONE_LIST,Context.MODE_PRIVATE)
//        val date = sharedPreference.getString(TODAY,"")
//        return arrayOf("")
//    }
}
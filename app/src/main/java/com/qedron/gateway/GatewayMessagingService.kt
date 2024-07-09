package com.qedron.gateway

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class GatewayMessagingService : FirebaseMessagingService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val phone = remoteMessage.data["phone"]
        val message = remoteMessage.data["message"]
        if (phone != null && message != null) {

            try {
                val sentPendingIntents = ArrayList<PendingIntent>()
                val deliveredPendingIntents = ArrayList<PendingIntent>()

                val sentPI = PendingIntent.getBroadcast(this, 0,
                    Intent(this, SmsSentReceiver::class.java), PendingIntent.FLAG_MUTABLE)
                val deliveredPI = PendingIntent.getBroadcast(this, 0,
                    Intent(this, SmsDeliveredReceiver::class.java),  PendingIntent.FLAG_MUTABLE)

                val sms = this.getSystemService(SmsManager::class.java)//SmsManager.getDefault()
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
                GatewayServiceUtil.setStat(this@GatewayMessagingService,message=message)
            } catch (e: Exception) {
                GatewayServiceUtil.setStat(this@GatewayMessagingService,last="${phone}: ${e.message}")
                GatewayServiceUtil.notifyStat(this@GatewayMessagingService)
                handler.post {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
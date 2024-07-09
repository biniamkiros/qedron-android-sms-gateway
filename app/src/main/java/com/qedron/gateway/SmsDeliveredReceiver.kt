package com.qedron.gateway

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SmsDeliveredReceiver : BroadcastReceiver() {
    val TAG = "TRACK_SMS_STATUS"
    override fun onReceive(context: Context, intent: Intent) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "SMS delivered")
                GatewayServiceUtil.increment(
                    context,
                    GatewayServiceUtil.DELIVERED
                )
                GatewayServiceUtil.setStat(
                        context,
                last = "success - sms delivered"
                )
                GatewayServiceUtil.notifyStat(context)
            }

            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "SMS not delivered")
                GatewayServiceUtil.setStat(
                    context,
                    last = "error - sms not delivered"
                )
                GatewayServiceUtil.notifyStat(context)
            }
        }
    }
}
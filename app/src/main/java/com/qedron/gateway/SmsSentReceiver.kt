package com.qedron.gateway

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsSentReceiver : BroadcastReceiver() {
    val TAG = "TRACK_SMS_STATUS"
    override fun onReceive(context: Context, intent: Intent) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(TAG, "SMS sent")
                GatewayServiceUtil.increment(context, GatewayServiceUtil.COUNT)
                GatewayServiceUtil.increment(context, GatewayServiceUtil.SENT)
                GatewayServiceUtil.setStat(context,last="success - sms sent")
                GatewayServiceUtil.notifyStat(context)
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.d(TAG,   "Generic failure")
                GatewayServiceUtil.increment(context, GatewayServiceUtil.COUNT)
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - generic failure")
                GatewayServiceUtil.notifyStat(context)
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.d(TAG, "No service")
                GatewayServiceUtil.increment(context, GatewayServiceUtil.COUNT)
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - no service to")
                GatewayServiceUtil.notifyStat(context)
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.d(TAG, "Null PDU")
                GatewayServiceUtil.increment(context, GatewayServiceUtil.COUNT)
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - null PDU")
                GatewayServiceUtil.notifyStat(context)
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.d(TAG, "Radio off")
                GatewayServiceUtil.increment(context, GatewayServiceUtil.COUNT)
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - radio off")
                GatewayServiceUtil.notifyStat(context)
            }
        }
    }
}
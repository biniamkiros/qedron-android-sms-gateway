package com.qedron.gateway

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsSentReceiver : BroadcastReceiver() {

    private val tag = "TRACK_SMS_STATUS"
    override fun onReceive(context: Context, intent: Intent) {

        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.d(tag, "SMS sent")
                GatewayServiceUtil.increment(context, GatewayServiceUtil.SENT)
                GatewayServiceUtil.notifyStat(context)
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.d(tag,   "Generic failure")
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - generic failure")
                GatewayServiceUtil.notifyStat(context)
                GatewayServiceUtil.reportGenericBroadcastError(context)
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.d(tag, "No service")
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - no service to")
                GatewayServiceUtil.notifyStat(context)
                GatewayServiceUtil.errorOnBroadcast(context)
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.d(tag, "Null PDU")
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - null PDU")
                GatewayServiceUtil.notifyStat(context)
                GatewayServiceUtil.errorOnBroadcast(context)
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.d(tag, "Radio off")
                GatewayServiceUtil.increment(context,
                    GatewayServiceUtil.FAILED
                )
                GatewayServiceUtil.setStat(context,last="error - radio off")
                GatewayServiceUtil.notifyStat(context)
                GatewayServiceUtil.errorOnBroadcast(context)
            }
        }
    }
}
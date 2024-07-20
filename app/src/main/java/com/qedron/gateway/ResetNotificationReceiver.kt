package com.qedron.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ResetNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GatewayServiceUtil.clear(context)
        GatewayServiceUtil.close(context)
    }
}
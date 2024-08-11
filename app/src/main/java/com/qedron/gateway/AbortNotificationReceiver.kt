package com.qedron.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

class AbortNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appViewModelStore: ViewModelStore = (context.applicationContext as App).viewModelStore

        val viewModel: BroadcastViewModel by lazy {
            ViewModelProvider(
                appViewModelStore,
                ViewModelProvider.AndroidViewModelFactory(context.applicationContext as App)
            )[BroadcastViewModel::class.java]
        }
        viewModel.abortBroadcast()
    }
}
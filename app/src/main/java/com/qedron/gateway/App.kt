package com.qedron.gateway

import android.app.Application
import androidx.lifecycle.ViewModelStore

class App: Application() {
    val viewModelStore = ViewModelStore()

    override fun onTerminate() {
        super.onTerminate()
        viewModelStore.clear()
    }
}
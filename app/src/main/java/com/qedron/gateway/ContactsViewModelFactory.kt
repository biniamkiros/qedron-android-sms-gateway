package com.qedron.gateway

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qedron.gateway.ui.main.ContactsViewModel


class ContactsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            return ContactsViewModel(context,
               dbHelper = DatabaseHelperImpl(ContactDatabase.getDatabase(context))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
package com.qedron.gateway.ui.main


import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.qedron.gateway.Contact
import com.qedron.gateway.ContactWithMessages
import com.qedron.gateway.DatabaseHelperImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ContactsViewModel(context: Context, private val dbHelper: DatabaseHelperImpl) : ViewModel() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var _contactList = MutableLiveData<List<ContactWithMessages>>()
    var contactList: LiveData<List<ContactWithMessages>> = _contactList
    private var _updateContact = MutableLiveData<Contact?>()
    var updateContact: LiveData<Contact?> = _updateContact
    private var _contactCount = MutableLiveData<Int>()
    var contactCount: LiveData<Int> = _contactCount

    fun getContactsCount(searchText: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val result = dbHelper.searchCountContacts(searchText,!preferences.getBoolean("live", false))
                withContext(Dispatchers.Main){
                    _contactCount.value = result
                }
            }
        }
    }

    fun searchPaginatedContacts(searchText: String, offset: Int, limit: Int, order:String, sortBy: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val result = dbHelper.searchPaginatedContacts(searchText, offset, limit, order, sortBy, !preferences.getBoolean("live", false))
                withContext(Dispatchers.Main){
                    if(result.isNotEmpty()) _contactList.value = result
                }
            }
        }
    }

    fun updateContact(contact: Contact) {
        scope.launch {
            withContext(Dispatchers.IO) {  }
            dbHelper.updateContact(contact)
            val result = dbHelper.getContactById(contact.id)
            withContext(Dispatchers.Main){
                _updateContact.value = result
            }
        }
    }

}
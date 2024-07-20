package com.qedron.gateway

interface DatabaseHelper {

    suspend fun getAllContacts(): List<Contact>

    suspend fun getFreshLimitedContacts(days:Int, maxMsg:Int, limit:Int): List<Contact>

    suspend fun getFreshContacts(days:Int): List<Contact>

    suspend fun insertContact(contact: Contact)

    suspend fun updateContact(contact: Contact)

    suspend fun insertAll(contacts: List<Contact>)

    suspend fun countContacts():Int

    suspend fun deleteAllContacts()

    suspend fun insertMessage(message: Message)

    suspend fun getContactMessages(contactId: Long):List<Message>

    suspend fun countContactMessages(contactId: Long):Int

    suspend fun deleteAllMessages()


}
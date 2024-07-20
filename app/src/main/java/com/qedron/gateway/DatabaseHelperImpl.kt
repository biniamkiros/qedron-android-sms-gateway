package com.qedron.gateway

class DatabaseHelperImpl(private val contactDatabase: ContactDatabase) : DatabaseHelper {
    override suspend fun getAllContacts(): List<Contact> = contactDatabase.contactDao().getAllContacts()
    override suspend fun getFreshLimitedContacts(days: Int, maxMsg:Int, limit:Int): List<Contact> = contactDatabase.contactDao().getFreshLimitedContacts(days, maxMsg, limit)
    override suspend fun getFreshContacts(days: Int): List<Contact> = contactDatabase.contactDao().getFreshContacts(days)
    override suspend fun insertContact(contact: Contact) = contactDatabase.contactDao().insert(contact)
    override suspend fun updateContact(contact: Contact) = contactDatabase.contactDao().update(contact)
    override suspend fun insertAll(contacts: List<Contact>) = contactDatabase.contactDao().insertAll(contacts)
    override suspend fun countContacts(): Int = contactDatabase.contactDao().countContacts()
    override suspend fun deleteAllContacts() = contactDatabase.contactDao().deleteAllContacts()
    override suspend fun insertMessage(message: Message) = contactDatabase.messageDao().insert(message)
    override suspend fun getContactMessages(contactId: Long): List<Message> = contactDatabase.messageDao().getContactMessages(contactId)
    override suspend fun countContactMessages(contactId: Long): Int = contactDatabase.messageDao().countContactMessages(contactId)
    override suspend fun deleteAllMessages() = contactDatabase.messageDao().deleteAllMessages()
}
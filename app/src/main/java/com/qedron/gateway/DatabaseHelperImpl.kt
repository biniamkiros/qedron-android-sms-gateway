package com.qedron.gateway

class DatabaseHelperImpl(private val contactDatabase: ContactDatabase) : DatabaseHelper {
    override suspend fun getAllContacts(): List<ContactWithMessages> = contactDatabase.contactDao().getAllContacts()

    override suspend fun searchPaginatedContacts(searchText: String, offset: Int, limit: Int, order:String, sortBy: String, isTest: Boolean): List<ContactWithMessages> = contactDatabase.contactDao().searchPaginatedContacts(searchText, offset, limit, order, sortBy, isTest)

    override suspend fun searchCountContacts(searchText: String, isTest: Boolean): Int = contactDatabase.contactDao().countSearchContacts(searchText, isTest)

    override fun getAllUniqueTags()= contactDatabase.contactDao().getAllUniqueTags()

    override fun getMinAndMaxRanking()= contactDatabase.contactDao().getMinAndMaxRanking()

    override suspend fun getFreshFilteredLimitedTopContacts(
        days: Int,
        maxMsg: Int,
        limit: Int,
        tags: List<String>,
        tagsSize: Int,
        minRank: Int,
        maxRank: Int,
        isTest: Boolean
    )= contactDatabase.contactDao().getFreshFilteredLimitedTopContacts(days, maxMsg, limit, tags, tagsSize, minRank, maxRank, isTest)

    override suspend fun getFreshLimitedTopContacts(days: Int, maxMsg:Int, limit:Int): List<Contact> = contactDatabase.contactDao().getFreshLimitedTopContacts(days, maxMsg, limit)

    override suspend fun getFreshTopContacts(days: Int, limit:Int): List<Contact> = contactDatabase.contactDao().getFreshTopContacts(days, limit)

    override suspend fun getFreshTopTestContacts(limit: Int): List<Contact> = contactDatabase.contactDao().getFreshTopTestContacts(limit)

    override suspend fun insertContact(contact: Contact) = contactDatabase.contactDao().insert(contact)

    override suspend fun updateContact(contact: Contact) = contactDatabase.contactDao().update(contact)

    override suspend fun getContactById(contactId: Long): Contact?  = contactDatabase.contactDao().getContactById(contactId)

    override suspend fun getContactByPhone(phone: String): Contact?  = contactDatabase.contactDao().getContactByPhone(phone)

    override suspend fun insertAll(contacts: List<Contact>) = contactDatabase.contactDao().insertAll(contacts)

    override suspend fun countContacts(): Int = contactDatabase.contactDao().countContacts()

    override suspend fun countContacts(isTest: Boolean): Int = contactDatabase.contactDao().countContacts(isTest)

    override suspend fun deleteAllContacts() = contactDatabase.contactDao().deleteAllContacts()

    override suspend fun deleteContactsByTags(tags: List<String>) = contactDatabase.contactDao().deleteContactsByTags(tags)

    override suspend fun insertMessage(message: Message) = contactDatabase.messageDao().insert(message)

    override suspend fun getContactMessages(contactId: Long): List<Message> = contactDatabase.messageDao().getContactMessages(contactId)

    override suspend fun countContactMessages(contactId: Long): Int = contactDatabase.messageDao().countContactMessages(contactId)

    override suspend fun deleteAllMessages() = contactDatabase.messageDao().deleteAllMessages()

}
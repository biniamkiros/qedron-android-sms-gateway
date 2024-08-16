package com.qedron.gateway

interface DatabaseHelper {

    suspend fun getAllContacts(): List<Contact>

    suspend fun getAllContactsWithMessages(): List<ContactWithMessages>

    suspend fun searchPaginatedContacts(searchText: String, offset: Int, limit: Int, order:String, sortBy: String, isTest: Boolean): List<ContactWithMessages>

    suspend fun searchCountContacts(searchText: String, isTest: Boolean): Int

    fun getAllUniqueTags(): List<String>

    fun getMinAndMaxRanking(): MinMaxRanking

    suspend fun getFreshFilteredLimitedTopRankings(
        tags: List<String> = emptyList(),
        tagsSize: Int = tags.size,
        isTest: Boolean
    ): List<Long>

    suspend fun getFreshFilteredLimitedTopContacts(
        days: Long,
        maxMsg: Int,
        limit: Int,
        tags: List<String> = emptyList(),
        tagsSize: Int = tags.size,
        minRank: Long = 0,
        maxRank: Long = Long.MAX_VALUE,
        isTest: Boolean
    ): List<Contact>

    suspend fun getFreshLimitedTopContacts(days:Int, maxMsg:Int, limit:Int): List<Contact>

    suspend fun getFreshTopContacts(days:Int, limit:Int): List<Contact>

    suspend fun getFreshTopTestContacts(limit:Int): List<Contact>

    suspend fun insertContact(contact: Contact)

    suspend fun updateContact(contact: Contact)

    suspend fun getContactById(contactId: Long): Contact?

    suspend fun getContactByPhone(phone: String): Contact?

    suspend fun insertAll(contacts: List<Contact>)

    suspend fun countContacts():Int

    suspend fun countContacts(isTest: Boolean):Int

    suspend fun deleteAllContacts()

    suspend fun deleteContactsByTags(tags: List<String>)

    suspend fun insertMessage(message: Message)

    suspend fun getContactMessages(contactId: Long):List<Message>

    suspend fun countContactMessages(contactId: Long):Int

    suspend fun deleteAllMessages()

}
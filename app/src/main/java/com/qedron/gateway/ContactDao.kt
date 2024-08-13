package com.qedron.gateway

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update


@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Insert
    suspend fun insertAll(contacts: List<Contact>)

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: Long): Contact?

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone")
    suspend fun getContactByPhone(phone: String): Contact?

    @Query("SELECT contacts.*, COUNT(messages.id) AS messageCount FROM contacts LEFT JOIN messages ON contacts.id = messages.contactId")
    fun getAllContacts(): List<ContactWithMessages>

    @Query("SELECT * FROM contacts WHERE lastContact <= date('now',:days +'day') OR lastContact IS NULL ")
    fun getFreshContacts(days: Int): List<Contact>

    @Transaction
    @Query(
        """
    SELECT * FROM contacts 
    WHERE (lastContact <= date('now', :days || ' day') OR lastContact IS NULL)
    AND isTest = 0
    AND (id IN (
        SELECT contactId 
        FROM messages 
        GROUP BY contactId 
        HAVING COUNT(id) < :maxMsg
    ) 
    OR id NOT IN (
        SELECT contactId 
        FROM messages
    ))
    ORDER BY RANDOM()
    LIMIT :limit
    """
    )
    fun getFreshLimitedTopContacts(days: Int, maxMsg: Int, limit: Int): List<Contact>


    @Query("SELECT DISTINCT tag FROM contacts")
    fun getAllUniqueTags(): List<String>

    @Query("SELECT MIN(ranking) AS minRanking, MAX(ranking) AS maxRanking FROM contacts")
    fun getMinAndMaxRanking(): MinMaxRanking

    @Transaction
    @Query(
        """
    SELECT * FROM contacts 
    WHERE (lastContact <= date('now', :days || ' day') OR lastContact IS NULL) AND isTest = :isTest 
    AND (id IN (
        SELECT contactId 
        FROM messages 
        GROUP BY contactId 
        HAVING COUNT(id) < :maxMsg OR :maxMsg = -1
    ) 
    OR id NOT IN (
        SELECT contactId 
        FROM messages
    ))
    AND (:tagsSize = 0 OR tag IN (:tags))
    AND ranking BETWEEN :minRank AND :maxRank
    ORDER BY RANDOM()
    LIMIT :limit
    """
    )
    fun getFreshFilteredLimitedTopContacts(
        days: Int,
        maxMsg: Int,
        limit: Int,
        tags: List<String> = emptyList(),
        tagsSize: Int = tags.size,
        minRank: Int = 0,
        maxRank: Int = Int.MAX_VALUE,
        isTest: Boolean
    ): List<Contact>

    @Query(
        """
    SELECT * FROM contacts 
    WHERE isTest = 1 
    ORDER BY RANDOM()
    LIMIT :limit
    """
    )
    fun getFreshTopTestContacts(limit: Int): List<Contact>


    @Transaction
    @Query(
        """
        SELECT * FROM contacts 
        WHERE (lastContact <= date('now',:days +'day') OR lastContact IS NULL) 
        AND isTest = 0
        ORDER BY RANDOM()
        LIMIT :limit
    """
    )
    fun getFreshTopContacts(days: Int, limit: Int): List<Contact>

    @Query("SELECT COUNT(*) FROM contacts")
    fun countContacts(): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE isTest = :isTest")
    fun countContacts(isTest:Boolean): Int

    @Query("DELETE FROM contacts")
    fun deleteAllContacts()

    @Query("DELETE FROM contacts WHERE tag IN (:tags)")
    fun deleteContactsByTags(tags: List<String>)

    @Query(
        """
    SELECT contacts.*, COALESCE(messageCount, 0) AS messageCount
    FROM contacts
    LEFT JOIN (
        SELECT contactId, COUNT(id) AS messageCount
        FROM messages
        GROUP BY contactId
    ) AS subquery ON contacts.id = subquery.contactId
    WHERE (contacts.phoneNumber LIKE '%' || :searchText || '%'  
           OR contacts.name LIKE '%' || :searchText || '%' 
           OR contacts.details LIKE '%' || :searchText || '%' 
           OR contacts.tag LIKE '%' || :searchText || '%' 
           OR contacts.id IN (
               SELECT DISTINCT contactId 
               FROM messages 
               WHERE message LIKE '%' || :searchText || '%' 
           )
           OR :searchText IS NULL OR :searchText = '') AND isTest = :isTest 
    ORDER BY 
    CASE 
        WHEN :sortBy = 'ASC' THEN
            CASE 
                WHEN :orderBy = 'name' THEN COALESCE(contacts.name, '')
                WHEN :orderBy = 'details' THEN COALESCE(contacts.details, '') 
                WHEN :orderBy = 'phoneNumber' THEN COALESCE(contacts.phoneNumber, '') 
                WHEN :orderBy = 'tag' THEN COALESCE(contacts.tag, '') 
                WHEN :orderBy = 'ranking' THEN COALESCE(contacts.ranking, 0) 
                WHEN :orderBy = 'lastContact' THEN COALESCE(contacts.lastContact, "never") 
                WHEN :orderBy = 'blocked' THEN COALESCE(contacts.blocked, 0) 
                WHEN :orderBy = 'messageSize' THEN COALESCE(messageCount, 0) 
                ELSE contacts.name -- Default ordering by id
            END 
    END ASC,
    CASE 
        WHEN :sortBy = 'DESC' THEN 
            CASE 
                WHEN :orderBy = 'name' THEN COALESCE(contacts.name, '') 
                WHEN :orderBy = 'details' THEN COALESCE(contacts.details, '') 
                WHEN :orderBy = 'phoneNumber' THEN COALESCE(contacts.phoneNumber, '') 
                WHEN :orderBy = 'tag' THEN COALESCE(contacts.tag, '') 
                WHEN :orderBy = 'ranking' THEN COALESCE(contacts.ranking, 0) 
                WHEN :orderBy = 'lastContact' THEN COALESCE(contacts.lastContact, "never") 
                WHEN :orderBy = 'blocked' THEN COALESCE(contacts.blocked, 0) 
                WHEN :orderBy = 'messageSize' THEN COALESCE(messageCount, 0) 
                ELSE contacts.name -- Default ordering by id
            END 
    END DESC
    LIMIT :limit OFFSET :offset
    """
    )
    fun searchPaginatedContacts(
        searchText: String,
        offset: Int,
        limit: Int,
        orderBy: String,
        sortBy: String,
        isTest: Boolean
    ): List<ContactWithMessages>


    @Query("""SELECT COUNT(*) FROM contacts 
        LEFT JOIN (
            SELECT contactId, COUNT(id) AS messageCount
            FROM messages
            GROUP BY contactId
        ) AS subquery ON contacts.id = subquery.contactId
        WHERE (contacts.phoneNumber LIKE '%' || :searchText || '%'  
               OR contacts.name LIKE '%' || :searchText || '%' 
               OR contacts.details LIKE '%' || :searchText || '%' 
               OR contacts.tag LIKE '%' || :searchText || '%' 
               OR contacts.id IN (
                   SELECT DISTINCT contactId 
                   FROM messages 
                   WHERE message LIKE '%' || :searchText || '%' 
               )
               OR :searchText IS NULL OR :searchText = '') AND isTest = :isTest
    """)
    fun countSearchContacts(searchText: String, isTest: Boolean): Int
}
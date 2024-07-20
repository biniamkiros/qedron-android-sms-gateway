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

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): List<Contact>

    @Query("SELECT * FROM contacts WHERE lastContact <= date('now',:days +'day') OR lastContact IS NULL ")
    fun getFreshContacts(days:Int): List<Contact>

    @Transaction
    @Query("""
        SELECT * FROM contacts 
        WHERE lastContact <= date('now',:days +'day') OR lastContact IS NULL 
        AND id IN (
            SELECT contactId 
            FROM messages 
            GROUP BY contactId 
            HAVING COUNT(id) < :maxMsg
        ) 
        OR id NOT IN (
            SELECT contactId 
            FROM messages
        )
        LIMIT :limit
    """)
    fun getFreshLimitedContacts(days:Int, maxMsg:Int, limit:Int): List<Contact>

    @Transaction
    @Query("""
        SELECT * FROM contacts 
        WHERE lastContact <= date('now',-7 +'day') OR lastContact IS NULL 
        AND id IN (
            SELECT contactId 
            FROM messages 
            GROUP BY contactId 
            HAVING COUNT(id) < 100
        ) 
        OR id NOT IN (
            SELECT contactId 
            FROM messages
        )
    """)
    fun getFreshLimitedContacts(): List<ContactWithMessages>

    @Query("SELECT COUNT(*) FROM contacts")
    fun countContacts(): Int

    @Query("DELETE FROM contacts")
    fun deleteAllContacts()

}
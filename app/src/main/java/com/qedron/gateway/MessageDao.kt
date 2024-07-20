package com.qedron.gateway

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message)

    @Update
    suspend fun update(message: Message)

    @Delete
    suspend fun delete(message: Message)

    @Query("SELECT * FROM messages WHERE contactId == :contactId")
    fun getContactMessages(contactId: Long): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE contactId == :contactId")
    fun countContactMessages(contactId: Long): Int

    @Query("SELECT COUNT(*) FROM messages")
    fun countMessages(): Int

    @Query("DELETE FROM messages")
    fun deleteAllMessages()
}
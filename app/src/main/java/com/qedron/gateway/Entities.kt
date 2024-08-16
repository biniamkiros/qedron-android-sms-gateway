package com.qedron.gateway

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "contacts", indices = [Index(value = ["phoneNumber"], unique = true)])
@TypeConverters(DateConverter::class)
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var name: String = "",
    var details: String = "",
    var phoneNumber: String,
    var tag: String = "",
    var ranking: Long = 0,
    var lastContact: Date? = null,
    var blocked: Boolean = false,
    val isTest:Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Contact::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )]
)
@TypeConverters(DateConverter::class)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val message:String,
    val timeStamp: Date)

data class ContactWithMessages(
    @Embedded var contact: Contact,
    @Relation(
        parentColumn = "id",
        entityColumn = "contactId"
    )
    val messages: List<Message>,
    val messageCount: Int
)

data class MinMaxRanking(
    val minRanking: Long,
    val avgRanking: Long,
    val maxRanking: Long
)


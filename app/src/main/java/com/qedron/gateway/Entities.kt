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
    val name: String?,
    val phoneNumber: String,
    var lastContact: Date?
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
    @Embedded val contact: Contact,
    @Relation(
        parentColumn = "id",
        entityColumn = "contactId"
    )
    val messages: List<Message>
)
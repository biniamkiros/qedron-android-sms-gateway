package com.qedron.gateway

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Contact::class, Message::class], version = 1)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    companion object {
        @Volatile
        private var INSTANCE: ContactDatabase? = null

        fun getDatabase(context: Context): ContactDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactDatabase::class.java,
                    "gateway_database"
                )
//                    .fallbackToDestructiveMigration()
//                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(val context: Context) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Populate the database in the background.
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.contactDao())
                    }
                }
            }

            suspend fun populateDatabase(contactDao: ContactDao) {
                contactDao.insertAll(GatewayServiceUtil.generateTestContacts(context))
            }
        }
    }
}

//val MIGRATION_1_2 = object : Migration(1, 2) {
//    override fun migrate(db: SupportSQLiteDatabase) {
//        // Custom migration logic (e.g., altering tables, adding columns)
//        // You can execute SQL statements here.
////        db.execSQL("ALTER TABLE contacts ADD COLUMN ranking INTEGER DEFAULT 0")
////        db.execSQL("ALTER TABLE contacts ADD COLUMN blocked INTEGER DEFAULT false")
//    }
//}

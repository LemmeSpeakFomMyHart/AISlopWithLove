package com.example.aislopwithlove.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * База данных Room для хранения истории диалогов агента.
 *
 * Содержит одну таблицу messages.
 */
@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Возвращает DAO для работы с сообщениями.
     */
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Возвращает синглтон базы данных.
         * Используется паттерн Double-Checked Locking для потокобезопасности.
         *
         * @param context Контекст приложения
         * @return Экземпляр AppDatabase
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agent_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
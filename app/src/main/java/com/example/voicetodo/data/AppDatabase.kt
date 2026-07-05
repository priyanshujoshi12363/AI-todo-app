package com.example.voicetodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun toRecurrence(value: String): Recurrence = Recurrence.valueOf(value)

    @TypeConverter
    fun fromRecurrence(value: Recurrence): String = value.name

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.valueOf(value)

    @TypeConverter
    fun fromActionType(value: ActionType): String = value.name
}

@Database(entities = [Todo::class, Note::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice-todo.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

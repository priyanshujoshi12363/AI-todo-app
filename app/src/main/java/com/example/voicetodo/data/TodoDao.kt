package com.example.voicetodo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY isDone ASC, dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE dueAt IS NOT NULL")
    suspend fun withReminders(): List<Todo>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun byId(id: Long): Todo?

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Delete
    suspend fun delete(todo: Todo)
}

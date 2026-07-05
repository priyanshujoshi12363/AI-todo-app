package com.example.voicetodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)

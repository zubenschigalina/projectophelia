package com.example.projectophelia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single note row in the database.
 *
 * `@Entity` tells Room "treat this data class as a table". The default table name is the
 * lowercase class name; we override it here as `notes` for clarity in SQL queries.
 *
 * Fields:
 *  - [id]:        Auto-generated row id. `autoGenerate = true` lets SQLite pick the next
 *                 integer; we pass 0 when *creating* a new note and Room replaces it with
 *                 the assigned id on insert.
 *  - [title]:     Short headline shown in the list. May be blank — we'll fall back to a
 *                 placeholder in the UI when it is.
 *  - [content]:   The full body text. Can be empty.
 *  - [updatedAt]: Last-modified timestamp in epoch milliseconds. Stored as Long so we
 *                 don't need a Room TypeConverter. Set/refreshed by the repository.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

package com.example.projectophelia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Note]. Room generates the implementation at compile time
 * (via KSP) — we just declare the queries and method signatures.
 *
 * Method shape conventions used here:
 *  - `suspend fun` for one-shot writes/reads. Coroutines off the main thread for free.
 *  - `Flow<...>` for observable reads. Room emits a fresh value whenever the underlying
 *    table changes, so the UI updates automatically when notes are added/removed/edited.
 */
@Dao
interface NoteDao {

    /**
     * All notes, freshest first. Returns a Flow so collectors get re-emitted on any
     * change to the `notes` table — perfect for driving a RecyclerView.
     */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAllNewestFirst(): Flow<List<Note>>

    /**
     * All notes, oldest first. Companion to [observeAllNewestFirst] for ascending sort.
     * The repository picks one or the other based on the user's [SortOrder] preference.
     */
    @Query("SELECT * FROM notes ORDER BY updatedAt ASC")
    fun observeAllOldestFirst(): Flow<List<Note>>

    /**
     * Look up a single note by primary key. Returns null if it doesn't exist.
     * Used when opening an existing note in the editor.
     */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Note?

    /**
     * Insert a brand-new note. Returns the auto-generated row id so callers can hold
     * onto it (e.g. to switch the editor from "create" mode to "edit existing" mode
     * after the first save).
     *
     * `OnConflictStrategy.ABORT` (the default) is fine here because new inserts pass
     * `id = 0` — there's no realistic primary-key collision.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: Note): Long

    /**
     * Update an existing note (matched by primary key).
     */
    @Update
    suspend fun update(note: Note)

    /**
     * Delete a note. We pass the whole entity rather than just the id so Room can
     * verify it knows the row.
     */
    @Delete
    suspend fun delete(note: Note)

    /**
     * Bulk-delete by id list. Used by the multi-select "delete X notes" flow.
     * One SQL DELETE is much cheaper than N individual deletes for the same set.
     */
    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

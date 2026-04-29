package com.example.projectophelia.data

import kotlinx.coroutines.flow.Flow

/**
 * A thin wrapper around [NoteDao]. In a tiny app this layer feels redundant, but it
 * gives us:
 *  - A single place to apply business rules (e.g. always refresh `updatedAt` on save).
 *  - A swap point if we ever add a network source or in-memory cache.
 *  - A class our ViewModels can depend on instead of Room directly, which keeps them
 *    easier to unit-test (you can stub the repository).
 */
class NotesRepository(private val dao: NoteDao) {

    /**
     * Live stream of notes for the requested [order].
     *
     * Each [SortOrder] maps to a different DAO query because Room can't take an
     * `ORDER BY` direction as a bind parameter. We could use `@RawQuery` to make this
     * fully dynamic, but two cases hardly justify the extra complexity.
     */
    fun notes(order: SortOrder): Flow<List<Note>> = when (order) {
        SortOrder.NEWEST_FIRST -> dao.observeAllNewestFirst()
        SortOrder.OLDEST_FIRST -> dao.observeAllOldestFirst()
    }

    /** Look up a single note by id (for the editor screen). */
    suspend fun getById(id: Long): Note? = dao.getById(id)

    /**
     * Save a note — either inserts a new row or updates an existing one based on
     * whether [Note.id] is zero.
     *
     * Returns the row id, which is the existing id for updates and the freshly
     * generated id for inserts. The editor uses this to switch from "new" mode to
     * "edit" mode after the first save.
     */
    suspend fun save(note: Note): Long {
        // Always stamp the modification time so the list ordering stays accurate.
        val touched = note.copy(updatedAt = System.currentTimeMillis())
        return if (touched.id == 0L) {
            dao.insert(touched)
        } else {
            dao.update(touched)
            touched.id
        }
    }

    /** Delete a note. Safe to call on an unsaved note (it just no-ops at the DAO). */
    suspend fun delete(note: Note) {
        // Guard: trying to delete an id=0 row would be a no-op anyway, but skipping
        // saves a round-trip and avoids surprising debug logs.
        if (note.id != 0L) dao.delete(note)
    }

    /**
     * Bulk-delete a set of notes by id. Used by the multi-select flow.
     * Empty input is a no-op so callers don't have to special-case "nothing selected".
     */
    suspend fun deleteAll(notes: List<Note>) {
        if (notes.isEmpty()) return
        dao.deleteByIds(notes.map { it.id })
    }

    /**
     * Re-insert a list of notes (the undo path after a bulk delete). We deliberately
     * insert with id=0 so SQLite assigns fresh ids — re-using the original ids would
     * collide with any rows added during the undo window.
     *
     * `save()` also stamps `updatedAt`, which means restored notes pop to the top of
     * the list (under NEWEST_FIRST sort) — easy to find again.
     */
    suspend fun restoreAll(notes: List<Note>) {
        notes.forEach { note -> save(note.copy(id = 0L)) }
    }
}

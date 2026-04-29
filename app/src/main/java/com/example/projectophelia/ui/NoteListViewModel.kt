package com.example.projectophelia.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projectophelia.data.Note
import com.example.projectophelia.data.NotesPreferences
import com.example.projectophelia.data.NotesRepository
import com.example.projectophelia.data.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the notes-list screen.
 *
 * Two cross-cutting pieces of state:
 *  - **Sort order** ([sortOrder]) — newest-first vs. oldest-first. Persisted in
 *    SharedPreferences so the user's choice survives across launches.
 *  - **Selection** ([selectedIds]) — which note ids are currently checked in
 *    multi-select mode. Empty == not in selection mode.
 *
 * The exposed [noteUiItems] flow combines:
 *   (a) the notes from the repo (whose ordering follows [sortOrder])
 *   (b) the current selection set
 * → producing the typed [NoteUiItem] list the adapter renders.
 */
@OptIn(ExperimentalCoroutinesApi::class) // for flatMapLatest
class NoteListViewModel(
    private val repository: NotesRepository,
    private val preferences: NotesPreferences,
) : ViewModel() {

    // ----- Sort order -----

    /** Backing flow seeded from disk so the previous choice is honored on launch. */
    private val _sortOrder = MutableStateFlow(preferences.sortOrder)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    /**
     * Switch sort direction. Updates the in-memory flow *and* persists to disk in
     * one step so it sticks across process death.
     */
    fun setSortOrder(order: SortOrder) {
        if (_sortOrder.value == order) return // no-op if unchanged
        _sortOrder.value = order
        preferences.sortOrder = order
    }

    // ----- Selection state -----

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /**
     * "Are we currently in multi-select mode?" Derived from selectedIds — true when
     * at least one row is checked. Activities collect this to swap the toolbar's
     * appearance.
     *
     * `Eagerly` because the UI consults this on every render; we don't want the
     * collector to spin up lazily and miss the initial value.
     */
    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Toggle one row's selection. Re-tapping a selected row deselects it. */
    fun toggleSelection(noteId: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (!add(noteId)) remove(noteId)
        }
    }

    /** Drop all selection — also exits selection mode. */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ----- Combined "what should the list render" flow -----

    /**
     * The data the adapter consumes. Whenever sort order changes we swap which
     * underlying DAO flow we listen to (via flatMapLatest, which cancels the previous
     * subscription cleanly). Whenever selection changes we re-map each note to a
     * NoteUiItem with its updated `selected` flag.
     */
    val noteUiItems: StateFlow<List<NoteUiItem>> = combine(
        _sortOrder.flatMapLatest { repository.notes(it) },
        _selectedIds,
    ) { notes, selected ->
        notes.map { NoteUiItem(it, it.id in selected) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // ----- Mutations (single + bulk) -----

    /** Single-row delete (used by swipe-to-delete). */
    fun delete(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    /**
     * Restore a single note — companion to the swipe-to-delete Snackbar's UNDO action.
     * Re-uses the bulk path; passing a one-element list keeps the code DRY.
     */
    fun restore(note: Note) {
        viewModelScope.launch { repository.restoreAll(listOf(note)) }
    }

    /**
     * Bulk-delete the currently-selected rows. Returns the deleted notes synchronously
     * (snapshot taken before the suspend) so the caller can show "X notes deleted —
     * UNDO" with the right list to restore.
     */
    fun deleteSelected(): List<Note> {
        val selectedSnapshot = _selectedIds.value
        if (selectedSnapshot.isEmpty()) return emptyList()

        // Resolve ids → full Note objects from our currently-cached list. We need the
        // full data so the undo path can re-insert with the original title/content.
        val toDelete = noteUiItems.value
            .filter { it.note.id in selectedSnapshot }
            .map { it.note }

        // Mutation order matters: clear selection BEFORE we suspend on the delete so
        // the UI exits selection mode immediately (no flash of "X selected" with no
        // rows). The actual SQL delete runs asynchronously.
        clearSelection()
        viewModelScope.launch { repository.deleteAll(toDelete) }
        return toDelete
    }

    /** UNDO path for [deleteSelected]. */
    fun restoreAll(notes: List<Note>) {
        viewModelScope.launch { repository.restoreAll(notes) }
    }

    // ----- Factory -----

    /**
     * Boilerplate factory so MainActivity can construct this ViewModel without Hilt.
     */
    class Factory(
        private val repository: NotesRepository,
        private val preferences: NotesPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NoteListViewModel::class.java))
            return NoteListViewModel(repository, preferences) as T
        }
    }
}

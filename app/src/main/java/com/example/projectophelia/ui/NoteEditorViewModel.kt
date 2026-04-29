package com.example.projectophelia.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.projectophelia.data.Note
import com.example.projectophelia.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the editor screen — both for creating a new note and editing an existing one.
 *
 * Lifecycle:
 *  1. Activity calls [load] with either an existing id or `Note.NEW_ID`.
 *  2. Editor observes [note] for the current state.
 *  3. User edits → activity calls [save] / [delete].
 *  4. ViewModel publishes a terminal [Event.Closed] event when the editor should finish.
 */
class NoteEditorViewModel(
    private val repository: NotesRepository,
) : ViewModel() {

    /** The note currently in the editor. Null until [load] has resolved. */
    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    /**
     * One-shot UI events. We use a StateFlow with a nullable value rather than a Channel
     * for simplicity; the activity nulls it out after handling so we don't re-fire on
     * config change. (Channels/SharedFlow are the more idiomatic answer in larger apps.)
     */
    private val _events = MutableStateFlow<Event?>(null)
    val events: StateFlow<Event?> = _events.asStateFlow()

    /**
     * Load an existing note, or initialize an empty one for create-mode.
     *
     * @param noteId Pass [NEW_NOTE_ID] to start a blank note.
     */
    fun load(noteId: Long) {
        // Already loaded? Skip — happens after a configuration change because the
        // ViewModel survived but the Activity re-runs onCreate.
        if (_note.value != null) return

        viewModelScope.launch {
            _note.value = if (noteId == NEW_NOTE_ID) {
                Note() // defaults: id=0, blank fields, fresh timestamp
            } else {
                // Fall back to a blank note if the id is missing (e.g. deleted from
                // another screen). The editor will treat it as a new note.
                repository.getById(noteId) ?: Note()
            }
        }
    }

    /**
     * Persist the given title/content. If both are blank, deletes any existing row
     * and closes — a blank note has no value and would clutter the list.
     */
    fun save(title: String, content: String) {
        val current = _note.value ?: return
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()

        viewModelScope.launch {
            if (trimmedTitle.isEmpty() && trimmedContent.isEmpty()) {
                // Nothing to save. If we're editing, drop the existing row.
                if (current.id != 0L) repository.delete(current)
            } else {
                repository.save(current.copy(title = trimmedTitle, content = trimmedContent))
            }
            _events.value = Event.Closed
        }
    }

    /** Delete the currently-loaded note and signal the editor to finish. */
    fun delete() {
        val current = _note.value ?: return
        viewModelScope.launch {
            repository.delete(current)
            _events.value = Event.Closed
        }
    }

    /** Activity calls this after handling an event so it doesn't fire twice. */
    fun consumeEvent() {
        _events.value = null
    }

    /** One-shot signals from the ViewModel back to the activity. */
    sealed interface Event {
        /** Editor should call finish() — work is done (saved or deleted). */
        data object Closed : Event
    }

    companion object {
        /** Sentinel id meaning "create a new note" (vs. editing an existing one). */
        const val NEW_NOTE_ID: Long = -1L
    }

    /** Factory mirroring [NoteListViewModel.Factory] — see comments there. */
    class Factory(private val repository: NotesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NoteEditorViewModel::class.java))
            return NoteEditorViewModel(repository) as T
        }
    }
}

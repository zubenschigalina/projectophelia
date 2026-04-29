package com.example.projectophelia.ui

import com.example.projectophelia.data.Note

/**
 * What the list adapter actually renders: a [Note] plus its current selection state.
 *
 * Kept as a separate type (not just `Pair<Note, Boolean>`) so DiffUtil can use the
 * data class equality to detect both content changes *and* selection toggles.
 */
data class NoteUiItem(
    val note: Note,
    val selected: Boolean,
)

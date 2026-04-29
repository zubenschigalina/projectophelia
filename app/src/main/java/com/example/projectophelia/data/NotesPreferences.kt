package com.example.projectophelia.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Tiny wrapper around [SharedPreferences] for the app's user preferences (just the
 * sort order today).
 *
 * Why SharedPreferences and not DataStore? DataStore is the modern, coroutine-friendly
 * replacement. For a single key with a simple value, SharedPreferences is plenty;
 * no Flow plumbing needed. Easy to swap in DataStore later if we add more settings.
 *
 * `commit = false` (the default for `edit { }`) is fine here — apply() writes async,
 * but the in-memory cache is updated synchronously, so reading right after writing
 * returns the new value immediately.
 */
class NotesPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Persistently-stored sort order.
     *
     * Stored as the enum's `name` (a String) so the file is human-readable and
     * resilient to enum reordering.
     */
    var sortOrder: SortOrder
        get() {
            val stored = prefs.getString(KEY_SORT_ORDER, null)
            return runCatching { SortOrder.valueOf(stored ?: "") }
                .getOrDefault(SortOrder.NEWEST_FIRST)
        }
        set(value) {
            prefs.edit { putString(KEY_SORT_ORDER, value.name) }
        }

    private companion object {
        const val PREFS_NAME = "notes_prefs"
        const val KEY_SORT_ORDER = "sort_order"
    }
}

package com.example.projectophelia

import android.app.Application
import com.example.projectophelia.data.NoteDatabase
import com.example.projectophelia.data.NotesPreferences
import com.example.projectophelia.data.NotesRepository

/**
 * Custom [Application] subclass — Android instantiates this once per process and we get
 * to hang process-wide singletons off it.
 *
 * We expose [repository] and [preferences] here so any Activity/ViewModel can fetch the
 * same instance via:
 *   `(applicationContext as NotesApplication).repository`
 *
 * It's the dead-simplest form of "dependency injection". Real-world apps usually reach
 * for Hilt or Koin; for a learning project this is plenty.
 *
 * Registered in AndroidManifest.xml via `android:name=".NotesApplication"`.
 *
 * `by lazy` defers construction until the first access, so opening the DB / reading
 * preferences doesn't block app startup.
 */
class NotesApplication : Application() {

    val repository: NotesRepository by lazy {
        NotesRepository(NoteDatabase.get(this).noteDao())
    }

    val preferences: NotesPreferences by lazy {
        NotesPreferences(this)
    }
}

package com.example.projectophelia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database singleton.
 *
 * `@Database` lists every entity class and the schema version. Bumping `version` later
 * requires a Migration (or `fallbackToDestructiveMigration` in dev — wipes data).
 *
 * `exportSchema = false` skips writing the schema JSON to disk. Real apps usually keep
 * this on for migration auditing, but it's overkill for a learning project.
 */
@Database(
    entities = [Note::class],
    version = 1,
    exportSchema = false,
)
abstract class NoteDatabase : RoomDatabase() {

    /**
     * Room generates the DAO implementation; we just declare the abstract accessor here.
     */
    abstract fun noteDao(): NoteDao

    companion object {
        /**
         * Backing field for our singleton. `@Volatile` guarantees that writes to
         * INSTANCE are visible across threads immediately (no per-CPU caching).
         */
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        /**
         * Returns the process-wide [NoteDatabase] instance, creating it the first time.
         *
         * The double-checked `synchronized` block makes initialization thread-safe
         * without paying lock cost on every subsequent call.
         */
        fun get(context: Context): NoteDatabase {
            // Fast path: already initialized.
            INSTANCE?.let { return it }

            // Slow path: initialize once, then cache.
            return synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    // applicationContext avoids leaking an Activity if this gets called
                    // from one (the database lives as long as the process).
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notes.db",
                )
                    // Schema upgrades will wipe all data. Fine for v1 of a demo app;
                    // replace with proper Migrations before shipping anything real.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

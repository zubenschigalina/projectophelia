package com.example.projectophelia.data

/**
 * Direction the notes list is sorted by `updatedAt`.
 *
 * Kept as an enum (not a Boolean) so we can grow this later (e.g. sort by title)
 * without touching every call site.
 */
enum class SortOrder {
    /** Most recently updated notes at the top. The default. */
    NEWEST_FIRST,
    /** Oldest notes at the top — useful for chronological "journal" reading. */
    OLDEST_FIRST,
}

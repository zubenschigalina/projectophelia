package com.example.projectophelia.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.projectophelia.databinding.ItemNoteBinding
import com.google.android.material.card.MaterialCardView

/**
 * RecyclerView adapter for the notes list.
 *
 * Why [ListAdapter]? It runs DiffUtil off the main thread, so updating the list is
 * animated and efficient — items that didn't change don't get rebound, removed items
 * animate out, added items animate in. Compared to `notifyDataSetChanged()`, this is
 * much smoother on a busy list.
 *
 * Items are [NoteUiItem]s rather than raw Notes so the adapter can render selection
 * state. Whenever a row's `selected` flips, DiffUtil reports a content change and we
 * re-bind that one row.
 *
 * @param onClick      Tapped a row: open the editor (in normal mode) or toggle the
 *                     selection (the activity decides which based on selection mode).
 * @param onLongClick  Long-pressed a row: enter selection mode and check this row.
 */
class NoteAdapter(
    private val onClick: (NoteUiItem) -> Unit,
    private val onLongClick: (NoteUiItem) -> Unit,
) : ListAdapter<NoteUiItem, NoteAdapter.NoteViewHolder>(DIFF) {

    /** Inflate `item_note.xml` and wrap it in a ViewHolder. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            /* attachToParent = */ false,
        )
        return NoteViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Public helper so an ItemTouchHelper can read which item is at a given row. */
    fun itemAt(position: Int): NoteUiItem = getItem(position)

    /** Holds references to the views for one row and (re)populates them on bind. */
    class NoteViewHolder(
        private val binding: ItemNoteBinding,
        onClick: (NoteUiItem) -> Unit,
        onLongClick: (NoteUiItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        // Cache the most recently bound item so listeners don't have to re-query the
        // adapter for the position (positions can shift mid-animation).
        private var current: NoteUiItem? = null

        // The root of item_note.xml is a MaterialCardView. We cast once so we can
        // toggle isChecked / accept ripple feedback on long-press without repeat casts.
        private val card: MaterialCardView = binding.root as MaterialCardView

        init {
            // Tap → open editor (or toggle selection in selection mode — activity decides).
            card.setOnClickListener {
                current?.let(onClick)
            }
            // Long-press → enter selection mode + check this row. Returning true tells
            // the framework we consumed the gesture (don't also fire onClick).
            card.setOnLongClickListener {
                current?.let(onLongClick)
                true
            }
        }

        fun bind(item: NoteUiItem) {
            current = item
            val note = item.note

            // Title falls back to a placeholder when blank, so users still have
            // something to tap.
            binding.title.text = note.title.ifBlank {
                binding.root.context.getString(
                    com.example.projectophelia.R.string.untitled_note,
                )
            }

            // Show the first non-blank line of content (or hide the preview if empty).
            val firstLine = note.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            if (firstLine.isNullOrEmpty()) {
                binding.preview.visibility = android.view.View.GONE
            } else {
                binding.preview.visibility = android.view.View.VISIBLE
                binding.preview.text = firstLine
            }

            // Relative timestamp like "5 minutes ago", "yesterday", "Mar 12".
            // DateUtils handles localization for us.
            binding.timestamp.text = DateUtils.getRelativeTimeSpanString(
                note.updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )

            // Apply Material's "checked" visual state. With app:checkable="true" on
            // the MaterialCardView, this draws an overlay tint + check mark in the
            // top-right corner.
            card.isChecked = item.selected
        }
    }

    companion object {
        /**
         * DiffUtil callback. `areItemsTheSame` matches by id (so ListAdapter can map
         * the same logical row across list updates); `areContentsTheSame` compares the
         * whole [NoteUiItem] which catches both content edits and selection toggles.
         */
        private val DIFF = object : DiffUtil.ItemCallback<NoteUiItem>() {
            override fun areItemsTheSame(oldItem: NoteUiItem, newItem: NoteUiItem) =
                oldItem.note.id == newItem.note.id

            override fun areContentsTheSame(oldItem: NoteUiItem, newItem: NoteUiItem) =
                oldItem == newItem
        }
    }
}

package com.example.projectophelia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.projectophelia.data.Note
import com.example.projectophelia.data.SortOrder
import com.example.projectophelia.databinding.ActivityMainBinding
import com.example.projectophelia.ui.NoteAdapter
import com.example.projectophelia.ui.NoteEditorActivity
import com.example.projectophelia.ui.NoteListViewModel
import com.example.projectophelia.ui.NoteUiItem
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Notes-list screen.
 *
 * Two modes:
 *  - **Normal mode**: toolbar shows "Notes" + sort action; FAB is visible; tapping a
 *    row opens the editor; swipe-to-delete is enabled.
 *  - **Selection mode** (entered via long-press on a row): toolbar shows "X selected"
 *    + an X close icon + delete action; FAB is hidden; tap toggles selection;
 *    swipe is disabled; back press exits selection mode.
 *
 * Sort choice (newest- vs oldest-first) lives in SharedPreferences and is applied
 * before the first frame.
 */
class MainActivity : AppCompatActivity() {

    /** ViewBinding generated from activity_main.xml. */
    private lateinit var binding: ActivityMainBinding

    /**
     * `viewModels { ... }` is an Activity-ktx delegate. It:
     *  - Lazily creates the ViewModel on first access.
     *  - Caches it across config changes (rotation, theme switch, etc.).
     *  - Uses our factory because the VM has constructor parameters.
     */
    private val viewModel: NoteListViewModel by viewModels {
        val app = applicationContext as NotesApplication
        NoteListViewModel.Factory(app.repository, app.preferences)
    }

    /**
     * Adapter receives both click types from us. We translate them into the right
     * VM action based on whether we're currently in selection mode.
     */
    private val adapter = NoteAdapter(
        onClick = { item -> handleRowClick(item) },
        onLongClick = { item -> handleRowLongClick(item) },
    )

    /**
     * Back-press handler that exits selection mode instead of finishing the activity.
     * We toggle `isEnabled` based on selection state so a back-press in normal mode
     * still bubbles up to the system (and finishes the activity).
     */
    private val backInSelectionMode = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.clearSelection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge before content inflation: see applyWindowInsets() for the rest.
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The MaterialToolbar is our "support" action bar — drives default menu/title behavior.
        setSupportActionBar(binding.toolbar)

        // Inflate the normal-mode menu so the sort icon appears at app start. The
        // selection menu is inflated on demand when entering selection mode.
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener(::onMenuItemClick)

        applyWindowInsets()
        setUpRecyclerView()
        setUpFab()
        observeViewModel()

        // Register back-press handler. It's disabled by default; we enable it whenever
        // we enter selection mode.
        onBackPressedDispatcher.addCallback(this, backInSelectionMode)
    }

    /**
     * Apply window-inset padding so the AppBar doesn't slide under the status bar and
     * the FAB / list content don't sit under the gesture/navigation bar.
     *
     * See the long comment in MainActivity at git history if curious about the
     * "capture original padding" pattern — short version: it prevents inset values
     * from compounding across repeat dispatches (e.g. when the IME opens).
     */
    private fun applyWindowInsets() {
        // ---- AppBar: pad top by the status-bar height ----
        val appBarInitialTopPadding = binding.appBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = appBarInitialTopPadding + sys.top)
            insets
        }

        // ---- RecyclerView: pad bottom by the navigation-bar height ----
        // (preserves the existing space for the FAB).
        val listInitialBottomPadding = binding.notesList.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.notesList) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = listInitialBottomPadding + sys.bottom)
            insets
        }

        // ---- FAB: lift its bottom margin above the gesture bar ----
        val fabInitialBottomMargin =
            (binding.fabAdd.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAdd) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabInitialBottomMargin + sys.bottom
            }
            insets
        }
    }

    /**
     * RecyclerView setup: layout manager, adapter, and a swipe-to-delete callback that
     * disables itself in selection mode.
     */
    private fun setUpRecyclerView() {
        binding.notesList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            /* dragDirs = */ 0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            // Returning 0 here tells ItemTouchHelper "this row can't be swiped right now."
            // We use it to suppress swipe while the user is multi-selecting — the
            // checkbox-style flow shouldn't double as a swipe gesture.
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int = if (viewModel.isSelectionMode.value) 0
            else super.getMovementFlags(recyclerView, viewHolder)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return

                val swiped = adapter.itemAt(position).note
                viewModel.delete(swiped)
                showSingleUndoSnackbar(swiped)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.notesList)
    }

    /** FAB → open the editor in "new note" mode. (The FAB is hidden in selection mode.) */
    private fun setUpFab() {
        binding.fabAdd.setOnClickListener {
            openEditor(noteId = null)
        }
    }

    /**
     * Single subscription that keeps the UI in sync with VM state:
     *  - The notes/UI items list (adapter + empty state).
     *  - Selection mode (toolbar appearance + FAB visibility + back handler enabled).
     *  - Sort order changes (currently only used for the sort popup's checked state,
     *    which we read on-demand when the popup opens).
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Notes list + empty state.
                launch {
                    viewModel.noteUiItems.collect { items ->
                        adapter.submitList(items)
                        binding.emptyState.visibility =
                            if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                // Selection mode → toolbar appearance + FAB + back handler.
                launch {
                    viewModel.isSelectionMode.collect { inSelection ->
                        renderSelectionMode(inSelection)
                    }
                }
                // Selection count → toolbar title in selection mode.
                launch {
                    viewModel.selectedIds.collect { ids ->
                        if (ids.isNotEmpty()) {
                            binding.toolbar.title =
                                getString(R.string.selection_title, ids.size)
                        }
                    }
                }
            }
        }
    }

    /**
     * Toggle the toolbar between "Notes" and "X selected" appearance, plus the FAB
     * and back-press handler.
     */
    private fun renderSelectionMode(inSelection: Boolean) {
        if (inSelection) {
            // Title is set elsewhere from the selectedIds collector — we just swap the
            // chrome here.
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_selection)
            binding.toolbar.setNavigationIcon(R.drawable.ic_close)
            binding.toolbar.setNavigationContentDescription(R.string.action_clear_selection)
            binding.toolbar.setNavigationOnClickListener { viewModel.clearSelection() }
            binding.fabAdd.hide()
            backInSelectionMode.isEnabled = true
        } else {
            binding.toolbar.title = getString(R.string.title_notes)
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_main)
            binding.toolbar.navigationIcon = null
            binding.toolbar.setNavigationOnClickListener(null)
            binding.fabAdd.show()
            backInSelectionMode.isEnabled = false
        }
    }

    // ----- Click routing -----

    /**
     * Tapping a row:
     *  - In selection mode → toggle this note's selection (potentially exiting mode).
     *  - In normal mode    → open the editor for this note.
     */
    private fun handleRowClick(item: NoteUiItem) {
        if (viewModel.isSelectionMode.value) {
            viewModel.toggleSelection(item.note.id)
        } else {
            openEditor(noteId = item.note.id)
        }
    }

    /**
     * Long-pressing a row enters selection mode (or, if already in it, just toggles
     * the long-pressed row).
     */
    private fun handleRowLongClick(item: NoteUiItem) {
        viewModel.toggleSelection(item.note.id)
    }

    // ----- Toolbar menu actions -----

    /**
     * Single dispatch point for both the normal-mode and selection-mode menus. The
     * inflated menu only has whichever items are valid for the current mode, so the
     * `when` cleanly handles both.
     */
    private fun onMenuItemClick(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_sort -> {
            showSortMenu()
            true
        }
        R.id.action_delete_selected -> {
            performBulkDelete()
            true
        }
        else -> false
    }

    /**
     * Show a popup menu anchored to the sort toolbar action with two radio-checked
     * options. The currently-active SortOrder is pre-checked.
     */
    private fun showSortMenu() {
        // Find the sort menu item's view to anchor the popup to. findViewById on the
        // toolbar reliably returns the action view for a menu item.
        val anchor = binding.toolbar.findViewById<View>(R.id.action_sort) ?: binding.toolbar

        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.menu_sort)

        // Pre-check the currently-active order.
        val activeId = when (viewModel.sortOrder.value) {
            SortOrder.NEWEST_FIRST -> R.id.sort_newest_first
            SortOrder.OLDEST_FIRST -> R.id.sort_oldest_first
        }
        popup.menu.findItem(activeId)?.isChecked = true

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_newest_first -> {
                    viewModel.setSortOrder(SortOrder.NEWEST_FIRST)
                    true
                }
                R.id.sort_oldest_first -> {
                    viewModel.setSortOrder(SortOrder.OLDEST_FIRST)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Bulk-delete the currently-selected rows and show an Undo Snackbar.
     *
     * The VM returns the deleted notes synchronously (a snapshot taken before the
     * suspend). We hold onto that list so UNDO can call restoreAll() with the right
     * data.
     */
    private fun performBulkDelete() {
        val deleted = viewModel.deleteSelected()
        if (deleted.isEmpty()) return

        val message = resources.getQuantityString(
            R.plurals.notes_deleted_count,
            deleted.size,
            deleted.size,
        )
        Snackbar
            .make(binding.coordinator, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) { viewModel.restoreAll(deleted) }
            .show()
    }

    /** Show "Note deleted | UNDO" after a single swipe-to-delete. */
    private fun showSingleUndoSnackbar(deleted: Note) {
        Snackbar
            .make(binding.coordinator, R.string.note_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) { viewModel.restore(deleted) }
            .show()
    }

    // ----- Navigation -----

    /**
     * Launch the editor.
     *
     * @param noteId Pass null to create a new note. Pass an existing id to edit it.
     */
    private fun openEditor(noteId: Long?) {
        val intent = Intent(this, NoteEditorActivity::class.java)
        if (noteId != null) {
            intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId)
        }
        startActivity(intent)
    }
}

package com.example.projectophelia.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projectophelia.NotesApplication
import com.example.projectophelia.R
import com.example.projectophelia.databinding.ActivityNoteEditorBinding
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Editor screen.
 *
 * Reused for both *creating* a new note and *editing* an existing one.
 * - Create mode: launched without an id extra; the toolbar shows "New note".
 * - Edit mode:   launched with EXTRA_NOTE_ID; toolbar shows "Edit note", Delete is shown.
 *
 * The system back gesture and the toolbar's back arrow both save before exiting.
 * The Save action does the same. Delete drops the note and exits.
 *
 * Edge-to-edge:
 *  - We pad the AppBar by the status-bar inset (top).
 *  - We pad the content area by max(navigation-bar, IME) inset (bottom). When the
 *    keyboard is up, the IME inset wins; when it's down, the gesture-bar inset takes
 *    over. windowSoftInputMode="adjustResize" in the manifest cooperates with this.
 */
class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding

    private val viewModel: NoteEditorViewModel by viewModels {
        val repo = (applicationContext as NotesApplication).repository
        NoteEditorViewModel.Factory(repo)
    }

    /** True when the screen represents a brand-new (unsaved) note. Drives UI. */
    private var isNewNote: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Same as MainActivity: switch to edge-to-edge before inflating content.
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read the optional id extra. Default to NEW_NOTE_ID for create mode.
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, NoteEditorViewModel.NEW_NOTE_ID)
        isNewNote = noteId == NoteEditorViewModel.NEW_NOTE_ID

        applyWindowInsets()
        setUpToolbar()
        setUpBackHandling()

        // Tell the VM what to load. Idempotent across config changes.
        viewModel.load(noteId)

        observeViewModel()
    }

    /**
     * Inset handling for the editor — see MainActivity.applyWindowInsets() for the
     * general approach. The editor differs in one place: its content area also reacts
     * to the IME (keyboard) inset, so the cursor stays visible while typing.
     */
    private fun applyWindowInsets() {
        // ---- AppBar: pad top by the status-bar height ----
        val appBarInitialTopPadding = binding.appBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = appBarInitialTopPadding + sys.top)
            insets
        }

        // ---- Content: pad bottom by max(nav-bar, keyboard) inset ----
        val contentInitialBottomPadding = binding.editorContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.editorContent) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // Pick whichever is bigger so we never end up with overlapping bars.
            // When the IME is closed, ime.bottom == 0 → nav-bar inset wins.
            // When the IME is open, it usually fully covers the nav bar → IME wins.
            val bottomInset = max(sys.bottom, ime.bottom)
            v.updatePadding(bottom = contentInitialBottomPadding + bottomInset)
            insets
        }
    }

    /**
     * Configure the toolbar:
     *  - Title reflects mode.
     *  - Back arrow saves and finishes.
     *  - Menu actions: Save / Delete.
     *  - Hide Delete when creating a brand-new note (nothing to delete).
     */
    private fun setUpToolbar() {
        binding.toolbar.title = getString(
            if (isNewNote) R.string.title_new_note else R.string.title_edit_note,
        )

        // Toolbar's nav icon is the "<" arrow. Tap = save and exit (same as back gesture).
        binding.toolbar.setNavigationOnClickListener { saveAndExit() }

        // Hide the Delete action when there's nothing on disk yet.
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = !isNewNote

        // Wire up menu actions.
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    saveAndExit()
                    true
                }
                R.id.action_delete -> {
                    viewModel.delete()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Hooks the system back press (gesture or button) so it also saves before exiting.
     *
     * `onBackPressed()` is deprecated; the recommended replacement is registering an
     * `OnBackPressedCallback` with the activity's dispatcher. The callback wins over
     * the default finish() behavior.
     */
    private fun setUpBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveAndExit()
                }
            },
        )
    }

    /** Reads the current field values, asks the VM to persist them. */
    private fun saveAndExit() {
        viewModel.save(
            title = binding.titleField.text?.toString().orEmpty(),
            content = binding.contentField.text?.toString().orEmpty(),
        )
    }

    /**
     * Observe two flows from the ViewModel:
     *  1. The note being edited — populate the fields when it first loads.
     *  2. One-shot events (Closed) — finish the activity.
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Local guard so we only populate the fields once, instead of every
                // time the StateFlow re-emits (which would clobber the user's edits).
                var populated = false

                launch {
                    viewModel.note.collect { note ->
                        if (note != null && !populated) {
                            // setText only when we have something to set — avoids
                            // replacing a freshly typed character with the same value.
                            if (binding.titleField.text.isNullOrEmpty()) {
                                binding.titleField.setText(note.title)
                            }
                            if (binding.contentField.text.isNullOrEmpty()) {
                                binding.contentField.setText(note.content)
                            }
                            // If we landed on a row that *did* exist, bring the Delete
                            // action back even though the activity launched in "new"
                            // mode (rare path, but cheap to handle).
                            if (note.id != 0L) {
                                binding.toolbar.menu.findItem(R.id.action_delete)
                                    ?.isVisible = true
                                binding.toolbar.title = getString(R.string.title_edit_note)
                            }
                            populated = true
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        if (event is NoteEditorViewModel.Event.Closed) {
                            viewModel.consumeEvent()
                            finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Intent extra key for the id of an existing note to edit. */
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}

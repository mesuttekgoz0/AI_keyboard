package com.fraunhofer.aikeyboard2

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fraunhofer.aikeyboard2.data.ShortcutRepository
import com.fraunhofer.aikeyboard2.data.WordRepository
import com.fraunhofer.aikeyboard2.filter.ProfanityFilter
import com.fraunhofer.aikeyboard2.ui.FilterWordsAdapter
import com.fraunhofer.aikeyboard2.ui.ShortcutAdapter

/**
 * Uygulama ana ekranı — üç bölüm:
 *  1. Klavye kurulum rehberi
 *  2. Filtre yönetimi (yasaklı kelimeler)
 *  3. Kısayol yönetimi (Fn + harf → aksiyon)
 */
class MainActivity : AppCompatActivity() {

    // ── Repository'ler ────────────────────────────────────────────────
    private lateinit var wordRepository: WordRepository
    private lateinit var shortcutRepository: ShortcutRepository

    // ── Filter views ──────────────────────────────────────────────────
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvWordCount: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var etNewWord: EditText
    private lateinit var rvWords: RecyclerView
    private lateinit var filterAdapter: FilterWordsAdapter

    // ── Shortcut views ────────────────────────────────────────────────
    private lateinit var tvShortcutCount: TextView
    private lateinit var tvShortcutEmpty: TextView
    private lateinit var rvShortcuts: RecyclerView
    private lateinit var shortcutAdapter: ShortcutAdapter

    // ─────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordRepository     = WordRepository(this)
        shortcutRepository = ShortcutRepository(this)

        setupFilterSection()
        setupShortcutSection()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // ── Klavye durumu ──────────────────────────────────────────────────

    private fun updateStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        if (enabled) {
            tvStatusBadge.text = "✅ Etkin"
            tvStatusBadge.setTextColor(0xFF4CAF50.toInt())
        } else {
            tvStatusBadge.text = "⚠️ Pasif"
            tvStatusBadge.setTextColor(0xFFFF9800.toInt())
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // FİLTRE BÖLÜMÜ
    // ══════════════════════════════════════════════════════════════════

    private fun setupFilterSection() {
        tvStatusBadge = findViewById(R.id.tv_status_badge)
        tvWordCount   = findViewById(R.id.tv_word_count)
        tvEmptyState  = findViewById(R.id.tv_empty_state)
        etNewWord     = findViewById(R.id.et_new_word)
        rvWords       = findViewById(R.id.rv_words)

        etNewWord.setHintTextColor(0xFF4A4B50.toInt())

        val btnEnable  = findViewById<Button>(R.id.btn_enable_keyboard)
        val btnSelect  = findViewById<Button>(R.id.btn_select_keyboard)
        val btnAddWord = findViewById<Button>(R.id.btn_add_word)

        filterAdapter = FilterWordsAdapter(onDelete = { word -> deleteWord(word) })
        rvWords.layoutManager = LinearLayoutManager(this)
        rvWords.adapter = filterAdapter

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        btnSelect.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // Otomatik Düzeltme toggle
        val switchAutocorrect = findViewById<SwitchCompat>(R.id.switch_autocorrect)
        val keyboardPrefs = getSharedPreferences("keyboard_settings", MODE_PRIVATE)
        switchAutocorrect.isChecked = keyboardPrefs.getBoolean("autocorrect_enabled", true)
        switchAutocorrect.setOnCheckedChangeListener { _, isChecked ->
            keyboardPrefs.edit().putBoolean("autocorrect_enabled", isChecked).apply()
            val state = if (isChecked) "Otomatik Düzeltme Açık" else "Otomatik Düzeltme Kapalı"
            Toast.makeText(this, state, Toast.LENGTH_SHORT).show()
        }

        btnAddWord.setOnClickListener { addWordFromInput() }
        etNewWord.setOnEditorActionListener { _, _, _ ->
            addWordFromInput()
            true
        }

        loadWords()
    }

    private fun loadWords() {
        val words = wordRepository.getWordSet().toList()
        filterAdapter.submitList(words)
        refreshWordCount(filterAdapter.getCount())
        ProfanityFilter.loadFromRepository(this)
    }

    private fun addWordFromInput() {
        val word = etNewWord.text.toString().trim().lowercase()
        if (word.isEmpty()) { etNewWord.error = "Kelime boş olamaz"; return }
        if (wordRepository.getWordSet().contains(word)) {
            Toast.makeText(this, "\"$word\" zaten listede", Toast.LENGTH_SHORT).show()
            etNewWord.text.clear(); return
        }
        wordRepository.addWord(word)
        filterAdapter.addItem(word)
        etNewWord.text.clear()
        etNewWord.clearFocus()
        refreshWordCount(filterAdapter.getCount())
        ProfanityFilter.loadFromRepository(this)
        Toast.makeText(this, "\"$word\" eklendi ✓", Toast.LENGTH_SHORT).show()
    }

    private fun deleteWord(word: String) {
        wordRepository.removeWord(word)
        filterAdapter.removeItem(word)
        refreshWordCount(filterAdapter.getCount())
        ProfanityFilter.loadFromRepository(this)
        Toast.makeText(this, "\"$word\" silindi", Toast.LENGTH_SHORT).show()
    }

    private fun refreshWordCount(count: Int) {
        tvWordCount.text = "$count kelime"
        tvEmptyState.visibility = if (count == 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ══════════════════════════════════════════════════════════════════
    // KISAYOL BÖLÜMÜ
    // ══════════════════════════════════════════════════════════════════

    private fun setupShortcutSection() {
        tvShortcutCount = findViewById(R.id.tv_shortcut_count)
        tvShortcutEmpty = findViewById(R.id.tv_shortcut_empty)
        rvShortcuts     = findViewById(R.id.rv_shortcuts)

        shortcutAdapter = ShortcutAdapter(onDelete = { shortcut -> deleteShortcut(shortcut) })
        rvShortcuts.layoutManager = LinearLayoutManager(this)
        rvShortcuts.adapter = shortcutAdapter

        findViewById<Button>(R.id.btn_add_shortcut).setOnClickListener {
            showAddShortcutDialog()
        }

        loadShortcuts()
    }

    private fun loadShortcuts() {
        val shortcuts = shortcutRepository.getAll().values
        shortcutAdapter.submitList(shortcuts)
        refreshShortcutCount(shortcutAdapter.getCount())
    }

    private fun deleteShortcut(shortcut: ShortcutRepository.Shortcut) {
        shortcutRepository.remove(shortcut.key)
        shortcutAdapter.removeItem(shortcut)
        refreshShortcutCount(shortcutAdapter.getCount())
        Toast.makeText(this, "Fn+${shortcut.key.uppercaseChar()} silindi", Toast.LENGTH_SHORT).show()
    }

    private fun refreshShortcutCount(count: Int) {
        tvShortcutCount.text = "$count kısayol"
        tvShortcutEmpty.visibility = if (count == 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ── Kısayol Ekleme Dialogu ────────────────────────────────────────

    private fun showAddShortcutDialog() {
        val dialog = Dialog(this, R.style.Theme_AIKeyboard2)
        dialog.setContentView(R.layout.dialog_add_shortcut)
        dialog.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val spinner    = dialog.findViewById<Spinner>(R.id.spinner_key)
        val rgAction   = dialog.findViewById<RadioGroup>(R.id.rg_action)
        val rbTypeText = dialog.findViewById<RadioButton>(R.id.rb_type_text)
        val etText     = dialog.findViewById<EditText>(R.id.et_type_text)
        val btnCancel  = dialog.findViewById<Button>(R.id.btn_cancel)
        val btnSave    = dialog.findViewById<Button>(R.id.btn_save_shortcut)

        etText.setHintTextColor(0xFF4A4B50.toInt())

        // Harf spinner'ı doldur (a-z, mevcut kısayollar italik gösterilebilir)
        val letters = ('a'..'z').map { it.toString().uppercase() }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, letters)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        // "Metin Yaz" seçilince EditText görünür
        rgAction.setOnCheckedChangeListener { _, checkedId ->
            etText.visibility = if (checkedId == R.id.rb_type_text) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val selectedKey   = ('a' + spinner.selectedItemPosition)
            val selectedAction = when (rgAction.checkedRadioButtonId) {
                R.id.rb_select_all -> ShortcutRepository.ActionType.SELECT_ALL
                R.id.rb_copy       -> ShortcutRepository.ActionType.COPY
                R.id.rb_paste      -> ShortcutRepository.ActionType.PASTE
                R.id.rb_type_text  -> ShortcutRepository.ActionType.TYPE_TEXT
                else               -> null
            }

            if (selectedAction == null) {
                Toast.makeText(this, "Bir aksiyon seç", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedAction == ShortcutRepository.ActionType.TYPE_TEXT) {
                val text = etText.text.toString().trim()
                if (text.isEmpty()) {
                    etText.error = "Metin boş olamaz"
                    return@setOnClickListener
                }
            }

            val shortcut = ShortcutRepository.Shortcut(
                key    = selectedKey,
                action = selectedAction,
                text   = if (selectedAction == ShortcutRepository.ActionType.TYPE_TEXT)
                             etText.text.toString().trim()
                         else ""
            )

            shortcutRepository.save(shortcut)
            shortcutAdapter.addItem(shortcut)
            refreshShortcutCount(shortcutAdapter.getCount())

            dialog.dismiss()
            Toast.makeText(
                this,
                "Fn+${selectedKey.uppercaseChar()} → ${shortcut.actionLabel()} kaydedildi",
                Toast.LENGTH_SHORT
            ).show()
        }

        dialog.show()
    }
}
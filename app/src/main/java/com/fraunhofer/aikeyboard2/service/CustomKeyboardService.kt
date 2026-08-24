package com.fraunhofer.aikeyboard2.service

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.fraunhofer.aikeyboard2.R
import com.fraunhofer.aikeyboard2.autocomplete.AutoCorrectEngine
import com.fraunhofer.aikeyboard2.filter.ProfanityFilter

/**
 * GBoard Birebir Tema, Ultra-Düşük Gecikme (0ms Latency), Dinamik Boyutlandırma
 * ve Canlı Otomatik Düzeltme / Öneri Şeridi Servisi.
 */
class CustomKeyboardService : InputMethodService() {

    // ─────────────────────────────────────────────────────────────────
    // Renk ve Grafik Sabitleri (GBoard Palette)
    // ─────────────────────────────────────────────────────────────────

    private val COLOR_TEXT_KEY     = Color.parseColor("#E3E2E6")
    private val COLOR_TEXT_SPECIAL = Color.parseColor("#C4C6D0")
    private val COLOR_TEXT_DARK    = Color.parseColor("#041E49")
    private val COLOR_FN_ACTIVE    = Color.parseColor("#A8C7FA")
    private val COLOR_FN_BG_ACTIVE = ColorStateList.valueOf(COLOR_FN_ACTIVE)
    private val COLOR_FN_BG_OFF    = ColorStateList.valueOf(Color.parseColor("#303134"))

    // ─────────────────────────────────────────────────────────────────
    // Klavye Durumu & Boyut Ayarları
    // ─────────────────────────────────────────────────────────────────

    private val wordBuffer = StringBuilder(32)
    private var isFnActive = false

    /**
     * Shift modu:
     *  0 → küçük harf
     *  1 → tek büyük (1 harf sonra sıfır)
     *  2 → CAPS LOCK (çift tık veya uzun basış)
     */
    private var shiftMode = 0
    private var lastShiftPressTime = 0L

    // Boyutlandırma (SharedPreferences)
    private val DEFAULT_KEY_HEIGHT_DP = 48
    private var currentKeyHeightDp = DEFAULT_KEY_HEIGHT_DP
    private lateinit var prefs: SharedPreferences

    // View Referansları
    private lateinit var btnShift: Button
    private lateinit var btnFn: Button
    private val letterButtons = HashMap<Int, Button>(32)

    // Öneri Şeridi Referansları
    private var toolbarDefault: View? = null
    private var toolbarSuggestions: View? = null
    private var btnSuggest1: Button? = null
    private var btnSuggest2: Button? = null
    private var btnSuggest3: Button? = null

    // Engellenen Kelime Bildirimi
    private var toolbarBlocked: View? = null
    private var tvBlockedMessage: TextView? = null
    private val blockedHandler = Handler(Looper.getMainLooper())

    // Cached Drawables
    private lateinit var drawableSpecial: Drawable
    private lateinit var drawableShiftActive: Drawable

    // Uzun basış silme Handler
    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            performDelete()
            deleteHandler.postDelayed(this, 50L)
        }
    }

    // Sayı Tuşları Haritası
    private val numKeyMap: Map<Int, String> by lazy {
        mapOf(
            R.id.btn_1 to "1", R.id.btn_2 to "2", R.id.btn_3 to "3",
            R.id.btn_4 to "4", R.id.btn_5 to "5", R.id.btn_6 to "6",
            R.id.btn_7 to "7", R.id.btn_8 to "8", R.id.btn_9 to "9",
            R.id.btn_0 to "0"
        )
    }

    // Sembol Haritası (Lazy Initialized)
    private val symKeyMap: Map<Int, String> by lazy {
        mapOf(
            R.id.sym_1     to "1", R.id.sym_2    to "2", R.id.sym_3    to "3",
            R.id.sym_4     to "4", R.id.sym_5    to "5", R.id.sym_6    to "6",
            R.id.sym_7     to "7", R.id.sym_8    to "8", R.id.sym_9    to "9",
            R.id.sym_0     to "0",
            R.id.sym_excl  to "!", R.id.sym_at   to "@", R.id.sym_hash to "#",
            R.id.sym_dol   to "$", R.id.sym_pct  to "%", R.id.sym_amp  to "&",
            R.id.sym_star  to "*", R.id.sym_lpar to "(",  R.id.sym_rpar to ")",
            R.id.sym_under to "_",
            R.id.sym_minus to "-", R.id.sym_plus  to "+", R.id.sym_eq   to "=",
            R.id.sym_slash to "/", R.id.sym_colon to ":", R.id.sym_semi to ";",
            R.id.sym_apos  to "'", R.id.sym_quot  to "\""
        )
    }

    // Harf Tablosu (Tam Türkçe Q - GBoard Dizilimi)
    private val letterMap: Map<Int, Pair<String, String>> by lazy {
        mapOf(
            R.id.btn_q  to ("q" to "Q"), R.id.btn_w  to ("w" to "W"),
            R.id.btn_e  to ("e" to "E"), R.id.btn_r  to ("r" to "R"),
            R.id.btn_t  to ("t" to "T"), R.id.btn_y  to ("y" to "Y"),
            R.id.btn_u  to ("u" to "U"), R.id.btn_i  to ("ı" to "I"),
            R.id.btn_o  to ("o" to "O"), R.id.btn_p  to ("p" to "P"),
            R.id.btn_gh to ("ğ" to "Ğ"), R.id.btn_uu to ("ü" to "Ü"),
            R.id.btn_a  to ("a" to "A"), R.id.btn_s  to ("s" to "S"),
            R.id.btn_d  to ("d" to "D"), R.id.btn_f  to ("f" to "F"),
            R.id.btn_g  to ("g" to "G"), R.id.btn_h  to ("h" to "H"),
            R.id.btn_j  to ("j" to "J"), R.id.btn_k  to ("k" to "K"),
            R.id.btn_l  to ("l" to "L"), R.id.btn_sh to ("ş" to "Ş"),
            R.id.btn_ii to ("i" to "İ"), R.id.btn_z  to ("z" to "Z"),
            R.id.btn_x  to ("x" to "X"), R.id.btn_c  to ("c" to "C"),
            R.id.btn_v  to ("v" to "V"), R.id.btn_b  to ("b" to "B"),
            R.id.btn_n  to ("n" to "N"), R.id.btn_m  to ("m" to "M"),
            R.id.btn_oe to ("ö" to "Ö"), R.id.btn_ch to ("ç" to "Ç")
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        currentKeyHeightDp = prefs.getInt("key_height_dp", DEFAULT_KEY_HEIGHT_DP)
        drawableSpecial     = resources.getDrawable(R.drawable.bg_key_special, null)
        drawableShiftActive = resources.getDrawable(R.drawable.bg_key_shift_active, null)
        ProfanityFilter.loadFromRepository(this)
        AutoCorrectEngine.loadDictionary(this)
    }

    override fun onCreateInputView(): View = buildLetterView()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ProfanityFilter.loadFromRepository(this)
        AutoCorrectEngine.loadDictionary(this)
        if (isFnActive) { isFnActive = false; updateFnVisual() }
        if (shiftMode != 0) { shiftMode = 0; updateShiftVisual() }
        wordBuffer.clear()
        updateSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        deleteHandler.removeCallbacks(deleteRunnable)
        blockedHandler.removeCallbacksAndMessages(null)
    }

    // ─────────────────────────────────────────────────────────────────
    // Anında (ACTION_DOWN) Dokunma Yardımcısı
    // ─────────────────────────────────────────────────────────────────

    private fun bindInstantTouch(btn: Button?, action: () -> Unit) {
        btn ?: return
        btn.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    action()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Harf Klavyesi Görünümü
    // ─────────────────────────────────────────────────────────────────

    private fun buildLetterView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        btnShift = view.findViewById(R.id.btn_shift)
        btnFn    = view.findViewById(R.id.btn_fn)

        // Öneri Şeridi Referansları
        toolbarDefault     = view.findViewById(R.id.toolbar_default)
        toolbarSuggestions = view.findViewById(R.id.toolbar_suggestions)
        btnSuggest1        = view.findViewById(R.id.btn_suggest_1)
        btnSuggest2        = view.findViewById(R.id.btn_suggest_2)
        btnSuggest3        = view.findViewById(R.id.btn_suggest_3)

        // Engellenen Kelime Bildirimi
        toolbarBlocked   = view.findViewById(R.id.toolbar_blocked)
        tvBlockedMessage = view.findViewById(R.id.tv_blocked_message)

        // Öneri Tuşları Dokunmaları
        bindInstantTouch(btnSuggest1) { commitSuggestion(btnSuggest1?.text.toString()) }
        bindInstantTouch(btnSuggest2) { commitSuggestion(btnSuggest2?.text.toString()) }
        bindInstantTouch(btnSuggest3) { commitSuggestion(btnSuggest3?.text.toString()) }

        // Sayı Satırı (1 2 3 4 5 6 7 8 9 0)
        numKeyMap.forEach { (id, char) ->
            bindInstantTouch(view.findViewById(id)) {
                currentInputConnection?.commitText(char, 1)
            }
        }

        // Harf Tuşları
        letterButtons.clear()
        letterMap.forEach { (id, chars) ->
            view.findViewById<Button>(id)?.also { btn ->
                letterButtons[id] = btn
                bindInstantTouch(btn) {
                    handleCharacter(if (shiftMode > 0) chars.second else chars.first)
                }
            }
        }

        updateLetterLabels()
        updateShiftVisual()
        updateFnVisual()

        // Shift Tuşu
        bindInstantTouch(btnShift) {
            val now = System.currentTimeMillis()
            if (now - lastShiftPressTime < 300L) {
                shiftMode = 2 // Çift tık -> CAPS LOCK
            } else {
                shiftMode = if (shiftMode == 0) 1 else 0
            }
            lastShiftPressTime = now
            updateShiftVisual()
        }
        btnShift.setOnLongClickListener {
            shiftMode = 2 // Uzun basış -> CAPS LOCK
            updateShiftVisual()
            true
        }

        // Fn Tuşu
        bindInstantTouch(btnFn) {
            isFnActive = !isFnActive
            updateFnVisual()
        }

        // Temel Kontrol Tuşları
        bindInstantTouch(view.findViewById(R.id.btn_space))  { handleSpaceOrPunct(" ") }
        bindInstantTouch(view.findViewById(R.id.btn_comma))  { handleSpaceOrPunct(",") }
        bindInstantTouch(view.findViewById(R.id.btn_period)) { handleSpaceOrPunct(".") }
        bindInstantTouch(view.findViewById(R.id.btn_enter))  { handleEnter() }
        bindInstantTouch(view.findViewById(R.id.btn_undo))   {
            currentInputConnection?.performContextMenuAction(android.R.id.undo)
        }

        // Sil Tuşu
        setupDeleteTouch(view.findViewById(R.id.btn_delete))

        // Sembol / AI Tuşları
        bindInstantTouch(view.findViewById(R.id.btn_sym)) {
            setInputView(buildSymbolView())
        }
        bindInstantTouch(view.findViewById(R.id.btn_ai)) { triggerAI() }

        // Boyutlandırma Menüsü Kontrolleri
        setupSizeControlPanel(view)

        // Mevcut klavye yüksekliğini uygula
        applyKeyHeight(view, currentKeyHeightDp)

        applyNavBarPadding(view, view.findViewById(R.id.nav_bar_spacer))
        return view
    }

    // ─────────────────────────────────────────────────────────────────
    // Sembol Klavyesi Görünümü
    // ─────────────────────────────────────────────────────────────────

    private fun buildSymbolView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_symbols_view, null)

        symKeyMap.forEach { (id, char) ->
            bindInstantTouch(view.findViewById(id)) {
                currentInputConnection?.commitText(char, 1)
            }
        }

        setupDeleteTouch(view.findViewById(R.id.sym_delete))

        bindInstantTouch(view.findViewById(R.id.sym_abc)) {
            setInputView(buildLetterView())
        }
        bindInstantTouch(view.findViewById(R.id.sym_space))  { handleSpaceOrPunct(" ") }
        bindInstantTouch(view.findViewById(R.id.sym_comma))  { handleSpaceOrPunct(",") }
        bindInstantTouch(view.findViewById(R.id.sym_period)) { handleSpaceOrPunct(".") }
        bindInstantTouch(view.findViewById(R.id.sym_enter))  { handleEnter() }
        bindInstantTouch(view.findViewById(R.id.sym_btn_ai)) { triggerAI() }

        applyKeyHeight(view, currentKeyHeightDp)
        applyNavBarPadding(view, view.findViewById(R.id.sym_nav_bar_spacer))
        return view
    }

    // ─────────────────────────────────────────────────────────────────
    // Canlı Öneri ve Otomatik Düzeltme Mantığı
    // ─────────────────────────────────────────────────────────────────

    /**
     * Mevcut wordBuffer kelimesine göre 3 slotlu canlı önerileri günceller.
     */
    private fun updateSuggestions() {
        val currentWord = wordBuffer.toString().trim()
        if (currentWord.isNotEmpty()) {
            val suggestions = AutoCorrectEngine.getSuggestions(currentWord, 3)
            if (suggestions.isNotEmpty()) {
                toolbarDefault?.visibility     = View.GONE
                toolbarSuggestions?.visibility = View.VISIBLE

                val s1 = suggestions.getOrNull(0) ?: ""
                val s2 = suggestions.getOrNull(1) ?: currentWord
                val s3 = suggestions.getOrNull(2) ?: ""

                btnSuggest1?.text = s1
                btnSuggest2?.text = s2
                btnSuggest3?.text = s3

                btnSuggest1?.visibility = if (s1.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                btnSuggest2?.visibility = View.VISIBLE
                btnSuggest3?.visibility = if (s3.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                return
            }
        }

        toolbarSuggestions?.visibility = View.GONE
        toolbarDefault?.visibility     = View.VISIBLE
    }

    /**
     * Öneri şeridindeki bir kelimeye dokunulduğunda çalışır.
     */
    private fun commitSuggestion(chosenWord: String) {
        if (chosenWord.isEmpty()) return
        val ic = currentInputConnection ?: return

        if (wordBuffer.isNotEmpty()) {
            ic.deleteSurroundingText(wordBuffer.length, 0)
        }
        ic.commitText("$chosenWord ", 1)
        wordBuffer.clear()
        updateSuggestions()

        if (shiftMode == 1) {
            shiftMode = 0
            updateShiftVisual()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Klavye Boyutlandırma Mantığı (Resizing Control)
    // ─────────────────────────────────────────────────────────────────

    private fun setupSizeControlPanel(view: View) {
        val tbDefault          = view.findViewById<View>(R.id.toolbar_default) ?: return
        val toolbarSizeControl = view.findViewById<View>(R.id.toolbar_size_control) ?: return
        val btnMenu            = view.findViewById<Button>(R.id.btn_menu)
        val btnMinus           = view.findViewById<Button>(R.id.btn_size_minus)
        val btnPlus            = view.findViewById<Button>(R.id.btn_size_plus)
        val btnClose           = view.findViewById<Button>(R.id.btn_size_close)
        val tvPercent          = view.findViewById<TextView>(R.id.tv_size_percent)

        fun updatePercentText() {
            val pct = (currentKeyHeightDp * 100) / DEFAULT_KEY_HEIGHT_DP
            tvPercent?.text = "%$pct"
        }

        bindInstantTouch(btnMenu) {
            tbDefault.visibility          = View.GONE
            toolbarSuggestions?.visibility = View.GONE
            toolbarSizeControl.visibility = View.VISIBLE
            updatePercentText()
        }

        bindInstantTouch(btnMinus) {
            if (currentKeyHeightDp > 36) {
                currentKeyHeightDp -= 3
                prefs.edit().putInt("key_height_dp", currentKeyHeightDp).apply()
                applyKeyHeight(view, currentKeyHeightDp)
                updatePercentText()
            }
        }

        bindInstantTouch(btnPlus) {
            if (currentKeyHeightDp < 64) {
                currentKeyHeightDp += 3
                prefs.edit().putInt("key_height_dp", currentKeyHeightDp).apply()
                applyKeyHeight(view, currentKeyHeightDp)
                updatePercentText()
            }
        }

        bindInstantTouch(btnClose) {
            toolbarSizeControl.visibility = View.GONE
            updateSuggestions()
        }
    }

    private fun applyKeyHeight(rootView: View, heightDp: Int) {
        val heightPx = (heightDp * resources.displayMetrics.density).toInt()

        val keyIds = intArrayOf(
            R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9, R.id.btn_0,
            R.id.btn_q, R.id.btn_w, R.id.btn_e, R.id.btn_r, R.id.btn_t, R.id.btn_y, R.id.btn_u, R.id.btn_i, R.id.btn_o, R.id.btn_p, R.id.btn_gh, R.id.btn_uu,
            R.id.btn_a, R.id.btn_s, R.id.btn_d, R.id.btn_f, R.id.btn_g, R.id.btn_h, R.id.btn_j, R.id.btn_k, R.id.btn_l, R.id.btn_sh, R.id.btn_ii,
            R.id.btn_shift, R.id.btn_z, R.id.btn_x, R.id.btn_c, R.id.btn_v, R.id.btn_b, R.id.btn_n, R.id.btn_m, R.id.btn_oe, R.id.btn_ch, R.id.btn_delete,
            R.id.btn_sym, R.id.btn_comma, R.id.btn_fn, R.id.btn_space, R.id.btn_period, R.id.btn_enter,
            R.id.sym_1, R.id.sym_2, R.id.sym_3, R.id.sym_4, R.id.sym_5, R.id.sym_6, R.id.sym_7, R.id.sym_8, R.id.sym_9, R.id.sym_0,
            R.id.sym_excl, R.id.sym_at, R.id.sym_hash, R.id.sym_dol, R.id.sym_pct, R.id.sym_amp, R.id.sym_star, R.id.sym_lpar, R.id.sym_rpar, R.id.sym_under,
            R.id.sym_more, R.id.sym_minus, R.id.sym_plus, R.id.sym_eq, R.id.sym_slash, R.id.sym_colon, R.id.sym_semi, R.id.sym_apos, R.id.sym_quot, R.id.sym_delete,
            R.id.sym_abc, R.id.sym_comma, R.id.sym_space, R.id.sym_period, R.id.sym_enter
        )

        for (id in keyIds) {
            val btn = rootView.findViewById<View>(id) ?: continue
            val lp = btn.layoutParams
            if (lp != null && lp.height != heightPx) {
                lp.height = heightPx
                btn.layoutParams = lp
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Sil Tuşu Mantığı
    // ─────────────────────────────────────────────────────────────────

    private fun setupDeleteTouch(btn: Button?) {
        btn ?: return
        btn.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    performDelete()
                    deleteHandler.postDelayed(deleteRunnable, 300L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    deleteHandler.removeCallbacks(deleteRunnable)
                    true
                }
                else -> false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Görsel Güncellemeler
    // ─────────────────────────────────────────────────────────────────

    private fun updateShiftVisual() {
        if (!::btnShift.isInitialized) return
        when (shiftMode) {
            0 -> {
                btnShift.background = drawableSpecial
                btnShift.setTextColor(COLOR_TEXT_SPECIAL)
                btnShift.text = "⇧"
            }
            1 -> {
                btnShift.background = drawableShiftActive
                btnShift.setTextColor(COLOR_TEXT_DARK)
                btnShift.text = "⇧"
            }
            2 -> {
                btnShift.background = drawableShiftActive
                btnShift.setTextColor(COLOR_TEXT_DARK)
                btnShift.text = "⇪"
            }
        }
        updateLetterLabels()
    }

    private fun updateFnVisual() {
        if (!::btnFn.isInitialized) return
        btnFn.backgroundTintList = if (isFnActive) COLOR_FN_BG_ACTIVE else COLOR_FN_BG_OFF
        btnFn.setTextColor(if (isFnActive) COLOR_TEXT_DARK else COLOR_TEXT_SPECIAL)
    }

    private fun updateLetterLabels() {
        val upper = shiftMode > 0
        letterMap.forEach { (id, chars) ->
            letterButtons[id]?.text = if (upper) chars.second else chars.first
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Navigasyon Çubuğu Dolgusu
    // ─────────────────────────────────────────────────────────────────

    private val navBarHeight: Int by lazy {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun applyNavBarPadding(rootView: View, spacer: View?) {
        spacer ?: return
        rootView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val h = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        rootView.rootWindowInsets
                            ?.getInsets(android.view.WindowInsets.Type.navigationBars())
                            ?.bottom ?: navBarHeight
                    } else navBarHeight
                    spacer.layoutParams = spacer.layoutParams.also { it.height = h }
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Girdi İşleyicileri
    // ─────────────────────────────────────────────────────────────────

    private fun handleCharacter(text: String) {
        if (isFnActive) {
            val key = text[0].lowercaseChar()
            val shortcut = com.fraunhofer.aikeyboard2.data.ShortcutRepository(this).get(key)
            currentInputConnection?.let { ic ->
                when (shortcut?.action) {
                    com.fraunhofer.aikeyboard2.data.ShortcutRepository.ActionType.SELECT_ALL ->
                        ic.performContextMenuAction(android.R.id.selectAll)
                    com.fraunhofer.aikeyboard2.data.ShortcutRepository.ActionType.COPY ->
                        ic.performContextMenuAction(android.R.id.copy)
                    com.fraunhofer.aikeyboard2.data.ShortcutRepository.ActionType.PASTE ->
                        ic.performContextMenuAction(android.R.id.paste)
                    com.fraunhofer.aikeyboard2.data.ShortcutRepository.ActionType.TYPE_TEXT -> {
                        val textToInsert = shortcut.text
                        if (textToInsert.isNotEmpty()) ic.commitText(textToInsert, 1)
                    }
                    null -> { /* Tanımsız kısayol — yoksay */ }
                }
            }
            isFnActive = false
            updateFnVisual()
            wordBuffer.clear()
            updateSuggestions()
            return
        }

        wordBuffer.append(text)
        currentInputConnection?.commitText(text, 1)
        updateSuggestions()

        if (shiftMode == 1) {
            shiftMode = 0
            updateShiftVisual()
        }
    }

    private fun handleSpaceOrPunct(text: String) {
        val ic = currentInputConnection ?: return
        val word = wordBuffer.toString().trim()

        if (word.isNotEmpty()) {
            if (ProfanityFilter.isProfane(word)) {
                // Kelimeyi tamamen sil — boşluk bırak, *** koyma
                ic.deleteSurroundingText(word.length, 0)
                // Sonrasındaki boşluk/noktalama da commit edilmesin (sadece kelime silindi)
                wordBuffer.clear()
                updateSuggestions()
                showBlockedBanner(word)
                if (isFnActive) { isFnActive = false; updateFnVisual() }
                return
            } else {
                // Otomatik Düzeltme (Auto-Correction):
                // Eğer ortadaki öneri (btnSuggest2) farklı bir kelime ise ve gösteriliyorsa otomatik tamamla
                val autocorrectEnabled = prefs.getBoolean("autocorrect_enabled", true)
                if (autocorrectEnabled) {
                    val autoCorrectCandidate = btnSuggest2?.text?.toString()?.trim() ?: ""
                    if (autoCorrectCandidate.isNotEmpty() &&
                        !autoCorrectCandidate.equals(word, ignoreCase = true) &&
                        toolbarSuggestions?.visibility == View.VISIBLE) {

                        ic.deleteSurroundingText(word.length, 0)
                        ic.commitText(autoCorrectCandidate, 1)
                    }
                }
            }
        }

        ic.commitText(text, 1)
        wordBuffer.clear()
        updateSuggestions()

        if (isFnActive) { isFnActive = false; updateFnVisual() }
    }

    /**
     * Klavye toolbar'ında 2 saniyelik engelleme bildirimi gösterir.
     * Toast değil — klavye içinde düz bir banner.
     */
    private fun showBlockedBanner(blockedWord: String) {
        val banner = toolbarBlocked ?: return
        val tv     = tvBlockedMessage ?: return

        // Önceki zamanlayıcıyı iptal et (art arda birden fazla kelime engellenirse)
        blockedHandler.removeCallbacksAndMessages(null)

        tv.text = "🚫  \"$blockedWord\" silindi"
        toolbarDefault?.visibility     = View.GONE
        toolbarSuggestions?.visibility = View.GONE
        banner.visibility              = View.VISIBLE

        blockedHandler.postDelayed({
            banner.visibility          = View.GONE
            updateSuggestions() // öneri şeridini ya da default toolbar'ı geri göster
        }, 2000L)
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        if (!ic.performEditorAction(EditorInfo.IME_ACTION_DONE))
            ic.commitText("\n", 1)
        wordBuffer.clear()
        updateSuggestions()
    }

    private fun performDelete() {
        val ic = currentInputConnection ?: return
        if (wordBuffer.isNotEmpty()) wordBuffer.deleteAt(wordBuffer.length - 1)
        ic.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    private fun deleteLastSentence(ic: android.view.inputmethod.InputConnection) {
        val before = ic.getTextBeforeCursor(1000, 0)?.takeIf { it.isNotEmpty() } ?: return
        val trimmed = before.trimEnd()
        var boundary = -1
        for (i in trimmed.length - 2 downTo 0) {
            val c = trimmed[i]
            if (c == '.' || c == '?' || c == '!') { boundary = i; break }
        }
        ic.deleteSurroundingText(
            if (boundary >= 0) before.length - (boundary + 1) else before.length, 0
        )
        wordBuffer.clear()
        updateSuggestions()
    }

    private fun triggerAI() {
        wordBuffer.clear()
        updateSuggestions()
        Toast.makeText(this, "✨ AI Düzelt tetiklendi!", Toast.LENGTH_SHORT).show()
        currentInputConnection?.commitText("[AI]", 1)
    }
}

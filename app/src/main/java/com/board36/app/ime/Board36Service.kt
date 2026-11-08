package com.board36.app.ime

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.board36.app.data.Prefs
import com.board36.app.data.UserData
import com.board36.app.engine.Emoji
import com.board36.app.engine.Fr
import com.board36.app.engine.FrenchDictionary
import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.KeyGeometry
import com.board36.app.engine.Suggester
import com.board36.app.engine.UserDictionary
import com.board36.app.engine.WordBlocklist
import com.board36.app.ui.MainActivity
import java.util.concurrent.Executors

/**
 * Service de saisie 36Board.
 *
 * Principe : le mot en cours est toujours une **zone de composition** dans le champ de
 * texte, qu'il vienne de la frappe ou d'un tracé. Le clavier peut ainsi le remplacer
 * d'un bloc (suggestion, correction, variante de swipe) sans devoir « rattraper » du
 * texte déjà validé. Quand le curseur bouge, le mot qui le touche est relu dans le
 * champ : les propositions repartent du mot entier, pas seulement des lettres tapées
 * depuis le dernier déplacement.
 */
open class Board36Service : InputMethodService(), KeyboardView.Listener,
    SuggestionStripView.Listener, EmojiPanelView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var userDict: UserDictionary
    private lateinit var blocklist: WordBlocklist
    private lateinit var suggester: Suggester
    private lateinit var decoder: GestureDecoder
    @Volatile private var dict: FrenchDictionary? = null

    private val bg = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var rootView: InputRootView? = null
    private var keyboardView: KeyboardView? = null
    private var emojiPanel: EmojiPanelView? = null
    private var strip: SuggestionStripView? = null
    private var overlay: PopupOverlayView? = null
    private var column: LinearLayout? = null

    /** Mot en cours de saisie, reflété dans la zone de composition du champ. */
    private val composing = StringBuilder()

    /** Mot qui touche le curseur quand rien n'est en composition (curseur déplacé). */
    private var contextWord = ""

    /** Mot précédent, relu dans le texte : sert aux prédictions et à l'apprentissage. */
    private var previousWord = ""

    /** Mot en cours issu d'un tracé, et ses variantes. */
    private var gestureWord: String? = null
    private var gestureAlternatives: List<String> = emptyList()

    /** Fin de la sélection, telle que rapportée par l'application : ancre de l'effacement. */
    private var selEnd = 0

    /** Balayage d'effacement en cours (ancre, longueur sélectionnée). */
    private var deleteAnchor = -1
    private var deleteSelected = 0
    /**
     * Texte précédant l'ancre, lu **une seule fois** au début du balayage : une fois la
     * sélection posée, `getTextBeforeCursor` ne renvoie plus que ce qui précède la
     * sélection, et les crans suivants porteraient sur le mauvais texte.
     */
    private var deleteBefore = ""

    private var composingAllowed = true
    /**
     * Champ mot de passe : rien de ce qui s'y tape ne doit se voir ni se retenir. Pas de
     * swipe (il insérait un mot du dictionnaire et une espace invisible), pas de lettres
     * colorées ni d'aperçu de touche (ils montraient le mot de passe), pas de « double
     * espace = point » (il modifiait le mot de passe sans que cela se voie).
     */
    private var sensitiveField = false
    /**
     * Le clavier peut-il retenir ce qui est tapé ici ? Non dans un champ mot de passe, un
     * champ qui refuse les propositions, ou quand l'application demande la navigation privée
     * (`IME_FLAG_NO_PERSONALIZED_LEARNING`). Le réglage « Apprendre mes mots » s'y ajoute.
     */
    private var learningAllowed = true
    /** Bande de suggestions autorisée dans ce champ (plus large que la composition). */
    private var suggestionsAllowed = true
    private var mode = MODE_LETTERS
    private var lastSpaceTime = 0L
    private var suggestionSeq = 0

    // ------------------------------------------------------------ cycle de vie

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        // mêmes instances que l'écran des réglages : voir UserData
        userDict = UserData.dictionary(this)
        blocklist = UserData.blocklist(this)
        suggester = Suggester(null, userDict, blocklist)
        decoder = GestureDecoder(null, userDict, blocklist)
        runBackground {
            decoder.setUserDictionary(userDict)
            val d = try {
                assets.open("fr_dict.txt").use { FrenchDictionary.load(it) }
            } catch (e: Exception) {
                null
            }
            dict = d
            suggester.setDictionary(d)
            decoder.setDictionary(d)
        }
    }

    override fun onDestroy() {
        runBackground { userDict.save() }
        super.onDestroy()
        bg.shutdown()
    }

    /** Point d'extension pour les tests : exécution hors du fil principal. */
    protected open fun runBackground(task: () -> Unit) {
        bg.execute { task() }
    }

    /** Point d'extension pour les tests : retour sur le fil principal. */
    protected open fun runMain(task: () -> Unit) {
        main.post { task() }
    }

    override fun onCreateInputView(): View {
        val theme = currentTheme()

        val kb = KeyboardView(this)
        kb.listener = this
        kb.theme = theme
        keyboardView = kb

        val ep = EmojiPanelView(this)
        ep.listener = this
        ep.theme = theme
        ep.setRecents(prefs.recentEmoji)
        ep.visibility = View.GONE
        emojiPanel = ep

        val st = SuggestionStripView(this)
        st.listener = this
        st.theme = theme
        strip = st

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(st, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, stripHeight()))
        col.addView(kb, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(ep, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column = col

        val ov = PopupOverlayView(this)
        ov.theme = theme
        overlay = ov

        val root = InputRootView(this)
        root.addView(col, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(ov, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootView = root

        applyPreferences()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetState()
        selEnd = info.initialSelEnd.coerceAtLeast(0)
        deleteAnchor = -1
        deleteSelected = 0
        suggestionsAllowed = allowsSuggestions(info)
        composingAllowed = suggestionsAllowed && allowsComposing(info)
        sensitiveField = isPasswordField(info)
        learningAllowed = suggestionsAllowed && !sensitiveField &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        // le mode est remis à zéro *avant* applyPreferences, qui reconstruit déjà la
        // disposition : sans cela elle était construite deux fois à chaque ouverture
        mode = MODE_LETTERS
        applyPreferences()
        updateShiftFromCaps()
        refreshContext()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        strip?.hideBlockPrompt()
        runBackground { userDict.save(); blocklist.save() }
    }

    private fun resetState() {
        composing.setLength(0)
        contextWord = ""
        previousWord = ""
        gestureWord = null
        gestureAlternatives = emptyList()
    }

    /** Champs sans aucune proposition : mots de passe, champs qui les refusent. */
    private fun allowsSuggestions(info: EditorInfo): Boolean {
        val type = info.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        return when (type and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            else -> true
        }
    }

    /** Champ mot de passe, en texte comme en chiffres (code PIN). */
    private fun isPasswordField(info: EditorInfo): Boolean {
        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Apprentissage permis ici et voulu par l'utilisateur. */
    private fun mayLearn(): Boolean = learningAllowed && prefs.learnWords

    /**
     * Champs où l'on ne compose pas : barre d'adresse, e-mail. On y garde les
     * propositions, calculées sur le mot relu dans le texte — sans quoi des applications
     * entières (la barre d'adresse de Firefox, par exemple) n'en auraient aucune.
     */
    private fun allowsComposing(info: EditorInfo): Boolean =
        when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
        }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * La fenêtre du clavier ne doit intercepter les appuis que sur sa partie visible :
     * sinon un appui dans le texte de l'application au-dessus ne place pas le curseur.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentTopInsets = outInsets.visibleTopInsets
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd)
        selEnd = newSelEnd
        if (deleteAnchor >= 0) return

        // Le curseur a quitté la zone de composition (appui dans le texte, sélection…).
        // `candidatesEnd < 0` = l'application ne suit pas la composition : on ne
        // réinitialise alors que sur une vraie sélection, sinon on casserait la frappe.
        val horsComposition = if (candidatesEnd >= 0) {
            newSelStart != candidatesEnd || newSelEnd != candidatesEnd
        } else {
            newSelStart != newSelEnd
        }
        if (composing.isNotEmpty() && horsComposition) {
            composing.setLength(0)
            gestureWord = null
            gestureAlternatives = emptyList()
            currentInputConnection?.finishComposingText()
        }
        if (composing.isEmpty()) refreshContext()
        updateShiftFromCaps()
    }

    // ------------------------------------------------------------ préférences

    private fun currentTheme(): KeyboardTheme {
        val contrast = prefs.contrast
        return when (prefs.theme) {
            "light" -> ThemeBuilder.build(ThemeBuilder.PALETTES[1], contrast)
            "dark" -> ThemeBuilder.build(ThemeBuilder.PALETTES[0], contrast)
            "custom" -> ThemeBuilder.build(
                prefs.colorBackground, prefs.colorKey, prefs.colorText, prefs.colorAccent, contrast
            )
            else -> {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val dark = night == Configuration.UI_MODE_NIGHT_YES
                ThemeBuilder.build(ThemeBuilder.PALETTES[if (dark) 0 else 1], contrast)
            }
        }
    }

    private fun stripHeight(): Int = (44 * resources.displayMetrics.density).toInt()

    private fun rowHeight(): Int {
        val dm = resources.displayMetrics
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val base = if (landscape) 42f else 54f
        return (base * dm.density * (prefs.heightPercent / 100f)).toInt()
    }

    private fun applyPreferences() {
        val theme = currentTheme()
        keyboardView?.let {
            it.theme = theme
            it.gestureEnabled = prefs.gestureTyping && !sensitiveField
            it.previewEnabled = prefs.keyPreview && !sensitiveField
            it.spaceCursorEnabled = prefs.spaceCursor
            it.highlightEnabled = prefs.highlightTypedLetters && !sensitiveField
            it.highlightColor = prefs.highlightColor
        }
        strip?.theme = theme
        overlay?.theme = theme
        emojiPanel?.theme = theme
        rootView?.let {
            // la marge n'est pas un trou : elle est peinte avec le clavier
            it.setBackgroundColor(theme.background)
            it.extraBottomPadding =
                (prefs.bottomMargin * resources.displayMetrics.density).toInt()
        }
        rebuildLayout()
    }

    private fun rebuildLayout() {
        val kb = keyboardView ?: return
        val emoji = mode == MODE_EMOJI
        kb.visibility = if (emoji) View.GONE else View.VISIBLE
        emojiPanel?.visibility = if (emoji) View.VISIBLE else View.GONE
        if (emoji) {
            // le panneau garde la hauteur du clavier alphabétique : rien ne saute à l'écran
            emojiPanel?.desiredHeight = rowHeight() * lettersRowCount()
            return
        }
        val numberRow = prefs.numberRow && mode == MODE_LETTERS
        val layout = when (mode) {
            MODE_SYMBOLS -> Layouts.symbols(enterLabel())
            MODE_SYMBOLS2 -> Layouts.symbols2(enterLabel())
            else -> Layouts.letters(numberRow, enterLabel())
        }
        kb.desiredHeight = rowHeight() * layout.rows.size
        kb.setLayout(layout)
    }

    /** Nombre de rangées de l'AZERTY courant, rangée de chiffres comprise. */
    private fun lettersRowCount(): Int = if (prefs.numberRow) 5 else 4

    private fun enterLabel(): String? {
        val info = currentInputEditorInfo ?: return null
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "OK"
            EditorInfo.IME_ACTION_SEND -> "Envoi"
            EditorInfo.IME_ACTION_GO -> "Aller"
            EditorInfo.IME_ACTION_NEXT -> "Suiv."
            EditorInfo.IME_ACTION_DONE -> "OK"
            else -> null
        }
    }

    // ------------------------------------------------------------ contexte

    private fun ic(): InputConnection? = currentInputConnection

    /** Mot touché par le curseur et mot précédent, relus dans le champ. */
    private class TextContext(val previous: String, val current: String, val midWord: Boolean)

    /**
     * Relit dans le champ le mot qui touche le curseur et celui qui le précède.
     * Retourne null si la lecture échoue (certaines applications refusent la requête) :
     * on garde alors l'état courant plutôt que d'effacer les propositions à tort.
     */
    private fun readContext(): TextContext? {
        val ic = ic() ?: return null
        val before = ic.getTextBeforeCursor(CONTEXT_LOOKBACK, 0)?.toString() ?: return null
        val after = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (after.isNotEmpty() && Fr.isWordChar(after[0])) return TextContext("", "", true)

        var i = before.length
        while (i > 0 && Fr.isWordChar(before[i - 1])) i--
        val current = before.substring(i)

        var j = i
        while (j > 0 && !Fr.isWordChar(before[j - 1])) j--
        var k = j
        while (k > 0 && Fr.isWordChar(before[k - 1])) k--
        return TextContext(before.substring(k, j), current, false)
    }

    /** Met à jour le contexte (mot touché par le curseur) puis les propositions. */
    private fun refreshContext() {
        val ctx = readContext() ?: return
        if (ctx.midWord) {
            contextWord = ""
            previousWord = ""
            strip?.clear()
            return
        }
        previousWord = ctx.previous
        contextWord = if (composing.isEmpty()) ctx.current else ""
        updateSuggestions()
    }

    /**
     * Reprend en composition le mot déjà écrit qui précède le curseur, pour que la
     * frappe suivante porte sur le mot entier (et non sur les seules lettres ajoutées).
     * On réécrit le mot en zone de composition plutôt que de manipuler des positions
     * absolues, qui ne sont pas fiables d'une application à l'autre.
     */
    private fun resumeComposing() {
        if (!composingAllowed || composing.isNotEmpty()) return
        val ic = ic() ?: return
        val ctx = readContext() ?: return
        if (ctx.midWord) return
        val word = ctx.current
        if (word.isEmpty() || word.length > MAX_WORD) return
        if (word.none { Fr.isLetter(it) }) return
        previousWord = ctx.previous
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        composing.append(word)
        ic.setComposingText(composing, 1)
        ic.endBatchEdit()
        contextWord = ""
    }

    // ------------------------------------------------------------ saisie

    override fun onKey(code: Int, output: String?) {
        val kb = keyboardView ?: return
        strip?.hideBlockPrompt()
        when (code) {
            Codes.SHIFT -> return
            Codes.DELETE -> handleDelete()
            Codes.SPACE -> handleSpace()
            Codes.ENTER -> handleEnter()
            Codes.SYMBOLS -> { mode = MODE_SYMBOLS; rebuildLayout() }
            Codes.SYMBOLS2 -> { mode = MODE_SYMBOLS2; rebuildLayout() }
            Codes.LETTERS -> { mode = MODE_LETTERS; rebuildLayout(); updateShiftFromCaps() }
            else -> handleText(output ?: return)
        }
        if (kb.shiftState == 1 && code >= 0) kb.shiftState = 0
    }

    private fun handleText(text: String) {
        val ic = ic() ?: return
        val c = text.firstOrNull() ?: return

        // Lettre : on poursuit (ou on reprend) le mot en cours.
        if (Fr.isLetter(c)) {
            // Une lettre tapée juste après un mot tracé ouvre le mot suivant : on entérine
            // le tracé et on insère la séparation, comme entre deux tracés.
            if (prefs.autoSpaceAfterGesture && composing.isNotEmpty() &&
                composing.toString() == gestureWord
            ) {
                finishWord(applyCorrection = false)
                val avant = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                if (avant.isNotEmpty() && avant[0] != ' ' && avant[0] != '\n') {
                    ic.commitText(" ", 1)
                }
            }
            if (!composingAllowed) {
                // champ sans composition (barre d'adresse…) : on écrit directement,
                // les propositions sont calculées sur le mot relu dans le texte
                ic.commitText(text, 1)
                gestureWord = null
                gestureAlternatives = emptyList()
                refreshContext()
                return
            }
            if (composing.isEmpty()) resumeComposing()
            gestureWord = null
            gestureAlternatives = emptyList()
            composing.append(text)
            ic.setComposingText(composing, 1)
            contextWord = ""
            updateSuggestions()
            return
        }

        // Apostrophe et trait d'union : ils appartiennent au mot (« j'ai », « rendez-vous »).
        val liaison = c == '\'' || c == '’' || c == '-'
        if (liaison && composingAllowed) {
            if (composing.isEmpty()) resumeComposing()
            if (composing.isNotEmpty() && Fr.isLetter(composing[composing.length - 1])) {
                composing.append(c)
                ic.setComposingText(composing, 1)
                contextWord = ""
                updateSuggestions()
                return
            }
        }

        // Ponctuation, chiffre… : le mot en cours est validé.
        finishWord(applyCorrection = !c.isDigit() && !liaison)
        ic.commitText(text, 1)
        if (c == '.' || c == '!' || c == '?') updateShiftFromCaps()
        refreshContext()
    }

    private fun handleSpace() {
        val ic = ic() ?: return
        finishWord(applyCorrection = true)
        val now = System.currentTimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        if (prefs.doubleSpacePeriod && !sensitiveField && now - lastSpaceTime < DOUBLE_SPACE_MS &&
            before.length == 2 && before[1] == ' ' && Fr.isLetter(before[0])
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTime = 0L
        } else {
            ic.commitText(" ", 1)
            lastSpaceTime = now
        }
        updateShiftFromCaps()
        refreshContext()
    }

    private fun handleEnter() {
        val ic = ic() ?: return
        finishWord(applyCorrection = true)
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnter = (info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) ?: 0) != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnter) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        updateShiftFromCaps()
        refreshContext()
    }

    private fun handleDelete() {
        val ic = ic() ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            composing.setLength(0)
            ic.commitText("", 1)
            refreshContext()
            return
        }
        // Un mot tracé s'efface d'un bloc : il a été écrit d'un geste, il part d'un geste.
        if (composing.isNotEmpty() && composing.toString() == gestureWord) {
            composing.setLength(0)
            gestureWord = null
            gestureAlternatives = emptyList()
            ic.setComposingText("", 1)
            ic.finishComposingText()
            refreshContext()
            return
        }
        // Effacer à l'intérieur d'un mot déjà écrit : on le reprend en composition,
        // les propositions repartiront du mot entier.
        if (composing.isEmpty()) resumeComposing()

        if (composing.isNotEmpty()) {
            gestureWord = null
            gestureAlternatives = emptyList()
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
                refreshContext()
            } else {
                ic.setComposingText(composing, 1)
                updateSuggestions()
            }
            return
        }
        deleteOneCharacter()
    }

    /**
     * Efface un caractère **tel qu'il s'affiche**. Un emoji vaut souvent plusieurs
     * caractères Java — paire de substitution, teinte de peau, séquence liée par des
     * jointures — et n'en effacer qu'un laisserait un résidu illisible dans le texte.
     */
    private fun deleteOneCharacter() {
        val ic = ic() ?: return
        val before = ic.getTextBeforeCursor(GRAPHEME_LOOKBACK, 0)?.toString()
        val n = if (before.isNullOrEmpty()) 1 else lastGraphemeLength(before)
        ic.deleteSurroundingText(n, 0)
        updateShiftFromCaps()
        refreshContext()
    }

    /** Longueur, en caractères, du dernier signe affiché de [text]. */
    private fun lastGraphemeLength(text: String): Int {
        val it = java.text.BreakIterator.getCharacterInstance(java.util.Locale.FRANCE)
        it.setText(text)
        val start = it.preceding(text.length)
        return if (start == java.text.BreakIterator.DONE) text.length else text.length - start
    }

    // ------------------------------------------------------------ effacement au doigt

    override fun onDeleteSwipeStart() {
        val ic = ic() ?: return
        if (composing.isNotEmpty()) {
            composing.setLength(0)
            gestureWord = null
            gestureAlternatives = emptyList()
            ic.finishComposingText()
        }
        deleteAnchor = selEnd
        deleteSelected = 0
        deleteBefore = ic.getTextBeforeCursor(DELETE_LOOKBACK, 0)?.toString() ?: ""
        strip?.clear()
    }

    /**
     * Le texte est **sélectionné** au fil du balayage plutôt qu'effacé tout de suite :
     * on voit ce qui va partir, et revenir en arrière annule. La suppression n'a lieu
     * qu'au relâchement.
     */
    override fun onDeleteSwipe(steps: Int, toEdge: Boolean) {
        val ic = ic() ?: return
        if (deleteAnchor < 0) return
        val before = deleteBefore
        if (before.isEmpty()) return

        val chars = if (toEdge) before.length else charsForWords(before, wordsForSteps(steps))
        if (chars == deleteSelected) return
        deleteSelected = chars

        val start = (deleteAnchor - chars).coerceAtLeast(0)
        try {
            ic.setSelection(start, deleteAnchor)
        } catch (_: Exception) {
        }
    }

    override fun onDeleteSwipeEnd(commit: Boolean) {
        val ic = ic()
        val chars = deleteSelected
        val anchor = deleteAnchor
        deleteAnchor = -1
        deleteSelected = 0
        deleteBefore = ""
        if (ic == null) return

        if (!commit || chars <= 0) {
            if (anchor >= 0) try { ic.setSelection(anchor, anchor) } catch (_: Exception) {}
            refreshContext()
            return
        }
        val selection = ic.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            // l'application ne suit pas la sélection : on efface directement
            if (anchor >= 0) try { ic.setSelection(anchor, anchor) } catch (_: Exception) {}
            ic.deleteSurroundingText(chars, 0)
        }
        refreshContext()
    }

    /** Nombre de mots couverts par [steps] crans : lent au début, plus large ensuite. */
    private fun wordsForSteps(steps: Int): Int = when {
        steps <= 0 -> 0
        steps <= 4 -> steps
        else -> 4 + (steps - 4) * 3
    }

    /** Longueur, en caractères, des [words] derniers mots de [before]. */
    private fun charsForWords(before: String, words: Int): Int {
        if (words <= 0) return 0
        var i = before.length
        var found = 0
        while (i > 0 && found < words) {
            while (i > 0 && !Fr.isWordChar(before[i - 1])) i--
            if (i == 0) break
            while (i > 0 && Fr.isWordChar(before[i - 1])) i--
            found++
        }
        return before.length - i
    }

    /** Valide le mot en cours, avec correction automatique si demandée. */
    private fun finishWord(applyCorrection: Boolean) {
        val ic = ic() ?: return
        if (composing.isEmpty()) return
        val typed = composing.toString()
        val fromGesture = typed == gestureWord
        var out = typed
        // un mot tracé vient déjà du décodeur : on ne le « corrige » pas une seconde fois
        if (applyCorrection && !fromGesture && prefs.autoCorrect && prefs.suggestions) {
            suggester.suggest(typed, previousWord, 3).autoCorrect?.let { out = it }
        }
        if (out != typed) ic.setComposingText(out, 1)
        ic.finishComposingText()
        composing.setLength(0)
        gestureWord = null
        gestureAlternatives = emptyList()
        learn(out, previousWord)
        previousWord = out
        contextWord = ""
    }

    private fun learn(committed: String, previous: String) {
        if (!mayLearn()) return
        val d = dict
        runBackground {
            if (committed.length >= 2 && committed.all { Fr.isWordChar(it) }) {
                val known = d?.containsExact(committed) == true ||
                    d?.containsExact(committed.lowercase()) == true
                if (!known) userDict.learnWord(committed)
            }
            if (previous.isNotEmpty()) userDict.learnBigram(previous, committed)
        }
    }

    // ------------------------------------------------------------ suggestions

    /** Signale à la vue les lettres du mot en cours, pour les mettre en couleur. */
    private fun updateHighlightedLetters() {
        val kb = keyboardView ?: return
        if (!prefs.highlightTypedLetters || sensitiveField) {
            if (kb.highlightedLetters.isNotEmpty()) kb.setHighlightedLetters(emptySet())
            return
        }
        val word = if (composing.isNotEmpty()) composing.toString() else contextWord
        kb.setHighlightedLetters(Fr.normalize(word).toSet())
    }

    private fun updateSuggestions() {
        updateHighlightedLetters()
        val st = strip ?: return
        if (!prefs.suggestions || !suggestionsAllowed) { st.clear(); return }

        // Mot tracé : on affiche les variantes du décodeur, pas une recherche par préfixe.
        val gw = gestureWord
        val traceEnCours = gw != null &&
            (composing.toString() == gw || (composing.isEmpty() && contextWord == gw))
        if (traceEnCours && gestureAlternatives.isNotEmpty()) {
            st.setSuggestions(gestureAlternatives.take(3), 0)
            return
        }

        val word = if (composing.isNotEmpty()) composing.toString() else contextWord
        val seq = ++suggestionSeq
        val prev = previousWord
        val composingNow = composing.isNotEmpty()
        runBackground {
            val r = suggester.suggest(word, prev, 3)
            runMain {
                if (seq != suggestionSeq) return@runMain
                if (word.isEmpty()) {
                    st.setSuggestions(r.words, -1)
                    return@runMain
                }
                val list = ArrayList<String>(3)
                var highlight = -1
                val auto = r.autoCorrect
                if (auto != null && composingNow && prefs.autoCorrect) {
                    list.add(word)
                    list.add(auto)
                    highlight = 1
                    r.words.firstOrNull { it != auto && it != word }?.let { list.add(it) }
                } else {
                    list.add(word)
                    list.addAll(r.words.filter { it != word })
                }
                st.setSuggestions(list.distinct().take(3), highlight)
            }
        }
    }

    override fun onPickSuggestion(index: Int, word: String) {
        val ic = ic() ?: return
        val typed = if (composing.isNotEmpty()) composing.toString() else contextWord

        // Ce qui suit le curseur décide de l'espace : ne pas en ajouter un deuxième,
        // ne pas en insérer avant une ponctuation.
        val after = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        val suivi = when {
            after == " " -> Suivi.ESPACE_EXISTANTE
            after.isNotEmpty() && !Fr.isWordChar(after[0]) -> Suivi.PONCTUATION
            else -> Suivi.RIEN
        }

        ic.beginBatchEdit()
        if (composing.isNotEmpty()) {
            composing.setLength(0)
            ic.setComposingText(word, 1)
            ic.finishComposingText()
        } else if (contextWord.isNotEmpty()) {
            // le curseur est à la fin d'un mot déjà validé : on le remplace
            ic.deleteSurroundingText(contextWord.length, 0)
            ic.commitText(word, 1)
        } else {
            // prédiction du mot suivant
            ic.commitText(word, 1)
        }
        when (suivi) {
            // on saute l'espace déjà présente au lieu d'en ajouter une seconde
            Suivi.ESPACE_EXISTANTE -> { ic.deleteSurroundingText(0, 1); ic.commitText(" ", 1) }
            Suivi.PONCTUATION -> Unit
            Suivi.RIEN -> ic.commitText(" ", 1)
        }
        ic.endBatchEdit()

        gestureWord = null
        gestureAlternatives = emptyList()
        contextWord = ""
        lastSpaceTime = System.currentTimeMillis()

        val prev = previousWord
        // le choix d'une proposition est un apprentissage comme un autre : il obéit au
        // réglage et au type de champ (il contournait les deux)
        if (mayLearn()) runBackground {
            if (word != typed) userDict.addWord(word) else userDict.learnWord(word)
            if (prev.isNotEmpty()) userDict.learnBigram(prev, word)
        }
        previousWord = word

        updateShiftFromCaps()
        // le mot est validé : on propose maintenant la suite de la phrase
        updateSuggestions()
    }

    /**
     * Blocage demandé par un appui long sur une proposition. Si le mot bloqué est celui
     * qui vient d'être inséré par un tracé, on le remplace aussitôt par la variante
     * suivante : l'utilisateur n'a pas à effacer lui-même.
     */
    override fun onBlockWord(word: String) {
        blocklist.add(word)
        runBackground { blocklist.save() }

        val restantes = gestureAlternatives.filterNot { blocklist.isBlocked(it) }
        val remplacable = composing.isNotEmpty() && composing.toString() == word
        if (remplacable) {
            val ic = ic()
            val remplacant = restantes.firstOrNull()
            if (ic != null) {
                composing.setLength(0)
                if (remplacant != null) {
                    composing.append(remplacant)
                    ic.setComposingText(remplacant, 1)
                    gestureWord = remplacant
                } else {
                    ic.setComposingText("", 1)
                    ic.finishComposingText()
                    gestureWord = null
                }
            }
        }
        gestureAlternatives = restantes
        updateSuggestions()
    }

    override fun onToolbarAction(action: Int) {
        when (action) {
            SuggestionStripView.Tool.EMOJI -> openEmoji()
            SuggestionStripView.Tool.SETTINGS -> openSettings()
        }
    }

    // ------------------------------------------------------------ emoji

    /**
     * Ouvre le panneau des emoji, ou le referme s'il est déjà là : le bouton reste
     * visible au-dessus du panneau, il doit ramener au clavier.
     *
     * Le mot en cours est validé au passage : on ne laisse pas une zone de composition
     * ouverte derrière un panneau qui n'écrit pas de lettres.
     */
    private fun openEmoji() {
        if (mode == MODE_EMOJI) {
            mode = MODE_LETTERS
            rebuildLayout()
            updateShiftFromCaps()
            refreshContext()
            return
        }
        finishWord(applyCorrection = false)
        strip?.clear()
        emojiPanel?.setRecents(prefs.recentEmoji)
        mode = MODE_EMOJI
        rebuildLayout()
    }

    override fun onEmoji(emoji: String) {
        val ic = ic() ?: return
        finishWord(applyCorrection = false)
        ic.commitText(emoji, 1)
        gestureWord = null
        gestureAlternatives = emptyList()
        contextWord = ""
        previousWord = ""
        lastSpaceTime = 0L
        noteRecentEmoji(emoji)
        strip?.clear()
    }

    override fun onEmojiKey(code: Int) {
        when (code) {
            Codes.LETTERS -> { mode = MODE_LETTERS; rebuildLayout(); updateShiftFromCaps() }
            Codes.SPACE -> { ic()?.commitText(" ", 1); refreshContext() }
            Codes.DELETE -> deleteOneCharacter()
        }
    }

    override fun onEmojiFeedback(code: Int) = onKeyPressFeedback(code)

    /** L'emoji choisi passe en tête des récents, sans doublon. */
    private fun noteRecentEmoji(emoji: String) {
        // un emoji tapé dans un champ mot de passe ou en navigation privée ne se retient pas
        if (!learningAllowed) return
        val list = (listOf(emoji) + prefs.recentEmoji.filterNot { it == emoji })
            .take(Prefs.MAX_RECENT_EMOJI)
        prefs.recentEmoji = list
        emojiPanel?.setRecents(list)
    }

    // ------------------------------------------------------------ swipe

    override fun onGestureStarted() {
        overlay?.hidePreview()
    }

    override fun onGestureCancelled() {}

    override fun onGestureFinished(px: FloatArray, py: FloatArray, count: Int, geo: KeyGeometry) {
        // la vue ne trace plus dans un champ sensible ; on refuse aussi ici par sécurité
        if (sensitiveField) return
        val xs = px.copyOf(count)
        val ys = py.copyOf(count)
        // La vue continue de vivre pendant le décodage : une rotation ou un changement de
        // disposition réécrirait sa géométrie sous les pieds du décodeur. On en fige une copie.
        val snapshot = geo.copy()
        val shifted = (keyboardView?.shiftState ?: 0) > 0
        suggestionSeq++   // annule les suggestions en attente
        runBackground {
            val words = decoder.decode(xs, ys, count, snapshot)
            runMain { if (words.isNotEmpty()) insertGestureWord(words, shifted) }
        }
    }

    /**
     * Le mot tracé est posé **en composition** : toucher une variante le remplace
     * proprement, et valider ajoute l'espace, comme pour un mot tapé.
     */
    private fun insertGestureWord(words: List<String>, shifted: Boolean) {
        val ic = ic() ?: return
        val alternatives = if (shifted) words.map { Fr.capitalize(it) } else words
        val word = alternatives.first()

        ic.beginBatchEdit()
        // un mot en cours de frappe est validé avant le mot tracé
        if (composing.isNotEmpty()) finishWord(applyCorrection = true)
        if (prefs.autoSpaceAfterGesture) {
            val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            if (before.isNotEmpty() && (Fr.isWordChar(before[0]) || before[0] in ",.!?;:")) {
                ic.commitText(" ", 1)
            }
        }
        readContext()?.let { previousWord = it.previous.ifEmpty { previousWord } }
        composing.setLength(0)
        composing.append(word)
        if (composingAllowed) {
            ic.setComposingText(word, 1)
        } else {
            ic.commitText(word, 1)
            composing.setLength(0)
        }
        ic.endBatchEdit()

        gestureWord = word
        gestureAlternatives = alternatives
        contextWord = if (composingAllowed) "" else word
        strip?.setSuggestions(alternatives.take(3), 0)
        updateHighlightedLetters()
        if ((keyboardView?.shiftState ?: 0) == 1) keyboardView?.shiftState = 0
    }

    // ------------------------------------------------------------ retours

    override fun onKeyPressFeedback(code: Int) {
        if (prefs.vibrate) vibrate(prefs.vibrateMs)
        if (prefs.sound) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val fx = when (code) {
                Codes.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Codes.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Codes.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            am?.playSoundEffect(fx, 0.4f)
        }
    }

    private fun vibrate(ms: Int) {
        if (ms <= 0) return
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vib?.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    override fun onShowPreview(key: Key, top: Float) {
        overlay?.showPreview(key, (keyboardView?.shiftState ?: 0) > 0, top)
    }

    override fun onHidePreview() {
        overlay?.hidePreview()
    }

    override fun onShowAccents(key: Key, choices: List<String>, top: Float) {
        overlay?.showAccents(key, choices, top)
    }

    override fun onMoveAccents(x: Float, y: Float) {
        overlay?.moveAccents(x)
    }

    override fun onHideAccents(commit: Boolean): String? = overlay?.hideAccents(commit)

    override fun onCursorDrag(steps: Int) {
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(minOf(kotlin.math.abs(steps), 8)) { moveCursor(code) }
    }

    /** Déplace le curseur d'un cran : seul le glissement sur la barre d'espace y mène. */
    private fun moveCursor(keyCode: Int) {
        val ic = ic() ?: return
        if (composing.isNotEmpty()) {
            composing.setLength(0)
            gestureWord = null
            gestureAlternatives = emptyList()
            ic.finishComposingText()
        }
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0))
    }

    // ------------------------------------------------------------ divers

    private fun updateShiftFromCaps() {
        val kb = keyboardView ?: return
        if (kb.shiftState == 2) return
        if (!prefs.autoCap) return
        if (composing.isNotEmpty()) return
        val info = currentInputEditorInfo ?: return
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
        val caps = ic()?.getCursorCapsMode(info.inputType) ?: 0
        kb.shiftState = if (caps != 0) 1 else 0
    }

    private fun openSettings() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
    }

    // ------------------------------------------------------------ tests

    /** Propositions affichées (vérification par les tests). */
    internal fun visibleSuggestions(): List<String> = strip?.suggestions() ?: emptyList()

    /** Mot en cours de composition (vérification par les tests). */
    internal fun composingText(): String = composing.toString()

    /** Lettres allumées sur le clavier (vérification par les tests). */
    internal fun highlightedLetters(): Set<Char> =
        keyboardView?.highlightedLetters ?: emptySet()

    /** Liste des mots bloqués (vérification par les tests). */
    internal fun blockedWords(): List<String> = blocklist.all()

    /** Ce qui suit le curseur au moment de valider une proposition. */
    private enum class Suivi { RIEN, ESPACE_EXISTANTE, PONCTUATION }

    companion object {
        const val MODE_LETTERS = 0
        const val MODE_SYMBOLS = 1
        const val MODE_SYMBOLS2 = 2
        const val MODE_EMOJI = 3
        const val DOUBLE_SPACE_MS = 900L
        const val CONTEXT_LOOKBACK = 64
        /** Assez de texte pour reconnaître le dernier signe affiché, emoji compris. */
        const val GRAPHEME_LOOKBACK = 16
        const val DELETE_LOOKBACK = 4000
        const val MAX_WORD = 40
    }
}

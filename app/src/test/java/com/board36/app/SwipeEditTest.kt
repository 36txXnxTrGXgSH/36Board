package com.board36.app

import android.content.Context
import android.text.InputType
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.board36.app.data.Prefs
import com.board36.app.ime.Board36Service
import com.board36.app.ime.Codes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ce qui entoure la saisie gestuelle : les lettres du mot tracé s'allument sur le
 * clavier, le mot tracé s'efface d'un bloc, et le balayage depuis Retour arrière
 * sélectionne de plus en plus de texte avant de l'effacer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SwipeEditTest {

    class TestService : Board36Service() {
        lateinit var connection: InputConnection
        var editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        override fun getCurrentInputConnection(): InputConnection = connection
        override fun getCurrentInputEditorInfo(): EditorInfo = editorInfo
        override fun runBackground(task: () -> Unit) = task()
        override fun runMain(task: () -> Unit) = task()
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var service: TestService
    private lateinit var edit: EditText

    @Before
    fun setUp() {
        Prefs(context).apply {
            autoCap = false
            vibrate = false
            highlightTypedLetters = true
        }
        java.io.File(context.filesDir, "blocked_words.txt").delete()
        edit = EditText(context)
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.connection = edit.onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)
    }

    private fun text() = edit.text.toString()

    private fun swipe(word: String) {
        val (xs, ys, n) = TestKeyboard.gestureFor(word)
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        pump()
    }

    private fun pump() {
        val sel = Selection.getSelectionStart(edit.text)
        service.onUpdateSelection(
            sel, sel, sel, sel,
            BaseInputConnection.getComposingSpanStart(edit.text),
            BaseInputConnection.getComposingSpanEnd(edit.text)
        )
    }

    private fun setText(content: String) {
        edit.setText(content)
        Selection.setSelection(edit.text, content.length)
        service.onUpdateSelection(0, 0, content.length, content.length, -1, -1)
    }

    // ------------------------------------------------------------ illumination

    @Test
    fun lesLettresDuMotTraceSAllument() {
        swipe("bonjour")
        assertEquals("bonjour", text())
        val allumees = service.highlightedLetters()
        assertEquals("lettres allumées : $allumees", "bonjur".toSortedSet(), allumees.toSortedSet())
    }

    @Test
    fun lIlluminationSuitLeMotRemplace() {
        swipe("bonjour")
        val variantes = service.visibleSuggestions()
        val autre = variantes[1]
        service.onPickSuggestion(1, autre)
        pump()
        // mot validé : plus rien n'est en cours, donc plus rien n'est allumé
        assertTrue("lettres restées allumées : ${service.highlightedLetters()}",
            service.highlightedLetters().isEmpty())
    }

    // ------------------------------------------------------------ enchaînement

    @Test
    fun onEnchaineUnMotTapeApresUnMotTrace() {
        swipe("bonjour")
        service.onKey('t'.code, "t"); pump()
        service.onKey('o'.code, "o"); pump()
        service.onKey('i'.code, "i"); pump()
        assertEquals("bonjour toi", text())
    }

    @Test
    fun laPonctuationResteColleeAuMotTrace() {
        swipe("bonjour")
        service.onKey('.'.code, "."); pump()
        assertEquals("bonjour.", text())
    }

    @Test
    fun uneEspaceTapeeApresUnTraceNenDoublePas() {
        swipe("bonjour")
        service.onKey(Codes.SPACE, " "); pump()
        assertEquals("bonjour ", text())
    }

    @Test
    fun deuxMotsTracesRestentSepares() {
        swipe("bonjour")
        swipe("demain")
        assertEquals("bonjour demain", text())
    }

    // ------------------------------------------------------------ effacement

    @Test
    fun leMotTraceSEffaceDunBloc() {
        setText("salut ")
        swipe("bonjour")
        assertEquals("salut bonjour", text())

        service.onKey(Codes.DELETE, null)
        pump()
        assertEquals("le mot tracé doit partir en entier", "salut ", text())
        assertTrue(service.composingText().isEmpty())
    }

    @Test
    fun apresLeMotTraceLeffacementRedevientNormal() {
        setText("salut ")
        swipe("bonjour")
        service.onKey(Codes.DELETE, null); pump()
        service.onKey(Codes.DELETE, null); pump()
        assertEquals("salut", text())
    }

    // ------------------------------------------------------------ balayage

    @Test
    fun leBalayageSelectionnePuisEfface() {
        setText("un deux trois quatre")

        service.onDeleteSwipeStart()
        service.onDeleteSwipe(1, false)
        assertEquals("le dernier mot doit être sélectionné", "quatre", selectedText())

        service.onDeleteSwipe(2, false)
        assertEquals("deux mots doivent être sélectionnés", "trois quatre", selectedText())

        service.onDeleteSwipeEnd(true)
        assertEquals("un deux ", text())
    }

    @Test
    fun revenirEnArriereAnnuleLaSelection() {
        setText("un deux trois quatre")
        service.onDeleteSwipeStart()
        service.onDeleteSwipe(2, false)
        service.onDeleteSwipe(0, false)
        service.onDeleteSwipeEnd(true)
        assertEquals("rien ne devait être effacé", "un deux trois quatre", text())
    }

    @Test
    fun relacherEnAnnulationNEffaceRien() {
        setText("un deux trois quatre")
        service.onDeleteSwipeStart()
        service.onDeleteSwipe(3, false)
        service.onDeleteSwipeEnd(false)
        assertEquals("un deux trois quatre", text())
    }

    @Test
    fun leBordEffaceToutLeTexteQuiPrecede() {
        setText("un deux trois quatre cinq six sept")
        service.onDeleteSwipeStart()
        service.onDeleteSwipe(2, false)
        service.onDeleteSwipe(9, true)
        service.onDeleteSwipeEnd(true)
        assertEquals("", text())
    }

    @Test
    fun leBalayageSAccelereApresQuelquesCrans() {
        setText("a b c d e f g h i j k")
        service.onDeleteSwipeStart()
        service.onDeleteSwipe(4, false)
        val quatre = text().length - selectionLength()
        service.onDeleteSwipe(6, false)
        val six = text().length - selectionLength()
        assertTrue("6 crans doivent effacer plus que 4", six < quatre)
        service.onDeleteSwipeEnd(true)
        // 6 crans = 10 mots ici : il reste le tout premier, pas plus
        assertTrue("il doit rester le début du texte : « ${text()} »", text().startsWith("a"))
        assertTrue("tout ne devait pas partir", text().isNotEmpty())
    }

    private fun selectedText(): String {
        val s = Selection.getSelectionStart(edit.text)
        val e = Selection.getSelectionEnd(edit.text)
        return if (s == e) "" else text().substring(minOf(s, e), maxOf(s, e))
    }

    private fun selectionLength(): Int {
        val s = Selection.getSelectionStart(edit.text)
        val e = Selection.getSelectionEnd(edit.text)
        return kotlin.math.abs(e - s)
    }
}

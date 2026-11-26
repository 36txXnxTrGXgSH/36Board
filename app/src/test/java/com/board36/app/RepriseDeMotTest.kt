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
import com.board36.app.engine.Suggester
import com.board36.app.engine.UserDictionary
import com.board36.app.ime.Board36Service
import com.board36.app.ime.Codes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Revenir sur un mot déjà écrit — curseur posé dedans, ou mot sélectionné — propose
 * d'autres mots, et en choisir un remplace le mot entier sans toucher au reste.
 * Pour un mot tracé, ce sont les variantes du tracé qui reviennent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RepriseDeMotTest {

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
            autoCap = false; vibrate = false; sound = false
            suggestions = true; autoCorrect = true; learnWords = false
        }
        edit = EditText(context)
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.connection = edit.onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)
    }

    private fun text() = edit.text.toString()

    private fun pump() {
        val a = Selection.getSelectionStart(edit.text)
        val b = Selection.getSelectionEnd(edit.text)
        service.onUpdateSelection(0, 0, a, b,
            BaseInputConnection.getComposingSpanStart(edit.text),
            BaseInputConnection.getComposingSpanEnd(edit.text))
    }

    private fun type(s: String) {
        for (c in s) {
            if (c == ' ') service.onKey(Codes.SPACE, " ") else service.onKey(c.code, c.toString())
            pump()
        }
    }

    private fun select(start: Int, end: Int = start) {
        Selection.setSelection(edit.text, start, end)
        pump()
    }

    // ------------------------------------------------------------ mot tapé

    @Test
    fun leCurseurDansUnMotProposeDAutresMots() {
        type("il fait beau ")
        val at = text().indexOf("fait") + 2           // « fa|it »
        select(at)
        val s = service.visibleSuggestions()
        assertTrue("aucune proposition dans le mot : $s", s.isNotEmpty())
        assertFalse("le mot lui-même n'est pas une alternative : $s", s.contains("fait"))
    }

    @Test
    fun choisirUnePropositionRemplaceLeMotEntierSansToucherAuReste() {
        type("il fait beau ")
        select(text().indexOf("fait") + 1)
        val choice = service.visibleSuggestions().first()
        service.onPickSuggestion(0, choice)
        assertEquals("il $choice beau ", text())
    }

    @Test
    fun unMotSelectionneProposeDAutresMotsEtSeRemplace() {
        type("il fait beau ")
        val a = text().indexOf("beau")
        select(a, a + 4)
        val s = service.visibleSuggestions()
        assertTrue("aucune proposition pour la sélection : $s", s.isNotEmpty())
        assertFalse(s.contains("beau"))
        service.onPickSuggestion(0, s.first())
        assertEquals("il fait ${s.first()} ", text())
    }

    @Test
    fun uneSelectionDePlusieursMotsNeProposeRien() {
        type("il fait beau ")
        select(0, 7)
        assertTrue(service.visibleSuggestions().isEmpty())
    }

    @Test
    fun unMotSansAccentProposeSaFormeAccentuee() {
        val alternatives = Suggester(TestKeyboard.dictionary, UserDictionary(null)).alternatives("ete")
        assertTrue("alternatives de « ete » : $alternatives", alternatives.contains("été"))
    }

    // ------------------------------------------------------------ mot tracé

    @Test
    fun unMotTraceRetrouveSesVariantesQuandOnYRevient() {
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        pump()
        val variants = service.visibleSuggestions()
        assertEquals("bonjour", text())
        type(" ")
        type("madame")
        type(" ")
        // l'utilisateur revient dans le mot tracé
        select(3)
        val s = service.visibleSuggestions()
        // les variantes du tracé, sans le mot lui-même : sa place en libère une de plus
        assertFalse(s.contains("bonjour"))
        assertEquals(3, s.size)
        assertEquals(variants.filter { it != "bonjour" }, s.take(variants.size - 1))
    }

    @Test
    fun choisirUneVarianteDuTraceRemplaceLeMotEtPermetDeRechoisir() {
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        pump()
        type(" ")
        type("madame ")
        select(2)
        val variant = service.visibleSuggestions().first()
        service.onPickSuggestion(0, variant)
        pump()
        assertEquals("$variant madame ", text())
        // on peut y revenir et retrouver « bonjour » parmi les variantes
        select(1)
        assertTrue("bonjour doit revenir : ${service.visibleSuggestions()}",
            service.visibleSuggestions().contains("bonjour"))
    }

    // ------------------------------------------------------------ garde-fous

    @Test
    fun taperDansUnMotRepriseEcritNormalement() {
        type("il fait beau ")
        select(text().indexOf("fait") + 4)            // fin de « fait »
        type("s")
        assertTrue("la frappe doit poursuivre le mot : ${text()}", text().startsWith("il faits"))
    }

    @Test
    fun rienNEstProposeDansUnMotDePasse() {
        service.editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        service.onStartInputView(service.editorInfo, false)
        type("motdepasse")
        select(3)
        assertTrue(service.visibleSuggestions().isEmpty())
    }
}

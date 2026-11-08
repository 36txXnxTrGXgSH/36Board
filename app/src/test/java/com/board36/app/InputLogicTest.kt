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
 * Parcours réels de saisie, sur un vrai champ de texte Android (`EditText` et sa
 * `InputConnection`) piloté par le vrai service. C'est le chemin qui casse en pratique :
 * reprise d'un mot déjà écrit, curseur déplacé, apostrophes, choix d'une variante de swipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class InputLogicTest {

    /** Service de test : dictionnaire chargé de façon synchrone, champ de texte imposé. */
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
        val prefs = Prefs(context)
        prefs.autoCap = false
        prefs.vibrate = false
        prefs.sound = false
        prefs.suggestions = true
        prefs.autoCorrect = true

        edit = EditText(context)
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.connection = edit.onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)
    }

    // ------------------------------------------------------------ utilitaires

    private fun text(): String = edit.text.toString()

    private fun type(s: String) {
        for (c in s) {
            when (c) {
                ' ' -> service.onKey(Codes.SPACE, " ")
                else -> service.onKey(c.code, c.toString())
            }
            pumpSelection()
        }
    }

    private fun delete(times: Int = 1) {
        repeat(times) { service.onKey(Codes.DELETE, null); pumpSelection() }
    }

    /** Rejoue la notification du framework après une modification du champ. */
    private fun pumpSelection() {
        val sel = Selection.getSelectionStart(edit.text)
        val start = BaseInputConnection.getComposingSpanStart(edit.text)
        val end = BaseInputConnection.getComposingSpanEnd(edit.text)
        service.onUpdateSelection(sel, sel, sel, sel, start, end)
    }

    /** Déplace le curseur comme le ferait un appui de l'utilisateur dans le texte. */
    private fun moveCursorTo(position: Int) {
        Selection.setSelection(edit.text, position)
        val start = BaseInputConnection.getComposingSpanStart(edit.text)
        val end = BaseInputConnection.getComposingSpanEnd(edit.text)
        service.onUpdateSelection(0, 0, position, position, start, end)
    }

    // ------------------------------------------------------------ tests

    @Test
    fun effacerDansUnMotPuisReecrireProposeLeMotEntier() {
        type("bonjour")
        assertEquals("bonjour", text())
        delete(3)                       // "bonj"
        assertEquals("bonj", text())
        type("our")
        assertEquals("bonjour", text())
        assertEquals("bonjour", service.composingText())
        assertTrue("propositions : ${service.visibleSuggestions()}",
            service.visibleSuggestions().contains("bonjour"))
    }

    @Test
    fun revenirALaFinDunMotRedonneDesPropositions() {
        type("bonjour ")
        assertEquals("bonjour ", text())
        moveCursorTo(7)                 // juste après « bonjour »
        val s = service.visibleSuggestions()
        assertTrue("propositions : $s", s.isNotEmpty())
        assertTrue("propositions : $s", s.contains("bonjour"))
    }

    @Test
    fun choisirUneProposotionSurUnMotDejaEcritLeRemplace() {
        type("bonjour ")
        moveCursorTo(7)
        val s = service.visibleSuggestions()
        val other = s.firstOrNull { it != "bonjour" } ?: "bonjours"
        service.onPickSuggestion(1, other)
        assertEquals("$other ", text())
    }

    @Test
    fun lesMotsAvecApostropheSontProposes() {
        type("j'ai")
        assertEquals("j'ai", text())
        assertEquals("j'ai", service.composingText())
        assertTrue("propositions : ${service.visibleSuggestions()}",
            service.visibleSuggestions().contains("j'ai"))
    }

    @Test
    fun lApostropheNEstPasCorrigeeATort() {
        type("j'ai ")
        assertEquals("j'ai ", text())
    }

    @Test
    fun lElisionCompleteLaSuite() {
        type("l'écol")
        val s = service.visibleSuggestions()
        assertTrue("propositions : $s", s.any { it == "l'école" })
    }

    @Test
    fun motTraceRestEnCompositionEtSeRemplaceParUneVariante() {
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)

        assertEquals("bonjour", text())
        assertEquals("bonjour", service.composingText())
        val alternatives = service.visibleSuggestions()
        assertTrue("variantes : $alternatives", alternatives.size >= 2)
        assertEquals("bonjour", alternatives[0])

        // choisir une variante remplace le mot et ajoute l'espace
        val variante = alternatives[1]
        service.onPickSuggestion(1, variante)
        assertEquals("$variante ", text())
        assertEquals("", service.composingText())
    }

    @Test
    fun validerLeMotTraceAjouteUneEspace() {
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        service.onPickSuggestion(0, "bonjour")
        assertEquals("bonjour ", text())
        assertEquals("", service.composingText())
    }

    @Test
    fun deuxMotsTracesSontSeparesParUneEspace() {
        val g1 = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(g1.first, g1.second, g1.third, TestKeyboard.geometry)
        val g2 = TestKeyboard.gestureFor("demain")
        service.onGestureFinished(g2.first, g2.second, g2.third, TestKeyboard.geometry)
        assertEquals("bonjour demain", text())
    }

    @Test
    fun laFrappeApresUnMotTraceOuvreLeMotSuivant() {
        val g = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(g.first, g.second, g.third, TestKeyboard.geometry)
        type("s")
        assertEquals("bonjour s", text())
        assertEquals("s", service.composingText())
    }

    @Test
    fun pasDeCompositionDansUnChampMotDePasse() {
        service.editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        service.onStartInputView(service.editorInfo, false)
        type("motdepasse")
        assertEquals("motdepasse", text())
        assertEquals("", service.composingText())
        assertTrue(service.visibleSuggestions().isEmpty())
    }

    @Test
    fun propositionsDansUnChampSansComposition() {
        // barre d'adresse (Firefox, Chrome…) : type URI, pas de composition possible,
        // mais les propositions doivent quand même apparaître
        service.editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        service.onStartInputView(service.editorInfo, false)
        type("bonjo")
        assertEquals("bonjo", text())
        assertEquals("", service.composingText())
        val s = service.visibleSuggestions()
        assertTrue("propositions : $s", s.contains("bonjour"))

        service.onPickSuggestion(1, "bonjour")
        assertEquals("bonjour ", text())
    }

    @Test
    fun swipeDansUnChampSansComposition() {
        service.editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        service.onStartInputView(service.editorInfo, false)
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        assertEquals("bonjour", text())
        val variantes = service.visibleSuggestions()
        assertTrue("variantes : $variantes", variantes.size >= 2)
        service.onPickSuggestion(1, variantes[1])
        assertEquals("${variantes[1]} ", text())
    }

    @Test
    fun leMotEliseEstProposeApresUnTrace() {
        val (xs, ys, n) = TestKeyboard.gestureFor("louverture")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        assertEquals("l'ouverture", text())
        assertEquals("l'ouverture", service.composingText())
        // la validation ajoute l'espace et l'apostrophe est conservée
        service.onPickSuggestion(0, "l'ouverture")
        assertEquals("l'ouverture ", text())
    }

    @Test
    fun lesMotsElidesTapesNeSontPasDeformes() {
        val mots = listOf("l'attaquant", "l'hérétique", "m'as", "m'intéresse",
            "m'ennuyer", "m'est-il", "l'astiqué", "l'école", "j'ai", "qu'il")
        for (mot in mots) {
            edit.setText("")
            service.onStartInputView(service.editorInfo, false)
            type("$mot ")
            assertEquals("le mot élidé a été déformé", "$mot ", text())
        }
    }

    @Test
    fun lesMotsElidesTapesSontProposes() {
        edit.setText("")
        service.onStartInputView(service.editorInfo, false)
        type("m'inté")
        val s = service.visibleSuggestions()
        assertTrue("propositions : $s", s.all { it.startsWith("m'") })
        edit.setText("")
        service.onStartInputView(service.editorInfo, false)
        type("l'att")
        val s2 = service.visibleSuggestions()
        assertTrue("propositions : $s2", s2.count { it.length > 5 } >= 1)
    }

    @Test
    fun laCorrectionAutomatiqueSAppliqueALEspace() {
        type("bonjoyr ")
        assertEquals("bonjour ", text())
    }
}

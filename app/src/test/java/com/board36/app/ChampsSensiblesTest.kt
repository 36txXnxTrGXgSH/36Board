package com.board36.app

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.board36.app.data.Prefs
import com.board36.app.data.UserData
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
import java.io.File

/**
 * Ce qui se tape dans un champ mot de passe ou en navigation privée ne doit ni se voir,
 * ni se retenir, ni être modifié en douce ; et « Apprendre mes mots » coupé doit l'être
 * pour de bon. Chacun de ces tests échouait sur la 1.12 (constatés sur un téléphone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChampsSensiblesTest {

    class TestService : Board36Service() {
        lateinit var connection: InputConnection
        var editorInfo = EditorInfo()
        override fun getCurrentInputConnection(): InputConnection = connection
        override fun getCurrentInputEditorInfo(): EditorInfo = editorInfo
        override fun runBackground(task: () -> Unit) = task()
        override fun runMain(task: () -> Unit) = task()
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        prefs = Prefs(context)
        prefs.autoCap = false
        prefs.vibrate = false
        prefs.sound = false
        prefs.suggestions = true
        prefs.autoCorrect = false        // le mot tapé est validé tel quel
        prefs.learnWords = true
        prefs.doubleSpacePeriod = true
        prefs.highlightTypedLetters = true
        prefs.recentEmoji = emptyList()
    }

    private val PASSWORD = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    private val TEXT = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    /** Service branché sur un vrai champ du type demandé. */
    private fun open(inputType: Int, imeOptions: Int = 0): Pair<TestService, EditText> {
        val edit = EditText(context)
        edit.inputType = inputType
        val service = Robolectric.buildService(TestService::class.java).create().get()
        service.editorInfo = EditorInfo().apply { this.inputType = inputType; this.imeOptions = imeOptions }
        service.connection = edit.onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)
        return service to edit
    }

    private fun TestService.type(s: String) {
        for (c in s) {
            if (c == ' ') onKey(Codes.SPACE, null) else onKey(c.code, c.toString())
        }
    }

    /** Ce que le clavier a réellement enregistré, relu sur le disque après fermeture du champ. */
    private fun TestService.learned(word: String): Boolean {
        onFinishInputView(true)
        return UserDictionary(File(context.filesDir, UserData.DICTIONARY_FILE)).also { it.load() }.contains(word)
    }

    // ------------------------------------------------------------ mots de passe

    @Test
    fun lesLettresDUnMotDePasseNeSAllumentPas() {
        val (s, _) = open(PASSWORD)
        s.type("chat")
        assertTrue("lettres allumées : ${s.highlightedLetters()}", s.highlightedLetters().isEmpty())
    }

    @Test
    fun leDoubleEspaceNeModifiePasUnMotDePasse() {
        val (s, edit) = open(PASSWORD)
        s.type("a  ")
        assertEquals("a  ", edit.text.toString())
    }

    @Test
    fun aucunTraceNEcritDansUnMotDePasse() {
        val (s, edit) = open(PASSWORD)
        s.type("chat")
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        s.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        assertEquals("ni mot tracé, ni espace ajoutée", "chat", edit.text.toString())
    }

    @Test
    fun rienNEstApprisDansUnMotDePasse() {
        val (s, _) = open(PASSWORD)
        s.onPickSuggestion(0, "zorglubien")
        assertFalse(s.learned("zorglubien"))
    }

    @Test
    fun unEmojiTapeDansUnMotDePasseNeVaPasDansLesRecents() {
        val (s, _) = open(PASSWORD)
        s.onEmoji("🔒")
        assertTrue(Prefs(context).recentEmoji.isEmpty())
    }

    // ------------------------------------------------------------ navigation privée

    @Test
    fun laNavigationPriveeNApprendRien() {
        val (s, _) = open(TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        s.type("zorglubien ")
        assertFalse("mot appris en navigation privée", s.learned("zorglubien"))
        s.onEmoji("🎉")
        assertTrue(Prefs(context).recentEmoji.isEmpty())
    }

    @Test
    fun laNavigationPriveeGardeLesPropositions() {
        val (s, _) = open(TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        s.type("bonj")
        assertTrue("plus aucune proposition", s.visibleSuggestions().isNotEmpty())
    }

    @Test
    fun unChampOrdinaireApprendToujours() {
        // témoin : sans cela, les tests ci-dessus réussiraient aussi avec un clavier qui
        // n'apprend plus rien nulle part
        val (s, _) = open(TEXT)
        s.type("zorglubien ")
        assertTrue(s.learned("zorglubien"))
    }

    // ------------------------------------------------------------ « Apprendre mes mots »

    @Test
    fun choisirUnePropositionNApprendPasQuandLApprentissageEstCoupe() {
        prefs.learnWords = false
        val (s, _) = open(TEXT)
        s.type("bonj")
        s.onPickSuggestion(1, "bonjourno")
        assertFalse("appris malgré le réglage", s.learned("bonjourno"))
    }

    @Test
    fun choisirUnePropositionApprendQuandLApprentissageEstActif() {
        val (s, _) = open(TEXT)
        s.type("bonj")
        s.onPickSuggestion(1, "bonjourno")
        assertTrue(s.learned("bonjourno"))
    }
}

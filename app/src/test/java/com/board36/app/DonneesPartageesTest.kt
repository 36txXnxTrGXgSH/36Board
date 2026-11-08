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
import com.board36.app.engine.WordBlocklist
import com.board36.app.ime.Board36Service
import com.board36.app.ime.Codes
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
 * Le clavier et l'écran des réglages travaillent sur les mêmes données. Sur la 1.12 chacun
 * avait sa copie : le clavier réécrivait la sienne par-dessus les suppressions faites dans
 * les réglages, et les mots bloqués depuis les réglages pouvaient être écrasés.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DonneesPartageesTest {

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

    @Before
    fun setUp() {
        Prefs(context).apply {
            autoCap = false; vibrate = false; sound = false
            suggestions = true; autoCorrect = false; learnWords = true
        }
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.connection = EditText(context).onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)
    }

    private fun type(s: String) {
        for (c in s) if (c == ' ') service.onKey(Codes.SPACE, null) else service.onKey(c.code, c.toString())
    }

    private fun dictionaryOnDisk() =
        UserDictionary(File(context.filesDir, UserData.DICTIONARY_FILE)).also { it.load() }

    private fun blocklistOnDisk() =
        WordBlocklist(File(context.filesDir, UserData.BLOCKLIST_FILE)).also { it.load() }

    @Test
    fun unMotSupprimeDansLesReglagesNeRevientPas() {
        type("zorglubien ")
        service.onFinishInputView(true)
        assertTrue("le mot doit d'abord être appris", dictionaryOnDisk().contains("zorglubien"))

        // l'écran des réglages supprime le mot, comme son bouton « Supprimer »
        val reglages = UserData.dictionary(context)
        reglages.removeWord("zorglubien")
        reglages.save()

        // le clavier continue de servir, apprend autre chose, puis enregistre
        service.onStartInputView(service.editorInfo, false)
        type("bonjour madame ")
        service.onFinishInputView(true)

        assertFalse("le mot supprimé est revenu", dictionaryOnDisk().contains("zorglubien"))
    }

    @Test
    fun unMotBloqueDansLesReglagesResteBloque() {
        // le champ des réglages utilise le clavier : il s'ouvre avant le blocage...
        service.onStartInputView(service.editorInfo, false)
        val reglages = UserData.blocklist(context)
        reglages.add("truc")
        reglages.save()
        // ... et se ferme après, ce qui fait enregistrer le clavier
        service.onFinishInputView(true)

        assertTrue("le mot bloqué a été écrasé", blocklistOnDisk().isBlocked("truc"))
    }
}

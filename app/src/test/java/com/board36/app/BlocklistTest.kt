package com.board36.app

import android.content.Context
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.board36.app.data.Prefs
import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.Suggester
import com.board36.app.engine.UserDictionary
import com.board36.app.engine.WordBlocklist
import com.board36.app.ime.Board36Service
import com.board36.app.ime.SuggestionStripView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mots interdits : ce qui est bloqué ne doit plus jamais sortir, ni au tracé, ni à la
 * frappe, ni par correction automatique — et le blocage doit se faire en un geste depuis
 * la bande de propositions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BlocklistTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // ------------------------------------------------------------ stockage

    @Test
    fun refuserLeBlocageNeChangeRien() {
        val strip = SuggestionStripView(context)
        var blocked: String? = null
        strip.listener = object : SuggestionStripView.Listener {
            override fun onPickSuggestion(index: Int, word: String) {}
            override fun onToolbarAction(action: Int) {}
            override fun onBlockWord(word: String) { blocked = word }
        }
        strip.setSuggestions(listOf("bonjour", "bonsoir"), -1)
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY)
        )
        strip.layout(0, 0, 1080, 130)
        strip.showBlockPrompt("bonsoir")

        // bouton d'annulation : le dernier à droite
        send(strip, MotionEvent.ACTION_UP, 1080f - 20f)
        assertEquals(null, blocked)
        assertEquals(null, strip.blockPromptWord())
    }

    @Test
    fun laListeSeConserveEtIgnoreLaCasse() {
        val f = File.createTempFile("blocked", ".txt").also { it.delete() }
        val list = WordBlocklist(f)
        list.add("Afrique")
        list.save()

        val relu = WordBlocklist(f)
        relu.load()
        assertTrue(relu.isBlocked("afrique"))
        assertTrue(relu.isBlocked("AFRIQUE"))
        assertFalse("le blocage ne doit pas déborder sur un autre mot", relu.isBlocked("afriques"))
        relu.remove("afrique")
        assertFalse(relu.isBlocked("Afrique"))
        f.delete()
    }

    // ------------------------------------------------------------ moteurs

    @Test
    fun leMotBloqueDisparaitDesTraces() {
        val list = WordBlocklist(null)
        val decoder = GestureDecoder(TestKeyboard.dictionary, UserDictionary(null), list)
        val (xs, ys, n) = TestKeyboard.gestureFor("couverture")

        val avant = decoder.decode(xs, ys, n, TestKeyboard.geometry)
        assertEquals("couverture", avant.first())

        list.add("couverture")
        val apres = decoder.decode(xs, ys, n, TestKeyboard.geometry)
        assertFalse("le mot bloqué est encore proposé : $apres", apres.contains("couverture"))
        assertTrue("il doit rester d'autres propositions", apres.isNotEmpty())
    }

    @Test
    fun leMotBloqueNestPlusProposeNiCorrige() {
        val list = WordBlocklist(null)
        val suggester = Suggester(TestKeyboard.dictionary, UserDictionary(null), list)

        assertTrue(suggester.suggest("bonjo", null).words.contains("bonjour"))
        assertEquals("bonjour", suggester.suggest("bonjoyr", null).autoCorrect)

        list.add("bonjour")
        val r = suggester.suggest("bonjo", null)
        assertFalse("encore proposé : ${r.words}", r.words.contains("bonjour"))
        val corr = suggester.suggest("bonjoyr", null)
        assertTrue("correction vers un mot bloqué : ${corr.autoCorrect}",
            corr.autoCorrect != "bonjour")
    }

    @Test
    fun unMotElideBloqueNeRessortPas() {
        val list = WordBlocklist(null)
        val decoder = GestureDecoder(TestKeyboard.dictionary, UserDictionary(null), list)
        val (xs, ys, n) = TestKeyboard.gestureFor("lattaquant")
        assertEquals("l'attaquant", decoder.decode(xs, ys, n, TestKeyboard.geometry).first())

        list.add("l'attaquant")
        val apres = decoder.decode(xs, ys, n, TestKeyboard.geometry)
        assertFalse("encore proposé : $apres", apres.contains("l'attaquant"))
    }

    // ------------------------------------------------------------ bande

    @Test
    fun unAppuiLongSurUneProposotionDemandeLeBlocage() {
        val strip = SuggestionStripView(context)
        var blocked: String? = null
        strip.listener = object : SuggestionStripView.Listener {
            override fun onPickSuggestion(index: Int, word: String) {}
            override fun onToolbarAction(action: Int) {}
            override fun onBlockWord(word: String) { blocked = word }
        }
        strip.setSuggestions(listOf("bonjour", "bonsoir", "bonjours"), -1)
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY)
        )
        strip.layout(0, 0, 1080, 130)

        val x = suggestionCenter(1)   // deuxième proposition

        send(strip, MotionEvent.ACTION_DOWN, x)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
        assertEquals("bonsoir", strip.blockPromptWord())

        // le doigt qui a déclenché l'appui long se relève : la question doit rester
        send(strip, MotionEvent.ACTION_UP, x)
        assertEquals("la question s'est refermée toute seule", "bonsoir", strip.blockPromptWord())
        assertEquals(null, blocked)

        // puis on touche la coche
        send(strip, MotionEvent.ACTION_UP, yesCenter())
        assertEquals("bonsoir", blocked)
        assertEquals(null, strip.blockPromptWord())
    }

    @Test
    fun laQuestionSeFermeToutSeuleAuBoutDunMoment() {
        val strip = stripWithSuggestions()
        strip.showBlockPrompt("bonsoir")
        assertEquals("bonsoir", strip.blockPromptWord())
        shadowOf(android.os.Looper.getMainLooper())
            .idleFor(SuggestionStripView.PROMPT_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS)
        assertEquals(null, strip.blockPromptWord())
    }

    @Test
    fun glisserDirectementSurLaCocheBloqueLeMot() {
        var blocked: String? = null
        val strip = stripWithSuggestions { blocked = it }
        send(strip, MotionEvent.ACTION_DOWN, suggestionCenter(1))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
        // sans lever le doigt, on glisse jusqu'à la coche et on relâche là
        send(strip, MotionEvent.ACTION_UP, yesCenter())
        assertEquals("bonsoir", blocked)
        assertEquals(null, strip.blockPromptWord())
    }

    /** Centre de la n-ième proposition : la bande réserve un bouton à chaque bout. */
    private fun suggestionCenter(index: Int, count: Int = 3): Float {
        val side = 130 * 0.9f
        val w = (1080 - 2f * side) / count
        return side + w * (index + 0.5f)
    }

    /** Centre du bouton de confirmation, calculé comme la vue le fait. */
    private fun yesCenter(): Float {
        val size = 130 * 0.62f
        val margin = 8f * context.resources.displayMetrics.density
        return 1080 - size - margin - margin - size / 2f
    }

    private fun stripWithSuggestions(onBlock: (String) -> Unit = {}): SuggestionStripView {
        val strip = SuggestionStripView(context)
        strip.listener = object : SuggestionStripView.Listener {
            override fun onPickSuggestion(index: Int, word: String) {}
            override fun onToolbarAction(action: Int) {}
            override fun onBlockWord(word: String) { onBlock(word) }
        }
        strip.setSuggestions(listOf("bonjour", "bonsoir", "bonjours"), -1)
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY)
        )
        strip.layout(0, 0, 1080, 130)
        return strip
    }

    private fun send(v: View, action: Int, x: Float) {
        val e = MotionEvent.obtain(0, 0, action, x, 65f, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    // ------------------------------------------------------------ bout en bout

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

    @Test
    fun bloquerLeMotTraceLeRemplaceParLaVarianteSuivante() {
        File(context.filesDir, "blocked_words.txt").delete()
        Prefs(context).apply { autoCap = false; vibrate = false }

        val edit = EditText(context)
        val service = Robolectric.buildService(TestService::class.java).create().get()
        service.connection = edit.onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)

        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        service.onGestureFinished(xs, ys, n, TestKeyboard.geometry)
        assertEquals("bonjour", edit.text.toString())

        val variantes = service.visibleSuggestions()
        val suivante = variantes[1]

        service.onBlockWord("bonjour")

        assertEquals("le mot bloqué doit céder la place", suivante, edit.text.toString())
        assertTrue(service.blockedWords().contains("bonjour"))
        assertFalse("le mot bloqué reste proposé : ${service.visibleSuggestions()}",
            service.visibleSuggestions().contains("bonjour"))

        File(context.filesDir, "blocked_words.txt").delete()
    }
}

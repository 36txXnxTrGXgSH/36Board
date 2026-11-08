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
import com.board36.app.engine.Emoji
import com.board36.app.ime.Board36Service
import com.board36.app.ime.Codes
import com.board36.app.ime.EmojiPanelView
import com.board36.app.ime.SuggestionStripView
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
 * Le panneau des emoji : son catalogue, son maniement au doigt, et ce qu'il écrit
 * réellement dans le champ de texte — un emoji est plusieurs caractères, il doit
 * s'insérer et s'effacer d'un bloc.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class EmojiTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val width = 1080
    private val height = 900

    // ------------------------------------------------------------ catalogue

    @Test
    fun leCatalogueCouvreToutesLesFamilles() {
        // récents + neuf familles
        assertEquals(10, Emoji.CATEGORIES.size)
        assertEquals(Emoji.RECENTS, Emoji.CATEGORIES[0].label)
        assertTrue(Emoji.CATEGORIES[0].items.isEmpty())
        for (c in Emoji.CATEGORIES.drop(1)) {
            assertTrue("famille vide : ${c.label}", c.items.size >= 80)
        }
        assertTrue("catalogue trop maigre : ${Emoji.ALL.size}", Emoji.ALL.size >= 1000)
    }

    @Test
    fun aucunEmojiNEstEnDoubleNiVide() {
        val doublons = Emoji.ALL.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("emoji en double : ${doublons.size}", doublons.isEmpty())
        assertTrue(Emoji.ALL.none { it.isBlank() })
    }

    @Test
    fun onTrouveLesEmojiLesPlusCourants() {
        for (e in listOf("😀", "😂", "❤️", "👍", "🙏", "🎉", "🔥", "😍", "🇫🇷")) {
            assertTrue("absent du catalogue : $e", Emoji.ALL.contains(e))
        }
    }

    // ------------------------------------------------------------ panneau

    private fun panel(): EmojiPanelView {
        val p = EmojiPanelView(context)
        p.desiredHeight = height
        p.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        p.layout(0, 0, width, height)
        return p
    }

    private fun tap(v: View, x: Float, y: Float) {
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(0, 0, action, x, y, 0)
            v.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    @Test
    fun toucherUneCaseChoisitLEmojiQuiSyTrouve() {
        val p = panel()
        var chosen: String? = null
        p.listener = object : EmojiPanelView.Listener {
            override fun onEmoji(emoji: String) { chosen = emoji }
            override fun onEmojiKey(code: Int) {}
        }
        val attendu = p.items()[5]
        val (x, y) = p.cellCenter(5)
        tap(p, x, y)
        assertEquals(attendu, chosen)
    }

    @Test
    fun lesOngletsChangentDeFamille() {
        val p = panel()
        p.listener = object : EmojiPanelView.Listener {
            override fun onEmoji(emoji: String) {}
            override fun onEmojiKey(code: Int) {}
        }
        val depart = p.items()
        tap(p, p.tabCenterX(4), 10f)
        assertEquals(4, p.category())
        assertFalse("la famille n'a pas changé", p.items() == depart)
    }

    @Test
    fun lesRecentsSaffichentEnPremierOnglet() {
        val p = panel()
        p.setCategory(EmojiPanelView.RECENTS_TAB)
        assertTrue(p.items().isEmpty())
        p.setRecents(listOf("😀", "🎉"))
        assertEquals(listOf("😀", "🎉"), p.items())
    }

    @Test
    fun laRangeeBasseRendLesLettresEtEfface() {
        val p = panel()
        val codes = ArrayList<Int>()
        p.listener = object : EmojiPanelView.Listener {
            override fun onEmoji(emoji: String) {}
            override fun onEmojiKey(code: Int) { codes.add(code) }
        }
        val y = height - 24f
        tap(p, width / 12f, y)              // ABC
        tap(p, width / 2f, y)               // espace
        tap(p, width - width / 12f, y)      // effacement
        assertEquals(listOf(Codes.LETTERS, Codes.SPACE, Codes.DELETE), codes)
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

    private lateinit var service: TestService
    private lateinit var edit: EditText

    @Before
    fun setUp() {
        val prefs = Prefs(context)
        prefs.autoCap = false
        prefs.vibrate = false
        prefs.sound = false
        prefs.recentEmoji = emptyList()

        edit = EditText(context)
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.connection = edit.onCreateInputConnection(EditorInfo())!!
        service.onCreateInputView()
        service.onStartInputView(service.editorInfo, false)
    }

    private fun type(s: String) {
        for (c in s) service.onKey(c.code, c.toString())
    }

    @Test
    fun lEmojiSInsereDansLeTexte() {
        service.onToolbarAction(SuggestionStripView.Tool.EMOJI)
        service.onEmoji("😀")
        assertEquals("😀", edit.text.toString())
        service.onEmoji("🎉")
        assertEquals("😀🎉", edit.text.toString())
    }

    @Test
    fun leMotEnCoursEstValideAvantLEmoji() {
        type("bonjour")
        service.onToolbarAction(SuggestionStripView.Tool.EMOJI)
        service.onEmoji("👍")
        assertEquals("bonjour👍", edit.text.toString())
        assertEquals("", service.composingText())
    }

    @Test
    fun unEmojiSEffaceDunSeulCoup() {
        service.onEmoji("😀")
        service.onEmoji("👍")
        assertEquals("😀👍", edit.text.toString())
        service.onEmojiKey(Codes.DELETE)
        assertEquals("😀", edit.text.toString())
        service.onEmojiKey(Codes.DELETE)
        assertEquals("", edit.text.toString())
    }

    @Test
    fun unEmojiCompositeSEffaceEntierEtNeLaissePasDeResidu() {
        // drapeau (deux indicateurs régionaux) et séquence liée par des jointures
        for (e in listOf("🇫🇷", "🏳️‍🌈", "❤️")) {
            edit.setText("")
            service.onEmoji(e)
            assertEquals(e, edit.text.toString())
            service.onEmojiKey(Codes.DELETE)
            assertEquals("résidu après effacement de $e", "", edit.text.toString())
        }
    }

    @Test
    fun leBoutonEmojiOuvreEtReferme() {
        service.onToolbarAction(SuggestionStripView.Tool.EMOJI)
        service.onEmoji("😀")
        // le bouton est resté visible au-dessus du panneau : il doit ramener aux lettres
        service.onToolbarAction(SuggestionStripView.Tool.EMOJI)
        type("ok")
        assertEquals("😀ok", edit.text.toString())
    }

    @Test
    fun laToucheAbcRevientAuClavier() {
        service.onToolbarAction(SuggestionStripView.Tool.EMOJI)
        service.onEmojiKey(Codes.LETTERS)
        type("ok")
        assertEquals("ok", edit.text.toString())
    }

    @Test
    fun lesEmojiUtilisesRemontentDansLesRecents() {
        service.onEmoji("😀")
        service.onEmoji("🎉")
        service.onEmoji("😀")
        assertEquals(listOf("😀", "🎉"), Prefs(context).recentEmoji)
    }

    @Test
    fun onPeutEcrireApresUnEmoji() {
        service.onEmoji("😀")
        service.onEmojiKey(Codes.LETTERS)
        type("salut")
        service.onKey(Codes.SPACE, null)
        assertEquals("😀salut ", edit.text.toString())
    }
}

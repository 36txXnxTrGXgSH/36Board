package com.board36.app

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.board36.app.ime.SuggestionStripView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La bande porte les propositions au centre et, à demeure aux deux extrémités, les
 * deux seuls boutons du clavier : les emoji à gauche, les réglages à droite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StripTest {

    private lateinit var strip: SuggestionStripView
    private val picked = ArrayList<String>()
    private val actions = ArrayList<Int>()

    private val width = 1080
    private val height = 130
    private val side = height * 0.9f

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        strip = SuggestionStripView(context)
        strip.listener = object : SuggestionStripView.Listener {
            override fun onPickSuggestion(index: Int, word: String) { picked.add(word) }
            override fun onToolbarAction(action: Int) { actions.add(action) }
        }
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        strip.layout(0, 0, width, height)
    }

    private fun tap(x: Float) {
        val y = height / 2f
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(0, 0, action, x, y, 0)
            strip.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    /** Centre de la n-ième proposition, entre les deux boutons. */
    private fun suggestionCenter(index: Int, count: Int): Float {
        val w = (width - 2f * side) / count
        return side + w * (index + 0.5f)
    }

    @Test
    fun lesDeuxBoutonsSontAuxExtremites() {
        strip.clear()
        tap(side / 2f)
        tap(width - side / 2f)
        assertEquals(
            listOf(SuggestionStripView.Tool.EMOJI, SuggestionStripView.Tool.SETTINGS),
            actions
        )
        assertTrue(picked.isEmpty())
    }

    @Test
    fun lesPropositionsOccupentLeCentre() {
        strip.setSuggestions(listOf("bonjour", "bonsoir", "bonjours"), -1)
        tap(suggestionCenter(1, 3))
        assertEquals(listOf("bonsoir"), picked)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun lesBoutonsRestentJoignablesPendantQuOnEcrit() {
        strip.setSuggestions(listOf("bonjour", "bonsoir", "bonjours"), -1)
        tap(side / 2f)
        tap(width - side / 2f)
        assertEquals(
            listOf(SuggestionStripView.Tool.EMOJI, SuggestionStripView.Tool.SETTINGS),
            actions
        )
        // les propositions sont toujours là, elles n'ont pas été remplacées par un menu
        assertEquals(listOf("bonjour", "bonsoir", "bonjours"), strip.suggestions())
        assertTrue(picked.isEmpty())
    }

    @Test
    fun lesTroisPropositionsSontJoignablesEntreLesBoutons() {
        strip.setSuggestions(listOf("un", "deux", "trois"), -1)
        for (i in 0 until 3) tap(suggestionCenter(i, 3))
        assertEquals(listOf("un", "deux", "trois"), picked)
        assertTrue(actions.isEmpty())
    }
}

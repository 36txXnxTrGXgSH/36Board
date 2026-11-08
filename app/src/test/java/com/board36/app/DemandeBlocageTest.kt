package com.board36.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.board36.app.ime.KeyboardTheme
import com.board36.app.ime.SuggestionStripView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Régression du gel de la 1.12 (P0) : un appui long sur une proposition longue ouvrait
 * « Ne plus proposer … ? », dont la troncature ne raccourcissait jamais le libellé. Le
 * dessin de la bande tournait sans fin et Android tuait le clavier (ANR).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // vraies mesures de texte : sans elles, pas de boucle
class DemandeBlocageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Mesure factice : 10 px par caractère. */
    private val tenPx: (String) -> Float = { it.length * 10f }

    @Test
    fun unMotCourtSAfficheEnEntier() {
        assertEquals("Ne plus proposer « chat » ?", SuggestionStripView.promptLabel("chat", 1000f, tenPx))
    }

    @Test
    fun unMotLongEstRaccourciDansLaLargeur() {
        val label = SuggestionStripView.promptLabel("a".repeat(500), 400f, tenPx)
        assertTrue("trop large : $label", tenPx(label) <= 400f)
        assertTrue(label.startsWith("Ne plus proposer « a"))
        assertTrue(label.endsWith("… » ?"))
    }

    @Test
    fun memeSansAucunePlaceLeLibelleSeTermine() {
        val label = SuggestionStripView.promptLabel("a".repeat(5000), 1f, tenPx)
        assertEquals("Ne plus proposer « a… » ?", label)
    }

    @Test(timeout = 10_000)
    fun laDemandeDeBlocageDUnMotTresLongSeDessine() {
        val strip = SuggestionStripView(context)
        strip.theme = KeyboardTheme.DARK
        strip.setSuggestions(listOf("a".repeat(60)), -1)
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY)
        )
        strip.layout(0, 0, 1080, 130)
        for (n in intArrayOf(20, 40, 60, 300)) {
            strip.showBlockPrompt("a".repeat(n))
            strip.draw(Canvas(Bitmap.createBitmap(1080, 130, Bitmap.Config.ARGB_8888)))
        }
        // arrivé ici, le dessin s'est terminé à chaque fois : la 1.12 restait bloquée à 50 lettres
    }
}

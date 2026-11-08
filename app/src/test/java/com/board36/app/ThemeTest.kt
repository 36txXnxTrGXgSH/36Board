package com.board36.app

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.board36.app.ime.KeyboardView
import com.board36.app.ime.Layouts
import com.board36.app.ime.ThemeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Thème réglable : les couleurs choisies sont respectées, les teintes dérivées restent
 * cohérentes, et le curseur de contraste agit vraiment sur la lisibilité.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTest {

    private val bg = 0xFF16181B.toInt()
    private val key = 0xFF2A2D32.toInt()
    private val text = 0xFFECEFF1.toInt()
    private val accent = 0xFF63C2B4.toInt()

    @Test
    fun lesCouleursChoisiesSontRespectees() {
        val t = ThemeBuilder.build(bg, key, text, accent, contrast = 50)
        assertEquals(bg, t.background)
        assertEquals(key, t.key)
        assertEquals(text, t.text)
        assertEquals(accent, t.accent)
        assertTrue(t.dark)
    }

    @Test
    fun leContrasteAgitSurLaLisibilite() {
        val doux = ThemeBuilder.build(bg, key, text, accent, contrast = 0)
        val neutre = ThemeBuilder.build(bg, key, text, accent, contrast = 50)
        val fort = ThemeBuilder.build(bg, key, text, accent, contrast = 100)

        val r = { t: com.board36.app.ime.KeyboardTheme ->
            ThemeBuilder.contrastRatio(t.text, t.key)
        }
        assertTrue("doux=${r(doux)} neutre=${r(neutre)}", r(doux) < r(neutre))
        assertTrue("neutre=${r(neutre)} fort=${r(fort)}", r(fort) > r(neutre))

        // les touches se détachent aussi davantage du fond
        val sep = { t: com.board36.app.ime.KeyboardTheme ->
            ThemeBuilder.contrastRatio(t.key, t.background)
        }
        assertTrue("séparation touches/fond : ${sep(doux)} → ${sep(fort)}", sep(fort) > sep(doux))
    }

    @Test
    fun unThemeClairEstDetecteEtLesTeintesSuivent() {
        val clair = ThemeBuilder.build(
            0xFFEFE7DC.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B2620.toInt(), 0xFFB4762A.toInt()
        )
        assertFalse(clair.dark)
        // sur fond clair, une touche enfoncée s'assombrit
        assertTrue(ThemeBuilder.luminance(clair.keyPressed) < ThemeBuilder.luminance(clair.key))
    }

    @Test
    fun lesTeintesDeriveesSeDistinguent() {
        val t = ThemeBuilder.build(bg, key, text, accent)
        assertTrue(t.keyPressed != t.key)
        assertTrue(t.functionKey != t.key)
        assertTrue(t.secondaryText != t.text)
        assertTrue(t.popupBackground != t.background)
    }

    @Test
    fun leTexteDeLaToucheEntreeResteLisible() {
        val surAccentClair = ThemeBuilder.build(bg, key, text, 0xFFFFE066.toInt())
        assertTrue("texte sombre attendu sur un accent clair",
            ThemeBuilder.luminance(surAccentClair.actionText) < 0.3f)
        val surAccentSombre = ThemeBuilder.build(bg, key, text, 0xFF204080.toInt())
        assertTrue("texte clair attendu sur un accent sombre",
            ThemeBuilder.luminance(surAccentSombre.actionText) > 0.7f)
    }

    @Test
    fun toutesLesPalettesSontLisibles() {
        val faibles = ThemeBuilder.PALETTES.filter {
            ThemeBuilder.contrastRatio(
                ThemeBuilder.build(it).text, ThemeBuilder.build(it).key
            ) < 4.5f
        }
        assertTrue("palettes peu lisibles : ${faibles.map { it.name }}", faibles.isEmpty())
    }

    @Test
    fun lesLettresDuMotEnCoursChangentDeCouleur() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kb = KeyboardView(context)
        kb.theme = ThemeBuilder.build(bg, key, text, accent)
        kb.highlightColor = Color.RED
        kb.setLayout(Layouts.letters(numberRow = false))
        kb.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        kb.layout(0, 0, 1080, 600)

        val keys = kb.currentLayout()!!.keys
        val a = keys.first { it.label == "a" }
        val z = keys.first { it.label == "z" }

        kb.setHighlightedLetters(setOf('a', 'b'))
        assertEquals(Color.RED, kb.labelColor(a))
        assertEquals(kb.theme.text, kb.labelColor(z))

        // désactivé, plus aucune lettre n'est colorée
        kb.highlightEnabled = false
        assertEquals(kb.theme.text, kb.labelColor(a))
    }
}

package com.board36.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.UserDictionary
import com.board36.app.ime.EmojiPanelView
import com.board36.app.ime.KeyboardTheme
import com.board36.app.ime.KeyboardView
import com.board36.app.ime.Layouts
import com.board36.app.ime.PopupOverlayView
import com.board36.app.ime.SuggestionStripView
import com.board36.app.ime.ThemeBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Images de la page de présentation (docs/index.html), dessinées par les vrais composants
 * du clavier : aucune capture de téléphone, donc aucune donnée personnelle.
 *
 *     BOARD36_CAPTURES=../docs/images ./gradlew testDebugUnitTest --tests "*CapturesPresentationTest*"
 *
 * Sans la variable, les images vont dans app/build/presentation (elles sont alors jetables).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturesPresentationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val out = File(System.getenv("BOARD36_CAPTURES") ?: "build/presentation").apply { mkdirs() }

    // proportions du clavier sur un téléphone : bande de 44 dp, rangées de 54 dp (xxhdpi)
    private val imgW = 1080
    private val stripH = 132
    private val rowH = 162

    private fun measure(v: View, w: Int, h: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
    }

    private fun strip(palette: KeyboardTheme, words: List<String>, highlight: Int = -1) =
        SuggestionStripView(context).apply {
            theme = palette
            if (words.isEmpty()) clear() else setSuggestions(words, highlight)
            measure(this, imgW, stripH)
        }

    private fun keyboard(theme: KeyboardTheme, lit: Set<Char> = emptySet()): KeyboardView {
        val kb = KeyboardView(context)
        kb.theme = theme
        val layout = Layouts.letters(numberRow = true)
        kb.desiredHeight = rowH * layout.rows.size
        kb.setLayout(layout)
        measure(kb, imgW, rowH * layout.rows.size)
        kb.setHighlightedLetters(lit)
        return kb
    }

    /** Bande + clavier, comme le service les empile ; [extra] dessine par-dessus (calque). */
    private fun save(name: String, top: View, bottom: View, extra: ((Canvas) -> Unit)? = null) {
        val h = stripH + bottom.height
        val bmp = Bitmap.createBitmap(imgW, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // chaque vue dans sa zone, comme à l'écran : un drawColor remplit tout le canevas découpé
        c.save(); c.clipRect(0, 0, imgW, stripH); top.draw(c); c.restore()
        c.save(); c.translate(0f, stripH.toFloat()); c.clipRect(0, 0, imgW, bottom.height)
        bottom.draw(c); c.restore()
        extra?.invoke(c)
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }


    @Test
    fun imagesDeLaPageDePresentation() {
        val dark = KeyboardTheme.DARK
        val light = KeyboardTheme.LIGHT

        // 1. le clavier pendant la frappe : mot en cours, lettres colorées, proposition mise en avant
        save("clavier-sombre.png",
            strip(dark, listOf("bonj", "bonjour", "bonjours"), 1),
            keyboard(dark, setOf('b', 'o', 'n', 'j')))

        // 1 bis. l'image d'ouverture : « week » propose « week-end »
        save("ouverture.png",
            strip(dark, listOf("week", "week-end", "weekend"), 1),
            keyboard(dark, setOf('w', 'e', 'k')))

        // 2. thème clair
        save("clavier-clair.png",
            strip(light, listOf("à", "demain", "bientôt")),
            keyboard(light))

        // 3. le tracé d'un mot (chemin complet), et les variantes proposées par le vrai décodeur
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour", 0.04f)
        val variants = GestureDecoder(TestKeyboard.dictionary, UserDictionary(null))
            .decode(xs, ys, n, TestKeyboard.geometry).take(3)
        assertTrue("le décodeur doit trouver « bonjour » : $variants", variants.firstOrNull() == "bonjour")
        val sx = imgW / TestKeyboard.WIDTH
        val sy = (rowH * 5f) / (TestKeyboard.ROW_H * 5f)
        val trace = android.graphics.Path().apply {
            moveTo(xs[0] * sx, stripH + ys[0] * sy)
            for (i in 1 until n) lineTo(xs[i] * sx, stripH + ys[i] * sy)
        }
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            color = (dark.accent and 0x00FFFFFF) or (0xD0 shl 24)
        }
        save("swipe.png", strip(dark, variants, 0), keyboard(dark)) { it.drawPath(trace, ink) }

        // 4. le vocabulaire des messages
        save("familier.png",
            strip(dark, listOf("mdr", "ptdr", "tkt")),
            keyboard(dark))

        // 5. le panneau des emoji
        val emoji = EmojiPanelView(context).apply {
            theme = dark
            desiredHeight = rowH * 5
            measure(this, imgW, rowH * 5)
        }
        save("emoji.png", strip(dark, emptyList()), emoji)

        // 6. les accents par appui long
        val kbAcc = keyboard(light)
        val overlay = PopupOverlayView(context).apply { theme = light }
        measure(overlay, imgW, stripH + rowH * 5)
        val e = kbAcc.currentLayout()!!.keys.first { it.label == "e" }
        overlay.showAccents(e, listOf("e", "é", "è", "ê", "ë", "€"), stripH.toFloat())
        save("accents.png", strip(light, listOf("été", "être", "elle")), kbAcc) { overlay.draw(it) }

        // 7. trois palettes
        for ((file, name) in listOf("theme-ocean.png" to "Océan", "theme-sable.png" to "Sable",
            "theme-violet.png" to "Violet")) {
            val t = ThemeBuilder.build(ThemeBuilder.PALETTES.first { it.name == name }, 60)
            save(file, strip(t, listOf("salut", "ça", "va")), keyboard(t, setOf('s', 'a', 'l')))
        }

        assertTrue(File(out, "clavier-sombre.png").length() > 10_000)
    }
}

package com.board36.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.board36.app.engine.KeyGeometry
import com.board36.app.ime.Key
import com.board36.app.ime.KeyboardTheme
import com.board36.app.ime.EmojiPanelView
import com.board36.app.ime.KeyboardView
import com.board36.app.ime.Layouts
import com.board36.app.ime.PopupOverlayView
import com.board36.app.ime.SuggestionStripView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendu réel du clavier hors appareil : on dessine la vue dans un bitmap et on
 * l'exporte en PNG. Sert à vérifier la disposition, les thèmes et le tracé du swipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = File("build/render").apply { mkdirs() }

    private fun prepare(view: View, w: Int, h: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
    }

    private fun render(view: View, w: Int, h: Int, name: String): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(outDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun keyboard(theme: KeyboardTheme, numberRow: Boolean = true): KeyboardView {
        val kb = KeyboardView(context)
        kb.theme = theme
        val layout = Layouts.letters(numberRow)
        kb.desiredHeight = ROW * layout.rows.size
        kb.setLayout(layout)
        return kb
    }

    @Test
    fun rendAzertySombreEtClair() {
        val dark = keyboard(KeyboardTheme.DARK)
        val b1 = render(dark, WIDTH, ROW * 5, "azerty-sombre.png")
        assertTrue(b1.width == WIDTH)

        val light = keyboard(KeyboardTheme.LIGHT)
        render(light, WIDTH, ROW * 5, "azerty-clair.png")

        val symbols = KeyboardView(context).apply {
            theme = KeyboardTheme.DARK
            val l = Layouts.symbols()
            desiredHeight = ROW * l.rows.size
            setLayout(l)
        }
        render(symbols, WIDTH, ROW * 4, "symboles.png")

        val symbols2 = KeyboardView(context).apply {
            theme = KeyboardTheme.DARK
            val l = Layouts.symbols2()
            desiredHeight = ROW * l.rows.size
            setLayout(l)
        }
        render(symbols2, WIDTH, ROW * 4, "symboles-2.png")
    }

    @Test
    fun rendLaBandeDeSuggestions() {
        val strip = SuggestionStripView(context)
        strip.theme = KeyboardTheme.DARK
        strip.setSuggestions(listOf("bonjoyr", "bonjour", "bonsoir"), 1)
        render(strip, WIDTH, STRIP, "suggestions.png")

        // sans proposition, il ne reste que les deux boutons, à chaque extrémité
        val vide = SuggestionStripView(context)
        vide.theme = KeyboardTheme.DARK
        vide.clear()
        render(vide, WIDTH, STRIP, "bande-vide.png")

        val block = SuggestionStripView(context)
        block.theme = KeyboardTheme.DARK
        block.setSuggestions(listOf("l'afrique", "l'astiqué", "laïque"), -1)
        block.showBlockPrompt("l'afrique")
        render(block, WIDTH, STRIP, "bloquer-mot.png")

        val videClair = SuggestionStripView(context)
        videClair.theme = KeyboardTheme.LIGHT
        videClair.clear()
        render(videClair, WIDTH, STRIP, "bande-vide-clair.png")
    }

    @Test
    fun rendLePanneauDesEmoji() {
        val panel = EmojiPanelView(context)
        panel.theme = KeyboardTheme.DARK
        panel.desiredHeight = ROW * 5
        val bmp = render(panel, WIDTH, ROW * 5, "emoji-sombre.png")
        assertTrue(bmp.width == WIDTH)

        val clair = EmojiPanelView(context)
        clair.theme = KeyboardTheme.LIGHT
        clair.desiredHeight = ROW * 5
        clair.setRecents(listOf("😀", "🎉", "👍"))
        clair.setCategory(EmojiPanelView.RECENTS_TAB)
        render(clair, WIDTH, ROW * 5, "emoji-recents-clair.png")
    }

    @Test
    fun rendLeTraceDuSwipe() {
        val kb = keyboard(KeyboardTheme.DARK)
        val geo = KeyGeometry()
        for (k in kb.currentLayout()!!.keys) {
            if (k.isLetter) geo.set(k.label[0], k.centerX(), k.centerY())
        }
        prepare(kb, WIDTH, ROW * 5)
        val (xs, ys, n) = TestKeyboard.gestureFor("bonjour")
        // on rejoue le tracé sur la vue : mise à l'échelle depuis la géométrie de test
        val sx = WIDTH / TestKeyboard.WIDTH
        val sy = (ROW * 5f) / (TestKeyboard.ROW_H * 5f)
        kb.dispatchTouchEvent(down(xs[0] * sx, ys[0] * sy))
        for (i in 1 until n) kb.dispatchTouchEvent(move(xs[i] * sx, ys[i] * sy))
        val bmp = render(kb, WIDTH, ROW * 5, "swipe-trace.png")
        assertTrue(bmp.height == ROW * 5)
        kb.dispatchTouchEvent(up(xs[n - 1] * sx, ys[n - 1] * sy))
    }

    @Test
    fun rendLApercuEtLesAccents() {
        val kb = keyboard(KeyboardTheme.DARK)
        prepare(kb, WIDTH, ROW * 5)
        val overlay = PopupOverlayView(context)
        overlay.theme = KeyboardTheme.DARK
        prepare(overlay, WIDTH, ROW * 5)

        val e: Key = kb.currentLayout()!!.keys.first { it.label == "e" }
        overlay.showAccents(e, listOf("e", "é", "è", "ê", "ë", "€"), 0f)
        val s: Key = kb.currentLayout()!!.keys.first { it.label == "s" }
        overlay.showPreview(s, false, 0f)

        val bmp = Bitmap.createBitmap(WIDTH, ROW * 5, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        kb.draw(canvas)
        overlay.draw(canvas)
        File(outDir, "accents.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(overlay.isAccentsVisible())
    }

    @Test
    fun rendUnThemePersonnaliseAvecLettresColorees() {
        val palette = com.board36.app.ime.ThemeBuilder.PALETTES.first { it.name == "Océan" }
        val theme = com.board36.app.ime.ThemeBuilder.build(palette, contrast = 70)
        val kb = KeyboardView(context)
        kb.theme = theme
        kb.highlightColor = 0xFFF2B84B.toInt()
        val layout = Layouts.letters(numberRow = false)
        kb.desiredHeight = ROW * layout.rows.size
        kb.setLayout(layout)
        kb.setHighlightedLetters(setOf('c', 'l', 'a', 'v'))
        render(kb, WIDTH, ROW * 4, "theme-ocean.png")

        val sable = com.board36.app.ime.ThemeBuilder.build(
            com.board36.app.ime.ThemeBuilder.PALETTES.first { it.name == "Sable" }, contrast = 60
        )
        val kb2 = KeyboardView(context)
        kb2.theme = sable
        kb2.highlightColor = 0xFFC0392B.toInt()
        val l2 = Layouts.letters(numberRow = false)
        kb2.desiredHeight = ROW * l2.rows.size
        kb2.setLayout(l2)
        kb2.setHighlightedLetters(setOf('b', 'o', 'n', 'j'))
        render(kb2, WIDTH, ROW * 4, "theme-sable.png")
    }

    private fun down(x: Float, y: Float) = event(MotionEvent.ACTION_DOWN, x, y)
    private fun move(x: Float, y: Float) = event(MotionEvent.ACTION_MOVE, x, y)
    private fun up(x: Float, y: Float) = event(MotionEvent.ACTION_UP, x, y)

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x, y, 0)

    companion object {
        const val WIDTH = 1080
        const val ROW = 150
        const val STRIP = 130
    }
}

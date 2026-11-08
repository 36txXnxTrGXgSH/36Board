package com.board36.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.board36.app.ime.Board36Service
import com.board36.app.ime.InputRootView
import com.board36.app.ime.PopupOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Régression : la vue de saisie ne doit jamais occuper toute la hauteur de l'écran.
 * Sinon la fenêtre du clavier couvre l'écran entier — le clavier se colle en haut et
 * la zone vide sous les touches intercepte les appuis destinés au texte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class InputViewSizeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun laRacineSeMesureSurLaColonne() {
        val root = InputRootView(context)

        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        val content = View(context)
        column.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 600))
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val overlay = PopupOverlayView(context)
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST)
        )
        assertEquals(600, root.measuredHeight)
        // le calque doit tout de même couvrir la colonne (accents, aperçus)
        assertEquals(600, overlay.measuredHeight)
        assertEquals(1080, overlay.measuredWidth)
    }

    @Test
    fun laMargeBasseRemonteLeClavier() {
        val root = InputRootView(context)
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.addView(View(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 600))
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.extraBottomPadding = 40

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST)
        )
        root.layout(0, 0, 1080, root.measuredHeight)
        // la fenêtre grandit de la marge, mais le clavier, lui, remonte d'autant
        assertEquals(640, root.measuredHeight)
        assertEquals(600, column.measuredHeight)
        assertEquals(600, column.bottom)
    }

    @Test
    fun laVueDuServiceTientEnBasDeLEcran() {
        val controller = Robolectric.buildService(Board36Service::class.java).create()
        val service = controller.get()
        val view = service.onCreateInputView()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST)
        )
        val h = view.measuredHeight
        println("hauteur de la vue de saisie : $h px (écran 2400)")
        assertTrue("le clavier est vide", h > 300)
        assertTrue("le clavier occupe tout l'écran : $h", h < 1400)
    }
}

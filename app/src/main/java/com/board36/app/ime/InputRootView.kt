package com.board36.app.ime

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

/**
 * Racine de la vue de saisie : une colonne (suggestions + clavier) et, par-dessus,
 * un calque pour les aperçus et les accents.
 *
 * Un `FrameLayout` ordinaire prendrait ici toute la hauteur disponible, parce qu'un
 * enfant en MATCH_PARENT (le calque) est mesuré avec la taille maximale et impose sa
 * hauteur au parent. La fenêtre du clavier occuperait alors tout l'écran : le clavier
 * se retrouverait collé en haut et la zone vide sous les touches intercepterait les
 * appuis destinés au texte de l'application.
 *
 * On mesure donc la hauteur sur la colonne seule, puis on force le calque à cette
 * même taille.
 */
class InputRootView(context: Context) : FrameLayout(context) {

    /** Place réservée à la barre de navigation, mesurée par le système. */
    private var insetBottom = 0

    /**
     * Marge réglée par l'utilisateur, en pixels. Elle s'ajoute à l'encart système : sur
     * les téléphones où celui-ci est mal rapporté, c'est elle qui remonte le clavier
     * au-dessus des touches de navigation.
     */
    var extraBottomPadding = 0
        set(v) { field = v; applyBottomPadding() }

    private fun applyBottomPadding() {
        val bottom = insetBottom + extraBottomPadding
        if (paddingBottom != bottom) setPadding(0, 0, 0, bottom)
    }

    /**
     * À partir d'Android 15, une application qui vise le SDK 35 dessine sous les barres
     * système : le clavier doit lui-même réserver la place de la barre de navigation,
     * sinon la dernière rangée passe dessous. Sur les versions antérieures le système
     * s'en charge et l'encart vaut 0.
     */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 35) {
            insetBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            applyBottomPadding()
        }
        return insets
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val column: View? = getChildAt(0)
        if (column == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        column.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val height = column.measuredHeight

        for (i in 1 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, height + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top - paddingBottom
        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, w, h)
        }
    }
}

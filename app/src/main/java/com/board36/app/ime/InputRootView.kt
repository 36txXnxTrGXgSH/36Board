package com.board36.app.ime

import android.content.Context
import android.graphics.Insets
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
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

    /** Place réservée à la barre de navigation (en bas, ou sur le côté en paysage). */
    private var nav: Insets = Insets.NONE

    /**
     * Marge réglée par l'utilisateur, en pixels. Elle s'ajoute à l'encart système : sur
     * les téléphones où celui-ci est mal rapporté, c'est elle qui remonte le clavier
     * au-dessus des touches de navigation.
     */
    var extraBottomPadding = 0
        set(v) { field = v; applyPadding() }

    private fun applyPadding() {
        val l = nav.left
        val r = nav.right
        val b = nav.bottom + extraBottomPadding
        if (paddingLeft != l || paddingRight != r || paddingBottom != b) setPadding(l, 0, r, b)
    }

    /**
     * À partir d'Android 15, une application qui vise le SDK 35 dessine sous les barres
     * système : le clavier doit lui-même réserver la place de la barre de navigation,
     * sinon la dernière rangée passe dessous. Sur les versions antérieures le système
     * s'en charge et l'encart vaut 0.
     */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 35) useInsets(navigationInsets(insets))
        return insets
    }

    /**
     * Relit la place de la barre de navigation. Le service l'appelle à chaque affichage
     * du clavier.
     *
     * Les encarts transmis à la fenêtre du clavier ne suffisent pas : il arrive qu'ils
     * annoncent une barre de navigation de hauteur nulle (constaté dans WhatsApp, après
     * un déverrouillage), et rien ne les corrige tant que le clavier reste affiché — la
     * dernière rangée passait alors sous les touches de navigation. On prend donc le plus
     * grand de trois relevés : les encarts reçus, ceux de la fenêtre au moment de
     * l'affichage, et ceux de l'écran (`WindowMetrics`), qui décrivent la barre de
     * navigation quel que soit l'état de la fenêtre du clavier.
     */
    fun refreshInsets() {
        if (Build.VERSION.SDK_INT < 35) return
        useInsets(navigationInsets(rootWindowInsets))
        requestApplyInsets()
    }

    private fun useInsets(insets: Insets) {
        if (insets == nav) return
        nav = insets
        applyPadding()
    }

    /** Plus grand encart de barre de navigation connu, qu'elle soit déclarée visible ou non. */
    private fun navigationInsets(dispatched: WindowInsets?): Insets {
        val type = WindowInsets.Type.navigationBars()
        var best = Insets.NONE
        dispatched?.let {
            best = Insets.max(best, it.getInsets(type))
            best = Insets.max(best, it.getInsetsIgnoringVisibility(type))
        }
        try {
            val screen = context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics?.windowInsets
            if (screen != null) best = Insets.max(best, screen.getInsetsIgnoringVisibility(type))
        } catch (_: Exception) {
            // contexte sans fenêtre (aperçu, tests) : on garde les encarts reçus
        }
        return Insets.of(best.left, 0, best.right, best.bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val column: View? = getChildAt(0)
        if (column == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        column.measure(
            MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val height = column.measuredHeight

        for (i in 1 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, height + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top - paddingBottom
        for (i in 0 until childCount) {
            getChildAt(i).layout(paddingLeft, 0, w - paddingRight, h)
        }
    }
}

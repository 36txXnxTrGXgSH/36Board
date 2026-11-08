package com.board36.app.ime

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Construit le thème du clavier à partir de quatre couleurs choisies par l'utilisateur
 * (fond, touches, caractères, accent) et d'un réglage de contraste.
 *
 * Les autres teintes — touche enfoncée, touches de fonction, texte secondaire, fond des
 * bulles — sont dérivées, pour qu'un thème reste cohérent quelles que soient les
 * couleurs choisies.
 */
object ThemeBuilder {

    /** Palette de départ proposée dans les réglages. */
    data class Palette(
        val name: String,
        val background: Int,
        val key: Int,
        val text: Int,
        val accent: Int
    )

    val PALETTES: List<Palette> = listOf(
        Palette("Sombre", 0xFF16181B.toInt(), 0xFF2A2D32.toInt(), 0xFFECEFF1.toInt(), 0xFF63C2B4.toInt()),
        Palette("Clair", 0xFFE3E6EB.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A1C1E.toInt(), 0xFF2F7F73.toInt()),
        Palette("Noir profond", 0xFF000000.toInt(), 0xFF141414.toInt(), 0xFFFFFFFF.toInt(), 0xFF00C2A8.toInt()),
        Palette("Océan", 0xFF0F212B.toInt(), 0xFF1B3440.toInt(), 0xFFE6F1F5.toInt(), 0xFF4FC3E8.toInt()),
        Palette("Sable", 0xFFEFE7DC.toInt(), 0xFFFFFDF9.toInt(), 0xFF2B2620.toInt(), 0xFFB4762A.toInt()),
        Palette("Violet", 0xFF1A1524.toInt(), 0xFF2A2338.toInt(), 0xFFEDE7F6.toInt(), 0xFFB388FF.toInt()),
        Palette("Forêt", 0xFF10231A.toInt(), 0xFF1B3428.toInt(), 0xFFE5F3EA.toInt(), 0xFF6FD08C.toInt()),
        Palette("Papier", 0xFFF3F1EC.toInt(), 0xFFFFFFFF.toInt(), 0xFF20242A.toInt(), 0xFF3D6CE0.toInt())
    )

    /**
     * @param contrast 0..100 (50 = neutre). Au-dessus, les touches se détachent du fond et
     *                 le texte force vers le blanc ou le noir ; en dessous, tout se rapproche.
     */
    fun build(
        background: Int,
        key: Int,
        text: Int,
        accent: Int,
        contrast: Int = 50
    ): KeyboardTheme {
        val dark = luminance(background) < 0.45f
        val way = if (dark) 1f else -1f          // « vers le clair » sur un thème sombre
        val c = (contrast - 50) / 50f            // -1 … +1

        // Monter le contraste, c'est écarter chaque élément de son voisin :
        // le fond s'éloigne des touches, les touches s'éloignent des caractères, et les
        // caractères filent vers le blanc ou le noir. Éclaircir les touches d'un thème
        // sombre ferait l'inverse : le texte clair s'y lirait moins bien.
        val textIsLight = luminance(text) > 0.5f
        val bgWay = if (luminance(background) < luminance(key)) -1f else 1f
        val keyWay = if (textIsLight) -1f else 1f

        val bg = shade(background, bgWay * 0.10f * c)
        val keyC = shade(key, keyWay * 0.06f * c)
        val textC = if (c >= 0f) {
            blend(text, if (textIsLight) Color.WHITE else Color.BLACK, 0.55f * c)
        } else {
            blend(text, keyC, 0.45f * -c)
        }

        return KeyboardTheme(
            background = bg,
            key = keyC,
            keyPressed = shade(keyC, way * 0.09f),
            functionKey = shade(keyC, -way * 0.05f),
            functionKeyPressed = shade(keyC, way * 0.04f),
            actionKey = accent,
            actionKeyPressed = shade(accent, way * 0.07f),
            text = textC,
            secondaryText = blend(textC, keyC, 0.42f),
            actionText = if (luminance(accent) > 0.55f) 0xFF101214.toInt() else Color.WHITE,
            accent = accent,
            trail = accent,
            popupBackground = shade(keyC, way * 0.07f),
            dark = dark
        )
    }

    fun build(palette: Palette, contrast: Int = 50): KeyboardTheme =
        build(palette.background, palette.key, palette.text, palette.accent, contrast)

    // ------------------------------------------------------------ couleurs

    /** Éclaircit ([amount] > 0) ou assombrit une couleur, en restant dans les bornes. */
    fun shade(color: Int, amount: Float): Int {
        if (amount == 0f) return color
        val target = if (amount > 0) 255 else 0
        val f = abs(amount).coerceIn(0f, 1f)
        return Color.argb(
            Color.alpha(color),
            mix(Color.red(color), target, f),
            mix(Color.green(color), target, f),
            mix(Color.blue(color), target, f)
        )
    }

    fun blend(a: Int, b: Int, ratio: Float): Int {
        val f = ratio.coerceIn(0f, 1f)
        return Color.argb(
            255,
            mix(Color.red(a), Color.red(b), f),
            mix(Color.green(a), Color.green(b), f),
            mix(Color.blue(a), Color.blue(b), f)
        )
    }

    private fun mix(from: Int, to: Int, f: Float): Int =
        (from + (to - from) * f).toInt().coerceIn(0, 255)

    /** Luminance relative (WCAG), 0 = noir, 1 = blanc. */
    fun luminance(color: Int): Float {
        fun channel(v: Int): Float {
            val s = v / 255f
            return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(Color.red(color)) +
            0.7152f * channel(Color.green(color)) +
            0.0722f * channel(Color.blue(color))
    }

    /** Rapport de contraste WCAG entre deux couleurs (1 = identiques, 21 = noir/blanc). */
    fun contrastRatio(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}

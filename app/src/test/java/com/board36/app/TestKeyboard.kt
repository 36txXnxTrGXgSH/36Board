package com.board36.app

import com.board36.app.engine.FrenchDictionary
import com.board36.app.engine.KeyGeometry
import com.board36.app.ime.Layouts
import java.io.File
import java.io.FileInputStream

/** Outils communs aux tests : vraie disposition AZERTY et vrai dictionnaire. */
object TestKeyboard {

    const val WIDTH = 1080f
    const val ROW_H = 150f

    val geometry: KeyGeometry by lazy {
        val layout = Layouts.letters(numberRow = true)
        layout.layout(WIDTH, ROW_H * layout.rows.size, 8f, 12f)
        val geo = KeyGeometry()
        var kw = 0f
        var kh = 0f
        for (k in layout.keys) {
            if (k.isLetter && k.label.length == 1) {
                geo.set(k.label[0], k.centerX(), k.centerY())
                kw = k.w + 8f
                kh = k.h + 12f
            }
        }
        geo.keyW = kw
        geo.keyH = kh
        geo
    }

    val dictionary: FrenchDictionary by lazy {
        val f = File("src/main/assets/fr_dict.txt")
        val file = if (f.exists()) f else File("app/src/main/assets/fr_dict.txt")
        FileInputStream(file).use { FrenchDictionary.load(it) }
    }

    /**
     * Fabrique un tracé plausible pour [word] : on relie les centres des touches en
     * échantillonnant régulièrement, avec une légère dérive (le doigt ne passe jamais
     * exactement sur les centres) et un arrondi dans les virages.
     */
    fun gestureFor(word: String, jitter: Float = 0.18f): Triple<FloatArray, FloatArray, Int> {
        val letters = word.lowercase().filter { it in 'a'..'z' }
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        var seed = word.hashCode().toLong() or 1L
        fun rnd(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33).toFloat() / Int.MAX_VALUE.toFloat()) - 0.5f
        }
        var prev = -1
        val pts = ArrayList<Pair<Float, Float>>()
        for (c in letters) {
            val i = c - 'a'
            if (i == prev) continue
            prev = i
            pts.add(geometry.cx[i] to geometry.cy[i])
        }
        if (pts.size < 2) return Triple(FloatArray(0), FloatArray(0), 0)
        for (s in 0 until pts.size - 1) {
            val (x0, y0) = pts[s]
            val (x1, y1) = pts[s + 1]
            val dist = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
            val steps = maxOf(4, (dist / 12f).toInt())
            for (t in 0 until steps) {
                val f = t.toFloat() / steps
                xs.add(x0 + (x1 - x0) * f + rnd() * geometry.keyW * jitter)
                ys.add(y0 + (y1 - y0) * f + rnd() * geometry.keyH * jitter)
            }
        }
        xs.add(pts.last().first + rnd() * geometry.keyW * jitter * 0.5f)
        ys.add(pts.last().second + rnd() * geometry.keyH * jitter * 0.5f)
        return Triple(xs.toFloatArray(), ys.toFloatArray(), xs.size)
    }
}

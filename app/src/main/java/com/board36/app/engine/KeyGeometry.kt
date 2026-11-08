package com.board36.app.engine

/** Position des 26 lettres sur le clavier courant, fournie par la vue au décodeur. */
class KeyGeometry {
    val cx = FloatArray(26)
    val cy = FloatArray(26)
    val present = BooleanArray(26)
    var keyW = 1f
    var keyH = 1f

    fun clear() {
        java.util.Arrays.fill(present, false)
    }

    /**
     * Copie figée de la géométrie. Le décodage d'un tracé se fait hors du fil principal,
     * pendant que la vue peut être remesurée (rotation, changement de hauteur) : sans
     * cette copie, le décodeur lirait des positions à moitié réécrites.
     */
    fun copy(): KeyGeometry {
        val c = KeyGeometry()
        cx.copyInto(c.cx)
        cy.copyInto(c.cy)
        present.copyInto(c.present)
        c.keyW = keyW
        c.keyH = keyH
        return c
    }

    fun set(letter: Char, x: Float, y: Float) {
        val i = letter.lowercaseChar() - 'a'
        if (i in 0..25) {
            cx[i] = x
            cy[i] = y
            present[i] = true
        }
    }

    fun isReady(): Boolean {
        var n = 0
        for (p in present) if (p) n++
        return n >= 20 && keyW > 0f
    }

    /** Distance du point au centre de la touche [i]. */
    fun dist(i: Int, x: Float, y: Float): Float {
        val dx = cx[i] - x
        val dy = cy[i] - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Les lettres les plus proches de (x,y), dans un rayon de [radius] * largeur de touche. */
    fun nearest(x: Float, y: Float, max: Int, radius: Float): IntArray {
        val limit = radius * keyW
        val found = ArrayList<Pair<Int, Float>>(8)
        var bestI = -1
        var bestD = Float.MAX_VALUE
        for (i in 0..25) {
            if (!present[i]) continue
            val dd = dist(i, x, y)
            if (dd < bestD) { bestD = dd; bestI = i }
            if (dd <= limit) found.add(i to dd)
        }
        if (found.isEmpty()) return if (bestI >= 0) intArrayOf(bestI) else IntArray(0)
        found.sortBy { it.second }
        val n = minOf(max, found.size)
        return IntArray(n) { found[it].first }
    }
}

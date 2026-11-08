package com.board36.app.ime

class KeyRow(val keys: List<Key>)

/** Une disposition complète, et le calcul qui place ses touches dans la vue. */
class KeyboardLayout(val rows: List<KeyRow>) {
    val keys: List<Key> = rows.flatMap { it.keys }

    /** Répartit les touches sur [width] x [height]. */
    fun layout(width: Float, height: Float, hGap: Float, vGap: Float) {
        val rowHeight = height / rows.size
        var y = 0f
        for (row in rows) {
            val total = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            for (k in row.keys) {
                val kw = width * (k.weight / total)
                k.x = x + hGap / 2f
                k.y = y + vGap / 2f
                k.w = kw - hGap
                k.h = rowHeight - vGap
                x += kw
            }
            y += rowHeight
        }
    }

    fun keyAt(px: Float, py: Float): Key? {
        for (k in keys) if (k.contains(px, py)) return k
        // Hors des touches (interstice ou bord) : on prend la plus proche de la même ligne.
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (k in keys) {
            if (py < k.y - k.h * 0.35f || py > k.y + k.h * 1.35f) continue
            val d = k.squaredDistanceTo(px, py)
            if (d < bestD) { bestD = d; best = k }
        }
        return best
    }
}

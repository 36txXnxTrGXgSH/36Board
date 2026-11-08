package com.board36.app.engine

/** Géométrie logique de l'AZERTY, utilisée pour la correction des fautes de frappe. */
object AzertyModel {

    private val ROWS = arrayOf("azertyuiop", "qsdfghjklm", "wxcvbn")
    private val ROW_X0 = floatArrayOf(0.5f, 0.5f, 2.5f)

    private val posX = HashMap<Char, Float>(26)
    private val posY = HashMap<Char, Float>(26)
    val neighbors = HashMap<Char, String>(26)

    init {
        for (r in ROWS.indices) {
            for (c in ROWS[r].indices) {
                val ch = ROWS[r][c]
                posX[ch] = ROW_X0[r] + c
                posY[ch] = r.toFloat()
            }
        }
        for (a in posX.keys) {
            val sb = StringBuilder()
            for (b in posX.keys) {
                if (a == b) continue
                val dx = posX[a]!! - posX[b]!!
                val dy = (posY[a]!! - posY[b]!!) * 1.1f
                if (dx * dx + dy * dy <= 1.5f) sb.append(b)
            }
            neighbors[a] = sb.toString()
        }
    }

    fun neighborsOf(c: Char): String = neighbors[c] ?: ""
}

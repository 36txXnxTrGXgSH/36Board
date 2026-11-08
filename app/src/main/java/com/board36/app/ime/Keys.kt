package com.board36.app.ime

/** Codes des touches de service. Les codes positifs sont des caractères Unicode. */
object Codes {
    const val SHIFT = -1
    const val DELETE = -2
    const val SYMBOLS = -3
    const val LETTERS = -4
    const val ENTER = -5
    const val SYMBOLS2 = -6
    const val NONE = 0
    const val SPACE = 32
}

enum class KeyStyle { NORMAL, FUNCTION, ACTION, SPACE }

/** Symboles dessinés au trait par [KeyboardView.drawIcon] (aucune ressource image). */
enum class KeyIcon { NONE, SHIFT, DELETE, ENTER }

data class Key(
    val code: Int,
    val label: String = "",
    val hint: String = "",
    val popup: String = "",
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    val icon: KeyIcon = KeyIcon.NONE,
    val repeatable: Boolean = false,
    val isLetter: Boolean = false
) {
    /** Rempli par le calcul de disposition. */
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f

    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h

    fun centerX() = x + w / 2f
    fun centerY() = y + h / 2f

    fun squaredDistanceTo(px: Float, py: Float): Float {
        val dx = px - centerX()
        val dy = py - centerY()
        return dx * dx + dy * dy
    }
}

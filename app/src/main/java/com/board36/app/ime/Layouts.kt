package com.board36.app.ime

/**
 * Dispositions du clavier. L'AZERTY suit la disposition française usuelle :
 * AZERTYUIOP / QSDFGHJKLM / (maj) WXCVBN ' (suppr).
 */
object Layouts {

    private fun letter(c: Char, hint: String = "", popup: String = "") =
        Key(code = c.code, label = c.toString(), hint = hint, popup = popup, isLetter = true)

    private fun sym(c: String, popup: String = "", hint: String = "") =
        Key(code = if (c.length == 1) c[0].code else Codes.NONE, label = c, popup = popup, hint = hint)

    private val ACCENTS = mapOf(
        'a' to "àâáäãå",
        'e' to "éèêë€",
        'i' to "îïìí",
        'o' to "ôöòóœø",
        'u' to "ùûüú",
        'y' to "ÿý",
        'c' to "çć",
        'n' to "ñń",
        's' to "śšß",
        'z' to "žź",
        'l' to "£",
        'm' to "µ",
        'p' to "π",
        'g' to "ĝ",
        't' to "†",
        'r' to "®",
        'w' to "ŵ",
        'x' to "×",
        'd' to "ð",
        'k' to "«»",
        'j' to "„",
        'h' to "•",
        'f' to "ƒ",
        'b' to "ß",
        'v' to "√",
        'q' to "%"
    )

    private const val ROW1 = "azertyuiop"
    private const val ROW2 = "qsdfghjklm"
    private const val ROW3 = "wxcvbn"
    private const val DIGITS = "1234567890"

    fun letters(numberRow: Boolean, enterLabel: String? = null): KeyboardLayout {
        val rows = ArrayList<KeyRow>(5)

        if (numberRow) {
            rows.add(KeyRow(DIGITS.mapIndexed { i, c ->
                Key(code = c.code, label = c.toString(), popup = NUM_POPUPS[i])
            }))
        }

        rows.add(KeyRow(ROW1.mapIndexed { i, c ->
            letter(c, hint = if (numberRow) "" else DIGITS[i].toString(),
                popup = buildPopup(c, if (numberRow) "" else DIGITS[i].toString()))
        }))

        rows.add(KeyRow(ROW2.map { c -> letter(c, popup = buildPopup(c, "")) }))

        val row3 = ArrayList<Key>()
        row3.add(Key(Codes.SHIFT, weight = 1.5f, style = KeyStyle.FUNCTION, icon = KeyIcon.SHIFT))
        ROW3.forEach { c -> row3.add(letter(c, popup = buildPopup(c, ""))) }
        row3.add(sym("'", popup = "’‘\"`"))
        row3.add(Key(Codes.DELETE, weight = 1.5f, style = KeyStyle.FUNCTION,
            icon = KeyIcon.DELETE, repeatable = true))
        rows.add(KeyRow(row3))

        rows.add(bottomRow("?123", Codes.SYMBOLS, enterLabel))
        return KeyboardLayout(rows)
    }

    private fun bottomRow(modeLabel: String, modeCode: Int, enterLabel: String?): KeyRow = KeyRow(
        listOf(
            Key(modeCode, label = modeLabel, weight = 1.5f, style = KeyStyle.FUNCTION),
            sym(",", popup = "?!;:…'\"()"),
            Key(Codes.SPACE, weight = 5f, style = KeyStyle.SPACE),
            sym(".", popup = "…,?!:;'\"-/"),
            Key(Codes.ENTER, label = enterLabel ?: "", weight = 1.5f,
                style = KeyStyle.ACTION, icon = if (enterLabel == null) KeyIcon.ENTER else KeyIcon.NONE)
        )
    )

    private val NUM_POPUPS = arrayOf("¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹", "⁰")

    private fun buildPopup(c: Char, hint: String): String {
        val sb = StringBuilder()
        if (hint.isNotEmpty()) sb.append(hint)
        ACCENTS[c]?.let { sb.append(it) }
        return sb.toString()
    }

    fun symbols(enterLabel: String? = null): KeyboardLayout {
        val rows = ArrayList<KeyRow>(4)
        rows.add(KeyRow(DIGITS.mapIndexed { i, c ->
            Key(code = c.code, label = c.toString(), popup = NUM_POPUPS[i])
        }))
        rows.add(KeyRow("@#€_&-+()/".map { c ->
            sym(c.toString(), popup = when (c) {
                '€' -> "$£¥¢₽"
                '-' -> "–—_·"
                '+' -> "±"
                '/' -> "\\"
                else -> ""
            })
        }))
        val row3 = ArrayList<Key>()
        row3.add(Key(Codes.SYMBOLS2, label = "=\\<", weight = 1.5f, style = KeyStyle.FUNCTION))
        "*\"':;!?".forEach { c ->
            row3.add(sym(c.toString(), popup = when (c) {
                '"' -> "“”«»"
                '\'' -> "’‘"
                '!' -> "¡"
                '?' -> "¿"
                else -> ""
            }))
        }
        row3.add(Key(Codes.DELETE, weight = 1.5f, style = KeyStyle.FUNCTION,
            icon = KeyIcon.DELETE, repeatable = true))
        rows.add(KeyRow(row3))
        rows.add(bottomRow("ABC", Codes.LETTERS, enterLabel))
        return KeyboardLayout(rows)
    }

    fun symbols2(enterLabel: String? = null): KeyboardLayout {
        val rows = ArrayList<KeyRow>(4)
        rows.add(KeyRow("~`|•√π÷×¶".map { sym(it.toString()) } + sym("∆")))
        rows.add(KeyRow("£¥$¢^°={}\\".map { sym(it.toString()) }))
        val row3 = ArrayList<Key>()
        row3.add(Key(Codes.SYMBOLS, label = "?123", weight = 1.5f, style = KeyStyle.FUNCTION))
        "%©®™§[]".forEach { row3.add(sym(it.toString())) }
        row3.add(Key(Codes.DELETE, weight = 1.5f, style = KeyStyle.FUNCTION,
            icon = KeyIcon.DELETE, repeatable = true))
        rows.add(KeyRow(row3))
        rows.add(bottomRow("ABC", Codes.LETTERS, enterLabel))
        return KeyboardLayout(rows)
    }
}

package com.board36.app.engine

/**
 * Normalisation du français : suppression des accents et des ligatures pour ramener
 * chaque mot sur les 26 touches de l'AZERTY (c'est ce que l'utilisateur trace ou tape).
 */
object Fr {

    /** Retourne la ou les lettres de base (a..z) correspondant à [c], ou "" si non pertinent. */
    fun base(c: Char): String = when (c) {
        in 'a'..'z' -> c.toString()
        in 'A'..'Z' -> (c + 32).toString()
        'à', 'â', 'ä', 'á', 'ã', 'å' -> "a"
        'À', 'Â', 'Ä', 'Á', 'Ã', 'Å' -> "a"
        'ç', 'Ç' -> "c"
        'é', 'è', 'ê', 'ë' -> "e"
        'É', 'È', 'Ê', 'Ë' -> "e"
        'î', 'ï', 'í', 'ì' -> "i"
        'Î', 'Ï', 'Í', 'Ì' -> "i"
        'ô', 'ö', 'ó', 'ò', 'õ' -> "o"
        'Ô', 'Ö', 'Ó', 'Ò', 'Õ' -> "o"
        'ù', 'û', 'ü', 'ú' -> "u"
        'Ù', 'Û', 'Ü', 'Ú' -> "u"
        'ÿ', 'ý', 'Ÿ', 'Ý' -> "y"
        'ñ', 'Ñ' -> "n"
        'œ', 'Œ' -> "oe"
        'æ', 'Æ' -> "ae"
        'ß' -> "ss"
        else -> ""
    }

    /** Mot ramené à l'alphabet des touches : minuscules, sans accent, sans ' ni -. */
    fun normalize(s: CharSequence): String {
        val sb = StringBuilder(s.length + 2)
        for (c in s) sb.append(base(c))
        return sb.toString()
    }

    /** Une lettre au sens du français (accents compris) : sert à délimiter les mots. */
    fun isWordChar(c: Char): Boolean = base(c).isNotEmpty() || c == '\'' || c == '’' || c == '-'

    /** Une lettre au sens strict (pas l'apostrophe ni le trait d'union). */
    fun isLetter(c: Char): Boolean = base(c).isNotEmpty()

    /** Met la première lettre en majuscule en respectant les accents. */
    fun capitalize(s: String): String =
        if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

    fun isAllUpper(s: String): Boolean {
        var hasLetter = false
        for (c in s) {
            if (c.isLetter()) {
                hasLetter = true
                if (!c.isUpperCase()) return false
            }
        }
        return hasLetter
    }
}

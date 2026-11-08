package com.board36.app.engine

import kotlin.math.ln

data class SuggestionResult(
    /** Propositions ordonnées, la meilleure d'abord. */
    val words: List<String>,
    /** Mot par lequel remplacer automatiquement la saisie à l'espace, ou null. */
    val autoCorrect: String?,
    /** Le mot tapé existe-t-il tel quel ? */
    val typedIsValid: Boolean
)

/**
 * Suggestions pendant la frappe : complétion par préfixe (accents ignorés, donc
 * « ecole » propose « école »), correction des fautes à une modification près
 * (touche voisine, lettre en trop, lettre manquante, inversion) et prédiction
 * du mot suivant à partir des enchaînements appris.
 */
class Suggester(
    private var dict: FrenchDictionary?,
    private val user: UserDictionary,
    private var blocklist: WordBlocklist? = null
) {

    fun setDictionary(d: FrenchDictionary?) { dict = d }
    fun setBlocklist(b: WordBlocklist?) { blocklist = b }

    fun suggest(typed: String, previousWord: String?, max: Int = 3): SuggestionResult {
        if (typed.isEmpty()) {
            val next = previousWord?.let { user.nextWords(it, max) } ?: emptyList()
            return SuggestionResult(next, null, true)
        }

        // Mots élidés (« j'ai », « l'école », « aujourd'hui ») : la recherche porte sur
        // la partie qui suit l'apostrophe, et l'élision est remise devant chaque
        // proposition. Sans cela « j'ai » se cherche comme « jai » et ne trouve rien —
        // ou pire, se fait « corriger ».
        val cut = elisionCut(typed)
        if (cut > 0) {
            val head = typed.substring(0, cut)
            val tail = typed.substring(cut)
            val whole = suggestPlain(typed, max)
            if (tail.isEmpty()) {
                return SuggestionResult(whole.words.filter { hasApostrophe(it) }.take(max), null, true)
            }
            val sub = suggestPlain(tail, max)
            val words = (whole.words.filter { hasApostrophe(it) } + sub.words.map { head + it })
                .distinct()
                .filterNot { blocklist?.isBlocked(it) == true }
                .take(max)
            val valid = whole.typedIsValid || sub.typedIsValid
            val auto = if (valid) null else sub.autoCorrect?.let { head + it }
                ?.takeUnless { blocklist?.isBlocked(it) == true }
            return SuggestionResult(words, auto, valid)
        }
        return suggestPlain(typed, max)
    }

    private fun hasApostrophe(w: String) = w.contains('\'') || w.contains('\u2019')

    /**
     * Position juste après l'apostrophe d'élision, ou -1. On n'accepte que les
     * élisions du français (j', l', d', qu', jusqu', aujourd'…) pour ne pas découper
     * un nom propre comme « O'Brien ».
     */
    private fun elisionCut(typed: String): Int {
        var idx = -1
        for (i in typed.indices) {
            if (typed[i] == '\'' || typed[i] == '\u2019') idx = i
        }
        if (idx <= 0 || idx >= typed.length) return -1
        val head = Fr.normalize(typed.substring(0, idx))
        return if (head in ELISIONS) idx + 1 else -1
    }

    private fun suggestPlain(typed: String, max: Int): SuggestionResult {

        val d = dict
        val norm = Fr.normalize(typed)
        val typedValid = (d?.containsExact(typed.lowercase()) ?: false) ||
            (d?.containsExact(typed) ?: false) || user.contains(typed)

        if (d == null || norm.isEmpty()) {
            return SuggestionResult(emptyList(), null, typedValid)
        }

        // Deux familles distinctes : ce que l'on complète, et ce que l'on corrige.
        // Les mélanger fait remonter « art » devant « attaquant » quand on tape « att »,
        // parce qu'un mot complet trouvé par correction marque plus qu'une complétion.
        val completions = HashMap<String, Float>(64)
        val corrections = HashMap<String, Float>(64)

        fun offer(word: String, score: Float, completion: Boolean = true) {
            val map = if (completion) completions else corrections
            val prev = map[word]
            if (prev == null || prev < score) map[word] = score
        }

        // 1. Complétions exactes par préfixe.
        d.forEachWithPrefix(norm, PREFIX_SCAN_LIMIT) { i ->
            val w = d.word(i)
            val extra = d.normLength(i) - norm.length
            var s = ln(d.freq(i).toFloat() + 1f) - 0.55f * extra
            if (extra == 0) {
                s += 6f
                if (w == typed || w == typed.lowercase()) s += 2.5f
            }
            offer(w, s)
        }

        // 2. Mots personnels.
        for (w in user.startingWith(norm, 5)) {
            val extra = Fr.normalize(w).length - norm.length
            offer(w, 11f + user.countOf(w).coerceAtMost(20) * 0.2f - 0.55f * extra +
                (if (extra == 0) 6f else 0f))
        }

        // 3. Corrections à une modification près.
        if (norm.length >= MIN_FUZZY_LEN) {
            for (variant in variantsOf(norm)) {
                if (variant == norm) continue
                d.forEachWithPrefix(variant, FUZZY_SCAN_LIMIT) { i ->
                    val w = d.word(i)
                    val extra = d.normLength(i) - variant.length
                    var s = ln(d.freq(i).toFloat() + 1f) - 0.55f * extra - FUZZY_PENALTY
                    if (extra == 0) s += 6f
                    offer(w, s, completion = false)
                }
            }
        }

        val rankedCompletions = completions.entries.sortedByDescending { it.value }
        val rankedCorrections = corrections.entries.sortedByDescending { it.value }
        // on montre d'abord les complétions du mot en cours, puis les corrections
        val ranked = (rankedCompletions + rankedCorrections).map { it.key }
        val scores = HashMap<String, Float>(completions.size + corrections.size)
        scores.putAll(corrections)
        for ((k, v) in completions) if ((scores[k] ?: Float.MIN_VALUE) < v) scores[k] = v

        val blocked = blocklist
        val cased = ranked.map { applyCase(typed, it) }.distinct()
            .filterNot { blocked?.isBlocked(it) == true }

        // Correction automatique : seulement si le mot tapé n'existe pas et si la
        // meilleure proposition est nettement plus probable.
        var auto: String? = null
        if (!typedValid && cased.isNotEmpty()) {
            val best = scores.entries
                .filterNot { blocked?.isBlocked(applyCase(typed, it.key)) == true }
                .maxByOrNull { it.value }?.key ?: ranked.first()
            val bestScore = scores[best] ?: 0f
            val sameLetters = Fr.normalize(best) == norm
            if (!sameLetters && bestScore >= AUTOCORRECT_MIN_SCORE && norm.length >= 3) {
                auto = applyCase(typed, best)
            } else if (sameLetters && best.lowercase() != typed.lowercase()) {
                // même suite de lettres : on ne fait que rétablir les accents
                auto = applyCase(typed, best)
            }
        }

        return SuggestionResult(cased.take(max), auto, typedValid)
    }

    /** Applique la casse du mot tapé (Majuscule initiale, TOUT EN MAJUSCULES) à la proposition. */
    private fun applyCase(typed: String, candidate: String): String = when {
        typed.length > 1 && Fr.isAllUpper(typed) -> candidate.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() -> Fr.capitalize(candidate)
        else -> candidate
    }

    /**
     * Variantes à une modification près : substitution voisine, suppression, inversion,
     * insertion.
     *
     * Les quatre familles se recouvrent largement — supprimer la 2ᵉ lettre de « aab »
     * et supprimer la 3ᵉ donnent le même mot, et une insertion redonne souvent une
     * substitution. Chaque doublon coûterait une recherche complète dans le
     * dictionnaire : on dédoublonne donc à la construction (un ensemble ordonné, pour
     * que l'ordre d'exploration reste celui d'avant).
     */
    private fun variantsOf(s: String): Collection<String> {
        val n = s.length
        if (n > MAX_FUZZY_LEN) return emptyList()
        val out = LinkedHashSet<String>(160)
        val sb = StringBuilder(s)

        // substitution par une touche voisine
        for (i in 0 until n) {
            val orig = s[i]
            for (nb in AzertyModel.neighborsOf(orig)) {
                sb.setCharAt(i, nb)
                out.add(sb.toString())
            }
            sb.setCharAt(i, orig)
        }
        // lettre en trop
        if (n > 3) {
            for (i in 0 until n) {
                sb.setLength(0)
                sb.append(s, 0, i).append(s, i + 1, n)
                out.add(sb.toString())
            }
            sb.setLength(0)
            sb.append(s)
        }
        // inversion de deux lettres
        for (i in 0 until n - 1) {
            if (s[i] == s[i + 1]) continue
            sb.setCharAt(i, s[i + 1])
            sb.setCharAt(i + 1, s[i])
            out.add(sb.toString())
            sb.setCharAt(i, s[i])
            sb.setCharAt(i + 1, s[i + 1])
        }
        // lettre manquante : tout l'alphabet pour les mots courts (« bonjur » -> « bonjour »),
        // seulement les voisines et les voyelles au-delà, pour rester temps réel.
        val insertions = if (n <= 12) ALPHABET else null
        for (i in 0..n) {
            val letters = insertions ?: buildString {
                append("aeiouy")
                if (i > 0) append(AzertyModel.neighborsOf(s[i - 1]))
                if (i < n) append(AzertyModel.neighborsOf(s[i]))
            }
            for (c in letters) {
                sb.setLength(0)
                sb.append(s, 0, i).append(c).append(s, i, n)
                out.add(sb.toString())
            }
            sb.setLength(0)
            sb.append(s)
        }
        return out
    }

    companion object {
        const val PREFIX_SCAN_LIMIT = 4000
        const val FUZZY_SCAN_LIMIT = 40
        const val FUZZY_PENALTY = 4.5f
        const val MIN_FUZZY_LEN = 3
        const val MAX_FUZZY_LEN = 16
        const val AUTOCORRECT_MIN_SCORE = 8.5f
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        /** Élisions du français : la partie avant l'apostrophe. */
        val ELISIONS = setOf(
            "j", "l", "d", "n", "m", "t", "s", "c", "qu", "z",
            "jusqu", "lorsqu", "puisqu", "quoiqu", "quelqu", "presqu", "entr",
            "aujourd", "prud", "y"
        )
    }
}

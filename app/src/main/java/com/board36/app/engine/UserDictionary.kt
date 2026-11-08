package com.board36.app.engine

import java.io.File

/**
 * Dictionnaire personnel : mots appris et enchaînements (bigrammes) de l'utilisateur.
 * Tout reste sur l'appareil, dans un fichier texte privé à l'application.
 *
 *   W<TAB>mot<TAB>compteur
 *   B<TAB>motPrécédent<TAB>motSuivant<TAB>compteur
 */
class UserDictionary(private val file: File?) {

    private val counts = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private var dirty = false

    /**
     * Incrémenté à chaque modification du contenu. Le décodeur gestuel s'en sert pour
     * savoir si son cache de mots normalisés est encore valable, sans avoir à recopier
     * la liste entière à chaque tracé.
     */
    @Volatile
    var version = 0
        private set

    private fun touch() {
        dirty = true
        version++
    }

    @Synchronized
    fun load() {
        counts.clear(); bigrams.clear()
        val f = file ?: return
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val p = line.split('\t')
                when {
                    p.size == 3 && p[0] == "W" -> counts[p[1]] = p[2].toIntOrNull() ?: 1
                    p.size == 4 && p[0] == "B" -> {
                        val m = bigrams.getOrPut(p[1]) { HashMap() }
                        m[p[2]] = p[3].toIntOrNull() ?: 1
                    }
                }
            }
        } catch (_: Exception) {
        }
        version++
    }

    @Synchronized
    fun save() {
        val f = file ?: return
        if (!dirty) return
        try {
            val sb = StringBuilder()
            for ((w, c) in counts) sb.append("W\t").append(w).append('\t').append(c).append('\n')
            for ((prev, m) in bigrams) {
                for ((next, c) in m) {
                    sb.append("B\t").append(prev).append('\t').append(next).append('\t').append(c).append('\n')
                }
            }
            Storage.writeAtomically(f, sb.toString())
            dirty = false
        } catch (_: Exception) {
        }
    }

    @Synchronized fun words(): List<String> = counts.keys.toList()

    @Synchronized fun countOf(w: String): Int = counts[w] ?: 0

    @Synchronized fun contains(w: String): Boolean = counts.containsKey(w)

    @Synchronized
    fun learnWord(w: String, weight: Int = 1) {
        if (w.length < 2 || w.length > MAX_LENGTH) return
        if (Storage.sanitizeEntry(w, MAX_LENGTH) == null) return
        counts[w] = (counts[w] ?: 0) + weight
        touch()
        if (counts.size > MAX_WORDS) prune()
    }

    @Synchronized
    fun addWord(w: String) {
        val word = Storage.sanitizeEntry(w, MAX_LENGTH) ?: return
        counts[word] = maxOf(counts[word] ?: 0, PROMOTED)
        touch()
    }

    @Synchronized
    fun removeWord(w: String) {
        counts.remove(w)
        bigrams.remove(w)
        for (m in bigrams.values) m.remove(w)
        touch()
    }

    @Synchronized
    fun clear() {
        counts.clear(); bigrams.clear(); touch()
    }

    @Synchronized
    fun learnBigram(prev: String, next: String) {
        if (Storage.sanitizeEntry(prev, MAX_LENGTH) == null) return
        if (Storage.sanitizeEntry(next, MAX_LENGTH) == null) return
        val key = prev.lowercase()
        val m = bigrams.getOrPut(key) { HashMap() }
        m[next] = (m[next] ?: 0) + 1
        touch()
        if (bigrams.size > MAX_BIGRAM_KEYS) pruneBigrams()
    }

    /** Mots qui suivent le plus souvent [prev], du plus fréquent au moins fréquent. */
    @Synchronized
    fun nextWords(prev: String, limit: Int = 3): List<String> {
        val m = bigrams[prev.lowercase()] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }
            .filter { it.value >= 2 }
            .take(limit)
            .map { it.key }
    }

    /** Mots personnels commençant par [prefix] (accents ignorés). */
    @Synchronized
    fun startingWith(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val n = Fr.normalize(prefix)
        return counts.entries
            .filter { Fr.normalize(it.key).startsWith(n) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    @Synchronized
    fun entries(): List<Pair<String, Int>> =
        counts.entries.sortedByDescending { it.value }.map { it.key to it.value }

    private fun prune() {
        val keep = counts.entries.sortedByDescending { it.value }.take(MAX_WORDS * 3 / 4)
        counts.clear()
        for (e in keep) counts[e.key] = e.value
    }

    private fun pruneBigrams() {
        val keep = bigrams.entries.sortedByDescending { e -> e.value.values.sum() }
            .take(MAX_BIGRAM_KEYS * 3 / 4)
        val copy = HashMap<String, HashMap<String, Int>>()
        for (e in keep) copy[e.key] = e.value
        bigrams.clear()
        bigrams.putAll(copy)
    }

    companion object {
        const val MAX_WORDS = 4000
        const val MAX_BIGRAM_KEYS = 6000
        /** Longueur maximale d'un mot appris ou ajouté à la main. */
        const val MAX_LENGTH = 32
        /** Poids donné à un mot ajouté explicitement : il passe devant les suggestions. */
        const val PROMOTED = 8
    }
}

package com.board36.app.engine

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Dictionnaire français principal (lecture seule), stocké à plat pour tenir en mémoire
 * sans créer 150 000 objets : les mots sont concaténés dans un CharArray et repérés par
 * un tableau d'offsets. Le fichier est trié par forme normalisée puis par fréquence
 * décroissante, ce qui permet une recherche par préfixe en O(log n).
 */
class FrenchDictionary(
    val size: Int,
    private val chars: CharArray,
    private val offsets: IntArray,
    private val freqs: IntArray,
    private val normChars: ByteArray,
    private val normOffsets: IntArray,
    /** 26*26 seaux indexés par (première lettre, dernière lettre) de la forme normalisée. */
    val buckets: Array<IntArray>
) {

    fun word(i: Int): String = String(chars, offsets[i], offsets[i + 1] - offsets[i])

    fun freq(i: Int): Int = freqs[i]

    fun normLength(i: Int): Int = normOffsets[i + 1] - normOffsets[i]

    /** Lettre normalisée n° [k] du mot [i], codée 0..25. */
    fun normAt(i: Int, k: Int): Int = normChars[normOffsets[i] + k].toInt()

    fun wordLength(i: Int): Int = offsets[i + 1] - offsets[i]

    private fun compareNormTo(i: Int, s: String): Int {
        val len = normLength(i)
        val n = minOf(len, s.length)
        val off = normOffsets[i]
        for (k in 0 until n) {
            val a = normChars[off + k].toInt() + 'a'.code
            val b = s[k].code
            if (a != b) return a - b
        }
        return len - s.length
    }

    private fun startsWith(i: Int, prefix: String): Boolean {
        if (normLength(i) < prefix.length) return false
        val off = normOffsets[i]
        for (k in prefix.indices) {
            if (normChars[off + k].toInt() + 'a'.code != prefix[k].code) return false
        }
        return true
    }

    /** Premier index dont la forme normalisée est >= [s]. */
    fun lowerBound(s: String): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareNormTo(mid, s) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Parcourt les mots dont la forme normalisée commence par [prefix].
     * [limit] borne le nombre d'entrées visitées pour rester temps réel.
     */
    inline fun forEachWithPrefix(prefix: String, limit: Int = 40000, action: (Int) -> Unit) {
        if (prefix.isEmpty()) return
        var i = lowerBound(prefix)
        var seen = 0
        while (i < size && seen < limit && normStartsWith(i, prefix)) {
            action(i)
            i++
            seen++
        }
    }

    /** Version publique de [startsWith] (nécessaire pour l'inline ci-dessus). */
    fun normStartsWith(i: Int, prefix: String): Boolean = startsWith(i, prefix)

    /** Index du mot dont la forme normalisée vaut exactement [s] et de plus forte fréquence. */
    fun indexOfNorm(s: String): Int {
        if (s.isEmpty()) return -1
        val i = lowerBound(s)
        if (i < size && normLength(i) == s.length && startsWith(i, s)) return i
        return -1
    }

    /** Le mot existe-t-il tel quel (accents compris) ? */
    fun containsExact(w: String): Boolean {
        val n = Fr.normalize(w)
        if (n.isEmpty()) return false
        var i = lowerBound(n)
        while (i < size && normLength(i) == n.length && startsWith(i, n)) {
            if (wordLength(i) == w.length) {
                var same = true
                val off = offsets[i]
                for (k in w.indices) if (chars[off + k] != w[k]) { same = false; break }
                if (same) return true
            }
            i++
        }
        return false
    }

    companion object {

        const val BUCKETS = 26 * 26

        fun bucketKey(first: Int, last: Int): Int = first * 26 + last

        fun load(input: InputStream): FrenchDictionary {
            var chars = CharArray(1 shl 21)
            var nChars = 0
            var norms = ByteArray(1 shl 21)
            var nNorms = 0
            var offsets = IntArray(1 shl 18)
            var normOffsets = IntArray(1 shl 18)
            var freqs = IntArray(1 shl 18)
            var n = 0

            // 150 000 lignes « mot<TAB>fréquence ». On les lit en place : les deux
            // `substring` par ligne de la version précédente créaient 300 000 String
            // jetables au démarrage du clavier, et autant de travail pour le ramasse-miettes.
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val f = parseFrequency(line, tab + 1)
                    if (f < 0) continue

                    if (n + 2 >= offsets.size) {
                        offsets = offsets.copyOf(offsets.size * 2)
                        normOffsets = normOffsets.copyOf(normOffsets.size * 2)
                        freqs = freqs.copyOf(freqs.size * 2)
                    }
                    // une lettre peut donner deux lettres normalisées (œ -> oe, ß -> ss)
                    while (nChars + tab >= chars.size) chars = chars.copyOf(chars.size * 2)
                    while (nNorms + 2 * tab >= norms.size) norms = norms.copyOf(norms.size * 2)

                    val charStart = nChars
                    val normStart = nNorms
                    for (k in 0 until tab) {
                        val c = line[k]
                        for (b in Fr.base(c)) norms[nNorms++] = (b - 'a').toByte()
                        chars[nChars++] = c
                    }
                    if (nNorms == normStart) {
                        // aucune lettre : le mot n'est atteignable par aucune touche
                        nChars = charStart
                        continue
                    }
                    offsets[n] = charStart
                    normOffsets[n] = normStart
                    freqs[n] = f
                    n++
                }
            }
            offsets[n] = nChars
            normOffsets[n] = nNorms

            // Seaux (première lettre, dernière lettre) pour le décodeur gestuel.
            val counts = IntArray(BUCKETS)
            for (i in 0 until n) {
                val len = normOffsets[i + 1] - normOffsets[i]
                if (len == 0) continue
                val f = norms[normOffsets[i]].toInt()
                val l = norms[normOffsets[i] + len - 1].toInt()
                if (f in 0..25 && l in 0..25) counts[bucketKey(f, l)]++
            }
            val buckets = Array(BUCKETS) { IntArray(counts[it]) }
            val fill = IntArray(BUCKETS)
            for (i in 0 until n) {
                val len = normOffsets[i + 1] - normOffsets[i]
                if (len == 0) continue
                val f = norms[normOffsets[i]].toInt()
                val l = norms[normOffsets[i] + len - 1].toInt()
                if (f in 0..25 && l in 0..25) {
                    val k = bucketKey(f, l)
                    buckets[k][fill[k]++] = i
                }
            }

            return FrenchDictionary(
                n,
                chars.copyOf(nChars),
                offsets.copyOf(n + 1),
                freqs.copyOf(n),
                norms.copyOf(nNorms),
                normOffsets.copyOf(n + 1),
                buckets
            )
        }

        /** Entier positif écrit à partir de [from] jusqu'à la fin de ligne, ou -1. */
        private fun parseFrequency(line: String, from: Int): Int {
            var i = from
            val end = line.length
            while (i < end && line[i] == ' ') i++
            var value = 0L
            var digits = 0
            while (i < end) {
                val c = line[i]
                if (c < '0' || c > '9') break
                value = value * 10 + (c - '0')
                if (value > Int.MAX_VALUE) value = Int.MAX_VALUE.toLong()
                digits++
                i++
            }
            while (i < end && (line[i] == ' ' || line[i] == '\r')) i++
            return if (digits == 0 || i != end) -1 else value.toInt()
        }
    }
}

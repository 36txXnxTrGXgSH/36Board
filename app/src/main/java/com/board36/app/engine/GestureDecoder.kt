package com.board36.app.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Décodeur de saisie gestuelle (swipe), inspiré de SHARK² :
 * on compare le tracé du doigt au « tracé idéal » de chaque mot candidat, à la fois
 * en position absolue (canal de position) et en forme normalisée (canal de forme),
 * puis on pondère par la fréquence du mot.
 *
 * L'espace de recherche est réduit par les seaux (première lettre, dernière lettre) :
 * seuls les mots pouvant commencer sous le point de départ et finir sous le point
 * d'arrivée sont évalués — quelques milliers au lieu de 150 000.
 */
class GestureDecoder(
    private var dict: FrenchDictionary?,
    private var user: UserDictionary? = null,
    private var blocklist: WordBlocklist? = null
) {

    fun setDictionary(d: FrenchDictionary?) { dict = d }
    fun setUserDictionary(u: UserDictionary?) { user = u; userCacheVersion = -1 }
    fun setBlocklist(b: WordBlocklist?) { blocklist = b }

    /**
     * Mots personnels, déjà normalisés. Ils sont réévalués à chaque tracé : les recopier
     * et les renormaliser à chaque fois (jusqu'à 4 000 mots) coûtait plus cher que le
     * décodage lui-même. Le cache suit la version du dictionnaire personnel.
     */
    private var userWords: List<Pair<String, String>> = emptyList()
    private var userCacheVersion = -1

    private fun userWords(u: UserDictionary): List<Pair<String, String>> {
        val v = u.version
        if (v != userCacheVersion) {
            userWords = u.words().map { it to Fr.normalize(it) }
            userCacheVersion = v
        }
        return userWords
    }

    private val gx = FloatArray(SAMPLES)
    private val gy = FloatArray(SAMPLES)
    private val wx = FloatArray(SAMPLES)
    private val wy = FloatArray(SAMPLES)
    private val ngx = FloatArray(SAMPLES)
    private val ngy = FloatArray(SAMPLES)
    private val nwx = FloatArray(SAMPLES)
    private val nwy = FloatArray(SAMPLES)
    private val pathX = FloatArray(40)
    private val pathY = FloatArray(40)

    private val bestScores = FloatArray(MAX_RESULTS) { -Float.MAX_VALUE }
    private val bestWords = arrayOfNulls<String>(MAX_RESULTS)

    /**
     * @param px,py,count  points bruts du tracé
     * @return les meilleurs mots, le premier étant le plus probable
     */
    fun decode(px: FloatArray, py: FloatArray, count: Int, geo: KeyGeometry): List<String> {
        val d = dict ?: return emptyList()
        if (count < 2 || !geo.isReady()) return emptyList()

        val gestureLen = polylineLength(px, py, count)
        if (gestureLen < geo.keyW * 0.6f) return emptyList()

        resample(px, py, count, gx, gy)

        val starts = geo.nearest(px[0], py[0], MAX_ENDPOINTS, 1.25f)
        val ends = geo.nearest(px[count - 1], py[count - 1], MAX_ENDPOINTS, 1.25f)
        if (starts.isEmpty() || ends.isEmpty()) return emptyList()

        normalizePath(gx, gy, ngx, ngy)

        java.util.Arrays.fill(bestScores, -Float.MAX_VALUE)
        java.util.Arrays.fill(bestWords, null)

        val kw = geo.keyW
        for (s in starts) {
            val startPenalty = geo.dist(s, px[0], py[0]) / kw
            for (e in ends) {
                val endPenalty = geo.dist(e, px[count - 1], py[count - 1]) / kw
                val bucket = d.buckets[FrenchDictionary.bucketKey(s, e)]
                for (idx in bucket) {
                    val len = d.normLength(idx)
                    if (len < 2 || len > 28) continue
                    val np = buildIdealPath(d, idx, geo) ?: continue
                    val idealLen = polylineLength(pathX, pathY, np)
                    if (idealLen > gestureLen * 2.1f + kw || idealLen < gestureLen * 0.3f - kw) continue

                    resample(pathX, pathY, np, wx, wy)
                    val loc = meanDistance(gx, gy, wx, wy) / kw
                    if (loc > 2.2f) continue
                    normalizePath(wx, wy, nwx, nwy)
                    val shape = meanDistance(ngx, ngy, nwx, nwy)

                    val score = score(loc, shape, d.freq(idx), idealLen, gestureLen,
                        startPenalty, endPenalty)
                    offer(score, idx, d)
                }
            }
        }

        // Formes élidées (« l'ouverture », « qu'il », « s'il ») : elles ne figurent pas
        // dans le dictionnaire, on les construit à la volée. Le tracé commence par la
        // lettre d'élision, la base commence par une voyelle ou un h.
        for (s in starts) {
            val head = ELISIONS[s] ?: continue
            val prefix = if (head == "qu") intArrayOf(s, 'u' - 'a') else intArrayOf(s)
            if (head == "qu" && !geo.present['u' - 'a']) continue
            val startPenalty = geo.dist(s, px[0], py[0]) / kw
            for (v in VOWEL_STARTS) {
                if (!geo.present[v]) continue
                for (e in ends) {
                    val endPenalty = geo.dist(e, px[count - 1], py[count - 1]) / kw
                    val bucket = d.buckets[FrenchDictionary.bucketKey(v, e)]
                    for (idx in bucket) {
                        if (d.freq(idx) < ELISION_MIN_FREQ) continue
                        val len = d.normLength(idx)
                        if (len < 1 || len > 24) continue
                        val np = buildIdealPath(d, idx, geo, prefix) ?: continue
                        val idealLen = polylineLength(pathX, pathY, np)
                        if (idealLen > gestureLen * 2.1f + kw || idealLen < gestureLen * 0.3f - kw) continue

                        resample(pathX, pathY, np, wx, wy)
                        val loc = meanDistance(gx, gy, wx, wy) / kw
                        if (loc > 2.2f) continue
                        normalizePath(wx, wy, nwx, nwy)
                        val shape = meanDistance(ngx, ngy, nwx, nwy)

                        val score = score(loc, shape, d.freq(idx), idealLen, gestureLen,
                            startPenalty, endPenalty) - ELISION_PENALTY
                        if (score > bestScores[MAX_RESULTS - 1]) {
                            offerWord(score, head + "'" + d.word(idx))
                        }
                    }
                }
            }
        }

        // Mots personnels : peu nombreux, on les évalue tous.
        user?.let { u ->
            for ((w, nrm) in userWords(u)) {
                if (nrm.length < 2 || nrm.length > 28) continue
                val np = buildIdealPathFor(nrm, geo) ?: continue
                val idealLen = polylineLength(pathX, pathY, np)
                if (idealLen > gestureLen * 2.1f + kw || idealLen < gestureLen * 0.3f - kw) continue
                resample(pathX, pathY, np, wx, wy)
                val loc = meanDistance(gx, gy, wx, wy) / kw
                if (loc > 2.2f) continue
                normalizePath(wx, wy, nwx, nwy)
                val shape = meanDistance(ngx, ngy, nwx, nwy)
                val score = score(loc, shape, u.countOf(w) * 1000, idealLen, gestureLen, 0f, 0f)
                offerWord(score, w)
            }
        }

        val out = ArrayList<String>(MAX_RESULTS)
        for (i in 0 until MAX_RESULTS) {
            val w = bestWords[i] ?: continue
            if (bestScores[i] == -Float.MAX_VALUE) continue
            if (!out.contains(w)) out.add(w)
        }
        return out
    }

    /**
     * Score d'un candidat. La pénalité de longueur est décisive : sans elle, un mot très
     * court et très fréquent (« le », « mes ») bat un mot long pourtant bien tracé,
     * parce que sa fréquence pèse plus que l'écart de position.
     */
    private fun score(
        loc: Float, shape: Float, freq: Int,
        idealLen: Float, gestureLen: Float,
        startPenalty: Float, endPenalty: Float
    ): Float {
        val ratio = kotlin.math.abs(ln((idealLen + 1f) / (gestureLen + 1f)))
        return -2.3f * loc - 1.6f * shape +
            0.26f * ln(freq.toFloat() + 1f) -
            0.35f * (startPenalty + endPenalty) -
            1.6f * ratio
    }

    private fun offer(score: Float, idx: Int, d: FrenchDictionary) {
        if (score <= bestScores[MAX_RESULTS - 1]) return
        offerWord(score, d.word(idx))
    }

    private fun offerWord(score: Float, word: String) {
        if (score <= bestScores[MAX_RESULTS - 1]) return
        if (blocklist?.isBlocked(word) == true) return
        var p = MAX_RESULTS - 1
        while (p > 0 && bestScores[p - 1] < score) {
            bestScores[p] = bestScores[p - 1]
            bestWords[p] = bestWords[p - 1]
            p--
        }
        bestScores[p] = score
        bestWords[p] = word
    }

    /**
     * Tracé idéal du mot [idx] : centres des touches, doublons consécutifs fusionnés.
     * [prefix] permet de préfixer des touches (lettre d'élision) sans créer d'entrée.
     */
    private fun buildIdealPath(
        d: FrenchDictionary, idx: Int, geo: KeyGeometry, prefix: IntArray? = null
    ): Int? {
        val len = d.normLength(idx)
        var n = 0
        var prev = -1
        if (prefix != null) {
            for (c in prefix) {
                if (!geo.present[c]) return null
                if (c == prev) continue
                pathX[n] = geo.cx[c]
                pathY[n] = geo.cy[c]
                n++
                prev = c
            }
        }
        for (k in 0 until len) {
            val c = d.normAt(idx, k)
            if (c < 0 || c > 25 || !geo.present[c]) return null
            if (c == prev) continue
            if (n >= pathX.size) return null
            pathX[n] = geo.cx[c]
            pathY[n] = geo.cy[c]
            n++
            prev = c
        }
        return if (n >= 2) n else null
    }

    private fun buildIdealPathFor(norm: String, geo: KeyGeometry): Int? {
        var n = 0
        var prev = -1
        for (ch in norm) {
            val c = ch - 'a'
            if (c < 0 || c > 25 || !geo.present[c]) return null
            if (c == prev) continue
            if (n >= pathX.size) return null
            pathX[n] = geo.cx[c]
            pathY[n] = geo.cy[c]
            n++
            prev = c
        }
        return if (n >= 2) n else null
    }

    companion object {
        const val SAMPLES = 30

        /** Lettres d'élision du français, indexées par touche (0 = a … 25 = z). */
        val ELISIONS: Map<Int, String> = mapOf(
            'l' - 'a' to "l", 'd' - 'a' to "d", 'j' - 'a' to "j", 'n' - 'a' to "n",
            'm' - 'a' to "m", 't' - 'a' to "t", 's' - 'a' to "s", 'c' - 'a' to "c",
            'q' - 'a' to "qu"
        )

        /** Une élision précède une voyelle ou un h muet. */
        val VOWEL_STARTS = intArrayOf(
            'a' - 'a', 'e' - 'a', 'i' - 'a', 'o' - 'a', 'u' - 'a', 'y' - 'a', 'h' - 'a'
        )

        /** Fréquence minimale de la base : évite d'explorer la longue traîne. */
        const val ELISION_MIN_FREQ = 40

        /** Une forme élidée est moins probable qu'un mot simple de même tracé. */
        const val ELISION_PENALTY = 0.9f
        const val MAX_RESULTS = 6
        const val MAX_ENDPOINTS = 5

        fun polylineLength(x: FloatArray, y: FloatArray, count: Int): Float {
            var total = 0f
            for (i in 1 until count) {
                val dx = x[i] - x[i - 1]
                val dy = y[i] - y[i - 1]
                total += sqrt(dx * dx + dy * dy)
            }
            return total
        }

        /** Rééchantillonne un tracé en [SAMPLES] points équidistants. */
        fun resample(x: FloatArray, y: FloatArray, count: Int, outX: FloatArray, outY: FloatArray) {
            val n = outX.size
            val total = polylineLength(x, y, count)
            if (count < 2 || total <= 0f) {
                for (i in 0 until n) { outX[i] = x[0]; outY[i] = y[0] }
                return
            }
            val step = total / (n - 1)
            outX[0] = x[0]; outY[0] = y[0]
            var seg = 1
            var segStartDist = 0f
            var segLen = segmentLength(x, y, 1)
            var written = 1
            var target = step
            while (written < n - 1 && seg < count) {
                if (segLen <= 0f) {
                    seg++
                    if (seg >= count) break
                    segLen = segmentLength(x, y, seg)
                    continue
                }
                if (target <= segStartDist + segLen) {
                    val t = (target - segStartDist) / segLen
                    outX[written] = x[seg - 1] + (x[seg] - x[seg - 1]) * t
                    outY[written] = y[seg - 1] + (y[seg] - y[seg - 1]) * t
                    written++
                    target += step
                } else {
                    segStartDist += segLen
                    seg++
                    if (seg >= count) break
                    segLen = segmentLength(x, y, seg)
                }
            }
            while (written < n) {
                outX[written] = x[count - 1]
                outY[written] = y[count - 1]
                written++
            }
        }

        private fun segmentLength(x: FloatArray, y: FloatArray, i: Int): Float {
            val dx = x[i] - x[i - 1]
            val dy = y[i] - y[i - 1]
            return sqrt(dx * dx + dy * dy)
        }

        fun meanDistance(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray): Float {
            var sum = 0f
            for (i in ax.indices) {
                val dx = ax[i] - bx[i]
                val dy = ay[i] - by[i]
                sum += sqrt(dx * dx + dy * dy)
            }
            return sum / ax.size
        }

        /** Centre le tracé et le met à l'échelle : compare les formes indépendamment de la position. */
        fun normalizePath(x: FloatArray, y: FloatArray, outX: FloatArray, outY: FloatArray) {
            val n = x.size
            var mx = 0f
            var my = 0f
            for (i in 0 until n) { mx += x[i]; my += y[i] }
            mx /= n; my /= n
            var scale = 0f
            for (i in 0 until n) {
                scale += abs(x[i] - mx) + abs(y[i] - my)
            }
            scale /= n
            if (scale < 1e-3f) scale = 1e-3f
            for (i in 0 until n) {
                outX[i] = (x[i] - mx) / scale
                outY[i] = (y[i] - my) / scale
            }
        }
    }
}

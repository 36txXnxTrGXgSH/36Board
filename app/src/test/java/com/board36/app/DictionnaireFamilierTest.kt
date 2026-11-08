package com.board36.app

import com.board36.app.engine.Fr
import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.Suggester
import com.board36.app.engine.UserDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le vocabulaire familier (dictionnaire/familier.md) est bien dans le dictionnaire de base,
 * le fichier reste trié comme le clavier l'exige, et le clavier se comporte comme prévu :
 * les abréviations ne sont plus « corrigées », les anglicismes sont proposés, les mots
 * courants gardent leur place.
 */
class DictionnaireFamilierTest {

    private val dict = TestKeyboard.dictionary
    private val suggester = Suggester(dict, UserDictionary(null))

    private fun file(vararg candidates: String): File =
        candidates.map { File(it) }.first { it.exists() }

    /** Mots et fréquences des tableaux de familier.md. */
    private val familier: List<Pair<String, Int>> by lazy {
        file("../dictionnaire/familier.md", "dictionnaire/familier.md").readLines()
            .map { line -> line.trim().trim('|').split('|').map { it.trim() } }
            .filter { it.size == 4 && it[3].all(Char::isDigit) && it[3].isNotEmpty() }
            .map { it[0].trim('`') to it[3].toInt() }
    }

    @Test
    fun leDictionnaireResteTrieCommeLeClavierLExige() {
        val lines = file("src/main/assets/fr_dict.txt", "app/src/main/assets/fr_dict.txt")
            .readLines().map { val (w, f) = it.split('\t'); Fr.normalize(w) to f.trim().toInt() }
        for (i in 1 until lines.size) {
            val (pn, pf) = lines[i - 1]
            val (n, f) = lines[i]
            assertTrue("ordre rompu à la ligne ${i + 1} : $pn/$pf puis $n/$f",
                pn < n || (pn == n && pf >= f))
        }
    }

    @Test
    fun chaqueMotDeFamilierMdEstDansLeDictionnaire() {
        assertTrue("familier.md semble vide : ${familier.size}", familier.size >= 100)
        for ((word, freq) in familier) {
            assertTrue("absent : $word", dict.containsExact(word))
            val f = exactFrequency(word)
            assertTrue("$word : fréquence $f, attendu au moins $freq", f >= freq)
        }
    }

    /** Fréquence du mot écrit exactement ainsi (« liké » et « like » ont la même forme normalisée). */
    private fun exactFrequency(word: String): Int {
        val norm = Fr.normalize(word)
        var best = -1
        dict.forEachWithPrefix(norm) { i ->
            if (dict.normLength(i) == norm.length && dict.word(i) == word) best = maxOf(best, dict.freq(i))
        }
        return best
    }

    @Test
    fun lesAbreviationsNeSontPlusCorrigees() {
        for (w in listOf("mdr", "ptdr", "lol", "tkt", "jsp", "bcp", "dsl", "slt", "askip", "osef")) {
            val r = suggester.suggest(w, null)
            assertTrue("$w devrait être un mot connu", r.typedIsValid)
            assertNull("$w serait corrigé en ${r.autoCorrect}", r.autoCorrect)
        }
    }

    @Test
    fun lesAnglicismesSontProposes() {
        assertTrue(suggester.suggest("feedb", null).words.contains("feedback"))
        assertTrue(suggester.suggest("spoil", null).words.contains("spoiler"))
        assertTrue(suggester.suggest("deadl", null).words.contains("deadline"))
        assertTrue(suggester.suggest("week", null).words.contains("week-end"))
        assertTrue(suggester.suggest("ptd", null).words.contains("ptdr"))
    }

    @Test
    fun lesMotsCourantsGardentLeurPlace() {
        // un mot familier ne doit pas passer devant un mot courant de même début
        assertEquals("bonjour", suggester.suggest("bonjo", null).words.first())
        assertEquals("bonjour", suggester.suggest("bonjoyr", null).autoCorrect)
        assertTrue(suggester.suggest("m", null).words.first() != "mdr")
        assertTrue(suggester.suggest("p", null).words.first() != "pk")
    }

    @Test
    fun leSwipeTrouveLesNouveauxMots() {
        val decoder = GestureDecoder(dict, UserDictionary(null))
        for (w in listOf("feedback", "spoiler", "smartphone", "playlist")) {
            val (xs, ys, n) = TestKeyboard.gestureFor(w)
            val got = decoder.decode(xs, ys, n, TestKeyboard.geometry)
            assertTrue("$w absent des 3 premiers : $got", got.take(3).contains(w))
        }
    }
}

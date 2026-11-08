package com.board36.app

import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.UserDictionary
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDecoderTest {

    private val decoder = GestureDecoder(TestKeyboard.dictionary, UserDictionary(null))

    private fun decode(word: String, jitter: Float = 0.18f): List<String> {
        val (xs, ys, n) = TestKeyboard.gestureFor(word, jitter)
        return decoder.decode(xs, ys, n, TestKeyboard.geometry)
    }

    @Test
    fun trouveLesMotsCourantsEnPremiereProposition() {
        val mots = listOf("bonjour", "salut", "merci", "comment", "demain", "voiture",
            "clavier", "maison", "travail", "message")
        val rates = ArrayList<String>()
        for (m in mots) {
            val r = decode(m)
            if (r.firstOrNull() != m) rates.add("$m -> $r")
        }
        assertTrue("mots non reconnus en tête : $rates", rates.size <= 1)
    }

    @Test
    fun retablitLesAccents() {
        // On trace « francais » : le mot accentué doit sortir en tête.
        val r = decode("francais")
        assertTrue("résultat : $r", r.firstOrNull() == "français")
    }

    @Test
    fun toleranceAuTraceImprecis() {
        val r = decode("bonjour", jitter = 0.42f)
        assertTrue("résultat : $r", r.take(3).contains("bonjour"))
    }

    @Test
    fun proposePlusieursCandidats() {
        val r = decode("bonjour")
        assertTrue(r.size >= 2)
    }

    @Test
    fun ignoreUnTraceTropCourt() {
        val xs = floatArrayOf(100f, 102f)
        val ys = floatArrayOf(100f, 101f)
        assertTrue(decoder.decode(xs, ys, 2, TestKeyboard.geometry).isEmpty())
    }

    @Test
    fun apprendLesMotsPersonnels() {
        val user = UserDictionary(null)
        user.addWord("Payneau")
        val d = GestureDecoder(TestKeyboard.dictionary, user)
        val (xs, ys, n) = TestKeyboard.gestureFor("payneau")
        val r = d.decode(xs, ys, n, TestKeyboard.geometry)
        assertTrue("résultat : $r", r.contains("Payneau"))
    }
}

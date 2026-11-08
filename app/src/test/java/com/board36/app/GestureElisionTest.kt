package com.board36.app

import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.UserDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les formes élidées (« l'ouverture », « qu'il ») ne figurent pas dans le dictionnaire :
 * le décodeur doit les construire à partir de la lettre d'élision tracée et du mot suivant.
 */
class GestureElisionTest {

    private val decoder = GestureDecoder(TestKeyboard.dictionary, UserDictionary(null))

    private fun decode(path: String): List<String> {
        val (xs, ys, n) = TestKeyboard.gestureFor(path)
        return decoder.decode(xs, ys, n, TestKeyboard.geometry)
    }

    @Test
    fun proposeLaFormeElidee() {
        val cas = mapOf(
            "louverture" to "l'ouverture",
            "lhistorien" to "l'historien",
            "sil" to "s'il",
            "quil" to "qu'il",
            "daccord" to "d'accord",
            "jai" to "j'ai"
        )
        val rates = ArrayList<String>()
        for ((trace, attendu) in cas) {
            val r = decode(trace)
            if (!r.take(3).contains(attendu)) rates.add("$trace -> $r")
        }
        assertTrue("formes élidées manquantes : $rates", rates.isEmpty())
    }

    @Test
    fun elisionsSuiviesDunMotLongOuAccentue() {
        val cas = mapOf(
            "lattaquant" to "l'attaquant",
            "lheretique" to "l'hérétique",
            "mas" to "m'as",
            "minteresse" to "m'intéresse",
            "mennuyer" to "m'ennuyer",
            "mestil" to "m'est-il"
        )
        val rates = ArrayList<String>()
        for ((trace, attendu) in cas) {
            val r = decode(trace)
            if (r.firstOrNull() != attendu) rates.add("$trace -> $r")
        }
        assertTrue("élisions mal décodées : $rates", rates.isEmpty())
    }

    @Test
    fun unMotSimpleResteProposeAvantUneElision() {
        // « couverture » existe : il doit passer devant « c'ouverture »
        assertEquals("couverture", decode("couverture").first())
        assertEquals("demain", decode("demain").first())
        assertEquals("sur", decode("sur").first())
    }

    @Test
    fun leSwipeCourantNestPasDegrade() {
        val mots = listOf("bonjour", "salut", "merci", "comment", "demain", "voiture",
            "clavier", "maison", "travail", "message")
        val rates = mots.filter { decode(it).firstOrNull() != it }
        assertTrue("mots non reconnus en tête : $rates", rates.size <= 1)
    }
}

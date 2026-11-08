package com.board36.app

import com.board36.app.engine.Fr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTest {

    private val dict = TestKeyboard.dictionary

    @Test
    fun chargeLesCentCinquanteMilleMots() {
        assertTrue("taille inattendue : ${dict.size}", dict.size > 140_000)
    }

    @Test
    fun contientLeVocabulaireCourant() {
        for (w in listOf("bonjour", "école", "clavier", "français", "aujourd'hui",
                "rendez-vous", "merci", "téléphone", "chat", "manger")) {
            assertTrue("mot absent : $w", dict.containsExact(w))
        }
    }

    @Test
    fun rechercheParPrefixeIgnoreLesAccents() {
        val found = ArrayList<String>()
        dict.forEachWithPrefix("ecol") { found.add(dict.word(it)) }
        assertTrue("« école » introuvable via « ecol » : $found", found.contains("école"))
        assertTrue(found.contains("écoles"))
    }

    @Test
    fun lesSeauxCouvrentTousLesMots() {
        var total = 0
        for (b in dict.buckets) total += b.size
        assertEquals(dict.size, total)
    }

    @Test
    fun normalisationDuFrancais() {
        assertEquals("ecole", Fr.normalize("École"))
        assertEquals("oeuvre", Fr.normalize("œuvre"))
        assertEquals("aujourdhui", Fr.normalize("aujourd'hui"))
        assertEquals("rendezvous", Fr.normalize("rendez-vous"))
        assertEquals("ca", Fr.normalize("ça"))
    }

    @Test
    fun lesMotsFrequentsSontDevantDansUnMemeGroupe() {
        // « à » est bien plus fréquent que « â » : il doit apparaître en premier.
        val i = dict.indexOfNorm("a")
        assertTrue(i >= 0)
        assertEquals("à", dict.word(i))
    }
}

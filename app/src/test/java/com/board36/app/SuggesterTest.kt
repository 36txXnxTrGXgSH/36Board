package com.board36.app

import com.board36.app.engine.Suggester
import com.board36.app.engine.UserDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggesterTest {

    private val user = UserDictionary(null)
    private val suggester = Suggester(TestKeyboard.dictionary, user)

    @Test
    fun completeUnDebutDeMot() {
        val r = suggester.suggest("bonjo", null)
        assertTrue("propositions : ${r.words}", r.words.contains("bonjour"))
    }

    @Test
    fun retablitLesAccentsSansCorrigerLeMot() {
        val r = suggester.suggest("ecole", null)
        assertEquals("école", r.autoCorrect)
    }

    @Test
    fun corrigeUneToucheVoisine() {
        // « bonjoyr » : le y est voisin du u sur l'AZERTY
        val r = suggester.suggest("bonjoyr", null)
        assertEquals("propositions : ${r.words}", "bonjour", r.autoCorrect)
    }

    @Test
    fun corrigeUneLettreOubliee() {
        val r = suggester.suggest("bonjur", null)
        assertEquals("propositions : ${r.words}", "bonjour", r.autoCorrect)
    }

    @Test
    fun corrigeUneInversion() {
        val r = suggester.suggest("bonjuor", null)
        assertEquals("propositions : ${r.words}", "bonjour", r.autoCorrect)
    }

    @Test
    fun neCorrigePasUnMotValide() {
        val r = suggester.suggest("chat", null)
        assertTrue(r.typedIsValid)
        assertNull("correction indue : ${r.autoCorrect}", r.autoCorrect)
    }

    @Test
    fun respecteLaCasse() {
        val r = suggester.suggest("Bonjo", null)
        assertTrue("propositions : ${r.words}", r.words.contains("Bonjour"))
    }

    @Test
    fun proposeLesMotsPersonnels() {
        val u = UserDictionary(null)
        u.addWord("Payneau")
        val s = Suggester(TestKeyboard.dictionary, u)
        val r = s.suggest("payn", null)
        assertTrue("propositions : ${r.words}", r.words.contains("Payneau"))
    }

    @Test
    fun preditLeMotSuivant() {
        val u = UserDictionary(null)
        repeat(3) { u.learnBigram("rendez-vous", "demain") }
        val s = Suggester(TestKeyboard.dictionary, u)
        val r = s.suggest("", "rendez-vous")
        assertEquals(listOf("demain"), r.words)
    }

    @Test
    fun neCorrigePasUnMotTropCourt() {
        val r = suggester.suggest("zk", null)
        assertNull(r.autoCorrect)
    }
}

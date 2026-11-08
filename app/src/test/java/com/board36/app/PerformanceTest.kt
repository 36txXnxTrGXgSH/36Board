package com.board36.app

import com.board36.app.engine.GestureDecoder
import com.board36.app.engine.Suggester
import com.board36.app.engine.UserDictionary
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le clavier doit rester temps réel : un décodage de swipe et une mise à jour de
 * suggestions doivent tenir largement sous la durée d'une frappe.
 */
class PerformanceTest {

    private val mots = listOf("bonjour", "comment", "message", "demain", "voiture",
        "clavier", "maison", "travail", "merci", "salut")

    @Test
    fun decodageGestuelRapide() {
        val decoder = GestureDecoder(TestKeyboard.dictionary, UserDictionary(null))
        val traces = mots.map { TestKeyboard.gestureFor(it) }
        // chauffe
        repeat(3) { traces.forEach { (x, y, n) -> decoder.decode(x, y, n, TestKeyboard.geometry) } }
        val start = System.nanoTime()
        val runs = 5
        repeat(runs) { traces.forEach { (x, y, n) -> decoder.decode(x, y, n, TestKeyboard.geometry) } }
        val ms = (System.nanoTime() - start) / 1e6 / (runs * traces.size)
        println("décodage swipe : %.1f ms/mot".format(ms))
        assertTrue("décodage trop lent : $ms ms", ms < 60.0)
    }

    @Test
    fun suggestionsRapides() {
        val s = Suggester(TestKeyboard.dictionary, UserDictionary(null))
        val saisies = listOf("b", "bo", "bon", "bonj", "bonjo", "bonjou", "bonjoyr", "message")
        repeat(3) { saisies.forEach { s.suggest(it, null) } }
        val start = System.nanoTime()
        val runs = 10
        repeat(runs) { saisies.forEach { s.suggest(it, null) } }
        val ms = (System.nanoTime() - start) / 1e6 / (runs * saisies.size)
        println("suggestions : %.1f ms/frappe".format(ms))
        assertTrue("suggestions trop lentes : $ms ms", ms < 40.0)
    }
}

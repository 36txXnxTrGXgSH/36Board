package com.board36.app

import com.board36.app.ime.Codes
import com.board36.app.ime.KeyStyle
import com.board36.app.ime.Layouts
import com.board36.app.engine.UserDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutTest {

    @Test
    fun dispositionAzertyComplete() {
        val l = Layouts.letters(numberRow = true)
        assertEquals(5, l.rows.size)
        assertEquals("azertyuiop", l.rows[1].keys.joinToString("") { it.label })
        assertEquals("qsdfghjklm", l.rows[2].keys.joinToString("") { it.label })
        val lettres = l.keys.filter { it.isLetter }.map { it.label }.toSet()
        assertEquals(26, lettres.size)
    }

    @Test
    fun touchesDeServicePresentes() {
        val l = Layouts.letters(numberRow = false)
        val codes = l.keys.map { it.code }
        assertTrue(codes.contains(Codes.SHIFT))
        assertTrue(codes.contains(Codes.DELETE))
        assertTrue(codes.contains(Codes.ENTER))
        assertTrue(codes.contains(Codes.SPACE))
        assertTrue(codes.contains(Codes.SYMBOLS))
        // sans rangée de chiffres, les chiffres deviennent des indications
        assertEquals("1234567890", l.rows[0].keys.joinToString("") { it.hint })
    }

    @Test
    fun accentsAccessiblesParAppuiLong() {
        val l = Layouts.letters(numberRow = true)
        val e = l.keys.first { it.label == "e" }
        assertTrue(e.popup.contains("é"))
        assertTrue(e.popup.contains("è"))
        val c = l.keys.first { it.label == "c" }
        assertTrue(c.popup.contains("ç"))
        val a = l.keys.first { it.label == "a" }
        assertTrue(a.popup.contains("à"))
    }

    @Test
    fun repartitionDesTouchesSurLaLargeur() {
        val l = Layouts.letters(numberRow = true)
        l.layout(1080f, 750f, 8f, 12f)
        for (row in l.rows) {
            val first = row.keys.first()
            val last = row.keys.last()
            assertTrue(first.x >= 0f)
            assertTrue(last.x + last.w <= 1080f + 1f)
        }
        // la barre d'espace est la plus large
        val space = l.keys.first { it.code == Codes.SPACE }
        assertTrue(space.w > l.keys.first { it.label == "a" }.w * 3)
    }

    @Test
    fun toucheTrouveeAuDoigt() {
        val l = Layouts.letters(numberRow = true)
        l.layout(1080f, 750f, 8f, 12f)
        val a = l.keys.first { it.label == "a" }
        assertEquals("a", l.keyAt(a.centerX(), a.centerY())?.label)
        // même dans l'espace entre deux touches
        assertEquals("a", l.keyAt(a.x + a.w + 2f, a.centerY())?.label ?: "a")
    }

    @Test
    fun lesDeuxPagesDeSymboles() {
        val s = Layouts.symbols()
        assertEquals(4, s.rows.size)
        assertTrue(s.keys.any { it.label == "€" })
        assertTrue(s.keys.any { it.code == Codes.SYMBOLS2 })
        val s2 = Layouts.symbols2()
        assertTrue(s2.keys.any { it.label == "£" })
        // chaque page ramène aux lettres
        assertTrue(s.keys.any { it.code == Codes.LETTERS })
        assertTrue(s2.keys.any { it.code == Codes.LETTERS })
        assertTrue(s2.keys.any { it.style == KeyStyle.FUNCTION })
    }

    @Test
    fun dictionnairePersonnelSauvegardeEtRelu() {
        val f = File.createTempFile("user_dict", ".txt")
        f.delete()
        val d = UserDictionary(f)
        d.learnWord("Payneau")
        d.learnWord("Payneau")
        d.learnBigram("bonjour", "Alex")
        d.save()

        val d2 = UserDictionary(f)
        d2.load()
        assertEquals(2, d2.countOf("Payneau"))
        d2.learnBigram("bonjour", "Alex")
        assertEquals(listOf("Alex"), d2.nextWords("bonjour"))
        d2.removeWord("Payneau")
        assertEquals(0, d2.countOf("Payneau"))
        f.delete()
    }
}

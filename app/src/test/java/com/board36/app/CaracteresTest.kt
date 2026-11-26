package com.board36.app

import com.board36.app.ime.KeyboardLayout
import com.board36.app.ime.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inventaire des caractères : tout ce qu'on peut avoir à écrire en français doit être
 * joignable, par une touche ou par un appui long. Sur la 1.14, `<` et `>` manquaient —
 * alors que la touche de la seconde page s'annonce « =\< ».
 */
class CaracteresTest {

    private val pages: List<KeyboardLayout> = listOf(
        Layouts.letters(numberRow = true), Layouts.letters(numberRow = false),
        Layouts.symbols(), Layouts.symbols2()
    )

    /** Caractères écrits par une touche, puis par appui long (majuscules comprises). */
    private val reachable: Set<Char> by lazy {
        val set = HashSet<Char>()
        for (page in pages) for (k in page.keys) {
            if (k.code > 0) set.add(k.code.toChar())
            for (c in k.popup) set.add(c)
            if (k.isLetter) {
                set.add(k.label[0].uppercaseChar())
                for (c in k.popup) set.add(c.uppercaseChar())
            }
        }
        set
    }

    @Test
    fun toutLASCIIImprimableEstJoignable() {
        val missing = (0x20..0x7E).map { it.toChar() }.filterNot { it in reachable }
        assertEquals("caractères ASCII sans touche ni appui long : $missing", emptyList<Char>(), missing)
    }

    @Test
    fun lesInferieurEtSuperieurOntLeurTouche() {
        val page2 = Layouts.symbols2().keys.map { it.label }
        assertTrue(page2.contains("<"))
        assertTrue(page2.contains(">"))
    }

    @Test
    fun laTypographieFrancaiseEstJoignable() {
        val french = "àâäçéèêëîïôöùûüÿœæÀÂÇÉÈÊËÎÏÔÙÛÜŒÆ«»“”’…–—€°§×÷±≤≥≠‰№"
        val missing = french.filterNot { it in reachable }
        assertEquals("caractères français manquants : $missing", "", missing)
    }

    @Test
    fun aucunePageNaDeToucheEnDouble() {
        for (page in pages) {
            val labels = page.keys.map { it.label }.filter { it.length == 1 }
            assertEquals("doublons : ${labels.groupBy { it }.filterValues { it.size > 1 }.keys}",
                labels.size, labels.toSet().size)
        }
    }
}

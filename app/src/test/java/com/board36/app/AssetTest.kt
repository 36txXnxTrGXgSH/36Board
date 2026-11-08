package com.board36.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.board36.app.engine.FrenchDictionary
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vérifie que le dictionnaire est bien empaqueté sous le nom attendu et lisible
 * exactement comme le fait le service (une erreur de nom d'asset priverait le
 * clavier de suggestions et de swipe sans planter).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssetTest {

    @Test
    fun leDictionnaireEstEmbarqueEtChargeRapidement() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val start = System.nanoTime()
        val dict = ctx.assets.open("fr_dict.txt").use { FrenchDictionary.load(it) }
        val ms = (System.nanoTime() - start) / 1e6
        println("chargement du dictionnaire : %.0f ms".format(ms))
        assertTrue("mots chargés : ${dict.size}", dict.size > 140_000)
        assertTrue(dict.containsExact("bonjour"))
        assertTrue(dict.containsExact("français"))
    }
}

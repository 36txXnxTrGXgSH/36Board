package com.board36.app.data

import android.content.Context
import com.board36.app.engine.UserDictionary
import com.board36.app.engine.WordBlocklist
import java.io.File

/**
 * Dictionnaire personnel et mots bloqués, **une seule instance** partagée par le service
 * de saisie et l'écran des réglages — ils tournent dans le même processus.
 *
 * Avec deux copies, chacune réécrivait le fichier avec son propre contenu : un mot supprimé
 * dans les réglages revenait dès que le clavier enregistrait ce qu'il venait d'apprendre,
 * et un mot bloqué depuis les réglages pouvait être écrasé par la liste du clavier.
 *
 * Les instances sont attachées à l'objet `Application` : une nouvelle application (ce que
 * fait chaque test Robolectric) repart de zéro, sans état hérité du test précédent.
 */
object UserData {

    private var owner: Context? = null
    private var dictionary: UserDictionary? = null
    private var blocklist: WordBlocklist? = null

    @Synchronized
    private fun app(context: Context): Context {
        val app = context.applicationContext
        if (owner !== app) {
            owner = app
            dictionary = null
            blocklist = null
        }
        return app
    }

    @Synchronized
    fun dictionary(context: Context): UserDictionary {
        val app = app(context)
        return dictionary ?: UserDictionary(File(app.filesDir, DICTIONARY_FILE))
            .also { it.load(); dictionary = it }
    }

    @Synchronized
    fun blocklist(context: Context): WordBlocklist {
        val app = app(context)
        return blocklist ?: WordBlocklist(File(app.filesDir, BLOCKLIST_FILE))
            .also { it.load(); blocklist = it }
    }

    const val DICTIONARY_FILE = "user_dict.txt"
    const val BLOCKLIST_FILE = "blocked_words.txt"
}

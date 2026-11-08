package com.board36.app.engine

import java.io.File

/**
 * Mots que le clavier ne doit jamais proposer.
 *
 * On bloque exactement ce qui est affiché, à la casse près : bloquer « l'Afrique »
 * n'empêche pas « Afrique » de sortir ailleurs. C'est le comportement le plus
 * prévisible — on bannit ce que l'on a vu de trop.
 */
class WordBlocklist(private val file: File?) {

    private val words = LinkedHashSet<String>()

    @Synchronized
    fun load() {
        words.clear()
        val f = file ?: return
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val w = line.trim()
                if (w.isNotEmpty()) words.add(w.lowercase())
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun save() {
        val f = file ?: return
        try {
            Storage.writeAtomically(f, words.joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun isBlocked(word: String): Boolean =
        words.isNotEmpty() && words.contains(word.lowercase())

    @Synchronized
    fun add(word: String): Boolean {
        val w = Storage.sanitizeEntry(word, MAX_LENGTH) ?: return false
        return words.add(w.lowercase())
    }

    @Synchronized
    fun remove(word: String) {
        words.remove(word.trim().lowercase())
    }

    @Synchronized
    fun clear() = words.clear()

    @Synchronized
    fun all(): List<String> = words.toList().sorted()

    companion object {
        const val MAX_LENGTH = 64
    }
}

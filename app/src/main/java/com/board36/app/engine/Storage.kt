package com.board36.app.engine

import java.io.File

/**
 * Écriture des petits fichiers de données du clavier (dictionnaire personnel, mots
 * bloqués) et contrôle de ce qu'on y met.
 *
 * Ces fichiers sont des formats à lignes et à tabulations : un mot contenant lui-même
 * une tabulation ou un retour à la ligne — un collage depuis les réglages suffit —
 * y créerait des entrées fantômes à la relecture. On refuse ces mots à l'entrée plutôt
 * que de les tronquer en silence.
 */
internal object Storage {

    /**
     * Remplace le contenu de [file] sans jamais laisser le fichier à moitié écrit :
     * on écrit un `.tmp` complet, puis on bascule. Si la bascule échoue (systèmes de
     * fichiers qui refusent d'écraser), on retombe sur une écriture directe.
     */
    fun writeAtomically(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (tmp.renameTo(file)) return
        // POSIX renomme par-dessus, pas Windows ni certains stockages Android : on efface
        // la cible puis on retente, et en dernier recours on écrit directement.
        if (file.exists() && file.delete() && tmp.renameTo(file)) return
        file.writeText(content)
        tmp.delete()
    }

    /**
     * Nettoie une entrée saisie par l'utilisateur, ou retourne null si elle ne peut pas
     * être stockée : vide, trop longue, ou porteuse d'un séparateur du format.
     */
    fun sanitizeEntry(raw: String, maxLength: Int): String? {
        val w = raw.trim()
        if (w.isEmpty() || w.length > maxLength) return null
        for (c in w) {
            if (c == '\t' || c == '\n' || c == '\r' || c.code < 0x20) return null
        }
        return w
    }
}

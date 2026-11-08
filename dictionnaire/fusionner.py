"""Fusionne le vocabulaire familier (familier.md) dans le dictionnaire de base.

    python dictionnaire/fusionner.py            vérifie et fusionne
    python dictionnaire/fusionner.py --verifier vérifie seulement (n'écrit rien)

Le clavier cherche les mots par dichotomie : fr_dict.txt doit rester trié par forme
normalisée (sans accents, ni apostrophe, ni trait d'union), puis par fréquence
décroissante. La normalisation ci-dessous reproduit `Fr.base` (engine/Fr.kt) ; le test
`DictionnaireFamilierTest` vérifie de son côté le tri avec le vrai code du clavier.

Un mot déjà présent garde la plus forte des deux fréquences : la fusion n'abaisse jamais
un mot du dictionnaire d'origine.
"""
import os
import re
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
DICO = os.path.join(ICI, "..", "app", "src", "main", "assets", "fr_dict.txt")
SOURCE = os.path.join(ICI, "familier.md")

_BASE = {}
for chars, b in [("àâäáãå", "a"), ("ÀÂÄÁÃÅ", "a"), ("çÇ", "c"), ("éèêë", "e"), ("ÉÈÊË", "e"),
                 ("îïíì", "i"), ("ÎÏÍÌ", "i"), ("ôöóòõ", "o"), ("ÔÖÓÒÕ", "o"), ("ùûüú", "u"),
                 ("ÙÛÜÚ", "u"), ("ÿýŸÝ", "y"), ("ñÑ", "n"), ("œŒ", "oe"), ("æÆ", "ae"), ("ß", "ss")]:
    for c in chars:
        _BASE[c] = b


def base(c):
    if "a" <= c <= "z":
        return c
    if "A" <= c <= "Z":
        return c.lower()
    return _BASE.get(c, "")


def normalise(mot):
    return "".join(base(c) for c in mot)


def lire_dictionnaire():
    entrees = []
    with open(DICO, encoding="utf-8", newline="") as f:
        for ligne in f:
            mot, freq = ligne.rstrip("\n").split("\t")
            entrees.append((mot, int(freq)))
    return entrees


def lire_familier():
    """Lignes de tableau « | mot | sens | registre | fréquence | » de familier.md."""
    mots = []
    for ligne in open(SOURCE, encoding="utf-8"):
        cases = [c.strip() for c in ligne.strip().strip("|").split("|")]
        if len(cases) != 4 or not cases[3].isdigit():
            continue
        mot = cases[0].strip("`")
        mots.append((mot, int(cases[3]), cases[2]))
    return mots


def cle(entree):
    mot, freq = entree
    return (normalise(mot), -freq)


def est_trie(entrees):
    return all(cle(a) <= cle(b) for a, b in zip(entrees, entrees[1:]))


def main():
    verifier = "--verifier" in sys.argv
    entrees = lire_dictionnaire()
    print(f"dictionnaire : {len(entrees)} mots, trié : {est_trie(entrees)}")
    if not est_trie(entrees):
        sys.exit("ARRÊT : le dictionnaire d'origine n'est pas trié selon la clé attendue")

    familier = lire_familier()
    doublons = {m for m, _, _ in familier if [x for x, _, _ in familier].count(m) > 1}
    if doublons:
        sys.exit(f"ARRÊT : mots en double dans familier.md : {sorted(doublons)}")
    for mot, _, _ in familier:
        if not normalise(mot):
            sys.exit(f"ARRÊT : « {mot} » n'a aucune lettre tapable")

    index = {mot: i for i, (mot, _) in enumerate(entrees)}
    ajoutes, releves, inchanges = [], [], []
    for mot, freq, _ in familier:
        if mot in index:
            ancien = entrees[index[mot]][1]
            if freq > ancien:
                entrees[index[mot]] = (mot, freq)
                releves.append(f"{mot} {ancien}→{freq}")
            else:
                inchanges.append(mot)
        else:
            entrees.append((mot, freq))
            ajoutes.append(mot)

    entrees.sort(key=cle)   # tri stable : l'ordre d'origine est conservé à clé égale
    print(f"familier.md : {len(familier)} mots — {len(ajoutes)} ajoutés, "
          f"{len(releves)} relevés, {len(inchanges)} déjà assez fréquents")
    if verifier:
        return
    with open(DICO, "w", encoding="utf-8", newline="") as f:
        for mot, freq in entrees:
            f.write(f"{mot}\t{freq}\n")
    print(f"écrit : {len(entrees)} mots, trié : {est_trie(lire_dictionnaire())}")


if __name__ == "__main__":
    main()

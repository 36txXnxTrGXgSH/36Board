# 36Board — clavier AZERTY français pour Android

Clavier virtuel indépendant, entièrement hors ligne. Une seule permission demandée —
la vibration des touches — et **aucun accès au réseau** : rien de ce qui est tapé ne
peut quitter le téléphone.

## Fonctionnalités

- **AZERTY Français (France)** : AZERTYUIOP / QSDFGHJKLM / (Maj) WXCVBN ' (Suppr),
  rangée de chiffres optionnelle, pages de symboles (`?123` et `=\<`).
- **Saisie gestuelle (swipe)** : décodeur inspiré de SHARK², comparaison du tracé
  au chemin idéal de chaque mot (canal de position + canal de forme) pondérée par
  la fréquence. ~2 ms par mot sur JVM.
- **Dictionnaire français de 150 000 mots** embarqué (Lexique 3.83 + fréquences
  OpenSubtitles), recherche par préfixe insensible aux accents : « ecole » → « école ».
- **Suggestions et correction** : complétion, correction des touches voisines, des
  lettres oubliées, en trop ou inversées, rétablissement des accents.
- **Apprentissage** : dictionnaire personnel (mots + enchaînements) stocké localement,
  consultable et modifiable dans l'application.
- **Accents par appui long** sur chaque lettre (é è ê ë, à â, ç, ù û…).
- **Emoji** : plus de 1 300 emoji embarqués, rangés en neuf familles, avec les derniers
  utilisés en tête. Le bouton reste à gauche de la bande de propositions, à un doigt, et
  ce que la police du téléphone ne sait pas dessiner n'est pas proposé.
- **Bande de propositions épurée** : les mots au centre, les emoji à gauche, les réglages
  à droite — et rien d'autre. Couper, copier et coller ne s'y trouvent plus : Android les
  propose déjà par un appui long dans le texte, là où l'on édite.
- **Confort** : glissement sur la barre d'espace pour déplacer le curseur, balayage vers la
  gauche depuis Retour arrière pour sélectionner puis effacer de plus en plus de texte
  (jusqu'à tout ce qui précède au bord de l'écran), effacement d'un bloc du mot tracé,
  double espace = point,
  majuscule automatique, aperçu des touches, vibration et son réglables.
- **Thèmes réglables** : clair / sombre / système, ou personnalisés — couleur du fond, des
  touches, des caractères et de l'accent, contraste global, huit palettes prêtes à l'emploi,
  le tout au-dessus d'un aperçu qui est le vrai clavier. Hauteur réglable de 70 % à 150 %,
  et **marge sous le clavier** réglable de 0 à 64 dp pour le remonter au-dessus des
  touches de navigation du téléphone.
- **Lettres du mot en cours colorées** sur le clavier (option, couleur au choix).
- **Mots interdits** : un appui long sur une proposition la bannit définitivement — pratique
  quand un tracé donne toujours le même mot indésirable. Liste gérable dans les réglages.

## Vie privée

Un clavier voit tout ce que l'on écrit. 36Board est construit pour qu'il n'ait
techniquement aucun moyen d'en faire quoi que ce soit :

| | |
|---|---|
| Permissions | `VIBRATE` seulement |
| Réseau | Aucun : la permission `INTERNET` n'est pas déclarée, l'application ne peut pas ouvrir de connexion |
| Micro, caméra, contacts, fichiers | Aucun accès |
| Sauvegardes | `allowBackup=false` + règles d'extraction : le dictionnaire personnel ne part ni vers le cloud, ni vers un autre téléphone |
| Données stockées | `user_dict.txt`, `blocked_words.txt` et les réglages (dont les emoji récents), dans le stockage privé de l'application |
| Champs mot de passe | Ni swipe, ni lettres colorées, ni aperçu des touches, ni « double espace = point », ni apprentissage, ni emoji récents : rien ne s'y voit, rien ne s'y retient, rien n'y est modifié en douce |
| Navigation privée | Respectée : quand l'application la demande (`IME_FLAG_NO_PERSONALIZED_LEARNING`), et dans tout champ qui refuse les propositions, le clavier n'apprend rien |
| « Apprendre mes mots » | Coupé, il l'est partout — y compris quand on touche une proposition |
| Traceurs, statistiques, publicité | Aucun, et aucune dépendance qui en contienne |

La dictée vocale hors ligne (Vosk) a été **retirée** en version 1.11 : elle imposait la
permission micro, la permission réseau pour récupérer le modèle, et 9 Mo de code natif
tiers dans l'application. Le clavier système reste accessible pour dicter, au choix de
l'utilisateur, dans une application qui en assume les permissions.

## À propos

Application **en français uniquement**, réalisée entièrement avec Claude (Anthropic).

## Compilation

```bash
export ANDROID_HOME=~/Android/sdk
./gradlew assembleDebug            # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest        # tests unitaires (moteur, dispositions, rendu, saisie)
```

Kotlin 2.0.21 · AGP 8.7.2 · Gradle 8.11.1 · JDK 17 ou 21 · minSdk 26 · targetSdk 35.

Gradle 8.11.1 n'accepte pas de JDK plus récent que le 23 : si le JDK du système est
plus avancé (celui d'Android Studio, par exemple), la compilation s'arrête sur un
message réduit au numéro de version. Indiquez alors le JDK à utiliser, soit par
`JAVA_HOME`, soit par `org.gradle.java.home` dans `~/.gradle/gradle.properties`.

### Version release signée

La clé de signature n'est pas versionnée. Créez-en une, puis décrivez-la dans
`keystore.properties` à la racine du projet (fichier à garder hors de tout dépôt) :

```properties
storeFile=36board-release.jks
storePassword=…
keyAlias=36board
keyPassword=…
```

```bash
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
```

Sans `keystore.properties`, la version release se construit non signée. La release est
réduite et obfusquée par R8 (`isMinifyEnabled`), avec les règles de conservation de
`app/proguard-rules.pro`.

## Installation sur le téléphone

1. Installer l'APK.
2. Ouvrir **36Board** → « Activer le clavier » → cocher 36Board.
3. « Choisir 36Board » pour en faire le clavier courant.

## Structure

```
engine/   Fr, FrenchDictionary, GestureDecoder, Suggester, UserDictionary,
          WordBlocklist, KeyGeometry, AzertyModel, Storage, Emoji
ime/      Board36Service (InputMethodService), KeyboardView, SuggestionStripView,
          EmojiPanelView, PopupOverlayView, InputRootView, Layouts, Keys,
          KeyboardLayout, KeyboardTheme, ThemeBuilder
ui/       MainActivity (réglages Compose Material 3, dictionnaire personnel,
          mots bloqués), ThemeScreen
data/     Prefs
test/     dictionnaire, décodeur gestuel, suggestions, dispositions, emoji,
          performances, parcours de saisie réels, rendu des vues en PNG
          (Robolectric, mode graphique natif)
```

## Données

- Lexique 3.83 — New & Pallier, lexique.org — licence CC BY-SA 4.0.
- FrequencyWords (OpenSubtitles 2018, fr) — hermitdave — licence MIT.

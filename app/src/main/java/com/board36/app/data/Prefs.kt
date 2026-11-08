package com.board36.app.data

import android.content.Context
import android.content.SharedPreferences

/** Préférences partagées entre l'application de réglages et le service clavier. */
class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString(THEME, "system") ?: "system"
        set(v) = sp.edit().putString(THEME, v).apply()

    /** Hauteur du clavier, en pourcentage de la hauteur de référence (70..150). */
    var heightPercent: Int
        get() = sp.getInt(HEIGHT, 100)
        set(v) = sp.edit().putInt(HEIGHT, v).apply()

    /**
     * Marge laissée sous le clavier, en dp. Sert à remonter le clavier au-dessus des
     * touches de navigation du téléphone quand le système ne réserve pas la place
     * lui-même (elle s'ajoute à l'encart de la barre de navigation, jamais à sa place).
     */
    var bottomMargin: Int
        get() = sp.getInt(BOTTOM_MARGIN, 0)
        set(v) = sp.edit().putInt(BOTTOM_MARGIN, v).apply()

    var numberRow: Boolean
        get() = sp.getBoolean(NUMBER_ROW, true)
        set(v) = sp.edit().putBoolean(NUMBER_ROW, v).apply()

    var gestureTyping: Boolean
        get() = sp.getBoolean(GESTURE, true)
        set(v) = sp.edit().putBoolean(GESTURE, v).apply()

    var suggestions: Boolean
        get() = sp.getBoolean(SUGGESTIONS, true)
        set(v) = sp.edit().putBoolean(SUGGESTIONS, v).apply()

    var autoCorrect: Boolean
        get() = sp.getBoolean(AUTOCORRECT, true)
        set(v) = sp.edit().putBoolean(AUTOCORRECT, v).apply()

    var autoCap: Boolean
        get() = sp.getBoolean(AUTOCAP, true)
        set(v) = sp.edit().putBoolean(AUTOCAP, v).apply()

    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean(DOUBLE_SPACE, true)
        set(v) = sp.edit().putBoolean(DOUBLE_SPACE, v).apply()

    var learnWords: Boolean
        get() = sp.getBoolean(LEARN, true)
        set(v) = sp.edit().putBoolean(LEARN, v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean(VIBRATE, true)
        set(v) = sp.edit().putBoolean(VIBRATE, v).apply()

    var vibrateMs: Int
        get() = sp.getInt(VIBRATE_MS, 14)
        set(v) = sp.edit().putInt(VIBRATE_MS, v).apply()

    var sound: Boolean
        get() = sp.getBoolean(SOUND, false)
        set(v) = sp.edit().putBoolean(SOUND, v).apply()

    var keyPreview: Boolean
        get() = sp.getBoolean(PREVIEW, true)
        set(v) = sp.edit().putBoolean(PREVIEW, v).apply()

    var spaceCursor: Boolean
        get() = sp.getBoolean(SPACE_CURSOR, true)
        set(v) = sp.edit().putBoolean(SPACE_CURSOR, v).apply()

    var autoSpaceAfterGesture: Boolean
        get() = sp.getBoolean(GESTURE_SPACE, true)
        set(v) = sp.edit().putBoolean(GESTURE_SPACE, v).apply()

    /**
     * Emoji récemment utilisés, du plus récent au plus ancien, séparés par une unité
     * de séparation — un emoji peut contenir des espaces à largeur nulle, pas ce
     * caractère de contrôle.
     */
    var recentEmoji: List<String>
        get() = sp.getString(RECENT_EMOJI, "")
            ?.split(EMOJI_SEP)?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString(RECENT_EMOJI, v.joinToString(EMOJI_SEP)).apply()

    // ---------------------------------------------------------------- thème

    /** Couleurs du thème personnalisé (utilisées quand theme = "custom"). */
    var colorBackground: Int
        get() = sp.getInt(COLOR_BG, 0xFF16181B.toInt())
        set(v) = sp.edit().putInt(COLOR_BG, v).apply()

    var colorKey: Int
        get() = sp.getInt(COLOR_KEY, 0xFF2A2D32.toInt())
        set(v) = sp.edit().putInt(COLOR_KEY, v).apply()

    var colorText: Int
        get() = sp.getInt(COLOR_TEXT, 0xFFECEFF1.toInt())
        set(v) = sp.edit().putInt(COLOR_TEXT, v).apply()

    var colorAccent: Int
        get() = sp.getInt(COLOR_ACCENT, 0xFF63C2B4.toInt())
        set(v) = sp.edit().putInt(COLOR_ACCENT, v).apply()

    /** Contraste global du clavier : 0 = doux, 50 = neutre, 100 = marqué. */
    var contrast: Int
        get() = sp.getInt(CONTRAST, 50)
        set(v) = sp.edit().putInt(CONTRAST, v).apply()

    /** Colorer sur le clavier les lettres du mot en cours d'écriture. */
    var highlightTypedLetters: Boolean
        get() = sp.getBoolean(HL_LETTERS, true)
        set(v) = sp.edit().putBoolean(HL_LETTERS, v).apply()

    var highlightColor: Int
        get() = sp.getInt(HL_COLOR, 0xFFF2B84B.toInt())
        set(v) = sp.edit().putInt(HL_COLOR, v).apply()

    companion object {
        const val NAME = "36board"
        const val THEME = "theme"
        const val HEIGHT = "height_percent"
        const val BOTTOM_MARGIN = "bottom_margin"
        const val NUMBER_ROW = "number_row"
        const val GESTURE = "gesture_typing"
        const val SUGGESTIONS = "suggestions"
        const val AUTOCORRECT = "auto_correct"
        const val AUTOCAP = "auto_cap"
        const val DOUBLE_SPACE = "double_space_period"
        const val LEARN = "learn_words"
        const val VIBRATE = "vibrate"
        const val VIBRATE_MS = "vibrate_ms"
        const val SOUND = "sound"
        const val PREVIEW = "key_preview"
        const val SPACE_CURSOR = "space_cursor"
        const val GESTURE_SPACE = "gesture_auto_space"
        const val COLOR_BG = "color_background"
        const val COLOR_KEY = "color_key"
        const val COLOR_TEXT = "color_text"
        const val COLOR_ACCENT = "color_accent"
        const val CONTRAST = "contrast"
        const val HL_LETTERS = "highlight_letters"
        const val HL_COLOR = "highlight_color"
        const val RECENT_EMOJI = "recent_emoji"
        const val EMOJI_SEP = "\u001F"

        /** Nombre d'emoji conservés dans les récents. */
        const val MAX_RECENT_EMOJI = 40
    }
}

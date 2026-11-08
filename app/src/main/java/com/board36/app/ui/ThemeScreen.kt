package com.board36.app.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.board36.app.data.Prefs
import com.board36.app.ime.KeyboardTheme
import com.board36.app.ime.KeyboardView
import com.board36.app.ime.Layouts
import com.board36.app.ime.SuggestionStripView
import com.board36.app.ime.ThemeBuilder
import kotlin.math.roundToInt

/**
 * Éditeur de thème du clavier : couleurs du fond, des touches, des caractères et de
 * l'accent, contraste global, et mise en couleur des lettres du mot en cours.
 * Tout se règle au-dessus d'un aperçu qui est le vrai clavier, pas une image.
 */
@Composable
fun ThemeScreen(activity: ComponentActivity) {
    val prefs = remember { Prefs(activity) }
    val scroll = rememberScrollState()

    var mode by remember { mutableStateOf(prefs.theme) }
    var bg by remember { mutableIntStateOf(prefs.colorBackground) }
    var keyColor by remember { mutableIntStateOf(prefs.colorKey) }
    var textColor by remember { mutableIntStateOf(prefs.colorText) }
    var accent by remember { mutableIntStateOf(prefs.colorAccent) }
    var contrast by remember { mutableIntStateOf(prefs.contrast) }
    var highlight by remember { mutableStateOf(prefs.highlightTypedLetters) }
    var highlightColor by remember { mutableIntStateOf(prefs.highlightColor) }

    val custom = mode == "custom"
    val theme = remember(mode, bg, keyColor, textColor, accent, contrast) {
        when (mode) {
            "light" -> ThemeBuilder.build(ThemeBuilder.PALETTES[1], contrast)
            "dark" -> ThemeBuilder.build(ThemeBuilder.PALETTES[0], contrast)
            "custom" -> ThemeBuilder.build(bg, keyColor, textColor, accent, contrast)
            else -> ThemeBuilder.build(ThemeBuilder.PALETTES[0], contrast)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        KeyboardPreview(theme, highlight, highlightColor)

        Spacer(Modifier.height(16.dp))
        Text("Thème", fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                "system" to "Système", "light" to "Clair",
                "dark" to "Sombre", "custom" to "Perso."
            ).forEach { (k, label) ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)) {
                    RadioButton(selected = mode == k, onClick = { mode = k; prefs.theme = k })
                    Text(label, fontSize = 13.sp)
                }
            }
        }

        Text("Palettes", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeBuilder.PALETTES.forEach { p ->
                PaletteChip(p) {
                    bg = p.background; keyColor = p.key
                    textColor = p.text; accent = p.accent
                    prefs.colorBackground = p.background
                    prefs.colorKey = p.key
                    prefs.colorText = p.text
                    prefs.colorAccent = p.accent
                    mode = "custom"
                    prefs.theme = "custom"
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Couleurs", fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary)
        if (!custom) {
            Text(
                "Choisissez « Perso. » ci-dessus pour modifier les couleurs.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ColorRow("Fond du clavier", bg, custom) { bg = it; prefs.colorBackground = it }
        ColorRow("Touches", keyColor, custom) { keyColor = it; prefs.colorKey = it }
        ColorRow("Caractères", textColor, custom) { textColor = it; prefs.colorText = it }
        ColorRow("Accent (entrée, sélection)", accent, custom) { accent = it; prefs.colorAccent = it }

        Spacer(Modifier.height(12.dp))
        Text("Contraste : ${contrastLabel(contrast)}", fontSize = 14.sp)
        Slider(
            value = contrast.toFloat(),
            onValueChange = { contrast = it.roundToInt() },
            onValueChangeFinished = { prefs.contrast = contrast },
            valueRange = 0f..100f,
            steps = 9
        )
        ContrastAdvice(theme)

        Spacer(Modifier.height(16.dp))
        Text("Lettres du mot en cours", fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Colorer les lettres employées", fontSize = 15.sp)
                Text(
                    "Sur le clavier, les lettres qui composent le mot en train d'être " +
                        "écrit prennent la couleur choisie.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = highlight, onCheckedChange = {
                highlight = it; prefs.highlightTypedLetters = it
            })
        }
        ColorRow("Couleur des lettres employées", highlightColor, highlight) {
            highlightColor = it; prefs.highlightColor = it
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** Aperçu : la vraie vue du clavier, avec le thème en cours d'édition. */
@Composable
private fun KeyboardPreview(theme: KeyboardTheme, highlight: Boolean, highlightColor: Int) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        factory = { context ->
            val column = LinearLayout(context)
            column.orientation = LinearLayout.VERTICAL

            val strip = SuggestionStripView(context)
            strip.setSuggestions(listOf("clav", "clavier", "clavecin"), 1)
            column.addView(strip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (40 * context.resources.displayMetrics.density).toInt()))

            val keyboard = KeyboardView(context)
            keyboard.setLayout(Layouts.letters(numberRow = false))
            keyboard.setHighlightedLetters(setOf('c', 'l', 'a', 'v'))
            column.addView(keyboard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
            column
        },
        update = { column ->
            val strip = column.getChildAt(0) as SuggestionStripView
            val keyboard = column.getChildAt(1) as KeyboardView
            strip.theme = theme
            keyboard.theme = theme
            keyboard.highlightEnabled = highlight
            keyboard.highlightColor = highlightColor
            keyboard.invalidate()
        }
    )
}

@Composable
private fun PaletteChip(palette: ThemeBuilder.Palette, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(UiColor(palette.background))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Swatch(palette.key, 18)
            Swatch(palette.text, 18)
            Swatch(palette.accent, 18)
        }
        Text(palette.name, fontSize = 11.sp)
    }
}

@Composable
private fun Swatch(color: Int, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(UiColor(color))
    )
}

/** Ligne de couleur : pastille, valeur, et trois curseurs rouge/vert/bleu dépliables. */
@Composable
private fun ColorRow(label: String, color: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { open = !open }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(UiColor(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(hex(color), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (enabled) Text(if (open) "Fermer" else "Modifier", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary)
        }
        if (open && enabled) {
            Channel("Rouge", Color.red(color)) { onChange(Color.rgb(it, Color.green(color), Color.blue(color))) }
            Channel("Vert", Color.green(color)) { onChange(Color.rgb(Color.red(color), it, Color.blue(color))) }
            Channel("Bleu", Color.blue(color)) { onChange(Color.rgb(Color.red(color), Color.green(color), it)) }
        }
    }
}

@Composable
private fun Channel(name: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text("$value", fontSize = 12.sp, modifier = Modifier.width(34.dp))
    }
}

/** Avertit si le texte devient difficile à lire sur les touches. */
@Composable
private fun ContrastAdvice(theme: KeyboardTheme) {
    val ratio = ThemeBuilder.contrastRatio(theme.text, theme.key)
    val ok = ratio >= 4.5f
    Text(
        if (ok) "Lisibilité : bonne (rapport %.1f:1)".format(ratio)
        else "Lisibilité : faible (rapport %.1f:1) — éclaircissez les caractères ou assombrissez les touches.".format(ratio),
        fontSize = 12.sp,
        color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    )
}

private fun contrastLabel(v: Int): String = when {
    v < 25 -> "doux"
    v < 45 -> "atténué"
    v <= 55 -> "neutre"
    v < 80 -> "marqué"
    else -> "maximal"
}

private fun hex(color: Int): String =
    "#%02X%02X%02X".format(Color.red(color), Color.green(color), Color.blue(color))

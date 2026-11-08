package com.board36.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.board36.app.data.Prefs
import com.board36.app.data.UserData
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(this)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(activity: ComponentActivity) {
    var screen by remember { mutableStateOf("main") }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when (screen) {
                        "dict" -> "Dictionnaire personnel"
                        "theme" -> "Thème du clavier"
                        "blocked" -> "Mots bloqués"
                        else -> "36Board"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }, navigationIcon = {
                if (screen != "main") {
                    TextButton(onClick = { screen = "main" }) { Text("Retour") }
                }
            })
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                "dict" -> PersonalDictionaryScreen(activity)
                "theme" -> ThemeScreen(activity)
                "blocked" -> BlockedWordsScreen(activity)
                else -> SettingsScreen(activity) { screen = it }
            }
        }
    }
}

@Composable
private fun SettingsScreen(activity: ComponentActivity, navigate: (String) -> Unit) {
    val prefs = remember { Prefs(activity) }
    val scroll = rememberScrollState()

    var height by remember { mutableStateOf(prefs.heightPercent) }
    var bottomMargin by remember { mutableStateOf(prefs.bottomMargin) }
    var numberRow by remember { mutableStateOf(prefs.numberRow) }
    var preview by remember { mutableStateOf(prefs.keyPreview) }
    var gesture by remember { mutableStateOf(prefs.gestureTyping) }
    var gestureSpace by remember { mutableStateOf(prefs.autoSpaceAfterGesture) }
    var suggestions by remember { mutableStateOf(prefs.suggestions) }
    var autoCorrect by remember { mutableStateOf(prefs.autoCorrect) }
    var autoCap by remember { mutableStateOf(prefs.autoCap) }
    var doubleSpace by remember { mutableStateOf(prefs.doubleSpacePeriod) }
    var learn by remember { mutableStateOf(prefs.learnWords) }
    var spaceCursor by remember { mutableStateOf(prefs.spaceCursor) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    var vibrateMs by remember { mutableStateOf(prefs.vibrateMs) }
    var sound by remember { mutableStateOf(prefs.sound) }

    val enabled = remember { mutableStateOf(isEnabled(activity)) }

    LaunchedEffect(Unit) {
        enabled.value = isEnabled(activity)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Installation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled.value) "36Board est activé dans les paramètres du système."
                    else "Étape 1 : activez 36Board dans la liste des claviers.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        activity.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }) { Text("Activer le clavier") }
                    OutlinedButton(onClick = {
                        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                                as InputMethodManager
                        imm.showInputMethodPicker()
                    }) { Text("Choisir 36Board") }
                }
            }
        }

        Section("Apparence")
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Thème du clavier", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    "Couleurs du fond, des touches et des caractères, contraste, et " +
                        "mise en couleur des lettres du mot en cours.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { navigate("theme") }) { Text("Ouvrir l'éditeur de thème") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Hauteur du clavier : ${height} %", fontSize = 14.sp)
        Slider(
            value = height.toFloat(),
            onValueChange = { height = it.roundToInt() },
            onValueChangeFinished = { prefs.heightPercent = height },
            valueRange = 70f..150f,
            steps = 15
        )
        Text("Marge sous le clavier : ${bottomMargin} dp", fontSize = 14.sp)
        Text(
            "Remonte le clavier au-dessus des touches de navigation du téléphone, quand " +
                "le système ne réserve pas la place lui-même.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = bottomMargin.toFloat(),
            onValueChange = { bottomMargin = it.roundToInt() },
            onValueChangeFinished = { prefs.bottomMargin = bottomMargin },
            valueRange = 0f..64f,
            steps = 15
        )
        SwitchRow("Rangée de chiffres", "Affiche 1 à 0 au-dessus des lettres", numberRow) {
            numberRow = it; prefs.numberRow = it
        }
        SwitchRow("Aperçu des touches", "Agrandit la lettre au moment de l'appui", preview) {
            preview = it; prefs.keyPreview = it
        }

        Section("Saisie")
        SwitchRow("Saisie gestuelle (swipe)", "Écrire un mot en glissant le doigt d'une lettre à l'autre", gesture) {
            gesture = it; prefs.gestureTyping = it
        }
        SwitchRow("Espace automatique après un swipe", "Sépare les mots tracés sans lever le pouce", gestureSpace) {
            gestureSpace = it; prefs.autoSpaceAfterGesture = it
        }
        SwitchRow("Suggestions", "Propositions de mots pendant la frappe", suggestions) {
            suggestions = it; prefs.suggestions = it
        }
        SwitchRow("Correction automatique", "Corrige le mot à l'espace si une faute est détectée", autoCorrect) {
            autoCorrect = it; prefs.autoCorrect = it
        }
        SwitchRow("Majuscule en début de phrase", "", autoCap) { autoCap = it; prefs.autoCap = it }
        SwitchRow("Double espace = point", "Deux espaces insèrent « . »", doubleSpace) {
            doubleSpace = it; prefs.doubleSpacePeriod = it
        }
        SwitchRow("Apprendre mes mots", "Mémorise le vocabulaire et les enchaînements", learn) {
            learn = it; prefs.learnWords = it
        }
        SwitchRow("Curseur sur la barre d'espace", "Glisser sur l'espace déplace le curseur", spaceCursor) {
            spaceCursor = it; prefs.spaceCursor = it
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { navigate("dict") }) { Text("Dictionnaire personnel") }
            OutlinedButton(onClick = { navigate("blocked") }) { Text("Mots bloqués") }
        }

        Section("Retour tactile et sonore")
        SwitchRow("Vibration", "", vibrate) { vibrate = it; prefs.vibrate = it }
        if (vibrate) {
            Text("Intensité : ${vibrateMs} ms", fontSize = 14.sp)
            Slider(
                value = vibrateMs.toFloat(),
                onValueChange = { vibrateMs = it.roundToInt() },
                onValueChangeFinished = { prefs.vibrateMs = vibrateMs },
                valueRange = 0f..40f,
                steps = 8
            )
        }
        SwitchRow("Son des touches", "", sound) { sound = it; prefs.sound = it }

        Section("À propos")
        Text(
            "36Board — clavier AZERTY français indépendant, avec saisie gestuelle et " +
                "dictionnaire de 150 000 mots embarqué. " +
                "L'application ne demande qu'une permission : la vibration. Pas de réseau, " +
                "pas de micro, pas d'accès aux fichiers — rien de ce que vous tapez ne peut " +
                "quitter le téléphone, et le dictionnaire personnel est exclu des sauvegardes.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PersonalDictionaryScreen(activity: ComponentActivity) {
    // la même instance que le clavier : une suppression ici n'est plus réécrite par lui
    val dict = remember { UserData.dictionary(activity) }
    var items by remember { mutableStateOf(dict.entries()) }
    var newWord by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it },
                label = { Text("Ajouter un mot") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (newWord.isNotBlank()) {
                    dict.addWord(newWord.trim())
                    dict.save()
                    items = dict.entries()
                    newWord = ""
                }
            }) { Text("Ajouter") }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Aucun mot appris pour le moment. 36Board ajoute ici les mots que vous " +
                "utilisez et qui ne figurent pas dans le dictionnaire français.", fontSize = 14.sp)
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
            items(items.size) { i ->
                val (w, c) = items[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w, Modifier.weight(1f), fontSize = 15.sp)
                    Text("×$c", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        dict.removeWord(w); dict.save(); items = dict.entries()
                    }) { Text("Supprimer") }
                }
                HorizontalDivider()
            }
        }
        if (items.isNotEmpty()) {
            OutlinedButton(onClick = { dict.clear(); dict.save(); items = dict.entries() }) {
                Text("Tout effacer")
            }
        }
    }
}

/**
 * Mots que le clavier ne proposera plus. On peut les ajouter ici, ou directement
 * depuis la bande de propositions par un appui long sur le mot fautif.
 */
@Composable
private fun BlockedWordsScreen(activity: ComponentActivity) {
    val list = remember {
        UserData.blocklist(activity)
    }
    var items by remember { mutableStateOf(list.all()) }
    var newWord by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Un appui long sur une proposition du clavier ajoute le mot ici. " +
                "Utile quand un tracé donne toujours le même mot indésirable.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it },
                label = { Text("Bloquer un mot") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (newWord.isNotBlank()) {
                    list.add(newWord.trim()); list.save()
                    items = list.all(); newWord = ""
                }
            }) { Text("Bloquer") }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Aucun mot bloqué pour le moment.", fontSize = 14.sp)
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
            items(items.size) { i ->
                val w = items[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w, Modifier.weight(1f), fontSize = 15.sp)
                    TextButton(onClick = {
                        list.remove(w); list.save(); items = list.all()
                    }) { Text("Rétablir") }
                }
                HorizontalDivider()
            }
        }
        if (items.isNotEmpty()) {
            OutlinedButton(onClick = { list.clear(); list.save(); items = list.all() }) {
                Text("Tout rétablir")
            }
        }
    }
}

private fun isEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

package io.github.rakasans.foodapp.ui.importing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.data.local.MaterialKind
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.domain.DictationText
import io.github.rakasans.foodapp.domain.RecipeTextParser
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.NoticeCard
import kotlinx.coroutines.launch

/**
 * Dyktowanie (v0.4): rozpoznawanie mowy zapewnia telefon (systemowe okno Google), tekst trafia do pola,
 * które można poprawić, a potem — jak wklejony tekst — do parsera i szkicu. Oryginał (transkrypcja) zostaje przy przepisie.
 */
@Composable
fun DictationImportScreen(container: AppContainer, onOpenEditor: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (heard.isNullOrBlank()) error = "Nic nie usłyszałem — spróbuj jeszcze raz, bliżej telefonu."
        else { text = DictationText.append(text, heard); error = null }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mów — np. „dwie łyżki oliwy, nowa linia, 200 g mąki”")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer.launch(intent)
        } catch (e: ActivityNotFoundException) {
            error = "Na tym telefonie nie ma rozpoznawania mowy. Zainstaluj aplikację Google albo wpisz tekst ręcznie."
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoticeCard(BadgeKind.INFO) {
            Text("Jak dyktować", fontWeight = FontWeight.Bold)
            Text(
                "Mów fragmentami — każdy fragment trafia do nowej linii. Najpierw nazwa, potem „składniki” i po jednym składniku, " +
                    "potem „przygotowanie” i kroki. W środku fragmentu powiedz „nowa linia”, żeby zacząć kolejną.",
            )
        }
        Button(onClick = ::listen, modifier = Modifier.fillMaxWidth()) {
            Text(if (text.isBlank()) "Zacznij mówić" else "Mów dalej")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Transkrypcja — możesz poprawić") },
            minLines = 8, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = text.isNotBlank(),
                onClick = { text = text.lines().dropLast(1).joinToString("\n") },
                modifier = Modifier.weight(1f),
            ) { Text("Usuń ostatnią linię", maxLines = 1) }
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    scope.launch {
                        val id = container.recipes.createDraftFromText(
                            RecipeTextParser.parse(text), text, SourceType.DICTATION, MaterialKind.DICTATION,
                        )
                        onOpenEditor(id)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Utwórz szkic", maxLines = 1) }
        }
        Text(
            "Rozpoznawanie mowy zapewnia telefon. Bez internetu działa, jeśli w ustawieniach Google masz pobrany język polski offline — inaczej pojawi się komunikat o braku sieci.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

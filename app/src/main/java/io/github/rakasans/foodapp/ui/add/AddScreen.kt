package io.github.rakasans.foodapp.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.ui.common.ActionTile
import io.github.rakasans.foodapp.ui.common.ActionTileGrid
import io.github.rakasans.foodapp.ui.common.HelpButton
import io.github.rakasans.foodapp.ui.common.HelpSheet
import io.github.rakasans.foodapp.ui.common.TileAction

/**
 * Wybór sposobu dodania przepisu: same etykiety z ikonami.
 * Opisy — po przytrzymaniu kafelka i w panelu „Jak dodawać przepisy?”.
 */
@Composable
fun AddScreen(
    onManual: () -> Unit,
    onLink: () -> Unit,
    onText: () -> Unit,
    onPhoto: () -> Unit,
    onDictation: () -> Unit,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }

    // Kolejność = częstość użycia. „Wpisz ręcznie” to wyjście awaryjne, więc osobno i skromniej.
    val main = listOf(
        TileAction("Wklej link", Icons.Outlined.Link,
            "Rolka z Instagrama albo strona z przepisem. Rolkę zapiszesz od razu, także bez internetu.", onLink),
        TileAction("Dodaj ze zdjęcia", Icons.Outlined.PhotoCamera,
            "Zdjęcie strony z książki lub czasopisma albo zrzut ekranu. Tekst rozpoznajemy na telefonie.", onPhoto),
        TileAction("Wklej tekst", Icons.Outlined.ContentPaste,
            "Skopiowany przepis z wiadomości, notatki lub maila.", onText),
        TileAction("Podyktuj", Icons.Outlined.Mic,
            "Powiedz przepis na głos, popraw transkrypcję.", onDictation),
    )
    val manual = TileAction("Wpisz ręcznie", Icons.Outlined.EditNote, "Pusty formularz przepisu.", onManual)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HelpButton("Jak dodawać przepisy?", onClick = { showHelp = true }, modifier = Modifier.align(Alignment.End))
        ActionTileGrid(main)
        ActionTile(manual, Modifier.fillMaxWidth(), compact = true)
    }

    if (showHelp) {
        HelpSheet(
            title = "Jak dodawać przepisy",
            actions = main + manual,
            onDismiss = { showHelp = false },
            footer = "Najszybciej: w Instagramie lub przeglądarce wybierz „Udostępnij → Zapisz w Przepisarni”. " +
                "Każdy przepis trafia najpierw do szkicu, który przejrzysz przed zatwierdzeniem.",
        )
    }
}

package io.github.rakasans.foodapp.ui.importing

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.data.instagram.ReelImporter
import io.github.rakasans.foodapp.data.local.MaterialKind
import io.github.rakasans.foodapp.data.local.ProcessingState
import io.github.rakasans.foodapp.data.local.RecipeEntity
import io.github.rakasans.foodapp.data.local.RecipeStatus
import io.github.rakasans.foodapp.data.local.SourceType
import io.github.rakasans.foodapp.domain.OcrCorrector
import io.github.rakasans.foodapp.domain.RecipeTextParser
import io.github.rakasans.foodapp.domain.extractUrl
import io.github.rakasans.foodapp.domain.isInstagramUrl
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.NoticeCard
import io.github.rakasans.foodapp.ui.common.RecipePhoto
import kotlinx.coroutines.launch
import java.io.File

/** Wspólny układ ekranów importu: przewijanie + odsunięcie od klawiatury. */
@Composable
private fun ImportColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

// ------------------------------------------------------------------ Link

@Composable
fun LinkImportScreen(
    container: AppContainer,
    initialText: String?,
    fromShare: Boolean,
    onOpenEditor: (String) -> Unit,
    onOpenOcrForReel: (String) -> Unit,
    onSaved: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialText?.let { extractUrl(it) ?: it }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    var reel by remember { mutableStateOf<ReelState>(ReelState.Idle) }
    val scope = rememberCoroutineScope()
    val clean = url.trim()
    val valid = clean.startsWith("http://") || clean.startsWith("https://")
    val instagram = valid && isInstagramUrl(clean)

    fun startReel() {
        reel = ReelState.Working
        scope.launch {
            reel = when (val r = container.reels.import(clean)) {
                is ReelImporter.Result.Draft -> { onOpenEditor(r.id); ReelState.Idle }
                is ReelImporter.Result.Waiting -> ReelState.Waiting(r.id)
                is ReelImporter.Result.Failed -> ReelState.Failed(r.id, r.reason)
            }
        }
    }

    fun retry(id: String) {
        reel = ReelState.Working
        scope.launch {
            reel = when (val r = container.reels.process(id)) {
                is ReelImporter.Result.Draft -> { onOpenEditor(r.id); ReelState.Idle }
                is ReelImporter.Result.Waiting -> ReelState.Waiting(r.id)
                is ReelImporter.Result.Failed -> ReelState.Failed(r.id, r.reason)
            }
        }
    }

    // Z „Udostępnij”: rolka importuje się od razu, bez dodatkowych kliknięć.
    LaunchedEffect(Unit) { if (fromShare && instagram) startReel() }

    ImportColumn {
        when (val st = reel) {
            ReelState.Idle -> {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("Adres przepisu lub rolki") },
                    placeholder = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = url.isNotBlank() && !valid,
                    supportingText = { if (url.isNotBlank() && !valid) Text("Adres powinien zaczynać się od https://") },
                )
                if (instagram) {
                    Text(
                        "Pobierzemy opis i zdjęcie rolki i od razu utworzymy szkic do sprawdzenia.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = ::startReel, modifier = Modifier.fillMaxWidth()) { Text("Importuj rolkę") }
                } else if (valid) {
                    NoticeCard(BadgeKind.INFO) {
                        Text("Strona z przepisem", fontWeight = FontWeight.Bold)
                        Text("Automatyczny import ze stron pojawi się w v0.3. Teraz możesz zapisać link na później albo od razu wpisać przepis — link zostanie jako źródło.")
                    }
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, label = { Text("Notatka (opcjonalnie)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        scope.launch { onOpenEditor(container.recipes.createEmptyDraft(SourceType.WEB, clean)) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Wprowadź przepis ręcznie") }
                    OutlinedButton(onClick = {
                        scope.launch { container.recipes.saveLinkMaterial(clean, note, instagram = false); onSaved() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Zapisz link na później") }
                }
            }
            ReelState.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Pobieram opis rolki…")
            }
            is ReelState.Waiting -> {
                NoticeCard(BadgeKind.INFO) {
                    Text("Rolka zapisana", fontWeight = FontWeight.Bold)
                    Text("Brak internetu. Opis pobierze się automatycznie, gdy telefon połączy się z siecią — szkic pojawi się w „Moich przepisach”.")
                }
                NoteField(note, { note = it }) { scope.launch { container.recipes.setMaterialNote(st.id, note); onSaved() } }
            }
            is ReelState.Failed -> {
                NoticeCard(BadgeKind.WARN) {
                    Text(st.reason, fontWeight = FontWeight.Bold)
                    Text("Rolka jest zapisana w „Moich przepisach”. Możesz spróbować ponownie albo dodać zrzut ekranu opisu.")
                }
                Button(onClick = { retry(st.id) }, modifier = Modifier.fillMaxWidth()) { Text("Spróbuj ponownie") }
                OutlinedButton(onClick = { onOpenOcrForReel(st.id) }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj zrzut ekranu opisu") }
                NoteField(note, { note = it }) { scope.launch { container.recipes.setMaterialNote(st.id, note); onSaved() } }
            }
        }
    }
}

private sealed interface ReelState {
    data object Idle : ReelState
    data object Working : ReelState
    data class Waiting(val id: String) : ReelState
    data class Failed(val id: String, val reason: String) : ReelState
}

/** Notatka „od kogo” do zapisanej rolki — ułatwia odnalezienie, zanim powstanie przepis. */
@Composable
private fun NoteField(note: String, onChange: (String) -> Unit, onSave: () -> Unit) {
    OutlinedTextField(
        value = note, onValueChange = onChange,
        label = { Text("Notatka (opcjonalnie)") }, placeholder = { Text("np. od Ani — makaron z dynią") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onSave) { Text("Zapisz i wróć do przepisów") }
}

// ------------------------------------------------------------------ Tekst

@Composable
fun TextImportScreen(container: AppContainer, initialText: String?, fromShare: Boolean, onOpenEditor: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    val scope = rememberCoroutineScope()
    ImportColumn {
        if (fromShare) NoticeCard(BadgeKind.INFO) { Text("Odebrano tekst przez „Udostępnij”.") }
        Text(
            "Wklej przepis. Rozpoznamy nazwę, porcje, składniki i kroki — oryginalny tekst zostanie przy przepisie.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it }, label = { Text("Tekst przepisu") },
            minLines = 10, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = text.isNotBlank(),
            onClick = {
                scope.launch {
                    val id = container.recipes.createDraftFromText(
                        RecipeTextParser.parse(text), text, SourceType.PASTED_TEXT, MaterialKind.TEXT,
                    )
                    onOpenEditor(id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Utwórz szkic") }
        Text("Rozpoznawanie działa na telefonie, także bez internetu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ Zdjęcie / zrzut + OCR

enum class OcrMode { PHOTO, SCREENSHOT }

private sealed interface OcrStep {
    data object Pick : OcrStep
    data object Working : OcrStep
    data class Review(val path: String) : OcrStep
    data class Failed(val path: String?, val message: String) : OcrStep
}

@Composable
fun OcrImportScreen(
    container: AppContainer,
    mode: OcrMode,
    targetId: String?,
    sharedImage: Uri?,
    onOpenEditor: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<OcrStep>(OcrStep.Pick) }
    var text by rememberSaveable { mutableStateOf("") }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var targetName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(targetId) { targetName = targetId?.let { container.recipes.getRecipe(it)?.name } }

    fun recognize(path: String) {
        step = OcrStep.Working
        scope.launch {
            step = try {
                // słownik poprawia typowe błędy polskich znaków (maka → mąka); użytkownik i tak sprawdza tekst
                text = OcrCorrector.correct(container.ocr.recognize(Uri.fromFile(File(path)))).trim()
                if (text.isBlank()) OcrStep.Failed(path, "Nie rozpoznano tekstu na zdjęciu.") else OcrStep.Review(path)
            } catch (e: Exception) {
                OcrStep.Failed(path, "Nie udało się rozpoznać tekstu.")
            }
        }
    }

    fun importUri(uri: Uri) {
        step = OcrStep.Working
        scope.launch {
            val path = container.photos.import(uri)
            if (path == null) step = OcrStep.Failed(null, "Nie udało się wczytać zdjęcia.") else recognize(path)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::importUri) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) recognize(f.absolutePath)
    }
    LaunchedEffect(sharedImage) { sharedImage?.let(::importUri) }

    fun createDraft(path: String?, recognized: String) {
        scope.launch {
            val id = container.recipes.createDraftFromText(
                parsed = RecipeTextParser.parse(recognized),
                originalText = recognized,
                sourceType = if (mode == OcrMode.SCREENSHOT) SourceType.INSTAGRAM_SCREENSHOT else SourceType.PHOTO,
                materialKind = if (mode == OcrMode.SCREENSHOT) MaterialKind.INSTAGRAM_SCREENSHOT else MaterialKind.PHOTO,
                materialUri = path,
                existingId = targetId,
            )
            onOpenEditor(id)
        }
    }

    ImportColumn {
        targetName?.let { NoticeCard(BadgeKind.INFO) { Text("Zrzut zostanie dołączony do: $it") } }
        when (val s = step) {
            OcrStep.Pick -> {
                Text(
                    if (mode == OcrMode.SCREENSHOT)
                        "Zrób w Instagramie zrzut ekranu z opisem rolki i wybierz go tutaj. Możesz też udostępnić zrzut prosto do FoodApp."
                    else "Zrób zdjęcie strony z przepisem albo wybierz je z galerii.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (mode == OcrMode.PHOTO) {
                    Button(onClick = {
                        val f = container.photos.newPhotoFile()
                        cameraFile = f
                        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Zrób zdjęcie") }
                }
                OutlinedButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (mode == OcrMode.SCREENSHOT) "Wybierz zrzut ekranu" else "Wybierz z galerii") }
                Text("Tekst rozpoznajemy na telefonie, także bez internetu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OcrStep.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Rozpoznawanie tekstu…")
            }
            is OcrStep.Review -> {
                RecipePhoto(s.path, container.photos, "Rozpoznawane zdjęcie", Modifier.fillMaxWidth())
                Text("Sprawdź rozpoznany tekst", fontWeight = FontWeight.Bold)
                Text(
                    "Popraw błędy rozpoznawania, jeśli są. Z tego tekstu powstanie szkic — oryginał zostanie przy przepisie.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 8, modifier = Modifier.fillMaxWidth())
                Button(onClick = { createDraft(s.path, text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Utwórz szkic") }
                TextButton(onClick = { step = OcrStep.Pick; text = "" }) { Text("Wybierz inne zdjęcie") }
            }
            is OcrStep.Failed -> {
                NoticeCard(BadgeKind.DANGER) {
                    Text(s.message, fontWeight = FontWeight.Bold)
                    Text(if (s.path != null) "Zdjęcie jest zachowane." else "Spróbuj wybrać inne zdjęcie.")
                }
                if (s.path != null) {
                    Button(onClick = { recognize(s.path) }, modifier = Modifier.fillMaxWidth()) { Text("Ponów rozpoznawanie") }
                    OutlinedButton(onClick = { createDraft(s.path, "") }, modifier = Modifier.fillMaxWidth()) { Text("Wprowadź ręcznie") }
                }
                TextButton(onClick = { step = OcrStep.Pick }) { Text("Wybierz inne zdjęcie") }
            }
        }
    }
}

// ------------------------------------------------------------------ Materiał (zapisana rolka / link)

@Composable
fun MaterialScreen(
    container: AppContainer,
    id: String,
    onOpenEditor: (String) -> Unit,
    onAddScreenshot: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recipe by remember { mutableStateOf<RecipeEntity?>(null) }
    LaunchedEffect(id) { recipe = container.recipes.getRecipe(id) }
    val r = recipe ?: return
    val reel = r.materialKind == MaterialKind.INSTAGRAM_LINK

    ImportColumn {
        Text(r.name, style = MaterialTheme.typography.headlineSmall)
        r.materialNote?.let { Text("Notatka: $it", style = MaterialTheme.typography.bodyLarge) }
        r.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        NoticeCard(BadgeKind.INFO) {
            Text(
                if (reel && r.processing == ProcessingState.WAITING_NETWORK)
                    "Rolka czeka na internet — opis pobierze się automatycznie po połączeniu."
                else if (reel)
                    "Rolka jest zapisana. Pobierz opis z Instagrama albo dodaj zrzut ekranu opisu."
                else "Link jest zapisany. Automatyczny import ze stron pojawi się w v0.3 — teraz możesz wpisać przepis ręcznie."
            )
        }
        if (reel) {
            var fetching by remember { mutableStateOf(false) }
            var fetchError by remember { mutableStateOf<String?>(null) }
            Button(
                enabled = !fetching,
                onClick = {
                    fetching = true
                    scope.launch {
                        when (val res = container.reels.process(id)) {
                            is ReelImporter.Result.Draft -> onOpenEditor(res.id)
                            is ReelImporter.Result.Waiting -> fetchError = "Brak internetu — opis pobierze się po połączeniu."
                            is ReelImporter.Result.Failed -> fetchError = res.reason
                        }
                        fetching = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (fetching) "Pobieram opis…" else "Pobierz opis z Instagrama") }
            fetchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { onAddScreenshot(id) }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj zrzut ekranu opisu") }
        }
        r.sourceUrl?.let { url ->
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (reel) "Otwórz w Instagramie" else "Otwórz stronę") }
        }
        OutlinedButton(onClick = {
            scope.launch { container.recipes.materialToEmptyDraft(id); onOpenEditor(id) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Wprowadź ręcznie") }
        TextButton(onClick = {
            scope.launch { container.recipes.softDelete(listOf(id)); onClose() }
        }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
    }
}

/** Czy materiał jest jeszcze materiałem (nie szkicem/przepisem). */
fun RecipeEntity.isMaterial(): Boolean = status == RecipeStatus.MATERIAL

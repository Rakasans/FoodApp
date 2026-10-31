package io.github.rakasans.foodapp.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rakasans.foodapp.ui.common.StepText

/**
 * Tryb gotowania: duże litery, ekran się nie wygasza, odhaczanie składników i kroków.
 * Odhaczenia żyją tylko w tej sesji gotowania (przepis się nie zmienia).
 */
@Composable
fun CookingScreen(viewModel: DetailViewModel) {
    val d by viewModel.details.collectAsStateWithLifecycle()
    val details = d ?: return

    // Ekran nie gaśnie, dopóki tryb gotowania jest otwarty
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val ingredients = details.ingredients.sortedBy { it.position }
    val steps = details.steps.sortedBy { it.position }.filter { it.text.isNotBlank() }
    var tab by rememberSaveable { mutableIntStateOf(if (ingredients.isEmpty()) 1 else 0) }
    var hadIngredients by rememberSaveable { mutableStateOf(listOf<String>()) }
    var doneSteps by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val current = steps.indices.firstOrNull { it !in doneSteps }

    Column(Modifier.fillMaxSize()) {
        Text(
            details.recipe.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = {
                Text("Składniki (${hadIngredients.size}/${ingredients.size})", fontSize = 17.sp)
            })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                Text("Kroki (${doneSteps.size}/${steps.size})", fontSize = 17.sp)
            })
        }

        if (tab == 0) {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                if (ingredients.isEmpty()) item { BigText("Brak składników w przepisie.") }
                itemsIndexed(ingredients, key = { _, i -> i.id }) { index, ing ->
                    val group = ing.groupName
                    if (group != null && group != ingredients.getOrNull(index - 1)?.groupName) {
                        Text(
                            group, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    val checked = ing.id in hadIngredients
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Checkbox) {
                            hadIngredients = if (checked) hadIngredients - ing.id else hadIngredients + ing.id
                        },
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            ing.rawText.ifBlank { ing.name },
                            fontSize = 22.sp, lineHeight = 30.sp,
                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }
            if (steps.isNotEmpty()) {
                BottomBar { Button(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Przejdź do kroków", fontSize = 18.sp) } }
            }
        } else {
            val listState = rememberLazyListState()
            // bieżący krok zawsze w zasięgu wzroku
            LaunchedEffect(current) { current?.let { listState.animateScrollToItem(it) } }
            LazyColumn(
                Modifier.weight(1f), state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (steps.isEmpty()) item { BigText("Brak kroków — przepis ich nie podaje.") }
                itemsIndexed(steps, key = { _, s -> s.id }) { index, step ->
                    val done = index in doneSteps
                    val isCurrent = index == current
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                            doneSteps = if (done) doneSteps - index else doneSteps + index
                        },
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (done) Icon(Icons.Default.Check, contentDescription = "Zrobione", tint = MaterialTheme.colorScheme.onPrimary)
                                        else Text("${index + 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            // nazwy składników w kroku są klikalne — pokazują ilość z listy składników
                            StepText(
                                text = step.text, ingredients = ingredients,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 34.sp),
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }
            }
            if (steps.isNotEmpty()) {
                BottomBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = doneSteps.isNotEmpty(),
                            onClick = { doneSteps = doneSteps.dropLast(1) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("Cofnij", fontSize = 18.sp) }
                        Button(
                            enabled = current != null,
                            onClick = { current?.let { doneSteps = doneSteps + it } },
                            modifier = Modifier.weight(2f).heightIn(min = 56.dp),
                        ) { Text(if (current == null) "Gotowe!" else "Zrobione — dalej", fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigText(text: String) {
    Text(text, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { content() }
    }
}

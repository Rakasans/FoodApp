package io.github.rakasans.foodapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.data.local.IngredientEntity
import io.github.rakasans.foodapp.domain.IngredientMentions
import io.github.rakasans.foodapp.domain.MentionIngredient

/**
 * Treść kroku z podkreślonymi nazwami składników. Kliknięcie nazwy pokazuje zapis z listy składników
 * (ilość łącznie na przepis — nie dzielimy jej między kroki, bo przepis tego nie podaje).
 */
@Composable
fun StepText(
    text: String,
    ingredients: List<IngredientEntity>,
    style: TextStyle,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<Pair<String, List<IngredientEntity>>?>(null) }
    val linkColor = MaterialTheme.colorScheme.primary
    val mentions = remember(text, ingredients) {
        IngredientMentions.find(text, ingredients.map { MentionIngredient(it.id, it.name, it.rawText) })
    }
    val annotated = remember(text, mentions, linkColor) {
        buildAnnotatedString {
            var pos = 0
            mentions.forEach { m ->
                append(text.substring(pos, m.start))
                val label = text.substring(m.start, m.end)
                val matched = ingredients.filter { it.id in m.ingredientIds }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "ing-${m.start}",
                        styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { shown = label to matched },
                    ),
                ) { append(label) }
                pos = m.end
            }
            append(text.substring(pos))
        }
    }
    Text(annotated, style = style, color = color, textDecoration = textDecoration, modifier = modifier)

    shown?.let { (label, list) ->
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(label.replaceFirstChar { it.uppercase() }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Na liście składników:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { ing ->
                        Text(
                            buildString {
                                append(ing.rawText.ifBlank { ing.name })
                                ing.groupName?.let { append("  ($it)") }
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    Text(
                        "To ilość na cały przepis. Jeśli składnik dodaje się w kilku krokach, przepis zwykle nie podaje, ile w którym.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { shown = null }) { Text("OK") } },
        )
    }
}

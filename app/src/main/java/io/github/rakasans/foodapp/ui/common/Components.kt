package io.github.rakasans.foodapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.ui.theme.LocalStatusColors

enum class BadgeKind { WARN, INFO, DANGER, NEUTRAL, OK }

data class BadgeUi(val text: String, val kind: BadgeKind)

@Composable
private fun colorsFor(kind: BadgeKind): Pair<Color, Color> {
    val s = LocalStatusColors.current
    return when (kind) {
        BadgeKind.WARN -> s.warnBg to s.warnFg
        BadgeKind.INFO -> s.infoBg to s.infoFg
        BadgeKind.DANGER -> s.dangerBg to s.dangerFg
        BadgeKind.NEUTRAL -> s.neutralBg to s.neutralFg
        BadgeKind.OK -> s.okBg to s.okFg
    }
}

@Composable
fun Badge(badge: BadgeUi, modifier: Modifier = Modifier) {
    val (bg, fg) = colorsFor(badge.kind)
    Text(
        text = badge.text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Karta komunikatu (ostrzeżenie / informacja / błąd) w kolorach statusu. */
@Composable
fun NoticeCard(kind: BadgeKind, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val (bg, fg) = colorsFor(kind)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Ekran zastępczy dla funkcji z kolejnych wersji. */
@Composable
fun PlannedScreen(title: String, version: String, description: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Badge(BadgeUi("Planowane: $version", BadgeKind.INFO))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

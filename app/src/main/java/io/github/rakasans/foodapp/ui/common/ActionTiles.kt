package io.github.rakasans.foodapp.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Akcja wybierana z siatki kafelków: ikona + krótka etykieta.
 * [help] nie jest widoczny na kafelku — pokazuje się po długim przytrzymaniu
 * (dymek) i w panelu „Jak to działa?” ([HelpSheet]).
 */
data class TileAction(
    val label: String,
    val icon: ImageVector,
    val help: String,
    val onClick: () -> Unit,
)

/** Kafelek akcji. Stuknięcie = akcja; przytrzymanie = dymek z opisem. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActionTile(action: TileAction, modifier: Modifier = Modifier, compact: Boolean = false) {
    val tooltip = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(16.dp)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(action.help) } },
        state = tooltip,
        modifier = modifier,
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    onClick = action.onClick,
                    onLongClick = { scope.launch { tooltip.show() } },
                    onLongClickLabel = "Pokaż opis",
                ),
        ) {
            if (compact) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(action.label, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                Column(
                    Modifier.heightIn(min = 104.dp).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text(action.label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Siatka kafelków w dwóch kolumnach. */
@Composable
fun ActionTileGrid(actions: List<TileAction>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ActionTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Mały przycisk „ⓘ Jak to działa?” — otwiera [HelpSheet]. */
@Composable
fun HelpButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label)
    }
}

/** Panel od dołu z opisami wszystkich akcji w jednym miejscu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(
    title: String,
    actions: List<TileAction>,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            actions.forEach { a ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(a.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.fillMaxWidth()) {
                        Text(a.label, fontWeight = FontWeight.Bold)
                        Text(a.help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            footer?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

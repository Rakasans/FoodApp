package io.github.rakasans.foodapp.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.BuildConfig
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.NoticeCard
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(container: AppContainer) {
    val user by container.auth.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(user.displayName, fontWeight = FontWeight.Bold)
                Text(
                    if (user.isDummy) "Konto testowe (atrapa)" else "Konto",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NoticeCard(BadgeKind.INFO) {
            Text("Logowanie jest atrapą.", fontWeight = FontWeight.Bold)
            Text("Wszystkie dane zapisują się tylko na tym urządzeniu. Prawdziwe logowanie i synchronizacja — późniejszy etap.")
        }
        NoticeCard(BadgeKind.WARN) {
            Text("Ryzyko", fontWeight = FontWeight.Bold)
            Text("Do czasu eksportu kopii zapasowej (v0.3) reset lub utrata telefonu oznacza utratę przepisów.")
        }
        Text("Język aplikacji: polski", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (BuildConfig.DEBUG) {
            Text("Narzędzia testowe (tylko wersja debug)", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { scope.launch { container.recipes.insertDemoData() } }) {
                Text("Wczytaj przepisy demonstracyjne")
            }
        }
    }
}

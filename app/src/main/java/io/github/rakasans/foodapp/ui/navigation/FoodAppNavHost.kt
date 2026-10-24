package io.github.rakasans.foodapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.ui.account.AccountScreen
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi
import io.github.rakasans.foodapp.ui.common.PlannedScreen
import io.github.rakasans.foodapp.ui.detail.DetailScreen
import io.github.rakasans.foodapp.ui.detail.DetailViewModel
import io.github.rakasans.foodapp.ui.library.LibraryScreen
import io.github.rakasans.foodapp.ui.library.LibraryViewModel

private object Routes {
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val ADD = "add"
    const val SHOPPING = "shopping"
    const val ACCOUNT = "account"
    const val DETAIL = "recipe/{id}"
    fun detail(id: String) = "recipe/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector, val title: String)

private val topLevel = listOf(
    TopLevel(Routes.LIBRARY, "Biblioteka", Icons.Default.Home, "Biblioteka"),
    TopLevel(Routes.COLLECTIONS, "Kolekcje", Icons.AutoMirrored.Filled.List, "Kolekcje"),
    TopLevel(Routes.ADD, "Dodaj", Icons.Default.Add, "Dodaj przepis"),
    TopLevel(Routes.SHOPPING, "Zakupy", Icons.Default.ShoppingCart, "Lista zakupów"),
    TopLevel(Routes.ACCOUNT, "Konto", Icons.Default.Person, "Konto"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodAppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val current = topLevel.firstOrNull { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(current?.title ?: "Przepis") },
                navigationIcon = {
                    if (current == null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    }
                },
                actions = { Badge(BadgeUi("Dane lokalnie", BadgeKind.NEUTRAL), Modifier.padding(end = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                topLevel.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.route,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.LIBRARY, modifier = Modifier.padding(padding)) {
            composable(Routes.LIBRARY) {
                val vm: LibraryViewModel = viewModel(factory = viewModelFactory {
                    initializer { LibraryViewModel(container.recipes, container.categories, container.tags) }
                })
                LibraryScreen(
                    viewModel = vm,
                    onOpenRecipe = { nav.navigate(Routes.detail(it)) },
                    onAddRecipe = { nav.navigate(Routes.ADD) },
                )
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: DetailViewModel = viewModel(key = "detail-$id", factory = viewModelFactory {
                    initializer { DetailViewModel(id, container.recipes) }
                })
                DetailScreen(vm)
            }
            composable(Routes.ADD) {
                PlannedScreen(
                    "Dodaj przepis", "v0.1 (krok 2) i v0.2",
                    "Najpierw ręczne wpisywanie z edytorem szkicu. Potem link, „Udostępnij”, rolki z Instagrama, tekst i zdjęcie z OCR.",
                )
            }
            composable(Routes.COLLECTIONS) {
                PlannedScreen("Kolekcje", "v0.3", "Zestawy przepisów na okazję, np. „Boże Narodzenie”. Niezależne od kategorii i etykiet.")
            }
            composable(Routes.SHOPPING) {
                PlannedScreen("Lista zakupów", "v0.3", "Składniki z wielu przepisów, przeliczanie porcji i bezpieczne łączenie pozycji. Reguły łączenia są już gotowe i przetestowane.")
            }
            composable(Routes.ACCOUNT) { AccountScreen(container) }
        }
    }
}

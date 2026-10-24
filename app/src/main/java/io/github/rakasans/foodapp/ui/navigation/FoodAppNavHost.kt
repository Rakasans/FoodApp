package io.github.rakasans.foodapp.ui.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.rakasans.foodapp.AppContainer
import io.github.rakasans.foodapp.ui.account.AccountScreen
import io.github.rakasans.foodapp.ui.add.AddScreen
import io.github.rakasans.foodapp.ui.common.Badge
import io.github.rakasans.foodapp.ui.common.BadgeKind
import io.github.rakasans.foodapp.ui.common.BadgeUi
import io.github.rakasans.foodapp.ui.common.PlannedScreen
import io.github.rakasans.foodapp.ui.detail.DetailScreen
import io.github.rakasans.foodapp.ui.detail.DetailViewModel
import io.github.rakasans.foodapp.ui.editor.EditorScreen
import io.github.rakasans.foodapp.ui.editor.EditorViewModel
import io.github.rakasans.foodapp.ui.library.LibraryScreen
import io.github.rakasans.foodapp.ui.library.LibraryViewModel
import io.github.rakasans.foodapp.ui.library.OpenTarget
import io.github.rakasans.foodapp.ui.importing.LinkImportScreen
import io.github.rakasans.foodapp.ui.importing.MaterialScreen
import io.github.rakasans.foodapp.ui.importing.OcrImportScreen
import io.github.rakasans.foodapp.ui.importing.OcrMode
import io.github.rakasans.foodapp.ui.importing.TextImportScreen
import io.github.rakasans.foodapp.domain.isLinkShare
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

private object Routes {
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val ADD = "add"
    const val SHOPPING = "shopping"
    const val ACCOUNT = "account"
    const val DETAIL = "recipe/{id}"
    const val EDITOR_NEW = "editor/new"
    const val EDITOR = "editor/{id}"
    const val IMPORT_LINK = "import/link?share={share}"
    const val IMPORT_TEXT = "import/text?share={share}"
    const val IMPORT_OCR = "import/ocr/{mode}?target={target}&share={share}"
    const val MATERIAL = "material/{id}"
    fun detail(id: String) = "recipe/$id"
    fun editor(id: String) = "editor/$id"
    /** `share` = znacznik konkretnego udostępnienia (pusty = wejście ręczne). Każde udostępnienie to nowy ekran. */
    fun importLink(share: String = "") = "import/link?share=$share"
    fun importText(share: String = "") = "import/text?share=$share"
    fun importOcr(mode: OcrMode, target: String? = null, share: String = "") =
        "import/ocr/${mode.name}?target=${target.orEmpty()}&share=$share"
    fun material(id: String) = "material/$id"
}

private val shareArg = navArgument("share") { type = NavType.StringType; defaultValue = "" }

/** Szkic z importu: edytor zamiast ekranu importu (wstecz wraca do biblioteki). */
private fun NavHostController.openEditorReplacing(id: String) {
    navigate(Routes.editor(id)) {
        popUpTo(Routes.LIBRARY)
        launchSingleTop = true
    }
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector, val title: String)

private val topLevel = listOf(
    TopLevel(Routes.LIBRARY, "Przepisy", Icons.Default.Home, "Moje przepisy"),
    TopLevel(Routes.COLLECTIONS, "Kolekcje", Icons.AutoMirrored.Filled.List, "Kolekcje"),
    TopLevel(Routes.ADD, "Dodaj", Icons.Default.Add, "Dodaj przepis"),
    TopLevel(Routes.SHOPPING, "Zakupy", Icons.Default.ShoppingCart, "Lista zakupów"),
    TopLevel(Routes.ACCOUNT, "Konto", Icons.Default.Person, "Konto"),
)

/** Po zatwierdzeniu: szczegóły przepisu, a pod spodem biblioteka (bez edytora na stosie). */
private fun NavHostController.openDetailReplacing(id: String) {
    navigate(Routes.detail(id)) {
        popUpTo(Routes.LIBRARY)
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodAppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val current = topLevel.firstOrNull { it.route == route }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val title = current?.title ?: when (route) {
        Routes.EDITOR_NEW -> "Nowy przepis"
        Routes.EDITOR -> "Edycja przepisu"
        Routes.IMPORT_LINK -> "Link do przepisu"
        Routes.IMPORT_TEXT -> "Wklej tekst"
        Routes.IMPORT_OCR -> "Rozpoznaj ze zdjęcia"
        Routes.MATERIAL -> "Zapisany materiał"
        else -> "Przepis"
    }

    // „Udostępnij” z innych aplikacji: kierujemy do właściwego importu; ekran odbiera treść ze skrzynki.
    val shared by container.shareInbox.pending.collectAsState()
    LaunchedEffect(shared) {
        val s = shared ?: return@LaunchedEffect
        val target = when {
            s.imageUri != null -> Routes.importOcr(OcrMode.PHOTO, share = s.token)
            s.text != null && isLinkShare(s.text) -> Routes.importLink(share = s.token)
            else -> Routes.importText(share = s.token)
        }
        nav.navigate(target) { popUpTo(Routes.LIBRARY) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (current == null) {
                        // Przez dispatcher, żeby ekrany mogły przechwycić „Wstecz” (np. niezapisane zmiany).
                        IconButton(onClick = { backDispatcher?.onBackPressed() ?: nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    }
                },
                actions = { Badge(BadgeUi("Dane lokalnie", BadgeKind.NEUTRAL), Modifier.padding(end = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            // Dolny pasek tylko na ekranach głównych — w edytorze i szczegółach daje miejsce treści i klawiaturze.
            if (current != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
        NavHost(
            nav,
            startDestination = Routes.LIBRARY,
            // consumeWindowInsets: ekrany z imePadding() nie dodadzą drugi raz paska systemowego.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.LIBRARY) {
                val vm: LibraryViewModel = viewModel(factory = viewModelFactory {
                    initializer { LibraryViewModel(container.recipes, container.categories, container.tags) }
                })
                LibraryScreen(
                    viewModel = vm,
                    onOpenRecipe = { id, target ->
                        nav.navigate(
                            when (target) {
                                OpenTarget.EDITOR -> Routes.editor(id)
                                OpenTarget.MATERIAL -> Routes.material(id)
                                OpenTarget.DETAIL -> Routes.detail(id)
                            }
                        )
                    },
                    onAddRecipe = { nav.navigate(Routes.ADD) },
                )
            }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: DetailViewModel = viewModel(key = "detail-$id", factory = viewModelFactory {
                    initializer { DetailViewModel(id, container.recipes) }
                })
                DetailScreen(vm, container.photos, onEdit = { nav.navigate(Routes.editor(id)) })
            }
            composable(Routes.EDITOR_NEW) { EditorRoute(null, container, nav) }
            composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                EditorRoute(entry.arguments?.getString("id"), container, nav)
            }
            composable(Routes.ADD) {
                AddScreen(
                    onManual = { nav.navigate(Routes.EDITOR_NEW) },
                    onLink = { nav.navigate(Routes.importLink()) },
                    onScreenshot = { nav.navigate(Routes.importOcr(OcrMode.SCREENSHOT)) },
                    onText = { nav.navigate(Routes.importText()) },
                    onPhoto = { nav.navigate(Routes.importOcr(OcrMode.PHOTO)) },
                )
            }
            composable(Routes.IMPORT_LINK, arguments = listOf(shareArg)) { entry ->
                val shareToken = entry.arguments?.getString("share").orEmpty()
                val fromShare = shareToken.isNotEmpty()
                val sharedText = remember(shareToken) { if (fromShare) container.shareInbox.take()?.text else null }
                LinkImportScreen(
                    container = container,
                    initialText = sharedText,
                    fromShare = fromShare,
                    onOpenEditor = { nav.openEditorReplacing(it) },
                    onOpenOcrForReel = { id ->
                        nav.navigate(Routes.importOcr(OcrMode.SCREENSHOT, target = id)) { popUpTo(Routes.LIBRARY) }
                    },
                    onSaved = { nav.navigate(Routes.LIBRARY) { popUpTo(Routes.LIBRARY) { inclusive = true } } },
                )
            }
            composable(Routes.IMPORT_TEXT, arguments = listOf(shareArg)) { entry ->
                val shareToken = entry.arguments?.getString("share").orEmpty()
                val fromShare = shareToken.isNotEmpty()
                val sharedText = remember(shareToken) { if (fromShare) container.shareInbox.take()?.text else null }
                TextImportScreen(container, sharedText, fromShare, onOpenEditor = { nav.openEditorReplacing(it) })
            }
            composable(
                Routes.IMPORT_OCR,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("target") { type = NavType.StringType; defaultValue = "" },
                    shareArg,
                ),
            ) { entry ->
                val mode = OcrMode.valueOf(entry.arguments?.getString("mode") ?: OcrMode.PHOTO.name)
                val target = entry.arguments?.getString("target")?.ifBlank { null }
                val shareToken = entry.arguments?.getString("share").orEmpty()
                val fromShare = shareToken.isNotEmpty()
                val sharedImage = remember(shareToken) { if (fromShare) container.shareInbox.take()?.imageUri else null }
                OcrImportScreen(container, mode, target, sharedImage, onOpenEditor = { nav.openEditorReplacing(it) })
            }
            composable(Routes.MATERIAL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                MaterialScreen(
                    container = container,
                    id = id,
                    onOpenEditor = { nav.openEditorReplacing(it) },
                    onAddScreenshot = { nav.navigate(Routes.importOcr(OcrMode.SCREENSHOT, target = it)) },
                    onClose = { nav.popBackStack() },
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

@Composable
private fun EditorRoute(recipeId: String?, container: AppContainer, nav: NavHostController) {
    val vm: EditorViewModel = viewModel(key = "editor-${recipeId ?: "new"}", factory = viewModelFactory {
        initializer { EditorViewModel(recipeId, container.recipes, container.categories, container.tags, container.photos) }
    })
    EditorScreen(
        viewModel = vm,
        onApproved = { id -> nav.openDetailReplacing(id) },
        onOpenRecipe = { id -> nav.openDetailReplacing(id) },
        onClose = { nav.popBackStack() },
    )
}

package io.github.rakasans.foodapp.data.local

/** Oś „treść”: materiał źródłowy → szkic → zatwierdzony przepis. */
enum class RecipeStatus { MATERIAL, DRAFT, APPROVED }

/** Oś „przetwarzanie” — niezależna od statusu treści. */
enum class ProcessingState { NONE, WAITING_NETWORK, RUNNING, ERROR, NEEDS_SCREENSHOT }

/** Oś „synchronizacja” — w v0.1 zawsze LOCAL_ONLY (decyzja 6). */
enum class SyncState { LOCAL_ONLY, PENDING, SYNCED }

enum class SourceType { MANUAL, PASTED_TEXT, DICTATION, PHOTO, WEB, INSTAGRAM_REEL, INSTAGRAM_SCREENSHOT }

enum class MaterialKind { NONE, LINK, TEXT, PHOTO, DICTATION, INSTAGRAM_LINK, INSTAGRAM_SCREENSHOT }

/** Pochodzenie informacji dodatkowych. W v0.1 tylko dane ze źródła (decyzja 11). */
enum class InfoOrigin { NONE, SOURCE }

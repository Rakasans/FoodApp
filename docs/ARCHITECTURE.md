# FoodApp — architektura (v0.1)

Status: propozycja do akceptacji · 24.10.2026
Powiązane: rejestr decyzji i specyfikacja prototypu w projekcie FoodApp (claude.ai).

## 1. Założenia, z których wynika architektura

| Decyzja | Konsekwencja techniczna |
|---|---|
| Tylko Android, Kotlin + Compose | Jedna aplikacja natywna, Material 3 |
| Dane tylko na urządzeniu, atrapa logowania | Room jako jedyne źródło prawdy, brak sieci w v0.1 |
| Docelowo Supabase + synchronizacja | Schemat „gotowy do synchronizacji” od pierwszego dnia |
| OCR i mowa na urządzeniu | ML Kit / systemowy SpeechRecognizer (v0.2 / v0.4) |
| Import stron: najpierw schema.org | Parser JSON-LD (v0.3), AI później |
| Wiele kategorii, własne kategorie | Kategorie jako encje + tabela łącząca |
| Tryb ciemny natywny, akcent: rozjaśniona zieleń | Dwa zestawy tokenów kolorów, bursztyn zarezerwowany dla ostrzeżeń |

## 2. Struktura projektu

Na start **jeden moduł `app`** z podziałem na pakiety. Podział na moduły Gradle (`core`, `data`, `feature-*`) dopiero, gdy czas budowania lub liczba ekranów zacznie przeszkadzać — przy jednym deweloperze wcześniejszy podział to głównie narzut.

```
io.github.rakasans.foodapp
├── FoodApp.kt                 Application + AppContainer (ręczne DI)
├── MainActivity.kt
├── core/                      Id (UUID), Clock, wspólne typy
├── auth/                      AuthProvider (interfejs), DummyAuthProvider
├── data/
│   ├── local/                 FoodDatabase, encje, DAO, konwertery
│   └── repository/            RecipeRepository, CategoryRepository …
├── domain/                    modele domenowe + reguły (np. scalanie listy zakupów)
└── ui/
    ├── theme/                 tokeny kolorów jasny/ciemny, typografia
    ├── navigation/            trasy, dolna nawigacja
    ├── library/  editor/  detail/  collections/  shopping/  account/
```

**DI:** ręczny `AppContainer` zamiast Hilt. Mniej konfiguracji i magii przy małym projekcie; przejście na Hilt jest proste, gdy liczba zależności urośnie.

**Wzorzec UI:** ekran = `@Composable` + `ViewModel` wystawiający `StateFlow<UiState>`. Room zwraca `Flow`, więc lista odświeża się sama po zapisie.

## 3. Model danych (Room)

Zasady wspólne dla wszystkich tabel synchronizowanych w przyszłości:
- `id: String` — UUID generowany na urządzeniu (bez kolizji przy synchronizacji).
- `ownerId: String` — do czasu kont: lokalny identyfikator instalacji.
- `createdAt`, `updatedAt: Long` (epoch ms), `deletedAt: Long?` — miękkie usuwanie.
- `syncState` — w v0.1 zawsze `LOCAL_ONLY`; później `PENDING` / `SYNCED`.

### Tabele

| Tabela | Najważniejsze pola | Uwagi |
|---|---|---|
| `recipe` | status (MATERIAL/DRAFT/APPROVED), processing (NONE/WAITING_NETWORK/RUNNING/ERROR/NEEDS_SCREENSHOT), name, servings?, yieldText, prepTime, cookTime, notes, photoUri, source (type/url/author), material (kind/text/uri/note), nutritionOrigin, nutritionBasis, allergensOrigin, allergensText | Trzy osie stanu ze specyfikacji; materiał źródłowy jest częścią rekordu |
| `ingredient` | recipeId, position, groupName, rawText, name, quantity?, unit, noQuantity, unitInferred | `rawText` = oryginalny zapis; `quantity == null && !noQuantity` = brak danych |
| `step` | recipeId, position, text | |
| `nutrition_value` | recipeId, label, valueText | Tylko dane ze źródła (decyzja 11) |
| `category` | name, isDefault | Domyślne: śniadanie, obiad, kolacja, deser, przekąska |
| `recipe_category` | recipeId, categoryId | Wiele kategorii na przepis |
| `tag` / `recipe_tag` | name | Etykiety użytkownika |
| `collection` / `recipe_collection` | name | Kolekcje (wspólne — później) |
| `shopping_part` | name, quantity?, unit, noQuantity, sourceRecipeId?, sourceRecipeName, rawText, factorLabel, manual | Jedna „część” = wkład jednego przepisu; pozycje listy liczone w domenie |
| `shopping_item_state` | normalizedName (PK), checked, quantityOverride | Odznaczenie i ręczna ilość per pozycja |

Uprawnienia, wspólne kolekcje i kopie przepisów dojdą z backendem — schemat ich nie blokuje (właściciel + UUID).

## 4. Reguły domenowe (testy jednostkowe od początku)

- **Scalanie listy zakupów:** grupowanie po znormalizowanej nazwie; sumowanie tylko tej samej jednostki; dokładne konwersje g↔kg, ml↔l; różne jednostki → osobne segmenty; brak ilości → „ilość nieznana”.
- **Przeliczanie porcji:** współczynnik tylko przy znanej liczbie porcji; inaczej mnożnik całego przepisu.
- **Kompletność przepisu:** brak nazwy, brak kroków, brak ilości w składniku.

Te reguły są czystym Kotlinem w `domain/` — łatwe do testowania bez Androida.

## 5. Motyw

- Tokeny kolorów w `ui/theme/Color.kt`, dwa zestawy: jasny i ciemny; przełączanie wg ustawienia systemu.
- Akcent: zieleń `#2F6B4F` (jasny) / rozjaśniona zieleń `#8FD0AE` (ciemny).
- Bursztyn tylko dla ostrzeżeń (niekompletny, brak ilości), niebieski dla informacji, czerwony dla błędów.
- Czcionka Figtree (licencja OFL) dołączona jako zasób `res/font`, by działała offline. W szkielecie jeszcze jej nie ma — środowisko Claude nie pobiera plików czcionek; do czasu dodania używana jest czcionka systemowa (miejsce podmiany: `ui/theme/Type.kt`).
- Kolory dynamiczne (Material You) wyłączone — spójny wygląd marki.

## 6. Wersje i narzędzia

- minSdk 26, compileSdk/targetSdk 35 (aktualizacja do 36 przez Android Studio — urządzenie testowe ma Android 16).
- Gradle Kotlin DSL + katalog wersji `gradle/libs.versions.toml`.
- Room z KSP, Navigation Compose, Lifecycle ViewModel Compose.
- Wersje bibliotek są ostrożnie dobrane, ale **nie zostały zbudowane w środowisku Claude** (brak dostępu do repozytoriów Maven). Przy pierwszym otwarciu Android Studio może zaproponować aktualizacje — można je przyjąć.

## 7. Plan v0.1

1. ✅ Szkielet: motyw, nawigacja, baza, atrapa konta, biblioteka (lista + wyszukiwanie + filtry).
2. ✅ Usuwanie (długie przytrzymanie, wiele naraz, „Cofnij”), kontrola duplikatów po nazwie.
3. ✅ Edytor przepisu: ręczne dodawanie, autozapis szkicu, parser składników („2 łyżki oliwy”, wklejanie listy),
   „bez ilości”, ręczna korekta, wiele kategorii (także nowe), etykiety, zdjęcie z galerii (Photo Picker),
   zatwierdzanie z kontrolą kompletności i duplikatów, edycja zatwierdzonych przepisów.
4. ✅ Szczegóły przepisu (oryginalny zapis składników, źródło, informacje dodatkowe, zdjęcie).
5. ✅ Testy reguł domenowych (parser, lista zakupów, kompletność, duplikaty).

## 8. v0.2 — import przepisów

- Parser tekstu przepisu (`domain/RecipeTextParser`): nagłówki, grupy składników, numeracja kroków, porcje,
  emoji i hashtagi z opisów rolek. Linie nieprzypisane są pomijane, oryginał zostaje w `materialText`.
- Rolki z Instagrama: link + notatka „od kogo” → materiał w bibliotece (szukany po notatce); zrzut opisu → OCR → szkic
  przypięty do tej samej rolki.
- Linki do stron: zapis na później albo ręczne wpisanie z linkiem jako źródłem (automatyczny import — v0.3).
- OCR: ML Kit z modelem w aplikacji (offline), zdjęcie z aparatu (FileProvider, bez uprawnienia CAMERA) lub galerii;
  rozpoznany tekst do poprawienia przed utworzeniem szkicu.
- „Udostępnij → Zapisz w FoodApp”: tekst/link → import linku lub tekstu, obraz → OCR.

## 9. v0.2.1 — import rolki z linku, słownik OCR

- „Udostępnij” rolki → telefon pobiera publiczną stronę rolki (strona „embed”, potem zwykła), odczytuje opis,
  miniaturę i autora (`domain/InstagramPage`), tworzy szkic ze zdjęciem. Bez internetu rolka czeka
  (`WAITING_NETWORK`) i pobiera się po odzyskaniu sieci (`ReelImporter.retryWaiting`). Przy niepowodzeniu:
  ponów, zrzut ekranu albo notatka i zapis na później. Nieoficjalny odczyt — może przestać działać.
- OCR: słownik kulinarny poprawia brakujące ogonki tylko tam, gdzie poprawka jest jednoznaczna (`domain/OcrCorrector`).
  Docelowo wariant C: przycisk „Rozpoznaj dokładniej (online)” z modelem AI po stronie backendu.

Znane uproszczenia v0.1: zdjęcie tylko z galerii (aparat — v0.2), grupy składników zachowywane, ale nieedytowalne,
stare pliki zdjęć nie są jeszcze sprzątane po podmianie.

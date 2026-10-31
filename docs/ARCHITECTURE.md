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

## v0.2.2 — zakresy, tekst z notatnika, link do oryginału
- Baza w wersji 2: `ingredient.quantityMax` (migracja 1→2, `ALTER TABLE`, dane zostają). Zakres „300–350 g” = `quantity` 300 + `quantityMax` 350; na liście zakupów zakres jest osobnym segmentem, nigdy nie sumowany.
- `IngredientParser`: ilość na końcu linii („Sezam - 10g”, „Liść laurowy 3”, „Cynamon 1 pałka”); liczba po „typ/nr/rozmiar” nie jest ilością. Nowe jednostki: pałka, laska, ziarenko, gwiazdka.
- `RecipeTextParser` bez nagłówków: krótka linia bez ilości w środku listy składników („Mała dymka”) to składnik z ilością „brak danych”.
- Zdjęcia w proporcjach źródła (przycięte do 2:3…2:1); miniatura rolki bez nakładki „play”.
- Przycisk „Otwórz w Instagramie” / „Otwórz stronę” przy źródle. „Pokaż oryginał” — w trybie przeglądania; w edytorze tylko przy sprawdzaniu szkicu z importu.

## v0.3 — import stron, lista zakupów, kolekcje, kopia zapasowa

- **Import ze stron** (`domain/WebRecipePage`, `domain/MiniJson`, `data/web/WebImporter`): telefon pobiera stronę i czyta
  bloki JSON-LD schema.org/Recipe (także w `@graph`): nazwa, składniki, kroki (HowToStep/HowToSection/HTML), porcje albo
  wydajność, czasy ISO 8601, zdjęcie, autor, wartości odżywcze (podstawa „1 porcja” — tak definiuje je schema.org).
  Oryginał danych strony trafia do `materialText`. Brak danych strukturalnych → link zostaje (`ERROR`), można wpisać ręcznie.
  Offline → `WAITING_NETWORK`, pobranie po odzyskaniu sieci. „Udostępnij”: tekst z linkiem i ≤200 znakami poza nim = link,
  dłuższy = tekst przepisu (`isLinkShare`).
- **Lista zakupów** (`domain/ShoppingBuilder` + `ShoppingMerge`, `ShoppingRepository`, `ui/shopping`): ekran „Porcje”
  (z przepisu albo z listy; wyszukiwanie przepisów, wiele naraz, podgląd scalania). Porcje znane → docelowa liczba porcji;
  nieznane → mnożnik ×½/×1/×2/×3. Zakresy przeliczane na obu końcach i nigdy nie sumowane. Pozycje: odhaczanie,
  rozwinięcie źródeł, ilość wpisana ręcznie (np. gdy nieznana), ręczne pozycje, „Usuń kupione”, „Wyczyść listę”.
- **Kolekcje** (`CollectionRepository`, `ui/collections`): lista, tworzenie, zmiana nazwy, usuwanie (przepisy zostają),
  dodawanie przepisów z wyszukiwaniem; w szczegółach przepisu okno „Kolekcje” z wyszukiwaniem i tworzeniem nowej.
- **Kopia zapasowa** (`domain/Backup`, `data/backup/BackupManager`): ZIP z `recipes.json` (format jawny, z wersją)
  i `photos/`. Przywracanie dokłada brakujące przepisy po identyfikatorze — niczego nie nadpisuje.
- Baza w wersji 3: `shopping_part.quantityMax` (migracja 2→3).

## v0.3.1 — import z treści strony, przyciski źródła

- Plan B importu ze stron bez schema.org (`domain/HtmlRecipeText`): widoczny tekst strony (bez menu, stopki, skryptów),
  start od nagłówka „Składniki” albo od najdłuższego ciągu linii z ilościami, koniec przy komentarzach / polecanych / radach.
  Fragment trafia do `RecipeTextParser`, a jego treść — do oryginału przepisu („Tekst ze strony…”).
- `IngredientParser`: „karczek wołowy 1 kg”, „cebule 300 g - 2 sztuki”, „1 ¼ szklanki”, „1 płaska łyżeczka”; jednostka gałązka.
- Pobieranie strony: przeglądarka mobilna, a przy odmowie desktopowa.
- Sekcja „Źródło”: „Otwórz rolkę / Otwórz stronę” i „Pokaż oryginał” w jednym rzędzie, ten sam styl (`ui/common/SourceActions`).

## v0.3.2 — mikrodane i lepszy odczyt treści stron

- Kolejność odczytu strony: JSON-LD schema.org/Recipe → **mikrodane** (`itemprop`, np. AniaGotuje) → treść strony.
  Mikrodane czyta `domain/MiniHtml` (prosty parser drzewa HTML, czysty Kotlin): grupy składników z nagłówków sekcji,
  nazwa i ilość z osobnych pól, kroki HowToStep bez porad autora (porady trafiają do oryginału, nie do kroków),
  podstawa wartości odżywczych ze strony („100 g zupy”).
- Treść strony (np. Rozkoszny): „4 porcje” to liczba porcji, „2 – 3 porcje” nie jest zgadywane; krótka linia na początku
  akapitu listy składników to grupa („Kopytka”, „Do podania”); koniec przepisu przy „Rady/porady”, „Nie zapomnij…”, „Wasz,”.
- Testy na wycinkach prawdziwych stron: `app/src/test/resources/web`.

## v0.4 — dyktowanie, duplikaty po adresie, porządki

- **Dyktowanie** (`ui/importing/DictationScreen`, `domain/DictationText`): systemowe rozpoznawanie mowy
  (`RecognizerIntent`, pl-PL, preferencja offline), fragmenty dopisywane jako nowe linie, „nowa linia” dzieli tekst,
  liczebnik na początku linii → cyfra. Transkrypcję można poprawić; dalej jak wklejony tekst. Bez uprawnienia RECORD_AUDIO
  (nagrywa systemowe okno); `<queries>` w manifeście dla Androida 11+.
- **Duplikaty po adresie** (`DuplicateRules.sourceKey/findSameSource`): klucz bez protokołu, www/m., parametrów śledzących,
  kotwicy i „/”; Instagram po kodzie rolki. Import linku (także z „Udostępnij”) pyta: „Otwórz istniejący” / „Importuj mimo to”.
- **Grupy składników w edytorze**: nagłówki grup na liście, pole „Grupa” i szybki wybór istniejących w „Popraw”;
  nowy składnik dziedziczy grupę poprzedniego.
- **Sprzątanie zdjęć**: przy starcie usuwane pliki zdjęć bez przepisu (starsze niż doba); zdjęcia usuniętych przepisów zostają.
- **Lista zakupów**: grupy opcjonalne („Dodatki opcjonalne”, „Do podania”, „Do dekoracji”) domyślnie pominięte, do włączenia na ekranie porcji.

## v0.5 — ulubione, miniatury, Przepisarnia, strona WWW

- **Ulubione**: `recipe.favorite` (baza v4, migracja 3→4 z `DEFAULT 0`), serduszko w szczegółach (bez zmiany `updatedAt`,
  żeby lista nie przeskakiwała), filtr „Ulubione” w „Moich przepisach”, pole w kopii zapasowej.
- **Lista przepisów**: kwadratowa miniatura zdjęcia przepisu (podgląd 256 px), bez zdjęcia — pierwsza litera nazwy.
- **Rolki**: emoji-cyfry (1️⃣) to numeracja kroków; nagłówek „Baza:”/„Tofu:” przed numerowanymi krokami to część
  przygotowania (dopisywana do pierwszego kroku), nie grupa składników. Opis ucięty przez Instagram (koniec w pół znaku)
  kończy się „…”, a edytor pokazuje ostrzeżenie.
- **Nazwa widoczna**: Przepisarnia (identyfikator pakietu bez zmian).
- **Strona** `site/` → GitHub Pages (`.github/workflows/pages.yml`), domena przepisarnia.pl; konfiguracja w `site/README.md`.

## v0.6 — serduszko na liście, ekran powitalny, tryb gotowania

- Karta przepisu na liście: serduszko (puste/wypełnione) dodaje i usuwa z ulubionych jednym kliknięciem.
- Ekran powitalny (`ui/welcome`) z treścią landingu przepisarnia.pl: przy pierwszym uruchomieniu i w „Konto → O aplikacji”.
  Docelowo miejsce na logowanie.
- Tryb gotowania (`ui/detail/CookingScreen`): przycisk „Gotuj” w przepisie; duży tekst, ekran nie gaśnie (`keepScreenOn`),
  odhaczanie składników i kroków, bieżący krok wyróżniony i przewijany do widoku, „Zrobione — dalej” / „Cofnij”.
  Odhaczenia nie zmieniają przepisu.

## Supabase — schemat synchronizacji (31.10)

- `supabase/migrations/20261031190000_init_sync_schema.sql`: lustro tabel Room (snake_case), id = UUID z telefonu,
  czasy z telefonu w ms, `deleted_at` (miękkie usuwanie), `server_updated_at` (ustawiany przez bazę — pobieranie zmian
  „od ostatniej synchronizacji” niezależnie od zegara telefonu).
- RLS na każdej tabeli: tylko `owner_id = auth.uid()`; wiersze podrzędne nie mogą wskazywać cudzego przepisu (trigger).
- Storage: prywatny bucket `photos`, ścieżka `<user_id>/<plik>`.
- Wdrażanie: integracja Supabase ↔ GitHub (migracje z `supabase/migrations` na gałęzi `main`).
- Migracja przetestowana lokalnie na PostgreSQL 16 z atrapami schematów `auth`/`storage` (RLS: użytkownik B nie widzi
  i nie zmienia danych A).

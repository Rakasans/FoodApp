# FoodApp

Prywatna biblioteka przepisów na Androida: zbieranie (strony, Instagram, tekst, zdjęcie, dyktowanie), porządkowanie (kategorie, etykiety, kolekcje) i lista zakupów z przeliczaniem porcji. Dane zapisywane lokalnie na urządzeniu.

Status: **v0.1 — szkielet** (biblioteka, szczegóły przepisu, konto-atrapa, baza danych, reguły listy zakupów z testami).

## Uruchomienie

1. Otwórz katalog projektu w Android Studio (wersja z obsługą AGP 8.7 lub nowsza).
2. Poczekaj na synchronizację Gradle. Jeśli Android Studio zaproponuje aktualizację wersji bibliotek/AGP — można przyjąć.
3. Uruchom konfigurację `app` na telefonie (tryb debugowania USB) lub emulatorze.
4. W zakładce **Konto** (wersja debug) kliknij „Wczytaj przepisy demonstracyjne”, żeby zobaczyć przykładowe dane.

Testy reguł domenowych: `./gradlew :app:testDebugUnitTest`

## Dokumentacja

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — architektura, model danych, motyw, plan v0.1.
- Specyfikacja prototypu i rejestr decyzji — w projekcie FoodApp na claude.ai.

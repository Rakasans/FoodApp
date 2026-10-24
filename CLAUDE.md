# FoodApp — zasady pracy w repozytorium

- Język interfejsu i komunikatów: polski. Kod, nazwy w kodzie: angielski; komentarze mogą być po polsku.
- Architektura: docs/ARCHITECTURE.md. Jeden moduł `app`, ręczne DI w `AppContainer`, Room jako jedyne źródło prawdy.
- Każda tabela synchronizowalna: UUID `id`, `ownerId`, `createdAt`, `updatedAt`, `deletedAt` (miękkie usuwanie), `syncState`.
- Zasady wiarygodności danych (nie łamać):
  - nie wymyślać brakujących składników, ilości ani kroków; brak = `null`, pokazywany jako „brak danych”,
  - nieznana wartość odżywcza nigdy jako 0; zawsze z podstawą (porcja / cały przepis / 100 g),
  - brak informacji o alergenach ≠ brak alergenów,
  - lista zakupów: łączyć tylko identyczne nazwy i zgodne jednostki; bez przeliczania sztuk na gramy; przeliczenie porcji nie zmienia przepisu.
- Reguły domenowe w `domain/` są czystym Kotlinem i mają testy jednostkowe — każda zmiana reguł = test.
- Kolory tylko z tokenów `ui/theme/Theme.kt`; bursztyn zarezerwowany dla ostrzeżeń.

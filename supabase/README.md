# Supabase — Przepisarnia

Projekt: `tawrhsnhfgzofvuljwnh` (region UE). Migracje w `migrations/` wdraża integracja Supabase ↔ GitHub
przy każdej zmianie na gałęzi `main`. Nowa zmiana bazy = nowy plik `migrations/<YYYYMMDDHHMMSS>_<opis>.sql`
(starych plików nie zmieniamy po wdrożeniu).

Klucz w aplikacji: tylko **publishable** (`sb_publishable_…`). Klucze `sb_secret_…` / `service_role`, JWT Secret
i hasło do bazy nie trafiają do repozytorium ani aplikacji.

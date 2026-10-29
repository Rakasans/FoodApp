# przepisarnia.pl — strona

Statyczna strona (landing + demo biblioteki w przeglądarce), bez budowania. Publikuje ją
`.github/workflows/pages.yml` na GitHub Pages przy każdej zmianie w `site/`.

## Jednorazowa konfiguracja

1. GitHub → repozytorium FoodApp → **Settings → Pages** → *Build and deployment* → Source: **GitHub Actions**.
2. Tamże **Custom domain**: `przepisarnia.pl` → Save. Po weryfikacji DNS zaznacz **Enforce HTTPS**.
3. U rejestratora domeny (DNS dla `przepisarnia.pl`):
   - rekordy **A** dla `@`: `185.199.108.153`, `185.199.109.153`, `185.199.110.153`, `185.199.111.153`
   - opcjonalnie **AAAA** dla `@`: `2606:50c0:8000::153`, `2606:50c0:8001::153`, `2606:50c0:8002::153`, `2606:50c0:8003::153`
   - **CNAME** dla `www` → `rakasans.github.io`
   - usuń inne rekordy A/AAAA/CNAME dla `@` i `www` (np. „parking” rejestratora).
4. Zalecane: GitHub → Settings (konta) → Pages → **Add a domain** → weryfikacja `przepisarnia.pl` rekordem TXT
   (chroni domenę przed przejęciem przez cudze repozytorium).

Zmiany DNS rozchodzą się zwykle od kilku minut do kilku godzin; certyfikat HTTPS GitHub wystawia sam.

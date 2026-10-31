# Ikona aplikacji — jak ją zmienić

Źródło: `docs/brand/icon-source.png` (kwadratowy obraz, motyw na jednolitym tle).

## Wymiana ikony (ok. 10–15 minut)

1. Podmień `docs/brand/icon-source.png` na nowy obraz (najlepiej ≥ 1024 px, motyw na jednolitym tle, bez tekstu).
2. Jeśli zmieniły się kolory, zaktualizuj paletę na początku `make_icon.py` (`BG`, `PAGE`, `LINE`, `POT`)
   i kolor tła w `app/src/main/res/values/ic_launcher_background.xml`.
   Skrypt zakłada cztery płaskie kolory: tło + trzy kolory motywu. Inny motyw = inne kolory w palecie
   (a dla wersji monochromatycznej: które kolory mają być „pełne”, a które wycięte — linia `mono_a`).
3. Uruchom z katalogu głównego repozytorium:

   ```
   python3 tools/icon/make_icon.py docs/brand/icon-source.png app/src/main/res /tmp/icon
   python3 tools/icon/make_psd.py /tmp/icon/fg_big.png /tmp/icon/mono_big.png docs/brand/Przepisarnia-ikona.psd docs/brand/Przepisarnia-ikona-1024.png
   ```

   Wymaga: Python 3, Pillow, NumPy.
4. Zbuduj aplikację. Jeśli launcher pokazuje starą ikonę — odinstaluj i zainstaluj aplikację ponownie.

## Co powstaje

- `mipmap-*/ic_launcher_foreground.png` — motyw (warstwa przednia ikony adaptacyjnej), 5 gęstości.
- `mipmap-*/ic_launcher_monochrome.png` — wersja do ikon tematycznych (Android 13+).
- `mipmap-anydpi-v26/ic_launcher*.xml` — składa warstwy; tło to kolor `ic_launcher_background`.
- `docs/brand/` — PSD z warstwami, okrągły PNG 1024 px (sklep, strona), podgląd 512 px.

## Zasady ikony adaptacyjnej (Android)

- Płótno 108 × 108 dp; widoczne ok. 72 dp; **bezpieczne koło 66 dp** — tylko tam motyw nie zostanie przycięty.
- Kształt (koło, squircle, kwadrat) wybiera launcher telefonu, nie aplikacja.

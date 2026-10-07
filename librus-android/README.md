# LibrusApp Android

Pierwsza wersja mobilna **0.1.0** projektu Librus Home. Nieoficjalny klient LIBRUS Synergia, przeznaczony do instalacji APK przez USB. Nie wymaga publikacji w Google Play, serwera ani przekazania hasła innemu pośrednikowi.

Telefon referencyjny: **Samsung Galaxy S10, Android 12, One UI 4.1**. Minimalne wymagania: Android 8.0 (API 26), procesor 64-bit ARM; pakiet zawiera też x86_64 do testów emulatora.

## Funkcje pierwszej wersji

- Login i hasło konta Synergia otrzymane ze szkoły; dobrowolne zapamiętanie konta.
- Pobieranie najnowszych danych po połączeniu, otwarciu lub powrocie do aplikacji, z przerwą co najmniej minuty między odczytami.
- Przegląd z licznikami oraz ostatnimi ocenami i najbliższymi wydarzeniami.
- Oceny, Wiadomości, Ogłoszenia, Terminarz, Frekwencja i Plan lekcji.
- Karty wpisów, wyszukiwanie, sortowanie, szczegóły i otwarcie oficjalnego Librusa.
- Kalendarz miesięczny z listą, wybór dnia, Dzisiaj i przełącznik Lista/Kalendarz.
- Do 200 nagłówków wiadomości; osobny przycisk pobrania treści z informacją o możliwym oznaczeniu jej jako przeczytanej.
- Zaszyfrowana kopia offline, czasy odczytów sekcji i zachowanie poprzednich danych przy błędzie.
- Demo z fikcyjnymi danymi bez logowania.

[Zakres MVP](MVP_SCOPE.md) wskazuje funkcje przeniesione z [historii użytkownika desktopu](../librus-app/USER_STORIES.md). Własne przypomnienia, powiadomienia systemowe, odświeżanie w tle i wiele kont są odłożone do następnego etapu. Ta wersja pobiera dane, kiedy użytkownik otwiera aplikację.

## Instalacja na S10 przez USB

Gotowy plik po kompilacji: `artifacts/LibrusApp-android-0.1.0-debug.apk` (około 39 MiB). Jest to wersja rozwojowa podpisana kluczem debug. Aktualizacje bez utraty danych wymagają tego samego klucza podpisu.

1. Na telefonie otwórz **Ustawienia → Telefon — informacje → Informacje o oprogramowaniu** i naciśnij **Numer kompilacji** siedem razy, aby włączyć Opcje programisty.
2. W **Opcjach programisty** włącz **Debugowanie USB**.
3. Podłącz S10 kablem USB obsługującym dane, odblokuj telefon i zaakceptuj klucz komputera w komunikacie debugowania.
4. Z folderu `librus-android` uruchom w PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\Install-USB.ps1
```

Skrypt wybiera jeden fizyczny telefon, instaluje APK z opcją aktualizacji i otwiera LibrusApp. Jeżeli ADB nie widzi telefonu, sprawdź komunikat na telefonie, kabel i sterownik USB Samsunga. Brak telefonu albo więcej niż jeden fizyczny telefon powoduje zatrzymanie skryptu.

Jeśli używasz innego SDK lub pliku:

```powershell
.\Install-USB.ps1 -SdkPath 'C:/Android/Sdk' -ApkPath 'C:/Downloads/LibrusApp-android-0.1.0-debug.apk'
```

Wymagania i działanie ADB opisuje [oficjalna instrukcja uruchamiania na urządzeniu](https://developer.android.com/studio/run/device). Google Play nie uczestniczy w tej instalacji.

## Pierwsze otwarcie

1. Wpisz login **Synergii**, niekoniecznie adres e-mail Konta LIBRUS, i hasło.
2. Domyślnie konto nie jest zapamiętywane. Zaznacz tę opcję tylko na własnym telefonie, jeśli chcesz automatycznego logowania przy kolejnych uruchomieniach.
3. Po połączeniu aplikacja pobiera sześć sekcji. Postęp jest widoczny w nagłówku; sekcje aktualizują się kolejno.
4. Menu sekcji u góry można przesuwać poziomo. Dotknij wpisu, żeby otworzyć szczegóły. W Wiadomościach osobno wybierz **Pobierz treść**.
5. W Ustawieniach możesz zmienić konto, uruchomić demo lub usunąć lokalne dane.

Przy aktywnej sesji powrót do aplikacji uruchamia odczyt, jeżeli minęła minuta od ostatniej próby. Bez zapamiętanego hasła po zakończeniu procesu potrzebne jest ponowne logowanie; zapisana kopia nadal jest dostępna. Po HTTP 429 pobieranie jest blokowane na co najmniej godzinę, także dla logowania i treści wiadomości.

## Dane i prywatność

Kopia danych i opcjonalne hasło są zapisywane w prywatnym katalogu aplikacji jako ciphertext AES-GCM. Klucz jest generowany w **Android Keystore**, a zapis aktualizowany atomowo. Systemowe kopie zapasowe aplikacji są wyłączone. Hasło nie jest wpisywane do kodu lub plików projektu; cookies i pełna pobrana treść wiadomości nie są utrwalane.

Sekcje korzystają bezpośrednio z dozwolonych serwerów Librusa przez HTTPS. Nie ma analityki, usług AI ani backendu pośredniczącego. Ekrany rzeczywistego konta blokują zrzuty systemowe; w demo można wykonać zrzut. Usunięcie lokalnych danych nie zmienia dziennika szkoły.

## Budowa projektu

Stos: natywny interfejs Android w Javie, CPython 3.13 przez **Chaquopy 17.0.0** oraz wybrane moduły integracji LibrusApp. Nie ma WebView ani osadzonego interfejsu desktopowego. Biblioteki dla Androida mają osobny plik [requirements-android.txt](app/requirements-android.txt).

Wymagane: JDK 17 lub 21, Python 3.13 oraz Android SDK z `platform-tools`, `platforms;android-35` i `build-tools;35.0.0`. Gradle Wrapper jest dołączony. Android Studio może otworzyć ten folder jako projekt Gradle.

Na przygotowanym komputerze narzędzia znajdują się w ignorowanym folderze `.tools`. `Build.ps1` wykrywa lokalnego Pythona w `.tools/python-host`, uruchamia kompilację, testy jednostkowe i Android Lint, a następnie kopiuje APK i jego SHA-256 do `artifacts`.

```powershell
powershell -ExecutionPolicy Bypass -File .\Build.ps1
```

Na innym komputerze wskaż własne ścieżki:

```powershell
.\Build.ps1 -SdkPath 'C:/Android/Sdk' -PythonPath 'C:/Python313/python.exe'
```

SDK i host Python są potrzebne tylko do budowy, nie na telefonie. Dane telefonu nie są dostępne skryptom kompilacji. Foldery `.tools`, `.gradle`, `build`, `artifacts`, lokalne konfiguracje SDK i klucze podpisu są wykluczone z Git.

## Struktura i wspólna integracja

| Plik / katalog | Odpowiedzialność |
|---|---|
| `app/src/main/java/.../MainActivity.java` | Nawigacja, logowanie, karty, kalendarz i szczegóły |
| `MobileRepository.java` | Worker, sesja procesu, cykl otwarcia i most do Pythona |
| `SecureStore.java` | Android Keystore, AES-GCM i atomowy zapis |
| `app/src/main/python/mobile_bridge.py` | Mobilne logowanie, odczyty, cache i obsługa błędów |
| `app/src/main/python/librus_shared/` | Kopie adaptera i mechanizmu porównań desktopu |
| `tools/sync_shared.py` | Odtworzenie lub sprawdzenie wspólnych modułów i licencji |
| `tests/` | Syntetyczne testy sesji mobilnej |
| `app/src/androidTest/` | Test uruchomienia demo i rzeczywistego Keystore na emulatorze |

Przed zmianą adaptera edytuj moduł desktopowy, sprawdź jego testy i świadomie odtwórz kopię Androida:

```powershell
python tools/sync_shared.py
python tools/sync_shared.py --check
```

Nie są kopiowane konta, pliki DPAPI ani pozostała część aplikacji Windows.

## Testy

[TESTING.md](TESTING.md) zawiera wyniki weryfikacji APK na emulatorze Androida 12, komendy testowe i ograniczenia tych prób.

Warstwa mobilna może być testowana na komputerze z zależnościami Python desktopu:

```powershell
..\librus-app\.venv\Scripts\python.exe -B -m unittest discover -s tests -v
```

Testy obejmują m.in. dobrowolność hasła, odtworzenie kopii, częściowe błędy, HTTP 429, wygaśnięcie sesji, odczyt treści tylko na żądanie, demo i izolację profili. Nie używają prawdziwych danych i blokują rzeczywistą sieć.

Testy urządzenia uruchamiaj na pustym emulatorze, bez podłączonego konta użytkownika. Sprawdzają załadowanie rzeczywistych bibliotek APK, ekran demo oraz szyfrowanie, uszkodzony zapis i usunięcie oddzielnego magazynu testowego. Testy nie potwierdzają logowania na konkretnym koncie; tę próbę wykonaj na swoim telefonie.

## Licencja

Kod projektu: **AGPL-3.0-or-later**, [LICENSE](LICENSE). Pochodzenie integracji i zależności opisuje [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Przy udostępnianiu APK zachowaj odpowiedni kod źródłowy i informacje licencyjne.

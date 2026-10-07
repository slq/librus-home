# LibrusApp Android

Wersja mobilna **0.3.2** projektu Librus Home. Nieoficjalny klient LIBRUS Synergia, przeznaczony do instalacji APK przez USB. Nie wymaga publikacji w Google Play, serwera ani przekazania hasła innemu pośrednikowi.

Telefon referencyjny: **Samsung Galaxy S10, Android 12, One UI 4.1**. Minimalne wymagania: Android 8.0 (API 26), procesor 64-bit ARM; pakiet zawiera też x86_64 do testów emulatora.

## Funkcje

- Login i hasło konta Synergia otrzymane ze szkoły; dobrowolne zapamiętanie konta.
- Pobieranie najnowszych danych po połączeniu, otwarciu lub powrocie do aplikacji, nie częściej niż co 5 minut. Limit obejmuje focus, powrót, ręczne odświeżenie i restart procesu.
- Przegląd z licznikami oraz ostatnimi ocenami i najbliższymi wydarzeniami.
- Oceny, Wiadomości, Ogłoszenia, Terminarz, Zadania domowe, Frekwencja i Plan lekcji.
- Karty wpisów, wyszukiwanie, sortowanie, szczegóły i otwarcie oficjalnego Librusa.
- Kalendarz miesięczny z listą, wybór dnia, Dzisiaj i przełącznik Lista/Kalendarz.
- Do 200 nagłówków wiadomości; osobny przycisk pobrania treści z informacją o możliwym oznaczeniu jej jako przeczytanej.
- Zadania domowe: przedmiot, temat, nauczyciel, kategoria, data dodania i termin; filtry „Terminy od dziś” / „Wszystkie”, wyszukiwanie, sortowanie i treść na żądanie.
- Zaszyfrowana kopia offline, czasy odczytów sekcji i zachowanie poprzednich danych przy błędzie.
- Demo z fikcyjnymi danymi bez logowania.
- Własne przypomnienia dla wiadomości, ogłoszeń, wydarzeń i zadań domowych: tekst, termin, edycja, usuwanie, historia i powrót do źródła.
- Alarmy lokalne offline i po zamknięciu aplikacji; odtworzenie alarmów po restarcie telefonu.
- Opcjonalne zbiorcze powiadomienia o zmianach oraz odczyty w tle.

[Zakres MVP](MVP_SCOPE.md) wskazuje funkcje przeniesione z [historii użytkownika desktopu](../librus-app/USER_STORIES.md). Wiele kont pozostaje poza zakresem. [NOTIFICATIONS.md](NOTIFICATIONS.md) opisuje przypomnienia, uprawnienia Androida, powiadomienia i interwały odczytu.

W 0.3.0 dodano [panel Zadania domowe](HOMEWORK.md), licznik i najbliższe terminy w Przeglądzie, przypomnienia oraz powiadomienia o nowych lub zmienionych zadaniach. Kliknięcie powiadomienia o zmianach otwiera Przegląd, a otwarcie przypomnienia wczytuje aktualny status z zaszyfrowanego zapisu.

W 0.3.2 naprawiono odczyt zadań domowych, których daty Librus uzupełnia nazwą dnia tygodnia.

Od 0.3.1 pobieranie w tle ma harmonogram około co 15 minut, również po zamknięciu okna lub zakończeniu procesu aplikacji. Połącz konto z zapamiętaniem hasła i włącz **Ustawienia → Pobieraj dane w tle**. Android może opóźniać odczyty; szczegóły w [NOTIFICATIONS.md](NOTIFICATIONS.md).

## Instalacja na S10 przez USB

Gotowy plik po kompilacji: `artifacts/LibrusApp-android-0.3.2-debug.apk` (około 39 MiB). Jest to wersja rozwojowa podpisana kluczem debug. Aktualizacje bez utraty danych wymagają tego samego klucza podpisu.

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
.\Install-USB.ps1 -SdkPath 'C:/Android/Sdk' -ApkPath 'C:/Downloads/LibrusApp-android-0.3.2-debug.apk'
```

Wymagania i działanie ADB opisuje [oficjalna instrukcja uruchamiania na urządzeniu](https://developer.android.com/studio/run/device). Google Play nie uczestniczy w tej instalacji.

## Pierwsze otwarcie

1. Wpisz login **Synergii**, niekoniecznie adres e-mail Konta LIBRUS, i hasło.
2. Domyślnie konto nie jest zapamiętywane. Zaznacz tę opcję tylko na własnym telefonie, jeśli chcesz automatycznego logowania przy kolejnych uruchomieniach.
3. Po połączeniu aplikacja pobiera siedem sekcji. Postęp jest widoczny w nagłówku; sekcje aktualizują się kolejno.
4. Menu sekcji u góry można przesuwać poziomo. Dotknij wpisu, żeby otworzyć szczegóły. W Wiadomościach osobno wybierz **Pobierz treść**.
5. W Ustawieniach możesz zmienić konto, uruchomić demo, włączyć powiadomienia i odczyt w tle albo usunąć lokalne dane i przypomnienia.

Przy aktywnej sesji powrót lub uzyskanie focusu uruchamia odczyt, jeżeli minęło 5 minut od ostatniej próby. Otwarta aplikacja odświeża się co około 5 minut. Czas ostatniej próby jest zapisany lokalnie, więc restart nie omija limitu. Ręczne logowanie w trakcie przerwy może połączyć sesję; samo pobranie danych czeka na koniec przerwy. Bez zapamiętanego hasła po zakończeniu procesu potrzebne jest ponowne logowanie; zapisana kopia nadal jest dostępna. Po HTTP 429 pobieranie jest blokowane na co najmniej godzinę, także dla logowania i treści wiadomości.

## Dane i prywatność

Kopia danych, własne przypomnienia i opcjonalne hasło są zapisywane w prywatnym katalogu aplikacji jako ciphertext AES-GCM. Klucz jest generowany w **Android Keystore**, a zapis aktualizowany atomowo. Systemowe kopie zapasowe aplikacji są wyłączone. Hasło nie jest wpisywane do kodu lub plików projektu; cookies oraz pełna pobrana treść wiadomości i zadań domowych nie są utrwalane. Lista i metadane zadań są dostępne z zaszyfrowanej kopii offline.

Sekcje korzystają bezpośrednio z dozwolonych serwerów Librusa przez HTTPS. Nie ma analityki, usług AI ani backendu pośredniczącego. Ekrany rzeczywistego konta blokują zrzuty systemowe; w demo można wykonać zrzut. Usunięcie lokalnych danych usuwa też przypomnienia i ich alarmy; nie zmienia dziennika szkoły. Przypomnienia mają osobny zaszyfrowany plik; listy są rozdzielone według profilu i trybu demo. Powiadomienia o danych pokazują tylko liczby. Treść przypomnienia jest domyślnie ukryta. Po świadomym włączeniu własnego tekstu aplikacja dostarcza ogólną wersję na ekran blokady, a ostateczną widocznością sterują ustawienia Androida.

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
| `ReminderStore.java`, `ReminderUi.java`, `ReminderAlarms.java` | Trwałe przypomnienia, formularze i alarmy |
| `ReminderReceiver.java` | Wykonanie alarmów i ich odtwarzanie po restarcie |
| `NotificationHub.java`, `BackgroundSync.java`, `SyncJobService.java` | Kanały powiadomień i opcjonalny JobScheduler |
| `SyncPolicy.java` | Wspólny limit pobierania co 5 minut |
| `ButtonStyles.java` | Płaskie przyciski, ripple i stan wyłączenia |
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

Testy obejmują m.in. limit odczytów, terminy ze zmianą czasu, szyfrowanie przypomnień, zadania domowe, migrację starszej kopii, brak podwójnej wysyłki, prywatność powiadomień, dobrowolność hasła, odtworzenie kopii, częściowe błędy, HTTP 429, wygaśnięcie sesji, odczyt treści tylko na żądanie, demo i izolację profili. Nie używają prawdziwych danych i blokują rzeczywistą sieć.

Testy urządzenia uruchamiaj na pustym emulatorze, bez podłączonego konta użytkownika. Sprawdzają załadowanie rzeczywistych bibliotek APK, ekran demo oraz szyfrowanie, uszkodzony zapis i usunięcie oddzielnego magazynu testowego. Testy nie potwierdzają logowania na konkretnym koncie; tę próbę wykonaj na swoim telefonie.

## Licencja

Kod projektu: **AGPL-3.0-or-later**, [LICENSE](LICENSE). Pochodzenie integracji i zależności opisuje [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Przy udostępnianiu APK zachowaj odpowiedni kod źródłowy i informacje licencyjne.

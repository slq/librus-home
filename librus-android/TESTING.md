# Weryfikacja LibrusApp Android 0.12.0

Weryfikacja lokalna: 8 października 2026. Telefon docelowy: Samsung Galaxy S10, Android 12, One UI 4.1. Nową wersję sprawdzono na emulatorze Androida 12, API 31, Google APIs, x86_64; fizyczny S10 nie był podłączony.

## Aktualizator APK — 0.12.0

Weryfikacja 8 października 2026: produkcyjne APK 0.12.0 zbudowane i podpisane dotychczasowym certyfikatem (SHA-256 `0b022decbecb6f36fa5279f98d4451165f3ade0b3dca2d50019e5039a10217fe`), debuggable wyłączone. Java **25**, natywne Android API 31 **29** różnych scenariuszy — **54 zaliczone testy**. Lint debug: **0 błędów, 23 ostrzeżenia** (nowe ostrzeżenie dotyczy nieprzeniesionego do resources tekstu). Wspólne źródła i licencje zgodne; warstwa Python nie zmieniała się. Fizycznego S10 nie testowano.

- `UpdatePolicyTest`: trzy nowe scenariusze stałego repozytorium i nazw pliku, HTTPS/hostów przekierowań, odrzucenia userinfo/innych portów/dowolnego serwera oraz dziennego limitu i cofnięcia zegara.
- `UpdateValidationTest`: trzy scenariusze w podstawowym zestawie — ścisłe metadane, niepoprawne typy/pakiet/hash/rozmiar/URL/opis; rzeczywiste APK release o zgodnym podpisie, odrzucenie niepodpisanego, zmienionego i o niewłaściwej wersji; częściowy/błędny plik nie jest gotowy do instalacji ani nie zmienia zapisu szkolnego. Osobny czwarty scenariusz potwierdza trwały JobScheduler 1703 co dobę, zatrzymanie po wyłączeniu, ograniczenie automatycznych sprawdzeń i brak automatycznego pobierania APK.
- `UpdateUiTest`: ręczne sprawdzenie, opis zmian i rozmiar, pobranie na żądanie, weryfikacja oraz zachowanie przycisku instalacji po odtworzeniu Activity.
- Regresje Startu, wspólnego wyszukiwania, historii zmian, powiadomień, przypomnień i statusów zadań: 26 scenariuszy w podstawowym zestawie, wszystkie poprawne, w tym cztery nowe scenariusze walidacji/interfejsu aktualizatora.

`UpdateInstallIntegrationTest` wykonano w dwóch etapach. Seed na pustym emulatorze zapisuje fikcyjne zaszyfrowane archiwum bez hasła, własne przypomnienie i wykonane zadanie. Aktualizator pobiera lokalnie symulowane wydanie 0.12.1 / code 19, weryfikuje je i otwiera rzeczywiste okno Androida „Do you want to update this app?”. Po potwierdzeniu Update przez ADB Android PackageInstaller zaktualizował aplikację z debug 0.12.0 / code 18 do release 0.12.1 / code 19. Verify w nowym procesie potwierdza brak debuggable oraz zachowanie archiwum, przypomnienia, statusu zadania i dostępu do Android Keystore. Po próbie usuwa tylko własne fikcyjne dane. Testowa 0.12.1 nie jest publikowana.

Wszystkie testy natywne wykonano na pustym `LibrusBackgroundTest` bez konta szkolnego. Pierwsza publikacja jest osobnym krokiem: testy pobierania i aktualizacji użyły fikcyjnych metadanych oraz podpisanego APK testowego, nie udają istniejącego wydania na GitHub. Publiczne repozytorium slq/librus-home nie miało jeszcze wydań podczas przygotowania. [Historie, instalacja rodzinna, podpis i publikowanie](UPDATES.md).

## Archiwum ogłoszeń — 0.11.0

Weryfikacja 8 października 2026: APK 0.11.0 zbudowany, Python **42**, Java **22** i Android API 31 **22** odrębne scenariusze — **86 zaliczonych testów**. Lint: **0 błędów, 22 ostrzeżenia**; wspólne źródła i licencje zgodne. Fizyczny S10 nie był podłączony.

Siedem nowych scenariuszy Python obejmuje: zachowanie wszystkich wpisów po częściowej i pustej liście bez fałszywych powiadomień; podmianę treści przy tym samym ID i ponowne pojawienie się bez duplikatów; pustą pierwszą bazę i późniejsze nowe ogłoszenia; migrację starszej kopii mimo zmiany roku bez przywracania starych ocen; przełączenie/ponowny wybór konta, demo, nieudane logowanie i usunięcie danych; niepoprawny odczyt i atomowe odrzucenie uszkodzonego archiwum; ponad 300 wpisów i opisy ponad 4000 znaków bez usuwania lub przycinania, z ochroną przed mutacją modelu widoku. Pozostałe 35 testów mostu i historii zmian również zaliczone.

`AnnouncementArchiveRestartTest` wykonano dwukrotnie z rzeczywistym `adb am force-stop` między Seed i Verify. Seed tworzy dwa fikcyjne ogłoszenia, zmienia treść pierwszego, potem symuluje pustą listę serwera i zapis starszego roku 2000/2001. Zapis nie zawiera hasła; sprawdzono brak jawnej treści w pliku AES. Verify w nowym procesie odtwarza oba ogłoszenia offline w bieżącym roku, sprawdza oznaczenie archiwum, oryginalne szczegóły, odtworzenie Activity i wyniki wspólnej wyszukiwarki: najnowsza treść daje 1 wynik, poprzednia 0. Po próbie usuwa tylko swoją fikcyjną kopię.

19 natywnych regresji (`HomeScreenTest`, `ChangesFeedUiTest`, `GlobalSearchUiTest`, `SearchDataTest`, `NotificationNavigationTest`, `HomeworkCompletionTest`) i osobny `BackgroundNotificationTest` przeszły. Wszystkie próby natywne odbyły się wyłącznie na pustym `LibrusBackgroundTest`. Główny emulator otrzymuje tylko główny APK bez fikcyjnych danych i instrumentacji.

Archiwum przechowuje pełną najnowszą wersję pod danym ID. Testy stabilnego ID nie znoszą ograniczenia obecnego adaptera HTML: tożsamość powstaje z tytułu/autora/daty, zatem zmiana tych pól może dać osobną pozycję. Wspólny adapter desktopu nie był zmieniany. [Historie, migracja i ograniczenia](ANNOUNCEMENTS.md).

Próba dwufazowa (tylko pusty emulator):

```powershell
adb -s emulator-5556 shell am instrument -w -e class 'pl.librushome.android.AnnouncementArchiveRestartTest#seed' pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am force-stop pl.librushome.android
adb -s emulator-5556 shell am instrument -w -e class 'pl.librushome.android.AnnouncementArchiveRestartTest#verify' pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

## Dolne menu — 0.10.1

Weryfikacja 8 października 2026: APK 0.10.1 zbudowany, Java **22** testy i Android API 31 **20** odrębnych scenariuszy — **42 zaliczone testy**. Lint: **0 błędów, 22 ostrzeżenia**; wspólne źródła i licencje zgodne. Warstwa Python i mechanizmy tła nie zmieniały się i nie wymagały ponownego uruchomienia wcześniejszych zestawów.

Sprawdzono kolejność Start, Kalendarz, Ogłoszenia, Wiadomości, Więcej oraz wyróżnienie Ogłoszeń i Więcej po wejściu do Zadań. Zadania domowe i Terminarz są dostępne przez More; kalendarz, szczegóły, checkboxy wykonania, wejścia z powiadomień i wspólne wyszukiwanie pozostają dostępne. Uruchomiono `HomeScreenTest`, `CalendarTest`, `HomeworkTest`, `HomeworkCompletionTest`, `NotificationNavigationTest`, `GlobalSearchUiTest`.

Pierwszy przebieg zaliczył 19 scenariuszy; pomocnik nawigacji w jednym teście kalendarza wybrał checkbox „Zadania domowe” zamiast przycisku menu. Po wykluczeniu `CompoundButton` z dopasowania przycisków poprawiony scenariusz zaliczono osobno. Usunięto nieużywaną ikonę dawnej zakładki Zadania. Układ i ikonę megafonu sprawdzono wizualnie na syntetycznym demo 320×640. Próby odbyły się tylko na pustym emulatorze, bez konta szkolnego; fizycznego S10 nie testowano. Główny emulator otrzymuje wyłącznie główny APK.

## Wspólne wyszukiwanie — 0.10.0

Weryfikacja 8 października 2026: APK 0.10.0 zbudowany. W tym zakresie uruchomiono Python **35**, Java **22** i Android API 31 **19** odrębnych scenariuszy — **76 zaliczonych testów**. Lint: **0 błędów, 22 ostrzeżenia**. Wspólne źródła i licencje zgodne z desktopem. Fizyczny S10 nie był podłączony.

- `SearchTextTest`: cztery nowe testy polskich znaków, wielkości liter i znaków łączących, wymagania wszystkich słów niezależnie od ich kolejności i białych znaków, literalnej interpunkcji oraz pustego zapytania.
- `SearchDataTest`: dwie nowe próby na rzeczywistym JSON Androida — siedem sekcji, podtytuły/opisy/daty w obu formatach, filtrowanie źródła, identyczne ID z różnych źródeł, pomijanie nieznanych metadanych i nienaruszanie danych; kolejność wyników, wszystkie tryby sortowania oraz historia przypomnień z izolacją profilu/demo.
- `GlobalSearchUiTest`: trzy nowe próby — wejście ze Startu i Więcej, wyszukiwanie bez ogonków, oryginalne szczegóły zadania bez pobierania treści, query/source po odtworzeniu Activity i niezależność od wyszukiwania w pojedynczej sekcji; 65 dawnych wpisów, rozwijanie kolejnych 50, aktualizacja do 66 bez utraty tekstu, brak wyników; własne przypomnienie i oryginalne szczegóły, bez ujawniania innego profilu.
- Regresje: `HomeScreenTest`, `ChangesFeedUiTest`, `CalendarTest`, `HomeworkCompletionTest` — 14 dotychczasowych scenariuszy. Pierwszy przebieg miał 18 zaliczeń i jedno oczekiwanie starego numeru wersji w teście nawigacji. Po aktualizacji oczekiwania do 0.10.0 ten scenariusz zaliczono osobno; łącznie 19 różnych scenariuszy natywnych przeszło. Nie powtarzano pozostałych testów tła i restartu, których kod ta zmiana nie dotyczy.

Wszystkie próby natywne odbyły się na pustym `LibrusBackgroundTest` z danymi fikcyjnymi. Wygląd wyników zweryfikowano na zrzucie demo 320×640: pole, źródło, kolejność i licznik są czytelne, pierwszy wynik wskazuje Terminarz, dolne menu pozostaje widoczne. Główny emulator otrzymuje tylko główny APK, bez testowej instrumentacji i fikcyjnych danych.

Historie oraz zakres lokalnych danych: [SEARCH.md](SEARCH.md).

## Zmiany od ostatniego wejścia — 0.9.0

Weryfikacja 8 października 2026: APK 0.9.0 zbudowany; Python **35** testów, Java **18** testów, Android API 31 **38** testów — **łącznie 91 poprawnych wyników**. Lint: **0 błędów, 22 ostrzeżenia**. Wspólne źródła i licencje zgodne z desktopem. Fizycznego S10 nie testowano w tej wersji.

Dziewięć nowych scenariuszy Python obejmuje: ciche pierwsze pobranie, siedem źródeł zmian zbieranych w tle, brak utraty przy powtórnym odczycie i odtworzenie zapisu, aktualizacje podczas wizyty, połączenie zmian tego samego ID bez mutacji modelu widoku, rozróżnienie nowych/zmienionych/usuniętych/przywróconych wpisów, częściowe błędy i pierwszy udany odczyt po błędzie, granice przesuwanego zakresu dat, brak zdarzeń od samego odczytu treści lub zmiany unread, brak trwałej pełnej treści, migrację starszego zapisu, konta/demo/rok i usunięcie danych, atomowe odrzucenie uszkodzonej struktury oraz jawny limit 300 wpisów i 4000 znaków kopii opisu. Dodatkowy scenariusz sprawdza ponowne logowanie po usunięciu danych bez wychodzenia z aplikacji: zmiany nadal aktualizują widoczny Start.

`ChangesFeedUiTest`: dwa scenariusze — demo siedmiu źródeł, wybór Terminarza i szczegóły wydarzenia bez szukania w panelu, zachowanie filtra oraz dokładnie tego samego znacznika wizyty po odtworzeniu Activity; także wejście do zachowanej kopii usuniętego wydarzenia spoza planu dnia bez modyfikacji oryginału.

`ChangesVisitIntegrationTest` wykonano w dwóch osobnych procesach instrumentacji z rzeczywistym force-stop między Seed/Verify. Seed tworzy fikcyjną kopię z siedmioma zmianami po ponownych odczytach, bez zapamiętanego hasła. Verify odtwarza zaszyfrowaną kopię w rzeczywistym repozytorium Androida, pokazuje wszystkie źródła, potwierdza brak rozpoczęcia nowej wizyty przy zmianie konfiguracji oraz symuluje pobranie kolejnego wpisu przy zatrzymanym Activity. Powrót pokazuje tylko nowy okres i resetuje filtr do wszystkich źródeł. Nie wykonywano prawdziwego logowania; po próbie usunięto tylko tę fikcyjną kopię.

31 podstawowych scenariuszy natywnych przeszło; osobno zaliczono po dwa etapy odtworzenia zadań i zmian oraz trzy testy powiadomień w tle, przerwy nocnej i eksportu Calendar Provider. Poprawiono wyłącznie pomocniczy dostęp do słownika Python oraz przekazywanie jawnego kontekstu do exec w teście mostu; końcowy Seed/Verify zaliczony. Wygląd listy sprawdzono na syntetycznym demo 320×640. Widoczny emulator otrzymuje wyłącznie główny APK, bez testowych danych.

Historie, definicja wejścia, limit i aktualizacja ze starszych wersji: [HOME_SCREEN.md](HOME_SCREEN.md#us-home-04--co-zmieniło-się-od-ostatniego-wejścia).

## Oznaczanie zadań jako zrobione — 0.8.0

Weryfikacja 8 października 2026: APK 0.8.0 zbudowany; Python **26** testów, Java **18** testów, Android API 31 **34** testy — **łącznie 78 poprawnych wyników**. Lint: **0 błędów, 22 ostrzeżenia**. Wspólne źródła i licencje pozostają zgodne z desktopem. Fizyczny S10 nie był testowany dla tej wersji.

Nowy `HomeworkCompletionTest` obejmuje sześć scenariuszy: szyfrowany trwały zapis i cofnięcie, izolację konta/demo/roku/ID, zachowanie statusu przy zmianie tematu lub terminu, brak ponownego zapisu przy tej samej wartości, odmowę nadpisania nieodczytywalnej struktury, odrzucenie brakującej tożsamości zadania, usunięcie wraz z odrzuceniem wcześniej zakolejkowanej zmiany, checkbox i filtry, zachowanie filtra po odtworzeniu Activity, cofnięcie ze szczegółów, wykonane zadanie usunięte ze Startu i nadal dostępne w kalendarzu, oraz rzeczywiste usuwanie przez Ustawienia z powrotem do czystego demo. Izolowane testy pliku mają osobne aliasy i nazwy. W teście potwierdzenia usuwania dopasowanie uwzględnia systemowe wielkie litery na przycisku; skorygowany scenariusz zaliczony osobno po pozostałych testach.

Dwufazowy `HomeworkCompletionRestartTest`: Seed zapisuje fikcyjne wykonanie, potem rzeczywiste `adb am force-stop` kończy proces. Verify w nowym procesie odtwarza status, uruchamia demo, potwierdza ukrycie zadania w Do zrobienia i obecność zaznaczonego checkboxa w Zrobione, a następnie usuwa tylko to syntetyczne oznaczenie. Statusy nie wymagają sesji ani dostępu do szkoły. Test warstwy Python potwierdza udostępnienie roku kopii jako kontekstu lokalnego statusu.

Pozostałe testy UI, powiadomień, odczytu w tle, przerwy nocnej i eksportu Calendar Provider przeszły. Podstawowy zestaw ma 29 scenariuszy (28 zaliczonych w zestawie i osobno zaliczony poprawiony scenariusz usuwania); osobno uruchomiono trzy testy tła/kalendarza oraz dwa etapy restartu. Wizualnie sprawdzono wykonaną pozycję z przekreślonym tytułem i checkboxem na ekranie 320×640. Wszystkie próby wykonano na pustym `LibrusBackgroundTest` z fikcyjnymi danymi, bez konta szkolnego lub przesyłania danych do Google.

Szczegóły i historie użytkownika: [HOMEWORK.md](HOMEWORK.md). Status jest własnym oznaczeniem na telefonie; nie zmienia szkolnego wpisu, wydarzenia Google ani alarmów przypomnień.

## Historia wcześniejszych weryfikacji

| Próba | Wynik |
|---|---|
| Gradle: assembleDebug | APK 0.5.0 zbudowany |
| Testy Python warstwy mobilnej | 22 testy, wszystkie poprawne |
| Testy Java | 13 testów, wszystkie poprawne |
| Android Lint | 0 błędów; 13 ostrzeżeń o nowszych SDK/Gradle i lokalizacji tekstów |
| Podstawowe testy emulatora | 21 testów, wszystkie poprawne |
| Parser zadań / desktop | 171 testów zaliczonych, 2 pominięte i 16 podtestów; bez ostrzeżeń. Zestaw obejmuje również syntetyczną tabelę Librusa, rzeczywisty parser apix, poprawne kolumny, daty z nazwą dnia tygodnia (pełną, skrótem i w nawiasie), odrzucenie nieznanych opisów dat, stały ID przy edycji terminu i brak automatycznych żądań szczegółów; brak regresji sześciu sekcji desktopu |
| Odczyt zadań z zapamiętanego konta (0.3.2) | Błąd odtworzony przed poprawką; po aktualizacji normalna synchronizacja otrzymała HTTP 200 i zapisała listę bez błędu. Potwierdzone kolumny daty + dnia tygodnia; nie pobierano pełnej treści ani załączników |
| Zadania domowe | Demo UI: filtrowanie minionych terminów, pobranie treści, zapis przypomnienia i Pokaż wpis; powiadomienie zawiera liczbę bez treści zadania |
| Kopia zadań / błędy | Odtworzenie wersji z sześcioma sekcjami, ciche pierwsze pobranie zadań, brak dostępu nie przerywa innych sekcji, błąd zachowuje listę i jej czas, treść nie jest utrwalana, 429 zachowuje blokadę |
| Dwufazowy test alarmu poza procesem (0.2.1) | Seed i Verify poprawne; alarm uruchomił odbiornik po zakończeniu procesu |
| Przejście z powiadomienia | Dwa testy rzeczywistego PendingIntent: aktualny status wykonanego przypomnienia po powrocie z tła oraz powiadomienie o zmianach otwierające Przegląd zamiast wcześniejszego panelu |
| Restart emulatora (0.2.1) | Rzeczywisty adb reboot; odtworzony alarm wykonał się po rozruchu, historia wykonanych zachowana, usunięty alert nie wrócił, ponowne odtworzenie nie wysłało wykonanych alertów |
| Powiadomienia zablokowane w Androidzie 12 (0.2.1) | Systemowy przełącznik wyłączony; alarm zapisuje wynik blocked bez banera i bez ponownej próby |
| Biblioteki w APK | lxml.etree, aiohttp i adapter Librusa załadowane |
| Android Keystore / AES-GCM | Brak jawnej treści; Unicode zachowany; naruszone ciphertext odrzucone bez nadpisania |
| Przypomnienia | Atomowy zapis, odrzucenie błędnej edycji bez zmiany pliku, jedna równoległa próba wykonania, stan zachowany po odtworzeniu, edycja planuje ponownie |
| Prywatność powiadomień | Domyślny komunikat bez źródłowego tematu i własnej treści; jawna treść dopiero po świadomej opcji; ogólna wersja na ekran blokady |
| Zimny start usługi (0.3.1) | Rzeczywisty JobScheduler uruchomił SyncJobService po zakończeniu procesu bez Activity; brak konta wyłącza harmonogram, a potwierdzenie zakończenia nie przywraca wyłączonego zadania |
| Harmonogram 15 minut | JobScheduler na API 31 przyjmuje trwałe zadanie wymagające sieci, migracja 30 → 15 minut pod jednym ID, ponowna konfiguracja nie tworzy drugiego zadania, wyłączenie usuwa je |
| Odczyt w tle i demo | JobScheduler przyjmuje zadanie; próba w demo wyłącza je, nie kontaktuje szkoły i kończy callback |
| Skróty przypomnień (0.4.0) | Termin bez godziny → poprzedni dzień 18:00, godzina przed wydarzeniem, strefy i zmiana czasu; minione i niepoprawne terminy odrzucone. Demo UI zapisuje termin wybrany skrótem |
| Odroczenie (0.4.0) | Rzeczywista akcja PendingIntent → receiver → zaszyfrowany zapis i AlarmManager, bez Activity; 30 minut od kliknięcia, zachowane źródło i prywatność. Duplikat i stary przycisk po edycji/usunięciu/kolejnym wykonaniu ignorowane |
| Powiadomienia danych w tle (0.4.0) | Repozytorium uruchomione bez Activity z zapamiętaną fikcyjną sesją: zmiany we wszystkich 7 sekcjach tworzą zbiorczy alert z liczbami. Pierwszy odczyt, brak zmian i focus są ciche; częściowy błąd nie blokuje pozostałych sekcji. Odpowiedzi Librusa zastąpione syntetycznym konektorem |
| Wspólny kalendarz (0.5.0) | Oddzielna zakładka, trzy źródła na jednym dniu, filtry źródeł i zachowanie wyboru po odtworzeniu Activity, szczegóły wydarzenia i przypomnienia, dotychczasowe panele dostępne. Agregacja nie mutuje źródeł, zachowuje daty bez godziny i izoluje profil/demo |
| Interwał i przerwa nocna (0.4.1) | 15/30/60 minut aktualizują ten sam trwały JobScheduler, wyłączone pobieranie nie włącza się od zmiany interwału. Granice 20:00/07:00 i przejście północy; zimna nocna próba nie uruchamia Pythona ani konta i nie zużywa limitu odczytu. O 07:00 próba wraca. UI zapisuje opcje i zachowuje je po odtworzeniu Activity |
| Przerwa 5 minut | Granica 299999/300000 ms, stan po restarcie, cofnięcie zegara i odroczenie danych podczas ponownego logowania |
| Zmiana czasu | Wiosenna luka odrzucona; podwójna jesienna godzina wybiera pierwszy offset |
| Interfejs demo | Płaskie przyciski, szczegóły wiadomości, formularz i zapis przypomnienia, lista ze statusem i liczbą zaplanowanych |
| Podpis | Poprawny podpis APK v2; ten sam klucz debug co w 0.1.0 |
| Wspólne moduły i licencje | sync_shared.py --check poprawny; licencje w APK zgodne z plikami źródłowymi |

Łącznie podstawowe zestawy obejmują **56 zaliczonych testów**; dwufazowe próby alarmu, restartu i blokady powiadomień wykonano dodatkowo dla 0.2.1. W 0.5.0 zestaw natywny ponownie sprawdza zapis, brak podwójnej wysyłki i otwieranie powiadomień; obsługa przywracania alarmów po rozruchu nie uległa zmianie. Testy Python blokują rzeczywistą sieć. Dodatkowa, tymczasowa instrumentacja potwierdziła normalny odczyt listy zadań z już zapamiętanego konta na osobnym emulatorze; raport zawierał wyłącznie formaty pól i stan błędu. Kod diagnostyczny usunięto z zestawu testów. Zachowania One UI nie sprawdzono.

## Poprawka odnawiania sesji — 0.5.1

Odtworzono błąd na syntetycznym zapamiętanym koncie: wygaśnięcie istniejącej sesji ustawiało trwałą blokadę automatycznego logowania, a usługa wyłączała harmonogram w tle. Trzy nowe testy przed poprawką kończyły się niepowodzeniem. Po poprawce wygaśnięcie pozostawia kopię i pozwala na jedno ponowne połączenie przy następnej dozwolonej próbie. Odrzucone logowanie lub świeża sesja wymagająca ponownego logowania blokują kolejne próby, również po odtworzeniu zapisu.

Zestaw Python: 25 testów poprawnych; Java: 13 testów poprawnych; emulator API 31: 21 testów poprawnych (19 głównych, osobno BackgroundNotificationTest i BackgroundNightTest). Łącznie 59 testów. APK 0.5.1 zbudowany, wspólne moduły zgodne z desktopem, Lint: 0 błędów, 13 ostrzeżeń. Podpis APK zgodny z poprzednimi wersjami. Zestaw prywatności powiadomień otrzymał ograniczone oczekiwanie na asynchroniczną publikację pierwszego komunikatu zamiast natychmiastowego odczytu listy Androida. Rozszerzony test `BackgroundNotificationTest` potwierdził w rzeczywistym repozytorium Androida: zachowanie harmonogramu po wygaśnięciu, brak ponownego połączenia w przerwie 5 minut, połączenie podczas kolejnej próby, brak powtórnego alertu o tej samej zawartości oraz wyłączenie prób dopiero po odrzuconym logowaniu. Użyto pustego emulatora API 31 i fikcyjnego konektora; nie wykonywano prawdziwego logowania.

Ta poprawka nie potwierdza rozwiązania zgłoszonego opóźnienia własnych przypomnień po dłuższym uśpieniu S10: alarmy są niezależne od sesji. Nie usuwa istniejącej blokady zapisanej przez starszą wersję, bo nie zachowano informacji, czy jej przyczyną była wygasła sesja czy odrzucone hasło. W takim przypadku po aktualizacji wymagane jest jednorazowe ręczne połączenie i włączenie odczytu w tle, jeśli został wyłączony.

## Integracja kalendarza Google — 0.6.0

Weryfikacja 8 października 2026: APK 0.6.0 zbudowany; Python 25 testów, Java 18 testów, natywne Android API 31 22 testy — łącznie 65 poprawnych wyników. Lint: 0 błędów i 21 ostrzeżeń dotyczących lokalizacji tekstów, nowszych narzędzi/SDK i zapisu preferencji. Podpis zgodny z poprzednimi wersjami. Wspólne źródła zgodne z desktopem.

`CalendarExportTimesTest` sprawdza daty całodniowe w UTC przy zmianie czasu, godziny szkolne, offsety, długości wydarzeń, odrzucenie niepoprawnych dat i luki DST oraz stabilne klucze oddzielające profile i źródła.

`GoogleCalendarSyncTest` uruchomiono osobno na pustym emulatorze `LibrusBackgroundTest`, po przyznaniu uprawnień odczytu/zapisu kalendarza. Tworzy rzeczywiste kalendarze Calendar Provider o fikcyjnym koncie `example.invalid`, bez podłączenia konta Google ani wysyłania danych do chmury. Potwierdza: wybór tylko kalendarza Google do zapisu, pomijanie lokalnego/tylko do odczytu, trzy źródła, brak duplikatów i brak zmian przy identycznym odczycie, zachowanie ID przy edycji, odroczenie z aktualizacją terminu, usunięcie tylko własnego przypomnienia, zachowanie szkolnego wydarzenia po skróceniu zakresu, ochronę obcego wydarzenia, odmowę zgody, uszkodzoną kopię, demo i inny profil. Sprawdzono wybór i zapis przez rzeczywisty ekran integracji, zachowanie ustawień i panelu po odtworzeniu Activity oraz rzeczywistą usługę JobScheduler aktualizującą wpis z lokalnej kopii bez otwartego Activity i bez logowania do Librusa. Po teście usunięto wyłącznie syntetyczne kalendarze i przypomnienie oraz odtworzono poprzednią kopię.

Test edycji ujawnił, że dostawca API 31 odrzuca dodatkową selekcję przy URI pojedynczego wydarzenia. Aktualizacja i usunięcie używają bazowego URI z jednoczesną selekcją ID, pakietu i metadanych źródła; test potwierdza, że obcy wpis nie jest zmieniany. Tymczasowe probe usunięto. Przy odtwarzaniu Activity wcześniej obsłużone powiadomienie nie nadpisuje zapisanego panelu ustawień.

19 podstawowych testów natywnych oraz oddzielne BackgroundNotificationTest i BackgroundNightTest również przeszły. Fizycznego telefonu ani przesłania wydarzeń do serwera Google nie sprawdzano dla 0.6.0. Widoczny emulator ma Kalendarz Google, lecz nie ma konta Google; użytkownik musi dodać konto, włączyć synchronizację wybranego kalendarza i skonfigurować integrację. Szczegóły: [GOOGLE_CALENDAR.md](GOOGLE_CALENDAR.md).

```powershell
# Tylko pusty emulator, nigdy telefon ani emulator z kontem szkolnym:
adb -s emulator-5556 shell pm grant pl.librushome.android android.permission.READ_CALENDAR
adb -s emulator-5556 shell pm grant pl.librushome.android android.permission.WRITE_CALENDAR
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.GoogleCalendarSyncTest pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

## Ekran startowy i dolne menu — 0.7.0

Weryfikacja 8 października 2026: APK 0.7.0 zbudowany; Python 25 testów, Java 18 testów, Android API 31 26 testów — **łącznie 69 poprawnych wyników**. Lint: 0 błędów i 21 ostrzeżeń. Wspólne źródła i licencje zgodne z desktopem.

Nowy `HomeScreenTest` obejmuje cztery scenariusze: lokalne daty i godziny (również offset UTC przechodzący na kolejny dzień), kolejność i brak mutacji źródeł, filtrowanie historii przypomnień, dziś/jutro i izolację profilu, wejście do szczegółów zadania z nowego Startu, zachowanie dnia/panelu po odtworzeniu Activity, pięć przypiętych dolnych przycisków, dostęp do wszystkich siedmiu paneli Więcej oraz kopię offline z częściowym błędem i niepobranymi sekcjami. Ten zestaw przeszedł również przy powiększeniu czcionki o 30% na ekranie 320×640; po próbie przywrócono poprzednie ustawienie czcionki.

Pozostałe testy UI przeszły po przejściu na rzeczywiste menu Więcej. Powiadomienia nadal otwierają aktualne przypomnienie lub Start. Osobne testy odczytu w tle, przerwy nocnej i natywnego eksportu kalendarza również zaliczone. Testy i wizualna kontrola nowego układu odbyły się na pustym emulatorze `LibrusBackgroundTest` z fikcyjnymi danymi; nie odczytywano konta szkolnego. Fizyczny S10 nie był testowany dla tej wersji.

Nowy widok nie zmienia limitu pobierania danych ani alarmów. Terminy zadań nie potwierdzają oddania pracy; oznaczanie jako zrobione pozostaje osobnym zadaniem. Historie i kryteria: [HOME_SCREEN.md](HOME_SCREEN.md).

## Powtarzanie testów

Z katalogu librus-android, po konfiguracji SDK/JDK/Pythona zgodnie z README:

```powershell
..\librus-app\.venv\Scripts\python.exe -B -m unittest discover -s tests -v
.\Build.ps1 -SdkPath 'C:/Android/Sdk' -PythonPath 'C:/Python313/python.exe'
.\gradlew.bat '-PbuildPython=C:/Python313/python.exe' :app:assembleDebugAndroidTest
```

Używaj pustego emulatora, bez konta szkolnego. Testy Keystore mają osobne pliki i aliasy. Powiadomienia testów mają wyłącznie fikcyjne dane.

```powershell
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.ChangesFeedUiTest,pl.librushome.android.HomeworkCompletionTest,pl.librushome.android.HomeScreenTest,pl.librushome.android.NativeSmokeTest,pl.librushome.android.NotificationAndReminderTest,pl.librushome.android.NotificationNavigationTest,pl.librushome.android.HomeworkTest,pl.librushome.android.BackgroundSyncTest,pl.librushome.android.ReminderConvenienceTest,pl.librushome.android.BackgroundOptionsUiTest,pl.librushome.android.CalendarTest pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

`BackgroundNotificationTest` uruchamiaj oddzielnie, jako pierwszy test w nowym procesie instrumentacji na pustym emulatorze. Wprowadza tylko fikcyjny profil do kopii testowej, zastępuje konektor syntetycznymi odpowiedziami i przywraca poprzedni zapis. Nigdy nie uruchamiaj go na koncie szkolnym. Nie wykonuje rzeczywistych żądań sieciowych:

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackgroundNotificationTest pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

`BackgroundNightTest` także uruchamiaj jako osobny proces na pustym emulatorze. Zegar lokalny jest zastąpiony wyłącznie w pamięci testu; nie zmienia czasu systemu. Sprawdza brak uruchomienia Pythona o 20:00 i ponowną próbę o 07:00 bez konta szkolnego:

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackgroundNightTest pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

## Alarm po zakończeniu procesu

Ten test ma dodatkową blokadę uruchomienia na fizycznym telefonie. Zapisuje jeden syntetyczny alert w magazynie aplikacji, a po sprawdzeniu usuwa tylko swój alert. Nie używaj go na emulatorze z danymi szkoły.

```powershell
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.AlarmProcessTest -e alarm_phase seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell pidof pl.librushome.android
# Brak PID po zakończeniu Seed jest oczekiwany. Poczekaj co najmniej 25 sekund.
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.AlarmProcessTest -e alarm_phase verify pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

Między fazami rzeczywisty AlarmManager uruchamia odbiornik, a stan wykonania i wynik wysyłki zostają zapisane. Verify sprawdza trwały wynik odbiornika, odtwarza ten sam komunikat i sprawdza jego PendingIntent oraz otwarcie zapisanych szczegółów. Bez alarm_phase ta dodatkowa próba jest pomijana.

### Dostarczenie alarmu przy zablokowanym ekranie

Na pustym emulatorze API 31 sprawdzono ponownie rzeczywisty alarm po zakończeniu procesu, przy wygaszonym ekranie i wymuszonym Doze. Stan przypomnienia zmienił się na `fired`, a wynik wysyłki na `sent`. Test dostarczenia jest oddzielony od otwierania szczegółów przez dotknięcie powiadomienia: zablokowany ekran nie pozwala ocenić tego drugiego zachowania.

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.AlarmProcessTest -e alarm_phase seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell input keyevent 223
adb -s emulator-5556 shell dumpsys deviceidle force-idle
Start-Sleep -Seconds 30
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.AlarmProcessTest -e alarm_phase verify-delivery pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell dumpsys deviceidle unforce
adb -s emulator-5556 shell input keyevent 224
```

Nie uruchamiaj fazy sprawdzającej przed terminem alarmu; start instrumentacji przerywa poprzedni proces. Po każdej próbie przywróć normalny stan urządzenia, również gdy test się nie powiedzie. Wynik tej próby nie wyklucza opóźnień na fizycznym telefonie po dłuższym uśpieniu.

### Krótki test na fizycznym S10 — diagnostyka opóźnień

8 października 2026 sprawdzono zainstalowaną na S10 wersję 0.4.1 (kod alarmów zgodny z 0.5.0). Jeden syntetyczny alarm ustawiono za minutę. Proces aplikacji zakończył się, użytkownik odłączył USB i pozostawił ekran zablokowany. Użytkownik potwierdził powiadomienie przed ponownym podłączeniem. Zapis odbiornika potwierdził `fired` / `sent` z opóźnieniem 53 ms. Nie zmieniano ustawień baterii: zgoda na dokładne alarmy i powiadomienia dostępna, oszczędzanie energii wyłączone, brak wyjątku od optymalizacji baterii. Usunięto wyłącznie testowy wpis.

To potwierdza działanie pojedynczego alarmu w tym krótkim scenariuszu, ale nie odtwarza zgłoszonego opóźnienia do otwarcia aplikacji. Nie dowodzi działania po długim uśpieniu One UI ani po restarcie telefonu. Nie ustalono przyczyny i nie zmieniono kodu produkcyjnego alarmów na podstawie tej próby. Po potwierdzeniu, że wcześniejszy problem dotyczył pojedynczego przypomnienia po dłuższej przerwie, sprawdzono pełne listy uśpionych i głęboko uśpionych aplikacji Samsunga: LibrusApp nie figuruje na żadnej z nich. Tymczasowy test APK odinstalowano z telefonu, a pozostawianie ekranu aktywnego przy USB przywrócono do wcześniejszej wartości 0. Kod diagnostyczny przeniesiono poza źródła testów do ignorowanego katalogu .tools/diagnostics; końcowy test APK nie zawiera tego kodu. Diagnostyka nie odczytywała danych logowania ani treści szkolnych wpisów; raportowała wyłącznie statusy, liczniki, uprawnienia i czasy.

## Restart Androida i wyłączone powiadomienia

`ReminderRecoveryTest` działa tylko na emulatorze i usuwa wyłącznie własne syntetyczne alerty. Przygotowuje oczekujący alarm, wykonany alert i usunięty alert. Po restarcie sprawdza trwałe statusy i brak ponownej wysyłki.

```powershell
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.ReminderRecoveryTest -e recovery_phase seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
# Natychmiast po poprawnym Seed:
adb -s emulator-5554 reboot
```

Nie rozpoczynaj Verify tuż po samym `sys.boot_completed=1`: odbiorniki rozruchu mogą być nadal w kolejce. Odczekaj zakończenie BOOT_COMPLETED, odblokowanie emulatora oraz upływ terminu na jego zegarze. W tej próbie sprawdzono też rzeczywisty komunikat w `dumpsys notification`, przed ponownym uruchomieniem instrumentacji. Następnie:

```powershell
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.ReminderRecoveryTest -e recovery_phase verify pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

Dla blokady otwórz ustawienia Androida i wyłącz główny przełącznik powiadomień LibrusApp. Samo `appops deny` nie zastępuje tego przełącznika w tej próbie na API 31.

```powershell
adb -s emulator-5554 shell am start -a android.settings.APP_NOTIFICATION_SETTINGS --es android.provider.extra.APP_PACKAGE pl.librushome.android
# Wyłącz wszystkie powiadomienia LibrusApp w pokazanym ekranie.
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.ReminderRecoveryTest -e recovery_phase seed-blocked pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
# Poczekaj co najmniej 15 sekund; w tej próbie nie restartuj emulatora.
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.ReminderRecoveryTest -e recovery_phase verify-blocked pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
# Po teście przywróć główny przełącznik powiadomień.
```

Verify usuwa swoje komunikaty przed ponownym odtworzeniem alarmów i sprawdza, że nie zostały wysłane ponownie. Bez argumentu `recovery_phase` test jest pomijany. Wszystkie fazy wymagają wyniku `OK`, nie tylko kodu wyjścia ADB.

## Start zadania z zamkniętej aplikacji

Dwufazowy `BackgroundProcessTest` uruchamiaj wyłącznie na osobnym pustym emulatorze bez konta szkolnego (w tej próbie `LibrusBackgroundTest`, `emulator-5556`). Nie używaj emulatora, na którym zapamiętano konto użytkownika. Seed zapisuje harmonogram 15 minut, po czym proces instrumentacji kończy się. Rzeczywisty JobScheduler uruchamia następnie `SyncJobService` bez Activity; ponieważ nie ma zapamiętanego konta, zadanie wyłącza harmonogram i nie kontaktuje się z Librusem.

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackgroundProcessTest -e background_phase seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell pidof pl.librushome.android
# Brak PID potwierdza zakończenie procesu. Wymuszenie terminu pomija oczekiwanie 15 minut, nie uruchamia Activity.
adb -s emulator-5556 shell cmd jobscheduler run -f pl.librushome.android 1701
# Poczekaj co najmniej 8 sekund na zakończenie usługi i inicjalizacji Pythona. Nie uruchamiaj Verify w trakcie usługi, bo instrumentacja przerywa poprzedni proces.
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackgroundProcessTest -e background_phase verify pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

Próba potwierdza uruchomienie usługi w tle i bezpieczną obsługę braku konta. Nie mierzy odczytów co 15 minut ani opóźnień podczas Doze; nie używa prawdziwego loginu. Wymagany wynik każdej fazy to `OK`.

## Granice weryfikacji

Nie wprowadzano nowego loginu ani hasła. Listę zadań odczytano przez normalną synchronizację na emulatorze z już zapamiętanym kontem. Automatyczne testy regresji biblioteki i adaptera używają syntetycznych odpowiedzi HTML; rzeczywistej pełnej treści i załączników nie sprawdzano. Nie testowano fizycznego ARM64, restartu fizycznego telefonu, długiego Doze ani mechanizmów oszczędzania baterii One UI. APK zawiera arm64-v8a i x86_64. Zgoda na powiadomienia Androida 13+ ma obsługę w kodzie i kontrolę Lint, ale test urządzenia wykonano na Androidzie 12.

Interwał JobScheduler zależy od Androida. Dokładne alarmy, systemowe wyciszenie, brak zgody i wymuszone zatrzymanie mają ograniczenia opisane w NOTIFICATIONS.md. Instalację i zachowanie One UI użytkownik sprawdza na swoim S10.

### Powtórzenie odtworzenia statusu po zakończeniu procesu

Tylko na pustym emulatorze bez konta szkolnego, po instalacji obu APK:

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.HomeworkCompletionRestartTest#seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am force-stop pl.librushome.android
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.HomeworkCompletionRestartTest#verify pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

### Zmiany z poprzedniej wizyty po zakończeniu procesu

Wyłącznie pusty emulator bez konta szkolnego; zainstaluj główny i testowy APK:

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.ChangesVisitIntegrationTest#seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am force-stop pl.librushome.android
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.ChangesVisitIntegrationTest#verify pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```


## Kopia JSON — Android 0.12.1

8 października 2026: 49 testów Python (w tym 7 nowych dla przenośnej kopii), 25 testów Java i 25 prób natywnych przeszło poprawnie. Kompilacja oraz Lint bez błędów. Dane wyłącznie syntetyczne, osobny pusty emulator LibrusBackgroundTest/API 31 na porcie 5556; nie odczytywano konta użytkownika.

- BackupDataTest (5): kompletne odtworzenie, pominięcie hasła, odrzucenie błędnych plików, brak powtórnych alertów, wycofanie błędu zapisu i odzyskanie dziennika przerwanego importu.
- BackupUiTest (1): prawdziwe przyciski Ustawień, intencje systemowego wyboru pliku, eksport JSON, podgląd, anulowanie bez zmiany danych i potwierdzony import. Odpowiedzi wyboru pliku przechwytuje Instrumentation; nie testowano konkretnego dostawcy chmurowego.
- BackupRepositoryTest (1): import usuwa sesję i wstrzymuje odczyty; po udanym fikcyjnym logowaniu z zapamiętaniem konto jest od razu utrwalone, a harmonogram 60 minut przywrócony. Test odkrył wcześniejszy błąd PyObject.get odczytujący atrybut zamiast pola słownika; odczyt wyniku logowania korzysta teraz z state_json.
- BackupMigrationTest (3 fazy): eksport syntetycznej kopii, zachowanie pliku na komputerze, prawdziwe odinstalowanie aplikacji, ponowna instalacja, potwierdzenie braku starego klucza Android Keystore, zwrot pliku i import. Archiwum, przypomnienie, status zadania i opcje odtworzone; dane logowania nie są przenoszone.
- Regresja HomeScreenTest, HomeworkCompletionTest i NotificationAndReminderTest (15).

Ta próba nie zmienia klucza podpisującego APK: testuje przenośność JSON i utratę starego klucza szyfrującego dane na telefonie. Migracja na docelowy podpis produkcyjny, fizyczny S10, dostawcy plików w chmurze oraz kalendarz Google po reinstalacji nie były testowane.

Kolejność próby reinstalacji (tylko pusty LibrusBackgroundTest):

```powershell
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackupMigrationTest#seed pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 pull /sdcard/Android/data/pl.librushome.android/files/backup-migration.json .tools/backup-migration.json
adb -s emulator-5556 uninstall pl.librushome.android
adb -s emulator-5556 install app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackupMigrationTest#prepare pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 push .tools/backup-migration.json /sdcard/Android/data/pl.librushome.android/files/backup-migration.json
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.BackupMigrationTest#verify pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

Każda faza wymaga `OK`, sam kod wyjścia ADB nie oznacza poprawnego testu. Faza Prepare tworzy katalog nowej instalacji przez API aplikacji, ponieważ scoped storage blokuje ręczne tworzenie tego katalogu przez shell.

Końcowe APK release 0.12.1/code 19 zainstalowano na pustym emulatorze i powtórzono BackupRepositoryTest oraz BackupDataTest/BackupUiTest: 7 prób zakończonych OK. Podpis zgodny z 0.12.0 (SHA-256 certyfikatu `0b022decbecb6f36fa5279f98d4451165f3ade0b3dca2d50019e5039a10217fe`), debuggable wyłączone. Archiwum odpowiadających źródeł obejmuje 134 pliki; nie zawiera kluczy, lokalnych konfiguracji, kopii JSON ani plików konta. Wydanie 0.12.1 jest przygotowane lokalnie; w tej próbie nie zostało opublikowane na GitHub.

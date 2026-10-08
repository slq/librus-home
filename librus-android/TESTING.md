# Weryfikacja LibrusApp Android 0.7.0

Weryfikacja lokalna: 8 października 2026. Telefon docelowy: Samsung Galaxy S10, Android 12, One UI 4.1. Nową wersję sprawdzono na emulatorze Androida 12, API 31, Google APIs, x86_64; fizyczny S10 nie był podłączony.

## Wyniki

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
adb -s emulator-5556 shell am instrument -w -e class pl.librushome.android.HomeScreenTest,pl.librushome.android.NativeSmokeTest,pl.librushome.android.NotificationAndReminderTest,pl.librushome.android.NotificationNavigationTest,pl.librushome.android.HomeworkTest,pl.librushome.android.BackgroundSyncTest,pl.librushome.android.ReminderConvenienceTest,pl.librushome.android.BackgroundOptionsUiTest,pl.librushome.android.CalendarTest pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
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

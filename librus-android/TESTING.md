# Weryfikacja LibrusApp Android 0.3.2

Weryfikacja lokalna: 7 października 2026. Telefon docelowy: Samsung Galaxy S10, Android 12, One UI 4.1. Nową wersję sprawdzono na emulatorze Androida 12, API 31, Google APIs, x86_64; fizyczny S10 nie był podłączony.

## Wyniki

| Próba | Wynik |
|---|---|
| Gradle: assembleDebug | APK 0.3.2 zbudowany |
| Testy Python warstwy mobilnej | 22 testy, wszystkie poprawne |
| Testy Java | 7 testów, wszystkie poprawne |
| Android Lint | 0 błędów; 8 ostrzeżeń o nowszych SDK/Gradle i lokalizacji tekstów |
| Podstawowe testy emulatora | 12 testów, wszystkie poprawne |
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
| Przerwa 5 minut | Granica 299999/300000 ms, stan po restarcie, cofnięcie zegara i odroczenie danych podczas ponownego logowania |
| Zmiana czasu | Wiosenna luka odrzucona; podwójna jesienna godzina wybiera pierwszy offset |
| Interfejs demo | Płaskie przyciski, szczegóły wiadomości, formularz i zapis przypomnienia, lista ze statusem i liczbą zaplanowanych |
| Podpis | Poprawny podpis APK v2; ten sam klucz debug co w 0.1.0 |
| Wspólne moduły i licencje | sync_shared.py --check poprawny; licencje w APK zgodne z plikami źródłowymi |

Łącznie podstawowe zestawy obejmują **41 zaliczonych testów**; dwufazowe próby alarmu, restartu i blokady powiadomień wykonano dodatkowo dla 0.2.1. W 0.3.2 zestaw natywny ponownie sprawdza zapis, brak podwójnej wysyłki i otwieranie powiadomień; kod AlarmManager i odbiornika rozruchu nie uległ zmianie. Testy Python blokują rzeczywistą sieć. Dodatkowa, tymczasowa instrumentacja potwierdziła normalny odczyt listy zadań z już zapamiętanego konta na osobnym emulatorze; raport zawierał wyłącznie formaty pól i stan błędu. Kod diagnostyczny usunięto z zestawu testów. Zachowania One UI nie sprawdzono.

## Powtarzanie testów

Z katalogu librus-android, po konfiguracji SDK/JDK/Pythona zgodnie z README:

```powershell
..\librus-app\.venv\Scripts\python.exe -B -m unittest discover -s tests -v
.\Build.ps1 -SdkPath 'C:/Android/Sdk' -PythonPath 'C:/Python313/python.exe'
.\gradlew.bat '-PbuildPython=C:/Python313/python.exe' :app:assembleDebugAndroidTest
```

Używaj pustego emulatora, bez konta szkolnego. Testy Keystore mają osobne pliki i aliasy. Powiadomienia testów mają wyłącznie fikcyjne dane.

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class pl.librushome.android.NativeSmokeTest,pl.librushome.android.NotificationAndReminderTest,pl.librushome.android.NotificationNavigationTest,pl.librushome.android.HomeworkTest,pl.librushome.android.BackgroundSyncTest pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
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

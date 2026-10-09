# LibrusApp Android 0.12.1 — zakres

Źródło: [USER_STORIES.md](../librus-app/USER_STORIES.md). Cel: aplikacja APK dla Samsung Galaxy S10, Android 12 / One UI 4.1. Bezpośrednie połączenie z Librusem, bez serwera i Google Play.

| Obszar historii | Zakres Android 0.5.0 |
|---|---|
| US-UPD — aktualizacje APK | GitHub Releases jako stałe źródło, sprawdzenie ręczne i raz dziennie, powiadomienie, opis zmian, pobranie na żądanie i systemowa instalacja z potwierdzeniem. Kontrola SHA-256, pakietu, wersji i aktualnego podpisu; [historie i publikowanie](UPDATES.md) |
| US-OGL — archiwum mobilne | Trwałe zachowanie wszystkich pobranych ogłoszeń i pełnej treści, upsert tego samego ID, bez usuwania po zniknięciu z serwera lub zmianie roku. Zaszyfrowane archiwa kont, migracja, oznaczenie lokalnej kopii, wyszukiwanie i przypomnienia; [historie i ograniczenie ID](ANNOUNCEMENTS.md) |
| US-SEARCH — rozszerzenie mobilne | Wspólne wyszukiwanie siedmiu pobranych sekcji i własnych przypomnień, źródło przy każdym wyniku, filtr, sortowanie i szczegóły. Każde słowo, polskie znaki/bez ogonków, kopia offline, bez automatycznego odczytu treści; [historie](SEARCH.md) |
| US-APP, US-LIS | Nawigacja dotykowa, stan operacji, błędy, karty, wyszukiwanie, sortowanie i szczegóły. Przyciski bez cieni, z obramowaniem, odstępami i ripple |
| US-KON | Logowanie Synergia, dobrowolny zapis, kopia, zmiana konta, wygaśnięcie sesji. Jedno aktywne konto. Nieudane logowanie automatyczne zostaje wstrzymane do świadomego ponownego połączenia |
| US-PRZ / US-HOME — rozszerzenie mobilne | Lista Od ostatniego wejścia ze wszystkich 7 źródeł, zachowana między odczytami i po zakończeniu procesu, filtrowanie źródła i szczegóły bez szukania w panelu. Start Dzisiaj/Jutro: lekcje, terminy zadań, wydarzenia, zaplanowane przypomnienia; godziny i statusy odczytu, kopia offline, skróty do wiadomości i ocen, do trzech ostatnich ocen. Dolne menu i Więcej dla pozostałych paneli; [historie i kryteria](HOME_SCREEN.md) |
| US-OCE, US-WIA, US-OGL, US-FRE, US-PLA | Sześć źródeł. Limit 200 wiadomości, treść na żądanie, ogłoszenia z treścią, frekwencja i dwa tygodnie planu |
| US-CAL — rozszerzenie mobilne | Osobny Kalendarz łączący terminarz, zadania i własne przypomnienia bieżącego profilu/demo; miesiąc/dzień, lista, filtry źródeł, wyszukiwanie, kolory i wejście do dotychczasowych szczegółów. Bez dodatkowych żądań sieciowych; [historie i kryteria](CALENDAR.md) |
| US-TER | Dwa miesiące danych, kalendarz + lista lub lista, wybór dnia, wyszukiwanie i informacja o niepełnym zakresie |
| US-ZAD — rozszerzenie mobilne | Osobny panel zadań: temat, przedmiot, nauczyciel, kategoria, dodanie i termin. Lista z bieżącego roku szkolnego, filtry terminów, wyszukiwanie i sortowanie; pełna treść na żądanie. Zadania do zrobienia na wybrany dzień w ekranie Start. Własny lokalny status zrobione/cofnięcie, filtry statusu i szyfrowany zapis dla konta/roku/demo; oznaczenie także w kalendarzu. Własne przypomnienia i zbiorcze liczby zmian. Kopia listy offline; załączniki i wysyłanie rozwiązania przez oficjalny Librus. [Historie i kryteria](HOMEWORK.md) |
| US-SYN, US-ZMI | Odczyt po otwarciu/powrocie/focusie; podczas aktywności około co 5 minut. Wspólny limit co najmniej 5 minut, również po restarcie i dla przycisku Odśwież. Postęp sekcji, częściowe błędy, cichy pierwszy odczyt, wyróżnienie zmian; HTTP 429 co najmniej godzina |
| US-DAN | AES-GCM i Android Keystore, atomowy zapis, wyłączony backup, brak trwałej treści wiadomości i cookies, bezpieczna diagnostyka i HTTPS. Przypomnienia w osobnym zaszyfrowanym pliku. Bez migracji DPAPI |
| US-DEMO | Przykładowe dane, treść wiadomości, osobna lista przypomnień demo i test powiadomienia. Demo wyłącza odczyt w tle i nie nadpisuje kopii dziennika |
| US-UST | Konto, demo, usunięcie danych i przypomnień, powiadomienia o danych, odczyt w tle, test, przejście do ustawień Androida, dokładne alarmy i licencje |
| US-REM | Tworzenie alertów ze szczegółów wiadomości, ogłoszenia, wydarzenia i zadania domowego. Tekst 1–200 znaków, przyszły termin lokalny, skróty Za 30 minut / względem terminu źródła, odroczenie z powiadomienia, ukrywanie tekstu, wyszukiwanie, statusy, edycja i usunięcie. Powrót do źródła; alert zachowany po zniknięciu wpisu |
| US-REM — cykl Androida | Alarmy offline i poza procesem aplikacji, odtworzenie po uruchomieniu telefonu/aktualizacji/zmianie zegara. Trwałe oznaczenie wykonania przed wysyłką chroni przed równoległym powtórzeniem. Dotknięcie powiadomienia otwiera zapisane przypomnienie |
| US-POW | Oddzielne kanały zmian i przypomnień; ogólne liczby zmian bez danych ucznia. Alerty o wszystkich siedmiu sekcjach poza focusem aplikacji; odczyt aktywnego okna, pierwszy odczyt i brak zmian są ciche. Informacja o blokadzie powiadomień; Android 13+ prosi o zgodę |
| Odczyty w tle | Opcjonalny JobScheduler z wyborem 15 / 30 / 60 minut (domyślnie 15), domyślna przerwa 20:00–07:00 przed uruchomieniem odczytu, z dostępem do sieci i zapamiętanym kontem. Trwały harmonogram po restarcie telefonu, uruchomienie bez aktywnego okna i wymiana starszego harmonogramu 30 minut przy aktualizacji. Wspólny limit 5 minut. Wyłączenie w demo, bez zapamiętanego konta lub po nieudanym automatycznym logowaniu |

Integracja 0.6.0: opcjonalny eksport terminarza, zadań i własnych przypomnień do wybranego kalendarza Google przez Android Calendar Provider; wybór źródeł i alertów, aktualizacje bez duplikatów na tym urządzeniu, usuwanie własnych przypomnień, odroczenia i usługa eksportu niezależna od pracy sieciowej. Demo nie eksportuje, profile są izolowane, pozostałe wydarzenia chronione. Konfiguracja i ograniczenia: [GOOGLE_CALENDAR.md](GOOGLE_CALENDAR.md).

Android nie gwarantuje dokładnej częstotliwości pracy w tle. Jest ona niezależna od pięciominutowego interwału otwartej aplikacji. Przypomnienia korzystają z AlarmManager, a przy braku zgody na dokładne alarmy z trybu dopuszczającego opóźnienie. Systemowe ograniczenia, tryb Nie przeszkadzać i wymuszone zatrzymanie są opisane w [NOTIFICATIONS.md](NOTIFICATIONS.md).

Desktop pozostaje przy sześciu sekcjach; rozszerzenie zadań uruchamia wyłącznie klient mobilny.

Nie ma wielu równoległych kont, 2FA/CAPTCHA, samodzielnej usługi działającej stale ani interwału krótszego niż 15 minut w tle. Mobile nie pokazuje automatycznie okna nad innymi aplikacjami — używa powiadomienia systemowego i jego przejścia do aplikacji.

## Kopia danych i ustawień

W Ustawieniach dostępny jest eksport/import zwykłego JSON, bez hasła i sesji Librusa. Szczegóły, wymagania i przygotowanie migracji podpisu: [BACKUPS.md](BACKUPS.md).

# LibrusApp Android 0.3.2 — zakres

Źródło: [USER_STORIES.md](../librus-app/USER_STORIES.md). Cel: aplikacja APK dla Samsung Galaxy S10, Android 12 / One UI 4.1. Bezpośrednie połączenie z Librusem, bez serwera i Google Play.

| Obszar historii | Zakres Android 0.3.2 |
|---|---|
| US-APP, US-LIS | Nawigacja dotykowa, stan operacji, błędy, karty, wyszukiwanie, sortowanie i szczegóły. Przyciski bez cieni, z obramowaniem, odstępami i ripple |
| US-KON | Logowanie Synergia, dobrowolny zapis, kopia, zmiana konta, wygaśnięcie sesji. Jedno aktywne konto. Nieudane logowanie automatyczne zostaje wstrzymane do świadomego ponownego połączenia |
| US-PRZ | Trzy liczniki oraz do czterech ocen i wydarzeń, wejście w szczegóły |
| US-OCE, US-WIA, US-OGL, US-FRE, US-PLA | Sześć źródeł. Limit 200 wiadomości, treść na żądanie, ogłoszenia z treścią, frekwencja i dwa tygodnie planu |
| US-TER | Dwa miesiące danych, kalendarz + lista lub lista, wybór dnia, wyszukiwanie i informacja o niepełnym zakresie |
| US-ZAD — rozszerzenie mobilne | Osobny panel zadań: temat, przedmiot, nauczyciel, kategoria, dodanie i termin. Lista z bieżącego roku szkolnego, filtry terminów, wyszukiwanie i sortowanie; pełna treść na żądanie. Najbliższe zadania w Przeglądzie. Własne przypomnienia i zbiorcze liczby zmian. Kopia listy offline; załączniki i wysyłanie rozwiązania przez oficjalny Librus. [Historie i kryteria](HOMEWORK.md) |
| US-SYN, US-ZMI | Odczyt po otwarciu/powrocie/focusie; podczas aktywności około co 5 minut. Wspólny limit co najmniej 5 minut, również po restarcie i dla przycisku Odśwież. Postęp sekcji, częściowe błędy, cichy pierwszy odczyt, wyróżnienie zmian; HTTP 429 co najmniej godzina |
| US-DAN | AES-GCM i Android Keystore, atomowy zapis, wyłączony backup, brak trwałej treści wiadomości i cookies, bezpieczna diagnostyka i HTTPS. Przypomnienia w osobnym zaszyfrowanym pliku. Bez migracji DPAPI |
| US-DEMO | Przykładowe dane, treść wiadomości, osobna lista przypomnień demo i test powiadomienia. Demo wyłącza odczyt w tle i nie nadpisuje kopii dziennika |
| US-UST | Konto, demo, usunięcie danych i przypomnień, powiadomienia o danych, odczyt w tle, test, przejście do ustawień Androida, dokładne alarmy i licencje |
| US-REM | Tworzenie alertów ze szczegółów wiadomości, ogłoszenia, wydarzenia i zadania domowego. Tekst 1–200 znaków, przyszły termin lokalny, skróty daty, ukrywanie tekstu, wyszukiwanie, statusy, edycja i usunięcie. Powrót do źródła; alert zachowany po zniknięciu wpisu |
| US-REM — cykl Androida | Alarmy offline i poza procesem aplikacji, odtworzenie po uruchomieniu telefonu/aktualizacji/zmianie zegara. Trwałe oznaczenie wykonania przed wysyłką chroni przed równoległym powtórzeniem. Dotknięcie powiadomienia otwiera zapisane przypomnienie |
| US-POW | Oddzielne kanały zmian i przypomnień; ogólne liczby zmian bez danych ucznia. Pierwszy odczyt i brak zmian są ciche. Informacja o blokadzie powiadomień; Android 13+ prosi o zgodę |
| Odczyty w tle | Opcjonalny JobScheduler około co 15 minut, z dostępem do sieci i zapamiętanym kontem. Trwały harmonogram po restarcie telefonu, uruchomienie bez aktywnego okna i wymiana starszego harmonogramu 30 minut przy aktualizacji. Wspólny limit 5 minut. Wyłączenie w demo, bez zapamiętanego konta lub po nieudanym automatycznym logowaniu |

Android nie gwarantuje dokładnej częstotliwości pracy w tle. Jest ona niezależna od pięciominutowego interwału otwartej aplikacji. Przypomnienia korzystają z AlarmManager, a przy braku zgody na dokładne alarmy z trybu dopuszczającego opóźnienie. Systemowe ograniczenia, tryb Nie przeszkadzać i wymuszone zatrzymanie są opisane w [NOTIFICATIONS.md](NOTIFICATIONS.md).

Desktop pozostaje przy sześciu sekcjach; rozszerzenie zadań uruchamia wyłącznie klient mobilny.

Nie ma wielu równoległych kont, 2FA/CAPTCHA, samodzielnej usługi działającej stale ani ustawień dowolnego interwału. Mobile nie pokazuje automatycznie okna nad innymi aplikacjami — używa powiadomienia systemowego i jego przejścia do aplikacji.

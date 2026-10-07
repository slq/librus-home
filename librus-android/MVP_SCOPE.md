# LibrusApp Android 0.1.0 — zakres pierwszej wersji

Źródło: [USER_STORIES.md](../librus-app/USER_STORIES.md). Cel: APK dla Samsung Galaxy S10, Android 12 / One UI 4.1, z odczytem dziennika po otwarciu. Aplikacja komunikuje się bezpośrednio z Librusem; nie wymaga serwera ani Google Play.

| Obszar historii | Zakres Android 0.1.0 |
|---|---|
| US-APP, US-LIS | Nawigacja dotykowa, stan operacji, błędy, karty, wyszukiwanie, podstawowe sortowanie i szczegóły. Bez skrótów klawiaturowych, separatorów i blokady drugiego okna |
| US-KON | Logowanie Synergia, dobrowolny zapis, kopia, zmiana konta, wygaśnięcie sesji. Jedno aktywne konto |
| US-PRZ | Trzy liczniki oraz do czterech ocen i wydarzeń, wejście w szczegóły |
| US-OCE, US-WIA, US-OGL, US-FRE, US-PLA | Sześć źródeł. Limit 200 wiadomości, treść na żądanie, ogłoszenia z treścią, frekwencja i dwa tygodnie planu. Bez alertów z wpisu |
| US-TER | Dwa miesiące danych, kalendarz + lista lub lista, wybór dnia, wyszukiwanie i informacja o niepełnym zakresie. Bez alarmów |
| US-SYN, US-ZMI | Odczyt po otwarciu/powrocie, ręczny odczyt, postęp sekcji, częściowe błędy, cichy pierwszy odczyt, wyróżnienie zmian. Przerwa co najmniej minutowa; HTTP 429 co najmniej godzina |
| US-DAN | AES-GCM i Android Keystore, atomowy zapis, wyłączony backup, brak trwałej treści wiadomości i cookies, bezpieczna diagnostyka i HTTPS. Bez migracji DPAPI |
| US-DEMO | Przykładowe dane i treść wiadomości bez nadpisywania konta. Bez symulacji ocen i testowych powiadomień |
| US-UST | Konto, demo, usunięcie zapisu, opis odświeżania i licencje. Bez cyklicznego interwału i ustawień powiadomień |
| US-REM, US-POW | Odłożone: przypomnienia, systemowe powiadomienia i harmonogram w tle |

Powrót po krótkim przełączeniu nie powoduje odczytu w ciągu minuty. Po zakończeniu procesu konto bez zapamiętanego hasła wymaga logowania; kopia pozostaje dostępna. Zapamiętane konto może po otwarciu zalogować się automatycznie. Nieudana sekcja nie jest poprawną pustą listą.

Wersja nie działa jako usługa w tle i nie obiecuje odświeżeń po zamknięciu. Cykliczne zadania, alarmy po zamknięciu, zgody systemowe, przejście z powiadomienia do wpisu i wiele kont wymagają kolejnego etapu. Logowanie 2FA/CAPTCHA nadal nie jest obsługiwane.

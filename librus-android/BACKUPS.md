# Kopia danych i ustawień — Android 0.12.1

## US-BACKUP-01 — eksport

Jako użytkownik chcę zapisać dane i ustawienia w zwykłym pliku JSON, aby zachować je po zmianie telefonu lub podpisu aplikacji.

- Ustawienia → Kopia danych i ustawień → Eksportuj kopię JSON otwiera systemowy wybór miejsca zapisu. Nie wymaga uprawnienia do całej pamięci.
- Plik `LibrusApp-kopia-RRRR-MM-DD.json` jest czytelny, bez hasła i szyfrowania. Zawiera prywatne dane szkolne; przechowuj go prywatnie, poza publicznym repozytorium.
- Kopia obejmuje ostatni zapis siedmiu sekcji, archiwa ogłoszeń wszystkich zapisanych kont, historię zmian, własne przypomnienia, statusy wykonania zadań i trwałe opcje powiadomień, pobierania w tle, przerwy nocnej, źródeł kalendarza oraz automatycznego sprawdzania aktualizacji.
- Hasło, login do logowania, cookies i sesja Librusa nie są eksportowane. W kopii pozostaje techniczny skrót identyfikatora konta, potrzebny do powiązania danych i przypomnień.
- Nie eksportujemy demo, plików APK, stanu instalatora, identyfikatora docelowego kalendarza na telefonie ani uprawnień Androida. Pełne treści wiadomości i zadań, które nie są utrwalane przez aplikację, nie trafiają do kopii.
- Eksport zapisuje wyłącznie dane już pobrane; sam nie wykonuje zapytań do szkoły ani Google.

## US-BACKUP-02 — import

Jako użytkownik chcę sprawdzić kopię przed odtworzeniem i otrzymać jednoznaczną informację, co zostanie zastąpione.

- Importuj kopię JSON otwiera systemowy wybór pliku. Format `LibrusApp-backup`, schema 1, UTF-8, maksymalnie 32 MiB. Nieznane wersje, dodatkowe pola, nieprawidłowe typy, duplikaty identyfikatorów i nadmierne zagnieżdżenie są odrzucane przed zapisem.
- Przed potwierdzeniem widzisz datę i wersję kopii oraz liczby wpisów, ogłoszeń w archiwach, przypomnień i wykonanych zadań. Anulowanie nie zmienia danych.
- Import zastępuje lokalną kopię, przypomnienia, statusy zadań i przenośne ustawienia. Nie scala kont ani kopii. Wyeksportuj bieżącą kopię, jeśli chcesz ją zachować.
- Przywrócone dane na telefonie nadal są szyfrowane nowym lokalnym kluczem Android Keystore. Nie kopiujemy starego klucza telefonu.
- Import kończy poprzednią sesję; zaloguj się ponownie do właściwego konta. Pobieranie w tle jest wstrzymane i wraca po udanym logowaniu z opcją zapamiętania, jeśli było włączone w kopii.
- Przyszłe przypomnienia są planowane pod zachowanymi identyfikatorami. Przypomnienia po terminie otrzymują status zakończony z informacją o imporcie po terminie; nie są wysyłane ponownie. Już wykonane alerty pozostają wykonane. Powtórny import nie mnoży wpisów.
- Zgody na powiadomienia, dokładne alarmy i kalendarz nadaje Android; po reinstalacji sprawdź je ponownie.
- Synchronizacja kalendarza jest wyłączona po imporcie. Wybierz ponownie kalendarz Google. Import nie usuwa ani nie dodaje wydarzeń w Google. Zachowane konto i identyfikatory źródeł pozwalają ponownie rozpoznać własne kopie w tym samym kalendarzu, jeśli nadal zawierają oznaczenia LibrusApp.
- Dane ocen i pozostałych sekcji z poprzedniego roku szkolnego podlegają istniejącej regule nowego roku; archiwum ogłoszeń jest zachowane niezależnie od roku.

## US-BACKUP-03 — przerwany import

Jako użytkownik chcę zachować poprzednią kopię, jeśli zapis zostanie przerwany.

- Wszystkie pola i stan szkolny są sprawdzane przed zmianą magazynów.
- Przed zapisem aplikacja tworzy prywatny, szyfrowany dziennik umożliwiający wycofanie operacji. Nie jest on częścią przenośnego JSON.
- Błąd zapisu odtwarza poprzednie dane i ustawienia. Po zakończeniu procesu w trakcie importu odtworzenie następuje przy inicjalizacji aplikacji lub obsłudze alarmu/zadania kalendarza.
- Problemy systemowego harmonogramu po zapisaniu kopii są raportowane oddzielnie; nie powodują utraty zapisanych danych.

## Migracja podpisu

0.12.1 jest wersją przygotowującą migrację: nadal ma podpis zgodny z 0.12.0. Nowy klucz produkcyjny nie został jeszcze utworzony ani wdrożony.

1. Zainstaluj 0.12.1 jako aktualizację, bez odinstalowania obecnej aplikacji.
2. Wyeksportuj kopię do Dokumentów/Pobranych lub innego miejsca poza prywatnym katalogiem aplikacji. Dodatkowa kopia na komputerze jest przydatna.
3. Wybierz plik w imporcie, sprawdź podsumowanie i naciśnij Anuluj — to pozwala sprawdzić poprawność pliku przed zmianą instalacji.
4. Dopiero gdy przygotujemy wersję z nowym podpisem i przetestujemy instalację, odinstaluj starą wersję, zainstaluj nową i odtwórz JSON.
5. Zaloguj się na to samo konto, sprawdź alarmy i wybierz ponownie kalendarz.

Nie odinstalowuj aplikacji przed utworzeniem i sprawdzeniem kopii. Utrata starego klucza Android Keystore uniemożliwia odczyt samego starego pliku `.aes`.

# Zadania domowe — Android 0.5.0

## Korzystanie z panelu

Przesuń poziome menu sekcji i wybierz **Zadania domowe**. Połączenie konta pobiera listę zadań wraz z pozostałymi sekcjami, z tym samym limitem minimum 5 minut między próbami. Do Librusa przekazywany jest zakres bieżącego roku szkolnego, od 1 września do 31 sierpnia, wszystkie przedmioty i statusy. Zakres wyniku zależy od danych udostępnionych przez szkołę i Librusa.

Domyślny filtr **Terminy od dziś** pokazuje zadania z terminem dzisiejszym lub późniejszym. **Wszystkie** pokazuje również minione terminy. Data na karcie jest terminem wykonania, a nie datą dodania. Wersja 0.3.2 obsługuje daty z osobnym opisem dnia tygodnia; termin bez godziny pozostaje samą datą. „Termin minął” opisuje datę; aplikacja nie wie, czy uczeń już wykonał lub oddał zadanie.

Dotknij karty, aby zobaczyć temat, przedmiot, nauczyciela, kategorię, datę dodania i termin. **Pobierz treść zadania** pobiera pełny opis osobnym żądaniem. **Przypomnij mi…** otwiera formularz własnego alertu. Z pobranej treści nadal można dodać przypomnienie. **Otwórz w Librusie** przechodzi do konkretnego zadania; przeglądarka może wymagać osobnego logowania. Załączniki i wysyłanie rozwiązania odbywają się w oficjalnym Librusie.

Lista i metadane są przechowywane w zaszyfrowanej kopii i dostępne offline. Pełna treść pobrana osobnym przyciskiem jest dostępna w otwartym oknie i nie jest utrwalana. Jej ponowne pobranie wymaga aktywnej sesji i sieci. Demo oferuje fikcyjne zadania i treść bez połączenia z Librusem.

Przegląd pokazuje liczbę zadań na dziś i później oraz do czterech najbliższych. Własne alerty i powiadomienia o zmianach działają tak jak dla innych sekcji; zobacz [NOTIFICATIONS.md](NOTIFICATIONS.md). Pełna treść na żądanie nie powoduje powiadomienia o zmianie.

## Historie użytkownika i kryteria akceptacji

### US-ZAD-01 — Lista zadań

Jako rodzic lub uczeń chcę widzieć zadania domowe z Librusa, aby zaplanować pracę przed ich terminem.

- Osobny panel zawiera temat, przedmiot, nauczyciela i termin; szczegóły zawierają też kategorię oraz datę dodania.
- Domyślnie lista ma rosnącą kolejność terminów; dostępne są wyszukiwanie i sortowanie.
- Filtry rozdzielają terminy od dziś i wszystkie pobrane zadania. Miniony termin nie jest przedstawiany jako potwierdzenie niewykonania pracy.
- Przegląd ma licznik i najbliższe terminy. Błąd odczytu nie zastępuje starszej listy pustą listą; jej czas odczytu pozostaje bez zmian.

### US-ZAD-02 — Szczegóły i oficjalny Librus

Jako użytkownik chcę pobrać treść wybranego zadania i przejść do niego w Librusie, aby zrozumieć polecenie oraz obsłużyć pliki.

- Synchronizacja pobiera listę bez dodatkowego żądania dla każdego zadania.
- Pełna treść jest pobierana po naciśnięciu przycisku; HTML jest wyświetlany jako tekst bez uruchamiania skryptów i pobierania obrazów.
- Odczyt szczegółów korzysta tylko z ID zadania na pobranej liście oraz stałego adresu HTTPS Librusa.
- Załączniki i wysyłanie pracy są obsługiwane przez oficjalny serwis.

### US-ZAD-03 — Przypomnienie

Jako użytkownik chcę ustawić własną treść i czas alertu dla zadania, aby nie przeoczyć ważnego terminu.

- Formularz umożliwia datę, godzinę, tekst 1–200 znaków i opcjonalne ujawnienie tekstu w powiadomieniu.
- Zapis, edycja, usuwanie i historia korzystają z istniejącego szyfrowanego magazynu i alarmów offline.
- **Pokaż wpis** wraca do zadania także z minionym terminem; brak źródła nie usuwa alertu.

### US-ZAD-04 — Zmiany, kopia i brak dostępu

Jako użytkownik chcę otrzymywać informacje o nowych lub zmienionych zadaniach i zachować dostęp do wcześniejszej listy przy błędzie.

- ID z Librusa jest stałe także po edycji tematu lub terminu; zmiana jest wykrywana dla tego samego wpisu.
- Pierwszy odczyt, również po aktualizacji ze starszej wersji aplikacji, jest cichy. Kolejne zmiany podlegają włącznikowi powiadomień.
- Powiadomienie pokazuje liczbę zmian bez polecenia i danych ucznia. Odczyt treści nie generuje zmiany.
- Aktualizacja zachowuje istniejące konto, kopię pozostałych sekcji i przypomnienia. Desktop pozostaje przy sześciu sekcjach.
- Brak dostępu do modułu zadań nie przerywa odczytu pozostałych sekcji; odrzucona sesja modułu nie jest automatycznie ponawiana do ponownego połączenia konta. HTTP 429 nadal wstrzymuje wszystkie odczyty na co najmniej godzinę.

## Weryfikacja

W 0.3.2 odtworzono błąd daty połączonej z dniem tygodnia i potwierdzono poprawny odczyt listy przez normalną synchronizację na już zapamiętanym koncie. Parser i integracja są także sprawdzane na odpowiedziach syntetycznych; natywne testy emulatora obejmują listę, filtry, pobranie treści, zapis przypomnienia, powrót do źródła oraz powiadomienie z samą liczbą. Szczegóły i ograniczenia: [TESTING.md](TESTING.md).

Struktura tabeli jest zgodna z [opisem modułu przez Librusa](https://portal.librus.pl/rodzina/artykuly/zadania-domowe-w-aplikacji-librus-i-synergii). Odczyt wykorzystuje istniejącą bibliotekę librus-apix 1.5.3; rzeczywisty dostęp zależy od konta i konfiguracji szkoły.

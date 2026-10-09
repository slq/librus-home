# Zadania domowe — Android 0.8.0

## Korzystanie z panelu

W dolnym menu wybierz **Więcej → Zadania domowe** (od Android 0.10.1). Zadania pozostają też widoczne w Kalendarzu i planie dnia na Starcie. Połączenie konta pobiera listę zadań wraz z pozostałymi sekcjami, z tym samym limitem minimum 5 minut między próbami. Do Librusa przekazywany jest zakres bieżącego roku szkolnego, od 1 września do 31 sierpnia, wszystkie przedmioty i statusy. Zakres wyniku zależy od danych udostępnionych przez szkołę i Librusa.

Domyślny filtr **Terminy od dziś** pokazuje zadania z terminem dzisiejszym lub późniejszym. **Wszystkie terminy** pokazuje również minione terminy. Osobny filtr statusu ma opcje **Do zrobienia / Zrobione / Wszystkie**; domyślnie widać zadania do zrobienia. Filtry statusu i daty działają razem: aby zobaczyć również wykonane zadania z przeszłości, wybierz Zrobione i Wszystkie terminy. Data na karcie jest terminem wykonania, a nie datą dodania. Wersja 0.3.2 obsługuje daty z osobnym opisem dnia tygodnia; termin bez godziny pozostaje samą datą. „Termin minął” opisuje datę; wykonanie możesz oznaczyć lokalnie, natomiast aplikacja nie potwierdza oddania pracy w szkole.

Dotknij karty, aby zobaczyć temat, przedmiot, nauczyciela, kategorię, datę dodania i termin. **Pobierz treść zadania** pobiera pełny opis osobnym żądaniem. **Przypomnij mi…** otwiera formularz własnego alertu. Z pobranej treści nadal można dodać przypomnienie. **Otwórz w Librusie** przechodzi do konkretnego zadania; przeglądarka może wymagać osobnego logowania. Załączniki i wysyłanie rozwiązania odbywają się w oficjalnym Librusie.

Lista i metadane są przechowywane w zaszyfrowanej kopii i dostępne offline. Pełna treść pobrana osobnym przyciskiem jest dostępna w otwartym oknie i nie jest utrwalana. Jej ponowne pobranie wymaga aktywnej sesji i sieci. Demo oferuje fikcyjne zadania i treść bez połączenia z Librusem.

Start **Dzisiaj / Jutro** pokazuje zadania do zrobienia na wybrany dzień i liczbę zrobionych. Wykonane zadanie pozostaje w kalendarzu z zaznaczonym checkboxem i przekreślonym tytułem; można stamtąd cofnąć oznaczenie. Własne alerty i powiadomienia o zmianach działają tak jak dla innych sekcji; zobacz [NOTIFICATIONS.md](NOTIFICATIONS.md). Pełna treść na żądanie nie powoduje powiadomienia o zmianie.

### Oznaczanie jako zrobione

Zaznacz **Zrobione** na karcie zadania, w szczegółach, na ekranie Start lub w kalendarzu. Odznaczenie przywraca zadanie do zrobienia. Stan jest zapisywany po zakończeniu operacji na telefonie, w osobnym pliku szyfrowanym przez Android Keystore, również bez sesji i internetu. Dotyczy tego samego ID zadania, konta i roku szkolnego; zmiana tematu lub terminu przez szkołę nie cofa Twojego oznaczenia. Statusy demo są oddzielone od rzeczywistego konta.

Oznaczenie służy organizacji własnej pracy i nie wysyła rozwiązania do Librusa. Nie zmienia wydarzenia eksportowanego do Google ani własnego przypomnienia — te nadal mogą przypomnieć np. o oddaniu przygotowanej pracy. Możesz nimi zarządzać w panelu Przypomnienia i integracji kalendarza. Usunięcie lokalnych danych w Ustawieniach usuwa również wszystkie statusy. Brak zadania w kolejnym odczycie nie usuwa oznaczenia; po powrocie tego samego zadania stan pozostaje.

Przy błędzie odczytu lub zapisu plik jest zachowany, a zmienianie statusów wstrzymane do ponownego udanego odczytu. Aplikacja pokazuje komunikat i przycisk **Ponów odczyt statusów**. Nie przedstawia nieudanego zapisu jako potwierdzonego wykonania.

## Historie użytkownika i kryteria akceptacji

### US-ZAD-01 — Lista zadań

Jako rodzic lub uczeń chcę widzieć zadania domowe z Librusa, aby zaplanować pracę przed ich terminem.

- Osobny panel zawiera temat, przedmiot, nauczyciela i termin; szczegóły zawierają też kategorię oraz datę dodania.
- Domyślnie lista ma rosnącą kolejność terminów; dostępne są wyszukiwanie i sortowanie.
- Filtry rozdzielają terminy od dziś i wszystkie pobrane zadania. Miniony termin nie jest przedstawiany jako potwierdzenie niewykonania pracy.
- Start ma terminy zadań do zrobienia na dzisiaj/jutro oraz liczbę wykonanych na ten dzień. Błąd odczytu nie zastępuje starszej listy pustą listą; jej czas odczytu pozostaje bez zmian.

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

### US-ZAD-05 — Własny status wykonania

Jako rodzic lub uczeń chcę oznaczyć zadanie jako zrobione i móc cofnąć oznaczenie, aby rozdzielić rzeczy do wykonania od przygotowanych.

- Checkbox jest dostępny w panelu Zadania, jego szczegółach, na ekranie Start i w kalendarzu. Wszystkie widoki korzystają z tego samego lokalnego stanu.
- Stan działa offline i pozostaje po zamknięciu procesu oraz aktualizacji danych. Klucz obejmuje konto, tryb demo, rok szkolny i ID z Librusa; tytuł i termin nie są kluczem.
- Filtry Do zrobienia/Zrobione/Wszystkie łączą się z zakresem terminów, wyszukiwaniem i sortowaniem. Wybrany filtr zachowuje się po odtworzeniu Activity.
- Start pokazuje zadania do zrobienia; kalendarz zachowuje wykonane zadania z oznaczeniem i możliwością cofnięcia. Przycisk Wszystkie przy grupie zadań na Starcie pokazuje pełną pobraną listę, bez ukrywania statusów i minionych terminów.
- Oznaczenie nie mutuje kopii szkolnego wpisu ani historii wykrywania zmian i nie wykonuje żądań do Librusa lub Google. Alarmów przypomnień nie odwołuje automatycznie.
- Status jest zapisany szyfrowanym, atomowym plikiem; błędny zapis lub nieodczytywalny plik nie są nadpisywane. Usunięcie lokalnych danych czyści statusy, także demo; wcześniej zakolejkowany zapis nie może przywrócić usuniętego stanu.

## Weryfikacja

W 0.3.2 odtworzono błąd daty połączonej z dniem tygodnia i potwierdzono poprawny odczyt listy przez normalną synchronizację na już zapamiętanym koncie. Parser i integracja są także sprawdzane na odpowiedziach syntetycznych; natywne testy emulatora obejmują listę, filtry, pobranie treści, zapis przypomnienia, powrót do źródła oraz powiadomienie z samą liczbą. Szczegóły i ograniczenia: [TESTING.md](TESTING.md).

Struktura tabeli jest zgodna z [opisem modułu przez Librusa](https://portal.librus.pl/rodzina/artykuly/zadania-domowe-w-aplikacji-librus-i-synergii). Odczyt wykorzystuje istniejącą bibliotekę librus-apix 1.5.3; rzeczywisty dostęp zależy od konta i konfiguracji szkoły.

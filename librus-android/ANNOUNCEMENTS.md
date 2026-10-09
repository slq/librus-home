# Archiwum ogłoszeń — Android 0.11.0

## US-OGL-ARCH-01 — zachowanie pobranych ogłoszeń

Jako rodzic chcę móc wrócić do raz pobranego ogłoszenia, nawet po zakończeniu jego publikacji w Librusie.

Kryteria akceptacji:

- Ogłoszenia pozostają w panelu Ogłoszenia, również gdy ostatni udany odczyt zwraca pustą listę lub tylko część poprzednich wpisów. Aplikacja nie usuwa starszych pozycji według daty, liczby ani przy zmianie roku szkolnego.
- Zachowujemy pełną pobraną treść wraz z tytułem, autorem i datą. Limit kopii opisu w historii zmian nie ogranicza treści archiwum.
- Pozycja nieobecna na ostatniej udanej liście ma oznaczenie „Lokalne archiwum”; szczegóły wyjaśniają, że jest to zapisana kopia. Nie zgadujemy przyczyny braku na serwerze. Nieudane pobranie nie zmienia oznaczenia dostępności ani ostatniego czasu odczytu.
- Archiwum działa offline i po zakończeniu procesu. Ogłoszenia z poprzedniego roku pozostają dostępne, podczas gdy pozostałe sekcje zachowują dotychczasowe zasady roku szkolnego.
- Archiwalne pozycje można wyszukiwać i otwierać oraz tworzyć z nich własne przypomnienia. Link do oficjalnego Librusa może już nie zawierać danego wpisu.

## US-OGL-ARCH-02 — najnowsza treść i wykrywanie zmian

Jako użytkownik chcę widzieć jedną, najnowszą pobraną wersję danego ogłoszenia.

Kryteria akceptacji:

- Nowe ID dodaje wpis; to samo ID aktualizuje całość poprzedniego wpisu. Nie trzymamy osobnych wersji treści w liście ogłoszeń. Sprzeczne duplikaty ID w jednym odczycie są błędem i zachowują wcześniejsze dane.
- Zmiana treści trafia do dotychczasowego licznika zmian i na Start. Samo zniknięcie albo ponowne pojawienie się niezmienionego ogłoszenia nie tworzy dodatkowego alertu. Pierwsze pobranie pozostaje cichą bazą, również gdy lista jest pusta.
- Aktualizację możemy pobrać tylko dopóki serwer udostępnia ogłoszenie. Po zakończeniu publikacji zachowujemy ostatnią pobraną wersję.

### Ograniczenie identyfikatora w obecnym adapterze

Biblioteka `librus-apix` zwraca tytuł, autora, datę i treść, bez stałego ID serwera. Dotychczasowy wspólny adapter tworzy identyfikator z **tytułu, autora i daty**, pomijając treść. Dlatego zmiana samej treści aktualizuje istniejące ogłoszenie, lecz zmiana tytułu, autora lub daty może utworzyć osobną pozycję. Bez stabilnego ID nie da się niezawodnie odróżnić takiej edycji od nowego ogłoszenia. Nie scalamy wpisów na podstawie podobieństwa, żeby nie utracić różnych ogłoszeń.

## US-OGL-ARCH-03 — konto, migracja i bezpieczny zapis

Jako użytkownik chcę zachować archiwum przy aktualizacji, nie ujawniając ogłoszeń innemu kontu.

Kryteria akceptacji:

- Aktualizacja automatycznie przenosi istniejącą pobraną listę do archiwum. Dotyczy też starszej kopii z poprzedniego roku. Wpisów utraconych przed aktualizacją nie da się odtworzyć.
- Archiwa są przypisane do identyfikatora konta i przechowywane w dotychczasowym atomowym zapisie AES-GCM/Android Keystore. Wyświetlamy wyłącznie archiwum aktywnego konta. Przełączenie konta nie usuwa poprzedniego archiwum; powrót do tego konta odtwarza je.
- Demo ma wyłącznie fikcyjne dane i nie nadpisuje archiwum. Nieudane logowanie zachowuje poprzednią kopię. Usunięcie lokalnych danych w Ustawieniach czyści wszystkie archiwa; czyszczenie danych Androida lub odinstalowanie usuwa zapis.
- Uszkodzony odczyt lub niepoprawna struktura zapisanych archiwów nie zastępuje poprzednich danych. W razie błędu zapisu aplikacja pokazuje istniejący komunikat, a poprzedni plik pozostaje nienaruszony.
- Archiwum nie wprowadza dodatkowych żądań do Librusa ani zmiany interwału. Nie ma automatycznego limitu liczby ogłoszeń; nadal obowiązuje techniczny limit 34 MiB całej zaszyfrowanej kopii. Po przekroczeniu limitu zapis zgłasza błąd zamiast usuwać stare ogłoszenia.

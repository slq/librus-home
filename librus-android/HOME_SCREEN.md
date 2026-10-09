# Ekran startowy i nawigacja — 0.10.1

## US-HOME-01 — plan dnia

Jako rodzic chcę po otwarciu aplikacji zobaczyć lekcje, zadania, wydarzenia i własne przypomnienia na dzisiaj lub jutro, aby szybko sprawdzić ważne sprawy.

Kryteria akceptacji:

- Domyślny Start pokazuje Dzisiaj; Jutro przełącza dzień według lokalnej daty telefonu. Wybrany dzień zachowuje się przy odtworzeniu ekranu i podczas przechodzenia do innych paneli.
- Lekcje z planu, terminy wykonania zadań i wydarzenia z terminarza są grupowane osobno. Widoczne są wszystkie pobrane wpisy na wybrany dzień, ich godziny oraz temat i opis pomocniczy. Wpisy w grupie są uporządkowane godziną; wpisy bez godziny są na początku i nie otrzymują fikcyjnej godziny.
- Przypomnienia obejmują tylko zaplanowane wpisy bieżącego profilu lub osobnego profilu demo. Historia wykonanych nie trafia na plan dnia.
- Każda grupa ma licznik i przycisk Wszystkie otwierający pełny panel. W przypadku niepobranej sekcji licznik pokazuje kreskę, a komunikat odróżnia brak odczytu od pustej listy.
- Dotknięcie wpisu otwiera istniejące szczegóły, w tym pobranie treści zadania lub edycję przypomnienia. Start nie pobiera treści automatycznie.
- Skróty pokazują liczbę nieprzeczytanych wiadomości w pobranych nagłówkach i liczbę pobranych ocen. Poniżej planu dnia pozostają do trzech ostatnich ocen.

## US-HOME-02 — stałe menu telefonu

Jako użytkownik chcę przechodzić do najczęstszych paneli jednym dotknięciem, aby nie przesuwać długiego menu sekcji.

Kryteria akceptacji:

- Dolne menu ma pięć pozycji: Start, Kalendarz, Ogłoszenia, Wiadomości, Więcej. Pozostaje widoczne podczas przewijania treści; podpisy dopasowują się do szerokości.
- Więcej otwiera listę: Szukaj, Oceny, Zadania domowe, Terminarz, Frekwencja, Plan lekcji, Przypomnienia, Ustawienia. Liczba zaplanowanych przypomnień pojawia się przy pozycji Przypomnienia.
- Wybrany panel jest wyróżniony; dla paneli z listy wyróżnia się Więcej. Panel zachowuje się po odtworzeniu Activity.
- Powiadomienie o zmianach nadal otwiera Start, a powiadomienie przypomnienia otwiera jego aktualne szczegóły. Nowe menu zachowuje wyszukiwanie, kalendarz i dotychczasowe akcje paneli.

## US-HOME-03 — dostęp do kopii i częściowe błędy

Jako użytkownik chcę czytać plan dnia również bez połączenia, aby móc wrócić do pobranych informacji.

Kryteria akceptacji:

- Widok korzysta z istniejącej zaszyfrowanej kopii i lokalnych przypomnień. Zmiana dnia, menu i otwieranie listy nie wprowadzają dodatkowych żądań do Librusa ani nowego harmonogramu pracy w tle.
- Sekcje pokazują czas ostatniego udanego odczytu. Błąd odświeżenia jest widoczny obok zachowanych wpisów; nie jest przedstawiany jako brak zdarzeń.
- Plan lekcji poza dwoma tygodniami ostatniego odczytu ma ostrzeżenie o zakresie. Wpisy bez rozpoznanej daty pozostają dostępne w pełnym panelu, bez zgadywania dnia.
- Powitanie z logowaniem/demo pojawia się przy braku kopii; istniejąca kopia pozostaje od razu czytelna. Start przelicza datę po północy także przy pozostawieniu otwartej aplikacji.

Od 0.8.0 grupa zadań pokazuje tylko zadania do zrobienia oraz liczbę wykonanych na ten dzień. Checkbox pozwala oznaczyć wykonanie również ze Startu; wykonane zadania pozostają w pełnym panelu i kalendarzu. Status jest lokalny i nie potwierdza oddania pracy. [Zasady i historie](HOMEWORK.md).

## US-HOME-04 — co zmieniło się od ostatniego wejścia

Jako rodzic chcę na głównym ekranie zobaczyć konkretne nowe i zaktualizowane wpisy od poprzedniego wejścia do aplikacji, aby od razu zapoznać się z ważną informacją i nie szukać jej w poszczególnych panelach.

Kryteria akceptacji:

- Sekcja **Od ostatniego wejścia** znajduje się nad planem Dzisiaj/Jutro. Obejmuje oceny, wiadomości, ogłoszenia, terminarz, frekwencję, plan lekcji i zadania domowe.
- Pozycja pokazuje panel, rodzaj zmiany, temat, opis pomocniczy i datę szkolnego wpisu. Nowy wpis jest odróżniony od zaktualizowanego. Zniknięcie wydarzenia/lekcji w nadal pobieranym zakresie ma osobne oznaczenie i zachowaną kopię metadanych.
- Domyślnie widać trzy ostatnio wykryte zmiany. **Pokaż wszystkie zmiany** rozwija listę. Filtr źródła pozwala np. zobaczyć tylko Terminarz albo Oceny.
- Dotknięcie pozycji otwiera dotychczasowe szczegóły wpisu bez szukania go w panelu. Jeśli źródła nie ma już na bieżącej liście, widoczna jest zachowana kopia z wyjaśnieniem; pełna treść nie jest automatycznie pobierana.
- Zmiany są zbierane podczas odczytów w tle i podczas używania aplikacji. Kolejny odczyt bez zmian nie kasuje listy; aktualizacje tego samego ID są połączone w jedną najnowszą pozycję.
- Wejście oznacza powrót do aplikacji po jej opuszczeniu lub zimne uruchomienie. Przełączenie panelu, otwarcie dialogu, uzyskanie focusu i odtworzenie ekranu po zmianie konfiguracji nie zaczynają nowego okresu. Przy nowym wejściu filtr wraca do wszystkich źródeł.
- Przy wejściu lista obejmuje zmiany wykryte od poprzedniego wejścia. Nowe odczyty dopisują zmiany do widocznej listy; te wykryte podczas bieżącej wizyty należą też do okresu prezentowanego przy następnej wizycie.
- Pierwszy udany odczyt każdej sekcji jest punktem odniesienia i nie oznacza wszystkich dotychczasowych danych jako nowych. Dotyczy to również sekcji, która wcześniej nie mogła zostać pobrana.
- Zakres przesuwany wraz z miesiącem/tygodniem nie tworzy fałszywych nowych zdarzeń ani usunięć po wyjściu poza zakres. Błąd pojedynczej sekcji zachowuje wcześniej zebrane zmiany.
- Lista i data poprzedniego wejścia są zapisane w istniejącej zaszyfrowanej kopii konta. Zachowują się po zakończeniu procesu, nie zawierają pełnej treści pobranej na żądanie i są izolowane od demo, innych kont oraz innych lat szkolnych. Usunięcie lokalnych danych usuwa również historię.
- Przechowywanych jest maksymalnie 300 ostatnio zmienionych wpisów oraz do 4000 znaków opisu zachowanej kopii każdej pozycji. Po przekroczeniu limitu aplikacja wyraźnie informuje o ograniczeniu. Pełne aktualne dane nadal są dostępne w panelach.

### Aktualizacja ze starszej wersji

Wersja 0.8.0 i starsze nie zapisywały historii między wizytami; zmian utraconych po kolejnych odczytach nie można odtworzyć. W 0.9.0 istniejąca kopia pozostaje punktem odniesienia, a nowe zmiany są zbierane od aktualizacji. Dane konta i statusy wykonania zadań pozostają zachowane. Nowa sekcja korzysta z dotychczasowego harmonogramu odczytów i nie zwiększa częstotliwości pobierania.

Od 0.10.0 Start ma skrót „Szukaj we wszystkich sekcjach”, dostępny również jako „Szukaj” pod Więcej. Wyszukiwarka jest dodatkowym ekranem, zachowującym dotychczasowy Start oraz filtry poszczególnych paneli. [Historie i kryteria](SEARCH.md).

Od 0.10.1 Ogłoszenia zajmują trzecie miejsce dolnego menu. Zadania domowe są pod Więcej, jak Terminarz; nadal można je otwierać i oznaczać w kalendarzu oraz planie dnia na Starcie. Wybranie Zadania domowe wyróżnia Więcej, a Ogłoszeń — ich własną zakładkę.

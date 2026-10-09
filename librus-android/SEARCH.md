# Wspólne wyszukiwanie — Android 0.10.0

## US-SEARCH-01 — jedno miejsce do szukania

Jako rodzic chcę znaleźć szkolną informację bez pamiętania, w którym panelu ją widziałem.

Kryteria akceptacji:

- Start ma przycisk „Szukaj we wszystkich sekcjach”, a Więcej — pozycję „Szukaj”. Dolne menu oraz istniejące panele pozostają dostępne.
- Jedno pole przeszukuje oceny, wiadomości, ogłoszenia, terminarz, zadania, frekwencję, plan lekcji i przypomnienia bieżącego profilu lub demo. Obejmuje również stare terminy, zrobione zadania i wykonane przypomnienia, niezależnie od filtrów innych paneli.
- Dopasowanie dotyczy tytułu, opisu, nadawcy/przedmiotu zawartego w podtytule i daty, zarówno w formacie kopii jak i widocznym dd.MM.yyyy. Wszystkie wpisane słowa muszą wystąpić w tej samej pozycji; kolejność słów, wielkość liter i polskie ogonki nie mają znaczenia. Znaki specjalne są zwykłym tekstem, nie wyrażeniem regularnym.
- Puste pole pokazuje instrukcję. Brak dopasowań pokazuje licznik 0 oraz informację o braku wyników w pobranych danych. Lista wyświetla pełny licznik, po 50 wyników z przyciskiem „Pokaż kolejne wyniki”.

## US-SEARCH-02 — źródła i szczegóły

Jako użytkownik chcę rozpoznać źródło wyniku i od razu przejść do właściwych szczegółów.

Kryteria akceptacji:

- Wynik ma nazwę źródła, tytuł, podtytuł i datę; zadania zachowują lokalny checkbox i oznaczenie wykonania.
- Filtr „Wszystkie sekcje” można zawęzić do jednego z ośmiu źródeł. Domyślna kolejność preferuje tytuł, następnie podtytuł, następnie pozostałe pola; w grupie pokazuje nowsze daty najpierw. Dostępne są też Najnowsze, Najstarsze i Tytuł A–Z.
- Dotknięcie wyniku otwiera istniejące szczegóły lub przypomnienie. Powrót zachowuje tekst i źródło, podobnie jak odtworzenie ekranu. Wyczyść usuwa zapytanie, pozostawiając wybrane źródło.
- Zapytanie globalne jest niezależne od zapytań i filtrów poszczególnych paneli. Wybór nowego tekstu lub źródła zaczyna listę od pierwszych 50 pozycji. Odświeżenie danych przelicza wyniki, zachowując tekst i źródło.

## US-SEARCH-03 — lokalna kopia i prywatność

Jako użytkownik chcę szukać również offline i wiedzieć, jakie dane zostały objęte wyszukiwaniem.

Kryteria akceptacji:

- Wyszukiwanie korzysta wyłącznie z bieżącej kopii i przypomnień należących do aktualnego konta/demo. Nie tworzy pliku indeksu ani dodatkowych żądań do Librusa i nie zmienia harmonogramu odczytów.
- Zakres ogranicza się do pobranych list: m.in. do 200 wiadomości, zakresu terminarza i planu lekcji ostatniego odczytu. Nie odtwarza usuniętych wpisów z historii zmian.
- Pełne treści wiadomości i zadań, pobierane osobnym przyciskiem, nie są trwale przechowywane ani indeksowane. Wyszukiwarka wyjaśnia ten zakres przed listą wyników. Opisy dostępne w pobranej liście, w tym treść ogłoszeń, są przeszukiwane.
- Niepobrane sekcje i błędy odświeżenia są jawnie pokazane. Nie zastępują zachowanych wyników komunikatem, że szkolnych danych nie ma.
- Ekran rzeczywistego konta pozostaje chroniony przed zrzutami przez FLAG_SECURE; demo wykorzystuje tylko dane fikcyjne. Hasła, identyfikatory konta i prywatne metadane spoza wpisów nie są przeszukiwane.

Od Android 0.11.0 wyszukiwanie ogłoszeń obejmuje również całe lokalne archiwum aktualnego konta, w tym poprzednie lata szkolne. Inne konta i dane demo pozostają odrębne. [Zasady](ANNOUNCEMENTS.md).

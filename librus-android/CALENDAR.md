# Wspólny kalendarz — Android 0.5.0

## US-CAL-01 — Dodatkowy panel

Jako rodzic lub uczeń chcę otworzyć osobną zakładkę Kalendarz, aby zobaczyć wszystkie ważne terminy bez przechodzenia między panelami.

- Zakładka Kalendarz jest obok Przeglądu.
- Łączy wydarzenia z Terminarza, terminy wykonania Zadań domowych i własne Przypomnienia.
- Terminarz, Zadania domowe i Przypomnienia pozostają dostępne osobno.
- Widok korzysta z już pobranej kopii i lokalnego magazynu przypomnień; nie generuje osobnych żądań do Librusa.

## US-CAL-02 — Miesiąc, dzień i źródła

Jako użytkownik chcę wybrać dzień i rodzaje wpisów, aby szybko zobaczyć sprawy na konkretny termin.

- Dostępne są poprzedni/następny miesiąc, Dzisiaj i Cały miesiąc.
- Liczba wpisów na dniu odpowiada włączonym źródłom i wyszukiwaniu.
- Pod kalendarzem jest lista wybranego dnia lub miesiąca.
- Każde źródło ma własny kolor i etykietę: terminarz, zadania, przypomnienia.
- Można wyłączyć dowolne źródło, wyszukiwać i sortować listę.
- Wybór dnia, miesiąca, źródeł i zapytania jest zachowany przy odtworzeniu ekranu przez Androida. Terminarz ma niezależny wybór miesiąca i dnia.

## US-CAL-03 — Szczegóły

Jako użytkownik chcę dotknąć wpisu z kalendarza, aby skorzystać z jego istniejących funkcji.

- Wydarzenie i zadanie otwierają dotychczasowe szczegóły, w tym dodanie przypomnienia, pobranie treści zadania i przejście do Librusa.
- Przypomnienie otwiera istniejące szczegóły, edycję, usunięcie i Pokaż wpis.
- Lista przypomnień obejmuje zaplanowane i wykonane wpisy według ich zapisanej daty; stan jest oznaczony tekstem.
- Po edycji, usunięciu lub odroczeniu przypomnienia widok aktualizuje lokalne dane.

## US-CAL-04 — Kopia, daty i prywatność

Jako użytkownik chcę widzieć poprawne terminy z mojego konta także offline, aby nie pomylić spraw różnych profili.

- Zadanie z samą datą nie dostaje sztucznej godziny 00:00.
- Chwila przypomnienia jest wyświetlana w aktualnej strefie telefonu.
- Widoczne są tylko przypomnienia bieżącego profilu i trybu; demo nie łączy się z rzeczywistym kontem.
- Kalendarz nie zmienia danych ani nie tworzy osobnej kopii źródeł na dysku.
- Błąd sekcji pokazuje komunikat i pozostawia starszą kopię. Terminarz informuje o miesiącu poza zakresem odczytu; zakres szkolnych danych nadal zależy od Librusa.
- Pusty widok wskazuje możliwość zmiany dnia, źródeł i wyszukiwania.

Weryfikacja jest opisana w [TESTING.md](TESTING.md). Oznaczanie zadań jako zrobione i przebudowa ekranu startowego pozostają kolejnymi etapami.

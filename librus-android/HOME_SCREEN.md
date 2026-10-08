# Ekran startowy i nawigacja — 0.7.0

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

- Dolne menu ma pięć pozycji: Start, Kalendarz, Zadania, Wiadomości, Więcej. Pozostaje widoczne podczas przewijania treści; podpisy dopasowują się do szerokości.
- Więcej otwiera listę: Oceny, Ogłoszenia, Terminarz, Frekwencja, Plan lekcji, Przypomnienia, Ustawienia. Liczba zaplanowanych przypomnień pojawia się przy pozycji Przypomnienia.
- Wybrany panel jest wyróżniony; dla paneli z listy wyróżnia się Więcej. Panel zachowuje się po odtworzeniu Activity.
- Powiadomienie o zmianach nadal otwiera Start, a powiadomienie przypomnienia otwiera jego aktualne szczegóły. Nowe menu zachowuje wyszukiwanie, kalendarz i dotychczasowe akcje paneli.

## US-HOME-03 — dostęp do kopii i częściowe błędy

Jako użytkownik chcę czytać plan dnia również bez połączenia, aby móc wrócić do pobranych informacji.

Kryteria akceptacji:

- Widok korzysta z istniejącej zaszyfrowanej kopii i lokalnych przypomnień. Zmiana dnia, menu i otwieranie listy nie wprowadzają dodatkowych żądań do Librusa ani nowego harmonogramu pracy w tle.
- Sekcje pokazują czas ostatniego udanego odczytu. Błąd odświeżenia jest widoczny obok zachowanych wpisów; nie jest przedstawiany jako brak zdarzeń.
- Plan lekcji poza dwoma tygodniami ostatniego odczytu ma ostrzeżenie o zakresie. Wpisy bez rozpoznanej daty pozostają dostępne w pełnym panelu, bez zgadywania dnia.
- Powitanie z logowaniem/demo pojawia się przy braku kopii; istniejąca kopia pozostaje od razu czytelna. Start przelicza datę po północy także przy pozostawieniu otwartej aplikacji.

Oznaczanie zadań jako zrobione jest osobnym zadaniem. Ten ekran pokazuje terminy z Librusa i nie potwierdza oddania pracy.

# LibrusApp — historie użytkownika

**Wersja referencyjna:** LibrusApp 0.1.1, kod w katalogu `librus_app/` po zmianie nazwy aplikacji.  
**Data opracowania:** 7 października 2026 r.  
**Cel:** wymagania w formie historii użytkownika, stanowiące bazę do zaprojektowania wersji mobilnej o podobnych funkcjonalnościach.

Dokument powstał na podstawie kodu, testów i README. Zawiera **59 historii użytkownika** z identyfikatorami `US-*` oraz **135 kryteriów akceptacji** z identyfikatorami `KA-*`. Historie opisują potrzeby użytkownika w formacie: **Jako [rola], chcę [cel], aby [korzyść]**. Kryteria określają warunki spełnienia historii, także błędy, ograniczenia i zachowanie offline. Każde dawne wymaganie WF-* zachowano pod odpowiadającym mu identyfikatorem KA-*.

**Zakres potwierdzony kodem:** historie dotyczą wersji referencyjnej desktopu. Nie są potwierdzeniem zgodności z każdym rzeczywistym kontem Librusa. Rozdziały 13 i 17 to kontekst danych i śledzenie implementacji, a 24 scenariusze AK-* w rozdziale 14 wspierają testy całych przepływów.

**Zakres przyszły:** rozdział 15 zawiera adaptację do mobile i 12 pytań MOB-* do rozstrzygnięcia. Nie są one zatwierdzonymi historiami ani obietnicami działania. Po decyzji należy opisać nowe lub zmienione historie z własnymi kryteriami akceptacji.

Projekt jest lokalnym, niezależnym i nieoficjalnym klientem LIBRUS Synergia. Głównym odbiorcą jest rodzic korzystający z konta Synergii otrzymanego ze szkoły. Źródłem danych jest dziennik; aplikacja nie zastępuje oficjalnego panelu ani nie prowadzi własnej dokumentacji szkolnej.

## Spis treści

1. [Zakres i pojęcia](#1-zakres-i-pojęcia)
2. [Uruchomienie, nawigacja i stany aplikacji](#2-uruchomienie-nawigacja-i-stany-aplikacji)
3. [Konto i logowanie](#3-konto-i-logowanie)
4. [Wspólne funkcje list i szczegółów](#4-wspólne-funkcje-list-i-szczegółów)
5. [Panel Przegląd](#5-panel-przegląd)
6. [Panele danych szkolnych](#6-panele-danych-szkolnych)
7. [Panel Terminarz i kalendarz](#7-panel-terminarz-i-kalendarz)
8. [Panel Przypomnienia i edytor alertów](#8-panel-przypomnienia-i-edytor-alertów)
9. [Panel Ustawienia](#9-panel-ustawienia)
10. [Synchronizacja, zmiany i powiadomienia](#10-synchronizacja-zmiany-i-powiadomienia)
11. [Dane lokalne i prywatność](#11-dane-lokalne-i-prywatność)
12. [Tryb demonstracyjny](#12-tryb-demonstracyjny)
13. [Model informacji](#13-model-informacji)
14. [Scenariusze akceptacyjne](#14-scenariusze-akceptacyjne)
15. [Przeniesienie funkcji do wersji mobilnej](#15-przeniesienie-funkcji-do-wersji-mobilnej)
16. [Funkcje poza obecnym zakresem](#16-funkcje-poza-obecnym-zakresem)
17. [Powiązanie z kodem i testami](#17-powiązanie-z-kodem-i-testami)

## 1. Zakres i pojęcia

| Pojęcie | Znaczenie |
|---|---|
| Konto | Konto Synergia z loginem i hasłem przekazanymi przez szkołę; niekoniecznie adres e-mail Konta LIBRUS |
| Sekcja danych | Oceny, Wiadomości, Ogłoszenia, Terminarz, Frekwencja lub Plan lekcji |
| Panel | Ekran dostępny w nawigacji; także Przegląd, Przypomnienia i Ustawienia |
| Wpis | Jedna ocena, wiadomość, ogłoszenie, wydarzenie, wpis frekwencji albo lekcja |
| Kopia lokalna | Ostatnie poprawnie pobrane dane każdej sekcji wraz z czasem odczytu |
| Odczyt / synchronizacja | Pobranie sekcji, walidacja, aktualizacja kopii i porównanie z poprzednim stanem |
| Nowy lub zmieniony wpis | Wpis wykryty przez porównanie identyfikatora i treści z historią, z uwzględnieniem zakresu dat |
| Nieprzeczytana wiadomość | Status pochodzący ze skrzynki Librusa; niezależny od znacznika zmiany |
| Powiadomienie o danych | Ogólny komunikat systemowy z liczbą zmian w sekcjach |
| Przypomnienie / alert własny | Jednorazowy alarm z własnym tekstem i terminem, powiązany z konkretnym wpisem |
| Tryb offline | Prezentacja wcześniej pobranej kopii; nie dowodzi fizycznego braku internetu |
| Pierwszy odczyt | Pierwsza poprawna synchronizacja danej sekcji; tworzy cichy punkt odniesienia |

Ogłoszenia i wiadomości są osobnymi źródłami. Terminarz przedstawia wydarzenia szkolne, a Plan lekcji zajęcia w kolejnych dniach. Własne przypomnienie nie tworzy ani nie zmienia wydarzenia w Librusie.

## 2. Uruchomienie, nawigacja i stany aplikacji

### 2.1. Uruchomienie

W folderze `librus-app` dostępne są `Start.cmd` dla zwykłego uruchomienia, `Demo.cmd` dla danych przykładowych i `Sprawdz-srodowisko.cmd` dla diagnostyki środowiska. Pierwsze uruchomienie przygotowuje lokalne środowisko Pythona i zależności. Minimalna wersja Pythona to 3.11 z pip i Tkinter. Wersja referencyjna nie ma instalatora EXE.

Domyślnie otwiera się **Przegląd**. Na Windows druga instancja LibrusApp nie rozpoczyna pracy i informuje, że aplikacja już działa. Minimalizacja pozostawia proces aktywny; zamknięcie głównego okna kończy pracę synchronizacji i zegara przypomnień. Nie ma usługi, ikony zasobnika ani automatycznie konfigurowanego autostartu.

### 2.2. Układ i nawigacja

Lewa nawigacja obejmuje kolejno: **Przegląd**, **Oceny**, **Wiadomości**, **Ogłoszenia**, **Terminarz**, **Frekwencja**, **Plan lekcji**, **Przypomnienia**, **Ustawienia**. Podświetlenie wskazuje aktywny panel. Przy Wiadomościach pojawia się liczba nieprzeczytanych w pobranej liście, a przy Przypomnieniach liczba zaplanowanych alertów; liczby równe zero są pomijane w etykiecie.

Nagłówek pokazuje nazwę panelu, opis jego zakresu, **Połącz konto / Zmień konto**, **Odśwież**, status operacji, ostatni odczyt i — w trybie połączonym — termin następnego odczytu. Status globalny nie zastępuje czasów odczytu poszczególnych sekcji. Dolna część nawigacji pokazuje tryb pracy i akcję **Otwórz Librusa**.

#### US-APP-01 — Nawigacja i orientacja w aplikacji

**Historia:** Jako rodzic chcę przechodzić między panelami i widzieć aktualny stan aplikacji, aby szybko odnajdywać potrzebne dane i oceniać ich aktualność.

**Kryteria akceptacji:**

- **KA-APP-01:** Aplikacja udostępnia wszystkie dziewięć paneli i zachowuje dane przy przechodzeniu między nimi bez ponownego pobierania na samą zmianę panelu.
- **KA-APP-02:** Aplikacja wskazuje aktywny panel, tryb pracy, status wykonywanej operacji i czasy odczytów.

#### US-APP-02 — Dostęp do głównych działań

**Historia:** Jako użytkownik desktopu chcę mieć dostęp do logowania, odświeżania i oficjalnego Librusa z dowolnego panelu, aby wykonywać te działania bez szukania ich w ustawieniach.

**Kryteria akceptacji:**

- **KA-APP-03:** Aplikacja udostępnia globalne połączenie lub zmianę konta, ręczne odświeżenie oraz przejście do oficjalnego Librusa.
- **KA-APP-08:** Aplikacja udostępnia desktopowe skróty: `Ctrl+R` — odświeżenie, `Ctrl+L` — logowanie, `Ctrl+F` — fokus i zaznaczenie pola wyszukiwania w panelu listy.

#### US-APP-03 — Przeglądanie podczas operacji i informacja o błędach

**Historia:** Jako rodzic chcę przeglądać dostępne dane podczas pobierania oraz otrzymywać jasne informacje o postępie i błędach, aby wiedzieć, kiedy poczekać i którym informacjom mogę ufać.

**Kryteria akceptacji:**

- **KA-APP-04:** Aplikacja prezentuje globalne błędy połączenia i zapisu oddzielnie od błędów poszczególnych sekcji. Krótkie komunikaty o działaniach są widoczne przez około 10 sekund.
- **KA-APP-05:** Podczas operacji pokazuje stan zajętości, wyłącza przyciski logowania i odświeżania oraz akcje pobierania treści i modyfikacji przypomnień. Ponowna operacja wymagająca workera komunikuje konieczność oczekiwania.
- **KA-APP-06:** Aplikacja pozwala nadal przeglądać i filtrować dostępne dane podczas pracy sieciowej. Zajętość nie zatrzymuje sprawdzania terminów alertów.

#### US-APP-04 — Kontrola działania aplikacji desktopowej

**Historia:** Jako użytkownik Windows chcę wiedzieć, kiedy aplikacja pozostaje aktywna, i uniknąć jej podwójnego uruchomienia, aby synchronizacja i przypomnienia działały w przewidywalny sposób.

**Kryteria akceptacji:**

- **KA-APP-07:** Na Windows ograniczać uruchomienie do jednej instancji LibrusApp. Zamknięcie okna zatrzymuje działanie; minimalizacja go nie zatrzymuje.

### 2.3. Stany widoczne dla użytkownika

| Stan | Etykieta / zachowanie | Działania |
|---|---|---|
| Brak połączenia (`idle`) | „Konto niepołączone”; przy braku danych ekran powitalny | Połączenie konta lub demo; odświeżenie nie ma danych do pobrania |
| Połączenie (`live`) | „Konto połączone”; może obejmować sukces części sekcji | Przeglądanie, odświeżanie, zmiana konta, przypomnienia |
| Kopia (`offline`) | „Dane z lokalnej kopii” i ostrzeżenie o aktualności | Przeglądanie kopii i alerty; bez sesji pobieranie wymaga logowania |
| Demo (`demo`) | „Dane demonstracyjne” i wyraźny baner | Przykładowe dane, symulacja zmiany, tymczasowe przypomnienia |
| Zajętość (`busy`) | Nakładka logiczna na powyższe stany, np. „Pobieranie…” | Przeglądanie dostępne; konkurencyjna operacja odrzucana |

Stan `live` nie gwarantuje aktualności każdej sekcji. Stan `offline` może wynikać także z wygaśnięcia sesji albo całkowicie nieudanego odczytu. Komunikat o zapisanej kopii wolno pokazać tylko wtedy, gdy istnieje poprawnie zainicjalizowana sekcja.

## 3. Konto i logowanie

### 3.1. Formularz „Połącz konto Synergia”

Formularz zawiera login, maskowane hasło, domyślnie wyłączone **Zapamiętaj konto na tym komputerze**, **Anuluj** oraz **Połącz i pobierz dane**. Wyjaśnia różnicę między loginem Synergii a e-mailem Konta LIBRUS. Przy braku pól wskazuje błąd i ustawia fokus. Enter zatwierdza formularz; Escape lub zamknięcie go anuluje. Hasło jest czyszczone z formularza po zatwierdzeniu i anulowaniu. Drugie wywołanie podnosi już otwarty formularz.

#### US-KON-01 — Świadome połączenie konta

**Historia:** Jako rodzic chcę połączyć konto Synergia loginem i hasłem oraz sam zdecydować o ich zapamiętaniu, aby uzyskać dostęp do dziennika bez niechcianego utrwalania hasła.

**Kryteria akceptacji:**

- **KA-KON-01:** Formularz przyjmuje login Synergii i hasło; usuwa skrajne spacje loginu, wymaga obu pól i maskuje hasło.
- **KA-KON-02:** Aplikacja udostępnia anulowanie oraz zatwierdzenie; nie zapamiętuje konta bez świadomego wyboru użytkownika.

#### US-KON-02 — Pobranie danych po logowaniu i zrozumienie odmowy

**Historia:** Jako rodzic chcę po poprawnym logowaniu otrzymać dane dziennika, a przy odmowie zobaczyć bezpieczny opis problemu, aby wiedzieć, czy konto zostało połączone i jaki krok wymaga poprawy.

**Kryteria akceptacji:**

- **KA-KON-03:** Po udanym logowaniu uruchamia odczyt wszystkich sześciu sekcji. Samo logowanie i odczyt sekcji mogą mieć różne wyniki.
- **KA-KON-09:** Aplikacja przedstawia bezpieczny komunikat błędu, etap i kod HTTP, gdy są dostępne; nie prezentuje loginu, hasła, cookies ani surowej odpowiedzi serwera.

#### US-KON-03 — Powrót do zapisanej kopii

**Historia:** Jako rodzic chcę po ponownym uruchomieniu zobaczyć zapisane dane i ustawienia, aby kontynuować pracę także bez ponownego wpisywania hasła, jeśli wybrałem jego zapamiętanie.

**Kryteria akceptacji:**

- **KA-KON-04:** Przy starcie odtwarza kopię, ustawienia i przypomnienia. Jeżeli istnieją poprawne zapamiętane dane zgodne z zapisanym profilem, podejmuje logowanie i synchronizację automatycznie.
- **KA-KON-05:** Bez zapamiętanego hasła aplikacja pozwala na przeglądanie kopii, ale wymaga połączenia konta do pobierania nowych danych.

#### US-KON-04 — Zmiana konta bez mieszania danych

**Historia:** Jako rodzic chcę móc połączyć inne konto lub wrócić do konta po obejrzeniu demo, aby oglądać wyłącznie dane i przypomnienia właściwego profilu.

**Kryteria akceptacji:**

- **KA-KON-06:** Aplikacja pozwala zmienić aktywne konto. Nie łączy danych ani alertów różnych profili; nowy profil zaczyna od własnego punktu odniesienia.
- **KA-KON-07:** Po przejściu z demo odtwarza wcześniejszy zapis, jeśli odpowiada logowanemu profilowi. Obsługuje jeden zapis aktywnego profilu, bez obietnicy archiwizacji wielu kont.

#### US-KON-05 — Cofnięcie zgody na zapamiętanie hasła

**Historia:** Jako rodzic chcę wycofać zapamiętywanie konta przy kolejnym logowaniu, aby wcześniejsze hasło zostało usunięte również wtedy, gdy nowa próba połączenia się nie uda.

**Kryteria akceptacji:**

- **KA-KON-08:** Odznaczenie zapamiętywania przy kolejnym połączeniu usuwa wcześniej zapisane hasło także przy nieudanej próbie logowania; nieudane usunięcie musi być jawne.

#### US-KON-06 — Wygaśnięcie sesji bez utraty kopii

**Historia:** Jako rodzic chcę po wygaśnięciu sesji zachować ostatnie dane i otrzymać prośbę o ponowne połączenie, aby kontynuować przeglądanie bez niekontrolowanych prób logowania.

**Kryteria akceptacji:**

- **KA-KON-10:** Po wygaśnięciu głównej sesji zatrzymuje synchronizację i prosi o ponowne połączenie, zachowując poprawną kopię. Nie prowadzi pętli ponownego wysyłania hasła.

### 3.2. Granice obsługi kont

Obecna wersja nie obsługuje formularzy kodu 2FA, CAPTCHA ani interaktywnego logowania przez przeglądarkę. Przy wymaganym dodatkowym potwierdzeniu należy użyć oficjalnego Librusa. Nie ma osobnego zarządzania dziećmi, przełącznika uczeń/rodzic ani katalogu wielu zapisanych kont. **Zmień konto** jest ponownym logowaniem, a nie wyborem z listy profili.

## 4. Wspólne funkcje list i szczegółów

Z funkcji wspólnego widoku korzystają Oceny, Wiadomości, Ogłoszenia, Terminarz, Frekwencja, Plan lekcji i Przypomnienia, z opisanymi dalej wyjątkami. Standardowy panel zawiera wyszukiwanie, **Wyczyść**, liczbę widocznych pozycji, czas odczytu, ewentualny błąd, przewijaną listę i podgląd szczegółów. Desktop pozwala regulować proporcje listy i podglądu separatorem; w Terminarzu szczegóły są obok listy, w pozostałych panelach poniżej.

### 4.1. Wyszukiwanie i sortowanie

#### US-LIS-01 — Wyszukanie potrzebnego wpisu

**Historia:** Jako rodzic chcę na bieżąco wyszukiwać wpisy w pobranych danych i łatwo wyczyścić filtr, aby odnaleźć interesującą mnie informację bez dodatkowego pobierania.

**Kryteria akceptacji:**

- **KA-LIS-01:** Aplikacja filtruje lokalnie po każdej zmianie zapytania, bez zapytania sieciowego i bez rozróżniania wielkości liter; usuwa skrajne spacje zapytania.
- **KA-LIS-02:** Dla wpisów szkolnych wyszukuje podciąg w tytule, informacjach dodatkowych, dacie źródłowej, dostępnym opisie i rodzaju wpisu. Nie pobiera brakującej treści wiadomości na potrzeby wyszukiwania.
- **KA-LIS-03:** Akcja „Wyczyść” usuwa zapytanie. Licznik „Pozycji” odpowiada wynikom po filtrach; w kalendarzu uwzględnia także dzień lub miesiąc.

#### US-LIS-02 — Uporządkowanie listy

**Historia:** Jako rodzic chcę zmienić kolejność wpisów według tytułu, informacji lub daty, aby łatwiej porównywać i odnajdywać dane.

**Kryteria akceptacji:**

- **KA-LIS-04:** Kliknięcie nagłówka sortuje po tytule, informacjach lub dacie; ponowne kliknięcie tej samej kolumny odwraca kierunek. Daty możliwe do rozpoznania są sortowane chronologicznie.
- **KA-LIS-05:** Aplikacja domyślnie pokazuje najnowsze wpisy jako pierwsze; wyjątki to rosnący Terminarz i Plan lekcji oraz kolejność status/termin w Przypomnieniach. Wpisy bez rozpoznawalnej daty pozostają dostępne.

#### US-LIS-03 — Zachowanie wyboru po odświeżeniu widoku

**Historia:** Jako rodzic chcę zachować zaznaczony wpis przy aktualizacji listy, jeśli nadal jest dostępny, aby móc czytać jego aktualne szczegóły bez ponownego wybierania.

**Kryteria akceptacji:**

- **KA-LIS-06:** W miarę dostępności wpisu zachowuje zaznaczenie po ponownym renderowaniu i aktualizuje jego szczegóły; po zniknięciu z wyników czyści podgląd.

Wyszukiwanie używa tekstu daty zapisanego we wpisie, zwykle ISO, a nie wyłącznie daty sformatowanej na ekranie. Brak wyników nie jest dowodem braku danych w źródle. Nie ma zaimplementowanych osobnych filtrów przedmiotu, nauczyciela, semestru ani statusu wiadomości.

### 4.2. Zaznaczenie, szczegóły i komunikaty

#### US-LIS-04 — Podgląd szczegółów i przejście do źródła

**Historia:** Jako rodzic chcę otworzyć szczegóły wybranego wpisu i przejść do jego oficjalnej sekcji w Librusie, aby przeczytać dostępne informacje i wykonać działania oferowane przez źródło.

**Kryteria akceptacji:**

- **KA-LIS-07:** Wybranie jednego wiersza pokazuje tytuł, informacje dodatkowe, datę i dostępny opis w panelu tylko do odczytu. Bez wyboru wyświetla instrukcję i wyłącza akcje zależne od wpisu.
- **KA-LIS-08:** Dwuklik w wierszu lub Enter otwiera przewijane okno szczegółów. Okno ma „Zamknij” i obsługuje Escape. Dla wiadomości jest to podgląd metadanych, bez pobrania treści.
- **KA-LIS-12:** „Otwórz w Librusie” otwiera stałą oficjalną stronę odpowiedniej sekcji w przeglądarce. Nie gwarantuje otwarcia dokładnego wpisu ani współdzielenia sesji z aplikacją.

#### US-LIS-05 — Rozpoznanie nieprzeczytanych wiadomości i zmian

**Historia:** Jako rodzic chcę odróżniać nieprzeczytane wiadomości od nowych lub zmienionych wpisów, aby wiedzieć, na co zwrócić uwagę.

**Kryteria akceptacji:**

- **KA-LIS-09:** Aplikacja pokazuje `●` i pogrubienie dla nieprzeczytanych wiadomości oraz `+` i wyróżnienie dla nowych lub zmienionych wpisów. Znacznik nieprzeczytania może mieć pierwszeństwo przed `+`, ale wyróżnienie zmiany pozostaje.

#### US-LIS-06 — Zrozumienie pustej listy i starszych danych

**Historia:** Jako rodzic chcę rozróżnić brak wyników, brak odczytu i awarię sekcji oraz widzieć czas poprawnego odczytu, aby nie uznać niepełnych danych za pusty dziennik.

**Kryteria akceptacji:**

- **KA-LIS-10:** Aplikacja rozróżnia brak konta, sekcję jeszcze niepobraną, pierwszy nieudany odczyt, poprawnie pobraną pustą listę oraz brak wyników filtracji.
- **KA-LIS-11:** Dla sekcji szkolnej pokazuje czas jej ostatniego poprawnego odczytu. Błąd odświeżenia pokazuje wraz ze starszymi danymi, bez zastępowania ich pustą listą.

Znacznik zmiany opisuje wynik ostatniego porównania, a nie trwałe „nieobejrzane przez użytkownika”. Kolejny odczyt bez zmian może go usunąć; ponowne uruchomienie nie odtwarza tych wyróżnień. Nie ma historii zmian ani przycisku „oznacz wszystkie zmiany jako obejrzane”.

## 5. Panel Przegląd

Panel służy do szybkiej oceny sytuacji. Bez konta i danych pokazuje kartę **Wszystko w jednym miejscu** z akcjami **Połącz konto Synergia** i **Zobacz demo**. Ma trzy karty liczników oraz podglądy **Ostatnie oceny** i **Najbliższe wydarzenia**. Podglądy i przyciski **Zobacz** przenoszą do odpowiedniej sekcji; nie wybierają konkretnego wpisu.

#### US-PRZ-01 — Szybki przegląd sytuacji szkolnej

**Historia:** Jako rodzic chcę zobaczyć liczbę nieprzeczytanych wiadomości, pobranych ocen i nadchodzących wydarzeń, aby szybko ocenić, co wymaga mojej uwagi.

**Kryteria akceptacji:**

- **KA-PRZ-01:** Aplikacja pokazuje liczbę nieprzeczytanych wiadomości wyłącznie w pobranej liście, bez sugerowania kompletnego stanu skrzynki.
- **KA-PRZ-02:** Aplikacja pokazuje liczbę wszystkich pobranych ocen, bez obliczania średniej ani prognozy.
- **KA-PRZ-03:** Aplikacja pokazuje liczbę wydarzeń od dzisiaj; wpisy z nierozpoznaną datą pozostają w zestawie nadchodzących i nie mogą być cicho pominięte.
- **KA-PRZ-04:** Przed pierwszym odczytem liczniki pokazują „—” i informację o oczekiwaniu; poprawny odczyt pustej sekcji pozwala pokazać zero.

#### US-PRZ-02 — Przejście z podsumowania do danych

**Historia:** Jako rodzic chcę zobaczyć ostatnie oceny, najbliższe wydarzenia i sekcje z błędami oraz przejść do pełnych list, aby sprawdzić szczegóły i aktualność informacji.

**Kryteria akceptacji:**

- **KA-PRZ-05:** Aplikacja pokazuje do czterech ostatnich ocen i do czterech najbliższych wydarzeń z tytułem, informacjami i datą oraz przejście do pełnej sekcji.
- **KA-PRZ-06:** Aplikacja wskazuje sekcje z błędami, w tym połączenie lub zapis, i ostrzega, że prezentują poprzedni odczyt.

Przegląd nie ma osobnych kart podglądu ogłoszeń, frekwencji, planu lekcji ani listy przypomnień. Liczniki Przeglądu obejmują całą pobraną kopię, niezależnie od wyszukiwania ustawionego w innych panelach.

## 6. Panele danych szkolnych

### 6.1. Oceny

Lista pokazuje **Pozycja**, **Informacje**, **Data**. Tytuł łączy przedmiot i wartość oceny; informacje dodatkowe to kategoria lub nauczyciel. Szczegóły zawierają udostępnione przez źródło: przedmiot, ocenę, opis, kategorię, nauczyciela i semestr, a dla ocen numerycznych również wagę oraz informację, czy wliczają się do średniej.

#### US-OCE-01 — Przeglądanie ocen zgodnych z dziennikiem

**Historia:** Jako rodzic chcę przeglądać oceny numeryczne i opisowe wraz z ich metadanymi i zmianami, aby znać wyniki dziecka zgodnie z informacjami szkoły.

**Kryteria akceptacji:**

- **KA-OCE-01:** Aplikacja odczytuje oceny numeryczne i opisowe dla wszystkich semestrów udostępnionych w bieżących danych szkoły i przedstawia je we wspólnej liście.
- **KA-OCE-02:** Aplikacja zachowuje oryginalną wartość, w tym `+` i `−`; nie przelicza jej według własnych reguł. Pokazuje dostępne metadane w szczegółach.
- **KA-OCE-03:** Aplikacja umożliwia wyszukiwanie, sortowanie, podgląd i przejście do oficjalnej sekcji ocen.
- **KA-OCE-04:** Aplikacja wykrywa nową ocenę lub zmianę istniejącej oceny na podstawie tożsamości i treści wpisu.

Aplikacja nie prezentuje własnej średniej, rankingów, trendów ani ocen przewidywanych. Waga to informacja ze źródła, a nie podstawowy składnik własnego kalkulatora. W tym panelu nie tworzy się przypomnień.

### 6.2. Wiadomości

Lista pokazuje **Temat**, **Nadawca**, **Data** i status nieprzeczytania. Podgląd zawiera nadawcę, datę, informację o załącznikach, jeżeli jest dostępna, i instrukcję pobrania treści. Dostępne akcje to **Pobierz treść (oznaczy jako przeczytaną)**, **Przypomnij mi…** oraz **Otwórz w Librusie**.

#### US-WIA-01 — Przejrzenie skrzynki bez odczytywania treści

**Historia:** Jako rodzic chcę zobaczyć nagłówki, nadawców i statusy najnowszych wiadomości bez automatycznego pobierania treści, aby zdecydować, które wiadomości otworzyć.

**Kryteria akceptacji:**

- **KA-WIA-01:** Aplikacja pobiera maksymalnie 200 najnowszych nagłówków skrzynki, w aktualnej integracji maksymalnie cztery strony po 50.
- **KA-WIA-02:** Aplikacja pokazuje temat, nadawcę, datę i status odczytu, a jeśli źródło sygnalizuje załącznik — stosowną informację. Brak tematu ma bezpieczną etykietę zastępczą.
- **KA-WIA-03:** Synchronizacja, zaznaczenie, dwuklik, otwarcie szczegółów i wyszukiwanie nie pobierają pełnej treści. Aplikacja nie wywołuje przy synchronizacji jawnej akcji oznaczania wiadomości jako przeczytanej.

#### US-WIA-02 — Świadome pobranie treści wiadomości

**Historia:** Jako rodzic chcę samodzielnie pobrać treść wybranej wiadomości z informacją o możliwej zmianie statusu odczytu, aby przeczytać ją w aplikacji i kontrolować skutki tej akcji.

**Kryteria akceptacji:**

- **KA-WIA-04:** Pobranie treści wymaga jawnej akcji użytkownika z informacją o możliwej zmianie statusu w Librusie. Przycisk pozostaje widoczny niezależnie od rozmiaru podglądu i wymaga zaznaczenia wpisu.
- **KA-WIA-05:** Wyświetla pobraną treść w osobnym przewijanym oknie jako zwykły tekst, bez wykonywania HTML, skryptów i pobierania zewnętrznych obrazów.
- **KA-WIA-06:** Po udanym pobraniu ustawia lokalny status wiadomości na przeczytaną i aktualizuje liczniki. Nie zapisuje pełnego tekstu wiadomości w trwałej kopii.

#### US-WIA-03 — Dostęp do nagłówków przy problemach ze skrzynką

**Historia:** Jako rodzic chcę nadal oglądać zapisane nagłówki i inne sekcje, gdy skrzynka jest niedostępna, aby awaria wiadomości nie blokowała całego dziennika.

**Kryteria akceptacji:**

- **KA-WIA-07:** Bez aktywnej sesji pozwalać przeglądać zapisane nagłówki, lecz przy pobieraniu treści prosi o połączenie konta.
- **KA-WIA-08:** Błąd treści lub skrzynki nie usuwa zapisanych nagłówków; pokazuje błąd. Utrata autoryzacji skrzynki przy synchronizacji nie blokuje innych sekcji i wyłącza automatyczne ponawianie tej sekcji do ponownego połączenia konta.

#### US-WIA-04 — Dalsze działania związane z wiadomością

**Historia:** Jako rodzic chcę zaplanować przypomnienie o wiadomości lub otworzyć oficjalną skrzynkę, aby wrócić do ważnej sprawy i obsłużyć załączniki.

**Kryteria akceptacji:**

- **KA-WIA-09:** Aplikacja umożliwia tworzenie własnego przypomnienia dla zaznaczonej wiadomości, także na podstawie jej nagłówka.
- **KA-WIA-10:** Załączniki i operacje wykraczające poza odczyt obsługuje przez przejście do oficjalnego Librusa.

Pełna treść nie jest później przeszukiwana w głównej liście. Aplikacja nie odpowiada na wiadomości, nie wysyła ich, nie usuwa i nie udostępnia ręcznego przełączania odczytana/nieodczytana. Licznik nieprzeczytanych nie obejmuje wiadomości poza limitem pobranych nagłówków.

### 6.3. Ogłoszenia

Lista pokazuje **Tytuł**, **Autor**, **Data**; zaznaczenie od razu prezentuje pobraną treść. Dostępne są **Przypomnij mi…** i **Otwórz w Librusie**. Nie ma osobnego przycisku pobierania treści, bo jest pobierana razem z listą.

#### US-OGL-01 — Czytanie i obserwowanie ogłoszeń szkoły

**Historia:** Jako rodzic chcę czytać, wyszukiwać i obserwować zmiany ogłoszeń oraz ustawiać dla nich przypomnienia, aby nie przeoczyć informacji organizacyjnych.

**Kryteria akceptacji:**

- **KA-OGL-01:** Aplikacja pobiera ogłoszenia szkoły wraz z tytułem, autorem, datą i treścią oraz udostępnia treść w kopii lokalnej.
- **KA-OGL-02:** Aplikacja umożliwia wyszukiwanie także w treści, sortowanie i podgląd szczegółów bez dodatkowego odczytu sieciowego.
- **KA-OGL-03:** Aplikacja wykrywa nowe ogłoszenie oraz zmianę treści istniejącego; pierwszy odczyt jest cichy, a awaria zachowuje poprzedni odczyt.
- **KA-OGL-04:** Aplikacja umożliwia zaplanowanie własnego przypomnienia i przejście do oficjalnej sekcji ogłoszeń.

Źródło nie zapewnia identyfikatora ogłoszenia. Integracja rozpoznaje je po tytule, autorze i dacie. Zmiana tych pól może być potraktowana jako nowy wpis; zmiana samej treści jest zmianą istniejącego. Nie ma statusu „przeczytane ogłoszenie”.

### 6.4. Frekwencja

Tytuł wpisu to rodzaj lub symbol frekwencji, informacje dodatkowe obejmują przedmiot i numer lekcji, a data wskazuje dzień. Szczegóły mogą zawierać rodzaj, symbol, przedmiot, lekcję, nauczyciela i temat.

#### US-FRE-01 — Przeglądanie frekwencji

**Historia:** Jako rodzic chcę widzieć wpisy frekwencji i ich zmiany w postaci udostępnionej przez szkołę, aby sprawdzić obecności i powiązane informacje o lekcjach.

**Kryteria akceptacji:**

- **KA-FRE-01:** Aplikacja pobiera wpisy frekwencji udostępnione w obu semestrach i zachowuje ich oznaczenia ze źródła.
- **KA-FRE-02:** Aplikacja umożliwia wyszukiwanie, sortowanie, podgląd oraz przejście do oficjalnej sekcji frekwencji.
- **KA-FRE-03:** Uczestniczy we wspólnym wykrywaniu nowych i zmienionych wpisów; zachowuje kopię w razie błędu.

Nie ma procentu obecności, własnej interpretacji symboli, usprawiedliwiania ani tworzenia przypomnienia z tego panelu.

### 6.5. Plan lekcji

Lista przedstawia lekcje chronologicznie. Tytuł to przedmiot lub **Zmiana w planie**, informacje to numer lekcji oraz nauczyciel i sala, a data może obejmować godzinę rozpoczęcia. Szczegóły pokazują nauczyciela i salę, początek, koniec oraz informacje o zmianach zwrócone przez źródło, np. zastępstwo lub odwołanie.

#### US-PLA-01 — Przeglądanie planu i zmian lekcji

**Historia:** Jako rodzic chcę znać plan bieżącego i kolejnego tygodnia wraz z zastępstwami i odwołaniami, aby przygotować się do nadchodzących zajęć.

**Kryteria akceptacji:**

- **KA-PLA-01:** Aplikacja pobiera plan na bieżący tydzień rozpoczynający się w poniedziałek i następny tydzień.
- **KA-PLA-02:** Aplikacja pokazuje przedmiot, datę i dostępną godzinę, numer lekcji, nauczyciela, salę oraz szczegóły zmian. Zachowuje odwołania także przy pustej nazwie przedmiotu, jeśli istnieją informacje o zmianie.
- **KA-PLA-03:** Aplikacja umożliwia wyszukiwanie, sortowanie, podgląd i przejście do oficjalnego planu.
- **KA-PLA-04:** Aplikacja wykrywa zmiany przyszłych lekcji w znanym zakresie, w tym zniknięcie lekcji, według zasad z rozdziału 10.

Wersja referencyjna ma listę, a nie siatkę godzinową. Nie ma nawigacji po dowolnych tygodniach, samodzielnej edycji planu ani przypomnień z lekcji.

## 7. Panel Terminarz i kalendarz

### 7.1. Dane i widoki

Integracja pobiera bieżący i następny miesiąc. Wpis obejmuje tytuł wydarzenia, przedmiot lub informacje dodatkowe, datę oraz opis i godzinę, jeśli są dostępne. Rzeczywista integracja zapisuje datę dnia w polu daty; godzina wydarzenia może być tylko częścią opisu. Nie wolno zakładać, że każdemu wydarzeniu przypisano dokładny czas.

Domyślny widok to **Kalendarz + lista**. Kalendarz jest nad listą, szczegóły obok niej. Widok **Lista** ukrywa kalendarz i pokazuje wszystkie pobrane wydarzenia pasujące do wyszukiwania, bez filtra miesiąca. **Szczegóły wydarzenia** otwiera osobne okno.

### 7.2. Nawigacja i filtrowanie

#### US-TER-01 — Przeglądanie terminarza jako kalendarza lub listy

**Historia:** Jako rodzic chcę oglądać wydarzenia w kalendarzu miesięcznym z listą albo w samej liście, aby wybrać wygodny sposób przeglądania terminów.

**Kryteria akceptacji:**

- **KA-TER-01:** Aplikacja pobiera wydarzenia bieżącego i następnego miesiąca, także przy zmianie roku, i zachowuje ostatnią poprawną kopię.
- **KA-TER-02:** Aplikacja domyślnie pokazuje miesięczny kalendarz z listą pod nim; umożliwia przełączenie na samą listę i powrót.
- **KA-TER-03:** Wyświetla polską nazwę miesiąca i rok, siedem kolumn od poniedziałku do niedzieli, prawidłową liczbę dni, lata przestępne i dni sąsiednich miesięcy.

#### US-TER-02 — Wybór miesiąca i dnia

**Historia:** Jako rodzic chcę przechodzić między miesiącami, wybrać dzień i szybko wrócić do dzisiaj lub całego miesiąca, aby zawęzić terminarz do interesującego mnie okresu.

**Kryteria akceptacji:**

- **KA-TER-04:** Strzałki zmieniają miesiąc i usuwają wybór dnia. Nawigacja jest lokalna; nie pobiera automatycznie dodatkowych miesięcy.
- **KA-TER-06:** Kliknięcie dnia wybiera go i ogranicza listę; ponowne kliknięcie tego dnia usuwa filtr. Kliknięcie dnia sąsiedniego miesiąca przechodzi do jego miesiąca.
- **KA-TER-07:** „Cały miesiąc” usuwa filtr dnia; „Dzisiaj” ustawia bieżący miesiąc i filtr aktualnego dnia. Zmiana trybu Lista/Kalendarz usuwa wybór dnia.
- **KA-TER-08:** W widoku miesiąca bez wybranego dnia pokazuje także wpisy z nierozpoznaną datą, żeby pozostały dostępne; nie umieszcza ich w komórkach kalendarza.

#### US-TER-03 — Wyszukiwanie wydarzeń w kalendarzu

**Historia:** Jako rodzic chcę, aby wyszukiwanie jednocześnie zmieniało listę i oznaczenia wydarzeń w dniach, aby widzieć, kiedy występują pasujące sprawy.

**Kryteria akceptacji:**

- **KA-TER-05:** Przy dniu pokazuje liczbę wydarzeń pasujących do wyszukiwania, wyróżnia dni z wydarzeniami, dzisiaj oznacza `★`, a zmiany `+`.
- **KA-TER-10:** Wyszukiwanie ma jednocześnie zmieniać listę i liczniki wydarzeń w dniach. Pusta lista ma odnosić komunikat do wybranego dnia/miesiąca i zapytania.

#### US-TER-04 — Ocena kompletności miesiąca

**Historia:** Jako rodzic chcę wiedzieć, czy wybrany miesiąc mieści się w zakresie pobranych danych, aby nie pomylić braku odczytu z brakiem wydarzeń.

**Kryteria akceptacji:**

- **KA-TER-09:** Poza bieżącym i kolejnym miesiącem względem czasu ostatniego odczytu ostrzega o możliwej niepełności danych. Pusty widok nie oznacza kompletnego odczytu odległego miesiąca.

#### US-TER-05 — Szczegóły wydarzenia i własny alert

**Historia:** Jako rodzic chcę otworzyć szczegóły wydarzenia i ręcznie ustawić przypomnienie, aby przygotować się do niego we właściwym czasie.

**Kryteria akceptacji:**

- **KA-TER-11:** Zaznaczenie pokazuje szczegóły i aktywuje „Szczegóły wydarzenia”, „Przypomnij mi…” oraz przejście do oficjalnego terminarza. Dwuklik lub Enter otwiera szczegóły.
- **KA-TER-12:** Aplikacja umożliwia ręczne podanie terminu własnego przypomnienia dla wydarzenia. Nie wylicza automatycznie alarmu przed godziną wydarzenia.

Przełącznik widoku, miesiąc, zaznaczony dzień i zapytanie są stanem interfejsu, bez trwałego zapisu w ustawieniach. Nie ma eksportu kalendarza ani integracji z kalendarzem urządzenia.

## 8. Panel Przypomnienia i edytor alertów

### 8.1. Utworzenie przypomnienia

W Ogłoszeniach, Terminarzu lub Wiadomościach zaznacz wpis i wybierz **Przypomnij mi…**. Formularz wyświetla sekcję i tytuł źródła oraz pola własnego tekstu, daty `dd.mm.rrrr`, godziny `gg:mm` i **Pokaż treść w powiadomieniu Windows**. Ma akcje **Za 15 minut**, **Za godzinę**, **Jutro 09:00**, **Anuluj** i **Zapisz przypomnienie**.

Domyślny tekst pochodzi z tytułu wpisu, maksymalnie 200 znaków. Domyślny termin to około godzina od otwarcia edytora, z dokładnością formularza do minuty. Pokazywanie własnego tekstu w Windows jest domyślnie włączone. W edycji formularz odtwarza zapisane wartości. Nie ma tworzenia alertu bez wpisu źródłowego ani przypomnień cyklicznych.

#### US-REM-01 — Zaplanowanie własnego przypomnienia

**Historia:** Jako rodzic chcę dodać własny tekst i przyszły termin alertu dla wiadomości, ogłoszenia lub wydarzenia, aby przypomnieć sobie o ważnej sprawie.

**Kryteria akceptacji:**

- **KA-REM-01:** Aplikacja umożliwia tworzenie alertów tylko z wiadomości, ogłoszeń i wydarzeń terminarza; wymaga istniejącego zaznaczonego wpisu przy utworzeniu. Możliwe są różne alerty dla tego samego wpisu.
- **KA-REM-02:** Aplikacja pozwala podać własny tekst o długości 1–200 znaków po usunięciu skrajnych spacji.
- **KA-REM-03:** Formularz wymaga poprawnej daty i godziny w przyszłości, interpretowanej według czasu lokalnego urządzenia. Odrzuca nieistniejący czas lokalny przy zmianie czasu; zapisuje termin z offsetem.
- **KA-REM-04:** Aplikacja udostępnia wymienione trzy skróty terminu, domyślne wartości oraz anulowanie bez zmiany danych.

#### US-REM-02 — Prywatność tekstu alertu

**Historia:** Jako rodzic chcę zdecydować, czy systemowe powiadomienie ujawni tekst mojego przypomnienia, aby chronić prywatność osób, których sprawa dotyczy.

**Kryteria akceptacji:**

- **KA-REM-05:** Użytkownik wybiera, czy baner systemowy zawiera własny tekst. Przy ukryciu tekstu aplikacja wysyła komunikat ogólny; okno w aplikacji nadal pokazuje pełne przypomnienie.

#### US-REM-03 — Pewność zapisania przypomnienia

**Historia:** Jako rodzic chcę otrzymać informację o błędnych danych lub nieudanym zapisie alertu, aby nie polegać na przypomnieniu, które nie zostało zapisane.

**Kryteria akceptacji:**

- **KA-REM-06:** Aplikacja pokazuje walidację w formularzu; zamyka formularz po rozpoczęciu poprawnego zapisu. Późniejszy sukces lub błąd zapisu komunikuje w aplikacji.
- **KA-REM-07:** Przy błędzie trwałego zapisu wycofuje utworzenie, edycję lub usunięcie alertu i zgłasza, że zmiana nie została zapisana.

### 8.2. Zarządzanie w panelu

Lista ma kolumny **Treść przypomnienia**, **Sekcja / status**, **Termin**. Domyślnie zaplanowane alerty poprzedzają wykonane, a każda grupa jest uporządkowana rosnąco według terminu. Dostępne są wyszukiwanie, sortowanie, **Zmień…**, **Usuń** i **Pokaż wpis**.

| Status | Znaczenie |
|---|---|
| Zaplanowane | Termin oczekuje na wykonanie (`pending`) |
| Wykonane | Alert uruchomiono, a system przyjął powiadomienie (`fired`, `system_sent=true`) |
| Wykonane w aplikacji | Alert uruchomiono, ale system nie przyjął powiadomienia (`fired`, `system_sent=false`) |

„Wykonane” nie dowodzi wyświetlenia banera ani zapoznania się użytkownika z jego treścią. Okno przypomnienia to komunikat, nie proces potwierdzania wykonania zadania.

#### US-REM-04 — Przegląd zaplanowanych i wykonanych alertów

**Historia:** Jako rodzic chcę przeglądać i wyszukiwać przypomnienia wraz z terminami, źródłami i statusami, aby wiedzieć, które jeszcze oczekują, a które już uruchomiono.

**Kryteria akceptacji:**

- **KA-REM-08:** Aplikacja pokazuje zaplanowane i wykonane alerty, liczbę zaplanowanych, termin, status, sekcję źródłową i treść.
- **KA-REM-09:** W szczegółach pokazuje tytuł źródła, termin, status, widoczność tekstu w systemie i czas uruchomienia, jeśli istnieje. Wyszukiwanie obejmuje te informacje.

#### US-REM-05 — Zmiana lub usunięcie alertu

**Historia:** Jako rodzic chcę zmienić treść lub termin przypomnienia albo je usunąć, aby dostosować plan do bieżących potrzeb bez zmieniania danych w szkole.

**Kryteria akceptacji:**

- **KA-REM-10:** Edycja zmienia tekst, termin i widoczność w systemie. Poprawna edycja wykonanego alertu ustawia go ponownie jako zaplanowany i czyści informację o wykonaniu.
- **KA-REM-11:** Aplikacja usuwa wybrany alert; obecny desktop nie ma dodatkowego potwierdzenia dla pojedynczego alertu. Nie usuwa danych wpisu źródłowego.

#### US-REM-06 — Powrót do źródła przypomnienia

**Historia:** Jako rodzic chcę wrócić z przypomnienia do jego wpisu źródłowego i zachować alert, jeśli wpis zniknie, aby nadal rozumieć sprawę, o której mam pamiętać.

**Kryteria akceptacji:**

- **KA-REM-12:** „Pokaż wpis” przechodzi do źródłowej sekcji, czyści wyszukiwanie, a dla terminarza włącza Listę; jeśli wpis istnieje, zaznacza i przewija do niego.
- **KA-REM-13:** Jeśli wpis zniknął z pobranej listy, zachowuje alert i tytuł źródła oraz informuje o niedostępności wpisu po próbie przejścia.

### 8.3. Wykonanie i trwałość

#### US-REM-07 — Alert niezależny od synchronizacji

**Historia:** Jako rodzic chcę otrzymać przypomnienie w zadanym czasie także offline i podczas pobierania danych, aby nie uzależniać ważnej sprawy od działania Librusa.

**Kryteria akceptacji:**

- **KA-REM-14:** Aplikacja sprawdza terminy co sekundę, niezależnie od interwału synchronizacji, dostępu do internetu i włącznika powiadomień o nowych danych.
- **KA-REM-15:** Dla należnego alertu podejmuje jedną wysyłkę systemową i wyświetla okno „Twoje przypomnienie” z własnym tekstem, źródłem i terminem. Nie wysyła ponownie przy równoległym sprawdzeniu zegara.
- **KA-REM-19:** Osobne alerty systemowe nie zastępują się wzajemnie ani nie zastępują zbiorczego powiadomienia o nowych danych. Zapis alertów nie wymaga zapamiętania hasła.

#### US-REM-08 — Zaległe alerty i brak powtórzeń po restarcie

**Historia:** Jako rodzic chcę zachować stan przypomnień między uruchomieniami i otrzymać zaległe alerty bez powtórzenia wykonanych, aby móc polegać na nich po przerwie w pracy aplikacji.

**Kryteria akceptacji:**

- **KA-REM-16:** Zapisuje wykonanie, czas i wynik przyjęcia przez system, aby po ponownym uruchomieniu nie powtórzyć wykonanego alertu. Jeśli zapis zawiedzie, ostrzega o możliwości powtórzenia.
- **KA-REM-17:** Po następnym uruchomieniu lub wznowieniu wykonuje zaległe alerty. Wersja desktopowa wymaga aktywnego procesu, nie budzi komputera i nie planuje alarmów w Harmonogramie zadań Windows.
- **KA-REM-18:** Aplikacja chroni izolację profili i demo; nie wykonuje alertu starego profilu podczas przejścia na inny. Alerty są niezależne od roku szkolnego i widoczności wpisu źródłowego.

Nie można edytować lub usuwać alertu właśnie wysyłanego. Zmiana konta nie tworzy katalogu zachowanych profili. Opcja usunięcia lokalnych danych usuwa alerty z aktywnego zapisu. W demo alerty są wyłącznie tymczasowe.

## 9. Panel Ustawienia

### 9.1. Konto Synergia

Karta pokazuje tryb konta oraz **Połącz / zmień konto** i **Włącz demo**. Stosuje zasady logowania z rozdziału 3 i izolacji demo z rozdziału 12.

### 9.2. Odświeżanie i powiadomienia

Karta zawiera wybór interwału, checkbox **Pokazuj powiadomienia o nowych danych**, **Zapisz ustawienia** oraz **Test powiadomienia**. Ustawienia nie stosują się tylko po zmianie pola — wymagają zapisu. Niezapisane wartości w formularzu nie są nadpisywane przez odświeżenie stanu interfejsu.

#### US-UST-01 — Ustawienie częstotliwości odczytu

**Historia:** Jako rodzic chcę ustawić i zapisać częstotliwość sprawdzania danych, aby dostosować działanie aplikacji do swoich potrzeb z zachowaniem ograniczeń Librusa.

**Kryteria akceptacji:**

- **KA-UST-01:** Aplikacja domyślnie odświeża dane co 15 minut i włącza powiadomienia o nowych danych. Akceptuje wyłącznie 10, 15, 30 lub 60 minut.
- **KA-UST-02:** Aplikacja zapisuje interwał i włącznik powiadomień po świadomej akcji użytkownika; odtwarza je przy uruchomieniu. Błąd trwałego zapisu musi być widoczny.
- **KA-UST-03:** Zmiana interwału przelicza termin kolejnego odczytu, jeśli taki istnieje, z uwzględnieniem trwającego ograniczenia zapytań.

#### US-UST-02 — Sterowanie powiadomieniami i ich test

**Historia:** Jako rodzic chcę włączyć lub wyłączyć powiadomienia o nowych danych oraz wysłać test, aby sprawdzić działanie systemu bez wyłączania własnych alertów.

**Kryteria akceptacji:**

- **KA-UST-04:** Włącznik dotyczy automatycznych powiadomień o danych; nie wyłącza zegara własnych przypomnień ani jawnego testu systemowego.
- **KA-UST-05:** Test powiadomienia wysyła neutralny tekst i pokazuje rezultat przekazania do systemu wraz ze wskazówką sprawdzenia ustawień lub trybu „Nie przeszkadzać”. Test działa również bez połączenia konta.

### 9.3. Dane na komputerze

Akcja **Usuń lokalne dane i konto** wymaga potwierdzenia. Usuwa aktywną kopię, dane logowania, stan porównywania i przypomnienia, zamyka sesję i zatrzymuje kolejne odczyty. Dane w szkole pozostają bez zmian.

#### US-UST-03 — Usunięcie lokalnych danych i konta

**Historia:** Jako rodzic chcę po potwierdzeniu usunąć aktywny lokalny zapis i zakończyć sesję, aby wyczyścić dane z urządzenia bez zmiany dziennika szkoły.

**Kryteria akceptacji:**

- **KA-UST-06:** Przed usunięciem całego aktywnego zapisu wyświetla potwierdzenie; anulowanie nie zmienia danych.
- **KA-UST-07:** Po usunięciu czyści stan konta w pamięci i wraca do braku połączenia. Jeśli plik nie może być usunięty, nadal czyści pamięć i jawnie ostrzega o pozostawieniu danych na dysku.

Usuwanie dotyczy także aktywnego pliku podczas działania demo. Samo **Włącz demo** nie usuwa danych z dysku; to odrębna, świadoma akcja. Ustawienia interwału i powiadomień mogą pozostać w pamięci do zakończenia procesu, chociaż usunięto ich zapis.

## 10. Synchronizacja, zmiany i powiadomienia

### 10.1. Synchronizacja

Kolejność odczytu w desktopie: Oceny, Terminarz, Frekwencja, Plan lekcji, Ogłoszenia, Wiadomości. Wiadomości są ostatnie, aby niedostępność skrzynki nie zasłoniła udanych danych pozostałych sekcji. Globalne **Odśwież** odczytuje zestaw sekcji; nie ma osobnego przycisku odświeżenia pojedynczej sekcji.

#### US-SYN-01 — Aktualizacja danych i czasów odczytu

**Historia:** Jako rodzic chcę odświeżać wszystkie sekcje ręcznie i automatycznie oraz widzieć czasy udanych odczytów, aby ocenić, czy oglądam aktualne informacje.

**Kryteria akceptacji:**

- **KA-SYN-01:** Aplikacja odczytuje wszystkie sześć sekcji po logowaniu oraz ręcznie i cyklicznie przy aktywnej sesji; nie uruchamia konkurencyjnych synchronizacji.
- **KA-SYN-02:** Aplikacja waliduje odpowiedź przed zastąpieniem kopii sekcji; błędny format nie jest poprawną pustą listą.
- **KA-SYN-03:** Aplikacja aktualizuje czas sekcji tylko po jej poprawnym odczycie. Globalny czas ostatniej synchronizacji aktualizuje, gdy co najmniej jedna sekcja się udała.

#### US-SYN-02 — Zachowanie danych po nieudanej synchronizacji

**Historia:** Jako rodzic chcę nadal widzieć ostatnią poprawną kopię i wyniki sekcji, które się udały, aby częściowa lub całkowita awaria nie odbierała mi dostępnych informacji.

**Kryteria akceptacji:**

- **KA-SYN-04:** Po częściowym sukcesie zachowuje sukcesy i kopie pozostałych sekcji, pokazuje licznik udanych sekcji oraz ich błędy.
- **KA-SYN-05:** Po całkowitym niepowodzeniu zachowuje poprzednią kopię, jeśli istnieje; w przeciwnym razie pokazuje brak pobranych danych, bez komunikatu sugerującego kopię.

#### US-SYN-03 — Bezpieczne ponawianie odczytów

**Historia:** Jako użytkownik chcę, aby aplikacja respektowała przerwy i ograniczenia Librusa oraz informowała o terminie kolejnej próby, aby móc odzyskać aktualne dane bez nasilania blokady usługi.

**Kryteria akceptacji:**

- **KA-SYN-06:** Ręczny odczyt respektuje przerwy ochronne. Po cyklu istnieje co najmniej około 60 sekund ochrony przed kolejnym ręcznym odczytem.
- **KA-SYN-07:** Całkowite niepowodzenia wydłużają odstęp przez kolejne potęgi dwóch, z limitem mnożnika 16 i maksymalnego odstępu 4 godzin. Częściowy lub pełny sukces zeruje licznik tych niepowodzeń.
- **KA-SYN-08:** HTTP 429 przerywa dalszy odczyt i wstrzymuje nowe próby na co najmniej godzinę. Ograniczenie obejmuje także ponowne logowanie i pobieranie treści wiadomości; akcje ręczne nie obchodzą blokady.

#### US-SYN-04 — Rozdzielenie problemów sesji i skrzynki

**Historia:** Jako rodzic chcę rozróżnić wygaśnięcie całej sesji od utraty dostępu do wiadomości, aby wiedzieć, czy ponownie się zalogować i które sekcje nadal działają.

**Kryteria akceptacji:**

- **KA-SYN-09:** Wygaśnięcie głównej sesji wymaga ponownego połączenia; utrata autoryzacji tylko skrzynki zatrzymuje jej cykliczne próby, pozostawiając resztę działającą.

#### US-SYN-05 — Informacja o błędzie zamiast zawieszenia

**Historia:** Jako rodzic chcę otrzymać zrozumiały wynik przy awarii sieci lub usługi, aby móc kontynuować korzystanie z interfejsu i podjąć kolejną próbę później.

**Kryteria akceptacji:**

- **KA-SYN-10:** Zapytania mają ograniczony czas oczekiwania i liczbę przekierowań. Przekroczenie terminu, awaria sieci, odmowa dostępu, prace serwisowe i nierozpoznane dane mają komunikat zamiast zawieszenia interfejsu.

Przerwy ochronne są stanem bieżącego procesu, a nie zapisanym harmonogramem serwera. Automatyczne odczyty wymagają działającej aplikacji i sesji. Nie ma mechanizmu push dla nowych danych szkolnych.

### 10.2. Wykrywanie zmian

#### US-ZMI-01 — Powiadomienia tylko o rzeczywistych zmianach

**Historia:** Jako rodzic chcę otrzymywać informacje o nowych i zmienionych danych bez alarmów o starych wpisach lub samym statusie odczytu, aby powiadomienia miały znaczenie.

**Kryteria akceptacji:**

- **KA-ZMI-01:** Pierwszy poprawny odczyt każdej sekcji ustala cichy punkt odniesienia, także gdy inne sekcje były już odczytane.
- **KA-ZMI-02:** Aplikacja porównuje tożsamość wpisu oddzielnie od jego treści. Zmiana tytułu, informacji, daty lub opisu może być zmianą; sam status nieprzeczytania i flagi interfejsu nią nie są.
- **KA-ZMI-03:** Aplikacja zachowuje historię rozpoznanych wpisów poza aktualną listą, aby powrót znanego, niezmienionego wpisu nie tworzył ponownego powiadomienia. Nie traktuje zniknięcia wiadomości poza limit pobrania jako usunięcia.

#### US-ZMI-02 — Wykrywanie zmian przyszłych terminów

**Historia:** Jako rodzic chcę wiedzieć o dodaniu, zmianie lub zniknięciu przyszłych wydarzeń i lekcji w już sprawdzonym okresie, aby dostosować plany bez alarmów wywołanych samym rozszerzeniem zakresu.

**Kryteria akceptacji:**

- **KA-ZMI-04:** Dla Terminarza i Planu lekcji uwzględniać tylko wpisy od dzisiaj oraz dni wcześniej objęte odczytem. Nowo dołączony miesiąc lub tydzień tworzy cichy zakres odniesienia.
- **KA-ZMI-05:** Aplikacja wykrywa zniknięcie przyszłego wydarzenia lub lekcji w zakresie wspólnym poprzedniego i obecnego odczytu. Zlicza taką zmianę, choć usunięty wpis nie ma wiersza do wyróżnienia.

#### US-ZMI-03 — Ciągłość porównań i nowy rok szkolny

**Historia:** Jako rodzic chcę zachować historię porównań po restarcie i rozpocząć nowy szkolny punkt odniesienia bez utraty alertów, aby nie otrzymywać powtórnych lub nieaktualnych informacji.

**Kryteria akceptacji:**

- **KA-ZMI-06:** Aplikacja zachowuje historię zmian przy restarcie tego samego profilu. Przy odtwarzaniu kopii z innego roku szkolnego resetuje szkolne dane odniesienia; zachowuje własne przypomnienia.

Rok szkolny jest liczony od września. Reset opisany powyżej zachodzi przy odtwarzaniu zapisu; kod nie ma osobnego zdarzenia przełączającego rok w działającym bez przerwy procesie. Historia porównań służy eliminowaniu duplikatów, nie jest ekranem historii dla użytkownika. Obecna implementacja ogranicza ją do około 20 000 identyfikatorów na sekcję.

### 10.3. Automatyczne powiadomienia o danych

#### US-POW-01 — Zbiorcze powiadomienie bez danych wrażliwych

**Historia:** Jako rodzic chcę otrzymać ogólną liczbę zmian w sekcjach bez danych ucznia i treści wiadomości, aby wiedzieć o nowych informacjach bez ujawniania ich w banerze systemowym.

**Kryteria akceptacji:**

- **KA-POW-01:** Po odczycie z wykrytymi zmianami i włączonych powiadomieniach wysyła zbiorczy komunikat zawierający liczby zmian w odpowiednich sekcjach. Brak zmian i pierwszy odczyt nie wysyłają komunikatu.
- **KA-POW-02:** Zbiorczy komunikat nie zawiera nazwiska ucznia, oceny, tematu ani treści wiadomości; zachęca do otwarcia LibrusApp.

#### US-POW-02 — Zrozumienie wyniku wysłania powiadomienia

**Historia:** Jako rodzic chcę wiedzieć, czy system przyjął powiadomienie i jakie ma ono ograniczenia, aby poprawnie ocenić brak banera bez utraty pobranych danych.

**Kryteria akceptacji:**

- **KA-POW-03:** Nieudane przekazanie komunikatu do Windows zgłasza w aplikacji, bez usuwania pobranych danych.
- **KA-POW-04:** Aplikacja rozróżnia przekazanie do systemu i faktyczne pokazanie banera. Ustawienia systemowe lub „Nie przeszkadzać” mogą ukrywać przyjęte powiadomienie.
- **KA-POW-05:** Powiadomienia o danych mogą zastępować poprzedni komunikat o danych; własne przypomnienia mają odrębne identyfikatory. Nie ma zaimplementowanej akcji kliknięcia banera kierującej do konkretnego wpisu.

## 11. Dane lokalne i prywatność

To wymagania ochrony danych towarzyszące funkcjom użytkowym. Mechanizmy Windows są opisem obecnej implementacji; wersja mobilna wymaga odpowiedników na wybranej platformie.

#### US-DAN-01 — Chroniona kopia lokalna

**Historia:** Jako rodzic chcę mieć zaszyfrowaną kopię danych i alertów oraz kontrolować zapis hasła i treści wiadomości, aby korzystać z aplikacji offline z zachowaniem prywatności.

**Kryteria akceptacji:**

- **KA-DAN-01:** Trwale zapisuje ustawienia, kopię sekcji, czasy odczytów, historię porównań, identyfikację profilu i przypomnienia. Dane logowania zapisuje wyłącznie po wyborze zapamiętywania.
- **KA-DAN-02:** Aplikacja szyfruje cały zapis na Windows przez DPAPI CurrentUser. Nie tworzy tekstowego pliku z hasłem ani zapisu cookies sesji.
- **KA-DAN-05:** Aplikacja nie zapisuje pełnej pobranej treści wiadomości. Treść ogłoszenia należy do trwałej kopii; tekst własnego przypomnienia należy do zapisu alertu.

#### US-DAN-02 — Bezpieczny zapis i zgodność starszych danych

**Historia:** Jako dotychczasowy użytkownik chcę zachować poprawny zapis także po awarii zapisu lub zmianie nazwy aplikacji, aby nie utracić kopii konta i przypomnień.

**Kryteria akceptacji:**

- **KA-DAN-03:** Aplikacja zapisuje atomowo: nieudana operacja nie niszczy poprzedniego poprawnego pliku. Błąd odczytu, szyfrowania, zapisu lub usunięcia ma jawny komunikat.
- **KA-DAN-04:** Nowy zapis umieszcza w `%LOCALAPPDATA%\LibrusApp\state.dpapi`; bez nowego pliku odczytuje istniejący `%LOCALAPPDATA%\SzkolnyPanel\state.dpapi`. Jeśli są oba, wybiera LibrusApp; nie przenosi ani nie scala ich automatycznie.

#### US-DAN-03 — Bezpieczny odczyt danych z Librusa

**Historia:** Jako rodzic chcę, aby aplikacja komunikowała się z dozwolonymi serwisami i pokazywała treść bez wykonywania aktywnego kodu, aby szkolne dane nie narażały mojego urządzenia i sesji.

**Kryteria akceptacji:**

- **KA-DAN-06:** Aplikacja ogranicza komunikację konta do dozwolonych hostów Librusa przez HTTPS z weryfikacją certyfikatu. Zablokowane przekierowanie ma podać bezpieczną przyczynę, bez pełnego adresu i parametrów sesji.
- **KA-DAN-07:** Aplikacja nie wykonuje aktywnego HTML z danych szkoły ani nie pobiera obrazów z opisów. Nie otwiera dowolnych adresów dostarczonych w wiadomości jako działania aplikacji.

#### US-DAN-04 — Prywatna diagnostyka i brak zewnętrznej analityki

**Historia:** Jako rodzic chcę, aby diagnostyka nie ujawniała sekretów, a dane nie trafiały do analityki lub modeli AI, aby móc zgłaszać problemy bez niepotrzebnego udostępniania informacji o koncie.

**Kryteria akceptacji:**

- **KA-DAN-08:** Diagnostyka i zgłoszenia błędów nie zawierają haseł, cookies, tokenów, surowych odpowiedzi ani pełnych adresów sesji. Użytkownik powinien anonimizować własne zrzuty ekranu.
- **KA-DAN-09:** Aplikacja nie wysyła danych do analityki lub modeli AI. Desktop komunikuje się bezpośrednio z Librusem; pobieranie zależności przy instalacji korzysta z PyPI.

DPAPI jest powiązane z użytkownikiem Windows, a nie uniwersalnym szyfrem eksportu danych. Program uruchomiony z uprawnieniami tego samego użytkownika może uzyskać dostęp do danych, które również występują w pamięci aplikacji. Nie ma eksportu/importu konta lub automatycznej migracji do telefonu. Poza Windows obecny magazyn jest tylko w pamięci, a natywne powiadomienia nie działają.

Usuwanie danych dotyczy aktywnego pliku i własnych plików tymczasowych, nie wszystkich historycznych katalogów. Jeżeli oba foldery zawierają zapis, usunięcie nowego nie kasuje starego; przy kolejnym uruchomieniu może zostać wykryty starszy plik. W projekcie mobilnym trzeba jawnie ustalić zakres „usuń wszystkie dane aplikacji”.

## 12. Tryb demonstracyjny

#### US-DEMO-01 — Poznanie aplikacji bez konta

**Historia:** Jako osoba rozważająca używanie LibrusApp chcę obejrzeć wszystkie sekcje na wyraźnie oznaczonych fikcyjnych danych, aby poznać aplikację bez logowania i bez nadpisania prawdziwej kopii.

**Kryteria akceptacji:**

- **KA-DEMO-01:** Aplikacja udostępnia demo przez skrypt lub opcję uruchomienia `--demo`, ekran powitalny i Ustawienia; obejmuje wszystkie sześć sekcji wymyślonymi danymi.
- **KA-DEMO-02:** Oznacza demo w nawigacji, banerze i stanie konta, tak aby nie można go pomylić z danymi szkoły.
- **KA-DEMO-03:** Nie loguje się do konta i nie synchronizuje danych ze szkołą. Wejście w demo zamyka bieżącą sesję i nie nadpisuje zapisanego konta, kopii ani alertów.

#### US-DEMO-02 — Wypróbowanie zmian i przypomnień w demo

**Historia:** Jako osoba poznająca aplikację chcę zasymulować ocenę, otworzyć przykładową wiadomość i wypróbować alerty, aby zrozumieć działanie funkcji bez zmian w dzienniku szkoły.

**Kryteria akceptacji:**

- **KA-DEMO-04:** „Zasymuluj nową ocenę” dodaje sztuczny wpis i pozwala sprawdzić wykrywanie zmian; włącznik automatycznych powiadomień nadal obowiązuje. „Odśwież” odświeża wyłącznie lokalny zestaw demo.
- **KA-DEMO-05:** Aplikacja umożliwia pobranie przykładowej treści wiadomości oraz tymczasowe tworzenie, edycję i wykonanie alertów. Demo nie zmienia statusów w prawdziwym Librusie.

#### US-DEMO-03 — Test systemowych alertów w demo

**Historia:** Jako użytkownik chcę przetestować rzeczywiste powiadomienie na fikcyjnych danych bez trwałego zapisu ustawień demo, aby sprawdzić urządzenie przed połączeniem konta.

**Kryteria akceptacji:**

- **KA-DEMO-06:** Demo może wysyłać rzeczywisty test lub alert systemowy na komputerze, ale wyłącznie z przykładowych lub podanych przez użytkownika treści. Jego ustawienia i alerty nie są utrwalane.

Uruchomienie nowej sesji demo tworzy nowy zestaw przykładów i czyści tymczasowe alerty. Powrót do prawdziwego konta wymaga połączenia; zgodny zapis profilu może zostać odtworzony. Wyjątkiem od ochrony zapisu jest świadome użycie „Usuń lokalne dane i konto”.

## 13. Model informacji

Poniższy model opisuje znaczenie danych, które warto zachować przy przenoszeniu funkcji. Nie narzuca bazy danych, protokołu ani języka implementacji mobilnej.

### 13.1. Wpis szkolny

| Pole w kodzie | Znaczenie i użycie |
|---|---|
| `id` | Tożsamość wpisu; powiązanie z historią i przypomnieniem. Źródło może wymagać wyliczonego identyfikatora |
| `kind` | `grades`, `messages`, `announcements`, `schedule`, `attendance` lub `timetable` |
| `title` | Tytuł / temat / przedmiot i ocena / rodzaj wpisu |
| `subtitle` | Nadawca, autor, kategoria, przedmiot, lekcja lub inne informacje z danej sekcji |
| `when` | Data lub data i godzina; brak godziny nie oznacza północy wydarzenia. Czasami data może być nierozpoznana lub pusta |
| `details` | Bezpieczny tekst dostępnych szczegółów; dla wiadomości metadane, dla ogłoszeń również treść |
| `url` | Adres oficjalnej sekcji; akcja otwarcia korzysta ze stałego mapowania sekcji |
| `unread` | Status nieprzeczytania wiadomości, pochodzący z Librusa |
| `changed` | Tymczasowy wynik ostatniego porównania; nie status obejrzenia przez użytkownika |

Wpisy bez identyfikatora lub tytułu oraz sprzeczne wpisy o tym samym identyfikatorze są błędem odczytu. Zgodne duplikaty są scalane. Brak pola udostępnianego opcjonalnie przez szkołę nie uprawnia do wymyślania jego wartości.

### 13.2. Przypomnienie

| Pole w kodzie | Znaczenie |
|---|---|
| `id` | Własny unikalny identyfikator alertu |
| `kind`, `item_id` | Sekcja i identyfikator źródłowego wpisu |
| `source_title` | Zachowany tytuł źródła, maksymalnie 300 znaków; przetrwa zniknięcie wpisu |
| `title` | Własna treść, 1–200 znaków |
| `due_at` | Termin z offsetem strefy czasu, ustawiony według lokalnego czasu użytkownika |
| `show_text` | Czy ujawniać treść w systemowym powiadomieniu |
| `status` | `pending` lub `fired` |
| `fired_at` | Czas wykonania; pusty dla zaplanowanego |
| `system_sent` | Czy system przyjął powiadomienie; nie informacja o przeczytaniu |

Przypomnienie nie zawiera pełnej kopii źródła ani pełnej treści wiadomości. Edycja zmienia jego tekst i termin, ale nie źródło. Dla mobile trzeba rozstrzygnąć zachowanie przy zmianie strefy czasowej: obecny zapis z offsetem nie opisuje na nowo zamiaru użytkownika po podróży.

### 13.3. Stan konta i sekcji

Trwały stan zawiera wersję formatu, identyfikator profilu wyliczony z loginu, opcjonalne dane logowania, interwał i włącznik powiadomień, rok szkolny kopii, listy wpisów, czasy odczytów, historię porównań, zakresy dat oraz alerty. Tryb, zajętość, błędy, sesje sieciowe, termin następnego odczytu, filtry, wybór dnia i sortowanie nie są trwałym zestawem ustawień.

## 14. Scenariusze akceptacyjne

Scenariusze służą do sprawdzenia zgodności następnej implementacji. Dane testowe mają być syntetyczne, a wyniki usług zewnętrznych kontrolowane. Obsługa konkretnego konta wymaga osobnej weryfikacji integracji.

| ID | Scenariusz i oczekiwany wynik | Powiązane historie |
|---|---|---|
| AK-01 | Pierwszy start bez zapisu: Przegląd, brak konta, liczniki „—”, możliwość logowania i demo. Pusta lista nie sugeruje udanego odczytu. | [US-APP-01](#us-app-01--nawigacja-i-orientacja-w-aplikacji), [US-APP-02](#us-app-02--dostęp-do-głównych-działań), [US-APP-03](#us-app-03--przeglądanie-podczas-operacji-i-informacja-o-błędach), [US-LIS-06](#us-lis-06--zrozumienie-pustej-listy-i-starszych-danych), [US-PRZ-01](#us-prz-01--szybki-przegląd-sytuacji-szkolnej) |
| AK-02 | Logowanie bez zapamiętywania: odczyt sekcji i zapis kopii/alertów bez hasła; ponowny start daje kopię i prośbę o logowanie. | [US-KON-01](#us-kon-01--świadome-połączenie-konta), [US-KON-02](#us-kon-02--pobranie-danych-po-logowaniu-i-zrozumienie-odmowy), [US-KON-03](#us-kon-03--powrót-do-zapisanej-kopii), [US-DAN-01](#us-dan-01--chroniona-kopia-lokalna) |
| AK-03 | Logowanie z zapamiętywaniem: ponowny start podejmuje połączenie, a identyczne wpisy nie generują zaległych powiadomień. | [US-KON-03](#us-kon-03--powrót-do-zapisanej-kopii), [US-ZMI-01](#us-zmi-01--powiadomienia-tylko-o-rzeczywistych-zmianach), [US-ZMI-03](#us-zmi-03--ciągłość-porównań-i-nowy-rok-szkolny) |
| AK-04 | Pierwszy nieudany login lub pierwszy nieudany odczyt: jawny błąd, brak fałszywego komunikatu o kopii. Nie ujawniać hasła lub sesji. | [US-KON-02](#us-kon-02--pobranie-danych-po-logowaniu-i-zrozumienie-odmowy), [US-SYN-02](#us-syn-02--zachowanie-danych-po-nieudanej-synchronizacji), [US-DAN-04](#us-dan-04--prywatna-diagnostyka-i-brak-zewnętrznej-analityki) |
| AK-05 | Awaria jednej sekcji: reszta aktualizuje się, wadliwa zachowuje wpisy i poprzedni czas, a Przegląd wskazuje błąd. | [US-LIS-06](#us-lis-06--zrozumienie-pustej-listy-i-starszych-danych), [US-PRZ-02](#us-prz-02--przejście-z-podsumowania-do-danych), [US-SYN-01](#us-syn-01--aktualizacja-danych-i-czasów-odczytu), [US-SYN-02](#us-syn-02--zachowanie-danych-po-nieudanej-synchronizacji) |
| AK-06 | Zaznaczenie, dwuklik i wyszukiwanie wiadomości: zero wywołań treści. Jawne pobranie pokazuje tekst i aktualizuje status, ale nie utrwala treści. | [US-WIA-01](#us-wia-01--przejrzenie-skrzynki-bez-odczytywania-treści), [US-WIA-02](#us-wia-02--świadome-pobranie-treści-wiadomości) |
| AK-07 | Zmniejszenie lub zwinięcie podglądu wiadomości: przycisk pobierania pozostaje widoczny; bez wyboru i przy zajętości jest nieaktywny. | [US-WIA-02](#us-wia-02--świadome-pobranie-treści-wiadomości), [US-APP-03](#us-app-03--przeglądanie-podczas-operacji-i-informacja-o-błędach) |
| AK-08 | Zmiana tylko statusu odczytu nie powiadamia; nowy temat lub opis wpisu powiadamia raz. Powrót znanego niezmienionego wpisu nie powiadamia. | [US-ZMI-01](#us-zmi-01--powiadomienia-tylko-o-rzeczywistych-zmianach), [US-POW-01](#us-pow-01--zbiorcze-powiadomienie-bez-danych-wrażliwych) |
| AK-09 | Ogłoszenie z poprawioną treścią zachowuje tożsamość; można znaleźć fragment treści offline i utworzyć alert. | [US-OGL-01](#us-ogl-01--czytanie-i-obserwowanie-ogłoszeń-szkoły) |
| AK-10 | Kalendarz: luty przestępny, sześć rzędów tygodni i granica grudzień/styczeń są poprawne. Filtr dnia, Dzisiaj, Cały miesiąc i Lista pokazują właściwe wyniki. | [US-TER-01](#us-ter-01--przeglądanie-terminarza-jako-kalendarza-lub-listy), [US-TER-02](#us-ter-02--wybór-miesiąca-i-dnia), [US-TER-03](#us-ter-03--wyszukiwanie-wydarzeń-w-kalendarzu) |
| AK-11 | Wyszukiwanie w kalendarzu zmienia liczniki dni i listę. Odległy miesiąc ma ostrzeżenie o niepełności, a nawigacja nie wywołuje sieci. | [US-TER-02](#us-ter-02--wybór-miesiąca-i-dnia), [US-TER-03](#us-ter-03--wyszukiwanie-wydarzeń-w-kalendarzu), [US-TER-04](#us-ter-04--ocena-kompletności-miesiąca) |
| AK-12 | Nowy zakres przyszłych dni jest cichy; dodanie lub usunięcie wydarzenia/lekcji w już znanym zakresie jest zmianą. | [US-ZMI-02](#us-zmi-02--wykrywanie-zmian-przyszłych-terminów), [US-PLA-01](#us-pla-01--przeglądanie-planu-i-zmian-lekcji) |
| AK-13 | Utworzenie alertu w każdej z trzech dozwolonych sekcji, walidacja pustego tekstu, >200 znaków, błędnej i przeszłej daty oraz nieistniejącego czasu lokalnego. | [US-REM-01](#us-rem-01--zaplanowanie-własnego-przypomnienia), [US-REM-02](#us-rem-02--prywatność-tekstu-alertu), [US-REM-03](#us-rem-03--pewność-zapisania-przypomnienia) |
| AK-14 | Zegar podczas synchronizacji, bez internetu i przy wyłączonych powiadomieniach o danych: alert uruchamia się raz, ma okno w aplikacji, a ukryta treść nie trafia do banera. | [US-REM-02](#us-rem-02--prywatność-tekstu-alertu), [US-REM-07](#us-rem-07--alert-niezależny-od-synchronizacji), [US-REM-08](#us-rem-08--zaległe-alerty-i-brak-powtórzeń-po-restarcie), [US-UST-02](#us-ust-02--sterowanie-powiadomieniami-i-ich-test) |
| AK-15 | Niewykonany alert po restarcie jest zaległy i uruchamia się; po zapisie wykonania kolejny restart go nie powtarza. Brak przyjęcia przez system daje status „Wykonane w aplikacji”. | [US-REM-07](#us-rem-07--alert-niezależny-od-synchronizacji), [US-REM-08](#us-rem-08--zaległe-alerty-i-brak-powtórzeń-po-restarcie) |
| AK-16 | Edycja wykonanego alertu planuje go ponownie; usunięcie nie usuwa wpisu szkoły. Błąd zapisu wycofuje każdą z tych modyfikacji. | [US-REM-03](#us-rem-03--pewność-zapisania-przypomnienia), [US-REM-05](#us-rem-05--zmiana-lub-usunięcie-alertu) |
| AK-17 | Źródło opuszcza zakres listy: alert nadal działa. Pokaż wpis informuje o braku źródła; zmiana roku nie usuwa alertu. | [US-REM-06](#us-rem-06--powrót-do-źródła-przypomnienia), [US-REM-08](#us-rem-08--zaległe-alerty-i-brak-powtórzeń-po-restarcie), [US-ZMI-03](#us-zmi-03--ciągłość-porównań-i-nowy-rok-szkolny) |
| AK-18 | HTTP 429 zatrzymuje kolejne sekcje i nie można obejść blokady przyciskiem Odśwież, logowaniem ani pobraniem treści. | [US-SYN-03](#us-syn-03--bezpieczne-ponawianie-odczytów) |
| AK-19 | Główna sesja wygasa: kopia pozostaje, cykl zostaje zatrzymany. Wygaśnięcie tylko skrzynki nie wyłącza innych sekcji ani nie uruchamia pętli logowania. | [US-KON-06](#us-kon-06--wygaśnięcie-sesji-bez-utraty-kopii), [US-WIA-03](#us-wia-03--dostęp-do-nagłówków-przy-problemach-ze-skrzynką), [US-SYN-04](#us-syn-04--rozdzielenie-problemów-sesji-i-skrzynki) |
| AK-20 | Demo i zmiana konta nie mieszają szkolnych danych i alertów. Powrót z demo do tego samego profilu odtwarza jego zapis. | [US-KON-04](#us-kon-04--zmiana-konta-bez-mieszania-danych), [US-REM-08](#us-rem-08--zaległe-alerty-i-brak-powtórzeń-po-restarcie), [US-DEMO-01](#us-demo-01--poznanie-aplikacji-bez-konta), [US-DEMO-02](#us-demo-02--wypróbowanie-zmian-i-przypomnień-w-demo), [US-DEMO-03](#us-demo-03--test-systemowych-alertów-w-demo) |
| AK-21 | Odznaczenie zapamiętywania usuwa stare hasło również po nieudanym loginie; awaria usunięcia pliku jest jawna. | [US-KON-05](#us-kon-05--cofnięcie-zgody-na-zapamiętanie-hasła), [US-UST-03](#us-ust-03--usunięcie-lokalnych-danych-i-konta) |
| AK-22 | Usunięcie danych: anulowanie nic nie zmienia; potwierdzenie czyści aktywny zapis i RAM, kończy sesję i nie wysyła żądania usunięcia do szkoły. | [US-UST-03](#us-ust-03--usunięcie-lokalnych-danych-i-konta) |
| AK-23 | Bezpieczny tekst i transport: HTML pozostaje nieaktywny, zewnętrzne obrazy nie są ładowane, obce przekierowania są blokowane, komunikaty nie zawierają sekretów. | [US-WIA-02](#us-wia-02--świadome-pobranie-treści-wiadomości), [US-DAN-03](#us-dan-03--bezpieczny-odczyt-danych-z-librusa), [US-DAN-04](#us-dan-04--prywatna-diagnostyka-i-brak-zewnętrznej-analityki) |
| AK-24 | Stary zapis SzkolnyPanel można odczytać, zmienić i usunąć; przy dwóch plikach aktywny jest LibrusApp i nie następuje scalenie kont. | [US-DAN-02](#us-dan-02--bezpieczny-zapis-i-zgodność-starszych-danych) |

## 15. Przeniesienie funkcji do wersji mobilnej

### 15.1. Proponowany zakres zgodności

Punktem wyjścia są historie US-* i ich kryteria KA-* i opisane scenariusze. Zachować należy znaczenie danych i skutki akcji użytkownika; desktopowe przyciski, separatory i skróty klawiaturowe mogą otrzymać odpowiedniki dotykowe. Poniższa tabela jest materiałem do projektu, nie opisem istniejącej aplikacji mobilnej.

| Obszar | Funkcja do zachowania | Element wymagający adaptacji |
|---|---|---|
| Nawigacja | Dziewięć paneli, widoczny tryb i liczby nieprzeczytanych / zaplanowanych | Menu, zakładki lub ekran sekcji; na małym ekranie osobny widok szczegółów |
| Konto | Login Synergia, dobrowolne zapamiętanie, izolacja konta | Magazyn sekretów telefonu i cykl odtwarzania sesji |
| Przegląd | Znaczenie trzech liczników i podglądy ocen/wydarzeń | Karty mieszczące się na telefonie |
| Listy | Wyszukiwanie, sortowanie, odróżnienie pustych wyników od błędu | Dotykowe sterowanie filtrami i przejście lista → szczegóły |
| Wiadomości | Jawne pobranie treści, informacja o statusie odczytu, limit i prywatność | Dostępna akcja pobrania na ekranie szczegółów bez ryzyka schowania pod treścią |
| Ogłoszenia | Pełna treść w kopii i alerty | Układ czytania długiego tekstu |
| Terminarz | Kalendarz oraz lista, wybór dnia, zakres i niepełność danych | Układ kalendarza i listy na małym ekranie |
| Plan i frekwencja | Wierne dane szkoły i zmiany, bez własnych obliczeń | Lista dostosowana do dotyku; alternatywna siatka planu to dodatkowa decyzja |
| Przypomnienia | Tekst, termin, prywatność, stany, edycja i trwałość | Mechanizm systemowego planowania i zgody na powiadomienia |
| Synchronizacja | Cichy pierwszy odczyt, częściowe błędy, historia zmian i ograniczenie żądań | Zasady działania w tle i harmonogram zgodny z możliwościami wybranej platformy |
| Dane lokalne | Kopia offline, szyfrowanie, dobrowolność hasła i usuwanie | Odpowiednik DPAPI, format mobilny i zakres danych do usunięcia |
| Demo | Pełny zestaw przykładów bez konta i bez nadpisywania zapisu | Wejście w demo bez skryptów Windows |

### 15.2. Decyzje otwarte przed implementacją

| ID | Decyzja | Co trzeba ustalić |
|---|---|---|
| MOB-01 | Platformy i technologia | Android, iOS lub oba; implementacja natywna lub wspólna. Ten dokument nie wybiera stosu. |
| MOB-02 | Integracja z Librusem | Bezpośredni klient na urządzeniu czy dodatkowy backend; miejsce logowania, odpowiedzialność za dane i weryfikacja dostępnej integracji. |
| MOB-03 | Odświeżanie w tle | Co oznacza wybrany interwał po schowaniu lub zamknięciu aplikacji; jak pokazywać aktualność, jeśli system opóźni wykonanie. Desktopowe „co 15 minut” nie jest gwarancją dla telefonu. |
| MOB-04 | Alarmy po zamknięciu aplikacji | Czy wersja mobilna ma uruchamiać własne alerty przy nieaktywnym procesie; jak sprawdzić opóźnienia, restart urządzenia, odmowę zgody i zaległe alerty. Jest to rozszerzenie względem zależności desktopu od procesu. |
| MOB-05 | Strefa czasowa i zmiana zegara | Termin jako ustalony moment czy lokalna godzina także po zmianie strefy; zachowanie przy zmianie czasu i podróży. |
| MOB-06 | Akcja powiadomienia | Czy kliknięcie ma przechodzić do alertu lub źródła; obecny desktop nie realizuje takiego powiązania. |
| MOB-07 | Wiele kont lub dzieci | Zachować jedno aktywne konto czy dodać profile; wersja z wieloma kontami wymaga rozszerzenia modelu i harmonogramów. |
| MOB-08 | Treści offline | Zachować brak trwałego zapisu pełnych wiadomości czy wprowadzić dobrowolny cache; to świadoma zmiana obecnej polityki danych. |
| MOB-09 | Ustawienia widoków | Czy utrwalać sortowanie, filtry, miesiąc i tryb kalendarza; obecnie są tylko stanem interfejsu. |
| MOB-10 | Migracja i usuwanie | Brak automatycznego przeniesienia pliku DPAPI na telefon; ustalić ponowne logowanie, ewentualny eksport alertów i usuwanie wszystkich danych aplikacji. |
| MOB-11 | Prywatność i dostępność UI | Domyślna widoczność tekstu własnych alertów, obsługa większej czcionki, czytnika ekranu i oznaczeń innych niż kolor. Domyślne ujawnianie tekstu w desktopie jest decyzją do ponownej oceny. |
| MOB-12 | Zakres integracji dodatkowych | Eksport do kalendarza, siatka planu, cykliczne alerty lub udostępnianie to nowe wymagania, które trzeba oddzielnie zatwierdzić. |

Najważniejszą różnicą projektową jest niezależność przypomnień od odczytu danych. Planowanie alarmu na telefonie i synchronizacja ze szkołą muszą zachować osobne znaczenie. Powiadomienie przyjęte przez system nadal nie powinno być utożsamiane z przeczytaniem lub wykonaniem zadania przez użytkownika.

## 16. Funkcje poza obecnym zakresem

Lista rozgranicza istniejące wymagania od potencjalnych rozszerzeń. Brak tych funkcji nie powinien być przypadkowo zamieniony w obietnicę w specyfikacji mobilnej.

- Wysyłanie i odpowiadanie na wiadomości, usuwanie wiadomości i ręczne ustawianie ich statusu.
- Pobieranie załączników, aktywny HTML i obrazy w treści.
- Usprawiedliwianie nieobecności i zmiana danych w dzienniku.
- Kalkulator średnich, prognozy ocen, procent obecności, raporty i statystyki.
- Pełne archiwum wiadomości, dowolny zakres miesięcy lub tygodni pobierany na żądanie.
- Osobny panel prac domowych, przełączanie dzieci lub zarządzanie wieloma kontami.
- Edycja wydarzeń szkoły, integracja z kalendarzem urządzenia i eksport danych.
- Swobodne przypomnienia bez wpisu, alerty cykliczne, drzemka, priorytety i potwierdzanie wykonania zadania.
- Serwer aplikacji, push o danych szkolnych, synchronizacja danych między urządzeniami.
- Interaktywne 2FA/CAPTCHA, logowanie przez przeglądarkę i automatyczna pętla ponawiania hasła.
- Usługa Windows, automatyczny autostart, zasobnik systemowy i alarmy budzące komputer.

## 17. Powiązanie z kodem i testami

Identyfikatory wymagań pozwalają odwoływać się do ustalonego zachowania przy projektowaniu ekranów i testów mobilnych. Poniższe pliki są dowodami implementacji, nie dodatkową specyfikacją funkcji poza dokumentem.

| Obszar wymagań | Główne źródło | Testy referencyjne |
|---|---|---|
| US-APP, US-LIS, US-PRZ, US-UST | [ui.py](librus_app/ui.py), [__main__.py](librus_app/__main__.py), [core.py](librus_app/core.py) | [test_calendar.py](tests/test_calendar.py), [test_message_layout.py](tests/test_message_layout.py), [test_core.py](tests/test_core.py); nie każdy element UI ma osobny test |
| US-KON | [connector.py](librus_app/connector.py), [core.py](librus_app/core.py) | [test_login.py](tests/test_login.py), [test_core.py](tests/test_core.py) |
| US-OCE, US-WIA, US-FRE, US-PLA | [connector.py](librus_app/connector.py), [ui.py](librus_app/ui.py) | [test_connector.py](tests/test_connector.py), [test_message_bootstrap.py](tests/test_message_bootstrap.py), [test_message_layout.py](tests/test_message_layout.py) |
| US-OGL | [connector.py](librus_app/connector.py), [data.py](librus_app/data.py) | [test_announcements.py](tests/test_announcements.py) |
| US-TER | [calendar_view.py](librus_app/calendar_view.py), [connector.py](librus_app/connector.py) | [test_calendar.py](tests/test_calendar.py), [test_connector.py](tests/test_connector.py) |
| US-REM | [reminders.py](librus_app/reminders.py), [reminder_view.py](librus_app/reminder_view.py), [core.py](librus_app/core.py) | [test_reminders.py](tests/test_reminders.py), [test_platform.py](tests/test_platform.py) |
| US-SYN, US-ZMI, US-POW, US-DEMO | [core.py](librus_app/core.py), [data.py](librus_app/data.py), [platform_windows.py](librus_app/platform_windows.py) | [test_core.py](tests/test_core.py), [test_platform.py](tests/test_platform.py), [test_announcements.py](tests/test_announcements.py) |
| US-DAN | [platform_windows.py](librus_app/platform_windows.py), [connector.py](librus_app/connector.py), [core.py](librus_app/core.py) | [test_platform.py](tests/test_platform.py), [test_connector.py](tests/test_connector.py), [test_login.py](tests/test_login.py) |

Ostatni wynik testów kodu referencyjnego, z wcześniejszej weryfikacji zmiany nazwy: **160 zaliczonych, 2 pominięte i 16 subtestów zaliczonych**, Windows 11 / Python 3.12.10. Utworzenie dokumentu nie stanowi ponownego testu połączenia z usługą ani testu wersji mobilnej.

Instrukcję instalacji i uruchomienia opisuje [README aplikacji](README.md). Licencja kodu to **AGPL-3.0-or-later**; informacje o pochodzeniu integracji i licencjach zależności znajdują się w [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Przy ponownym wykorzystaniu kodu w wersji mobilnej trzeba zachować właściwe informacje licencyjne i sprawdzić obowiązki wynikające z faktycznie użytych komponentów.

# Szkolny Panel — Windows 11

Wersja **0.1.1**, przygotowana 7 października 2026 r.

Lokalny, nieoficjalny panel rodzica do konta LIBRUS Synergia. Pokazuje dane w jednym oknie i okresowo sprawdza zmiany. Kod źródłowy jest dołączony w całości.

**Status: prototyp do sprawdzenia na własnym komputerze.** Testy logiki i adapterów przeszły na danych syntetycznych. Nie potwierdzono logowania poprawionej wersji na rzeczywistym koncie ani nie przeprowadzono pełnej weryfikacji interfejsu, DPAPI i powiadomień na Windows. Nie jest to gotowy instalator EXE.

## Aktualizacja z wersji 0.1.0

1. Zamknij uruchomiony Szkolny Panel.
2. Rozpakuj folder `SzkolnyPanel` z nowego ZIP-a w miejsce poprzedniego folderu aplikacji i zaakceptuj zastąpienie plików. `Start.cmd` powinien znajdować się w tym samym miejscu co wcześniej.
3. Uruchom `Start.cmd`. Tytuł okna powinien brzmieć **Szkolny Panel 0.1.1**.
4. Wybierz **Połącz konto** i ponów logowanie.

Nie trzeba ponownie instalować Pythona, usuwać `.venv` ani kasować zapisanych danych. Zależności i format zapisu konta pozostają zgodne z wersją 0.1.0.

### Co poprawiono

- Usunięto dodatkowe anonimowe zapytanie do głównego adresu API, które mogło przerwać logowanie z HTTP 401/403 jeszcze przed wysłaniem danych konta.
- Logowanie zaczyna się od wejścia Synergii, zachowuje stan i cookies oraz przechodzi przez adresy przekierowań podane przez Librusa. Odpowiedź na login jest sprawdzana przed kolejnym krokiem. Hasło jest wysyłane jeden raz do stałego adresu logowania.
- Komunikaty błędów logowania zawierają etap, a odmowy i błędy odpowiedzi HTTP również jej kod. Nie zawierają hasła, loginu, wartości cookies, treści odpowiedzi ani parametrów adresu.
- Po nieudanym pierwszym logowaniu aplikacja nie twierdzi już, że wyświetla zapisaną kopię, jeśli żadnych danych jeszcze nie pobrano.

Poprzedni komunikat „Librus wymaga ponownego zalogowania lub potwierdzenia dostępu” wskazywał jedynie HTTP 401 lub 403. Bez wskazania etapu nie rozstrzygał, dlaczego Librus odmówił połączenia. Nowa diagnostyka umożliwia ustalenie tego przy następnej próbie; poprawka nie jest potwierdzeniem zgodności z każdym kontem.

## Najpierw obejrzyj demo

1. Rozpakuj cały ZIP do zwykłego folderu, np. `D:\Aplikacje\SzkolnyPanel`. Nie uruchamiaj plików bezpośrednio wewnątrz archiwum.
2. Potrzebujesz standardowego Pythona 3.11 lub nowszego z `pip` i `tkinter`. Dobrym wyborem do tej paczki jest Python 3.13, instalator Windows 64-bit: <https://www.python.org/downloads/windows/>. Kod i testy sprawdzono na Pythonie 3.12. Nie wybieraj pakietu „embeddable”.
3. Uruchom `Demo.cmd`. Przy pierwszym uruchomieniu skrypt tworzy folder `.venv` i pobiera biblioteki z PyPI. Później korzysta z przygotowanego środowiska.
4. W aplikacji przejdź do **Ustawienia → Test powiadomienia**. Możesz też użyć **Zasymuluj nową ocenę**.

Demo używa wyłącznie wymyślonych przykładów i nie loguje się do Librusa. Nie zastępuje zapisanych danych prawdziwego konta. Test powiadomienia wyświetla ogólny komunikat na Twoim komputerze.

## Połączenie prawdziwego konta

1. Zamknij demo i uruchom `Start.cmd` albo wybierz w aplikacji **Połącz konto**.
2. Wpisz **login konta Synergia otrzymany ze szkoły i jego hasło**. To nie musi być adres e-mail Konta LIBRUS używanego w aplikacji mobilnej. Szczegóły wyjaśnia oficjalna [instrukcja logowania](https://portal.librus.pl/rodzina/artykuly/jak-zalogowac-sie-do-systemu-synergia-krotki-przewodnik-2).
3. Opcja **Zapamiętaj konto na tym komputerze** jest domyślnie wyłączona. Po jej zaznaczeniu dane logowania trafiają do lokalnego, zaszyfrowanego zapisu Windows.
4. Sprawdź oceny i najnowsze wiadomości, porównując je z oficjalnym panelem. Przy każdej sekcji zobaczysz czas ostatniego poprawnego odczytu i ewentualny błąd.

Nie przekazuj hasła, cookies ani pliku `state.dpapi` przez czat. Aplikacja nie wymaga wpisania danych konta do kodu, konsoli ani pliku `.env`.

Pierwsza poprawna synchronizacja każdej sekcji zapisuje stan początkowy bez powiadomień o wszystkich starych wpisach. Następne wykrywają nowe lub zmienione informacje. Przy ponownym uruchomieniu z tym samym kontem aplikacja wykorzystuje poprzedni stan odniesienia.

## Co obejmuje ta wersja

| Sekcja | Zakres |
|---|---|
| Przegląd | Liczba nieprzeczytanych w pobranej liście, ostatnie oceny i nadchodzące wydarzenia |
| Oceny | Numeryczne i opisowe wpisy udostępnione przez szkołę, daty, kategorie i komentarze |
| Wiadomości | Do 200 najnowszych nagłówków skrzynki, nadawca, data, status przeczytania; osobne pobieranie treści |
| Ogłoszenia | Ogłoszenia szkoły: tytuł, autor, data i treść; wyszukiwanie, kopia lokalna i powiadomienia o nowych lub zmienionych wpisach |
| Terminarz | Kalendarz miesięczny z listą poniżej lub sama lista; bieżący i kolejny miesiąc, w tym wydarzenia i sprawdziany |
| Frekwencja | Wpisy udostępnione przez Librusa, bez wyliczania własnego procentu obecności |
| Plan lekcji | Bieżący i kolejny tydzień, z informacjami o zmianach zwracanymi przez bibliotekę |
| Wyszukiwanie | Filtr w każdej sekcji i sortowanie list |
| Powiadomienia | Ogólny komunikat systemowy o liczbie zmian w sekcjach |
| Przypomnienia | Własna treść i termin alertu dla ogłoszenia, wydarzenia lub wiadomości; edycja, usuwanie i lista wykonanych alertów |
| Kopia lokalna | Ostatnie poprawnie pobrane dane wraz z czasem odczytu każdej sekcji |

**Treść wiadomości:** samo sprawdzanie listy nie wywołuje endpointu szczegółu wiadomości ani jawnego oznaczania odczytania. Przycisk **Pobierz treść (oznaczy jako przeczytaną)** pobiera szczegół; Librus może wtedy oznaczyć wiadomość jako przeczytaną. Tekst wyświetla się bez aktywnego HTML i zewnętrznych obrazów. Pełna pobrana treść nie trafia do trwałej kopii. Załączniki otwierasz w oficjalnym Librusie.

**Ogłoszenia:** treść jest pobierana razem z listą i dostępna po wybraniu wpisu. Pierwszy odczyt jest cichy; kolejne wykrywają nowe wpisy i zmiany treści. Przy błędzie pozostaje ostatnia poprawna kopia. Biblioteka nie udostępnia identyfikatora ogłoszenia, dlatego wpis rozpoznajemy po tytule, autorze i dacie; zmiana tych pól jest traktowana jako nowy wpis. Obsługa wymaga sprawdzenia na rzeczywistym koncie.

**Terminarz:** domyślnie pokazuje **Kalendarz + lista**. Strzałki zmieniają miesiąc, a liczba obok dnia wskazuje liczbę wydarzeń pasujących do wyszukiwania. Gwiazdka oznacza dzisiaj, a „+” nowe lub zmienione wpisy. Kliknięcie dnia zawęża listę poniżej; ponowne kliknięcie lub **Cały miesiąc** przywraca wpisy miesiąca. **Dzisiaj** wybiera aktualny dzień. Przycisk **Lista** ukrywa kalendarz i pokazuje wszystkie pobrane wpisy. Wybierz wydarzenie, aby zobaczyć szczegóły obok listy; dwuklik otwiera je w osobnym oknie. Nawigacja po miesiącach korzysta z lokalnej kopii. Miesiące poza zakresem ostatniego odczytu są oznaczone jako niepełne.

Nie ma jeszcze wysyłania odpowiedzi, usprawiedliwiania nieobecności, pobierania załączników, osobnego modułu prac domowych ani wielu kont jednocześnie. Przycisk **Otwórz w Librusie** prowadzi do oficjalnej strony wybranej sekcji; zwykła przeglądarka może poprosić o osobne logowanie.

Przycisk **Pobierz treść (oznaczy jako przeczytaną)** w Wiadomościach znajduje się w stałym pasku nad listą. Pozostaje widoczny również po zmniejszeniu lub zwinięciu podglądu wiadomości; aktywuje się po wybraniu wpisu.

## Jak działają powiadomienia

- Domyślnie aplikacja odświeża dane **co 15 minut**. Można wybrać 10, 15, 30 lub 60 minut.
- To okresowe sprawdzanie zmian, więc powiadomienie zwykle pojawi się przy najbliższym odczycie, a nie natychmiast po wpisie nauczyciela.
- Komputer musi działać, mieć internet, a aplikacja musi pozostawać uruchomiona. **Minimalizacja utrzymuje synchronizację; zamknięcie okna, wyłączenie albo uśpienie PC ją zatrzymuje.**
- Pierwszy odczyt jest cichy. Zmiana samego statusu „przeczytana” nie jest nowym zdarzeniem.
- Automatyczne powiadomienie o zmianach nie zawiera nazwiska dziecka, oceny ani treści wiadomości — szczegóły są w panelu. Dla własnego przypomnienia wybierasz, czy Windows pokazuje Twój tekst.
- Po błędach odświeżanie zwalnia. Odpowiedź HTTP 429 wstrzymuje nowe próby na co najmniej godzinę. Nie ma pętli ponownego logowania.
- Tryb „Nie przeszkadzać” i ustawienia Windows mogą ukryć okienko. Wysłanie testu oznacza przekazanie go systemowi, a nie gwarancję pokazania banera.

W kodzie poprawiono dwa błędy wysyłki: automatyczne wyliczanie kolekcji węzłów XML przez PowerShell podczas dodawania tekstu oraz traktowanie pustego stanu nowo zarejestrowanej aplikacji jako blokady. Skrypt wybiera teraz pojedyncze węzły przez `Item()` i odrzuca wysyłkę tylko przy jawnym stanie wyłączonych powiadomień. Po zmianie kodu trzeba ponownie uruchomić aplikację.

Ta wersja nie instaluje usługi Windows i nie dodaje się samodzielnie do autostartu. Jeśli po lokalnej weryfikacji chcesz ją uruchamiać po zalogowaniu do Windows, możesz dodać skrót do `Start.cmd` do własnego folderu Autostart. Nie jest potrzebny serwer ani hosting.

## Własne przypomnienia

1. W **Ogłoszeniach**, **Terminarzu** albo **Wiadomościach** wybierz wpis i kliknij **Przypomnij mi…**. W terminarzu przycisk działa także dla listy pod kalendarzem.
2. Wpisz własną treść (do 200 znaków), datę `dd.mm.rrrr` i godzinę `gg:mm`. Możesz użyć opcji **Za 15 minut**, **Za godzinę** lub **Jutro 09:00**. Termin musi być w przyszłości i jest interpretowany według czasu lokalnego komputera.
3. Opcja **Pokaż treść w powiadomieniu Windows** pozwala wybrać, czy baner ma zawierać Twój tekst. Po jej wyłączeniu Windows pokaże ogólne przypomnienie, a szczegóły będą w aplikacji.
4. Zakładka **Przypomnienia** pokazuje zaplanowane i wykonane alerty. Możesz je wyszukać, zmienić, usunąć albo wrócić do źródłowego wpisu. Zmiana terminu wykonanego alertu planuje go ponownie.

Każde przypomnienie jest jednorazowe. Zegar sprawdza terminy co sekundę, niezależnie od synchronizacji Librusa i ustawienia powiadomień o nowych danych. Alerty działają także offline oraz podczas odświeżania. Wskazany wpis może już zniknąć z pobieranej listy — przypomnienie zachowuje jego tytuł i wybrany termin.

**Aplikacja musi być uruchomiona lub zminimalizowana.** Zamknięcie aplikacji, uśpienie lub wyłączenie komputera wstrzymuje zegar. Po wznowieniu lub kolejnym uruchomieniu zaległe przypomnienia pojawią się przy najbliższym sprawdzeniu. To nie harmonogram zadań Windows i nie budzi komputera.

Alert otwiera też okno z treścią w aplikacji. Jeśli Windows nie przyjmie powiadomienia, alert jest oznaczony jako **Wykonane w aplikacji**. Tryb Nie przeszkadzać może ukryć baner mimo przyjęcia go przez Windows. Własne alerty mają osobne identyfikatory, więc powiadomienie o nowych danych nie zastępuje przypomnienia.

Przypomnienia zapisują się w tym samym zaszyfrowanym pliku co kopia danych, bez konieczności zapamiętywania hasła. Zachowują wykonany stan po restarcie i nie znikają przy zmianie roku szkolnego. Błąd zapisu jest pokazywany; jeśli nie uda się zapisać wykonania alertu, może on powtórzyć się po restarcie. W demo przypomnienia są tymczasowe i nie zmieniają zapisu prawdziwego konta. Zmiana konta oddziela jego przypomnienia, a **Usuń lokalne dane i konto** usuwa również alerty.

## Dane i hasło na komputerze

Windows zapisuje stan w:

```text
%LOCALAPPDATA%\SzkolnyPanel\state.dpapi
```

Zapis zawiera lokalną kopię danych, stan wykrywania zmian i własne przypomnienia. Login i hasło znajdują się w nim tylko po zaznaczeniu opcji zapamiętania konta. Cookies sesji nie są zapisywane. Cały plik jest szyfrowany przez Windows **DPAPI CurrentUser**, czyli mechanizm powiązany z kontem Windows; nie ma otwartego tekstowo pliku z hasłem. [Dokumentacja Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata) opisuje jego działanie.

DPAPI nie stanowi ochrony przed dowolnym programem już działającym z uprawnieniami tego samego zalogowanego użytkownika. Hasło i dane muszą być dostępne w pamięci procesu na czas pracy. W tej aplikacji nie ma zewnętrznej analityki ani wysyłania danych do modeli AI. Zapytania o dane konta trafiają tylko do dozwolonych hostów Librusa przez HTTPS. Biblioteki są pobierane z PyPI podczas przygotowania środowiska.

**Usuń lokalne dane i konto** usuwa zapis aplikacji, bez zmiany danych w szkole. Odznaczenie zapamiętywania przy ponownym połączeniu usuwa wcześniej zapisane hasło także wtedy, gdy nowa próba logowania się nie powiedzie. Jeśli zapis lub usuwanie pliku zawiedzie, aplikacja pokaże komunikat.

Przy starcie bez zapamiętanego hasła możesz przeglądać zaszyfrowaną kopię; odświeżanie wymaga zalogowania. Na systemach innych niż Windows magazyn działa wyłącznie w RAM, a natywne powiadomienia są wyłączone.

## Znane ograniczenia integracji

1. **Poprawione logowanie wymaga sprawdzenia na rzeczywistym koncie.** Wersję 0.1.1 sprawdzono lokalnie na całych sekwencjach HTTP z odpowiedziami syntetycznymi. Parser może wymagać dostosowania do konfiguracji szkoły lub kolejnych zmian Librusa. Publiczna biblioteka nie jest umową o stabilności API.
2. **Brak interaktywnego 2FA, CAPTCHA i logowania przez przeglądarkę.** Jeśli konto ich wymaga, ta wersja przerwie logowanie; nie wyłączaj dodatkowego zabezpieczenia tylko na potrzeby prototypu.
3. Oceny i dane szkolne pobiera `librus-apix==1.5.3`. Własny adapter logowania OAuth tworzy sesję przekazywaną tej bibliotece. Nowa skrzynka używa kopii sesji, odświeżenia tokenu istniejącej sesji i `wiadomosci3`. Podczas otwierania skrzynki przekierowanie GET do `http://wiadomosci.librus.pl` jest zamieniane na HTTPS przed przygotowaniem żądania i cookies. Działanie obu części trzeba potwierdzić na koncie.
4. Błąd sekcji nie jest interpretowany jako „brak danych”. Pozostaje jej ostatni udany odczyt i jawny komunikat błędu. Daty odczytów są ważniejsze niż sam fakt otwarcia aplikacji.
5. Licznik nieprzeczytanych dotyczy maksymalnie 200 pobranych wiadomości. To nie kompletne archiwum skrzynki.
6. Nie obliczamy prognoz ocen ani szkolnych średnich według arbitralnych zasad dla „+” i „−”.
7. Windows przyjął rzeczywiste testowe powiadomienie po poprawce skryptu PowerShell. Nie potwierdzono wizualnie wyświetlenia banera; może go ukryć tryb Nie przeszkadzać. Pełna weryfikacja interfejsu i połączenia na rzeczywistym koncie pozostaje do wykonania.

## Jeśli pojawi się problem

| Problem | Co sprawdzić |
|---|---|
| Skrypt nie znajduje Pythona | Zainstaluj standardowego Pythona z pip i tkinter; uruchom nowe okno terminala lub ponownie kliknij skrypt |
| Instalacja bibliotek się nie powiodła | Przeczytaj błąd konsoli, sprawdź dostęp do internetu; `Start.cmd` ponowi instalację przy kolejnym uruchomieniu |
| Brak okna | Uruchom `Sprawdz-srodowisko.cmd`; nie odczytuje konta ani hasła |
| Błąd logowania | Przekaż nowy komunikat z etapem i kodem HTTP. Sprawdź, czy to dane konta Synergia ze szkoły i czy logowanie na oficjalnej stronie działa |
| Oceny działają, wiadomości nie | Przekaż dokładny komunikat sekcji Wiadomości; nie dołączaj cookies ani odpowiedzi HTTP |
| Zablokowane przekierowanie skrzynki | Po ponownym uruchomieniu przekaż komunikat z polami „Powód” i „serwer”, jeśli są pokazane. Nazwa serwera Librusa pozwala sprawdzić brakującą domenę; pełny adres i parametry sesji są ukryte |
| Nie widać powiadomień | Pozostaw aplikację otwartą, sprawdź Test powiadomienia, ustawienia powiadomień Windows i Nie przeszkadzać |
| Brak „starych” powiadomień na starcie | To poprawne: pierwszy odczyt jest punktem odniesienia |
| Zmienił się Python lub zepsuła się `.venv` | Zamknij aplikację i usuń tylko folder `.venv` obok kodu. `Start.cmd` odbuduje go; plik danych konta leży osobno |

Podczas zgłaszania błędu logowania wystarczy wersja aplikacji i jej komunikat, np. `HTTP 403; etap: otwarcie formularza logowania`. W pozostałych problemach przydatna jest również wersja Pythona i wskazana sekcja. Przed wysłaniem zrzutu ukryj dane szkoły i dziecka. Nie wysyłaj pliku ze stanem konta.

## Dla programisty

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m szkolny_panel --demo
```

Uruchomienie testów:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest tests -q
```

| Plik | Odpowiedzialność |
|---|---|
| `szkolny_panel/ui.py` | Okno Tkinter, listy, formularz logowania, prezentacja błędów |
| `szkolny_panel/calendar_view.py` | Kalendarz terminarza, wybór dnia i miesiąca, przełączenie na pełną listę |
| `szkolny_panel/reminders.py` | Walidacja terminów, treści i zapisu własnych alertów |
| `szkolny_panel/reminder_view.py` | Formularz przypomnienia i lista zaplanowanych oraz wykonanych alertów |
| `szkolny_panel/core.py` | Worker, harmonogram, stan konta, odświeżanie, powiadomienia |
| `szkolny_panel/connector.py` | Autoryzacja, adaptery Librusa, limit czasu zapytań i normalizacja danych |
| `szkolny_panel/data.py` | Kopie danych, identyfikacja zmian i demonstracyjne wpisy |
| `szkolny_panel/platform_windows.py` | DPAPI i systemowe powiadomienia |
| `tests/` | Testy syntetyczne; brak prawdziwych danych logowania |

Nie ustawiaj globalnego polskiego `LC_TIME`: biblioteka `librus-apix` sprawdza dzień tygodnia jako angielskie `Monday`. UI formatuje polskie daty samodzielnie.

Powiadomienie wywołuje stały program PowerShell/WinRT. Tytuł i tekst przekazywane są oddzielnie przez JSON na stdin, bez wstawiania treści wiadomości do kodu. Nie ma dodatkowej zależności `winotify`.

## Wynik weryfikacji

**159 testów zaliczonych, 2 pominięte, dodatkowo 16 subtestów zaliczonych.** Środowisko: Windows, Python 3.12.10. Pominięte testy dotyczą wyłącznie zachowania poza Windows; test Windows DPAPI przeszedł. Testy używają osobnego magazynu danych, bez odczytywania ani zastępowania zapisanego konta użytkownika.

Sprawdzono m.in. pełny przebieg logowania na syntetycznym transporcie HTTP, zachowanie cookies, brak dodatkowego zapytania do głównego API, pojedyncze wysłanie hasła i sanitizację diagnostyki. Dalsze testy obejmują brak odczytywania treści podczas synchronizacji, paginację, mapowanie wpisów, blokowanie obcych przekierowań, zachowanie cache przy awarii, brak powtórnych powiadomień, zachowanie demo, dobrowolne zapamiętywanie hasła i usuwanie go przy błędzie dysku. Ogłoszenia sprawdzono na syntetycznej stronie HTML, wraz z wykrywaniem zmian treści, zachowaniem kopii przy awarii i odczytem starszego zapisu bez tej sekcji. Testy logowania blokują prawdziwą sieć; nie potwierdzają działania konkretnego konta. Osobny test interfejsu na danych demonstracyjnych sprawdził zakładkę Ogłoszenia, wyszukiwanie w treści, sortowanie i podgląd szczegółów.

Testy powiadomień uruchamiają rzeczywisty kod tworzenia XML w Windows PowerShell, sprawdzają zachowanie dla pustego i wyłączonego stanu oraz zatrzymują wykonanie przed wysyłką. Osobna próba pełnej funkcji `notify()` potwierdziła przyjęcie testu przez Windows.

Testy kalendarza w rzeczywistym Tkinter sprawdzają wybór dnia z kilkoma wydarzeniami, wyszukiwanie, odświeżanie szczegółów, przełączanie widoków, luty przestępny i nawigację poza zakresem kopii. Układ sprawdzono w oknach 1080×720 i 1240×850, również dla miesiąca zajmującego sześć rzędów dni.

Diagnostyka blokowanych przekierowań rozróżnia brak serwera na liście, HTTP, niedozwolony port i dane logowania w adresie. Pokazuje wyłącznie poprawną nazwę hosta w domenie Librusa, bez ścieżki, parametrów i danych sesji. Zgłoszony błąd skrzynki wskazał przekierowanie HTTP do `wiadomosci.librus.pl`; adapter zamienia je teraz na HTTPS. Testy całej sekwencji potwierdzają zachowanie parametrów i cookies Secure oraz pobranie listy wiadomości. Inne przekierowania HTTP są nadal blokowane, a błąd TLS nie powoduje przejścia na HTTP. Serwer skrzynki sprawdzono anonimowo przez HTTPS z weryfikacją certyfikatu; poprawka wymaga ponownej próby na rzeczywistym koncie.

## Źródła i licencje


- [Regulamin Synergii, §3 ust. 10](https://synergia.librus.pl/regulamin): opis API i komunikacji z zewnętrznymi aplikacjami; nie jest to techniczna dokumentacja integracji.
- [librus-apix na GitHub](https://github.com/RustySnek/librus-apix), [wydanie 1.5.3 na PyPI](https://pypi.org/project/librus-apix/1.5.3/): wersja z 29.09.2026, używana do odczytu danych szkolnych.
- [emsi/librus_pyapi](https://github.com/emsi/librus_pyapi/tree/1dfbb98d20ba84b459cb32319d7e273279ee959f): odniesienie dla logowania OAuth, nowej skrzynki, pól odpowiedzi i bootstrapu domeny.

Kod tej paczki udostępniono na AGPL-3.0-or-later; pełny tekst znajduje się w `LICENSE`. Informacje o zależnościach i rozbieżności metadanych licencji `librus-apix` są w `THIRD_PARTY_NOTICES.md`. Nazwy Librus i Synergia należą do ich właścicieli. Projekt jest niezależny od producenta dziennika.

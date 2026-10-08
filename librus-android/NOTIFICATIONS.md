# Powiadomienia i przypomnienia — Android 0.5.1

## Pobieranie danych i focus

Aplikacja próbuje pobrać dane po otwarciu, powrocie i ponownym uzyskaniu focusu. Pobiera je także podczas aktywności około co 5 minut. Wszystkie te wejścia oraz przycisk Odśwież korzystają z jednej przerwy: **minimum 5 minut między próbami pobrania danych**. Przerwa dotyczy też błędnego odczytu i jest zapisana lokalnie, więc restart procesu jej nie omija. W czasie przerwy aplikacja pokazuje ostatnią kopię.

Pierwsze logowanie pobiera dane, jeśli przerwa nie obowiązuje. Ręczne ponowne logowanie w czasie przerwy może połączyć sesję; odczyt danych czeka do jej końca. Treść jednej wiadomości nadal jest pobierana osobno na wyraźne żądanie. HTTP 429 ma nadrzędną przerwę co najmniej godziny, także dla logowania i treści wiadomości.

## Powiadomienia o zmianach

1. W Ustawieniach włącz **Powiadomienia o nowych danych w tle**.
2. Na Androidzie 13 lub nowszym zaakceptuj zgodę na powiadomienia. Android 12 nie ma tego dodatkowego okna.
3. Wybierz **Test powiadomienia**. Wynik mówi o przyjęciu przez system, a nie o gwarancji pokazania banera.

Od 0.4.0 przy wykryciu zmian poza focusem aplikacji otrzymasz liczby zmian w ocenach, frekwencji, wiadomościach, ogłoszeniach, terminarzu, planie lekcji i zadaniach domowych. Otwarte i aktywne okno pokazuje dane bez dodatkowego alertu systemowego. Powrót do tła nie wysyła ponownie zmian pobranych podczas korzystania z aplikacji. Zbiorczy alert jest wysyłany po zapisaniu kopii; błąd pojedynczej sekcji nie blokuje alertu o poprawnie pobranych pozostałych sekcjach. Nowy temat lub zmieniony termin istniejącego zadania są zmianami; pierwsze pobranie listy zadań po aktualizacji aplikacji jest ciche. Pobranie pełnej treści na żądanie nie tworzy zdarzenia o zmianie. Powiadomienie nie zawiera nazwiska, oceny, tematu wiadomości ani treści szkolnego wpisu. Pierwszy odczyt każdej sekcji i brak zmian są ciche. Zmiana samego statusu przeczytania wiadomości nie jest nowym powiadomieniem. Dotknięcie powiadomienia o zmianach otwiera Przegląd, również jeśli poprzednio używano innego panelu.

## Odświeżanie po zamknięciu aplikacji

Opcja **Pobieraj dane w tle** jest domyślnie wyłączona. Aby ją włączyć, połącz konto z zaznaczonym zapamiętaniem konta. Android planuje odczyt w wybranym interwale **15, 30 lub 60 minut**, domyślnie 15, i tylko z dostępną siecią; może go opóźnić. System może uruchomić usługę zadania także po zakończeniu procesu aplikacji; otwarte okno nie jest wymagane. Harmonogram jest zapisany przez Androida i odtwarzany po restarcie telefonu. Aktualizacja 0.3.1 wymienia poprzedni harmonogram 30 minut na 15 minut, jeśli użytkownik miał włączoną pracę w tle. Praca w tle nie daje harmonogramu co 5 minut ani gwarancji odczytu dokładnie co kwadrans. Wspólny limit 5 minut nadal uniemożliwia nałożenie bliskich prób z focusu i zadania w tle.

Demo, rezygnacja z zapamiętania konta i usunięcie danych wyłączają pracę w tle. Nieudane logowanie automatyczne zostaje wstrzymane; po poprawieniu konta włącz odczyt w tle ponownie. Nie ma stałej usługi ani trwałego powiadomienia o działaniu aplikacji. Android budzi krótkie zadanie odczytu; powiadomienia o nowych danych wymagają oddzielnego włącznika **Powiadomienia o nowych danych w tle** i dostępnego kanału Androida.

### Włączenie odczytu co około 15 minut

1. Zaloguj się przez **Konto**, zaznaczając zapamiętanie konta. Bez zapisanego hasła zakończony proces nie może samodzielnie połączyć nowej sesji.
2. W **Ustawieniach** włącz **Pobieraj dane w tle**.
3. Aby otrzymywać alerty, włącz też **Powiadomienia o nowych danych w tle** i zezwól na powiadomienia Androida.
4. Na Samsungu sprawdź, czy LibrusApp nie jest na liście uśpionych aplikacji i czy ma dozwolone działanie w tle. Ograniczenia baterii lub tryb Doze mogą opóźniać odczyt.

Zamknięcie okna i zakończenie procesu przez system są obsługiwane. **Wymuś zatrzymanie** w ustawieniach telefonu blokuje zaplanowaną pracę do ponownego otwarcia aplikacji. Po aktualizacji użytkownik z wcześniej wyłączonym odczytem nadal ma go wyłączonego. Opcja pozostaje domyślnie wyłączona; nie dodano automatycznego zapisywania hasła.

## Odnawianie sesji zapamiętanego konta (0.5.1)

Wygaśnięcie istniejącej sesji nie wyłącza pobierania w tle i nie blokuje automatycznego logowania, jeśli konto jest zapamiętane. Nie ma ponawiania odczytu w tej samej operacji: kolejna dozwolona próba może połączyć konto i pobrać dane. Obowiązuje wspólne minimum 5 minut; w tle termin wybiera JobScheduler, a przerwa nocna i blokada HTTP 429 nadal mają zastosowanie. Poprzednia kopia i historia zmian są zachowane, więc odzyskanie tej samej zawartości nie tworzy powtórnych alertów.

Jeżeli logowanie zapisanymi danymi zostanie odrzucone albo nowo utworzona sesja również wymaga logowania, aplikacja wstrzymuje kolejne automatyczne próby. Wtedy połącz konto ręcznie i ponownie włącz pobieranie w tle, jeśli zostało wyłączone. Bez opcji **Zapamiętaj konto na tym telefonie** ponowne logowanie po zakończeniu procesu lub utracie sesji jest wymagane.

Aktualizacja nie usuwa istniejącej blokady automatycznego logowania: starszy zapis nie rozróżnia wygasłej sesji od odrzuconego hasła. Jeśli blokada już wystąpiła, jednorazowo połącz konto po aktualizacji. Kolejne wygaśnięcia aktywnej sesji podlegają nowej obsłudze. Własne przypomnienia korzystają z oddzielnego lokalnego magazynu i AlarmManager; brak sesji nie blokuje ich wysyłki.

## Interwał i przerwa nocna (0.4.1)

W Ustawieniach wybierz **Interwał pobierania w tle: 15 / 30 / 60 minut**. Zmiana aktualizuje jeden istniejący harmonogram Androida; nie tworzy dodatkowego zadania i nie włącza wyłączonego pobierania w tle. Ustawienie jest zachowane po ponownym uruchomieniu i aktualizacji aplikacji. Interwał otwartej aplikacji i wspólne minimum 5 minut pozostają bez zmian.

**Przerwa nocna 20:00–07:00** jest domyślnie włączona, również po aktualizacji ze starszej wersji. W tym czasie nowe próby pobierania w tle są pomijane przed uruchomieniem Pythona, odczytem konta i logowaniem. Harmonogram Androida pozostaje zapisany. Odczyt rozpoczęty przed przerwą może się dokończyć. Po 07:00 pobieranie wraca przy kolejnym terminie zadania wybranym przez Androida; nie ma gwarancji odczytu dokładnie o 07:00.

Godziny odnoszą się do aktualnej strefy czasu telefonu: 20:00 jest już przerwą, a 07:00 jest już poza nią. Możesz wyłączyć przerwę przełącznikiem. Otwarcie aplikacji w nocy pozwala pobrać dane z zachowaniem limitu 5 minut. Własne przypomnienia i ich odroczenia działają niezależnie od przerwy.

## Własne przypomnienie

1. Otwórz szczegóły wiadomości, ogłoszenia, wydarzenia lub zadania domowego.
2. Wybierz **Przypomnij mi…**.
3. Wpisz własny tekst (1–200 znaków), wybierz przyszłą datę i godzinę. Skróty: **Za 30 minut**, **Za godzinę**, **Jutro 08:00**, **Za tydzień**. Dla wydarzeń i zadań dostępny jest **Dzień wcześniej o 18:00**, a przy podanej godzinie także **Godzinę wcześniej**. Skrót dla minionego terminu pokazuje błąd i nie zmienia wyboru.
4. Domyślnie powiadomienie systemowe ma ogólną treść. Aby ujawnić własny tekst w powiadomieniu, zaznacz **Pokaż mój tekst w powiadomieniu**. Aplikacja dostarcza ogólną wersję na ekran blokady; ostateczną widocznością treści sterują ustawienia Androida i kanału powiadomień.
5. Zapisz. W panelu **Przypomnienia** znajdziesz termin, status i źródło.

Przypomnienie nie wymaga internetu ani zalogowanej sesji. Android może uruchomić odbiornik alarmu po zakończeniu procesu. Po restarcie telefonu alarmy są odtwarzane po odblokowaniu urządzenia; zaległe terminy zostaną obsłużone. Powiadomienie o przypomnieniu działa niezależnie od włącznika zmian w dzienniku.

Dotknięcie powiadomienia otwiera zapisane przypomnienie i ponownie odczytuje jego status, także gdy aplikacja pozostała w tle z wcześniejszym stanem „Zaplanowane”. **Pokaż wpis** przechodzi do źródła; jeśli wpis opuścił pobrany zakres, aplikacja wyjaśnia brak źródła i zachowuje alert. Listy przypomnień są rozdzielone między profile i demo; powiadomienie starego profilu nie przełącza samoczynnie konta.

Edycja może zmienić treść, termin i widoczność tekstu. Edycja wykonanego alertu planuje go ponownie. Usunięcie dotyczy tylko lokalnego alertu, nie wpisu w szkole. Usunięcie wszystkich lokalnych danych usuwa także alarmy i historię przypomnień.

## Odroczenie z powiadomienia

Rozwiń powiadomienie przypomnienia i wybierz **Przypomnij za 30 minut**. Alert wróci do stanu Zaplanowane z terminem 30 minut od kliknięcia; stary baner zniknie. Aplikacja nie musi mieć otwartego okna, a internet nie jest potrzebny. Treść, źródło i wybrana prywatność są zachowane. Nowy termin podlega tym samym uprawnieniom i ograniczeniom alarmów Androida.

Ponowne kliknięcie tej samej akcji nie przesuwa terminu drugi raz. Stary przycisk po edycji, usunięciu lub kolejnym wykonaniu przypomnienia jest ignorowany. Powiadomienie testowe nie ma akcji odroczenia.

## Uprawnienia i ograniczenia systemowe

W Ustawieniach dostępne są **Ustawienia powiadomień Androida** oraz **Zgoda na dokładne alarmy**. Zgoda na dokładne alarmy jest oddzielna od zgody na powiadomienia. Bez niej aplikacja zachowuje przypomnienie i planuje alarm, który system może opóźnić.

Kanały Androida są oddzielne: **Zmiany w dzienniku** i **Twoje przypomnienia**. Wyłączenie kanału, brak zgody na Androidzie 13+, tryb Nie przeszkadzać i ograniczenia baterii mogą ukrywać lub opóźniać alert. Nawet dokładne alarmy podlegają ograniczeniom Androida dotyczącym uśpienia i częstotliwości. Na Samsungu w razie opóźnień sprawdź ustawienia baterii i uśpionych aplikacji dla LibrusApp.

Zwykłe zamknięcie lub usunięcie aplikacji z listy ostatnich nie jest tym samym co **Wymuś zatrzymanie** w ustawieniach Androida. Po wymuszonym zatrzymaniu otwórz LibrusApp ponownie, aby odtworzyć alarmy i zadania. Wyłączony telefon nie pokaże alertu w pierwotnym terminie.

Termin jest zapisany jako konkretna chwila; zmiana strefy czasu zmienia jego lokalny opis, a nie chwilę wykonania. Nieistniejąca godzina podczas wiosennej zmiany czasu jest odrzucana. Przy podwójnej godzinie jesienią wybierane jest pierwsze wystąpienie; szczegóły pokazują offset.

## Statusy i dane

- Zaplanowane / Zaległe — oczekuje na alarm.
- Przekazane do Androida — system przyjął powiadomienie; baner może być wyciszony.
- Wykonane — powiadomienia zablokowane / błąd wysyłki.
- Wykonane — wynik wysyłki niepotwierdzony, jeśli proces zakończył się między zapisem wykonania i potwierdzeniem wysyłki.

Przypomnienia są przechowywane oddzielnie od dziennika, w prywatnym zaszyfrowanym pliku AES-GCM z kluczem Android Keystore. Zapis wykonania poprzedza wysyłkę, aby równoległe alarmy i restart nie powtarzały już podjętej próby. Rzadka awaria procesu w tym miejscu może pozostawić niepotwierdzony wynik; sprawdź historię. Błąd zapisu nie jest traktowany jako sukces i nie usuwa poprzedniego pliku.

Dokumentacja Androida: [alarmy](https://developer.android.com/develop/background-work/services/alarms), [zgoda na powiadomienia](https://developer.android.com/develop/ui/views/notifications/notification-permission), [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler), [harmonogram okresowy i trwałość po restarcie](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long)).

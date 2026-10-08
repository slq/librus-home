# Synchronizacja z kalendarzem Google — Android 0.6.0

## Włączenie

1. Dodaj konto Google w ustawieniach Androida i włącz synchronizację kalendarza. W aplikacji Kalendarz Google sprawdź synchronizację wybranego kalendarza domowego.
2. W LibrusApp połącz konto Librusa. Integracja może użyć również zapisanej kopii offline; aktywne logowanie do Librusa nie jest potrzebne do skopiowania już pobranych terminów.
3. Otwórz **Ustawienia → Kalendarz Google → Wybierz kalendarz i źródła** i zezwól na odczyt oraz zapis kalendarza.
4. Wybierz kalendarz docelowy, źródła i opcję dodatkowego alertu Google. Zatwierdź **Zapisz i synchronizuj**.

Synchronizacja jest domyślnie wyłączona. Lista obejmuje kalendarze kont Google dostępne na telefonie, ze włączoną synchronizacją i prawem dodawania wydarzeń. Nie wyświetla kalendarzy lokalnych ani tylko do odczytu. Dla kalendarza współdzielonego konto na telefonie musi mieć odpowiedni dostęp. Tytuły i własne notatki zostaną skopiowane do wybranego kalendarza i mogą być dostępne jego współużytkownikom.

## Zakres

| Źródło | Wydarzenie Google |
|---|---|
| Terminarz | Tytuł i przedmiot, data całodniowa lub rozpoznana godzina z długością 60 minut |
| Zadania domowe | Całodniowe wydarzenie w dniu oddania, temat, przedmiot i dostępny link do Librusa |
| Własne przypomnienie | Własna notatka jako tytuł, termin i długość 15 minut; opcjonalny alert na początku wydarzenia |
| Wiadomości i ogłoszenia | Eksport przez powiązane własne przypomnienie; data publikacji nie tworzy automatycznie terminu w kalendarzu |

Pierwszy eksport obejmuje nadchodzące terminy. Pełna treść wiadomości, zadań i załączniki nie są kopiowane. Demo nie eksportuje danych. Konfiguracja jest przypisana do profilu Librusa: zmiana profilu wstrzymuje zapis do wcześniej wybranego kalendarza, dopóki użytkownik nie skonfiguruje integracji dla nowego konta.

## Aktualizacje i usuwanie

Po zapisaniu nowego odczytu danych aplikacja aktualizuje kalendarz. Zapis, edycja, usunięcie i odroczenie własnego przypomnienia zlecają oddzielny eksport, niezależny od internetowego pobierania danych i przerwy nocnej. Wpisy są rozpoznawane po metadanych źródła i profilu w dostawcy kalendarza. Ponowne uruchomienie synchronizacji na tym samym urządzeniu aktualizuje te same wydarzenia zamiast tworzyć następne. Bez stabilnego ID źródła zmiana jego identyfikatora może być potraktowana jako nowy wpis. Nie jest to synchronizacja wielu niezależnych instalacji aplikacji.

Zmiana terminu lub tytułu aktualizuje istniejący wpis o zachowanym identyfikatorze źródła. Usunięcie własnego przypomnienia usuwa jego kopię. Zniknięcie szkolnego wpisu z ograniczonego odczytu nie usuwa wydarzenia z Google — terminarz jest pobierany tylko dla bieżącego i następnego miesiąca, a błąd sekcji również nie jest potwierdzeniem usunięcia.

Zmiany w Google nie są przesyłane do szkoły. Przy kolejnym eksporcie aplikacja przywraca zarządzane tytuły i terminy. Wyłączenie integracji lub źródła pozostawia wcześniejsze kopie. Zmiana kalendarza tworzy kopie w nowym miejscu i nie usuwa wcześniejszych. **Usuń skopiowane wpisy** usuwa wyłącznie wydarzenia oznaczone przez LibrusApp dla skonfigurowanego profilu w wybranym kalendarzu i wyłącza eksport. Pozostałe wydarzenia pozostają bez zmian.

## Powiadomienia i status

Alarm LibrusApp pozostaje niezależny. Opcja **Powiadamiaj również w Google o własnych przypomnieniach** jest domyślnie wyłączona; po jej włączeniu mogą pojawić się dwa powiadomienia. Powiadomienia członków współdzielonego kalendarza zależą od ich własnych ustawień.

Status w aplikacji potwierdza zapis do kalendarza telefonu. Przesłaniem do Google steruje synchronizacja konta Androida; aplikacja nie potwierdza odbioru przez serwer. Bez internetu lokalny zapis może zostać przesłany później. Odmowa uprawnień, niedostępny kalendarz, zmiana profilu lub błąd lokalnego zapisu pozostawiają czytelny komunikat i nie usuwają poprzednich kopii. **Synchronizuj teraz** ponawia eksport z lokalnej kopii, bez dodatkowego pobrania Librusa.

## Weryfikacja i ograniczenia

Testy używają rzeczywistego Calendar Provider API 31, fikcyjnego kalendarza typu Google bez prawdziwego konta oraz syntetycznych danych szkolnych. Nie wysyłają wydarzeń do chmury. Sprawdzana jest selekcja kalendarzy, dodawanie, brak duplikatów, aktualizacja, daty i alarmy, odroczenie, usuwanie tylko własnych wpisów, brak eksportu demo/innego profilu, błędy zgody i kopii, ekran konfiguracji oraz usługa eksportu w tle.

Do sprawdzenia przesyłania do chmury należy dodać własne konto Google na telefonie lub emulatorze, wybrać docelowy kalendarz i porównać wydarzenie z [calendar.google.com](https://calendar.google.com). Czyszczenie pamięci kalendarza Androida lub konfiguracja kolejnego urządzenia wymagają osobnego sprawdzenia istniejących kopii; nie należy traktować tej wersji jako globalnego mechanizmu deduplikacji po wymianie telefonu.

Dokumentacja: [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider), [synchronizacja konta Google](https://support.google.com/calendar/answer/6261951?hl=pl).
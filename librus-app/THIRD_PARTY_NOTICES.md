# Źródła zewnętrzne i informacje licencyjne

## LibrusApp

Kod aplikacji i jej testy: AGPL-3.0-or-later. Pełny tekst licencji znajduje się w `LICENSE`. Cały kod źródłowy aplikacji znajduje się w tej paczce; nie zawiera ona zamkniętego pliku wykonywalnego.

## librus-apix 1.5.3

Autor: Pascal Jodłowski / RustySnek.

- Repozytorium: <https://github.com/RustySnek/librus-apix>
- Dystrybucja: <https://pypi.org/project/librus-apix/1.5.3/>
- Użycie: obiekt sesji oraz parsery ocen, frekwencji, terminarza i planu lekcji. Od wersji 0.1.1 logowaniem zajmuje się własny adapter panelu.

Odczytany plik `LICENSE` w repozytorium i wheelu 1.5.3 zawiera GPL v3. Metadane PyPI deklarują MIT, co jest rozbieżnością w projekcie źródłowym. W tej paczce zachowano kopię rzeczywistego tekstu jako `licenses/librus-apix-GPL-3.0.txt`. Biblioteka instalowana jest oddzielnie z PyPI, a nie kopiowana do źródeł aplikacji.

## emsi/librus_pyapi

- Autor/maintainer: emsi.
- Źródło odniesienia: <https://github.com/emsi/librus_pyapi/tree/1dfbb98d20ba84b459cb32319d7e273279ee959f>
- Wersja deklarowana w projekcie: 0.2.0.
- Licencja źródła: AGPL v3.

Publiczny kod posłużył do ustalenia kolejności logowania OAuth od wejścia Synergii, bootstrapu domeny `wiadomosci.librus.pl`, adresów listy/szczegółu skrzynki i mapowania odpowiedzi. LibrusApp ma własny adapter `connector.py`, który tworzy sesję dla apix i zachowuje osobną kopię cookies dla wiadomości. Nie instaluje `librus_pyapi` ani nie kopiuje jego pełnego pakietu. Informację o pochodzeniu zachowano również w module adaptera.

## Pozostałe zależności

`requests`, `beautifulsoup4`, `lxml`, `aiohttp` oraz ich zależności są pobierane przez pip. Ich oryginalne licencje i informacje o autorach znajdują się w zainstalowanych pakietach i ich metadanych. Wersje głównych zależności zapisano w `requirements.txt`. Tkinter jest częścią standardowej dystrybucji Pythona z Tcl/Tk.

## Usługa źródłowa

LIBRUS, Synergia i nazwy usług należą do ich właścicieli. LibrusApp jest niezależnym, nieoficjalnym klientem; nie sugeruje afiliacji ani wsparcia ze strony Librusa.

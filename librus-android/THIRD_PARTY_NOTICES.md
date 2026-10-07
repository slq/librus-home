# LibrusApp Android — źródła i licencje

Kod aplikacji: AGPL-3.0-or-later, pełny tekst w LICENSE. Projekt jest niezależnym i nieoficjalnym klientem LIBRUS Synergia.

## Integracja z desktopem

Pliki app/src/main/python/librus_shared/connector.py i data.py są kopiami modułów LibrusApp 0.1.1 z projektu librus-app. tools/sync_shared.py sprawdza ich zgodność. Android ma własny interfejs, cykl sesji i szyfrowanie Android Keystore; nie używa Tkinter ani DPAPI.

Adapter OAuth i skrzynki powstał z wykorzystaniem emsi/librus_pyapi 0.2.0 (AGPL v3), commit 1dfbb98d20ba84b459cb32319d7e273279ee959f: https://github.com/emsi/librus_pyapi/tree/1dfbb98d20ba84b459cb32319d7e273279ee959f

## librus-apix 1.5.3

Autor: Pascal Jodłowski / RustySnek. Źródło: https://github.com/RustySnek/librus-apix

Pakiet jest instalowany do APK przez pip. Faktyczny tekst LICENSE zawiera GPL v3, mimo że metadane PyPI deklarują MIT. Kopia rzeczywistej licencji znajduje się w licenses/librus-apix-GPL-3.0.txt. Oryginalne informacje zawarte w pakiecie zachowano w jego dystrybucji.

## Runtime i biblioteki

Chaquopy 17.0.0: https://github.com/chaquo/chaquopy — MIT. Runtime CPython: licencja Python Software Foundation i informacje o komponentach w dystrybucji.

requests, beautifulsoup4, lxml, aiohttp i zależności są instalowane z PyPI lub indeksu Android Chaquopy. lxml ma licencję BSD; libxml2 i libxslt mają własne informacje licencyjne. Oryginalne metadane bibliotek pozostają w paczkach. Wersje głównych zależności opisuje app/requirements-android.txt, a faktycznie rozstrzygnięty zestaw raport kompilacji.

## Udostępnianie

Przy przekazywaniu APK dalej należy udostępnić odpowiadający mu kod aplikacji i właściwe źródła komponentów objętych obowiązkiem ich udostępnienia, wraz z licencjami i instrukcją budowy. Sama paczka APK nie zastępuje tych obowiązków. Nie zmieniaj oznaczenia projektu na MIT wyłącznie na podstawie metadanych librus-apix.

Nazwy LIBRUS i Synergia należą do ich właścicieli. Aplikacja nie sugeruje afiliacji ani oficjalnego wsparcia producenta dziennika.

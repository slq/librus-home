# Librus Home

Repozytorium lokalnej aplikacji **LibrusApp** do konta LIBRUS Synergia. Aplikacja jest napisana w Pythonie i korzysta z Tkinter; pokazuje dane dziennika w jednym oknie na Windows oraz przypomina o ważnych sprawach.

Projekt jest niezależnym, nieoficjalnym klientem. Nie jest powiązany z producentem Librusa. Aktualna wersja aplikacji: **0.1.1**; status: prototyp rozwijany i sprawdzany lokalnie.

## Możliwości

- Oceny, wiadomości, ogłoszenia, frekwencja i plan lekcji.
- Terminarz w widoku kalendarza z listą poniżej lub samej listy.
- Wyszukiwanie i sortowanie wpisów oraz osobne pobieranie treści wiadomości.
- Powiadomienia Windows o nowych lub zmienionych danych.
- Własne przypomnienia z treścią i terminem dla wiadomości, ogłoszeń i wydarzeń.
- Zaszyfrowana kopia lokalna danych i opcjonalne zapamiętanie konta przez Windows DPAPI.
- Tryb demo z fikcyjnymi danymi, bez logowania do Librusa.

## Szybki start

Potrzebujesz Windows i standardowego Pythona **3.11 lub nowszego** z `pip` oraz `tkinter`. Testy uruchamiane są na Pythonie 3.12.

1. Sklonuj repozytorium lub rozpakuj jego ZIP do zwykłego folderu.
2. Otwórz folder [`librus-app`](librus-app/).
3. Uruchom [`Demo.cmd`](librus-app/Demo.cmd), aby obejrzeć aplikację na danych przykładowych.
4. Uruchom [`Start.cmd`](librus-app/Start.cmd), aby połączyć własne konto Synergia.

Przy pierwszym uruchomieniu skrypt tworzy `.venv` i instaluje zależności z PyPI; potrzebny jest internet. Instalator EXE nie jest jeszcze dostępny.

[README aplikacji](librus-app/README.md) zawiera instrukcję logowania, konfigurację przypomnień, informacje o danych lokalnych, ograniczenia integracji i rozwiązywanie problemów.

## Struktura repozytorium

```text
librus-home/
├── README.md
├── .gitignore
└── librus-app/
    ├── README.md
    ├── Start.cmd
    ├── Demo.cmd
    ├── Sprawdz-srodowisko.cmd
    ├── requirements.txt
    ├── requirements-dev.txt
    ├── librus_app/            # Kod aplikacji
    ├── tests/                 # Testy na danych syntetycznych
    ├── LICENSE
    ├── THIRD_PARTY_NOTICES.md
    └── licenses/
```

## Rozwój i testy

Uruchom w PowerShell z katalogu głównego repozytorium:

```powershell
cd librus-app
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m librus_app --demo
.\.venv\Scripts\python.exe -m pytest tests -q
```

Testy używają osobnego magazynu danych i odpowiedzi syntetycznych. Nie potwierdzają zgodności integracji z każdym kontem lub konfiguracją szkoły.

## Prywatność i licencja

Zapis konta znajduje się poza repozytorium, domyślnie w `%LOCALAPPDATA%\LibrusApp\state.dpapi`. Starsze instalacje zachowują obsługę zapisu w `%LOCALAPPDATA%\SzkolnyPanel`. Hasło jest zapisywane tylko po wybraniu opcji zapamiętania konta. Szczegóły ochrony danych opisano w [README aplikacji](librus-app/README.md#dane-lokalne-i-prywatność).

Nie dodawaj do repozytorium danych logowania, plików `state.dpapi`, cookies ani zrzutów ekranu z danymi uczniów lub nauczycieli. `.gitignore` wyklucza środowiska lokalne, cache Pythona i typowe pliki z danymi konta.

Kod aplikacji jest udostępniony na **AGPL-3.0-or-later**. Zachowaj [licencję](librus-app/LICENSE) oraz [informacje o źródłach i zależnościach](librus-app/THIRD_PARTY_NOTICES.md), w tym opisaną rozbieżność metadanych licencji `librus-apix`.

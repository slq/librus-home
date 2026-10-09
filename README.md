# Librus Home

Repozytorium aplikacji **LibrusApp** do konta LIBRUS Synergia: wersji desktopowej dla Windows oraz mobilnej dla Androida. Obie pokazują dane dziennika i przechowują zaszyfrowaną kopię na urządzeniu.

Projekt jest niezależnym, nieoficjalnym klientem. Nie jest powiązany z producentem Librusa. Wersja desktopowa: **0.1.1**, Android: **0.12.1**. Status: prototypy rozwijane i sprawdzane lokalnie.

## Możliwości

Repozytorium zawiera dwie aplikacje: [LibrusApp dla Windows](librus-app/README.md) i [LibrusApp dla Androida](librus-android/README.md). Wersja Android ma APK do instalacji przez USB, z logowaniem, odczytem danych po otwarciu, limitem 5 minut, lokalnymi przypomnieniami, zadaniami domowymi i opcjonalnymi powiadomieniami. Od 0.12.1 ma wbudowany aktualizator APK z GitHub Releases, opis zmian i instalację z potwierdzeniem Androida. Od 0.11.0 zachowuje pobrane ogłoszenia w zaszyfrowanym archiwum konta, także po ich zniknięciu z Librusa i zmianie roku. Od 0.10.0 ma wspólną wyszukiwarkę wszystkich pobranych sekcji i własnych przypomnień, dostępną ze Startu i Więcej. Od 0.9.0 pokazuje na Starcie nowe i zmienione wpisy od poprzedniego wejścia, także pobrane w tle. Od 0.8.0 pozwala oznaczać zadania jako zrobione i cofać ten lokalny status. Od 0.7.0 ma ekran Start „Dzisiaj / Jutro” i dolne menu z pozostałymi panelami pod Więcej. Jej [zakres pierwszej wersji](librus-android/MVP_SCOPE.md) opisuje adaptację wymagań desktopu.

- Oceny, wiadomości, ogłoszenia, frekwencja i plan lekcji.
- Terminarz w widoku kalendarza z listą poniżej lub samej listy.
- Wyszukiwanie i sortowanie wpisów oraz osobne pobieranie treści wiadomości.
- Powiadomienia Windows o nowych lub zmienionych danych.
- Własne przypomnienia z treścią i terminem dla wiadomości, ogłoszeń i wydarzeń.
- Zaszyfrowana kopia lokalna danych i opcjonalne zapamiętanie konta przez Windows DPAPI.
- Tryb demo z fikcyjnymi danymi, bez logowania do Librusa.

## Szybki start — Windows

Potrzebujesz Windows i standardowego Pythona **3.11 lub nowszego** z `pip` oraz `tkinter`. Testy uruchamiane są na Pythonie 3.12.

1. Sklonuj repozytorium lub rozpakuj jego ZIP do zwykłego folderu.
2. Otwórz folder [`librus-app`](librus-app/).
3. Uruchom [`Demo.cmd`](librus-app/Demo.cmd), aby obejrzeć aplikację na danych przykładowych.
4. Uruchom [`Start.cmd`](librus-app/Start.cmd), aby połączyć własne konto Synergia.

Przy pierwszym uruchomieniu skrypt tworzy `.venv` i instaluje zależności z PyPI; potrzebny jest internet. Instalator EXE nie jest jeszcze dostępny.

[README aplikacji](librus-app/README.md) zawiera instrukcję logowania, konfigurację przypomnień, informacje o danych lokalnych, ograniczenia integracji i rozwiązywanie problemów.

[USER_STORIES.md](librus-app/USER_STORIES.md) zawiera historie użytkownika dla wszystkich paneli, kryteria akceptacji, scenariusze testowe oraz kwestie do rozstrzygnięcia przed stworzeniem wersji mobilnej.

## Struktura repozytorium

```text
librus-home/
├── README.md
├── .gitignore
├── librus-app/               # Aplikacja Windows
│   ├── README.md
│   ├── USER_STORIES.md
│   ├── Start.cmd
│   ├── Demo.cmd
│   ├── Sprawdz-srodowisko.cmd
│   ├── requirements.txt
│   ├── requirements-dev.txt
│   ├── librus_app/
│   ├── tests/
│   ├── LICENSE
│   ├── THIRD_PARTY_NOTICES.md
│   └── licenses/
└── librus-android/           # Aplikacja Android
    ├── README.md
    ├── MVP_SCOPE.md
    ├── TESTING.md
    ├── NOTIFICATIONS.md
    ├── HOMEWORK.md
    ├── Build.ps1
    ├── Install-USB.ps1
    ├── app/
    ├── tests/
    └── tools/
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

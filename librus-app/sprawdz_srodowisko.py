"""Local, read-only runtime diagnostic. Never reads the account or state.dpapi."""

from importlib import metadata
import platform
import sys

from librus_app import __version__

print(f"LibrusApp {__version__} - sprawdzenie srodowiska (bez logowania)")
print("System:", platform.system(), platform.release())
print("Python:", platform.python_version(), "architektura:", platform.machine())
for name in ("librus-apix", "requests", "beautifulsoup4", "lxml", "aiohttp"):
    try:
        print(name + ":", metadata.version(name))
    except metadata.PackageNotFoundError:
        print(name + ": BRAK")
try:
    import tkinter
    print("tkinter: OK, Tk", tkinter.TkVersion)
except ImportError:
    print("tkinter: BRAK")
print("Dane konta, hasla i kopia dziennika nie byly odczytywane.")
print("Ten test nie potwierdza dzialania logowania, okna ani powiadomien.")

"""Keep Windows test storage separate from the user's saved account."""

import sys

import pytest


@pytest.fixture(autouse=True)
def isolated_windows_storage(monkeypatch, tmp_path):
    if sys.platform == "win32":
        monkeypatch.setenv("LOCALAPPDATA", str(tmp_path))


@pytest.fixture(scope="session")
def tk_root():
    import tkinter as tk

    try:
        root = tk.Tk()
    except tk.TclError as error:
        if "display" in str(error).lower():
            pytest.skip("Tk display is unavailable")
        raise
    root.withdraw()
    yield root
    root.destroy()

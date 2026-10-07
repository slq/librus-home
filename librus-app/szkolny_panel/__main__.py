from __future__ import annotations

import argparse
import os
import sys


def _single_instance():
    """Prevent two Windows windows from polling and replacing the same cache."""
    if os.name != "nt":
        return None, None
    import ctypes
    kernel = ctypes.WinDLL("kernel32.dll", use_last_error=True, winmode=0x800)
    kernel.CreateMutexW.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_wchar_p]
    kernel.CreateMutexW.restype = ctypes.c_void_p
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    kernel.CloseHandle.restype = ctypes.c_int
    handle = kernel.CreateMutexW(None, False, "Local\\SzkolnyPanel")
    if not handle:
        raise OSError("Cannot create instance mutex")
    if ctypes.get_last_error() == 183:
        kernel.CloseHandle(handle)
        ctypes.windll.user32.MessageBoxW(None, "Szkolny Panel jest już uruchomiony. Sprawdź pasek zadań.", "Szkolny Panel", 0x40)
        return False, None
    return handle, kernel.CloseHandle


def main() -> int:
    parser = argparse.ArgumentParser(description="Szkolny Panel — lokalny klient Synergii")
    parser.add_argument("--demo", action="store_true", help="tylko fikcyjne dane, bez logowania")
    args = parser.parse_args()
    # Keep LC_TIME at Python's default C locale: apix checks English 'Monday'.
    if os.name == "nt":
        import ctypes
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except (AttributeError, OSError):
            pass
        try:
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("SzkolnyPanel")
        except (AttributeError, OSError):
            pass
    try:
        handle, release = _single_instance()
        if handle is False:
            return 0
        from .core import Controller
        from .ui import run
        try:
            controller = Controller(demo_on_start=args.demo)
            try:
                run(controller)
            finally:
                controller.stop()
        finally:
            if handle and release:
                release(handle)
    except Exception:
        # No credential-bearing traceback/log file is written by the launcher.
        message = ("Nie udało się uruchomić Szkolnego Panelu. Uruchom ponownie Start.cmd. "
                   "Wymagany jest Python 3.11 lub nowszy z modułem tkinter.")
        if os.name == "nt":
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, message, "Szkolny Panel", 0x10)
        else:
            print(message, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

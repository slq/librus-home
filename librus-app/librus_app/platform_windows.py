"""Windows credential storage and local notifications.

Importing this module does not touch disk, contact a service or show a toast.
On other operating systems SecureStorage deliberately keeps data in memory only.
"""

from __future__ import annotations

import base64
import ctypes
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
from typing import Callable


# Retain the storage header so existing encrypted accounts remain readable.
_MAGIC = b"SZKOLNYPANEL-DPAPI-1\n"
_MAX_JSON_BYTES = 32 * 1024 * 1024
_MAX_FILE_BYTES = _MAX_JSON_BYTES + 1024 * 1024
_UI_FORBIDDEN = 0x01
_SYSTEM_DLL_SEARCH = 0x00000800
_LOAD_ERROR = (
    "Nie można odczytać zapisanych danych. Plik może być uszkodzony albo "
    "należeć do innego konta Windows. Nie został zmieniony."
)
_SAVE_ERROR = "Nie udało się bezpiecznie zapisać danych. Poprzedni zapis został zachowany."


class StorageError(RuntimeError):
    """A user-facing storage failure; its message never contains stored data."""


class _DataBlob(ctypes.Structure):
    # DWORD is always 32 bits, including on 64-bit Windows.
    _fields_ = [("cbData", ctypes.c_uint32), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]


class _DPAPI:
    """DPAPI CurrentUser, without UI, application-managed keys or plaintext fallback."""

    def __init__(self) -> None:
        crypt32 = ctypes.WinDLL(  # type: ignore[attr-defined]
            "crypt32.dll", use_last_error=True, winmode=_SYSTEM_DLL_SEARCH
        )
        kernel32 = ctypes.WinDLL(  # type: ignore[attr-defined]
            "kernel32.dll", use_last_error=True, winmode=_SYSTEM_DLL_SEARCH
        )
        blob_pointer = ctypes.POINTER(_DataBlob)
        self._protect = crypt32.CryptProtectData
        self._protect.argtypes = [
            blob_pointer, ctypes.c_wchar_p, blob_pointer, ctypes.c_void_p,
            ctypes.c_void_p, ctypes.c_uint32, blob_pointer,
        ]
        self._protect.restype = ctypes.c_int32
        self._unprotect = crypt32.CryptUnprotectData
        self._unprotect.argtypes = [
            blob_pointer, ctypes.POINTER(ctypes.c_wchar_p), blob_pointer,
            ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, blob_pointer,
        ]
        self._unprotect.restype = ctypes.c_int32
        self._free = kernel32.LocalFree
        self._free.argtypes = [ctypes.c_void_p]
        self._free.restype = ctypes.c_void_p

    def protect(self, data: bytes) -> bytes:
        return self._transform(data, decrypt=False)

    def unprotect(self, data: bytes) -> bytes:
        return self._transform(data, decrypt=True)

    def _transform(self, data: bytes, *, decrypt: bool) -> bytes:
        source = ctypes.create_string_buffer(data)
        incoming = _DataBlob(len(data), ctypes.cast(source, ctypes.POINTER(ctypes.c_ubyte)))
        outgoing = _DataBlob()
        try:
            if decrypt:
                # No description output is requested, so no second allocation is owned.
                ok = self._unprotect(
                    ctypes.byref(incoming), None, None, None, None,
                    _UI_FORBIDDEN, ctypes.byref(outgoing),
                )
            else:
                # Not setting CRYPTPROTECT_LOCAL_MACHINE binds this to CurrentUser.
                ok = self._protect(
                    ctypes.byref(incoming), "LibrusApp", None, None, None,
                    _UI_FORBIDDEN, ctypes.byref(outgoing),
                )
            if not ok or not outgoing.pbData or not 0 < outgoing.cbData <= _MAX_FILE_BYTES:
                raise StorageError(_LOAD_ERROR if decrypt else _SAVE_ERROR)
            return ctypes.string_at(outgoing.pbData, outgoing.cbData)
        finally:
            # Clear the native copies. Python's immutable bytes cannot promise zeroization.
            ctypes.memset(ctypes.addressof(source), 0, ctypes.sizeof(source))
            if outgoing.pbData:
                if outgoing.cbData:
                    ctypes.memset(outgoing.pbData, 0, outgoing.cbData)
                self._free(ctypes.cast(outgoing.pbData, ctypes.c_void_p))


class SecureStorage:
    """Store a JSON dictionary in DPAPI on Windows, or only in instance RAM elsewhere.

    The optional directory is useful for a portable test environment. Passing it on
    Linux does not enable persistence. A missing state is represented by an empty dict;
    an unreadable state raises StorageError and is never silently overwritten by load.
    """

    def __init__(self, directory: Path | None = None) -> None:
        self._persistent = sys.platform == "win32"
        if directory is not None:
            self.base_dir: Path | None = Path(directory)
        elif self._persistent:
            local_app_data = os.environ.get("LOCALAPPDATA", "")
            if not local_app_data or not Path(local_app_data).is_absolute():
                raise StorageError("Nie można ustalić lokalnego folderu danych Windows.")
            self.base_dir = Path(local_app_data) / "LibrusApp"
            # Keep the previous state location when no LibrusApp state exists.
            # Never merge accounts or move the user's encrypted data implicitly.
            legacy_dir = Path(local_app_data) / "SzkolnyPanel"
            if not (self.base_dir / "state.dpapi").exists() and (legacy_dir / "state.dpapi").exists():
                self.base_dir = legacy_dir
        else:
            self.base_dir = None
        self._protector: _DPAPI | None = None
        self._memory: bytes | None = None
        self._temporary_files: set[Path] = set()
        self._lock = threading.RLock()

    @property
    def persistent(self) -> bool:
        return self._persistent

    def path(self) -> Path | None:
        """The state path, or None for a default in-memory instance."""
        return self.base_dir / "state.dpapi" if self.base_dir is not None else None

    def _get_protector(self) -> _DPAPI:
        if self._protector is None:
            self._protector = _DPAPI()
        return self._protector

    @staticmethod
    def _encode(data: dict) -> bytes:
        try:
            if not isinstance(data, dict):
                raise ValueError
            result = json.dumps(
                data, ensure_ascii=False, allow_nan=False, separators=(",", ":")
            ).encode("utf-8")
            if len(result) > _MAX_JSON_BYTES:
                raise ValueError
            return result
        except Exception:
            raise StorageError("Dane do zapisania mają nieprawidłowy format lub są zbyt duże.") from None

    @staticmethod
    def _decode(data: bytes) -> dict:
        def reject_constant(_value: str) -> None:
            raise ValueError

        try:
            if len(data) > _MAX_JSON_BYTES:
                raise ValueError
            result = json.loads(data.decode("utf-8"), parse_constant=reject_constant)
            if not isinstance(result, dict):
                raise ValueError
            return result
        except Exception:
            raise StorageError(_LOAD_ERROR) from None

    def load(self) -> dict:
        with self._lock:
            if not self.persistent:
                return self._decode(self._memory) if self._memory is not None else {}
            path = self.path()
            try:
                if path is None:
                    raise StorageError(_LOAD_ERROR)
                with path.open("rb") as handle:
                    encrypted = handle.read(_MAX_FILE_BYTES + 1)
            except FileNotFoundError:
                return {}
            except Exception:
                raise StorageError(_LOAD_ERROR) from None
            try:
                if (
                    len(encrypted) > _MAX_FILE_BYTES
                    or not encrypted.startswith(_MAGIC)
                    or len(encrypted) == len(_MAGIC)
                ):
                    raise ValueError
                payload = self._get_protector().unprotect(encrypted[len(_MAGIC):])
                return self._decode(payload)
            except Exception:
                raise StorageError(_LOAD_ERROR) from None

    def save(self, data: dict) -> None:
        with self._lock:
            payload = self._encode(data)
            if not self.persistent:
                self._memory = payload
                return
            try:
                encrypted = self._get_protector().protect(payload)
                if not isinstance(encrypted, bytes) or not encrypted:
                    raise ValueError
                self._write_atomic(_MAGIC + encrypted)
            except Exception:
                raise StorageError(_SAVE_ERROR) from None

    def _write_atomic(self, encrypted: bytes) -> None:
        destination = self.path()
        if destination is None or self.base_dir is None:
            raise StorageError(_SAVE_ERROR)
        self.base_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        descriptor, filename = tempfile.mkstemp(
            prefix="state.dpapi.", suffix=".tmp", dir=self.base_dir
        )
        temporary = Path(filename)
        self._temporary_files.add(temporary)
        try:
            try:
                stream = os.fdopen(descriptor, "wb")
            except Exception:
                os.close(descriptor)
                raise
            with stream:
                stream.write(encrypted)
                stream.flush()
                os.fsync(stream.fileno())
            self._replace_file(temporary, destination)
        finally:
            try:
                temporary.unlink(missing_ok=True)
                self._temporary_files.discard(temporary)
            except OSError:
                # It contains only ciphertext. clear() can retry our exact owned path.
                pass

    @staticmethod
    def _replace_file(source: Path, destination: Path) -> None:
        os.replace(source, destination)

    def clear(self) -> None:
        with self._lock:
            self._memory = None
            if not self.persistent:
                return
            targets = set(self._temporary_files)
            path = self.path()
            if path is not None:
                targets.add(path)
            failed = False
            for target in targets:
                try:
                    target.unlink(missing_ok=True)
                    self._temporary_files.discard(target)
                except OSError:
                    failed = True
            if failed:
                raise StorageError("Nie udało się usunąć zapisanych danych. Zamknij inne kopie aplikacji i spróbuj ponownie.")


# This is a fixed program, not a template. Notification text enters only as JSON
# through stdin, then becomes XML text nodes. It is never evaluated as PowerShell.
_TOAST_SCRIPT = r"""
$ErrorActionPreference = 'Stop'
try {
    $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
    [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
    [Windows.UI.Notifications.ToastNotification, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
    [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
    $doc = New-Object Windows.Data.Xml.Dom.XmlDocument
    $doc.LoadXml('<toast duration="short"><visual><binding template="ToastGeneric"><text/><text/></binding></visual></toast>')
    # Resolve individual WinRT nodes with Item(). PowerShell's automatic
    # enumeration of a stored node collection fails when AppendChild mutates it.
    [void]$doc.GetElementsByTagName('text').Item(0).AppendChild($doc.CreateTextNode([string]$payload.title))
    [void]$doc.GetElementsByTagName('text').Item(1).AppendChild($doc.CreateTextNode([string]$payload.message))
    $toast = [Windows.UI.Notifications.ToastNotification]::new($doc)
    if ([string]$payload.title -eq 'LibrusApp — przypomnienie') {
        $toast.Tag = [Guid]::NewGuid().ToString('N').Substring(0, 16)
        $toast.Group = 'reminders'
    } else {
        $toast.Tag = 'updates'
        $toast.Group = 'school'
    }
    $toast.ExpirationTime = [DateTimeOffset]::Now.AddHours(8)
    $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('LibrusApp')
    # A newly registered desktop identity may have no setting before its first
    # Show(). Only an explicit disabled status should stop that first submission.
    $setting = [string]$notifier.Setting
    if ($setting -and $setting -ne 'Enabled') { exit 1 }
    $notifier.Show($toast)
    exit 0
} catch { exit 1 }
"""


def _toast_payload(title: str, message: str) -> bytes:
    def clean(text: str, limit: int) -> str:
        if not isinstance(text, str):
            raise TypeError
        return "".join(
            char for char in text
            if ord(char) in (9, 10, 13)
            or 0x20 <= ord(char) <= 0xD7FF
            or 0xE000 <= ord(char) <= 0xFFFD
            or 0x10000 <= ord(char) <= 0x10FFFF
        ).strip()[:limit]

    # ASCII JSON also avoids dependence on the PowerShell console's code page.
    return json.dumps({
        "title": clean(title, 150) or "LibrusApp",
        "message": clean(message, 500),
    }, ensure_ascii=True).encode("ascii")


def _powershell_path() -> Path:
    kernel32 = ctypes.WinDLL(  # type: ignore[attr-defined]
        "kernel32.dll", use_last_error=True, winmode=_SYSTEM_DLL_SEARCH
    )
    get_system_directory = kernel32.GetSystemDirectoryW
    get_system_directory.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32]
    get_system_directory.restype = ctypes.c_uint32
    buffer = ctypes.create_unicode_buffer(32768)
    length = get_system_directory(buffer, len(buffer))
    if not 0 < length < len(buffer):
        raise OSError
    executable = Path(buffer.value) / "WindowsPowerShell" / "v1.0" / "powershell.exe"
    if not executable.is_file():
        raise OSError
    return executable


def _register_toast_application() -> None:
    import winreg

    # Register only this application's display identity in this user's registry.
    with winreg.CreateKeyEx(
        winreg.HKEY_CURRENT_USER,
        r"Software\Classes\AppUserModelId\LibrusApp",
        0, winreg.KEY_SET_VALUE,
    ) as key:
        winreg.SetValueEx(key, "DisplayName", 0, winreg.REG_SZ, "LibrusApp")
        winreg.SetValueEx(key, "ShowInSettings", 0, winreg.REG_DWORD, 1)


def _send_toast(
    executable: Path,
    payload: bytes,
    *,
    runner: Callable = subprocess.run,
) -> bool:
    encoded_program = base64.b64encode(_TOAST_SCRIPT.encode("utf-16le")).decode("ascii")
    result = runner(
        [str(executable), "-NoLogo", "-NoProfile", "-NonInteractive",
         "-EncodedCommand", encoded_program],
        input=payload,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        shell=False,
        creationflags=0x08000000,  # CREATE_NO_WINDOW
        timeout=10,
        check=False,
    )
    return result.returncode == 0


def notify(title: str, message: str) -> bool:
    """Submit a native Windows toast; False elsewhere or on failure.

    Call from a worker: starting PowerShell can take a few seconds (10-second limit).
    True means Windows accepted the request, not that Focus Assist showed a popup.
    Only general text should be passed, since toasts can appear on the lock screen.
    """
    if sys.platform != "win32":
        return False
    try:
        payload = _toast_payload(title, message)
        executable = _powershell_path()
        _register_toast_application()
        return _send_toast(executable, payload)
    except Exception:
        return False

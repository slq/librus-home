"""Storage failure cases and notification command isolation; never sends a toast."""

import base64
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from librus_app.platform_windows import (
    SecureStorage, StorageError, _MAGIC, _TOAST_SCRIPT,
    _powershell_path, _send_toast, _toast_payload, notify,
)


class FakeProtector:
    """Reversible test transform. It is deliberately not production encryption."""

    def protect(self, data):
        return b"test-crypto:" + bytes(value ^ 0xA5 for value in data)

    def unprotect(self, data):
        if not data.startswith(b"test-crypto:"):
            raise ValueError("fake crypto rejected SECRET_CONTENTS")
        return bytes(value ^ 0xA5 for value in data[len(b"test-crypto:"):])


class FakePersistentStorage(SecureStorage):
    def __init__(self, directory):
        super().__init__(directory)
        # Instance-only injection allows testing disk failure semantics on Linux.
        self._persistent = True
        self._protector = FakeProtector()


class PlatformStorageTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def test_default_directory_preserves_legacy_account_and_prefers_new_state(self):
        with patch("librus_app.platform_windows.sys.platform", "win32"), patch.dict(
            "os.environ", {"LOCALAPPDATA": str(self.directory)}
        ):
            self.assertEqual(SecureStorage().base_dir, self.directory / "LibrusApp")
            legacy = FakePersistentStorage(self.directory / "SzkolnyPanel")
            legacy.save({"sample": "legacy-account", "reminders": ["test-only"]})
            legacy_bytes = legacy.path().read_bytes()
            restored = SecureStorage()
            restored._protector = FakeProtector()
            self.assertEqual(restored.base_dir, legacy.base_dir)
            self.assertEqual(restored.load(), legacy.load())
            restored.save({"sample": "updated-account"})
            self.assertEqual(legacy.load(), {"sample": "updated-account"})
            restored.clear()
            self.assertFalse(legacy.path().exists())
            self.assertEqual(SecureStorage().base_dir, self.directory / "LibrusApp")

            legacy.path().write_bytes(legacy_bytes)
            current = FakePersistentStorage(self.directory / "LibrusApp")
            current.save({"sample": "new-account"})
            restored = SecureStorage()
            restored._protector = FakeProtector()
            self.assertEqual(restored.load(), {"sample": "new-account"})
            restored.clear()
            self.assertEqual(legacy.path().read_bytes(), legacy_bytes)

    def test_persistent_roundtrip_is_not_aliased_or_plaintext(self):
        storage = FakePersistentStorage(self.directory)
        original = {"password": "SECRET_CONTENTS", "student": "Zażółć", "seen": ["one"]}
        storage.save(original)
        original["seen"].append("changed-after-save")
        expected = {"password": "SECRET_CONTENTS", "student": "Zażółć", "seen": ["one"]}
        self.assertEqual(FakePersistentStorage(self.directory).load(), expected)
        loaded = storage.load()
        loaded["seen"].append("changed-after-load")
        self.assertEqual(storage.load(), expected)
        ciphertext = storage.path().read_bytes()
        self.assertNotIn(b"SECRET_CONTENTS", ciphertext)
        self.assertNotIn(b'"password"', ciphertext)
        self.assertEqual(list(self.directory.iterdir()), [storage.path()])

    def test_failed_atomic_replacement_preserves_previous_state(self):
        storage = FakePersistentStorage(self.directory)
        storage.save({"version": "old"})
        old_bytes = storage.path().read_bytes()
        observed = []

        def fail_replace(source, destination):
            observed.append(source)
            self.assertEqual(destination.read_bytes(), old_bytes)
            self.assertNotIn(b"SECRET_CONTENTS", source.read_bytes())
            raise OSError("SECRET_CONTENTS")

        storage._replace_file = fail_replace
        with self.assertRaises(StorageError) as caught:
            storage.save({"password": "SECRET_CONTENTS"})
        self.assertNotIn("SECRET_CONTENTS", str(caught.exception))
        self.assertIsNone(caught.exception.__cause__)
        self.assertTrue(caught.exception.__suppress_context__)
        self.assertEqual(storage.load(), {"version": "old"})
        self.assertTrue(observed)
        self.assertFalse(observed[0].exists())

    def test_successful_replacement_sees_complete_old_and_new_files(self):
        storage = FakePersistentStorage(self.directory)
        storage.save({"sequence": 1})
        observed = []

        def inspect_replace(source, destination):
            observed.append(True)
            self.assertEqual(storage.load(), {"sequence": 1})
            encrypted = source.read_bytes()
            new_json = storage._protector.unprotect(encrypted[len(_MAGIC):])
            self.assertEqual(json.loads(new_json), {"sequence": 2})
            SecureStorage._replace_file(source, destination)

        storage._replace_file = inspect_replace
        storage.save({"sequence": 2})
        self.assertEqual(observed, [True])
        self.assertEqual(storage.load(), {"sequence": 2})

    def test_crypto_failure_never_creates_plaintext_fallback(self):
        class BrokenProtector(FakeProtector):
            def protect(self, data):
                raise RuntimeError("SECRET_CONTENTS")

        storage = FakePersistentStorage(self.directory / "not-created")
        storage._protector = BrokenProtector()
        with self.assertRaises(StorageError) as caught:
            storage.save({"password": "SECRET_CONTENTS"})
        self.assertNotIn("SECRET_CONTENTS", str(caught.exception))
        self.assertFalse(storage.base_dir.exists())

    def test_corrupt_file_raises_sanitized_error_without_changing_file(self):
        storage = FakePersistentStorage(self.directory)
        bad_files = [
            b"", b"SECRET_CONTENTS", _MAGIC, _MAGIC + b"invalid-ciphertext",
            _MAGIC + storage._protector.protect(b"SECRET_CONTENTS"),
            _MAGIC + storage._protector.protect(b"[]"),
            _MAGIC + storage._protector.protect(b'{"value": NaN}'),
        ]
        for corrupt in bad_files:
            with self.subTest(file_length=len(corrupt)):
                storage.path().write_bytes(corrupt)
                with self.assertRaises(StorageError) as caught:
                    storage.load()
                self.assertNotIn("SECRET_CONTENTS", str(caught.exception))
                self.assertIsNone(caught.exception.__cause__)
                self.assertTrue(caught.exception.__suppress_context__)
                self.assertEqual(storage.path().read_bytes(), corrupt)

    def test_invalid_input_does_not_destroy_valid_state(self):
        storage = FakePersistentStorage(self.directory)
        storage.save({"valid": True})
        for invalid in [["SECRET_CONTENTS"], {"bad": object()}, {"bad": float("nan")}]:
            with self.subTest(input_type=type(invalid).__name__):
                with self.assertRaises(StorageError):
                    storage.save(invalid)
                self.assertEqual(storage.load(), {"valid": True})

    def test_clear_removes_only_state_and_known_owned_temporary_files(self):
        storage = FakePersistentStorage(self.directory)
        storage.save({"password": "SECRET_CONTENTS"})
        owned_temp = self.directory / "state.dpapi.owned.tmp"
        owned_temp.write_bytes(b"ciphertext")
        storage._temporary_files.add(owned_temp)
        untouched = [self.directory / "notes.txt", self.directory / "state.dpapi.someone-else.tmp"]
        for path in untouched:
            path.write_text("keep", encoding="utf-8")
        storage.clear()
        storage.clear()
        self.assertEqual(storage.load(), {})
        self.assertFalse(owned_temp.exists())
        for path in untouched:
            self.assertEqual(path.read_text(encoding="utf-8"), "keep")

    def test_missing_state_is_empty(self):
        storage = FakePersistentStorage(self.directory / "missing")
        self.assertEqual(storage.load(), {})
        self.assertFalse(storage.base_dir.exists())

    @unittest.skipIf(sys.platform == "win32", "Non-Windows memory-only contract")
    def test_non_windows_never_reads_writes_or_clears_files(self):
        disk_state = self.directory / "state.dpapi"
        disk_state.write_bytes(b"untouched")
        storage = SecureStorage(self.directory)
        self.assertFalse(storage.persistent)
        self.assertEqual(storage.load(), {})
        source = {"nested": ["value"]}
        storage.save(source)
        source["nested"].append("new")
        self.assertEqual(storage.load(), {"nested": ["value"]})
        self.assertEqual(SecureStorage(self.directory).load(), {})
        storage.clear()
        self.assertEqual(storage.load(), {})
        self.assertEqual(disk_state.read_bytes(), b"untouched")
        self.assertEqual(list(self.directory.iterdir()), [disk_state])

    @unittest.skipUnless(sys.platform == "win32", "Requires real Windows DPAPI")
    def test_windows_dpapi_roundtrip(self):
        storage = SecureStorage(self.directory)
        self.assertTrue(storage.persistent)
        storage.save({"sample": "test-only", "unicode": "Łódź"})
        self.assertEqual(storage.load(), {"sample": "test-only", "unicode": "Łódź"})
        self.assertNotIn(b"test-only", storage.path().read_bytes())
        storage.clear()


class NotificationTests(unittest.TestCase):
    @unittest.skipUnless(sys.platform == "win32", "Requires Windows PowerShell and WinRT")
    def test_windows_allows_first_notification_but_respects_disabled_settings(self):
        for setting, expected in (("$null", 7), ("'Enabled'", 7),
                                  ("'DisabledForApplication'", 1), ("'DisabledForUser'", 1),
                                  ("'DisabledByGroupPolicy'", 1), ("'DisabledByManifest'", 1)):
            with self.subTest(setting=setting):
                # Run the real program with a simulated notifier. Exit 7 is a
                # sentinel proving Show was reached, without sending a toast.
                script = _TOAST_SCRIPT.replace(
                    "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('LibrusApp')",
                    "[pscustomobject]@{ Setting = " + setting + " }",
                ).replace("$notifier.Show($toast)", "exit 7")
                encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
                result = subprocess.run(
                    [str(_powershell_path()), "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded],
                    input=_toast_payload("Test", "Test"),
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    shell=False, creationflags=0x08000000, timeout=15, check=False,
                )
                self.assertEqual(result.returncode, expected)

    @unittest.skipUnless(sys.platform == "win32", "Requires Windows PowerShell and WinRT")
    def test_windows_builds_real_toast_xml_without_sending_a_notification(self):
        # Execute the production XML construction, stopping before notifier
        # creation. No registry change or actual system notification in tests.
        construction, separator, _ = _TOAST_SCRIPT.partition("    $notifier = ")
        self.assertTrue(separator)
        script = construction + r'''
    if ($doc.GetElementsByTagName('text').Item(0).InnerText -cne [string]$payload.title) { exit 2 }
    if ($doc.GetElementsByTagName('text').Item(1).InnerText -cne [string]$payload.message) { exit 3 }
    exit 0
} catch { exit 1 }
'''
        encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
        result = subprocess.run(
            [str(_powershell_path()), "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded],
            input=_toast_payload("Łódź & <test>", 'Tekst "@ $([unused])\nZażółć'),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            shell=False, creationflags=0x08000000, timeout=15, check=False,
        )
        self.assertEqual(result.returncode, 0, "Windows PowerShell could not build the toast XML")

    @unittest.skipUnless(sys.platform == "win32", "Requires Windows PowerShell and WinRT")
    def test_personal_toast_has_its_own_group_and_tag(self):
        construction, _, _ = _TOAST_SCRIPT.partition("    $notifier = ")
        script = construction + r'''
    if ($toast.Group -ne 'reminders') { exit 2 }
    if ($toast.Tag -eq 'updates' -or $toast.Tag.Length -ne 16) { exit 3 }
    exit 0
} catch { exit 1 }
'''
        encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
        result = subprocess.run(
            [str(_powershell_path()), "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded],
            input=_toast_payload("LibrusApp — przypomnienie", "Treść testowa"),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            shell=False, creationflags=0x08000000, timeout=15, check=False,
        )
        self.assertEqual(result.returncode, 0)

    def test_hostile_text_stays_data_and_is_never_in_command_source(self):
        title = 'Nowość "@ $([System.IO.File]::Delete("unused"))'
        message = '</text>\n"@\nWrite-Output SECRET_CONTENTS\n# & < > ]]> Żółw'
        payload = _toast_payload(title, message)
        calls = []

        def fake_runner(command, **kwargs):
            calls.append((command, kwargs))
            return subprocess.CompletedProcess(command, 0)

        self.assertTrue(_send_toast(Path("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"), payload, runner=fake_runner))
        command, kwargs = calls[0]
        self.assertFalse(kwargs["shell"])
        self.assertEqual(kwargs["timeout"], 10)
        self.assertEqual(json.loads(kwargs["input"]), {"title": title, "message": message})
        fixed_script = base64.b64decode(command[-1]).decode("utf-16le")
        self.assertEqual(fixed_script, _TOAST_SCRIPT)
        self.assertNotIn("SECRET_CONTENTS", fixed_script)
        self.assertNotIn(title, fixed_script)
        self.assertIn("CreateTextNode", fixed_script)

    def test_payload_supports_polish_and_removes_xml_invalid_characters(self):
        value = json.loads(_toast_payload("Łódź\x00", "Zażółć\ud800"))
        self.assertEqual(value, {"title": "Łódź", "message": "Zażółć"})

    def test_failed_submission_returns_false(self):
        def fake_runner(command, **kwargs):
            return subprocess.CompletedProcess(command, 1)

        self.assertFalse(_send_toast(Path("unused.exe"), b"{}", runner=fake_runner))

    @unittest.skipIf(sys.platform == "win32", "Must not emit actual Windows notifications")
    def test_notify_is_noop_on_non_windows(self):
        self.assertFalse(notify("Test", "No actual toast is emitted"))


if __name__ == "__main__":
    unittest.main()

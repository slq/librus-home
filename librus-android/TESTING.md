# Weryfikacja LibrusApp Android 0.1.0

Weryfikacja lokalna: 7 października 2026. Telefon docelowy wskazany przez użytkownika: Samsung Galaxy S10, Android 12, One UI 4.1.

## Wyniki

| Próba | Wynik |
|---|---|
| Gradle: assembleDebug | APK zbudowany |
| Testy Python warstwy mobilnej | 13 testów, wszystkie poprawne |
| Testy Java formatowania dat | 3 testy, wszystkie poprawne |
| Android Lint | 0 błędów; 5 ostrzeżeń dotyczących nowszych SDK/Gradle i lokalizacji tekstów |
| Emulator Android 12, API 31, Google APIs, x86_64 | APK zainstalowany i uruchomiony |
| Testy urządzenia | 2 testy, wszystkie poprawne |
| Biblioteki osadzone w APK | Import lxml.etree, aiohttp i adaptera Librusa poprawny |
| Android Keystore / AES-GCM | Unicode zachowany; brak tekstu jawnego; modyfikacja ciphertext odrzucona bez nadpisania pliku |
| Przegląd i wiadomości w demo | Liczniki, lista, szczegóły i odczyt treści sprawdzone |
| Terminarz w demo | Kalendarz miesięczny i lista wydarzeń sprawdzone |
| Podpis APK | Poprawny podpis v2; pakiet pl.librushome.android; minSdk 26, targetSdk 35 |
| Wspólne źródła i licencje | sync_shared.py --check: zgodne z desktopem |

Testy Python nie wykonują rzeczywistych zapytań; używają syntetycznych odpowiedzi i blokują requests.Session.send. Test Keystore używa osobnego pliku i aliasu testowego. Ekrany były sprawdzane na fikcyjnych danych. Przycisk pobrania treści znajduje się nad przewijanym opisem wiadomości.

## Powtarzanie prób

Z katalogu librus-android:

```powershell
..\librus-app\.venv\Scripts\python.exe -B -m unittest discover -s tests -v
.\Build.ps1 -SdkPath 'C:/Android/Sdk' -PythonPath 'C:/Python313/python.exe'
```

Testy urządzenia uruchamiaj na pustym emulatorze Androida 12 bez konta szkolnego. Skonfiguruj Android SDK, JDK oraz ścieżkę Python 3.13 zgodnie z README:

```powershell
.\gradlew.bat '-PbuildPython=C:/Python313/python.exe' :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w pl.librushome.android.test/androidx.test.runner.AndroidJUnitRunner
```

## Granice weryfikacji

Nie podłączono fizycznego S10 i nie używano rzeczywistego loginu ani hasła. Nie potwierdzono odczytu danych konkretnej szkoły, zachowania One UI ani osadzonych bibliotek na fizycznym ARM64. APK zawiera biblioteki arm64-v8a, ale test uruchomienia wykonano na x86_64. Pierwszy test rzeczywistego logowania użytkownik wykonuje na własnym telefonie.

Wersja 0.1.0 jest wersją rozwojową. Nie zawiera przypomnień, powiadomień systemowych ani odczytów w tle. Szczegóły zakresu: MVP_SCOPE.md.
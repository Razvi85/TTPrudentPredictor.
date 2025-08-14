# TT Prudent Predictor (Android, Kotlin + Compose)

Aplicație Android care calculează predicții prudente pentru tenis de masă din **ultimele 10 meciuri** ale fiecărui jucător.
În prezent funcționează dintr-un **asset local** (`app/src/main/assets/sample_matches.json`). Când ai backend-ul, setează `apiUrl` în cod pentru a consuma API-ul tău.

## Cum compilezi APK
1. Deschide folderul proiectului în **Android Studio (Giraffe/Koala)**.
2. Lasă `apiUrl` gol pentru test – va citi din `assets/sample_matches.json`.
3. Rulează: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. APK-ul îl găsești în `app/build/outputs/apk/debug/app-debug.apk`.

## Cum pui API-ul tău
- În `MainActivity.kt`, variabila `apiUrl` (linia de sus a composable-ului `App`) poate fi setată la endpoint-ul tău public:
  ```kotlin
  var apiUrl by remember { mutableStateOf("https://<domeniu>/api/tt-predictions") }
  ```
- Structura JSON-ului așteptată e definită în `ApiPayload`/`Match`/`Last10` din cod.

## Format JSON
Vezi `app/src/main/assets/sample_matches.json` pentru exemplu.

## Build automat (GitHub Actions)
1. Creează un repository GitHub și împinge acest proiect (folderul întreg) pe ramura `main`.
2. Intră la **Actions** și pornește workflow-ul `Android APK (Debug)`.
3. După ~10 minute, găsești fișierul APK în **Actions → Run → Artifacts → TTPrudentPredictor-debug-apk**.

Pentru semnare release, adaugă un keystore și configurare în `gradle.properties` și `app/build.gradle`.

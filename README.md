# EquiTag iOS

Kopfnummern für Pferde per NFC auf E-Ink-Displays (FMSH, z. B. 3,52").

- Code: `EquiTag/` (SwiftUI, CoreNFC)
- Xcode-Projekt wird beim Bauen aus `project.yml` erzeugt (XcodeGen)
- Bauen + TestFlight: `codemagic.yaml` (Workflow „EquiTag iOS (TestFlight)“)

Benötigt in Codemagic:
- App-Store-Connect-API-Schlüssel als Integration mit dem Namen **EquiTag**
- Umgebungsvariable **CERTIFICATE_PRIVATE_KEY** (geheim) in der Gruppe **equitag**

Bundle-ID: `de.equitag.app` (Capability „NFC Tag Reading“ muss aktiviert sein)

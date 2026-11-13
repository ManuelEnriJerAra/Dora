# Spanish Text Pass

## Status

Fixed

## Original Issue

The 2026-11-13 UX review found English text mixed into the Spanish UI:

- Configuración showed raw keys such as "Online mode", "Difficulty" values like `peaceful`, and many title-cased property names.
- Mundo showed "Seed", "Gamemode", "Generator settings", "Region file compression" and "Generar preview".
- On a Windows set to English, every confirmation said "Yes / No / Cancel" and file pickers said "Look in", "Open" and "Cancel".
- Ports and sizes were shown with thousands separators (`25,565`, `29,999,984`).
- "Dia 1" and "Sin gestion" were missing accents.
- Nothing said that property changes only apply after a restart.

## Root Cause

Labels were only translated for a few keys, value combos rendered the stored English values, Swing picks dialog texts from the JVM locale, and `JSpinner` uses a grouping number format by default.

## Solution

- `PanelConfigServidor`: about 40 Spanish labels, `etiquetaValor` renderer for difficulty and game mode, `crearModeloNumerico` with sane bounds, `"#"` spinner editors, and a "Los cambios se aplican al reiniciar el servidor." hint next to Guardar.
- `PanelMundo`: Spanish labels, "vista previa" instead of "preview" in all visible text, `"#"` editors on the spawn protection and world size spinners.
- `vista.TextosSwingEspanol`: Spanish `OptionPane.*` and `FileChooser.*` texts in `UIManager`, English mnemonics turned off. The default `Locale` is not changed.
- `WorldDataReader`: "Día"; `PanelExtensiones`: "Sin gestión".

## Verification

- `PanelConfigServidorTextosTest` and the updated `WorldDataReaderTest`.
- Checked in the app: Configuración, Mundo and the remove-server confirmation ("Sí / No").

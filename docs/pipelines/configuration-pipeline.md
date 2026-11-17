# Configuration Pipeline

Use this guide before editing app config, server.properties editing, known users, or persisted UI preferences.

## App Configuration

Core classes:

- `controlador.GestorConfiguracion`
- `modelo.DoraConfig`
- `modelo.ServerConfig`
- `modelo.Server`

`GestorConfiguracion` handles persisted app-level data such as known servers and UI preferences.

App-owned persistence paths are centralized in `controlador.AppPaths`. Default app config files live under the user data root, currently `~/.dora/config/`, while disposable extension caches live under `~/.dora/cache/`. Do not add new executable-relative app state; use `AppPaths.configDirectory()`, `cacheDirectory()`, `locksDirectory()`, or `statsDirectory()` as appropriate.

Before changing serialized fields, check existing config compatibility and default handling.

## Server Properties

`modelo.ServerProperties` represents `server.properties` style values.

Read and write `server.properties` only through `Utilidades.cargarPropertiesUtf8(...)` and `Utilidades.guardarPropertiesUtf8(...)`. The loader accepts UTF-8 and falls back to ISO-8859-1 for hand-edited ANSI files; the writer is atomic. Never continue with an empty `Properties` after a failed read and then save: that replaces the user's file with an almost empty one. `escribirPuertoEnProperties` and `escribirMotdEnProperties` throw instead of writing when the existing file cannot be read.

`vista.PanelConfigServidor` provides the UI for editing server properties and runtime-related settings.

Panel responsibilities:

- Load properties for the selected server.
- Categorize fields into sections.
- Create editors based on field kind.
- Track dirty state.
- Save values back to disk.
- Keep some fields managed elsewhere out of this panel.

Important helper areas in `PanelConfigServidor`:

- `FIELD_LABELS`
- `FIELD_DESCRIPTIONS`
- `SECTION_ORDER`
- `captureCurrentValues()`
- `readComponentValue(...)`
- `markCurrentStateAsPersisted()`
- `updateSaveButtonState()`
- RAM slider/spinner synchronization
- field kind detection and normalization

Display rules (keep `server.properties` values in English, show them in Spanish):

- `FIELD_LABELS` is looked up lower-case first, so every known key gets a Spanish label; unknown keys fall back to the title-cased key.
- `etiquetaValor(...)` turns stored values such as `peaceful` or `survival` into Spanish text in the difficulty and game mode combos. Only the renderer changes; the combo still holds the English value that is saved.
- Number spinners use `crearModeloNumerico(...)` (ports 1..65535, counters at least 0) and a `"#"` editor, so ports show as `25565`, not `25,565`.
- The actions row says that changes apply on the next server restart.

## Managed Outside Config Panel

Some server/world settings are intentionally managed elsewhere, such as world settings in `PanelMundo`. Do not duplicate controls unless the UX explicitly calls for it.

## Known Users

`GestorUsuariosConocidos` stores and resolves known player identities. Player-related UI uses it to maintain display consistency.

## Dirty Tracking Rules

When adding a new editor:

- Register dirty tracking.
- Include it in value capture.
- Normalize values consistently with save logic.
- Update persisted state after successful save.

## Tests

Relevant tests include:

- `PanelServidoresTest`
- `PanelConfigServidorTextosTest`
- `PanelExtensionesTest`
- config-related coverage in controller/model tests

For property parsing or persistence changes, run targeted tests and compile.

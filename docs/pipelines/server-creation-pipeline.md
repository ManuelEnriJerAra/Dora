# Server Creation Pipeline

The server creation wizard lives in `src/main/java/controlador/AsistenteCreacionServidor.java`. `GestorServidores.crearServidor()` opens it and then installs the chosen platform with `crearServidorAutomatizado(...)`.

Existing-server conversion lives in `src/main/java/controlador/ConversorPlataformaServidor.java`; `GestorServidores` keeps the public `convertirServidorAPlataformaCompatible(...)` and `puedeConvertirseAPlataformaCompatible(...)` entry points and delegates to it.

## Main Flow

1. `AsistenteCreacionServidor.mostrar()` opens the modal wizard.
2. The wizard collects platform, version, parent directory, EULA acceptance, and folder name.
3. `validarPasoCreacionServidor(...)` validates the active step.
4. `ServerCreationWizardResult` carries the selected adapter, option, and final target directory.
5. The selected `ServerPlatformAdapter` performs platform-specific installation.
6. The created server is added to the managed server list and persisted through the normal server management path.

## Wizard Steps

- `platform`: choose a `ServerPlatformAdapter` from `ServerPlatformAdapters.creatable()`.
- `version`: choose a `ServerCreationOption`, filtered by snapshots/releases, and accept the Mojang EULA.
- `parent`: choose the parent folder through `JFileChooser`.
- `folderName`: edit the final child folder name.

The wizard keeps loaded creation options per platform for the lifetime of the dialog. The underlying platform metadata clients also use the shared in-memory cache from `platform-adapters-pipeline.md`, so switching platforms in one wizard session should not repeat identical remote version calls.

Release/snapshot filtering is driven by `ServerCreationOption.versionType` and adapter `supportsUnstableCreationOptions()`. Releases are shown by default. When snapshots are enabled, adapters that support unstable creation options may also show Minecraft snapshot, pre-release, release-candidate, or other unstable Minecraft-version entries. Do not use this checkbox as a loader-channel filter; beta/alpha loader artifacts for stable Minecraft releases belong in the release list only when no stable loader artifact exists yet for that Minecraft release.

Existing-server conversion reuses the creation option model but first detects the source platform and Minecraft version from the server directory. Before rendering the target platform tiles, load each target adapter's creation options and filter them against the detected Minecraft version. Platform tiles with no compatible build should be unavailable with a tooltip explaining that there is no version for the current Minecraft version. The conversion version picker should show the source context, including the full Minecraft version such as `26.2-snapshot-7`, before listing compatible target builds.

Before installing the target platform, conversion uses `ProcessWizardDialog` steps for destination platform and compatible version. The backup choice belongs below the compatible version selector in that final step, so the combined version/backup step uses the wizard's rocket action. The backup choice should default to enabled. If the user declines the full backup, conversion must still preserve existing config, world, and extension data through an internal temporary preservation snapshot so platform installers cannot delete user data.

The `folderName` step uses a custom visual path editor:

- The parent path is rendered with leading ellipsis when it cannot fit.
- The editable folder name is left-aligned.
- The folder name starts immediately after the visible parent path.
- A very long folder name may hide the parent path entirely.

## State Rules

`ServerCreationWizardState` stores wizard state. Keep it as the source of truth for selections and suggested folder names.

When the platform, version, or parent directory changes, the suggested folder name may be recomputed with `actualizarSugerenciaNombreCarpeta(...)`.

When the user edits the folder name manually:

- Update `state.folderName`.
- Update `state.folderNameEdited`.
- Revalidate/repaint the folder path editor if its layout depends on text width.
- Refresh the Next/Create button enabled state after the document event finishes, for example with `SwingUtilities.invokeLater(...)`, because Swing text documents cannot be mutated while notifying listeners.

## Validation Rules

Folder names are validated with `AsistenteCreacionServidor.validarNombreCarpetaServidor(...)`.

Also check for existing folders with `existeCarpetaConNombreNoPortable(...)` so Windows-style case-insensitive conflicts are caught.

Avoid writing files during wizard validation. Creation should happen only after the final step succeeds.

## UI Notes

Use existing app UI helpers:

- `AppTheme.applyHeaderIconButtonStyle(...)`
- `SvgIconFactory`
- existing FlatLaf/Swing components

The local wizard should match shared process wizard navigation behavior: use the right-arrow icon for intermediate steps and switch the final right-side action to `doraicons/rocket.svg` when the active step creates the server.

Keep Spanish user-facing text consistent with the rest of the wizard.

## Importing

- The Importar button in the server list opens a menu with "Carpeta de servidor..." (`GestorServidores.importarServidor`) and "Modpack (.mrpack o .zip)..." (`VentanaPrincipal.importarModpackDesdeLista`).
- Imported folders are named after the folder (`construirNombreDesdeCarpeta`); generic names like `server` or `minecraft` fall back to "Servidor <versión>". Servers created by the wizard keep "Servidor <versión>".
- A modpack goes into the selected server when it is a compatible mod server. Otherwise Dora shows the Minecraft and loader versions from `modrinth.index.json`, can open the wizard, and then imports into the new server through `PanelExtensiones.importarModpackDesdeArchivo`.

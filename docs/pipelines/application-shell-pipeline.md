# Application Shell Pipeline

Use this guide before editing startup, navigation, window layout, or right-side panel composition.

## Entry Point

Main class: `src/main/java/controlador/Main.java`.

The application starts the Swing UI, configures Look and Feel/theme, and opens the main window.

`Main.aplicarPreferenciasUI()` also calls `vista.TextosSwingEspanol.aplicar()`, which puts Spanish texts for `JOptionPane` and `JFileChooser` buttons and labels into `UIManager`. The default `Locale` is left alone on purpose, so number and date formats keep following the system.

Startup first acquires `controlador.ApplicationInstanceLock`, an OS file lock stored under `AppPaths.locksDirectory()`. The first instance also opens a localhost handoff listener so duplicate launches can ask the running app to restore/focus its current window before exiting. Keep this guard before theme/config loading, controller construction, and window creation so a duplicate launch exits without mutating shared app state.

## Main Window

Primary class: `src/main/java/vista/VentanaPrincipal.java`.

Responsibilities:

- Owns the root frame.
- Builds left navigation and right content.
- Tracks the selected server.
- Wires global actions such as create/import/delete/open folder.
- Toggles Debug mode through the Info navigation entry click sequence.
- Updates the title when Debug mode changes.
- Owns system tray integration when the platform supports `SystemTray`.

Helpers:

- `VentanaPrincipalNavigationBuilder`: navigation construction.
- `VentanaPrincipalRightContentBuilder`: right-side content panel creation.

## Main Panels

Right-side content is composed from `vista` panels:

- `PanelTotalServidor`: server overview/dashboard.
- `PanelControlServidor`: start/stop/restart controls.
- `PanelConsola`: live console and command input.
- `PanelConfigServidor`: server.properties and runtime settings.
- `PanelJugadores`: connected players and player lists.
- `PanelMundo`: worlds, metadata, storage, previews, recent connections.
- `PanelExtensiones`: mods/plugins listing, install/remove, catalog integration.
- `PanelPrevisualizacion`: preview/edit of server presentation data.
- `PanelEstadisticas`: stats view.
- `PanelAutomatizacion`: automation tab for scheduled lifecycle actions and commands, including rule creation, editing, enable/disable, and deletion.

## Selection Flow

`PanelServidores` renders the left server list and reports selection changes to `VentanaPrincipal`.

When a server changes:

1. The selected `Server` is stored in `GestorServidores`.
2. Visible panels refresh from the selected server.
3. Runtime-sensitive panels update state based on process status and filesystem state.

When the current selection disappears during a manual server-list refresh, the UI should automatically select the first remaining managed server if one exists. Only clear the right-side content when the managed list becomes empty.

Do not let individual panels keep stale selected-server assumptions. Prefer fetching through `GestorServidores.getServidorSeleccionado()` or responding to the existing selection refresh path.

## Debug Mode

`VentanaPrincipal` owns the hidden click sequence that toggles `DebugMode`. Panels should only consume `DebugMode`, not duplicate toggle logic.

See `debug-mode-pipeline.md`.

## System Tray And Exit

`VentanaPrincipal` installs a tray icon when `SystemTray.isSupported()` succeeds. In that mode, the window close button hides the main frame instead of exiting the application. Explicit quit actions, including the tray `Salir de Dora` action and the no-tray fallback close path, keep the active-server confirmation and shutdown flow.

Tray menu state should be rebuilt from `GestorServidores.getServidoresActivos()` so active server counts and names do not go stale. Swing frame restore and exit prompts launched from tray callbacks should return to the EDT.

## Layout Notes

The app is dense operational UI, not a marketing page. Prefer compact, scan-friendly cards and controls.

Use existing builders and panels rather than constructing alternate navigation trees.

## When Editing

- If changing navigation, inspect both `VentanaPrincipal` and the builder classes.
- If adding a panel, decide where it belongs in the navigation enum/page model before wiring the view.
- If changing selected-server propagation, audit panels that cache listeners or server references.

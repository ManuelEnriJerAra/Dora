# World Management Pipeline

World UI behavior is mostly in `src/main/java/vista/PanelMundo.java`.

Supporting services live under:

- `src/main/java/controlador/GestorMundos.java`
- `src/main/java/controlador/WorldDataReader.java`
- `src/main/java/controlador/world`

## Refresh Pipeline

`refrescarDatos()` is the broad refresh entry point.

Typical sequence:

1. Resolve the selected server through `GestorServidores`.
2. Synchronize worlds with `GestorMundos.sincronizarMundosServidor(...)`.
3. List worlds with `GestorMundos.listarMundos(...)`.
4. Resolve the active world with `GestorMundos.getMundoActivo(...)`.
5. Populate the world combo box.
6. Call `actualizarVistaMundos()`.

`actualizarVistaMundos()` updates:

- use-world button state
- preview preferences
- metadata labels
- world settings controls
- selected preview
- shared preview-render state
- storage stats
- recent connections

Storage stats walk the whole world folder, so `actualizarDatosAlmacenamiento()` runs `WorldStorageAnalyzer.analyze(...)` on a background thread and shows "Calculando..." until it finishes. Bump `almacenamientoSequence` whenever the labels are reset so a late result for the previous world is dropped.

When several labels need `level.dat` data during one refresh, use `WorldDataReader.readMetadata(...)` and pass the resulting snapshot through the UI update path instead of calling many independent getters. The individual getters remain available for narrow compatibility calls, but page refreshes should avoid reparsing the same file repeatedly.

`level.dat` numeric fields should be read as generic NBT `NumberTag` values unless the exact tag width is meaningful. Older or migrated worlds may store the same logical field as byte, short, int, or long; exact getters like `getIntTag(...)` can throw when the stored numeric width differs.

## Recent Connections Pipeline

`actualizarConexionesRecientes()` renders the "Últimas conexiones" card.

Data source order:

1. Parse recent join lines from `Server.getRawLogLines()`.
2. If no log joins are found, fallback to `WorldPlayerDataService.findRecentPlayers(...)`.
3. In Debug mode, merge in-memory fake connections ahead of real entries.
4. Deduplicate repeated users by username, keeping the newest occurrence first.
5. Render rows with `crearFilaConexion(...)`.

There is no product-level hard cap on recent connections now. The practical size depends on the available log lines or `playerdata` files, so the card should stay scrollable rather than truncating the history.

Recent-connection rows use `PlayerIdentityView.SizePreset.COMPACT` without vertical gaps between users, with the timestamp centered in the trailing area. Location/coordinate data may remain in the internal `RecentConnection` value, but this compact card does not render it.

## Preview Pipeline

Preview state is coordinated between UI controls, metadata, and background render workers.

Important areas:

- `PreviewRenderPreferences`
- preview option menu installation
- preview status label
- shared render state fields
- preview generation SwingWorker

When touching preview code, keep UI updates on the EDT and avoid starting duplicate generation workers.

## World Settings

The configuration card edits server/world properties such as:

- `allow-nether`
- region file compression
- spawn protection
- max world size

Use existing helper methods for reading, applying, tracking persisted state, and enabling the save button.

## UI Notes

Cards use `CardPanel`. Compact rows use `BoxCategory`.

For header/debug controls, use icon-only buttons styled through `AppTheme` and `SvgIconFactory`.

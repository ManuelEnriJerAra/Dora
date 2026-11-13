# Server Lifecycle Pipeline

Use this guide before editing server import/listing/persistence, start/stop/restart, backups, deletion, or process handling.

## Core Classes

- `controlador.GestorServidores`: central server manager (server list, selection, listeners, import, start/stop).
- `controlador.ArchivoListaServidores`: reads and writes `dora-server-list.json`.
- `controlador.MetadatosServidor`: default values filled into every saved server and the `server.properties` port sync.
- `controlador.PuertosServidor` and `controlador.DialogoPuertoOcupado`: port checks and the "port busy" dialog used when starting.
- `modelo.Server`: server model, runtime process, console listeners, extension list.
- `modelo.ServerConfig`: persisted server config values.
- `controlador.ServerRuntime`: runtime helper.
- `controlador.ServerLogReader`: reads process output and forwards console lines.
- `controlador.DetectorTipoServidor`: legacy/server type detection.
- `controlador.DetectorVersionServidor`: version detection support.
- `controlador.platform.*`: modern platform detection, validation, startup command, creation.

## Managed Server List

`GestorServidores` owns the server collection and selected server.

Typical responsibilities:

- Load persisted servers.
- Add imported or newly created servers.
- Persist changes after reorder/favorite/edit.
- Expose `PropertyChangeSupport` events for UI panels.
- Keep selected server consistent.

Before changing persistence, inspect `GestorConfiguracion`, `DoraConfig`, `Server`, and tests in `GestorServidoresTest`.

Persistence safety rules:

- Write `dora-server-list.json` and `dora-config.json` through `Utilidades.escribirArchivoAtomico(...)` (temp file plus move), never directly onto the target file.
- If a data file cannot be parsed, copy it to `<name>.corrupt-<timestamp>` with `Utilidades.respaldarArchivoIlegible(...)` before continuing with defaults, and tell the user.
- Saved servers that are not loadable right now (missing folder, unplugged drive, invalid jar) are hidden, not deleted: they move to `servidoresNoDisponibles`, stay in the JSON on every save, and return to the visible list when validation passes again. Only `olvidarServidorNoDisponible(...)` or `eliminarServidor(...)` removes an entry.

## Start Pipeline

General sequence:

1. Resolve selected or requested `Server`.
2. Validate server directory.
3. Detect/resolve platform profile.
4. Locate executable jar if required.
5. Build a `ProcessBuilder` through the platform adapter or default Java jar path.
6. Start the process.
7. Store the `Process` in the `Server`.
8. Start `ServerLogReader` to stream logs.
9. Notify property listeners so UI controls update.

Platform adapters can customize start behavior through:

- `buildStartProcess(...)`
- `requiresExecutableJarForStart()`

Forge/NeoForge/Quilt may use generated argument files or special launch paths.

Starting is serialized by `GestorServidores.inicioLock`, not by `synchronized` on the manager. The lock is released while the "port busy" dialog is open: a background thread (automation, restart after exit) that held the lock while waiting on the EDT would freeze the app as soon as the EDT tried to start another server. Ports of other servers Dora is already running count as busy, because a freshly launched server takes a few seconds to bind its port.

Before taking the lock, `iniciarServidor` compares the server's Minecraft version with the system `java` (`controlador.platform.JavaRuntimeCompatibility`). A mismatch is logged as `[WARN]`; manual starts also get a dialog once per server per session.

## Stop And Command Pipeline

Commands are written to the running process input stream.

Stop should prefer graceful server command behavior before killing the process. UI buttons should reflect whether the process exists and is alive.

Force stop (`forzarPararServidor`) must close the whole process tree through `controlador.ProcessTreeTerminator`. Forge/NeoForge servers start through `run.bat`/`run.sh`, so the process Dora holds is `cmd.exe`/`sh` and the real Java server is a child; killing only the root leaves an orphaned server holding the world and the port.

Quitting with active servers (`vista.SalidaServidoresActivos`) sends `stop`, then waits in 60-second rounds without killing anything. After each round the user chooses to keep waiting, force close and exit, or cancel the exit. Never add a silent force-kill timeout to the quit path: large or modded worlds can take longer than a minute to save.

`controlador.automation.ServerAutomationService` reuses the same `GestorServidores` lifecycle methods for scheduled starts, stops, restarts, and commands. Keep future automation behavior routed through the manager so manual controls and scheduled actions do not drift.

## Import Pipeline

Importing an existing server should:

- Validate the selected folder.
- Detect platform/version where possible.
- Avoid corrupting or rewriting unknown server contents.
- Preserve user data.
- Add the server to the managed list only after validation succeeds.

Server conversion preservation now routes through `controlador.ConversionPreservationHelper`. Keep backup/snapshot/restore behavior there unless a broader service split is being made, so `GestorServidores` does not absorb more filesystem-preservation detail.

## Delete/Remove Pipeline

There is a distinction between removing a server from the app and deleting files from disk. Keep UI copy clear and do not delete folders unless the flow explicitly asks for that destructive operation.

## Events And UI

Panels use `GestorServidores` property changes for state updates. If adding events, keep names stable and update consumers.

## Tests

Relevant tests:

- `src/test/java/controlador/GestorServidoresTest.java`
- `src/test/java/modelo/ServerModelPlatformTest.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`

Run at least targeted tests when changing lifecycle, persistence, or platform detection.

# Server Stop Leaves Forge Process Alive And Quit Kills Servers After 7 Seconds Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-stop-process-tree-and-quit-timeout.md`

## Scope

Force stop on `run.bat`/`run.sh` servers left the Java server orphaned, and quitting Dora force-killed servers that took more than 7 seconds to save.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `server-lifecycle-pipeline.md`, `forzarPararServidor`, the quit flows in `VentanaPrincipal` and `NoServerFrame`, and the Forge/NeoForge launch path. |
| DONE | 2. Kill the process tree | Added `ProcessTreeTerminator` (descendants snapshot, graceful then forced) and routed `forzarPararServidor` through it. |
| DONE | 3. Stop silent kills on quit | Split the quit logic into request/wait/force methods and added `SalidaServidoresActivos` with 60-second rounds and a keep waiting / force / cancel prompt. |
| DONE | 4. Verify behavior | `ProcessTreeTerminatorTest` covers a `sh` wrapper with a child, a tree that ignores SIGTERM, `forzarPararServidor`, and waiting without forcing. |
| DONE | 5. Update docs | Added the force-stop and quit rules to `server-lifecycle-pipeline.md`. |

## Implementation Notes

Descendants must be captured before the root is destroyed: once the root dies, its children are reparented and no longer appear in `ProcessHandle.descendants()`. On Unix `destroy()` sends SIGTERM to the Java server, which runs Minecraft's shutdown hook and saves; on Windows it is a hard terminate, so force stop still warns that changes may be lost.

## Verification Notes

- `mvn -q -Dtest=ProcessTreeTerminatorTest test`
- `mvn test` on Java 25.

The quit prompt was not exercised in a real desktop session in this change; check it manually on Windows with a Forge server.

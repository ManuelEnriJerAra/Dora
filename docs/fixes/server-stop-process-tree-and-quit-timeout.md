# Server Stop Leaves Forge Process Alive And Quit Kills Servers After 7 Seconds

## Status

Fixed

## Original Issue

- "Forzar Cierre Servidor" on Forge/NeoForge servers that start through `run.bat`/`run.sh` only killed the launcher script. The real Java server kept running in the background, holding the world files and the port, while Dora showed the server as stopped.
- Quitting Dora with active servers sent `stop` and force-killed every server still alive after 7 seconds. Large or modded worlds can take longer than that to save, so quitting could cut off a world save.

## Root Cause

- `forzarPararServidor` called `Process.destroy()`/`destroyForcibly()` on the process Dora started. For Forge/NeoForge that is `cmd.exe /c run.bat` or `sh run.sh`; Java never forwards the kill to child processes.
- `detenerServidoresActivosParaSalir` had a hard 7-second budget for all servers together, followed by a forced kill without asking.

## Solution

- New `controlador.ProcessTreeTerminator` snapshots the process descendants before touching the root, asks the whole tree to close (children first), waits, and then force-kills whatever is left. `forzarPararServidor` uses it, and "is it still running" checks during force stop and quit look at the whole tree.
- The quit flow moved to `vista.SalidaServidoresActivos`, shared by `VentanaPrincipal` and `NoServerFrame`. It sends `stop`, waits in 60-second rounds without killing anything, and after each round asks the user: keep waiting, force close and exit, or cancel the exit.
- `GestorServidores.detenerServidoresActivosParaSalir` was replaced by `pedirParadaServidoresActivosParaSalir`, `esperarParadaServidores` and `forzarPararServidores`.

## Files Changed

- `src/main/java/controlador/ProcessTreeTerminator.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/SalidaServidoresActivos.java`
- `src/main/java/vista/VentanaPrincipal.java`
- `src/main/java/vista/NoServerFrame.java`
- `src/test/java/controlador/ProcessTreeTerminatorTest.java`
- `docs/pipelines/server-lifecycle-pipeline.md`

## Verification

- `mvn -q -Dtest=ProcessTreeTerminatorTest test` (uses `sh` to stand in for `run.sh`; skipped on Windows)
- `mvn test`

## Detailed Process

- `docs/fixes/process/server-stop-process-tree-and-quit-timeout.md`

## Regression Notes

Any code that kills a server must kill its process tree, not just the `Process` Dora holds. Never add a silent force-kill timeout to the quit path; ask the user instead.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`

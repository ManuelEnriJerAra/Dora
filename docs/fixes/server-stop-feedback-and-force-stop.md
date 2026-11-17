# Server Stop Feedback And Force Stop

## Status

Fixed

## Original Issue

After pressing Parar nothing showed that the server was still saving: Parar stayed clickable and the list kept saying "Activo". The "Forzar cierre" button existed in `PanelControlServidor` but was never added to any panel, so a server stuck on `stop` could only be killed by quitting Dora.

## Root Cause

The UI only knew two states (process alive or not). `btnForzarCierreServidor` was created and wired but never placed in a layout.

## Solution

- `Server` has transient `deteniendo` and `paradaSolicitadaMillis` fields, set by `GestorServidores.mandarComando` when the command is `stop`/`end` (also typed in the console) and cleared when the process exits or is force-stopped.
- `PanelControlServidor` has a third state: a "Deteniendo..." button with elapsed seconds (or "Reiniciando..." for a restart). After 10 s a "Forzar cierre" button appears; it asks for confirmation and runs `forzarPararServidor` off the EDT.
- `PanelServidores` shows "Deteniendo" with an orange dot.

## Files Changed

- `src/main/java/modelo/Server.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/ArranqueServidor.java`
- `src/main/java/controlador/ParadaServidor.java`
- `src/main/java/vista/PanelControlServidor.java`
- `src/main/java/vista/PanelServidores.java`
- `src/test/java/controlador/ParadaServidorEstadoTest.java`

## Verification

- `mvn test`
- Manual: a stand-in server that waits 25 s on `stop` showed "Deteniendo... N s", then "Forzar cierre" after 10 s; confirming killed it and the controls returned to Iniciar.

## Detailed Process

- `docs/fixes/process/server-stop-feedback-and-force-stop.md`

## Regression Notes

Any new code path that stops a server should go through `mandarComando`/`forzarPararServidor` so the flag is set and cleared. Never run `forzarPararServidor` on the EDT: it waits up to 6 s for the process tree.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`

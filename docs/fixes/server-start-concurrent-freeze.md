# App Freezes When Two Servers Start At Once

## Status

Fixed

## Original Issue

Dora could freeze completely when a server was started from a background thread (scheduled automation, or the restart that runs after a server exits) while the user started another server from the UI, if the background start hit a busy port. Separately, two servers started a few seconds apart could both pick the same port, because a newly launched server has not bound its port yet.

## Root Cause

`iniciarServidor` was `synchronized` on `GestorServidores`. When the port was busy it showed the "Puerto del servidor ocupado" dialog with `SwingUtilities.invokeAndWait` while still holding the monitor. If the EDT was at that moment blocked trying to enter `iniciarServidor` (or any other `synchronized` method of the manager, such as `getAutomationService`), the dialog could never run and both threads waited forever.

Port selection only checked whether a socket could bind, which is true for a server that was just launched but is still loading.

## Solution

- Starting is serialized by a dedicated `inicioLock` instead of the manager's monitor.
- `iniciarServidor` now runs the locked part in `iniciarServidorBloqueado`. When the port is busy, that method returns a `SolicitudPuertoInicio`; the lock is released, the dialog is shown, and the start is retried under the lock with the chosen port.
- Ports configured for other servers Dora is already running are treated as busy.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/pipelines/server-lifecycle-pipeline.md`

## Verification

- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/server-start-concurrent-freeze.md`

## Regression Notes

Never show a dialog (or call `invokeAndWait`) while holding a lock that the EDT can also try to take.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`

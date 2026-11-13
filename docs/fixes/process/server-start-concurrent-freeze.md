# App Freezes When Two Servers Start At Once Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-start-concurrent-freeze.md`

## Scope

Deadlock between a background server start waiting on the port dialog and the EDT waiting on the manager's monitor, plus two servers picking the same port.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `server-lifecycle-pipeline.md`, every caller of `iniciarServidor` (UI buttons, list menu, automation, restart) and `solicitarPuertoAlternativo`. |
| DONE | 2. Move the dialog out of the lock | Added `inicioLock`, split the locked part into `iniciarServidorBloqueado`, and return a `SolicitudPuertoInicio` instead of showing the dialog inside the lock. |
| DONE | 3. Reserve ports of running servers | `resolverPuertoParaInicio` treats ports of other active servers as busy. |
| DONE | 4. Verify behavior | Added `iniciarServidor_noReutilizaElPuertoDeOtroServidorQueSigueArrancando`. |
| DONE | 5. Update docs | Added the locking rule to `server-lifecycle-pipeline.md`. |

## Implementation Notes

After the dialog, the start is retried from the top under the lock, so a server that was started meanwhile from another place is detected as already running. If the chosen port is taken again by then, the user is asked again.

## Verification Notes

- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn test` on Java 25.

The deadlock itself needs a desktop session to reproduce (automation start on a busy port while clicking Start); it was not reproduced by hand in this change.

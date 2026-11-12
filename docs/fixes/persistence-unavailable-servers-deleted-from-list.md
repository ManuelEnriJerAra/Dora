# Unavailable Servers Deleted From List

## Status

Fixed

## Original Issue

A saved server whose folder was missing at startup or refresh (unplugged external drive, unmounted network share, jar being replaced) was permanently removed from `dora-server-list.json`, together with its RAM/port config, automations and stats preferences.

## Root Cause

`GestorServidores.validarYLimpiarServidoresPersistidos` dropped every non-loadable entry from the list and saved immediately.

## Solution

Non-loadable entries now move to `servidoresNoDisponibles`. They are hidden from the UI but written back on every save, re-validated on each startup/refresh, and return to the visible list when their folder is back. The warning now says they are kept. `getServidoresNoDisponibles()` and `olvidarServidorNoDisponible(id)` exist for a future "remove" action in the UI.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/fixes/process/persistence-unavailable-servers-deleted-from-list.md`

## Verification

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

## Detailed Process

- `docs/fixes/process/persistence-unavailable-servers-deleted-from-list.md`

## Regression Notes

Validation failures are not proof that the user deleted a server. Hide, do not delete, unless the user asks. The earlier refresh fix (`server-list-refresh-stale-deleted-folders`) still holds: stale folders disappear from the visible list.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/fixes/server-list-refresh-stale-deleted-folders.md`

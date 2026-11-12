# Unavailable Servers Deleted From List Process

## Status

Fixed

## Linked Fix

- `docs/fixes/persistence-unavailable-servers-deleted-from-list.md`

## Scope

A saved server whose folder was missing at startup or refresh (unplugged external drive, unmounted network share, jar being replaced) was permanently removed from `dora-server-list.json`, together with its RAM/port config, automations and stats preferences.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the lifecycle pipeline and the earlier stale-folder refresh fix. |
| DONE | 2. Keep unavailable entries | Added `servidoresNoDisponibles`, persisted on save and re-validated on refresh. |
| DONE | 3. Update the warning | Message now says entries are kept and will return. |
| DONE | 4. Verify behavior | Updated the two existing tests and added return-with-config and forget tests. |

## Implementation Notes

Non-loadable entries now move to `servidoresNoDisponibles`. They are hidden from the UI but written back on every save, re-validated on each startup/refresh, and return to the visible list when their folder is back. The warning now says they are kept. `getServidoresNoDisponibles()` and `olvidarServidorNoDisponible(id)` exist for a future "remove" action in the UI.

## Verification Notes

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

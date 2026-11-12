# Corrupt Server List Overwritten Process

## Status

Fixed

## Linked Fix

- `docs/fixes/persistence-server-list-corrupt-file-overwritten.md`

## Scope

If `dora-server-list.json` could not be parsed, Dora started with an empty list and the next save overwrote the file, losing every registered server, automation rule and per-server preference. `dora-config.json` behaved the same way and reset the theme and CurseForge API key.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the lifecycle and configuration pipelines. |
| DONE | 2. Atomic saves | Server list and app config now go through `escribirArchivoAtomico`. |
| DONE | 3. Back up unreadable files | Added `respaldarArchivoIlegible` and the startup warning. |
| DONE | 4. Verify behavior | Added backup and no-temp-file tests in `GestorServidoresTest`. |

## Implementation Notes

Both files are now written with `Utilidades.escribirArchivoAtomico` (temp file plus atomic move). On a parse error the unreadable file is first copied to `<name>.corrupt-<timestamp>` by `GestorServidores.respaldarArchivoIlegible`, and for the server list the user gets a startup dialog with the path of the copy.

## Verification Notes

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

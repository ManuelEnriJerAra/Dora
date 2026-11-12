# Server Properties Wiped When Unreadable Process

## Status

Fixed

## Linked Fix

- `docs/fixes/config-server-properties-wiped-when-unreadable.md`

## Scope

Starting a server (or editing its MOTD) could replace `server.properties` with a file containing only `server-port` (or only `motd`). All other settings were lost, and because `level-name` fell back to `world` the server generated a new world on its next boot.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the configuration pipeline and every caller of the properties helpers. |
| DONE | 2. Make the loader tolerant | Strict UTF-8 with ISO-8859-1 fallback, BOM stripping, malformed escapes surfaced as `IOException`. |
| DONE | 3. Fail closed in the writers | Port and MOTD writers throw instead of saving an empty file. |
| DONE | 4. Make the save atomic | Added `escribirArchivoAtomico` and routed `guardarPropertiesUtf8` through it. |
| DONE | 5. Verify behavior | Added Latin-1, unreadable-file, and no-temp-file tests in `UtilidadesTest`. |

## Implementation Notes

`cargarPropertiesUtf8` now decodes strict UTF-8 first and falls back to ISO-8859-1, strips a BOM, and reports malformed `\\u` escapes as `IOException`. The port and MOTD writers no longer swallow read failures: they throw and leave the file untouched. `guardarPropertiesUtf8` now writes through the new atomic `Utilidades.escribirArchivoAtomico`.

## Verification Notes

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

# Server Properties Wiped When Unreadable

## Status

Fixed

## Original Issue

Starting a server (or editing its MOTD) could replace `server.properties` with a file containing only `server-port` (or only `motd`). All other settings were lost, and because `level-name` fell back to `world` the server generated a new world on its next boot.

## Root Cause

`Utilidades.escribirPuertoEnProperties` and `escribirMotdEnProperties` swallowed the `IOException` from `cargarPropertiesUtf8`, continued with an empty `Properties`, and saved it. The loader used a strict UTF-8 reader, so any file saved in ANSI/Latin-1 (for example a MOTD with accents edited in Notepad) threw `MalformedInputException`. `iniciarServidor` calls the port writer on every start.

## Solution

`cargarPropertiesUtf8` now decodes strict UTF-8 first and falls back to ISO-8859-1, strips a BOM, and reports malformed `\\u` escapes as `IOException`. The port and MOTD writers no longer swallow read failures: they throw and leave the file untouched. `guardarPropertiesUtf8` now writes through the new atomic `Utilidades.escribirArchivoAtomico`.

## Files Changed

- `src/main/java/controlador/Utilidades.java`
- `src/test/java/controlador/UtilidadesTest.java`
- `docs/fixes/process/config-server-properties-wiped-when-unreadable.md`

## Verification

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

## Detailed Process

- `docs/fixes/process/config-server-properties-wiped-when-unreadable.md`

## Regression Notes

Any "load, modify one key, save" flow must fail closed when the load fails. Never substitute an empty `Properties` for an unreadable file before saving.

## Related Docs

- `docs/pipelines/configuration-pipeline.md`

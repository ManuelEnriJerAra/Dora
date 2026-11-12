# Corrupt Server List Overwritten

## Status

Fixed

## Original Issue

If `dora-server-list.json` could not be parsed, Dora started with an empty list and the next save overwrote the file, losing every registered server, automation rule and per-server preference. `dora-config.json` behaved the same way and reset the theme and CurseForge API key.

## Root Cause

`GestorServidores.cargarServidores` and `GestorConfiguracion.cargarConfiguracion` treated a parse error as "no data". Both files were also written directly onto the target, so a crash or power cut during a save produced exactly that truncated file.

## Solution

Both files are now written with `Utilidades.escribirArchivoAtomico` (temp file plus atomic move). On a parse error the unreadable file is first copied to `<name>.corrupt-<timestamp>` by `GestorServidores.respaldarArchivoIlegible`, and for the server list the user gets a startup dialog with the path of the copy.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/GestorConfiguracion.java`
- `src/main/java/controlador/Utilidades.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/fixes/process/persistence-server-list-corrupt-file-overwritten.md`

## Verification

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

## Detailed Process

- `docs/fixes/process/persistence-server-list-corrupt-file-overwritten.md`

## Regression Notes

New persisted JSON files should use the atomic writer and back up unreadable content before falling back to defaults.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`

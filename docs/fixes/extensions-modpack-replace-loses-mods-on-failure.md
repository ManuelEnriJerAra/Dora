# Modpack Replace Loses Mods On Failure

## Status

Fixed

## Original Issue

Importing a modpack with "replace existing" deleted every managed mod jar and only then started downloading. A network error, a CurseForge API failure or a hash mismatch partway through left the server with a partial modpack and the original mods gone.

## Root Cause

`eliminarJarsGestionadosParaReemplazoModpack` deleted the jars with no backup before any download had succeeded.

## Solution

The jars are now moved to a temporary `.dora-backup/mods-*` folder inside the server directory (`apartarJarsGestionadosParaReemplazoModpack`). If any install fails, `restaurarModsTrasFalloDeModpack` deletes the partially installed jars, moves the previous jars back, restores the previous extension metadata and rethrows with "no se ha cambiado nada". The backup is deleted only after every entry installed (`confirmarReemplazoModpack`), so a successful import leaves no extra folder. Applies to both Modrinth and CurseForge imports.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/fixes/process/extensions-modpack-replace-loses-mods-on-failure.md`

## Verification

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.

## Detailed Process

- `docs/fixes/process/extensions-modpack-replace-loses-mods-on-failure.md`

## Regression Notes

Destructive multi-step operations should stage or back up first and only discard the old state after the new state is complete.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/fixes/extensions-modpack-import-existing-mods.md`
